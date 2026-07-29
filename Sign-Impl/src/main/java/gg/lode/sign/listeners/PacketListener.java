package gg.lode.sign.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import gg.lode.sign.Sign;
import gg.lode.sign.nametags.Nametag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketListener extends PacketListenerAbstract {
    private final Sign plugin;

    public PacketListener(Sign plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.config().getNametagConfig().isEnabled()) return;

        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnEntity(event);
        } else if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handleSetPassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
            handleRespawn(event);
        }
    }

    /**
     * A respawn/dimension change wipes the client's entire entity list WITHOUT
     * sending DESTROY_ENTITIES, so every nametag's tracked/visible state for
     * this viewer is silently stale. Forget the viewer everywhere; the
     * SPAWN_ENTITY path re-shows each tag once the client actually has the
     * player entity again (prevents mount packets racing the world load on
     * teleports to new worlds or spectate jumps).
     */
    private void handleRespawn(PacketSendEvent event) {
        Player viewer = (Player) event.getPlayer();
        if (viewer == null) return;

        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            if (nametag.getPlayer().equals(viewer)) continue;
            nametag.forgetViewer(viewer.getUniqueId());
        }
    }

    private void handleSpawnEntity(PacketSendEvent event) {
        Player viewer = (Player) event.getPlayer();
        if (viewer == null) return;

        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
        int entityId = packet.getEntityId();

        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            Player target = nametag.getPlayer();
            if (target.equals(viewer)) continue;
            if (target.getEntityId() != entityId) continue;

            // Mark that this viewer's client now has the player entity
            nametag.markTracked(viewer.getUniqueId());

            Bukkit.getScheduler().runTaskLater(plugin.host(), () -> {
                if (viewer.isOnline() && target.isOnline()) {
                    // Uses updateVisibilityFor which checks shouldSee before showing,
                    // preventing show at tracking range when beyond visibility distance
                    nametag.updateVisibilityFor(viewer);
                }
            }, 2L);
            break;
        }
    }

    private void handleDestroyEntities(PacketSendEvent event) {
        Player viewer = (Player) event.getPlayer();
        if (viewer == null) return;

        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
        int[] entityIds = packet.getEntityIds();

        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            Player target = nametag.getPlayer();
            if (target.equals(viewer)) continue;

            for (int id : entityIds) {
                if (id == target.getEntityId()) {
                    // Mark that this viewer's client no longer has the player entity
                    nametag.markUntracked(viewer.getUniqueId());

                    Bukkit.getScheduler().runTask(plugin.host(), () -> {
                        if (viewer.isOnline()) {
                            nametag.hide(viewer);
                        }
                    });
                    break;
                }
            }
        }
    }

    private void handleSetPassengers(PacketSendEvent event) {
        Player viewer = (Player) event.getPlayer();
        if (viewer == null) return;

        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        int vehicleId = packet.getEntityId();
        int[] serverPassengers = packet.getPassengers();

        boolean vehicleHandled = false;

        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            Player target = nametag.getPlayer();
            if (!nametag.isVisibleTo(viewer)) continue;

            // Case 1: Vehicle is the nametag player — merge our display IDs into the passenger list
            if (!vehicleHandled && target.getEntityId() == vehicleId) {
                vehicleHandled = true;

                int[] displayIds = nametag.getDisplayEntityIds();

                boolean alreadyPresent = false;
                for (int displayId : displayIds) {
                    for (int serverId : serverPassengers) {
                        if (serverId == displayId) {
                            alreadyPresent = true;
                            break;
                        }
                    }
                    if (alreadyPresent) break;
                }

                if (!alreadyPresent) {
                    int[] merged = new int[serverPassengers.length + displayIds.length];
                    System.arraycopy(serverPassengers, 0, merged, 0, serverPassengers.length);
                    System.arraycopy(displayIds, 0, merged, serverPassengers.length, displayIds.length);
                    packet.setPassengers(merged);
                    serverPassengers = merged;
                }
                continue;
            }

            // Case 2: Nametag player appears as a passenger (e.g., GSit sit/lay/crawl, riding another player)
            // The client can drop sub-passengers when mounting, so re-send our mount packet after a delay
            for (int passengerId : serverPassengers) {
                if (target.getEntityId() == passengerId) {
                    Bukkit.getScheduler().runTaskLater(plugin.host(), () -> {
                        if (viewer.isOnline() && target.isOnline()) {
                            nametag.remount(viewer);
                        }
                    }, 2L);
                    break;
                }
            }
        }
    }
}

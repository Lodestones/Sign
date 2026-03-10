package gg.lode.sign.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
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

        if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
            handleDestroyEntities(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handleSetPassengers(event);
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
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline() && target.isOnline()) {
                            nametag.hide(viewer);
                            nametag.show(viewer);
                        }
                    }, 20L);
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

        // Find the nametag whose player entity matches this vehicle ID
        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            Player target = nametag.getPlayer();
            if (target.getEntityId() != vehicleId) continue;
            if (!nametag.isVisibleTo(viewer)) continue;

            // Merge our display entity IDs into the passenger list
            int[] serverPassengers = packet.getPassengers();
            int[] displayIds = nametag.getDisplayEntityIds();

            // Check if our IDs are already present (avoid duplicates from our own mount packets)
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
            if (alreadyPresent) break;

            // Merge: server passengers + our display entities
            int[] merged = new int[serverPassengers.length + displayIds.length];
            System.arraycopy(serverPassengers, 0, merged, 0, serverPassengers.length);
            System.arraycopy(displayIds, 0, merged, serverPassengers.length, displayIds.length);
            packet.setPassengers(merged);

            break;
        }
    }
}

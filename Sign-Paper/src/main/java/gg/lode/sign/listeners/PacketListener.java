package gg.lode.sign.listeners;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
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
        if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES) return;
        if (!plugin.config().getNametagConfig().isEnabled()) return;

        Player viewer = (Player) event.getPlayer();
        if (viewer == null) return;

        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);
        int[] entityIds = packet.getEntityIds();

        for (Nametag nametag : plugin.getNametagManager().getAll()) {
            Player target = nametag.getPlayer();
            if (target.equals(viewer)) continue;

            for (int id : entityIds) {
                if (id == target.getEntityId()) {
                    // Player entity is being destroyed for this viewer (profile refresh/nick)
                    // Re-show after a tick so the respawn packet goes through first
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (viewer.isOnline() && target.isOnline()) {
                            nametag.hide(viewer);
                            nametag.show(viewer);
                        }
                    }, 2L);
                    break;
                }
            }
        }
    }
}

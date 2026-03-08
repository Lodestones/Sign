package gg.lode.sign.utils.handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import gg.lode.sign.Sign;
import gg.lode.sign.utils.helpers.DependencyHelper;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import me.neznamy.tab.api.nametag.NameTagManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class NametagHandler {
    public static void load() {
        if (DependencyHelper.isTABEnabled()) {
            TabAPI.getInstance().getEventBus().register(PlayerLoadEvent.class, (event) -> {
                TabPlayer player = event.getPlayer();
                NameTagManager manager = TabAPI.getInstance().getNameTagManager();

                if (Sign.getInstance().config().getNametagConfig().isEnabled() && manager != null) {
                    manager.hideNameTag(player);
                }
            });
        }
    }

    public static void hide(Player target, Player viewer) {
        if (DependencyHelper.isTABEnabled() && TabAPI.getInstance().getNameTagManager() != null) return;
        String name = getTeamName(target);
        WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.ALWAYS,
                null,
                WrapperPlayServerTeams.OptionData.NONE
        );
        WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
                name,
                WrapperPlayServerTeams.TeamMode.CREATE,
                teamInfo,
                target.getName()
        );
        sendPacket(packet, viewer);
    }

    public static void show(Player target, Player viewer) {
        if (DependencyHelper.isTABEnabled() && TabAPI.getInstance().getNameTagManager() != null) return;
        String name = getTeamName(target);
        WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
                name,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                target.getName()
        );
        sendPacket(packet, viewer);
    }

    private static String getTeamName(Player target) {
        return "sign_" + target.getEntityId();
    }

    private static void sendPacket(PacketWrapper<?> packet, Player viewer) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }
}
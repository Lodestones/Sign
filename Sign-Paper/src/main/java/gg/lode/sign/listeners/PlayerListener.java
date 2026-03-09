package gg.lode.sign.listeners;

import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import gg.lode.sign.Sign;
import gg.lode.sign.nametags.Nametag;
import gg.lode.sign.nametags.NametagManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PlayerListener implements Listener {
    private final Sign plugin = Sign.getInstance();
    private final NametagManager nametagManager = Sign.getInstance().getNametagManager();

    @EventHandler
    public void onPlayerLoad(PlayerClientLoadedWorldEvent event) {
        Player player = event.getPlayer();
        if (!plugin.config().getNametagConfig().isEnabled()) return;

        if (nametagManager.get(player) != null) {
            nametagManager.remove(player);
        }

        // Delay nametag creation to ensure all clients have received entity spawn packets
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            nametagManager.create(player);

            // Also refresh existing nametags for this viewer
            for (Nametag nametag : nametagManager.getAll()) {
                if (nametag.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                nametag.updateVisibilityForAll();
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            Player player = event.getPlayer();
            nametagManager.remove(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            Nametag nametag = nametagManager.get(event.getPlayer());
            if (nametag != null) {
                nametag.hideForAll();
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.config().getNametagConfig().isEnabled()) return;

        Player player = event.getPlayer();

        // Re-show this player's nametag + show other nametags to them after respawn
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Nametag nametag = nametagManager.get(player);
            if (nametag != null) {
                nametag.updateVisibilityForAll();
            }

            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.updateVisibilityForAll();
            }
        }, 5L);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            Nametag nametag = nametagManager.get(event.getPlayer());
            // NULL CHECK
            if (nametag != null) {
                nametag.updateVisibilityForAll();
            }
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            Nametag nametag = nametagManager.get(event.getPlayer());
            // NULL CHECK
            if (nametag != null) {
                nametag.hideForAll();
                nametag.updateVisibilityForAll();
            }
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (plugin.config().getNametagConfig().isEnabled() && plugin.config().getNametagConfig().supportsCrouching()) {
            Nametag nametag = nametagManager.get(event.getPlayer());
            if (nametag != null) {
                nametag.updateVisibilityForAll();
            }
        }
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.config().getNametagConfig().isEnabled()) return;

        Player player = event.getPlayer();
        Nametag nametag = nametagManager.get(player);

        if (nametag != null) {
            if (event.getNewGameMode() == GameMode.SPECTATOR) {
                nametag.hideForAll();
            } else {
                nametag.updateVisibilityForAll();
            }
        }

        // Recalculate all other nametags for this viewer
        // (spectator can't see nametags, exiting spectator needs them back)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.updateVisibilityForAll();
            }
        }, 1L);
    }
}

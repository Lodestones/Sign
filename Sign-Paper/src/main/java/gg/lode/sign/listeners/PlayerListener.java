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

        nametagManager.create(player);

        // Show existing nametags to this viewer
        for (Nametag nametag : nametagManager.getAll()) {
            if (nametag.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
            nametag.updateVisibilityFor(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            nametagManager.remove(event.getPlayer());
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

        // Short delay — entity was never destroyed for other viewers, just need respawn to finish
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // Re-show after death hid the nametag (entity still exists on viewers' clients)
            Nametag nametag = nametagManager.get(player);
            if (nametag != null) {
                nametag.showToEligible();
            }

            // Show existing nametags to the respawned player
            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.updateVisibilityFor(player);
            }
        }, 2L);
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
            } else if (player.getGameMode() == GameMode.SPECTATOR) {
                // Coming out of spectator — entity will be re-spawned for viewers via SPAWN_ENTITY,
                // but we also explicitly show in case tracking doesn't trigger a respawn
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) nametag.showToEligible();
                }, 2L);
            }
        }

        // Update other nametags for this viewer
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.updateVisibilityFor(player);
            }
        }, 1L);
    }
}

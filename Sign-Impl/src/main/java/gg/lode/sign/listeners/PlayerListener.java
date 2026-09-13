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

        Nametag existing = nametagManager.get(player);
        if (existing != null) {
            // A world or dimension change, and the client has just finished loading the new one — which
            // means it threw away every entity it had, these displays included. What was believed here
            // before is that DESTROY/SPAWN would cover it; nothing sends either, so the tag went on
            // believing it was shown, never spawned anything again, and the five-second heartbeat
            // re-sent mounts naming display ids the client no longer had and dropped every one.
            //
            // So the bookkeeping is dropped on both sides and rebuilt: this player's own tag for
            // everybody who can see them now, and everybody else's for this player, who has just lost
            // all of them.
            existing.forgetAllViewers();
            existing.showToEligible();
            for (Nametag nametag : nametagManager.getAll()) {
                if (nametag == existing) continue;
                nametag.forgetViewer(player);
                nametag.updateVisibilityFor(player);
            }
            return;
        }

        // Initial join — create nametag and show to nearby viewers
        nametagManager.create(player);

        // Show existing nametags to this viewer (entities already loaded on client)
        for (Nametag nametag : nametagManager.getAll()) {
            if (nametag.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
            nametag.updateVisibilityFor(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.config().getNametagConfig().isEnabled()) {
            nametagManager.remove(event.getPlayer());
            // Tags riding an entity id are keyed by nothing, so nothing else drops a viewer who has
            // gone — and a later line change would send packets down a closed connection.
            nametagManager.forgetViewer(event.getPlayer());
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
        plugin.getServer().getScheduler().runTaskLater(plugin.host(), () -> {
            if (!player.isOnline()) return;

            // Re-show after death hid the nametag. The entity survives a same-world respawn on
            // viewers' clients, but a death that sends somebody to another dimension — the nether,
            // most of the time — clears it, so what was shown before cannot be trusted either way.
            Nametag nametag = nametagManager.get(player);
            if (nametag != null) {
                nametag.forgetAllViewers();
                nametag.showToEligible();
            }

            // Show existing nametags to the respawned player
            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.forgetViewer(player);
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
                plugin.getServer().getScheduler().runTaskLater(plugin.host(), () -> {
                    if (player.isOnline()) nametag.showToEligible();
                }, 2L);
            }
        }

        // Update other nametags for this viewer
        plugin.getServer().getScheduler().runTaskLater(plugin.host(), () -> {
            if (!player.isOnline()) return;
            for (Nametag other : nametagManager.getAll()) {
                if (other.getPlayer().getUniqueId().equals(player.getUniqueId())) continue;
                other.updateVisibilityFor(player);
            }
        }, 1L);
    }
}

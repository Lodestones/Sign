package gg.lode.sign.nametags;

import gg.lode.sign.Sign;
import gg.lode.sign.api.nametag.INametagManager;
import gg.lode.sign.utils.handlers.NametagHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NametagManager implements INametagManager {
    private final Map<UUID, Nametag> nametags;

    public NametagManager() {
        this.nametags = new ConcurrentHashMap<>();
    }

    @Override
    public Nametag get(Player player) {
        return this.nametags.get(player.getUniqueId());
    }

    @Override
    public Collection<Nametag> getAll() {
        return this.nametags.values();
    }

    @Override
    public void create(Player player) {
        Nametag nametag = new Nametag(player);
        nametag.updateVisibilityForAll();
        this.nametags.put(player.getUniqueId(), nametag);

        // Re-show after a delay to guarantee mounts are received by all clients
        Bukkit.getScheduler().runTaskLater(Sign.getInstance(), () -> {
            if (!player.isOnline()) return;
            Nametag n = this.nametags.get(player.getUniqueId());
            if (n != null) {
                n.hideForAll();
                n.updateVisibilityForAll();
            }
        }, 10L);
    }

    @Override
    public void remove(Player player) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            NametagHandler.show(player, viewer);
        }

        Nametag nametag = nametags.get(player.getUniqueId());
        if (nametag != null) {
            nametag.hideForAll();
            nametags.remove(player.getUniqueId());
        }
    }

    @Override
    public void createAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.create(player);
        }
    }

    @Override
    public void removeAll() {
        for (Nametag nametag : nametags.values()) {
            this.remove(nametag.getPlayer());
        }
    }
}

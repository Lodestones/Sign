package gg.lode.sign.nametags;

import gg.lode.sign.api.nametag.INametagManager;
import gg.lode.sign.utils.handlers.NametagHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NametagManager implements INametagManager {
    private final Map<UUID, Nametag> nametags;

    /**
     * Tags riding entity ids rather than players.
     *
     * <p>A list, not a map: nothing identifies them but the handle the caller holds, and the same
     * vehicle may carry more than one. Held only so they can all be taken down at once on reload, and
     * so a leaving viewer can be dropped from every one of them.
     */
    private final Set<VirtualNametag> virtualNametags = ConcurrentHashMap.newKeySet();

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
        this.nametags.put(player.getUniqueId(), nametag);
        nametag.showToEligible();
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
        for (VirtualNametag virtual : List.copyOf(virtualNametags)) {
            virtual.remove();
        }
    }

    @Override
    public VirtualNametag createVirtual(int vehicleEntityId) {
        VirtualNametag nametag = new VirtualNametag(this, vehicleEntityId);
        virtualNametags.add(nametag);
        return nametag;
    }

    /** Every virtual tag still standing, so a leaving viewer can be dropped from all of them. */
    public Collection<VirtualNametag> getVirtual() {
        return List.copyOf(virtualNametags);
    }

    void forgetVirtual(VirtualNametag nametag) {
        virtualNametags.remove(nametag);
    }

    /**
     * Drops a viewer from every virtual tag.
     *
     * <p>Without this each tag keeps a reference to a player who has gone, and a later line change
     * sends packets to a connection that is closed. A player's own nametag is keyed by uuid and cleaned
     * up by {@link #remove(Player)}; these are keyed by nothing, so nothing else would.
     */
    public void forgetViewer(Player viewer) {
        for (VirtualNametag nametag : List.copyOf(virtualNametags)) {
            nametag.forgetViewer(viewer);
        }
    }
}

package gg.lode.sign.nametags;

import gg.lode.sign.api.nametag.IVirtualNametag;
import gg.lode.sign.entities.ClientTextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A nametag riding an entity id rather than a player.
 *
 * <p>One text display, mounted on the vehicle, carrying every line as one block of text. A player's
 * nametag uses a display per line so lines can be shown and hidden independently as their state
 * changes; nothing here has state to change, so the simpler shape is also the correct one.
 *
 * <p>Deliberately reads nothing about its vehicle. It cannot: the vehicle may be an entity the server
 * has never had, which is the whole reason this class exists. Who sees it and what it says are the
 * caller's to decide, and its position is the client's problem — a mounted entity follows its vehicle
 * without the server saying anything further.
 */
public class VirtualNametag implements IVirtualNametag {

    private final NametagManager manager;
    private final int vehicleEntityId;
    private final ClientTextDisplay display;
    private final Set<Player> viewers = new LinkedHashSet<>();

    private List<Component> lines = List.of();
    private boolean removed;

    VirtualNametag(NametagManager manager, int vehicleEntityId) {
        this.manager = manager;
        this.vehicleEntityId = vehicleEntityId;
        // Spawned at the world's origin and never moved: mounting is what puts it in the right place,
        // and the spawn position only matters for the tick before the mount arrives.
        this.display = new ClientTextDisplay(new Location(Bukkit.getWorlds().get(0), 0, 0, 0));
        this.display.setSeeThrough(false);
        this.display.setText(Component.empty());
    }

    @Override
    public int getVehicleEntityId() {
        return vehicleEntityId;
    }

    @Override
    public void setLines(List<Component> lines) {
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.display.setText(Component.join(JoinConfiguration.newlines(), this.lines));
        if (removed) return;
        for (Player viewer : List.copyOf(viewers)) display.update(viewer);
    }

    @Override
    public void setLine(Component line) {
        setLines(line == null ? List.of() : List.of(line));
    }

    @Override
    public void show(Player viewer) {
        if (removed || viewer == null || !viewer.isOnline()) return;
        if (!viewers.add(viewer)) return;
        display.spawn(viewer);
        display.update(viewer);
        // Last, and always: a mount sent before the display exists client-side is dropped, and the tag
        // then sits wherever it spawned rather than above its vehicle.
        display.mount(vehicleEntityId, viewer);
    }

    @Override
    public void hide(Player viewer) {
        if (viewer == null || !viewers.remove(viewer)) return;
        if (viewer.isOnline()) display.despawn(viewer);
    }

    @Override
    public List<Player> getViewers() {
        return Collections.unmodifiableList(new ArrayList<>(viewers));
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        for (Player viewer : List.copyOf(viewers)) {
            if (viewer.isOnline()) display.despawn(viewer);
        }
        viewers.clear();
        manager.forgetVirtual(this);
    }

    /**
     * Re-sends the mount to everybody shown this tag.
     *
     * <p>The vehicle is usually not the server's to begin with — a replayed subject is built from
     * packets and spawned for each viewer on its own schedule — so the mount this tag sent when it was
     * shown may have named an entity that viewer's client did not have yet, and a mount naming nothing
     * is discarded without a word. Re-sent, it attaches as soon as the vehicle is really there.
     *
     * <p>Only the mount: the display itself already exists client-side, and respawning it would make the
     * tag blink every time this runs.
     */
    public void remountAll() {
        if (removed) return;
        for (Player viewer : List.copyOf(viewers)) {
            if (viewer.isOnline()) display.mount(vehicleEntityId, viewer);
        }
    }

    /** Takes a viewer off the list without sending anything — for one who has already gone. */
    void forgetViewer(Player viewer) {
        viewers.remove(viewer);
    }

    boolean isRemoved() {
        return removed;
    }
}

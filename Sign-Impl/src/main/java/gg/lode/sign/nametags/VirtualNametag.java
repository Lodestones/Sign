package gg.lode.sign.nametags;

import gg.lode.sign.Sign;
import gg.lode.sign.api.nametag.IVirtualNametag;
import gg.lode.sign.config.NametagConfig;
import gg.lode.sign.entities.ClientEntity;
import gg.lode.sign.entities.ClientTextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.github.retrooper.packetevents.util.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Set;

/**
 * A nametag riding an entity id rather than a player.
 *
 * <p>Built the way a player's nametag is, from the same config and the same offsets: one display per
 * line when holograms are not condensed, one for all of them when they are, each translated up by the
 * spacing a player's tag uses. Anything less and a replayed subject wears a tag that is recognisably not
 * the server's — sitting low, unshaded, or with its lines bunched.
 *
 * <p>What it deliberately does not do is read its vehicle. It cannot: the vehicle may be an entity the
 * server has never had, which is the whole reason this class exists. Who sees it and what it says are
 * the caller's to decide, and its position is the client's problem — a mounted entity follows its vehicle
 * without the server saying anything further.
 */
public class VirtualNametag implements IVirtualNametag {

    private final NametagManager manager;
    private final int vehicleEntityId;
    private final boolean condensed;
    private final Set<Player> viewers = new LinkedHashSet<>();

    /**
     * One set of displays per viewer.
     *
     * <p>Not one set shared between them. The lines can differ per viewer — a health readout staff are
     * shown and players are not — and so can how many there are, which decides where each display sits.
     * Separate entity ids per viewer is what makes that possible without every tag having to agree.
     */
    private final Map<UUID, List<ClientTextDisplay>> displays = new LinkedHashMap<>();

    /** Per-viewer line overrides, and the lines for everybody without one. */
    private final Map<UUID, List<Component>> overrides = new LinkedHashMap<>();
    private List<Component> lines = List.of();
    private boolean removed;

    VirtualNametag(NametagManager manager, int vehicleEntityId) {
        this.manager = manager;
        this.vehicleEntityId = vehicleEntityId;
        this.condensed = Sign.getInstance().config().getNametagConfig().isCondenseHolograms();
    }

    @Override
    public int getVehicleEntityId() {
        return vehicleEntityId;
    }

    /**
     * Builds the displays for the current lines.
     *
     * <p>Rebuilt when the number of lines changes rather than patched: the translation of every display
     * depends on how many there are, so a tag that gained a line would otherwise have all of them a
     * line too low.
     */
    /** The lines this viewer should see: their own if they have been given any, otherwise the shared set. */
    private List<Component> linesFor(Player viewer) {
        List<Component> own = overrides.get(viewer.getUniqueId());
        return own != null ? own : lines;
    }

    private List<ClientTextDisplay> build(List<Component> forLines) {
        NametagConfig config = Sign.getInstance().config().getNametagConfig();
        int background = Nametag.configuredBackground();
        // Spawned at the origin of the first world and never moved: the mount is what puts it in place,
        // and the spawn position only matters for the moment before the mount lands.
        Location somewhere = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);

        List<ClientTextDisplay> built = new ArrayList<>();
        int count = condensed ? 1 : Math.max(1, forLines.size());
        for (int i = 0; i < count; i++) {
            float y = Nametag.BASE_Y_OFFSET + (count - 1 - i) * Nametag.LINE_SPACING;
            ClientTextDisplay display = new ClientTextDisplay(somewhere);
            display.setTranslation(new Vector3f(0, y, 0));
            display.setScale(config.getScale());
            display.setTextShadow(config.hasTextShadow());
            display.setTextAlignment(config.getTextAlignment());
            display.setSeeThrough(config.isSeeThrough());
            display.setBillboard(config.getBillboard());
            display.setBackground(background);
            display.setText(textFor(forLines, i, count));
            built.add(display);
        }
        return built;
    }

    /** The text for one display: every line at once when condensed, otherwise the line at that index. */
    private Component textFor(List<Component> forLines, int index, int count) {
        if (forLines.isEmpty()) return Component.empty();
        if (condensed || count == 1) return Component.join(JoinConfiguration.newlines(), forLines);
        return index < forLines.size() ? forLines.get(index) : Component.empty();
    }

    @Override
    public void setLines(List<Component> lines) {
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        for (Player viewer : List.copyOf(viewers)) {
            if (!overrides.containsKey(viewer.getUniqueId())) redraw(viewer);
        }
    }

    @Override
    public void setLines(Player viewer, List<Component> lines) {
        if (viewer == null) return;
        if (lines == null) overrides.remove(viewer.getUniqueId());
        else overrides.put(viewer.getUniqueId(), List.copyOf(lines));
        if (viewers.contains(viewer)) redraw(viewer);
    }

    /**
     * Puts this viewer's tag back on their screen with the lines they should now see.
     *
     * <p>Rebuilt rather than updated when the number of lines changes, because every display's offset
     * depends on how many there are — a tag that gained a line would otherwise sit a line too low.
     */
    private void redraw(Player viewer) {
        if (removed || !viewer.isOnline()) return;
        List<Component> want = linesFor(viewer);
        List<ClientTextDisplay> existing = displays.get(viewer.getUniqueId());
        int have = existing == null ? -1 : existing.size();
        int need = condensed ? 1 : Math.max(1, want.size());

        if (existing == null || have != need) {
            if (existing != null) for (ClientTextDisplay display : existing) display.despawn(viewer);
            List<ClientTextDisplay> built = build(want);
            displays.put(viewer.getUniqueId(), built);
            spawnAll(viewer);
            return;
        }
        for (int i = 0; i < have; i++) {
            existing.get(i).setText(textFor(want, i, have));
            existing.get(i).update(viewer);
        }
    }

    @Override
    public void setLine(Component line) {
        setLines(line == null ? List.of() : List.of(line));
    }

    @Override
    public void show(Player viewer) {
        if (removed || viewer == null || !viewer.isOnline()) return;
        if (!viewers.add(viewer)) return;
        displays.computeIfAbsent(viewer.getUniqueId(), id -> build(linesFor(viewer)));
        spawnAll(viewer);
    }

    @Override
    public void hide(Player viewer) {
        if (viewer == null || !viewers.remove(viewer)) return;
        if (viewer.isOnline()) despawnAll(viewer);
        displays.remove(viewer.getUniqueId());
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
            if (viewer.isOnline()) despawnAll(viewer);
        }
        viewers.clear();
        displays.clear();
        overrides.clear();
        manager.forgetVirtual(this);
    }

    /**
     * Re-sends the mounts to everybody shown this tag.
     *
     * <p>The vehicle is usually not the server's — a replayed subject is built from packets and spawned
     * for each viewer on its own schedule — so the mount sent when this was shown may have named an
     * entity that client did not have yet, and a mount naming nothing is discarded without a word.
     * Re-sent, it attaches as soon as the vehicle is really there.
     *
     * <p>Mounts only. The displays already exist client-side, and respawning them would make the tag
     * blink every time this runs.
     */
    public void remountAll() {
        if (removed) return;
        for (Player viewer : List.copyOf(viewers)) {
            if (!viewer.isOnline()) continue;
            List<ClientTextDisplay> mine = displays.getOrDefault(viewer.getUniqueId(), List.of());
            if (mine.isEmpty()) continue;
            ClientEntity.sendBundle(viewer, List.of(ClientEntity.createMountPacket(vehicleEntityId, mine)));
        }
    }

    private void spawnAll(Player viewer) {
        List<ClientTextDisplay> mine = displays.getOrDefault(viewer.getUniqueId(), List.of());
        for (ClientTextDisplay display : mine) {
            display.spawn(viewer);
            display.update(viewer);
        }
        // One mount for all of them, after they exist client-side. SET_PASSENGERS replaces the vehicle's
        // whole passenger list, so a mount per display left only the last line riding and dropped the
        // rest at the point they spawned — which read as the bottom line replacing the tag.
        if (!mine.isEmpty()) {
            ClientEntity.sendBundle(viewer, List.of(ClientEntity.createMountPacket(vehicleEntityId, mine)));
        }
    }

    private void despawnAll(Player viewer) {
        for (ClientTextDisplay display : displays.getOrDefault(viewer.getUniqueId(), List.of())) {
            display.despawn(viewer);
        }
    }

    /** Takes a viewer off the list without sending anything — for one who has already gone. */
    void forgetViewer(Player viewer) {
        viewers.remove(viewer);
        displays.remove(viewer.getUniqueId());
        overrides.remove(viewer.getUniqueId());
    }

    boolean isRemoved() {
        return removed;
    }
}

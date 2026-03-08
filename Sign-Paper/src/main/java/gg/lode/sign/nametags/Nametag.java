package gg.lode.sign.nametags;

import gg.lode.sign.api.nametag.INametag;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import me.clip.placeholderapi.PlaceholderAPI;
import gg.lode.sign.Sign;
import gg.lode.sign.config.NametagConfig;
import gg.lode.sign.entities.ClientEntity;
import gg.lode.sign.entities.ClientTextDisplay;
import gg.lode.sign.utils.ComponentUtils;
import gg.lode.sign.utils.handlers.NametagHandler;
import gg.lode.sign.utils.helpers.DependencyHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Nametag implements INametag {
    private static final Pattern CONDITION_PATTERN = Pattern.compile("<condition:'([^']+)'>(.+?)</condition>");
    private static final byte OPACITY_FULL = -1;
    private static final byte OPACITY_CROUCHING = 64;
    private static final float BASE_Y_OFFSET = 0.25f;
    private static final float LINE_SPACING = 0.27f;

    private final Sign plugin;
    private final Player player;
    private final Set<UUID> viewers;

    private final List<String> lines;
    private final boolean hideSelf;
    private final boolean supportCrouching;
    private final boolean condensed;
    private final int visibilityDistance;

    // Global line override
    private List<String> globalOverride;

    // Per-viewer line overrides
    private final Map<UUID, List<String>> viewerOverrides;

    // Condensed mode: single display
    private final ClientTextDisplay condensedDisplay;

    // Non-condensed mode: one display per line
    private final List<ClientTextDisplay> lineDisplays;

    private Component cachedText;
    private List<Component> cachedLineTexts;
    private boolean cachedSneaking;

    public Nametag(Player player) {
        this.plugin = Sign.getInstance();
        this.player = player;
        this.viewers = new HashSet<>();
        this.viewerOverrides = new HashMap<>();

        NametagConfig config = plugin.config().getNametagConfig();
        this.lines = config.getLines();
        this.hideSelf = config.shouldHideSelf();
        this.supportCrouching = config.supportsCrouching();
        this.condensed = config.isCondenseHolograms();
        this.visibilityDistance = config.getVisibilityDistance();

        int background = getBackground();

        if (condensed) {
            this.condensedDisplay = createDisplay(config, background, new Vector3f(0, BASE_Y_OFFSET, 0));
            this.lineDisplays = null;
        } else {
            this.condensedDisplay = null;
            this.lineDisplays = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                float y = BASE_Y_OFFSET + (lines.size() - 1 - i) * LINE_SPACING;
                lineDisplays.add(createDisplay(config, background, new Vector3f(0, y, 0)));
            }
            this.cachedLineTexts = new ArrayList<>(Collections.nCopies(lines.size(), null));
        }
    }

    private ClientTextDisplay createDisplay(NametagConfig config, int background, Vector3f translation) {
        ClientTextDisplay display = new ClientTextDisplay(player.getLocation().setRotation(0, 0));
        display.setTranslation(translation);
        display.setScale(config.getScale());
        display.setTextShadow(config.hasTextShadow());
        display.setTextAlignment(config.getTextAlignment());
        display.setSeeThrough(config.isSeeThrough());
        display.setBillboard(config.getBillboard());
        display.setBackground(background);
        return display;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void showForAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.show(viewer);
        }
    }

    @Override
    public void hideForAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.hide(viewer);
        }
    }

    @Override
    public void updateVisibilityForAll() {
        viewers.removeIf((uuid) -> {
            Player viewer = Bukkit.getPlayer(uuid);
            return viewer == null || !viewer.isOnline();
        });

        boolean sneaking = player.isSneaking();
        boolean sneakingChanged = supportCrouching && sneaking != cachedSneaking;
        boolean dirty;

        if (condensed) {
            setAllLocations(player.getLocation().setRotation(0, 0));
            Component newText = getJoinedText();
            boolean textChanged = !newText.equals(cachedText);
            dirty = textChanged || sneakingChanged;

            if (textChanged) {
                cachedText = newText;
                condensedDisplay.setText(newText);
            }
            if (sneakingChanged) {
                cachedSneaking = sneaking;
                condensedDisplay.setTextOpacity(sneaking ? OPACITY_CROUCHING : OPACITY_FULL);
            }
        } else {
            List<Component> resolvedLines = resolveLines();
            boolean textChanged = !resolvedLines.equals(cachedLineTexts);
            dirty = textChanged || sneakingChanged;

            if (textChanged) {
                cachedLineTexts = resolvedLines;
                applyLineTexts(resolvedLines);
            }
            if (sneakingChanged) {
                cachedSneaking = sneaking;
                byte opacity = sneaking ? OPACITY_CROUCHING : OPACITY_FULL;
                for (ClientTextDisplay display : lineDisplays) {
                    display.setTextOpacity(opacity);
                }
            }
            setAllLocations(player.getLocation().setRotation(0, 0));
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = shouldSee(viewer);
            boolean isVisible = this.viewers.contains(viewer.getUniqueId());
            boolean viewerDirty = dirty || hasOverride(viewer);

            if (shouldSee) {
                if (isVisible) {
                    if (viewerDirty) this.update(viewer);
                } else {
                    this.show(viewer);
                }
            } else {
                if (isVisible) this.hide(viewer);
            }
        }
    }

    private boolean shouldSee(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.isDead()) return false;
        if (hideSelf && player.getUniqueId().equals(viewer.getUniqueId())) return false;
        if (player.isDead() || player.getGameMode().equals(GameMode.SPECTATOR)) return false;
        if (!viewer.getWorld().getName().equals(player.getWorld().getName())) return false;
        if (player.isInvisible() || !viewer.canSee(player)) return false;

        return viewer.getLocation().distanceSquared(player.getLocation()) < visibilityDistance * visibilityDistance;
    }

    @Override
    public void show(Player viewer) {
        NametagHandler.hide(player, viewer);
        if (hideSelf && player.getUniqueId().equals(viewer.getUniqueId())) return;

        this.viewers.add(viewer.getUniqueId());

        cachedSneaking = player.isSneaking();
        byte opacity = supportCrouching && cachedSneaking ? OPACITY_CROUCHING : OPACITY_FULL;
        List<String> viewerLines = getLinesForViewer(viewer);

        if (condensed) {
            Component text = getJoinedText(viewerLines);
            if (!hasOverride(viewer)) cachedText = text;
            condensedDisplay.setText(text);
            condensedDisplay.setLocation(player.getLocation());
            condensedDisplay.setTextOpacity(opacity);

            List<PacketWrapper<?>> packets = new ArrayList<>(3);
            packets.add(condensedDisplay.createSpawnPacket());
            packets.add(condensedDisplay.createMetadataPacket());
            packets.add(condensedDisplay.createMountPacket(this.player));
            ClientEntity.sendBundle(viewer, packets);
        } else {
            List<Component> resolved = resolveLines(viewerLines);
            if (!hasOverride(viewer)) cachedLineTexts = resolved;
            applyLineTexts(resolved);
            setAllLocations(player.getLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(lineDisplays.size() * 2 + 1);
            for (ClientTextDisplay display : lineDisplays) {
                display.setTextOpacity(opacity);
                packets.add(display.createSpawnPacket());
                packets.add(display.createMetadataPacket());
            }
            packets.add(ClientEntity.createMountPacket(this.player, lineDisplays));
            ClientEntity.sendBundle(viewer, packets);
        }
    }

    @Override
    public void hide(Player viewer) {
        this.viewers.remove(viewer.getUniqueId());
        if (condensed) {
            condensedDisplay.despawn(viewer);
        } else {
            for (ClientTextDisplay display : lineDisplays) {
                display.despawn(viewer);
            }
        }
    }

    @Override
    public void update(Player viewer) {
        List<String> viewerLines = getLinesForViewer(viewer);

        if (condensed) {
            Component text = getJoinedText(viewerLines);
            condensedDisplay.setText(text);
            condensedDisplay.setLocation(player.getLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(2);
            packets.add(condensedDisplay.createMetadataPacket());
            packets.add(condensedDisplay.createMountPacket(this.player));
            ClientEntity.sendBundle(viewer, packets);
        } else {
            List<Component> resolved = resolveLines(viewerLines);
            applyLineTexts(resolved);
            setAllLocations(player.getLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(lineDisplays.size() + 1);
            for (ClientTextDisplay display : lineDisplays) {
                packets.add(display.createMetadataPacket());
            }
            packets.add(ClientEntity.createMountPacket(this.player, lineDisplays));
            ClientEntity.sendBundle(viewer, packets);
        }
    }

    private void setAllLocations(org.bukkit.Location location) {
        if (condensed) {
            condensedDisplay.setLocation(location);
        } else {
            for (ClientTextDisplay display : lineDisplays) {
                display.setLocation(location);
            }
        }
    }

    private void applyLineTexts(List<Component> resolvedLines) {
        // Count visible lines and assign translations so visible lines stack with no gaps
        List<Integer> visibleIndices = new ArrayList<>();
        for (int i = 0; i < resolvedLines.size(); i++) {
            if (resolvedLines.get(i) != null) {
                visibleIndices.add(i);
            }
        }

        int visibleCount = visibleIndices.size();
        for (int rank = 0; rank < visibleCount; rank++) {
            int idx = visibleIndices.get(rank);
            float y = BASE_Y_OFFSET + (visibleCount - 1 - rank) * LINE_SPACING;
            lineDisplays.get(idx).setTranslation(new Vector3f(0, y, 0));
            lineDisplays.get(idx).setText(resolvedLines.get(idx));
        }

        // Hide lines that resolved to blank
        for (int i = 0; i < resolvedLines.size(); i++) {
            if (resolvedLines.get(i) == null) {
                lineDisplays.get(i).setText(Component.empty());
                lineDisplays.get(i).setTranslation(new Vector3f(0, 0, 0));
            }
        }
    }

    private List<String> getLinesForViewer(Player viewer) {
        List<String> perViewer = viewerOverrides.get(viewer.getUniqueId());
        if (perViewer != null) return perViewer;
        if (globalOverride != null) return globalOverride;
        return lines;
    }

    private List<Component> resolveLines() {
        return resolveLines(lines);
    }

    private List<Component> resolveLines(List<String> linesToResolve) {
        List<Component> result = new ArrayList<>(linesToResolve.size());
        for (String line : linesToResolve) {
            String modified = line
                    .replace("{player}", player.getName())
                    .replace("{health}", String.valueOf(new DecimalFormat("#.##").format(player.getHealth())));
            if (DependencyHelper.isPlaceholderAPIEnabled()) {
                modified = PlaceholderAPI.setPlaceholders(player, modified);
            }

            Matcher matcher = CONDITION_PATTERN.matcher(modified);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String conditionValue = matcher.group(1).trim();
                String content = matcher.group(2);
                matcher.appendReplacement(sb, conditionValue.equalsIgnoreCase("true") ? Matcher.quoteReplacement(content) : "");
            }
            matcher.appendTail(sb);
            modified = sb.toString();

            Component component = ComponentUtils.format(modified);
            result.add(ComponentUtils.isBlank(component) ? null : component);
        }
        return result;
    }

    private Component getJoinedText() {
        return getJoinedText(lines);
    }

    private Component getJoinedText(List<String> linesToResolve) {
        List<Component> components = new ArrayList<>(linesToResolve.size());
        for (Component resolved : resolveLines(linesToResolve)) {
            if (resolved != null) components.add(resolved);
        }
        return ComponentUtils.join(components);
    }

    @Override
    public void setLines(List<String> lines) {
        this.globalOverride = List.copyOf(lines);
        updateVisibilityForAll();
    }

    @Override
    public void setLines(Player viewer, List<String> lines) {
        viewerOverrides.put(viewer.getUniqueId(), List.copyOf(lines));
        if (this.viewers.contains(viewer.getUniqueId())) {
            this.update(viewer);
        }
    }

    @Override
    public void release() {
        this.globalOverride = null;
        updateVisibilityForAll();
    }

    @Override
    public void release(Player viewer) {
        viewerOverrides.remove(viewer.getUniqueId());
        if (this.viewers.contains(viewer.getUniqueId())) {
            this.update(viewer);
        }
    }

    @Override
    public boolean hasOverride() {
        return globalOverride != null;
    }

    @Override
    public boolean hasOverride(Player viewer) {
        return viewerOverrides.containsKey(viewer.getUniqueId()) || globalOverride != null;
    }

    private int getBackground() {
        String background = plugin.config().getNametagConfig().getBackground();
        if (background == null || background.equalsIgnoreCase("default")) return -1;
        if (background.equalsIgnoreCase("transparent")) return 0;
        if (background.startsWith("#")) background = background.substring(1);

        Color color = Color.fromARGB((int) Long.parseLong(background, 16));
        if (background.length() == 6) color = color.setAlpha(255);
        return color.asARGB();
    }
}

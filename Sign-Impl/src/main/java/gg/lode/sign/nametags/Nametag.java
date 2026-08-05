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
import gg.lode.sign.utils.hooks.AmplifierHook;
import gg.lode.sign.utils.hooks.ItemsAdderHook;
import gg.lode.sign.utils.hooks.NexoHook;
import gg.lode.sign.utils.hooks.VoiceChatHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Nametag implements INametag {
    private static final Pattern CONDITION_PATTERN = Pattern.compile("<condition:'([^']+)'>(.+?)</condition>");
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0");
    private static final DecimalFormat HEALTH_VERBOSE_FORMAT = new DecimalFormat("0.0");
    // {health}, {health:verbose}, {health:verbose:<n>}
    private static final Pattern HEALTH_PATTERN = Pattern.compile("\\{health(?::verbose(?::(\\d+))?)?\\}");
    private static final byte OPACITY_FULL = -1;
    private static final byte OPACITY_CROUCHING = 64;
    private static final float BASE_Y_OFFSET = 0.25f;
    private static final float LINE_SPACING = 0.27f;

    private final Sign plugin;
    private final Player player;
    private final Set<UUID> viewers;
    private final Set<UUID> tracked; // viewers whose clients have the player entity (received SPAWN_ENTITY)
    // After a client-side world wipe (respawn/dimension change), the catch-up
    // pass must not blind-show until the packet path re-confirms the entity.
    private static final long CATCH_UP_SUPPRESS_MS = 3000;
    private final Map<UUID, Long> catchUpSuppressedUntil;

    private final List<String> lines;
    private final boolean hideSelf;
    private final boolean supportCrouching;
    private final boolean seeThrough;
    private final boolean condensed;
    private final int visibilityDistance;

    // Global line override
    private List<String> globalOverride;

    // Persistent hide — survives the scheduler's visibility passes
    private volatile boolean hidden;

    // Viewer whitelist — null means everyone may see the tag
    private volatile Set<UUID> viewerWhitelist;

    // Per-viewer line overrides
    private final Map<UUID, List<String>> viewerOverrides;

    // Per-viewer resolved text cache (for dirty checking)
    private final Map<UUID, List<Component>> viewerResolvedCache;
    private final Map<UUID, Component> viewerCondensedCache;

    // String-level caches — compared before expensive MiniMessage parsing
    private List<String> cachedResolvedStrings;
    private final Map<UUID, List<String>> viewerResolvedStringCache;

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
        this.viewers = ConcurrentHashMap.newKeySet();
        this.tracked = ConcurrentHashMap.newKeySet();
        this.viewerOverrides = new HashMap<>();
        this.viewerResolvedCache = new HashMap<>();
        this.viewerCondensedCache = new HashMap<>();
        this.viewerResolvedStringCache = new HashMap<>();
        this.catchUpSuppressedUntil = new ConcurrentHashMap<>();

        NametagConfig config = plugin.config().getNametagConfig();
        this.lines = config.getLines();
        this.hideSelf = config.shouldHideSelf();
        this.supportCrouching = config.supportsCrouching();
        this.seeThrough = config.isSeeThrough();
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
        ClientTextDisplay display = new ClientTextDisplay(getHeadLocation());
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
        for (UUID viewerUuid : new HashSet<>(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
                this.hide(viewer);
            }
        }
        viewers.clear();
    }

    @Override
    public void updateVisibilityForAll() {
        // Clean up offline players
        viewers.removeIf((uuid) -> {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer == null || !viewer.isOnline()) {
                viewerResolvedCache.remove(uuid);
                viewerCondensedCache.remove(uuid);
                viewerResolvedStringCache.remove(uuid);
                catchUpSuppressedUntil.remove(uuid);
                return true;
            }
            return false;
        });
        tracked.removeIf((uuid) -> {
            Player viewer = Bukkit.getPlayer(uuid);
            return viewer == null || !viewer.isOnline();
        });

        if (tracked.isEmpty() && viewers.isEmpty()) return;

        boolean sneaking = player.isSneaking();
        boolean sneakingChanged = supportCrouching && sneaking != cachedSneaking;

        // Phase 1: Cheap string-level dirty check — skip expensive MiniMessage parsing when unchanged
        List<String> resolvedStrings = resolveToStrings(lines);
        boolean textChanged = !resolvedStrings.equals(cachedResolvedStrings);
        boolean dirty = textChanged || sneakingChanged;

        if (condensed) {
            setAllLocations(getHeadLocation());
            if (textChanged) {
                cachedResolvedStrings = resolvedStrings;
                cachedText = joinComponents(parseComponents(resolvedStrings));
                condensedDisplay.setText(cachedText);
            }
            if (sneakingChanged) {
                cachedSneaking = sneaking;
                condensedDisplay.setTextOpacity(sneaking ? OPACITY_CROUCHING : OPACITY_FULL);
                condensedDisplay.setSeeThrough(sneaking ? false : seeThrough);
            }
        } else {
            if (textChanged) {
                cachedResolvedStrings = resolvedStrings;
                cachedLineTexts = parseComponents(resolvedStrings);
                applyLineTexts(cachedLineTexts);
            }
            if (sneakingChanged) {
                cachedSneaking = sneaking;
                byte opacity = sneaking ? OPACITY_CROUCHING : OPACITY_FULL;
                for (ClientTextDisplay display : lineDisplays) {
                    display.setTextOpacity(opacity);
                    display.setSeeThrough(sneaking ? false : seeThrough);
                }
            }
            setAllLocations(getHeadLocation());
        }

        // Iterate tracked viewers (clients that have the player entity).
        // Safe to show because tracked = entity exists on that client.
        for (UUID viewerUuid : new HashSet<>(tracked)) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer == null || !viewer.isOnline()) continue;

            boolean shouldSee = shouldSee(viewer);
            boolean isVisible = this.viewers.contains(viewerUuid);

            if (shouldSee && !isVisible) {
                this.show(viewer);
            } else if (!shouldSee && isVisible) {
                this.hide(viewer);
            } else if (isVisible) {
                // Per-viewer dirty check using string comparison (avoids expensive parsing)
                boolean viewerDirty;
                if (hasOverride(viewer)) {
                    List<String> viewerLines = getLinesForViewer(viewer);
                    List<String> viewerStrings = resolveToStrings(viewerLines);
                    List<String> cachedViewerStrings = viewerResolvedStringCache.get(viewerUuid);
                    boolean viewerTextChanged = !viewerStrings.equals(cachedViewerStrings);
                    viewerDirty = viewerTextChanged || sneakingChanged;

                    if (viewerTextChanged) {
                        viewerResolvedStringCache.put(viewerUuid, viewerStrings);
                        if (condensed) {
                            viewerCondensedCache.put(viewerUuid, joinComponents(parseComponents(viewerStrings)));
                        } else {
                            viewerResolvedCache.put(viewerUuid, parseComponents(viewerStrings));
                        }
                    }
                } else {
                    viewerDirty = dirty;
                }

                if (viewerDirty) {
                    if (hasOverride(viewer)) {
                        this.update(viewer);
                    } else {
                        sendMetadataUpdate(viewer);
                    }
                }
            }
        }

        // Hide any viewers not in tracked (entity was destroyed but hide was missed)
        for (UUID viewerUuid : new HashSet<>(viewers)) {
            if (!tracked.contains(viewerUuid)) {
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null && viewer.isOnline()) {
                    this.hide(viewer);
                }
            }
        }

        // Catch players in range but not tracked (missed SPAWN_ENTITY, re-entered vicinity)
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) continue;
            if (tracked.contains(viewer.getUniqueId())) continue;
            if (viewers.contains(viewer.getUniqueId())) continue;
            Long suppressedUntil = catchUpSuppressedUntil.get(viewer.getUniqueId());
            if (suppressedUntil != null) {
                if (System.currentTimeMillis() < suppressedUntil) continue;
                catchUpSuppressedUntil.remove(viewer.getUniqueId());
            }
            if (shouldSee(viewer)) {
                tracked.add(viewer.getUniqueId());
                this.show(viewer);
            }
        }
    }

    /**
     * Shows the nametag to all eligible players who don't already see it.
     * Only call when the player entity is guaranteed to exist on viewers' clients
     * (e.g., after initial creation, after respawn).
     */
    public void showToEligible() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldSee(viewer) && !viewers.contains(viewer.getUniqueId())) {
                tracked.add(viewer.getUniqueId());
                this.show(viewer);
            }
        }
    }

    @Override
    public void updateVisibilityFor(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;

        boolean shouldSee = shouldSee(viewer);
        boolean isVisible = this.viewers.contains(viewer.getUniqueId());

        if (shouldSee && !isVisible && tracked.contains(viewer.getUniqueId())) {
            this.show(viewer);
        } else if (!shouldSee && isVisible) {
            this.hide(viewer);
        }
    }

    private boolean shouldSee(Player viewer) {
        if (hidden) return false;
        if (viewer == null || !viewer.isOnline() || viewer.isDead()) return false;
        if (viewerWhitelist != null && !viewerWhitelist.contains(viewer.getUniqueId())) return false;
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
        if (this.viewers.contains(viewer.getUniqueId())) return;

        this.viewers.add(viewer.getUniqueId());

        cachedSneaking = player.isSneaking();
        boolean crouching = supportCrouching && cachedSneaking;
        byte opacity = crouching ? OPACITY_CROUCHING : OPACITY_FULL;
        List<String> viewerLines = getLinesForViewer(viewer);

        org.bukkit.Location headLoc = getHeadLocation();

        if (condensed) {
            Component text = getJoinedText(viewerLines);
            if (!hasOverride(viewer)) cachedText = text;
            condensedDisplay.setText(text);
            condensedDisplay.setLocation(headLoc);
            condensedDisplay.setTextOpacity(opacity);
            condensedDisplay.setSeeThrough(crouching ? false : seeThrough);

            List<PacketWrapper<?>> packets = new ArrayList<>(3);
            packets.add(condensedDisplay.createSpawnPacket());
            packets.add(condensedDisplay.createMetadataPacket());
            packets.add(condensedDisplay.createMountPacket(this.player));
            ClientEntity.sendBundle(viewer, packets);
        } else {
            List<Component> resolved = resolveLines(viewerLines);
            if (!hasOverride(viewer)) cachedLineTexts = resolved;
            applyLineTexts(resolved);
            setAllLocations(headLoc);

            List<PacketWrapper<?>> packets = new ArrayList<>(lineDisplays.size() * 2 + 1);
            for (ClientTextDisplay display : lineDisplays) {
                display.setTextOpacity(opacity);
                display.setSeeThrough(crouching ? false : seeThrough);
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

    /**
     * Re-sends the mount packet to a viewer without respawning the display entities.
     * Used to restore mounts after the client drops them (e.g., when the player is
     * mounted as a passenger on another entity via GSit or player riding).
     */
    public void remount(Player viewer) {
        if (!this.viewers.contains(viewer.getUniqueId())) return;

        if (condensed) {
            ClientEntity.sendBundle(viewer, List.of(condensedDisplay.createMountPacket(this.player)));
        } else {
            ClientEntity.sendBundle(viewer, List.of(ClientEntity.createMountPacket(this.player, lineDisplays)));
        }
    }

    /**
     * Re-sends mount packets to all current viewers.
     * Lightweight heartbeat to recover from client-side mount drops.
     */
    public void remountAll() {
        for (UUID viewerUuid : viewers) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
                remount(viewer);
            }
        }
    }

    @Override
    public void update(Player viewer) {
        List<String> viewerLines = getLinesForViewer(viewer);

        if (condensed) {
            Component text = getJoinedText(viewerLines);
            condensedDisplay.setText(text);
            condensedDisplay.setLocation(getHeadLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(2);
            packets.add(condensedDisplay.createMetadataPacket());
            packets.add(condensedDisplay.createMountPacket(this.player));
            ClientEntity.sendBundle(viewer, packets);
        } else {
            List<Component> resolved = resolveLines(viewerLines);
            applyLineTexts(resolved);
            setAllLocations(getHeadLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(lineDisplays.size() + 1);
            for (ClientTextDisplay display : lineDisplays) {
                packets.add(display.createMetadataPacket());
            }
            packets.add(ClientEntity.createMountPacket(this.player, lineDisplays));
            ClientEntity.sendBundle(viewer, packets);
        }
    }

    /**
     * Sends metadata and mount packets to a viewer using the current display state.
     * Unlike update(), this does NOT re-resolve text — used when the display already
     * has the correct text applied (e.g., global text set by updateVisibilityForAll).
     */
    private void sendMetadataUpdate(Player viewer) {
        if (condensed) {
            condensedDisplay.setLocation(getHeadLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(2);
            packets.add(condensedDisplay.createMetadataPacket());
            packets.add(condensedDisplay.createMountPacket(this.player));
            ClientEntity.sendBundle(viewer, packets);
        } else {
            setAllLocations(getHeadLocation());

            List<PacketWrapper<?>> packets = new ArrayList<>(lineDisplays.size() + 1);
            for (ClientTextDisplay display : lineDisplays) {
                packets.add(display.createMetadataPacket());
            }
            packets.add(ClientEntity.createMountPacket(this.player, lineDisplays));
            ClientEntity.sendBundle(viewer, packets);
        }
    }

    private void ensureDisplayCount(int needed) {
        if (lineDisplays == null || needed <= lineDisplays.size()) return;

        NametagConfig config = plugin.config().getNametagConfig();
        int background = getBackground();

        // Hide from all viewers before adding new displays
        Set<UUID> previousViewers = new HashSet<>(viewers);
        for (UUID viewerUuid : previousViewers) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
                hide(viewer);
            }
        }

        // Create additional displays
        for (int i = lineDisplays.size(); i < needed; i++) {
            float y = BASE_Y_OFFSET + (needed - 1 - i) * LINE_SPACING;
            lineDisplays.add(createDisplay(config, background, new Vector3f(0, y, 0)));
        }

        // Re-show to all previous viewers
        for (UUID viewerUuid : previousViewers) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline() && shouldSee(viewer)) {
                show(viewer);
            }
        }
    }

    private org.bukkit.Location getHeadLocation() {
        org.bukkit.Location loc = player.getLocation().clone();
        loc.setYaw(0);
        loc.setPitch(0);
        loc.add(0, player.getHeight(), 0);
        return loc;
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
        int displayCount = lineDisplays.size();
        int lineCount = Math.min(resolvedLines.size(), displayCount);

        // Count visible lines and assign translations so visible lines stack with no gaps
        List<Integer> visibleIndices = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
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

        // Hide lines that resolved to blank or beyond the override's line count
        for (int i = 0; i < displayCount; i++) {
            if (i >= lineCount || resolvedLines.get(i) == null) {
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

    /**
     * Resolves the {health} placeholder family:
     *   {health}            -> integer health (20)
     *   {health:verbose}    -> one decimal place (20.0)
     *   {health:verbose:<n>} -> one decimal when health <= n, else integer
     */
    private String resolveHealth(String line) {
        if (line.indexOf("{health") < 0) return line;
        double health = player.getHealth();
        Matcher matcher = HEALTH_PATTERN.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            boolean verbose = matcher.group(0).contains(":verbose");
            String threshold = matcher.group(1);
            String replacement;
            if (!verbose) {
                replacement = HEALTH_FORMAT.format(health);
            } else if (threshold == null) {
                replacement = HEALTH_VERBOSE_FORMAT.format(health);
            } else {
                replacement = health <= Double.parseDouble(threshold)
                        ? HEALTH_VERBOSE_FORMAT.format(health)
                        : HEALTH_FORMAT.format(health);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveVoiceIcon() {
        NametagConfig config = plugin.config().getNametagConfig();
        if (!config.isVoiceChatEnabled() || !DependencyHelper.isSimpleVoiceChatEnabled()) return "";

        VoiceChatHook.VoiceState state = AmplifierHook.isActive()
                ? AmplifierHook.getState(player.getUniqueId())
                : VoiceChatHook.getState(player.getUniqueId());

        return switch (state) {
            case SPEAKING -> config.getVoiceIconSpeaking();
            case IDLE -> config.getVoiceIconIdle();
            case DEAFENED -> config.getVoiceIconDeafened();
            case DISCONNECTED -> config.getVoiceIconDisconnected();
        };
    }

    /**
     * Resolves placeholders and conditions to raw strings (cheap).
     * Does NOT call ComponentUtils.format() — use parseComponents() for that.
     */
    private List<String> resolveToStrings(List<String> linesToResolve) {
        List<String> result = new ArrayList<>(linesToResolve.size());
        for (String line : linesToResolve) {
            String modified = resolveHealth(line
                    .replace("{player}", player.getName())
                    .replace("{voice}", resolveVoiceIcon()));
            if (DependencyHelper.isPlaceholderAPIEnabled()) {
                modified = resolvePlaceholdersRecursively(modified);
            }

            Matcher matcher = CONDITION_PATTERN.matcher(modified);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String conditionValue = matcher.group(1).trim();
                String content = matcher.group(2);
                matcher.appendReplacement(sb, conditionValue.equalsIgnoreCase("true") ? Matcher.quoteReplacement(content) : "");
            }
            matcher.appendTail(sb);
            result.add(sb.toString());
        }
        return result;
    }

    /**
     * Resolves PlaceholderAPI placeholders repeatedly so that a placeholder whose
     * output contains another placeholder (e.g. %luckperms_prefix% expanding to a
     * value that itself holds %another_papi%) is fully expanded.
     * <p>
     * Loops until the output stops changing or the configured
     * {@code nametags.display.placeholder-depth} is reached, guarding against
     * self-referential placeholders that never resolve.
     */
    private String resolvePlaceholdersRecursively(String input) {
        String current = input;
        int maxDepth = plugin.config().getNametagConfig().getPlaceholderDepth();
        for (int depth = 0; depth < maxDepth; depth++) {
            // Brackets first: PlaceholderAPI treats {placeholder} and %placeholder%
            // as two separate syntaxes with a call each, and a bracket is normally
            // written *inside* a percent placeholder's arguments — as in
            // %math0:CEILING{player_health_rounded}%. Resolving percent first would
            // hand the expansion an argument that still says "{player_health}".
            String resolved = PlaceholderAPI.setBracketPlaceholders(player, current);
            resolved = PlaceholderAPI.setPlaceholders(player, resolved);
            if (resolved.equals(current)) break;
            current = resolved;
        }
        return current;
    }

    /**
     * Parses resolved strings into Components via MiniMessage (expensive).
     * Only call when the resolved strings have actually changed.
     */
    private List<Component> parseComponents(List<String> resolvedStrings) {
        List<Component> result = new ArrayList<>(resolvedStrings.size());
        for (String s : resolvedStrings) {
            Component component = toComponent(s);
            result.add(ComponentUtils.isBlank(component) ? null : component);
        }
        return result;
    }

    /**
     * Renders the final resolved line (after PlaceholderAPI recursion) to a
     * Component. Legacy color/style codes using either {@code &} or {@code §}
     * are translated to MiniMessage first; literals such as {@code $} are not
     * color codes and pass through untouched. When Nexo is installed, its
     * {@code <glyph:id>} tags are resolved via the glyph resolver, with the
     * nametag owner supplied as the MiniMessage target so Nexo gates
     * permission-restricted glyphs by the owner's permissions. Finally, when
     * ItemsAdder is installed, its {@code :emoji:} font images are resolved on
     * the rendered component (owner-gated).
     */
    private Component toComponent(String input) {
        String converted = ComponentUtils.convertAmpersandToMiniMessage(input);
        TagResolver glyphResolver = NexoHook.glyphResolver();
        Component component = glyphResolver != null
                ? MiniMessage.miniMessage().deserialize(converted, player, glyphResolver)
                : MiniMessage.miniMessage().deserialize(converted);
        return ItemsAdderHook.replaceEmotes(player, component);
    }

    private List<Component> resolveLines(List<String> linesToResolve) {
        return parseComponents(resolveToStrings(linesToResolve));
    }

    private Component joinComponents(List<Component> components) {
        List<Component> nonNull = new ArrayList<>();
        for (Component c : components) {
            if (c != null) nonNull.add(c);
        }
        return ComponentUtils.join(nonNull);
    }

    private Component getJoinedText() {
        return getJoinedText(lines);
    }

    private Component getJoinedText(List<String> linesToResolve) {
        return joinComponents(resolveLines(linesToResolve));
    }

    @Override
    public void setLines(List<String> lines) {
        this.globalOverride = List.copyOf(lines);
        this.cachedResolvedStrings = null; // Invalidate string cache
        if (!condensed) ensureDisplayCount(lines.size());
        updateVisibilityForAll();
    }

    @Override
    public void setLines(Player viewer, List<String> lines) {
        List<String> previousLines = viewerOverrides.containsKey(viewer.getUniqueId())
                ? viewerOverrides.get(viewer.getUniqueId()) : getLinesForViewer(viewer);
        viewerOverrides.put(viewer.getUniqueId(), List.copyOf(lines));
        viewerResolvedStringCache.remove(viewer.getUniqueId()); // Invalidate viewer string cache
        if (!condensed) ensureDisplayCount(lines.size());
        if (this.viewers.contains(viewer.getUniqueId())) {
            boolean lineCountChanged = !condensed && previousLines.size() != lines.size();
            if (lineCountChanged) {
                this.hide(viewer);
                this.show(viewer);
            } else {
                this.update(viewer);
            }
        }
    }

    @Override
    public void release() {
        this.globalOverride = null;
        this.cachedResolvedStrings = null; // Invalidate string cache
        updateVisibilityForAll();
    }

    @Override
    public void release(Player viewer) {
        List<String> previousOverride = viewerOverrides.remove(viewer.getUniqueId());
        viewerResolvedCache.remove(viewer.getUniqueId());
        viewerCondensedCache.remove(viewer.getUniqueId());
        viewerResolvedStringCache.remove(viewer.getUniqueId());
        if (this.viewers.contains(viewer.getUniqueId())) {
            List<String> currentLines = getLinesForViewer(viewer);
            boolean lineCountChanged = !condensed && previousOverride != null && previousOverride.size() != currentLines.size();
            if (lineCountChanged) {
                this.hide(viewer);
                this.show(viewer);
            } else {
                this.update(viewer);
            }
        }
    }

    @Override
    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        if (hidden) {
            hideForAll();
        } else {
            updateVisibilityForAll();
        }
    }

    @Override
    public boolean isHidden() {
        return hidden;
    }

    @Override
    public void setViewers(Collection<Player> allowedViewers) {
        if (allowedViewers == null) {
            clearViewers();
            return;
        }
        Set<UUID> whitelist = ConcurrentHashMap.newKeySet();
        for (Player allowed : allowedViewers) {
            if (allowed != null) whitelist.add(allowed.getUniqueId());
        }
        this.viewerWhitelist = whitelist;
        // Hides now-excluded viewers and shows newly-allowed tracked ones.
        updateVisibilityForAll();
    }

    @Override
    public void clearViewers() {
        if (this.viewerWhitelist == null) return;
        this.viewerWhitelist = null;
        updateVisibilityForAll();
    }

    @Override
    public boolean hasViewerWhitelist() {
        return viewerWhitelist != null;
    }

    @Override
    public boolean hasOverride() {
        return globalOverride != null;
    }

    @Override
    public boolean hasOverride(Player viewer) {
        return viewerOverrides.containsKey(viewer.getUniqueId()) || globalOverride != null;
    }

    public int[] getDisplayEntityIds() {
        if (condensed) {
            return new int[]{condensedDisplay.getEntityId()};
        } else {
            int[] ids = new int[lineDisplays.size()];
            for (int i = 0; i < lineDisplays.size(); i++) {
                ids[i] = lineDisplays.get(i).getEntityId();
            }
            return ids;
        }
    }

    public boolean isVisibleTo(Player viewer) {
        return viewers.contains(viewer.getUniqueId());
    }

    /**
     * Mark that a viewer's client has the player entity (received SPAWN_ENTITY).
     * Called by the packet listener when SPAWN_ENTITY is intercepted.
     */
    public void markTracked(UUID viewerUuid) {
        catchUpSuppressedUntil.remove(viewerUuid);
        tracked.add(viewerUuid);
    }

    /**
     * Forget everything about a viewer whose client just wiped its world
     * (RESPAWN / dimension change sends no DESTROY_ENTITIES, so the tracked
     * and visible state is silently stale). No packets are sent — the client
     * already dropped the displays. The catch-up pass is suppressed briefly
     * so the SPAWN_ENTITY packet path re-shows at the right moment instead
     * of a blind show racing the client's world load (early mount packets).
     */
    public void forgetViewer(UUID viewerUuid) {
        viewers.remove(viewerUuid);
        tracked.remove(viewerUuid);
        viewerResolvedCache.remove(viewerUuid);
        viewerCondensedCache.remove(viewerUuid);
        viewerResolvedStringCache.remove(viewerUuid);
        catchUpSuppressedUntil.put(viewerUuid, System.currentTimeMillis() + CATCH_UP_SUPPRESS_MS);
    }

    /**
     * Mark that a viewer's client no longer has the player entity (received DESTROY_ENTITIES).
     * Called by the packet listener when DESTROY_ENTITIES is intercepted.
     */
    public void markUntracked(UUID viewerUuid) {
        tracked.remove(viewerUuid);
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

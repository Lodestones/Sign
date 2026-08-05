package gg.lode.sign.utils.hooks;

import gg.lode.frameapi.FrameAPI;
import gg.lode.frameapi.IFrameAPI;
import gg.lode.frameapi.api.FramePriority;
import gg.lode.frameapi.api.nametag.NameTagStyle;
import gg.lode.frameapi.api.nametag.NameVisibility;
import gg.lode.frameapi.api.provider.NametagProvider;
import gg.lode.sign.Sign;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tells Frame to keep the vanilla nametag hidden for anyone Sign is drawing.
 * <p>
 * Both plugins express "hide this nametag" through scoreboard teams, and a
 * player can only be on one team at a time. Sign sends a client-side team with
 * {@code NEVER}; Frame puts every styled player on a real team of its own. Left
 * alone the two overwrite each other — Sign hides the tag, Frame's next refresh
 * puts the player back on its team and the tag reappears, taking Sign's hide
 * (and Frame's own colour and prefix) with it.
 * <p>
 * Registering as a provider ends the fight instead of trying to win it: Frame
 * asks on every refresh, hears {@code NEVER}, and writes that onto the team it
 * was going to write anyway. This is the same arrangement Sign already has with
 * TAB, which owns nametags outright when it is installed.
 */
public class FrameHook implements NametagProvider {

    private static boolean active;
    private static FrameHook instance;

    public static void register(Sign plugin) {
        IFrameAPI api = FrameAPI.get();
        if (api == null) {
            plugin.getLogger().warning("Frame is installed but the API is unavailable.");
            return;
        }
        instance = new FrameHook();
        api.register(plugin.host(), instance);
        active = true;
        plugin.getLogger().info("Hooked into Frame!");
    }

    public static void unregister() {
        if (instance != null) {
            IFrameAPI api = FrameAPI.get();
            if (api != null) api.unregister(instance);
            instance = null;
        }
        active = false;
    }

    /** Whether Frame is carrying the hide, so Sign should not send team packets itself. */
    public static boolean isActive() {
        return active;
    }

    @Override
    public int priority() {
        // Above the config layer and ordinary feature plugins: a nametag Sign
        // has replaced with its own must not be un-hidden by a rank prefix.
        return FramePriority.HIGH;
    }

    @Override
    public @Nullable NameTagStyle style(@NotNull Player viewer, @NotNull Player target) {
        if (Sign.getInstance() == null) return null;
        if (!Sign.getInstance().config().getNametagConfig().isEnabled()) return null;
        // Only for players Sign is actually drawing. Anyone it isn't keeps their
        // vanilla nametag, and Frame keeps whatever opinion it had about them.
        if (Sign.getInstance().getNametagManager().get(target) == null) return null;

        // Visibility only — prefix, suffix and colour stay Frame's business, so
        // a rank prefix still applies if the tag is ever shown again.
        return NameTagStyle.builder().visibility(NameVisibility.NEVER).build();
    }
}

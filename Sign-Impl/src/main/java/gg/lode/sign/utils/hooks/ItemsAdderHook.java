package gg.lode.sign.utils.hooks;

import gg.lode.sign.utils.helpers.DependencyHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Soft hook into ItemsAdder for resolving {@code :emoji:} font-image
 * placeholders in nametag lines.
 * <p>
 * Like {@link NexoHook}, ItemsAdder is not declared as a dependency in the
 * loader's paper-plugin.yml, so its classes are loaded reflectively from
 * ItemsAdder's own plugin classloader to avoid forcing a loader-jar redrop.
 * Uses {@code FontImageWrapper.replaceFontImages(Permissible, Component)} so
 * permission-restricted emojis are gated by the nametag owner.
 */
public final class ItemsAdderHook {
    private static final String FONT_IMAGE_CLASS = "dev.lone.itemsadder.api.FontImages.FontImageWrapper";

    private static boolean resolved;
    private static Method replaceMethod;

    private ItemsAdderHook() {
    }

    /**
     * Replaces ItemsAdder {@code :emoji:} font-image placeholders in the given
     * component, gated by the player's permissions. Returns the input unchanged
     * when ItemsAdder is absent or its API is unavailable.
     */
    public static Component replaceEmotes(Player player, Component input) {
        Method method = method();
        if (method == null) return input;
        try {
            return (Component) method.invoke(null, player, input);
        } catch (Throwable t) {
            return input;
        }
    }

    private static Method method() {
        if (resolved) return replaceMethod;
        resolved = true;
        if (!DependencyHelper.isItemsAdderEnabled()) return null;
        try {
            Plugin itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            if (itemsAdder == null) return null;
            Class<?> fontImage = Class.forName(FONT_IMAGE_CLASS, true, itemsAdder.getClass().getClassLoader());
            replaceMethod = fontImage.getMethod("replaceFontImages", Permissible.class, Component.class);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Sign] ItemsAdder is installed but its font-image API could not be loaded; :emoji: placeholders will not resolve.");
        }
        return replaceMethod;
    }
}

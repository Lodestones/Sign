package gg.lode.sign.utils.hooks;

import gg.lode.sign.utils.helpers.DependencyHelper;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Soft hook into Nexo for resolving {@code <glyph:id>} MiniMessage tags in
 * nametag lines.
 * <p>
 * Nexo is NOT declared as a dependency in the loader's paper-plugin.yml, so
 * Paper's classloader isolation prevents Sign from referencing Nexo classes
 * directly (a direct reference throws {@link NoClassDefFoundError} at runtime
 * even when Nexo is installed). To avoid forcing a loader-jar redrop just to
 * add a soft-depend, this hook loads {@code GlyphTag} reflectively from Nexo's
 * own plugin classloader. The returned {@link TagResolver} is a net.kyori type
 * shared across plugins, so it is safe to use directly.
 */
public final class NexoHook {
    private static final String GLYPH_TAG_CLASS = "com.nexomc.nexo.glyphs.GlyphTag";

    private static boolean resolved;
    private static TagResolver cachedResolver;

    private NexoHook() {
    }

    /**
     * Returns Nexo's glyph tag resolver, or {@code null} when Nexo is absent or
     * its API is unavailable. Resolved once and cached — Nexo's resolver is a
     * stable singleton.
     */
    public static TagResolver glyphResolver() {
        if (resolved) return cachedResolver;
        resolved = true;
        if (!DependencyHelper.isNexoEnabled()) return null;
        try {
            Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
            if (nexo == null) return null;
            Class<?> glyphTag = Class.forName(GLYPH_TAG_CLASS, true, nexo.getClass().getClassLoader());
            Object instance = glyphTag.getField("INSTANCE").get(null);
            cachedResolver = (TagResolver) glyphTag.getMethod("getRESOLVER").invoke(instance);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Sign] Nexo is installed but its glyph API could not be loaded; <glyph:..> tags will not resolve.");
        }
        return cachedResolver;
    }
}

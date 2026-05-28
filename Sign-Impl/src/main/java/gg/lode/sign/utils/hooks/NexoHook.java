package gg.lode.sign.utils.hooks;

import com.nexomc.nexo.glyphs.GlyphTag;
import gg.lode.sign.utils.helpers.DependencyHelper;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Soft hook into Nexo for resolving {@code <glyph:id>} MiniMessage tags in
 * nametag lines. Every Nexo class reference sits behind
 * {@link DependencyHelper#isNexoEnabled()}, so the {@link GlyphTag} class is
 * only loaded when Nexo is actually present on the server.
 */
public final class NexoHook {
    private NexoHook() {
    }

    /**
     * Returns Nexo's glyph tag resolver, or {@code null} when Nexo is not
     * installed. The resolver pulls any player from the MiniMessage context to
     * honor permission-gated glyphs.
     */
    public static TagResolver glyphResolver() {
        if (!DependencyHelper.isNexoEnabled()) return null;
        return GlyphTag.INSTANCE.getRESOLVER();
    }
}

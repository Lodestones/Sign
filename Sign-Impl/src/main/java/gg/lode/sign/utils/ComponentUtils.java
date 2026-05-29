package gg.lode.sign.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;

public class ComponentUtils {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final JoinConfiguration joinConfig = JoinConfiguration.separator(Component.newline());

    public static Component format(String input) {
        String sanitized = input.indexOf('§') != -1
                ? input.replace('§', '&')
                : input;
        Component legacy = LEGACY_SERIALIZER.deserialize(sanitized);
        String modern = MINI_MESSAGE.serialize(legacy).replace("\\", "");
        return MINI_MESSAGE.deserialize(modern);
    }

    /**
     * Deserializes a MiniMessage string after translating legacy {@code &} and
     * {@code §} color/style codes to MiniMessage tags. Bundled (net.kyori only)
     * so it works at runtime without any external API on the classpath.
     */
    public static Component miniMessage(String input) {
        return MINI_MESSAGE.deserialize(convertAmpersandToMiniMessage(input));
    }

    /**
     * String-level translation of legacy {@code &}/{@code §} color and style
     * codes into MiniMessage tags. Does not handle legacy hex codes.
     */
    public static String convertAmpersandToMiniMessage(String input) {
        return input
                .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("§0", "<black>").replace("§1", "<dark_blue>").replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>").replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
                .replace("§6", "<gold>").replace("§7", "<gray>").replace("§8", "<dark_gray>")
                .replace("§9", "<blue>").replace("§a", "<green>").replace("§b", "<aqua>")
                .replace("§c", "<red>").replace("§d", "<light_purple>").replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("&l", "<bold>").replace("&o", "<italic>").replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>").replace("&k", "<obfuscated>")
                .replace("§l", "<bold>").replace("§o", "<italic>").replace("§n", "<underlined>")
                .replace("§m", "<strikethrough>").replace("§k", "<obfuscated>")
                .replace("&r", "<reset>").replace("§r", "<reset>");
    }

    public static boolean isBlank(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).trim().isEmpty();
    }

    public static Component join(List<Component> components) {
        return Component.join(joinConfig, components);
    }
}
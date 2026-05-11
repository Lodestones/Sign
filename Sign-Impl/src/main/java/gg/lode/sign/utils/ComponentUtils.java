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

    public static boolean isBlank(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).trim().isEmpty();
    }

    public static Component join(List<Component> components) {
        return Component.join(joinConfig, components);
    }
}
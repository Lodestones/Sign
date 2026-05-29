package gg.lode.sign.utils.helpers;

import gg.lode.sign.utils.ComponentUtils;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Sends command feedback in a plain, vanilla-style format: yellow text with no
 * icons, brackets, gradients, or other decoration. All severities (success,
 * warning, error, info) render identically as yellow text so output reads like
 * vanilla Minecraft command responses.
 */
public class MessageHelper {
    private static final String COLOR = "<yellow>";

    public static void success(CommandSender sender, String message) {
        send(sender, message);
    }

    public static void warning(CommandSender sender, String message) {
        send(sender, message);
    }

    public static void error(CommandSender sender, String message) {
        send(sender, message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(ComponentUtils.format(COLOR + message));
    }

    public static void send(CommandSender sender, List<String> messages) {
        for (String message : messages) {
            send(sender, message);
        }
    }
}

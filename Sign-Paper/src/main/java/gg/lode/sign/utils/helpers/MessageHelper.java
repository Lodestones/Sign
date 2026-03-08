package gg.lode.sign.utils.helpers;

import gg.lode.sign.utils.ComponentUtils;
import org.bukkit.command.CommandSender;

import java.util.List;

public class MessageHelper {
    static String SUCCESS = "<#7EFF00>";
    static String WARN = "<#FFFF00>";
    static String DANGER = "<#FF0000>";

    public static void success(CommandSender sender, String message) {
        send(sender, format("{success} <reset>{success_color}" + message));
    }

    public static void warning(CommandSender sender, String message) {
        send(sender, format("{warn} <reset>{warn_color}" + message));
    }

    public static void error(CommandSender sender, String message) {
        send(sender, format("{danger} <reset>{danger_color}" + message));
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(
                ComponentUtils.format(format(message))
        );
    }

    public static void send(CommandSender sender, List<String> messages) {
        for (String message : messages) {
            send(sender, message);
        }
    }

    private static String format(String input) {
        return input
                .replace("{success}", "{start}{success_color}✔{end}")
                .replace("{warn}", "{start}{warn_color}⚠{end}")
                .replace("{danger}", "{start}{danger_color}❌{end}")
                .replace("{success_color}", SUCCESS)
                .replace("{warn_color}", WARN)
                .replace("{danger_color}", DANGER)
                .replace("{start}", "<dark_gray>[")
                .replace("{end}", "<dark_gray>]");
    }
}

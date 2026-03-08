package gg.lode.sign.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandExecutor;
import gg.lode.sign.Sign;
import gg.lode.sign.config.NametagConfig;
import gg.lode.sign.entities.DisplayBillboard;
import gg.lode.sign.entities.TextAlignment;
import gg.lode.sign.utils.helpers.MessageHelper;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SignCommand extends CommandAPICommand {
    public SignCommand(Sign plugin) {
        super("sign");

        withSubcommand(new CommandAPICommand("version")
                .executes((sender, args) -> {
                    MessageHelper.send(sender, "This server is running <yellow>Sign v" + plugin.getPluginMeta().getVersion() + "<white>!");
                    MessageHelper.send(sender, "Run <yellow>'/sign help' <white>for a full list of commands.");
                }));

        withSubcommand(new CommandAPICommand("reload")
                .withPermission("lodestone.sign.admin")
                .executes((sender, args) -> {
                    boolean success = plugin.reloadPlugin();
                    if (success) {
                        MessageHelper.success(sender, "Successfully reloaded!");
                    } else {
                        MessageHelper.error(sender, "Failed to reload the plugin, please check server logs!");
                    }
                })
        );

        withSubcommand(new CommandAPICommand("config")
                .withPermission("lodestone.sign.admin")
                .executes((sender, args) -> {
                    NametagConfig config = plugin.config().getNametagConfig();
                    boolean enabled = config.isEnabled();
                    boolean hideSelf = config.shouldHideSelf();
                    int updateInterval = config.getUpdateInterval();
                    int visibilityDistance = config.getVisibilityDistance();
                    List<String> lines = config.getLines();
                    boolean textShadow = config.hasTextShadow();
                    boolean seeThrough = config.isSeeThrough();
                    TextAlignment textAlignment = config.getTextAlignment();
                    String background = config.getBackground();
                    DisplayBillboard billboard = config.getBillboard();
                    Vector scale = config.getScale();
                    List<String> scaleText = new ArrayList<>();
                    scaleText.add("<white>X <dark_gray>→ <gray>" + scale.getX());
                    scaleText.add("<white>Y <dark_gray>→ <gray>" + scale.getY());
                    scaleText.add("<white>Z <dark_gray>→ <gray>" + scale.getZ());

                    List<String> messages = new ArrayList<>();
                    messages.add("<dark_gray>• <white>Nametags");
                    messages.add("  <white>Enabled <dark_gray>→ " + booleanToString(enabled));
                    messages.add("  <white>Show Self <dark_gray>→ " + booleanToString(!hideSelf));
                    messages.add("  <white>Update Interval <dark_gray>→ <gray>" + updateInterval + " ticks");
                    messages.add("  <white>Visibility Distance <dark_gray>→ <gray>" + visibilityDistance + " blocks");
                    messages.add("<dark_gray>• <white>Display");
                    messages.add("  <white>Lines <dark_gray>→ <hover:show_text:'{lines}'><gray><u>Hover".replace("{lines}", String.join("\n", lines)));
                    messages.add("  <white>Text Shadow <dark_gray>→ " + booleanToString(textShadow));
                    messages.add("  <white>See Through <dark_gray>→ " + booleanToString(seeThrough));
                    messages.add("  <white>Support Crouching <dark_gray>→ " + booleanToString(config.supportsCrouching()));
                    messages.add("  <white>Condense Holograms <dark_gray>→ " + booleanToString(config.isCondenseHolograms()));
                    messages.add("  <white>Text Alignment <dark_gray>→ <gray>" + textAlignment.name());
                    messages.add("  <white>Background <dark_gray>→ " + color(background) + background(background));
                    messages.add("  <white>Billboard <dark_gray>→ <gray>" + billboard.name());
                    messages.add("  <white>Scale <dark_gray>→ <hover:show_text:'{scale}'><gray><u>Hover".replace("{scale}", String.join("\n", scaleText)));

                    sender.sendMessage("");
                    MessageHelper.send(sender, messages);
                    sender.sendMessage("");
                })
        );
    }

    private static String booleanToString(boolean b) {
        return b ? "<green>Yes" : "<red>No";
    }

    private static String background(String background) {
        if (Objects.equals(background, "default")) return "Default";
        if (Objects.equals(background, "Transparent")) return "Transparent";
        return background;
    }

    private static String color(String hex) {
        if (Objects.equals(hex, "default")) return "<gray>";
        if (Objects.equals(hex, "transparent")) return "<white>";
        return "<" + hex + ">";
    }
}

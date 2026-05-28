package gg.lode.sign.commands;

import dev.jorel.commandapi.CommandAPICommand;
import gg.lode.sign.Sign;
import gg.lode.sign.config.NametagConfig;
import gg.lode.sign.utils.helpers.MessageHelper;
import org.bukkit.util.Vector;
import gg.lode.bookshelfapi.api.util.MiniMessageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SignCommand extends CommandAPICommand {
    public SignCommand(Sign plugin) {
        super("sign");

        withSubcommand(new CommandAPICommand("version")
                .executes((sender, args) -> {
                    sender.sendMessage(MiniMessageHelper.deserialize(" "));
                    sender.sendMessage(MiniMessageHelper.deserialize("  <yellow><bold>Sign"));
                    sender.sendMessage(MiniMessageHelper.deserialize("  You are running <yellow>Sign " + Sign.VERSION));
                    sender.sendMessage(MiniMessageHelper.deserialize("  Using loader version <yellow>v" + plugin.host().getDescription().getVersion()));
                    sender.sendMessage(MiniMessageHelper.deserialize(" "));
                    sender.sendMessage(MiniMessageHelper.deserialize("  You can download <yellow>Sign<reset> over at <gold>Lodestone<reset>!"));
                    sender.sendMessage(MiniMessageHelper.deserialize("  <gold><underlined><click:open_url:'https://lode.gg/plugin/sign'>https://lode.gg/plugin/sign"));
                    sender.sendMessage(MiniMessageHelper.deserialize(" "));
                }));

        withSubcommand(new CommandAPICommand("reload")
                .withPermission("lodestone.sign.admin")
                .executes((sender, args) -> {
                    boolean success = plugin.reloadPlugin();
                    if (success) {
                        MessageHelper.send(sender, "Reloaded Sign configuration.");
                    } else {
                        MessageHelper.send(sender, "Failed to reload Sign, check the server logs.");
                    }
                })
        );

        withSubcommand(new CommandAPICommand("config")
                .withPermission("lodestone.sign.admin")
                .executes((sender, args) -> {
                    NametagConfig config = plugin.config().getNametagConfig();
                    Vector scale = config.getScale();

                    List<String> messages = new ArrayList<>();
                    messages.add("Nametags:");
                    messages.add("  Enabled: " + config.isEnabled());
                    messages.add("  Show Self: " + !config.shouldHideSelf());
                    messages.add("  Update Interval: " + config.getUpdateInterval() + " ticks");
                    messages.add("  Visibility Distance: " + config.getVisibilityDistance() + " blocks");
                    messages.add("Display:");
                    messages.add("  Lines: " + String.join(", ", config.getLines()));
                    messages.add("  Text Shadow: " + config.hasTextShadow());
                    messages.add("  See Through: " + config.isSeeThrough());
                    messages.add("  Support Crouching: " + config.supportsCrouching());
                    messages.add("  Condense Holograms: " + config.isCondenseHolograms());
                    messages.add("  Text Alignment: " + config.getTextAlignment().name());
                    messages.add("  Background: " + background(config.getBackground()));
                    messages.add("  Billboard: " + config.getBillboard().name());
                    messages.add("  Placeholder Depth: " + config.getPlaceholderDepth());
                    messages.add("  Scale: " + scale.getX() + ", " + scale.getY() + ", " + scale.getZ());
                    messages.add("Voice Chat:");
                    messages.add("  Enabled: " + config.isVoiceChatEnabled());
                    if (config.isVoiceChatEnabled()) {
                        messages.add("  Speaking Icon: " + config.getVoiceIconSpeaking());
                        messages.add("  Idle Icon: " + config.getVoiceIconIdle());
                        messages.add("  Deafened Icon: " + config.getVoiceIconDeafened());
                        messages.add("  Disconnected Icon: " + config.getVoiceIconDisconnected());
                    }

                    MessageHelper.send(sender, messages);
                })
        );
    }

    private static String background(String background) {
        if (Objects.equals(background, "default")) return "Default";
        if (Objects.equals(background, "transparent")) return "Transparent";
        return background;
    }
}

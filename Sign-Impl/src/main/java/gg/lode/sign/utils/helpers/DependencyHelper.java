package gg.lode.sign.utils.helpers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

public class DependencyHelper {
    static boolean enabledPlaceholderAPI;
    static boolean enabledTAB;
    static boolean enabledSimpleVoiceChat;
    static boolean enabledAmplifier;
    static boolean enabledNexo;
    static boolean enabledItemsAdder;
    static boolean enabledFrame;

    public static void load() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) enabledPlaceholderAPI = true;
        if (pluginManager.isPluginEnabled("TAB")) enabledTAB = true;
        if (pluginManager.isPluginEnabled("voicechat")) enabledSimpleVoiceChat = true;
        if (pluginManager.isPluginEnabled("Amplifier")) enabledAmplifier = true;
        if (pluginManager.isPluginEnabled("Nexo")) enabledNexo = true;
        if (pluginManager.isPluginEnabled("ItemsAdder")) enabledItemsAdder = true;
        if (pluginManager.isPluginEnabled("Frame")) enabledFrame = true;
    }

    public static boolean isPlaceholderAPIEnabled() {
        return enabledPlaceholderAPI;
    }

    public static boolean isTABEnabled() {
        return enabledTAB;
    }

    public static boolean isFrameEnabled() {
        return enabledFrame;
    }

    public static boolean isSimpleVoiceChatEnabled() {
        return enabledSimpleVoiceChat;
    }

    public static boolean isAmplifierEnabled() {
        return enabledAmplifier;
    }

    public static boolean isNexoEnabled() {
        return enabledNexo;
    }

    public static boolean isItemsAdderEnabled() {
        return enabledItemsAdder;
    }
}

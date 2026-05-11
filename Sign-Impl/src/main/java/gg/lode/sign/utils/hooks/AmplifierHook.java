package gg.lode.sign.utils.hooks;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import gg.lode.amplifierapi.AmplifierAPI;
import gg.lode.amplifierapi.IAmplifierAPI;
import gg.lode.amplifierapi.api.data.IVoicePlayer;
import gg.lode.sign.Sign;

import java.util.UUID;

public class AmplifierHook {
    private static boolean active;

    public static void register(Sign plugin) {
        IAmplifierAPI api = AmplifierAPI.getApi();
        if (api == null) {
            plugin.getLogger().warning("Amplifier is installed but the API is unavailable.");
            return;
        }
        active = true;
        plugin.getLogger().info("Hooked into Amplifier!");
    }

    public static void unregister() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static VoiceChatHook.VoiceState getState(UUID playerUuid) {
        if (!active) return VoiceChatHook.VoiceState.DISCONNECTED;

        IAmplifierAPI api = AmplifierAPI.getApi();
        if (api == null) return VoiceChatHook.VoiceState.DISCONNECTED;

        VoicechatServerApi voicechatApi = api.getVoicechatApi();
        if (voicechatApi == null) return VoiceChatHook.VoiceState.DISCONNECTED;

        VoicechatConnection connection = voicechatApi.getConnectionOf(playerUuid);
        if (connection == null) return VoiceChatHook.VoiceState.DISCONNECTED;

        // Check Amplifier's deafen state
        IVoicePlayer voicePlayer = api.getVoiceManager().getVoicePlayer(playerUuid);
        if (voicePlayer != null && voicePlayer.isDeafened()) return VoiceChatHook.VoiceState.DEAFENED;

        // Fall back to SVC's disabled state
        if (connection.isDisabled()) return VoiceChatHook.VoiceState.DEAFENED;

        // Delegate speaking detection to VoiceChatHook's mic packet tracking
        VoiceChatHook.VoiceState svcState = VoiceChatHook.getState(playerUuid);
        if (svcState == VoiceChatHook.VoiceState.SPEAKING) return VoiceChatHook.VoiceState.SPEAKING;

        return VoiceChatHook.VoiceState.IDLE;
    }
}

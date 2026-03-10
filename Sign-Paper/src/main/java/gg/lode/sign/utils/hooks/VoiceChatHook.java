package gg.lode.sign.utils.hooks;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import gg.lode.sign.Sign;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceChatHook implements VoicechatPlugin {
    private static final long SPEAKING_TIMEOUT_MS = 750;

    private static VoiceChatHook instance;
    private VoicechatServerApi serverApi;
    private final Map<UUID, Long> lastSpeakingTime = new ConcurrentHashMap<>();

    public static void register(Sign plugin) {
        BukkitVoicechatService service = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().warning("Simple Voice Chat is installed but the API service is unavailable.");
            return;
        }
        instance = new VoiceChatHook();
        service.registerPlugin(instance);
        plugin.getLogger().info("Hooked into Simple Voice Chat!");
    }

    public static void unregister() {
        if (instance != null) {
            instance.lastSpeakingTime.clear();
            instance = null;
        }
    }

    @Override
    public String getPluginId() {
        return "sign";
    }

    @Override
    public void initialize(de.maxhenkel.voicechat.api.VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            this.serverApi = serverApi;
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null) return;
        lastSpeakingTime.put(connection.getPlayer().getUuid(), System.currentTimeMillis());
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        lastSpeakingTime.remove(event.getPlayerUuid());
    }

    public enum VoiceState {
        SPEAKING,
        IDLE,
        DEAFENED,
        DISCONNECTED
    }

    public static VoiceState getState(UUID playerUuid) {
        if (instance == null || instance.serverApi == null) return VoiceState.DISCONNECTED;

        VoicechatConnection connection = instance.serverApi.getConnectionOf(playerUuid);
        if (connection == null) return VoiceState.DISCONNECTED;

        if (connection.isDisabled()) return VoiceState.DEAFENED;

        Long lastSpoke = instance.lastSpeakingTime.get(playerUuid);
        if (lastSpoke != null && System.currentTimeMillis() - lastSpoke < SPEAKING_TIMEOUT_MS) {
            return VoiceState.SPEAKING;
        }

        return VoiceState.IDLE;
    }
}

package gg.lode.sign.config;

import gg.lode.sign.Sign;
import gg.lode.sign.entities.DisplayBillboard;
import gg.lode.sign.entities.TextAlignment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.List;

public class NametagConfig {
    private final Sign plugin;

    private boolean enabled;
    private boolean showSelf;
    private int updateInterval;
    private int visibilityDistance;
    private List<String> lines;
    private boolean textShadow;
    private boolean seeThrough;
    private boolean supportCrouching;
    private boolean condenseHolograms;
    private TextAlignment textAlignment;
    private String background;
    private DisplayBillboard billboard;
    private Vector scale;
    private int placeholderDepth;

    // Voice chat
    private boolean voiceChatEnabled;
    private String voiceIconSpeaking;
    private String voiceIconIdle;
    private String voiceIconDeafened;
    private String voiceIconDisconnected;

    public NametagConfig(Sign plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.load(plugin.getConfig());
    }

    public void load(FileConfiguration config) {
        this.enabled = config.getBoolean("nametags.enabled", false);
        this.showSelf = config.getBoolean("nametags.show-self", true);
        this.updateInterval = config.getInt("nametags.update-interval", 20);
        this.visibilityDistance = config.getInt("nametags.visibility-distance", 64);
        this.lines = config.getStringList("nametags.display.lines");
        this.textShadow = config.getBoolean("nametags.display.text-shadow", false);
        this.seeThrough = config.getBoolean("nametags.display.see-through", false);
        this.supportCrouching = config.getBoolean("nametags.display.support-crouching", true);
        this.condenseHolograms = config.getBoolean("nametags.display.condense-holograms", false);
        this.textAlignment = TextAlignment.valueOf(config.getString("nametags.display.text-alignment", "center").toUpperCase());
        this.background = config.getString("nametags.display.background", "default");
        this.billboard = DisplayBillboard.valueOf(config.getString("nametags.display.billboard", "center").toUpperCase());
        this.scale = parseScale(config);
        this.placeholderDepth = config.getInt("nametags.display.placeholder-depth", 5);

        // Voice chat
        this.voiceChatEnabled = config.getBoolean("nametags.voice-chat.enabled", false);
        this.voiceIconSpeaking = config.getString("nametags.voice-chat.icons.speaking", "🔊");
        this.voiceIconIdle = config.getString("nametags.voice-chat.icons.idle", "🔈");
        this.voiceIconDeafened = config.getString("nametags.voice-chat.icons.deafened", "🔇");
        this.voiceIconDisconnected = config.getString("nametags.voice-chat.icons.disconnected", "🔌");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public boolean shouldHideSelf() {
        return !showSelf;
    }

    public int getVisibilityDistance() {
        return visibilityDistance;
    }

    public List<String> getLines() {
        return lines;
    }

    public boolean hasTextShadow() {
        return textShadow;
    }

    public boolean isSeeThrough() {
        return seeThrough;
    }

    public boolean supportsCrouching() {
        return supportCrouching;
    }

    public boolean isCondenseHolograms() {
        return condenseHolograms;
    }

    public TextAlignment getTextAlignment() {
        return textAlignment;
    }

    public String getBackground() {
        return background;
    }

    public DisplayBillboard getBillboard() {
        return billboard;
    }

    public Vector getScale() {
        return scale;
    }

    public int getPlaceholderDepth() {
        return placeholderDepth;
    }

    public boolean isVoiceChatEnabled() {
        return voiceChatEnabled;
    }

    public String getVoiceIconSpeaking() {
        return voiceIconSpeaking;
    }

    public String getVoiceIconIdle() {
        return voiceIconIdle;
    }

    public String getVoiceIconDeafened() {
        return voiceIconDeafened;
    }

    public String getVoiceIconDisconnected() {
        return voiceIconDisconnected;
    }

    private Vector parseScale(FileConfiguration config) {
        return new Vector()
                .setX(config.getDouble("scale.x", 1))
                .setY(config.getDouble("scale.y", 1))
                .setZ(config.getDouble("scale.z", 1));
    }
}

package gg.lode.sign;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import gg.lode.sign.api.ISign;
import gg.lode.sign.api.SignAPI;
import gg.lode.sign.api.event.SignReloadEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import gg.lode.sign.commands.SignCommand;
import gg.lode.sign.config.SignConfig;
import gg.lode.sign.listeners.PacketListener;
import gg.lode.sign.listeners.PlayerListener;
import gg.lode.sign.nametags.NametagManager;
import gg.lode.sign.nametags.NametagScheduler;
import gg.lode.sign.utils.VersionUpdater;
import gg.lode.sign.utils.handlers.NametagHandler;
import gg.lode.sign.utils.helpers.DependencyHelper;
import gg.lode.sign.utils.hooks.AmplifierHook;
import gg.lode.sign.utils.hooks.VoiceChatHook;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;

public final class Sign extends JavaPlugin implements ISign {
    private static final int CONFIG_VERSION = 1;

    private static Sign instance;
    private static SignConfig config;
    private NametagManager nametagManager;
    private NametagScheduler nametagScheduler;

    @Override
    public void onLoad() {
        // Initialize PacketEvents
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this, new PacketEventsSettings()
                .checkForUpdates(false)
                .fullStackTrace(true)
                .kickIfTerminated(false)
                .kickOnPacketException(false)
        ));
        PacketEvents.getAPI().load();

        CommandAPI.onLoad(new CommandAPIPaperConfig(this).silentLogs(true));

        // Configuration
        saveDefaultConfig();
        migrateConfig();
        config = new SignConfig(this);
        config.load();
    }

    @Override
    public void onEnable() {
        instance = this;
        SignAPI.register(this);
        PacketEvents.getAPI().init();
        CommandAPI.onEnable();
        PluginManager pluginManager = getServer().getPluginManager();

        // Nametags
        this.nametagManager = new NametagManager();
        this.nametagScheduler = new NametagScheduler(this);
        nametagScheduler.start();

        // Register Listeners & Commands
        pluginManager.registerEvents(new PlayerListener(), this);
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
        new SignCommand(this).register();

        // Other
        new Metrics(this, 30001);
        DependencyHelper.load();
        if (config.getNametagConfig().isVoiceChatEnabled() && DependencyHelper.isSimpleVoiceChatEnabled()) {
            VoiceChatHook.register(this);
            if (DependencyHelper.isAmplifierEnabled()) {
                AmplifierHook.register(this);
            }
        }
        NametagHandler.load();

        String version = getPluginMeta().getVersion();
        VersionUpdater versionUpdater = new VersionUpdater(this, "Sign", "https://lode.gg/plugin/sign",
                "https://lode.gg/api/plugins/sign/version", version);
        pluginManager.registerEvents(versionUpdater, this);

        getLogger().info(String.format("Sign v%s has been enabled.", version));
    }

    @Override
    public void onDisable() {
        AmplifierHook.unregister();
        VoiceChatHook.unregister();
        nametagScheduler.stop();
        nametagManager.removeAll();
        PacketEvents.getAPI().terminate();
        CommandAPI.onDisable();
        getLogger().info("Sign has been disabled.");
    }

    public boolean reloadPlugin() {
        try {
            getLogger().info("Reloading...");
            config.reload();
            nametagScheduler.stop();
            nametagManager.removeAll();
            if (config().getNametagConfig().isEnabled()) {
                nametagManager.createAll();
                nametagScheduler.start();
            }

            new SignReloadEvent().call();
            getLogger().info("Reloaded!");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload plugin: " + e.getMessage(), e);
            return false;
        }
    }

    private void migrateConfig() {
        FileConfiguration cfg = getConfig();
        int currentVersion = cfg.getInt("version", 0);

        if (currentVersion >= CONFIG_VERSION) return;

        getLogger().info("Updating config from version " + currentVersion + " to " + CONFIG_VERSION + "...");

        while (currentVersion < CONFIG_VERSION) {
            switch (currentVersion) {
                case 0 -> {
                    // update-interval changed from seconds to ticks
                    int seconds = cfg.getInt("nametags.update-interval", 1);
                    cfg.set("nametags.update-interval", seconds * 20);

                    // New display options
                    if (!cfg.contains("nametags.display.support-crouching")) {
                        cfg.set("nametags.display.support-crouching", true);
                    }
                    if (!cfg.contains("nametags.display.condense-holograms")) {
                        cfg.set("nametags.display.condense-holograms", false);
                    }
                }
            }

            currentVersion++;
            cfg.set("version", currentVersion);
        }

        saveConfig();
        getLogger().info("Config updated to version " + CONFIG_VERSION + ".");
    }

    public static Sign getInstance() {
        return instance;
    }

    public SignConfig config() {
        return config;
    }

    public NametagManager getNametagManager() {
        return nametagManager;
    }
}

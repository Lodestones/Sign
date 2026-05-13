package gg.lode.sign;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import gg.lode.sign.api.ISign;
import gg.lode.sign.api.SignAPI;
import gg.lode.sign.api.bootstrap.SignBootstrap;
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
import gg.lode.sign.utils.handlers.NametagHandler;
import gg.lode.sign.utils.helpers.DependencyHelper;
import gg.lode.sign.utils.hooks.AmplifierHook;
import gg.lode.sign.utils.hooks.VoiceChatHook;
import org.bstats.bukkit.Metrics;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Sign implements ISign, SignBootstrap {
    public static final String VERSION = "${VERSION}";

    private static final int CONFIG_VERSION = 1;

    private static Sign instance;
    private static SignConfig config;

    private JavaPlugin host;
    private NametagManager nametagManager;
    private NametagScheduler nametagScheduler;
    private PlayerListener playerListener;

    public JavaPlugin host() { return host; }
    public Server getServer() { return host.getServer(); }
    public Logger getLogger() { return host.getLogger(); }
    public File getDataFolder() { return host.getDataFolder(); }
    public FileConfiguration getConfig() { return host.getConfig(); }
    public InputStream getResource(String filename) { return host.getResource(filename); }
    public String getName() { return host.getName(); }

    @Override
    public void onLoad(JavaPlugin host) {
        this.host = host;

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(host, new PacketEventsSettings()
                .checkForUpdates(false)
                .fullStackTrace(true)
                .kickIfTerminated(false)
                .kickOnPacketException(false)
        ));
        PacketEvents.getAPI().load();

        CommandAPI.onLoad(new CommandAPIPaperConfig(host).silentLogs(true));

        host.saveDefaultConfig();
        migrateConfig();
        config = new SignConfig(this);
        config.load();
    }

    @Override
    public void onEnable(JavaPlugin host) {
        this.host = host;
        instance = this;
        SignAPI.register(this);
        PacketEvents.getAPI().init();
        CommandAPI.onEnable();
        PluginManager pluginManager = host.getServer().getPluginManager();

        this.nametagManager = new NametagManager();
        this.nametagScheduler = new NametagScheduler(this);
        nametagScheduler.start();

        this.playerListener = new PlayerListener();
        pluginManager.registerEvents(playerListener, host);
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(this));
        new SignCommand(this).register();

        new Metrics(host, 30001);
        DependencyHelper.load();
        if (config.getNametagConfig().isVoiceChatEnabled() && DependencyHelper.isSimpleVoiceChatEnabled()) {
            VoiceChatHook.register(this);
            if (DependencyHelper.isAmplifierEnabled()) {
                AmplifierHook.register(this);
            }
        }
        NametagHandler.load();

        getLogger().info(String.format("Sign v%s has been enabled.", VERSION));
    }

    @Override
    public void onDisable(JavaPlugin host) {
        AmplifierHook.unregister();
        VoiceChatHook.unregister();
        if (nametagScheduler != null) nametagScheduler.stop();
        if (nametagManager != null) nametagManager.removeAll();
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
        FileConfiguration cfg = host.getConfig();
        int currentVersion = cfg.getInt("version", 0);

        if (currentVersion >= CONFIG_VERSION) return;

        getLogger().info("Updating config from version " + currentVersion + " to " + CONFIG_VERSION + "...");

        while (currentVersion < CONFIG_VERSION) {
            switch (currentVersion) {
                case 0 -> {
                    int seconds = cfg.getInt("nametags.update-interval", 1);
                    cfg.set("nametags.update-interval", seconds * 20);

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

        host.saveConfig();
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

    public PlayerListener getPlayerListener() {
        return playerListener;
    }
}

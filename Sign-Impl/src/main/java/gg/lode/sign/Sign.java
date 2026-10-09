package gg.lode.sign;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import gg.lode.bookshelflocales.LocaleManager;
import gg.lode.bookshelflocales.picker.LanguagePicker;
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
import gg.lode.sign.utils.hooks.FrameHook;
import gg.lode.sign.utils.hooks.VoiceChatHook;
import org.bstats.bukkit.Metrics;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import gg.lode.bookshelfapi.api.util.VariableContext;

public final class Sign implements ISign, SignBootstrap {
    public static final String VERSION = "${VERSION}";

    private static final int CONFIG_VERSION = 3;

    private static final String[] BUNDLED_LOCALES = {
            "en_us", "es_es", "pt_br", "fr_fr", "de_de", "it_it", "nl_nl",
            "pl_pl", "ru_ru", "tr_tr", "ja_jp", "ko_kr", "zh_cn", "zh_tw"
    };

    private static Sign instance;
    private static SignConfig config;

    private JavaPlugin host;
    private NametagManager nametagManager;
    private NametagScheduler nametagScheduler;
    private PlayerListener playerListener;
    private LocaleManager locales;

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

        saveDefaultConfig();
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

        this.locales = LocaleManager.builder()
                .defaultLocale("en_us")
                .bundled(getClass().getClassLoader(), "locales", BUNDLED_LOCALES)
                .github("Lodestones/Locales", "Sign")
                .folder(getDataFolder().toPath().resolve("locales"))
                .exportBundledDefaults(true)
                .logger(getLogger()::warning)
                .build();

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
        if (DependencyHelper.isFrameEnabled()) {
            FrameHook.register(this);
        }
        NametagHandler.load();

        getLogger().info(String.format("Sign v%s has been enabled.", VERSION));
    }

    @Override
    public void onDisable(JavaPlugin host) {
        if (DependencyHelper.isFrameEnabled()) FrameHook.unregister();
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
            locales.reload();
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

    /**
     * Copy the impl jar's bundled config.yml into the data folder if absent.
     * Replaces host.saveDefaultConfig() — the loader jar bundles only loader.yml,
     * so the host plugin can't resolve config.yml as an embedded resource.
     */
    private void saveDefaultConfig() {
        File cfg = new File(getDataFolder(), "config.yml");
        if (cfg.exists()) return;
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create data folder: " + getDataFolder());
        }
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            if (in == null) throw new IllegalStateException("Bundled config.yml missing from impl jar");
            Files.copy(in, cfg.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save default config.yml", e);
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
                case 1 -> {
                    if (!cfg.contains("nametags.display.placeholder-depth")) {
                        cfg.set("nametags.display.placeholder-depth", 5);
                    }
                }
                case 2 -> {
                    if (!cfg.contains("language")) {
                        cfg.set("language", LanguagePicker.AUTOMATIC);
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

    public LocaleManager locales() {
        return locales;
    }

    /**
     * Resolves {@code key} in the recipient's own language where they have one, so
     * a server that ships more than one locale file does not have to pick a single
     * language for everybody.
     */
    public Component message(Object recipient, String key, VariableContext variables) {
        String locale = localeOf(recipient);
        return variables == null ? locales.get(key, locale) : locales.get(key, locale, variables);
    }

    public Component message(Object recipient, String key) {
        return message(recipient, key, null);
    }

    /** Raw MiniMessage for {@code key} in the recipient's language, for dialog labels. */
    public String text(Object recipient, String key) {
        return locales._get(key, localeOf(recipient));
    }

    private String localeOf(Object recipient) {
        // A language picked in config wins. "auto" leaves it to each player's own.
        String language = getLanguage();
        if (!LanguagePicker.AUTOMATIC.equalsIgnoreCase(language) && locales.hasLocale(language)) {
            return language.toLowerCase(Locale.ROOT);
        }

        String locale = recipient instanceof Player player
                ? player.locale().toString().toLowerCase(Locale.ROOT)
                : locales.getDefaultLocale();
        return locales.hasLocale(locale) ? locale : locales.getDefaultLocale();
    }

    public String getLanguage() {
        return host.getConfig().getString("language", LanguagePicker.AUTOMATIC);
    }

    public void setLanguage(String language) {
        host.getConfig().set("language", language);
        host.saveConfig();
    }
}

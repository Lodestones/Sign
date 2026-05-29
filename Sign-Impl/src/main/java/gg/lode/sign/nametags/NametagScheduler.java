package gg.lode.sign.nametags;

import gg.lode.sign.Sign;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class NametagScheduler {
    private final Sign plugin;
    private BukkitTask task;

    /** Re-send mount packets every 5 seconds (100 ticks) to recover from client-side drops. */
    private static final int REMOUNT_INTERVAL_TICKS = 100;
    private int ticksUntilRemount;

    public NametagScheduler(Sign plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (plugin.config().getNametagConfig().isEnabled()) {
            plugin.getLogger().info("Starting Nametag scheduler...");
            int interval = plugin.config().getNametagConfig().getUpdateInterval();
            ticksUntilRemount = REMOUNT_INTERVAL_TICKS;
            task = Bukkit.getScheduler().runTaskTimer(plugin.host(), () -> {
                boolean remount = (ticksUntilRemount -= interval) <= 0;
                if (remount) ticksUntilRemount = REMOUNT_INTERVAL_TICKS;

                for (Nametag nametag : plugin.getNametagManager().getAll()) {
                    nametag.updateVisibilityForAll();
                    if (remount) nametag.remountAll();
                }

            }, interval, interval);
        } else {
            plugin.getLogger().warning("Nametags are disabled for this server, therefore the nametag scheduler has not been started.");
            plugin.getLogger().warning("If you want to enable the nametags again, enable them in config.yml and run /sign reload.");
        }
    }

    public void stop() {
        if (task != null) {
            plugin.getLogger().info("Stopping Nametag scheduler...");
            task.cancel();
        }
    }
}

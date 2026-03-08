package gg.lode.sign.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionUpdater implements Listener {
    private final JavaPlugin plugin;

    private final String name;
    private final String latestVersion;
    private final String currentVersion;
    private final String baseUrl;

    public VersionUpdater(JavaPlugin plugin, String name, String baseUrl, String updateUrl, String currentVersion) {
        this.plugin = plugin;
        this.baseUrl = baseUrl;
        this.currentVersion = currentVersion;
        this.name = name;
        this.latestVersion = getLatestVersion(updateUrl);

        if (this.latestVersion != null) {
            if (isNewerVersion(this.latestVersion, currentVersion)) {
                plugin.getLogger().severe("=================================");
                plugin.getLogger().severe(String.format("A newer version of %s is available for download at Lodestone!", name));
                plugin.getLogger().severe(String.format("Newest Version: %s | Current Version: %s", this.latestVersion, currentVersion));
                plugin.getLogger().severe(String.format("%s/version/%s", this.baseUrl, this.latestVersion));
                plugin.getLogger().severe("=================================");
            }
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOp()) {
                if (this.latestVersion != null) {
                    if (isNewerVersion(this.latestVersion, currentVersion)) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(MiniMessage.miniMessage().deserialize(String.format("  A newer version of <yellow>%s <reset>is available for download.", name)));
                        player.sendMessage(MiniMessage.miniMessage().deserialize(String.format("  Newest Version: <green>%s <reset>| <reset>Current Version: <yellow>%s", latestVersion, currentVersion)));
                        player.sendMessage(MiniMessage.miniMessage().deserialize(String.format("  <white>Update %s at <hover:show_text:'<green>Update %s at Lodestone!'><click:open_url:%s/version/%s><underlined><gold>Lodestone!", name, name, baseUrl, latestVersion)));
                        player.sendMessage(Component.empty());
                    }
                }
            }
        }, 30L);
    }

    private String getLatestVersion(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonArray jsonArray = JsonParser.parseString(response.toString()).getAsJsonArray();
                return jsonArray.get(0).getAsJsonObject().get("version_number").getAsString();
            } else {
                plugin.getLogger().severe("=================================");
                plugin.getLogger().severe(String.format("Failed to retrieve latest version of %s from Lodestone!", name));
                plugin.getLogger().severe("\"version_number\" is missing or status didn't return OK");
                plugin.getLogger().severe("=================================");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("=================================");
            plugin.getLogger().severe(String.format("Failed to retrieve latest version of %s from Lodestone!", name));
            e.printStackTrace();
            plugin.getLogger().severe("=================================");
        }
        return null;
    }

    private boolean isNewerVersion(String latestVersion, String currentVersion) {
        String latestCore = latestVersion.replaceAll("v", "").split("\\+")[0];
        String currentCore = currentVersion.replaceAll("v", "").split("\\+")[0];

        String[] latestSplit = latestCore.split("-", 2);
        String[] currentSplit = currentCore.split("-", 2);
        String[] latestNums = latestSplit[0].split("\\.");
        String[] currentNums = currentSplit[0].split("\\.");

        int max = Math.max(latestNums.length, currentNums.length);
        for (int i = 0; i < max; i++) {
            int l = i < latestNums.length ? Integer.parseInt(latestNums[i]) : 0;
            int c = i < currentNums.length ? Integer.parseInt(currentNums[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }

        boolean hasLatestPre = latestSplit.length > 1;
        boolean hasCurrentPre = currentSplit.length > 1;

        if (!hasLatestPre && !hasCurrentPre) return false;
        if (!hasLatestPre) return true;
        if (!hasCurrentPre) return false;

        return comparePreRelease(latestSplit[1], currentSplit[1]) > 0;
    }

    private int comparePreRelease(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int max = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < max; i++) {
            if (i >= aParts.length) return -1;
            if (i >= bParts.length) return +1;
            String ap = aParts[i], bp = bParts[i];
            boolean aNum = ap.matches("\\d+"), bNum = bp.matches("\\d+");
            if (aNum && bNum) {
                int ai = Integer.parseInt(ap), bi = Integer.parseInt(bp);
                if (ai != bi) return ai - bi;
            } else if (aNum) {
                return -1;
            } else if (bNum) {
                return +1;
            } else {
                int cmp = ap.compareTo(bp);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }
}

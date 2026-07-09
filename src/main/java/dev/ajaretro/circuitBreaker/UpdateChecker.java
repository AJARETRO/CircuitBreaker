package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private final CircuitBreaker plugin;
    private final String currentVersion;

    public UpdateChecker(CircuitBreaker plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/AJARETRO/CircuitBreaker/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "CircuitBreaker-UpdateChecker");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }

                        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher matcher = pattern.matcher(response.toString());
                        if (matcher.find()) {
                            String latestVersion = matcher.group(1);
                            String cleanLatest = latestVersion.replace("v", "").replace("Stable-", "").trim();
                            String cleanCurrent = currentVersion.replace("v", "").replace("Stable-", "").trim();

                            if (!cleanCurrent.equalsIgnoreCase(cleanLatest)) {
                                plugin.getServer().getConsoleSender().sendMessage(
                                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                                    "A new version (" + ChatColor.GREEN + latestVersion + ChatColor.YELLOW + 
                                    ") is available! You are running v" + currentVersion
                                );
                                plugin.getServer().getConsoleSender().sendMessage(
                                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                                    "Download it at: " + ChatColor.AQUA + "https://github.com/AJARETRO/CircuitBreaker/releases"
                                );
                            } else {
                                plugin.getServer().getConsoleSender().sendMessage(
                                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + 
                                    "You are running the latest version (v" + currentVersion + ")."
                                );
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }
}

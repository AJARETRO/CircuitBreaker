package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.io.BufferedReader;
import java.io.File;
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

                        String responseBody = response.toString();

                        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
                        Matcher matcher = pattern.matcher(responseBody);
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
                                
                                // Look for browser_download_url matching .jar files
                                Pattern jarPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"");
                                Matcher jarMatcher = jarPattern.matcher(responseBody);
                                if (jarMatcher.find()) {
                                    String downloadUrl = jarMatcher.group(1);
                                    
                                    if (plugin.getConfig().getBoolean("auto-update.enabled", true)) {
                                        downloadUpdate(downloadUrl);
                                    } else {
                                        plugin.getServer().getConsoleSender().sendMessage(
                                            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                                            "Download it at: " + ChatColor.AQUA + "https://github.com/AJARETRO/CircuitBreaker/releases"
                                        );
                                    }
                                } else {
                                    plugin.getServer().getConsoleSender().sendMessage(
                                        ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                                        "Download it at: " + ChatColor.AQUA + "https://github.com/AJARETRO/CircuitBreaker/releases"
                                    );
                                }
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

    private void downloadUpdate(String downloadUrlString) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                    "Downloading the new update from: " + downloadUrlString
                );

                URL url = new URL(downloadUrlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "CircuitBreaker-AutoUpdater");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int status = connection.getResponseCode();
                // Handle HTTP redirects (GitHub redirects to S3)
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = connection.getHeaderField("Location");
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                    connection.setRequestProperty("User-Agent", "CircuitBreaker-AutoUpdater");
                }

                File updateFolder = new File(plugin.getDataFolder().getParentFile(), Bukkit.getUpdateFolder());
                if (!updateFolder.exists()) {
                    updateFolder.mkdirs();
                }

                String fileName = "CircuitBreaker.jar";
                try {
                    File pluginFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                    fileName = pluginFile.getName();
                } catch (Exception ignored) {}

                File targetFile = new File(updateFolder, fileName);
                
                try (java.io.InputStream in = connection.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + 
                    "Successfully downloaded version update to: " + ChatColor.WHITE + targetFile.getPath()
                );
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.AQUA + 
                    "The update will be applied automatically on the next server reboot."
                );

            } catch (Exception e) {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.RED + 
                    "Failed to download update: " + e.getMessage()
                );
            }
        });
    }
}

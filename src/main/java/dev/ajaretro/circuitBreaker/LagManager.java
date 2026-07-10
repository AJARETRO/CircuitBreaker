package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * Handles detection, monitoring, and freezing of laggy chunks,
 * as well as entity culling optimization.
 */
public class LagManager {

    private final CircuitBreaker plugin;
    private final Map<ChunkKey, Integer> strikeList = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Long> frozenChunks = new ConcurrentHashMap<>();
    private final Set<String> ignoredChunks = ConcurrentHashMap.newKeySet();

    // TPS Sentinel parameters
    private boolean tpsSentinelEnabled;
    private double tpsThreshold;
    private double msptThreshold;
    private boolean sendTpsToWebhook;
    
    private int consecutiveLowTpsSeconds = 0;
    private long lastTpsReportTime = 0;

    // Core detection parameters
    private boolean physicsLagEnabled;
    private int lagThreshold;
    private int strikeLimit;
    private long softResetDuration;
    private long freezeDuration;
    private boolean notifyAdmins;
    private int strikeResetMinutes;
    private String chunkFrozenMessage;

    // Entity culler parameters
    private boolean entityCullingEnabled;
    private int entityThreshold;
    private long entityScanInterval;
    private List<String> entityWhitelist;

    private FileConfiguration dataConfig = null;
    private File dataFile = null;

    // Statistics
    private int lagMachinesStopped = 0;
    private int physicsEventsDefused = 0;
    private int totalPlaytimeMinutes = 0;

    public LagManager(CircuitBreaker plugin) {
        this.plugin = plugin;

        // Load configuration keys
        FileConfiguration config = plugin.getConfig();
        this.physicsLagEnabled = config.getBoolean("enabled", true);
        this.lagThreshold = config.getInt("lag-threshold", 20000);
        this.strikeLimit = config.getInt("strike-limit", 3);
        this.softResetDuration = config.getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = config.getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = config.getBoolean("notify-admins", true);
        this.strikeResetMinutes = config.getInt("strike-reset-minutes", 15);
        this.chunkFrozenMessage = ChatColor.translateAlternateColorCodes('&', config.getString("chunk-frozen-message", "&cThis chunk has been frozen due to excessive lag detection!"));

        // Load entity culler parameters
        this.entityCullingEnabled = config.getBoolean("entity-culling.enabled", false);
        this.entityThreshold = config.getInt("entity-culling.threshold", 500);
        this.entityWhitelist = config.getStringList("entity-culling.whitelist");
        long scanSeconds = config.getLong("entity-culling.scan-interval-seconds", 15);
        this.entityScanInterval = scanSeconds * 20L;

        // Load TPS Sentinel parameters
        this.tpsSentinelEnabled = config.getBoolean("tps-sentinel.enabled", true);
        this.tpsThreshold = config.getDouble("tps-sentinel.threshold", 18.0);
        this.msptThreshold = config.getDouble("tps-sentinel.mspt-threshold", 48.0);
        this.sendTpsToWebhook = config.getBoolean("tps-sentinel.send-to-webhook", true);
        
        startTpsSentinel();

        loadIgnoredChunks();

        // Initialize core tickers
        if (this.physicsLagEnabled) {
            startTicker();
            if (this.strikeResetMinutes > 0) {
                startStrikeResetter();
            }
        } else {
            plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + "Physics Lag detection is disabled via config.yml."
            );
        }

        if (this.entityCullingEnabled) {
            startEntityScanner();
            plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + "Entity Culler is enabled and running."
            );
        }

        // Start playtime tracking task
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            if (online > 0) {
                totalPlaytimeMinutes += online;
                saveIgnoredChunks();
            }
        }, 1200L, 1200L);
    }

    private void startTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<ChunkKey, Integer> counts = plugin.getLagListener().getAndResetCounts();
            for (Map.Entry<ChunkKey, Integer> entry : counts.entrySet()) {
                ChunkKey key = entry.getKey();
                if (isIgnored(key.getWorldUid(), key.getX(), key.getZ())) {
                    continue;
                }
                int count = entry.getValue();
                if (count > getDynamicThreshold()) {
                    handleLaggyChunk(key, count);
                }
            }
        }, 0L, 20L);
    }

    public int getDynamicThreshold() {
        double tps = 20.0;
        double mspt = 20.0;
        try {
            tps = Bukkit.getTPS()[0];
            mspt = Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {}

        if (tps < 18.5 || mspt > 45.0) {
            return Math.max(1000, lagThreshold / 4);
        }
        if (tps < 19.5 || mspt > 35.0) {
            return Math.max(2000, lagThreshold / 2);
        }
        return lagThreshold;
    }

    private void startStrikeResetter() {
        long resetTicks = this.strikeResetMinutes * 60 * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            strikeList.clear();
            plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "All chunk strikes have been reset."
            );
        }, resetTicks, resetTicks);

        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Strike resetter task started. Will clear all strikes every " + strikeResetMinutes + " minutes."
        );
    }

    private void startEntityScanner() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Chunk> chunksToScan = new ArrayList<>();
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    if (!isIgnored(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
                        chunksToScan.add(chunk);
                    }
                }
            }

            if (chunksToScan.isEmpty()) return;

            // Spread chunk culling scans over multiple ticks (50 chunks per tick) to prevent TPS drop
            final int chunksPerTick = 50;
            final int totalChunks = chunksToScan.size();
            
            new org.bukkit.scheduler.BukkitRunnable() {
                private int currentIndex = 0;

                @Override
                public void run() {
                    if (currentIndex >= totalChunks) {
                        this.cancel();
                        return;
                    }

                    int limit = Math.min(currentIndex + chunksPerTick, totalChunks);
                    for (int i = currentIndex; i < limit; i++) {
                        Chunk chunk = chunksToScan.get(i);
                        if (!chunk.isLoaded()) {
                            continue;
                        }

                        Entity[] entities = chunk.getEntities();
                        if (entities.length > entityThreshold) {
                            int culledCount = cullChunk(chunk, entities);

                            if (culledCount > 0) {
                                String consoleMessage = ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                                        "Entity Culler removed " + culledCount + " entities from chunk at [" +
                                        chunk.getX() + ", " + chunk.getZ() + "] in " + chunk.getWorld().getName();

                                plugin.getServer().getConsoleSender().sendMessage(consoleMessage);
                                lagMachinesStopped++;
                                saveIgnoredChunks();

                                if (notifyAdmins) {
                                    String adminMessage = ChatColor.RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                                            "Entity Culler removed " + culledCount + " entities from chunk at [" +
                                            chunk.getX() + ", " + chunk.getZ() + "]";
                                    Bukkit.broadcast(adminMessage, "antilag.notify");
                                }
                            }
                        }
                    }
                    currentIndex += chunksPerTick;
                }
            }.runTaskTimer(plugin, 0L, 1L);

        }, 0L, entityScanInterval);
    }

    private int cullChunk(Chunk chunk, Entity[] entities) {
        int removedCount = 0;
        for (Entity entity : entities) {
            if (isImportant(entity)) {
                continue;
            }
            entity.remove();
            removedCount++;
        }
        return removedCount;
    }

    private boolean isImportant(Entity entity) {
        String typeName = entity.getType().name();
        for (String whitelistedType : entityWhitelist) {
            if (whitelistedType.equalsIgnoreCase(typeName)) {
                return true;
            }
        }

        if (entity.getCustomName() != null) return true;
        if (entity instanceof Tameable && ((Tameable) entity).isTamed()) return true;
        if (entity instanceof Vehicle) return true;

        return false;
    }

    private void handleLaggyChunk(ChunkKey key, int count) {
        if (isFrozen(key.getWorldUid(), key.getX(), key.getZ())) {
            return;
        }

        int strikes = strikeList.getOrDefault(key, 0) + 1;
        plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Lag detected in chunk [" + key.getX() + ", " + key.getZ() + "] (" + count + " events). " +
                        "Strike " + strikes + "/" + strikeLimit
        );

        if (strikes >= strikeLimit) {
            performHardFreeze(key);
            notifyAdmins(key, count);
            strikeList.remove(key);
        } else {
            performSoftReset(key);
            strikeList.put(key, strikes);
        }
    }

    private void performSoftReset(ChunkKey key) {
        Chunk chunk = key.toChunk();
        if (chunk == null) return;
        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Performing soft reset on chunk [" + key.getX() + ", " + key.getZ() + "]"
        );
        chunk.unload();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Chunk reloadedChunk = key.toChunk();
            if (reloadedChunk != null) {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Reloading chunk [" + key.getX() + ", " + key.getZ() + "]"
                );
                reloadedChunk.load();
            }
        }, softResetDuration);
    }

    private void performHardFreeze(ChunkKey key) {
        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + "Persistent lag! Freezing 3x3 chunks around [" + key.getX() + ", " + key.getZ() + "]"
        );
        
        UUID worldUid = key.getWorldUid();
        int cx = key.getX();
        int cz = key.getZ();

        long unfreezeTime = freezeDuration > -1 ? (System.currentTimeMillis() + (freezeDuration * 50)) : -1L;

        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                ChunkKey k = new ChunkKey(worldUid, x, z);
                frozenChunks.put(k, unfreezeTime);
                
                if (freezeDuration > -1) {
                    final ChunkKey finalK = k;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        frozenChunks.remove(finalK);
                    }, freezeDuration);
                }
            }
        }
        
        // Notify players inside the frozen chunks
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getUID().equals(worldUid)) {
                int px = player.getLocation().getBlockX() >> 4;
                int pz = player.getLocation().getBlockZ() >> 4;
                if (px >= cx - 1 && px <= cx + 1 && pz >= cz - 1 && pz <= cz + 1) {
                    player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + chunkFrozenMessage);
                    player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "by " + ChatColor.RED + "AJA RETRO" + ChatColor.GRAY + " (ajaretro.dev)");
                }
            }
        }

        this.lagMachinesStopped++;
        saveIgnoredChunks();
    }

    private void notifyAdmins(ChunkKey key, int count) {
        if (!notifyAdmins) {
            return;
        }
        
        net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.RED + "Lag machine frozen in chunk " + 
            "[" + key.getX() + ", " + key.getZ() + "] (" + count + " events). "
        );

        net.md_5.bungee.api.chat.TextComponent teleportBtn = new net.md_5.bungee.api.chat.TextComponent(ChatColor.AQUA + "[TP] ");
        teleportBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tp " + (key.getX() << 4) + " 100 " + (key.getZ() << 4)));
        teleportBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("Teleport to chunk")));

        net.md_5.bungee.api.chat.TextComponent unfreezeBtn = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GREEN + "[UNFREEZE] ");
        unfreezeBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/cb unfreeze"));
        unfreezeBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("Unfreeze this area")));

        net.md_5.bungee.api.chat.TextComponent ignoreBtn = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY + "[IGNORE]");
        ignoreBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/cb ignore"));
        ignoreBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text("Whitelist this chunk")));

        message.addExtra(teleportBtn);
        message.addExtra(unfreezeBtn);
        message.addExtra(ignoreBtn);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("antilag.notify")) {
                player.spigot().sendMessage(message);
            }
        }

        // Webhook Trigger
        if (plugin.getConfig().getBoolean("discord-webhook.enabled", false)) {
            World world = Bukkit.getWorld(key.getWorldUid());
            String worldName = world != null ? world.getName() : "unknown";
            double tps = 20.0;
            double mspt = 20.0;
            try {
                tps = Bukkit.getTPS()[0];
                mspt = Bukkit.getAverageTickTime();
            } catch (Throwable ignored) {}
            sendDiscordWebhook(worldName, key.getX(), key.getZ(), count, tps, mspt);
        }
    }

    private void sendDiscordWebhook(String worldName, int cx, int cz, int count, double tps, double mspt) {
        String urlString = plugin.getConfig().getString("discord-webhook.url", "");
        if (urlString.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL(urlString);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "CircuitBreaker-Webhook");
                conn.setDoOutput(true);

                // Build payload
                String json = "{"
                    + "\"embeds\": [{"
                    + "  \"title\": \"⚙️ CircuitBreaker Sentinel Alert\","
                    + "  \"url\": \"https://ajaretro.dev/circuitbreaker.html\","
                    + "  \"color\": 10027008," // #990000
                    + "  \"description\": \"⚠️ **A persistent lag source was detected and neutralized!**\","
                    + "  \"fields\": ["
                    + "    {\"name\": \"🌍 World\", \"value\": \"" + worldName + "\", \"inline\": true},"
                    + "    {\"name\": \"📍 Coordinates\", \"value\": \"Chunk: [" + cx + ", " + cz + "]\\\\nBlock X: " + (cx << 4) + ", Z: " + (cz << 4) + "\", \"inline\": true},"
                    + "    {\"name\": \"⚡ Physics Rate\", \"value\": \"" + count + " events/sec\", \"inline\": true},"
                    + "    {\"name\": \"📈 Server Load\", \"value\": \"TPS: " + String.format("%.2f", tps) + " | MSPT: " + String.format("%.1f", mspt) + "ms\", \"inline\": false},"
                    + "    {\"name\": \"💾 Teleport Command\", \"value\": \"`/tp " + (cx << 4) + " 100 " + (cz << 4) + "`\", \"inline\": false},"
                    + "    {\"name\": \"📥 Modrinth Page\", \"value\": \"[Download on Modrinth](https://modrinth.com/project/circuitbreaker)\", \"inline\": false}"
                    + "  ],"
                    + "  \"footer\": {\"text\": \"CircuitBreaker Anti-Lag Guard • ajaretro.dev\"}"
                    + "}]"
                    + "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                conn.getResponseCode();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord Webhook: " + e.getMessage());
            }
        });
    }

    private String getChunkIdentifier(Chunk chunk) {
        return chunk.getWorld().getUID().toString() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public boolean isFrozen(UUID worldUid, int x, int z) {
        return frozenChunks.containsKey(new ChunkKey(worldUid, x, z));
    }

    public boolean isIgnored(UUID worldUid, int x, int z) {
        return ignoredChunks.contains(worldUid.toString() + ":" + x + ":" + z);
    }

    public String getChunkStatus(Chunk chunk) {
        return getChunkStatus(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    }

    public String getChunkStatus(ChunkKey key) {
        if (isFrozen(key.getWorldUid(), key.getX(), key.getZ())) {
            return ChatColor.RED + "FROZEN";
        }
        if (isIgnored(key.getWorldUid(), key.getX(), key.getZ())) {
            return ChatColor.GRAY + "IGNORED (Persistent)";
        }
        if (strikeList.containsKey(key)) {
            return ChatColor.YELLOW + "WATCHED (Strikes: " + strikeList.get(key) + ")";
        }
        return ChatColor.GREEN + "NORMAL";
    }

    public boolean manuallyUnfreezeChunk(Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        strikeList.remove(key);
        return frozenChunks.remove(key) != null;
    }

    public int unfreezeArea(Location loc, int radius) {
        UUID worldUid = loc.getWorld().getUID();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        int count = 0;
        if (radius >= 0) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    ChunkKey k = new ChunkKey(worldUid, x, z);
                    strikeList.remove(k);
                    if (frozenChunks.remove(k) != null) {
                        count++;
                    }
                }
            }
        } else {
            int minCX = (loc.getBlockX() - 5) >> 4;
            int maxCX = (loc.getBlockX() + 5) >> 4;
            int minCZ = (loc.getBlockZ() - 5) >> 4;
            int maxCZ = (loc.getBlockZ() + 5) >> 4;

            for (int x = minCX; x <= maxCX; x++) {
                for (int z = minCZ; z <= maxCZ; z++) {
                    ChunkKey k = new ChunkKey(worldUid, x, z);
                    strikeList.remove(k);
                    if (frozenChunks.remove(k) != null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public boolean addChunkToIgnoreList(Chunk chunk) {
        manuallyUnfreezeChunk(chunk);
        boolean added = ignoredChunks.add(getChunkIdentifier(chunk));
        if (added) {
            saveIgnoredChunks();
        }
        return added;
    }

    public boolean removeChunkFromIgnoreList(Chunk chunk) {
        boolean removed = ignoredChunks.remove(getChunkIdentifier(chunk));
        if (removed) {
            saveIgnoredChunks();
        }
        return removed;
    }

    public void loadIgnoredChunks() {
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "data.yml");
        }
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        List<String> ignoredList = dataConfig.getStringList("ignored-chunks");
        ignoredChunks.clear();
        ignoredChunks.addAll(ignoredList);

        this.lagMachinesStopped = dataConfig.getInt("stats.lag-machines-stopped", 0);
        this.physicsEventsDefused = dataConfig.getInt("stats.physics-events-defused", 0);
        this.totalPlaytimeMinutes = dataConfig.getInt("stats.total-playtime-minutes", 0);

        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Loaded " + ignoredChunks.size() + " ignored chunks and statistics."
        );
    }

    public void saveIgnoredChunks() {
        if (dataConfig == null || dataFile == null) {
            loadIgnoredChunks();
        }
        try {
            dataConfig.set("ignored-chunks", new ArrayList<>(ignoredChunks));
            dataConfig.set("stats.lag-machines-stopped", this.lagMachinesStopped);
            dataConfig.set("stats.physics-events-defused", this.physicsEventsDefused);
            dataConfig.set("stats.total-playtime-minutes", this.totalPlaytimeMinutes);
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.RED + "Could not save ignored chunks and stats to data.yml!"
            );
            e.printStackTrace();
        }
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        
        this.physicsLagEnabled = config.getBoolean("enabled", true);
        this.lagThreshold = config.getInt("lag-threshold", 20000);
        this.strikeLimit = config.getInt("strike-limit", 3);
        this.softResetDuration = config.getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = config.getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = config.getBoolean("notify-admins", true);
        this.strikeResetMinutes = config.getInt("strike-reset-minutes", 15);
        this.chunkFrozenMessage = ChatColor.translateAlternateColorCodes('&', config.getString("chunk-frozen-message", "&cThis chunk has been frozen due to excessive lag detection!"));

        this.entityCullingEnabled = config.getBoolean("entity-culling.enabled", false);
        this.entityThreshold = config.getInt("entity-culling.threshold", 500);
        this.entityWhitelist = config.getStringList("entity-culling.whitelist");
        long scanSeconds = config.getLong("entity-culling.scan-interval-seconds", 15);
        this.entityScanInterval = scanSeconds * 20L;

        this.tpsSentinelEnabled = config.getBoolean("tps-sentinel.enabled", true);
        this.tpsThreshold = config.getDouble("tps-sentinel.threshold", 18.0);
        this.msptThreshold = config.getDouble("tps-sentinel.mspt-threshold", 48.0);
        this.sendTpsToWebhook = config.getBoolean("tps-sentinel.send-to-webhook", true);

        loadIgnoredChunks();
    }

    public int getLagMachinesStopped() {
        return lagMachinesStopped;
    }

    public int getTotalPlaytimeMinutes() {
        return totalPlaytimeMinutes;
    }

    public int getPhysicsEventsDefused() {
        return physicsEventsDefused;
    }

    public void incrementEventsDefused() {
        this.physicsEventsDefused++;
    }

    public boolean isPhysicsLagEnabled() {
        return physicsLagEnabled;
    }

    public boolean isEntityCullingEnabled() {
        return entityCullingEnabled;
    }

    public java.util.Set<ChunkKey> getFrozenChunks() {
        return frozenChunks.keySet();
    }

    public java.util.Map<ChunkKey, Long> getFrozenChunksMap() {
        return frozenChunks;
    }

    public java.util.Set<String> getIgnoredChunksList() {
        return ignoredChunks;
    }

    public int getIgnoredChunksCount() {
        return ignoredChunks.size();
    }

    private void startTpsSentinel() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!tpsSentinelEnabled) return;
            
            double tps = 20.0;
            double mspt = 20.0;
            try {
                tps = Bukkit.getTPS()[0];
                mspt = Bukkit.getAverageTickTime();
            } catch (Throwable ignored) {}
            
            if (tps < tpsThreshold || mspt > msptThreshold) {
                consecutiveLowTpsSeconds += 5;
                if (consecutiveLowTpsSeconds >= 15) {
                    long now = System.currentTimeMillis();
                    if (now - lastTpsReportTime >= 300000L) { // 5 mins cooldown
                        lastTpsReportTime = now;
                        triggerTpsReport(tps, mspt);
                    }
                    consecutiveLowTpsSeconds = 0;
                }
            } else {
                consecutiveLowTpsSeconds = 0;
            }
        }, 100L, 100L);
    }

    private void triggerTpsReport(double tps, double mspt) {
        int loadedChunks = 0;
        int totalEntities = 0;
        for (World w : Bukkit.getWorlds()) {
            loadedChunks += w.getLoadedChunks().length;
            try {
                totalEntities += w.getEntityCount();
            } catch (Throwable t) {
                totalEntities += w.getEntities().size();
            }
        }
        
        int players = Bukkit.getOnlinePlayers().size();
        
        String alertMsg = ChatColor.GOLD + "[CircuitBreaker] " + ChatColor.RED + "Server performance drop! " +
            ChatColor.YELLOW + "TPS: " + String.format("%.2f", tps) + " | MSPT: " + String.format("%.1f", mspt) + "ms. " +
            ChatColor.GRAY + "Players: " + players + " | Loaded Chunks: " + loadedChunks + " | Entities: " + totalEntities;
            
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("antilag.notify")) {
                p.sendMessage(alertMsg);
            }
        }
        
        if (sendTpsToWebhook && plugin.getConfig().getBoolean("discord-webhook.enabled", false)) {
            sendDiscordTpsReport(tps, mspt, loadedChunks, totalEntities, players);
        }
    }

    private void sendDiscordTpsReport(double tps, double mspt, int loadedChunks, int totalEntities, int players) {
        String urlString = plugin.getConfig().getString("discord-webhook.url", "");
        if (urlString.isEmpty()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL(urlString);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "CircuitBreaker-Webhook");
                conn.setDoOutput(true);

                String json = "{"
                    + "\"embeds\": [{"
                    + "  \"title\": \"📈 Server Performance Alert\","
                    + "  \"url\": \"https://ajaretro.dev/circuitbreaker.html\","
                    + "  \"color\": 16750848," // #ff9900
                    + "  \"description\": \"⚠️ **The server is experiencing high resource usage!**\","
                    + "  \"fields\": ["
                    + "    {\"name\": \"⚡ Server TPS\", \"value\": \"" + String.format("%.2f", tps) + "\", \"inline\": true},"
                    + "    {\"name\": \"⏱️ Server MSPT\", \"value\": \"" + String.format("%.1f", mspt) + "ms\", \"inline\": true},"
                    + "    {\"name\": \"👥 Players Online\", \"value\": \"" + players + "\", \"inline\": true},"
                    + "    {\"name\": \"📦 Loaded Chunks\", \"value\": \"" + loadedChunks + "\", \"inline\": true},"
                    + "    {\"name\": \"👾 Ticking Entities\", \"value\": \"" + totalEntities + "\", \"inline\": true},"
                    + "    {\"name\": \"📥 Modrinth Page\", \"value\": \"[Download on Modrinth](https://modrinth.com/project/circuitbreaker)\", \"inline\": false}"
                    + "  ],"
                    + "  \"footer\": {\"text\": \"CircuitBreaker Sentinel System • ajaretro.dev\"}"
                    + "}]"
                    + "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                conn.getResponseCode();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord TPS Alert: " + e.getMessage());
            }
        });
    }
}
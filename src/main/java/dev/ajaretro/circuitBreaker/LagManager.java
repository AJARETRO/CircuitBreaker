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
    private final Set<ChunkKey> frozenChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoredChunks = ConcurrentHashMap.newKeySet();

    // Core detection parameters
    private final boolean physicsLagEnabled;
    private final int lagThreshold;
    private final int strikeLimit;
    private final long softResetDuration;
    private final long freezeDuration;
    private final boolean notifyAdmins;
    private final int strikeResetMinutes;

    // Entity culler parameters
    private final boolean entityCullingEnabled;
    private final int entityThreshold;
    private final long entityScanInterval;
    private final List<String> entityWhitelist;

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

        // Load entity culler parameters
        this.entityCullingEnabled = config.getBoolean("entity-culling.enabled", false);
        this.entityThreshold = config.getInt("entity-culling.threshold", 500);
        this.entityWhitelist = config.getStringList("entity-culling.whitelist");
        long scanSeconds = config.getLong("entity-culling.scan-interval-seconds", 15);
        this.entityScanInterval = scanSeconds * 20L;

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

        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                ChunkKey k = new ChunkKey(worldUid, x, z);
                frozenChunks.add(k);
                
                if (freezeDuration > -1) {
                    final ChunkKey finalK = k;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        frozenChunks.remove(finalK);
                    }, freezeDuration);
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
    }

    private String getChunkIdentifier(Chunk chunk) {
        return chunk.getWorld().getUID().toString() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public boolean isFrozen(UUID worldUid, int x, int z) {
        return frozenChunks.contains(new ChunkKey(worldUid, x, z));
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
        return frozenChunks.remove(key);
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
                    if (frozenChunks.remove(k)) {
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
                    if (frozenChunks.remove(k)) {
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
}
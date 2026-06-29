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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles detection, monitoring, and freezing of laggy chunks,
 * as well as entity culling optimization.
 */
public class LagManager {

    private final CircuitBreaker plugin;
    private final Map<Chunk, Integer> strikeList = new ConcurrentHashMap<>();
    private final Set<Chunk> frozenChunks = ConcurrentHashMap.newKeySet();
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

        // Initialize core tickers using clean lambda syntax
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
            Map<Chunk, Integer> counts = plugin.getLagListener().getAndResetCounts();
            for (Map.Entry<Chunk, Integer> entry : counts.entrySet()) {
                Chunk chunk = entry.getKey();
                if (isIgnored(chunk)) {
                    continue;
                }
                int count = entry.getValue();
                if (count > lagThreshold) {
                    handleLaggyChunk(chunk, count);
                }
            }
        }, 0L, 20L);
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
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    if (isIgnored(chunk)) {
                        continue;
                    }

                    Entity[] entities = chunk.getEntities();
                    if (entities.length > entityThreshold) {
                        int culledCount = cullChunk(chunk, entities);

                        if (culledCount > 0) {
                            String consoleMessage = ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                                    "Entity Culler removed " + culledCount + " entities from chunk at [" +
                                    chunk.getX() + ", " + chunk.getZ() + "] in " + world.getName();

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
            }
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

    private void handleLaggyChunk(Chunk chunk, int count) {
        if (isFrozen(chunk)) {
            return;
        }

        int strikes = strikeList.getOrDefault(chunk, 0) + 1;
        plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Lag detected in chunk [" + chunk.getX() + ", " + chunk.getZ() + "] (" + count + " events). " +
                        "Strike " + strikes + "/" + strikeLimit
        );

        if (strikes >= strikeLimit) {
            performHardFreeze(chunk);
            notifyAdmins(chunk, count);
            strikeList.remove(chunk);
        } else {
            performSoftReset(chunk);
            strikeList.put(chunk, strikes);
        }
    }

    private void performSoftReset(Chunk chunk) {
        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Performing soft reset on chunk [" + chunk.getX() + ", " + chunk.getZ() + "]"
        );
        chunk.unload();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getServer().getConsoleSender().sendMessage(
                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Reloading chunk [" + chunk.getX() + ", " + chunk.getZ() + "]"
            );
            chunk.load();
        }, softResetDuration);
    }

    private void performHardFreeze(Chunk chunk) {
        plugin.getServer().getConsoleSender().sendMessage(
            ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + "Persistent lag! Freezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]"
        );
        frozenChunks.add(chunk);
        this.lagMachinesStopped++;
        saveIgnoredChunks();

        if (freezeDuration > -1) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getServer().getConsoleSender().sendMessage(
                    ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Auto-unfreezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]"
                );
                frozenChunks.remove(chunk);
            }, freezeDuration);
        }
    }

    private void notifyAdmins(Chunk chunk, int count) {
        if (!notifyAdmins) {
            return;
        }
        String message = ChatColor.RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                "Persistent lag (" + count + " events) detected! " +
                "Chunk at [" + chunk.getX() + ", " + chunk.getZ() + "] in " +
                chunk.getWorld().getName() + " has been frozen.";
        Bukkit.broadcast(message, "antilag.notify");
    }

    private String getChunkIdentifier(Chunk chunk) {
        return chunk.getWorld().getUID().toString() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public boolean isFrozen(Chunk chunk) {
        return frozenChunks.contains(chunk);
    }

    public boolean isIgnored(Chunk chunk) {
        return ignoredChunks.contains(getChunkIdentifier(chunk));
    }

    public String getChunkStatus(Chunk chunk) {
        if (isFrozen(chunk)) {
            return ChatColor.RED + "FROZEN";
        }
        if (isIgnored(chunk)) {
            return ChatColor.GRAY + "IGNORED (Persistent)";
        }
        if (strikeList.containsKey(chunk)) {
            return ChatColor.YELLOW + "WATCHED (Strikes: " + strikeList.get(chunk) + ")";
        }
        return ChatColor.GREEN + "NORMAL";
    }

    public boolean manuallyUnfreezeChunk(Chunk chunk) {
        strikeList.remove(chunk);
        return frozenChunks.remove(chunk);
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

    public boolean isPhysicsLagEnabled() {
        return physicsLagEnabled;
    }

    public boolean isEntityCullingEnabled() {
        return entityCullingEnabled;
    }
}
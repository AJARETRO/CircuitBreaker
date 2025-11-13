package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LagManager {

    private final CircuitBreaker plugin;
    private final Map<Chunk, Integer> strikeList = new ConcurrentHashMap<>();
    // Use ConcurrentHashMap.newKeySet() for thread-safe sets
    private final Set<Chunk> frozenChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoredChunks = ConcurrentHashMap.newKeySet();

    // --- v1.0 Config Values ---
    private final boolean physicsLagEnabled;
    private final int lagThreshold;
    private final int strikeLimit;
    private final long softResetDuration;
    private final long freezeDuration;
    private final boolean notifyAdmins;
    private final int strikeResetMinutes;

    // --- v2.0 ENTITY CULLER Config Values ---
    private final boolean entityCullingEnabled;
    private final int entityThreshold;
    private final long entityScanInterval;
    private final List<String> entityWhitelist;

    private FileConfiguration dataConfig = null;
    private File dataFile = null;

    public LagManager(CircuitBreaker plugin) {
        this.plugin = plugin;

        // --- Load v1.0 Config.yml ---
        this.physicsLagEnabled = plugin.getConfig().getBoolean("enabled", true);
        this.lagThreshold = plugin.getConfig().getInt("lag-threshold", 20000);
        this.strikeLimit = plugin.getConfig().getInt("strike-limit", 3);
        this.softResetDuration = plugin.getConfig().getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = plugin.getConfig().getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = plugin.getConfig().getBoolean("notify-admins", true);
        this.strikeResetMinutes = plugin.getConfig().getInt("strike-reset-minutes", 15);

        // --- v2.0: Load Entity Culler Config ---
        this.entityCullingEnabled = plugin.getConfig().getBoolean("entity-culling.enabled", false);
        this.entityThreshold = plugin.getConfig().getInt("entity-culling.threshold", 500);
        this.entityWhitelist = plugin.getConfig().getStringList("entity-culling.whitelist");
        long scanSeconds = plugin.getConfig().getLong("entity-culling.scan-interval-seconds", 15);
        this.entityScanInterval = scanSeconds * 20L;

        loadIgnoredChunks();

        // Start v1.0 Physics Ticker
        if (this.physicsLagEnabled) {
            startTicker();
            if (this.strikeResetMinutes > 0) {
                startStrikeResetter();
            }
        } else {
            plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + "Physics Lag detection is disabled via config.yml.");
        }

        // --- v2.0: Start Entity Culler Ticker ---
        if (this.entityCullingEnabled) {
            startEntityScanner();
            plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + "Entity Culler is enabled and running.");
        }
    }

    // --- v1.0 Physics Lag Methods ---

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
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
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startStrikeResetter() {
        long resetTicks = this.strikeResetMinutes * 60 * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                strikeList.clear();
                plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "All chunk strikes have been reset.");
            }
        }.runTaskTimer(plugin, resetTicks, resetTicks);

        plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Strike resetter task started. Will clear all strikes every " + strikeResetMinutes + " minutes.");
    }

    // --- v2.0: ENTITY CULLER METHODS ---

    private void startEntityScanner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        if (isIgnored(chunk)) {
                            continue;
                        }

                        Entity[] entities = chunk.getEntities();

                        if (entities.length > entityThreshold) {
                            // We found a problem chunk, cull it
                            int culledCount = cullChunk(chunk, entities);

                            // --- NEW: Log and Notify Admins ---
                            if (culledCount > 0) {
                                String consoleMessage = ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                                        "Entity Culler removed " + culledCount + " entities from chunk at [" +
                                        chunk.getX() + ", " + chunk.getZ() + "] in " + world.getName();

                                // 1. Log to console
                                plugin.getServer().getConsoleSender().sendMessage(consoleMessage);

                                // 2. Notify in-game admins (if enabled)
                                // We re-use the 'notify-admins' config setting
                                if (notifyAdmins) {
                                    String adminMessage = ChatColor.RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                                            "Entity Culler removed " + culledCount + " entities from chunk at [" +
                                            chunk.getX() + ", " + chunk.getZ() + "]";
                                    // We re-use the 'antilag.notify' permission
                                    Bukkit.broadcast(adminMessage, "antilag.notify");
                                }
                            }
                            // --- END NEW ---
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, entityScanInterval);
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

    // --- v1.0 CORE & ADMIN METHODS ---

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
        plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Performing soft reset on chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
        chunk.unload();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Reloading chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
                chunk.load();
            }
        }.runTaskLater(plugin, softResetDuration);
    }

    private void performHardFreeze(Chunk chunk) {
        plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + "Persistent lag! Freezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
        frozenChunks.add(chunk);

        if (freezeDuration > -1) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Auto-unfreezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
                    frozenChunks.remove(chunk);
                }
            }.runTaskLater(plugin, freezeDuration);
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
        plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Loaded " + ignoredChunks.size() + " ignored chunks from data.yml.");
    }

    public void saveIgnoredChunks() {
        if (dataConfig == null || dataFile == null) {
            loadIgnoredChunks();
        }
        try {
            dataConfig.set("ignored-chunks", new ArrayList<>(ignoredChunks));
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getServer().getConsoleSender().sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.RED + "Could not save ignored chunks to data.yml!");
            e.printStackTrace();
        }
    }
}
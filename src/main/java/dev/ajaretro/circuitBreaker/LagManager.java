package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
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
    private final Set<Chunk> frozenChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoredChunks = ConcurrentHashMap.newKeySet();

    // --- Config Values ---
    private final boolean pluginEnabled;
    private final int lagThreshold;
    private final int strikeLimit;
    private final long softResetDuration;
    private final long freezeDuration;
    private final boolean notifyAdmins;

    // --- NEW: Strike Reset Value ---
    private final int strikeResetMinutes;

    // --- Data File ---
    private FileConfiguration dataConfig = null;
    private File dataFile = null;

    public LagManager(CircuitBreaker plugin) {
        this.plugin = plugin;

        // --- Load Config.yml ---
        this.pluginEnabled = plugin.getConfig().getBoolean("enabled", true);
        this.lagThreshold = plugin.getConfig().getInt("lag-threshold", 5000);
        this.strikeLimit = plugin.getConfig().getInt("strike-limit", 3);
        this.softResetDuration = plugin.getConfig().getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = plugin.getConfig().getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = plugin.getConfig().getBoolean("notify-admins", true);

        // --- NEW: Load the reset timer ---
        this.strikeResetMinutes = plugin.getConfig().getInt("strike-reset-minutes", 15);
        // --- END NEW ---

        // Load our data.yml
        loadIgnoredChunks();

        // Start our core logic only if enabled
        if (this.pluginEnabled) {
            startTicker();

            // --- NEW: Start the Strike Reset Timer ---
            if (this.strikeResetMinutes > 0) {
                startStrikeResetter();
            } else {
                plugin.getLogger().info("Strike resetting is disabled via config.");
            }
            // --- END NEW ---

        } else {
            plugin.getLogger().warning("CircuitBreaker is disabled via config.yml.");
        }
    }

    /**
     * This is our main 1-second lag check ticker.
     */
    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // ... (all the logic for checking chunks is the same) ...
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
        }.runTaskTimer(plugin, 0L, 20L); // Runs every 20 ticks (1 second)
    }

    // --- NEW: STRIKE RESETTER METHOD ---
    /**
     * Starts a new, separate timer that clears the strike list
     * every X minutes, based on the config.
     */
    private void startStrikeResetter() {
        // Convert minutes to ticks (Minutes * 60 seconds * 20 ticks)
        long resetTicks = this.strikeResetMinutes * 60 * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                // This is its ONLY job!
                strikeList.clear();

                plugin.getLogger().info("All chunk strikes have been reset.");
            }
        }.runTaskTimer(plugin, resetTicks, resetTicks); // Wait X ticks, then run every X ticks

        plugin.getLogger().info("Strike resetter task started. Will clear all strikes every " + strikeResetMinutes + " minutes.");
    }
    // --- END NEW ---

    // ... (All other methods like handleLaggyChunk, performSoftReset, etc... are exactly the same) ...
    // ... (All data saving/loading methods are also the same) ...

    // (Rest of your LagManager.java file...)
    private void handleLaggyChunk(Chunk chunk, int count) {
        if (isFrozen(chunk)) {
            return;
        }

        int strikes = strikeList.getOrDefault(chunk, 0) + 1;
        plugin.getLogger().info(
                "Lag detected in chunk [" + chunk.getX() + ", " + chunk.getZ() + "] (" + count + " events). " +
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
        plugin.getLogger().info("Performing soft reset on chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
        chunk.unload();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Reloading chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
                chunk.load();
            }
        }.runTaskLater(plugin, softResetDuration);
    }

    private void performHardFreeze(Chunk chunk) {
        plugin.getLogger().warning("Persistent lag! Freezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
        frozenChunks.add(chunk);

        if (freezeDuration > -1) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getLogger().info("Auto-unfreezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
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
        plugin.getLogger().info("Loaded " + ignoredChunks.size() + " ignored chunks from data.yml.");
    }

    public void saveIgnoredChunks() {
        if (dataConfig == null || dataFile == null) {
            loadIgnoredChunks();
        }
        try {
            dataConfig.set("ignored-chunks", new ArrayList<>(ignoredChunks));
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save ignored chunks to data.yml!");
            e.printStackTrace();
        }
    }
}
package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitRunnable;
// --- NEW IMPORTS ---
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
// --- END NEW IMPORTS ---
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LagManager {

    private final CircuitBreaker plugin;
    private final Map<Chunk, Integer> strikeList = new ConcurrentHashMap<>();
    private final Set<Chunk> frozenChunks = ConcurrentHashMap.newKeySet();

    // --- UPDATED: This is now a Set of Strings, not Chunks ---
    private final Set<String> ignoredChunks = ConcurrentHashMap.newKeySet();
    // --- END UPDATE ---

    // --- Config Values ---
    private final boolean pluginEnabled;
    private final int lagThreshold;
    private final int strikeLimit;
    private final long softResetDuration;
    private final long freezeDuration;
    private final boolean notifyAdmins;

    // --- NEW: For saving/loading data.yml ---
    private FileConfiguration dataConfig = null;
    private File dataFile = null;
    // --- END NEW ---

    public LagManager(CircuitBreaker plugin) {
        this.plugin = plugin;

        // --- Load Config.yml ---
        this.pluginEnabled = plugin.getConfig().getBoolean("enabled", true);
        this.lagThreshold = plugin.getConfig().getInt("lag-threshold", 5000);
        this.strikeLimit = plugin.getConfig().getInt("strike-limit", 3);
        this.softResetDuration = plugin.getConfig().getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = plugin.getConfig().getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = plugin.getConfig().getBoolean("notify-admins", true);

        // --- NEW: Load our data.yml ---
        loadIgnoredChunks();
        // --- END NEW ---

        if (this.pluginEnabled) {
            startTicker();
        } else {
            plugin.getLogger().warning("CircuitBreaker is disabled via config.yml.");
        }
    }

    private void startTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Map<Chunk, Integer> counts = plugin.getLagListener().getAndResetCounts();
                for (Map.Entry<Chunk, Integer> entry : counts.entrySet()) {
                    Chunk chunk = entry.getKey();

                    // --- UPDATED: Check ignore list ---
                    // This now calls our updated isIgnored() method
                    if (isIgnored(chunk)) {
                        continue;
                    }
                    // --- END UPDATE ---

                    int count = entry.getValue();
                    if (count > lagThreshold) {
                        handleLaggyChunk(chunk, count);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

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

    // --- CHUNK STATUS METHODS (Updated) ---

    public boolean isFrozen(Chunk chunk) {
        return frozenChunks.contains(chunk);
    }

    /**
     * Gets a unique string ID for a chunk.
     * @return String in "world_uuid:x:z" format
     */
    private String getChunkIdentifier(Chunk chunk) {
        return chunk.getWorld().getUID().toString() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public boolean isIgnored(Chunk chunk) {
        // Now checks our Set<String>
        return ignoredChunks.contains(getChunkIdentifier(chunk));
    }

    public String getChunkStatus(Chunk chunk) {
        if (isFrozen(chunk)) {
            return ChatColor.RED + "FROZEN";
        }
        if (isIgnored(chunk)) { // Uses our new method
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
        manuallyUnfreezeChunk(chunk); // Also unfreeze it

        boolean added = ignoredChunks.add(getChunkIdentifier(chunk));
        if (added) {
            saveIgnoredChunks(); // Save to file!
        }
        return added;
    }

    public boolean removeChunkFromIgnoreList(Chunk chunk) {
        boolean removed = ignoredChunks.remove(getChunkIdentifier(chunk));
        if (removed) {
            saveIgnoredChunks(); // Save to file!
        }
        return removed;
    }

    // --- NEW: DATA SAVING/LOADING METHODS ---

    /**
     * Loads the list of ignored chunks from data.yml.
     */
    public void loadIgnoredChunks() {
        if (dataFile == null) {
            // Get the plugin's data folder and create a "data.yml" file
            dataFile = new File(plugin.getDataFolder(), "data.yml");
        }
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false); // Create it if it doesn't exist
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Get the list of strings from the file
        List<String> ignoredList = dataConfig.getStringList("ignored-chunks");
        ignoredChunks.clear();
        ignoredChunks.addAll(ignoredList);

        plugin.getLogger().info("Loaded " + ignoredChunks.size() + " ignored chunks from data.yml.");
    }

    /**
     * Saves the current list of ignored chunks to data.yml.
     */
    public void saveIgnoredChunks() {
        if (dataConfig == null || dataFile == null) {
            // Just in case, this should already be set
            loadIgnoredChunks();
        }
        try {
            // Save the Set as a new ArrayList
            dataConfig.set("ignored-chunks", new ArrayList<>(ignoredChunks));
            dataConfig.save(dataFile); // Save the file
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save ignored chunks to data.yml!");
            e.printStackTrace();
        }
    }
}
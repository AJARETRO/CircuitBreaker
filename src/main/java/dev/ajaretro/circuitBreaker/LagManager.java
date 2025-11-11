package dev.ajaretro.circuitBreaker;

// Import all the new classes we need
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LagManager {

    private final CircuitBreaker plugin;
    private final Map<Chunk, Integer> strikeList = new ConcurrentHashMap<>();
    private final Set<Chunk> frozenChunks = ConcurrentHashMap.newKeySet();

    // --- Config Values ---
    // We've replaced the 'static final' with variables
    // that we will load from the config.
    private final boolean pluginEnabled;
    private final int lagThreshold;
    private final int strikeLimit;
    private final long softResetDuration;
    private final long freezeDuration;
    private final boolean notifyAdmins;

    public LagManager(CircuitBreaker plugin) {
        this.plugin = plugin;

        // --- Load all values from config.yml ---
        // We use 'getConfig()' from the main plugin class
        this.pluginEnabled = plugin.getConfig().getBoolean("enabled", true);
        this.lagThreshold = plugin.getConfig().getInt("lag-threshold", 5000);
        this.strikeLimit = plugin.getConfig().getInt("strike-limit", 3);
        this.softResetDuration = plugin.getConfig().getLong("soft-reset-duration-ticks", 200L);
        this.freezeDuration = plugin.getConfig().getLong("freeze-duration-ticks", 6000L);
        this.notifyAdmins = plugin.getConfig().getBoolean("notify-admins", true);

        // Only start the ticker if the plugin is enabled
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
                // 1. Get counts
                Map<Chunk, Integer> counts = plugin.getLagListener().getAndResetCounts();

                // 2. Loop
                for (Map.Entry<Chunk, Integer> entry : counts.entrySet()) {
                    Chunk chunk = entry.getKey();
                    int count = entry.getValue();

                    // 3. Check (now using our config value!)
                    if (count > lagThreshold) {
                        handleLaggyChunk(chunk, count);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Runs every 20 ticks (1 second)
    }

    /**
     * This is the core logic method, now filled in!
     */
    private void handleLaggyChunk(Chunk chunk, int count) {
        if (isFrozen(chunk)) {
            return; // It's already in jail, do nothing.
        }

        // 1. Get strike count and add 1
        int strikes = strikeList.getOrDefault(chunk, 0) + 1;

        plugin.getLogger().info(
                "Lag detected in chunk [" + chunk.getX() + ", " + chunk.getZ() + "] (" + count + " events). " +
                        "Strike " + strikes + "/" + strikeLimit
        );

        // 4. Check if strikes are at the limit
        if (strikes >= strikeLimit) {
            // --- STRIKE 3: HARD FREEZE ---
            performHardFreeze(chunk);
            notifyAdmins(chunk, count);

            // Remove from strike list, it's in jail now.
            strikeList.remove(chunk);
        } else {
            // --- STRIKES 1 or 2: SOFT RESET ---
            performSoftReset(chunk);

            // Save the new strike count
            strikeList.put(chunk, strikes);
        }
    }

    /**
     * Performs a "Soft Reset" by unloading and reloading the chunk.
     */
    private void performSoftReset(Chunk chunk) {
        plugin.getLogger().info("Performing soft reset on chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");

        // Unload the chunk
        chunk.unload();

        // Schedule a task to reload it after the time in our config
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Reloading chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
                chunk.load(); // Reload the chunk
            }
        }.runTaskLater(plugin, softResetDuration); // Uses config value
    }

    /**
     * Performs a "Hard Freeze" by adding the chunk to the 'frozenChunks' set.
     */
    private void performHardFreeze(Chunk chunk) {
        plugin.getLogger().warning("Persistent lag! Freezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");

        // Add to the "Jail"
        frozenChunks.add(chunk);

        // Check config if we should unfreeze it automatically
        if (freezeDuration > -1) {
            // Schedule an "unfreeze" task
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getLogger().info("Auto-unfreezing chunk [" + chunk.getX() + ", " + chunk.getZ() + "]");
                    frozenChunks.remove(chunk);
                }
            }.runTaskLater(plugin, freezeDuration); // Uses config value
        }
    }

    /**
     * Broadcasts a message to all admins.
     */
    private void notifyAdmins(Chunk chunk, int count) {
        // Only run if enabled in config
        if (!notifyAdmins) {
            return;
        }

        String message = ChatColor.RED + "[CircuitBreaker] " + ChatColor.YELLOW +
                "Persistent lag (" + count + " events) detected! " +
                "Chunk at [" + chunk.getX() + ", " + chunk.getZ() + "] in " +
                chunk.getWorld().getName() + " has been frozen.";

        // Broadcast to players with the "antilag.notify" permission
        Bukkit.broadcast(message, "antilag.notify");
    }

    /**
     * Checks if a chunk is currently in our "Jail".
     */
    public boolean isFrozen(Chunk chunk) {
        return frozenChunks.contains(chunk);
    }
}
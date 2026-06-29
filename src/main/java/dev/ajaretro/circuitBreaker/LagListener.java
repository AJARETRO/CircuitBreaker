package dev.ajaretro.circuitBreaker;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for physics events to monitor chunk activity and cancel events in frozen chunks.
 */
public class LagListener implements Listener {

    private final CircuitBreaker plugin;
    private final Map<Chunk, Integer> eventCounter = new ConcurrentHashMap<>();

    public LagListener(CircuitBreaker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        LagManager manager = plugin.getLagManager();

        if (manager.isFrozen(chunk)) {
            event.setCancelled(true);
            return;
        }

        if (manager.isIgnored(chunk)) {
            return;
        }

        // Increment event count for the chunk using a thread-safe atomic merge
        eventCounter.merge(chunk, 1, Integer::sum);
    }

    /**
     * Snapshots the current event counts and resets the counter for the next interval.
     */
    public Map<Chunk, Integer> getAndResetCounts() {
        Map<Chunk, Integer> snapshot = new ConcurrentHashMap<>(eventCounter);
        eventCounter.clear();
        return snapshot;
    }
}
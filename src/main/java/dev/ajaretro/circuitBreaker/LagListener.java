package dev.ajaretro.circuitBreaker;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for physics events to monitor chunk activity and cancel events in frozen chunks.
 */
public class LagListener implements Listener {

    private final CircuitBreaker plugin;
    private final Map<ChunkKey, Integer> eventCounter = new ConcurrentHashMap<>();
    private Map<ChunkKey, Integer> lastSnapshot = new ConcurrentHashMap<>();

    public LagListener(CircuitBreaker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        UUID worldUid = block.getWorld().getUID();

        LagManager manager = plugin.getLagManager();

        if (manager.isFrozen(worldUid, chunkX, chunkZ)) {
            event.setCancelled(true);
            manager.incrementEventsDefused();
            return;
        }

        if (manager.isIgnored(worldUid, chunkX, chunkZ)) {
            return;
        }

        ChunkKey key = new ChunkKey(worldUid, chunkX, chunkZ);
        // Increment event count for the chunk using a thread-safe atomic merge
        eventCounter.merge(key, 1, Integer::sum);
    }

    /**
     * Snapshots the current event counts and resets the counter for the next interval.
     */
    public Map<ChunkKey, Integer> getAndResetCounts() {
        Map<ChunkKey, Integer> snapshot = new ConcurrentHashMap<>(eventCounter);
        eventCounter.clear();
        this.lastSnapshot = snapshot;
        return snapshot;
    }

    public Map<ChunkKey, Integer> getLastSnapshot() {
        return lastSnapshot;
    }
}
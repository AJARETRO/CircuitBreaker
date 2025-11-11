package dev.ajaretro.circuitBreaker;

import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LagListener implements Listener {

    // --- NEW ---
    // We need a reference to the main plugin
    private final CircuitBreaker plugin;
    // --- END NEW ---

    private final Map<Chunk, Integer> eventCounter = new ConcurrentHashMap<>();

    // --- NEW: Constructor ---
    // We pass the main plugin in when we create the listener
    public LagListener(CircuitBreaker plugin) {
        this.plugin = plugin;
    }
    // --- END NEW ---


    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Chunk chunk = event.getBlock().getChunk();

        // --- UPDATED CHECK ---
        LagManager manager = plugin.getLagManager();
        if (manager.isFrozen(chunk)) {
            event.setCancelled(true); // Stop the event
            return; // Don't count it
        }
        if (manager.isIgnored(chunk)) {
            return; // It's ignored, do nothing (don't even count it)
        }
        // --- END UPDATE ---

        // If the chunk isn't frozen or ignored, we count the event
        eventCounter.compute(chunk, (c, count) -> (count == null) ? 1 : count + 1);
    }



    public Map<Chunk, Integer> getAndResetCounts() {
        Map<Chunk, Integer> countsSnapshot = new ConcurrentHashMap<>(eventCounter);
        eventCounter.clear();
        return countsSnapshot;
    }

}
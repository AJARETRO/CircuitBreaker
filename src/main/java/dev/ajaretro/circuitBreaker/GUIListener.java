package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class GUIListener implements Listener {

    private final CircuitBreaker plugin;

    public GUIListener(CircuitBreaker plugin) {
        this.plugin = plugin;
    }

    public void openMainGui(Player player) {
        LagManager manager = plugin.getLagManager();
        double tps = 20.0;
        double mspt = 20.0;
        try {
            tps = Bukkit.getTPS()[0];
            mspt = Bukkit.getAverageTickTime();
        } catch (Throwable ignored) {}

        ControlPanelHolder holder = new ControlPanelHolder("main", 27, ChatColor.DARK_RED + "CircuitBreaker Control Panel");
        Inventory inv = holder.getInventory();

        // Fill background with gray glass
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Server Health
        inv.setItem(10, createGuiItem(Material.BEACON, ChatColor.GREEN + "Server Health Status",
            ChatColor.GRAY + "TPS: " + ChatColor.AQUA + String.format("%.2f", tps),
            ChatColor.GRAY + "MSPT: " + ChatColor.AQUA + String.format("%.1f", mspt) + "ms",
            ChatColor.GRAY + "Sentinel Mode: " + ChatColor.GREEN + "Active"
        ));

        // Frozen Chunks
        inv.setItem(12, createGuiItem(Material.REDSTONE_BLOCK, ChatColor.RED + "Frozen Chunks List",
            ChatColor.GRAY + "Currently Frozen: " + ChatColor.YELLOW + manager.getFrozenChunks().size(),
            " ",
            ChatColor.YELLOW + "Click to open Frozen Chunks viewer."
        ));

        // Ignored Chunks
        inv.setItem(14, createGuiItem(Material.BARRIER, ChatColor.GRAY + "Whitelisted Chunks",
            ChatColor.GRAY + "Currently Ignored: " + ChatColor.YELLOW + manager.getIgnoredChunksCount(),
            " ",
            ChatColor.YELLOW + "Click to view whitelisted areas."
        ));

        // Stats
        inv.setItem(16, createGuiItem(Material.BOOK, ChatColor.GOLD + "Sentinel Statistics",
            ChatColor.GRAY + "Lag Machines Stopped: " + ChatColor.LIGHT_PURPLE + manager.getLagMachinesStopped(),
            ChatColor.GRAY + "Physics Loops Defused: " + ChatColor.LIGHT_PURPLE + manager.getPhysicsEventsDefused(),
            ChatColor.GRAY + "Active Tracked Playtime: " + ChatColor.LIGHT_PURPLE + manager.getTotalPlaytimeMinutes() + " mins"
        ));

        player.openInventory(inv);
    }

    public void openFrozenChunksGui(Player player) {
        LagManager manager = plugin.getLagManager();
        List<ChunkKey> frozen = new ArrayList<>(manager.getFrozenChunks());

        ControlPanelHolder holder = new ControlPanelHolder("frozen", 54, ChatColor.RED + "Frozen Chunks Viewer");
        Inventory inv = holder.getInventory();

        // Fill background with black glass
        ItemStack border = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Back button
        inv.setItem(49, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Back to Main Panel"));

        // Populate frozen chunks
        int limit = Math.min(frozen.size(), 45);
        for (int i = 0; i < limit; i++) {
            ChunkKey key = frozen.get(i);
            World world = Bukkit.getWorld(key.getWorldUid());
            String worldName = world != null ? world.getName() : "unknown";
            inv.setItem(i, createGuiItem(Material.RED_WOOL, ChatColor.RED + "Frozen: " + worldName + " [" + key.getX() + ", " + key.getZ() + "]",
                ChatColor.GRAY + "Coordinates: " + (key.getX() << 4) + ", " + (key.getZ() << 4),
                " ",
                ChatColor.GREEN + "Left-Click: Teleport to Chunk",
                ChatColor.RED + "Right-Click: Unfreeze Chunk"
            ));
        }

        player.openInventory(inv);
    }

    public void openIgnoredChunksGui(Player player) {
        LagManager manager = plugin.getLagManager();
        List<String> ignored = new ArrayList<>(manager.getIgnoredChunksList());

        ControlPanelHolder holder = new ControlPanelHolder("ignored", 54, ChatColor.GRAY + "Whitelisted Chunks Viewer");
        Inventory inv = holder.getInventory();

        // Fill background
        ItemStack border = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border);
        }

        // Back button
        inv.setItem(49, createGuiItem(Material.ARROW, ChatColor.YELLOW + "Back to Main Panel"));

        int limit = Math.min(ignored.size(), 45);
        for (int i = 0; i < limit; i++) {
            String ident = ignored.get(i);
            String[] parts = ident.split(":");
            if (parts.length < 3) continue;
            
            try {
                UUID worldUid = UUID.fromString(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                World world = Bukkit.getWorld(worldUid);
                String worldName = world != null ? world.getName() : "unknown";

                inv.setItem(i, createGuiItem(Material.WHITE_WOOL, ChatColor.GRAY + "Whitelisted: " + worldName + " [" + x + ", " + z + "]",
                    ChatColor.GRAY + "Coordinates: " + (x << 4) + ", " + (z << 4),
                    " ",
                    ChatColor.GREEN + "Left-Click: Teleport to Chunk",
                    ChatColor.RED + "Right-Click: Remove Whitelist"
                ));
            } catch (Exception ignoredErr) {}
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ControlPanelHolder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ControlPanelHolder holder = (ControlPanelHolder) event.getInventory().getHolder();
            String type = holder.getGuiType();
            int slot = event.getRawSlot();

            if (type.equals("main")) {
                if (slot == 12) {
                    openFrozenChunksGui(player);
                } else if (slot == 14) {
                    openIgnoredChunksGui(player);
                }
            } else if (type.equals("frozen")) {
                if (slot == 49) {
                    openMainGui(player);
                    return;
                }
                if (slot >= 0 && slot < 45) {
                    LagManager manager = plugin.getLagManager();
                    List<ChunkKey> frozen = new ArrayList<>(manager.getFrozenChunks());
                    if (slot < frozen.size()) {
                        ChunkKey key = frozen.get(slot);
                        if (event.isLeftClick()) {
                            World world = Bukkit.getWorld(key.getWorldUid());
                            if (world != null) {
                                player.teleport(new Location(world, key.getX() << 4, 100, key.getZ() << 4));
                                player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Teleported to frozen chunk.");
                            }
                        } else if (event.isRightClick()) {
                            manager.getFrozenChunks().remove(key);
                            player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + "Chunk unfrozen successfully.");
                            openFrozenChunksGui(player); // Refresh GUI
                        }
                    }
                }
            } else if (type.equals("ignored")) {
                if (slot == 49) {
                    openMainGui(player);
                    return;
                }
                if (slot >= 0 && slot < 45) {
                    LagManager manager = plugin.getLagManager();
                    List<String> ignored = new ArrayList<>(manager.getIgnoredChunksList());
                    if (slot < ignored.size()) {
                        String ident = ignored.get(slot);
                        String[] parts = ident.split(":");
                        if (parts.length >= 3) {
                            try {
                                UUID worldUid = UUID.fromString(parts[0]);
                                int x = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                if (event.isLeftClick()) {
                                    World world = Bukkit.getWorld(worldUid);
                                    if (world != null) {
                                        player.teleport(new Location(world, x << 4, 100, z << 4));
                                        player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GRAY + "Teleported to whitelisted chunk.");
                                    }
                                } else if (event.isRightClick()) {
                                    manager.getIgnoredChunksList().remove(ident);
                                    manager.saveIgnoredChunks();
                                    player.sendMessage(ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.GREEN + "Chunk removed from ignore list.");
                                    openIgnoredChunksGui(player); // Refresh GUI
                                }
                            } catch (Exception ignoredErr) {}
                        }
                    }
                }
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}

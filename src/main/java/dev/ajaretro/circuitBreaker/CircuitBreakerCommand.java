package dev.ajaretro.circuitBreaker;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CircuitBreakerCommand implements CommandExecutor, TabCompleter {

    private final CircuitBreaker plugin;
    private final String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CB-Admin" + ChatColor.DARK_RED + "] " + ChatColor.YELLOW;

    public CircuitBreakerCommand(CircuitBreaker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        // We already have the permission on the command in plugin.yml, but this is a good safety check
        if (!sender.hasPermission("circuitbreaker.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        Player player = (Player) sender;
        Chunk chunk = player.getLocation().getChunk();
        String chunkCoords = "[" + chunk.getX() + ", " + chunk.getZ() + "]";

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        LagManager manager = plugin.getLagManager();

        switch (subCommand) {
            case "unfreeze":
                int radius = -1;
                if (args.length > 1) {
                    try {
                        radius = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(prefix + ChatColor.RED + "Invalid radius number. Usage: /cb unfreeze [radius]");
                        return true;
                    }
                }
                
                int count = manager.unfreezeArea(player.getLocation(), radius);
                if (count > 0) {
                    if (radius >= 0) {
                        player.sendMessage(prefix + "Unfrozen " + count + " chunks within a " + radius + "-chunk radius.");
                    } else {
                        player.sendMessage(prefix + "Unfrozen " + count + " chunks within a 10x10 block area.");
                    }
                } else {
                    player.sendMessage(prefix + "No frozen chunks found in the designated area.");
                }
                break;

            case "top":
                java.util.Map<ChunkKey, Integer> snapshot = plugin.getLagListener().getLastSnapshot();
                if (snapshot.isEmpty()) {
                    player.sendMessage(prefix + "No block physics activity recorded in the last second.");
                    return true;
                }
                
                java.util.List<java.util.Map.Entry<ChunkKey, Integer>> sorted = snapshot.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(5)
                        .collect(Collectors.toList());
                        
                player.sendMessage(prefix + "--- Top 5 Chunks by Block Physics Updates ---");
                for (int i = 0; i < sorted.size(); i++) {
                    java.util.Map.Entry<ChunkKey, Integer> entry = sorted.get(i);
                    ChunkKey k = entry.getKey();
                    player.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " + ChatColor.RED + 
                            "[" + k.getX() + ", " + k.getZ() + "]" + ChatColor.YELLOW + " - " + 
                            entry.getValue() + " events (" + manager.getChunkStatus(k) + ")");
                }
                break;

            case "ignore":
                if (manager.addChunkToIgnoreList(chunk)) {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " will now be ignored.");
                } else {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " is already being ignored.");
                }
                break;

            case "unignore":
                if (manager.removeChunkFromIgnoreList(chunk)) {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " is no longer being ignored.");
                } else {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " was not on the ignore list.");
                }
                break;

            case "status":
                String status = manager.getChunkStatus(chunk);
                player.sendMessage(prefix + "Chunk " + chunkCoords + " status: " + status);
                break;

            case "gui":
            case "panel":
                plugin.getGuiListener().openMainGui(player);
                break;

            case "reload":
                manager.reload();
                player.sendMessage(prefix + ChatColor.GREEN + "Configuration reloaded successfully.");
                break;

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix + "--- CircuitBreaker Admin ---");
        sender.sendMessage(ChatColor.AQUA + "/cb gui" + ChatColor.GRAY + " - Opens the visual sentinel control panel GUI.");
        sender.sendMessage(ChatColor.AQUA + "/cb status" + ChatColor.GRAY + " - Checks the status of your current chunk.");
        sender.sendMessage(ChatColor.AQUA + "/cb unfreeze [radius]" + ChatColor.GRAY + " - Unfreezes a chunk radius (default: 10x10 blocks).");
        sender.sendMessage(ChatColor.AQUA + "/cb top" + ChatColor.GRAY + " - Shows the top 5 chunks with highest physics activity.");
        sender.sendMessage(ChatColor.AQUA + "/cb ignore" + ChatColor.GRAY + " - Makes the plugin ignore your current chunk.");
        sender.sendMessage(ChatColor.AQUA + "/cb unignore" + ChatColor.GRAY + " - Removes your current chunk from the ignore list.");
        sender.sendMessage(ChatColor.AQUA + "/cb reload" + ChatColor.GRAY + " - Reloads the configuration from config.yml.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("status", "gui", "panel", "unfreeze", "top", "ignore", "unignore", "reload", "help");
            // Return a list of commands that start with what the user is typing
            return commands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // No suggestions for args 2+
        return null;
    }
}
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
                if (manager.manuallyUnfreezeChunk(chunk)) {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " has been manually unfrozen.");
                } else {
                    player.sendMessage(prefix + "Chunk " + chunkCoords + " was not frozen.");
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

            default:
                sendHelp(player);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix + "--- CircuitBreaker Admin ---");
        sender.sendMessage(ChatColor.AQUA + "/cb status" + ChatColor.GRAY + " - Checks the status of your current chunk.");
        sender.sendMessage(ChatColor.AQUA + "/cb unfreeze" + ChatColor.GRAY + " - Manually unfreezes your current chunk.");
        sender.sendMessage(ChatColor.AQUA + "/cb ignore" + ChatColor.GRAY + " - Makes the plugin ignore your current chunk.");
        sender.sendMessage(ChatColor.AQUA + "/cb unignore" + ChatColor.GRAY + " - Removes your current chunk from the ignore list.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("status", "unfreeze", "ignore", "unignore", "help");
            // Return a list of commands that start with what the user is typing
            return commands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // No suggestions for args 2+
        return null;
    }
}
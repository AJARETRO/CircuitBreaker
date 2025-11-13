package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class CircuitBreaker extends JavaPlugin {

    private LagListener lagListener;
    private LagManager lagManager;

    @Override
    public void onEnable() {
        if (isFolia()) {
            logFoliaWarning();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        Bukkit.getConsoleSender().sendMessage(prefix + "Enabling CircuitBreaker v" + ChatColor.WHITE + getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        Bukkit.getConsoleSender().sendMessage(prefix + "Website: " + ChatColor.AQUA + getDescription().getWebsite());
        Bukkit.getConsoleSender().sendMessage(prefix + "Initializing lag detection systems...");

        this.lagListener = new LagListener(this);
        getServer().getPluginManager().registerEvents(this.lagListener, this);
        Bukkit.getConsoleSender().sendMessage(prefix + "LagListener registered.");

        this.lagManager = new LagManager(this);

        CircuitBreakerCommand cbCommand = new CircuitBreakerCommand(this);
        getCommand("circuitbreaker").setExecutor(cbCommand);
        getCommand("circuitbreaker").setTabCompleter(cbCommand);
        Bukkit.getConsoleSender().sendMessage(prefix + "Admin command /cb registered.");

        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.GREEN + "Successfully enabled!");
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
    }

    @Override
    public void onDisable() {
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        Bukkit.getConsoleSender().sendMessage(prefix + "Disabling CircuitBreaker...");
        Bukkit.getConsoleSender().sendMessage(prefix + "Thank you for using the plugin!");
        Bukkit.getConsoleSender().sendMessage(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void logFoliaWarning() {
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.RED + "FOLIA DETECTED! This plugin is incompatible.");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "Folia's design (separate threads per region) makes");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "traditional lag machines nearly impossible, as they");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "would only break the owner's gameplay, not the server.");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.RED + "CircuitBreaker will now be disabled.");
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
    }

    public LagListener getLagListener() {
        return lagListener;
    }

    public LagManager getLagManager() {
        return lagManager;
    }
}
package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit; // We need this import!
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class CircuitBreaker extends JavaPlugin {

    private LagListener lagListener;
    private LagManager lagManager;

    @Override
    public void onEnable() {
        // --- NEW: FOLIA CHECK ---
        if (isFolia()) {
            logFoliaWarning();
            getServer().getPluginManager().disablePlugin(this);
            return; // Stop onEnable() right here
        }
        // --- END FOLIA CHECK ---

        saveDefaultConfig();

        // --- Our Sexy Startup Messages ---
        // We are using direct ChatColor concatenation now!
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        // --- SWITCHED TO Bukkit.getConsoleSender() ---
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        Bukkit.getConsoleSender().sendMessage(prefix + "Enabling CircuitBreaker v" + ChatColor.WHITE + getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        Bukkit.getConsoleSender().sendMessage(prefix + "Website: " + ChatColor.AQUA + getDescription().getWebsite());
        Bukkit.getConsoleSender().sendMessage(prefix + "Initializing lag detection systems...");

        // 1. Create the listener
        this.lagListener = new LagListener(this);

        // 2. Register the listener
        getServer().getPluginManager().registerEvents(this.lagListener, this);
        Bukkit.getConsoleSender().sendMessage(prefix + "LagListener registered.");

        // 3. Create the manager (this starts the ticker AND loads saved data)
        this.lagManager = new LagManager(this);
        // NOTE: The LagManager needs the same update for its logs!
        Bukkit.getConsoleSender().sendMessage(prefix + "LagManager initialized and ticker is running.");

        // 4. Register the Admin Command
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

    // --- FOLIA CHECK METHODS ---
    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Logs your custom Folia incompatibility message to the console
     * using the console sender for color.
     */
    private void logFoliaWarning() {
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        // --- SWITCHED TO Bukkit.getConsoleSender() ---
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.RED + "FOLIA DETECTED! This plugin is incompatible.");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "Folia's design (separate threads per region) makes");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "traditional lag machines nearly impossible, as they");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.YELLOW + "would only break the owner's gameplay, not the server.");
        Bukkit.getConsoleSender().sendMessage(prefix + ChatColor.RED + "CircuitBreaker will now be disabled.");
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
    }
    // --- END NEW METHODS ---

    public LagListener getLagListener() {
        return lagListener;
    }

    public LagManager getLagManager() {
        return lagManager;
    }
}
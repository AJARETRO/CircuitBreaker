package dev.ajaretro.circuitBreaker;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

// We don't need to import the command class, it's in the same package

public final class CircuitBreaker extends JavaPlugin {

    private LagListener lagListener;
    private LagManager lagManager;

    @Override
    public void onEnable() {
        // --- NEW: FOLIA CHECK ---
        // We do this *before* anything else.
        if (isFolia()) {
            // Log your custom message
            logFoliaWarning();
            // Disable the plugin
            getServer().getPluginManager().disablePlugin(this);
            return; // Stop onEnable() right here
        }
        // --- END FOLIA CHECK ---

        // This loads our config.yml
        saveDefaultConfig();

        // --- Our Sexy Startup Messages ---
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        getLogger().info(ChatColor.DARK_RED + "==================================================");
        getLogger().info(prefix + "Enabling CircuitBreaker v" + getDescription().getVersion());
        getLogger().info(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        getLogger().info(prefix + "Website: " + ChatColor.AQUA + "ajaretro.dev");
        getLogger().info(prefix + "Initializing lag detection systems...");
        // --- End Startup Messages ---

        // 1. Create the listener
        this.lagListener = new LagListener(this);

        // 2. Register the listener
        getServer().getPluginManager().registerEvents(this.lagListener, this);
        getLogger().info(prefix + "LagListener registered.");

        // 3. Create the manager (this starts the ticker AND loads saved data)
        this.lagManager = new LagManager(this);
        getLogger().info(prefix + "LagManager initialized and ticker is running.");

        // 4. Register the Admin Command
        CircuitBreakerCommand cbCommand = new CircuitBreakerCommand(this);
        getCommand("circuitbreaker").setExecutor(cbCommand);
        getCommand("circuitbreaker").setTabCompleter(cbCommand);
        getLogger().info(prefix + "Admin command /cb registered.");

        getLogger().info(prefix + ChatColor.GREEN + "Successfully enabled!");
        getLogger().info(ChatColor.DARK_RED + "==================================================");
    }

    @Override
    public void onDisable() {
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        getLogger().info(ChatColor.DARK_RED + "==================================================");
        getLogger().info(prefix + "Disabling CircuitBreaker...");
        getLogger().info(prefix + "Thank you for using the plugin!");
        getLogger().info(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        getLogger().info(ChatColor.DARK_RED + "==================================================");
    }

    // --- NEW: FOLIA CHECK METHODS ---
    /**
     * Checks if the server is running Folia by looking for a Folia-specific class.
     * @return true if Folia is detected, false otherwise.
     */
    private boolean isFolia() {
        try {
            // This is the standard way to check for Folia.
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true; // Class found, it's Folia
        } catch (ClassNotFoundException e) {
            return false; // Class not found, it's Paper/Spigot
        }
    }

    /**
     * Logs your custom Folia incompatibility message to the console.
     */
    private void logFoliaWarning() {
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;
        getLogger().severe(ChatColor.DARK_RED + "==================================================");
        getLogger().severe(prefix + ChatColor.RED + "FOLIA DETECTED! This plugin is incompatible.");
        getLogger().severe(prefix + ChatColor.YELLOW + "Folia's design (separate threads per region) makes");
        getLogger().severe(prefix + ChatColor.YELLOW + "traditional lag machines nearly impossible, as they");
        getLogger().severe(prefix + ChatColor.YELLOW + "would only break the owner's gameplay, not the server.");
        getLogger().severe(prefix + ChatColor.RED + "CircuitBreaker will now be disabled.");
        getLogger().severe(ChatColor.DARK_RED + "==================================================");
    }
    // --- END NEW METHODS ---

    public LagListener getLagListener() {
        return lagListener;
    }

    public LagManager getLagManager() {
        return lagManager;
    }
}
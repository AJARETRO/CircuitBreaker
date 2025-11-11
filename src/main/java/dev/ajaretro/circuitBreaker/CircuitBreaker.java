package dev.ajaretro.circuitBreaker;

// --- NEW ---
// We need to import ChatColor to use colors!
import org.bukkit.ChatColor;
// --- END NEW ---
import org.bukkit.plugin.java.JavaPlugin;

public final class CircuitBreaker extends JavaPlugin {

    private LagListener lagListener;
    private LagManager lagManager;

    @Override
    public void onEnable() {
        // This is where we load the config file
        saveDefaultConfig();

        // --- NEW SEXY MESSAGES ---
        // We use getLogger().info() so the messages send to the console.
        // We can build the strings with ChatColor.
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        getLogger().info(ChatColor.DARK_RED + "==================================================");
        getLogger().info(prefix + "Enabling CircuitBreaker v" + getDescription().getVersion());
        getLogger().info(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        getLogger().info(prefix + "Website: " + ChatColor.AQUA + "ajaretro.dev");
        getLogger().info(prefix + "Initializing lag detection systems...");
        // --- END NEW MESSAGES ---

        // 1. Create the listener
        this.lagListener = new LagListener(this);

        // 2. Register the listener
        getServer().getPluginManager().registerEvents(this.lagListener, this);
        getLogger().info(prefix + "LagListener registered.");

        // 3. Create the manager (this starts the ticker)
        this.lagManager = new LagManager(this);
        getLogger().info(prefix + "LagManager initialized and ticker is running.");

        getLogger().info(prefix + ChatColor.GREEN + "Successfully enabled!");
        getLogger().info(ChatColor.DARK_RED + "==================================================");
    }

    @Override
    public void onDisable() {
        // --- NEW SEXY MESSAGES ---
        String prefix = ChatColor.DARK_RED + "[" + ChatColor.RED + "CircuitBreaker" + ChatColor.DARK_RED + "] " + ChatColor.GRAY;

        getLogger().info(ChatColor.DARK_RED + "==================================================");
        getLogger().info(prefix + "Disabling CircuitBreaker...");
        getLogger().info(prefix + "Thank you for using the plugin!");
        getLogger().info(prefix + "Author: " + ChatColor.AQUA + "AJARETRO");
        getLogger().info(ChatColor.DARK_RED + "==================================================");
        // --- END NEW MESSAGES ---
    }

    // "getter" for the listener
    public LagListener getLagListener() {
        return lagListener;
    }

    // "getter" for the manager
    public LagManager getLagManager() {
        return lagManager;
    }
}
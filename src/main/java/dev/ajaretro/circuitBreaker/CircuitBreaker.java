/*
 * Copyright (c) 2026 AJA_RETRO (https://ajaretro.dev). All Rights Reserved.
 * 
 * This source code and compiled binaries are the intellectual property of the author.
 * Redistribution, modification, or derivative works are strictly prohibited under the
 * terms of the Source-Available License.
 */

package dev.ajaretro.circuitBreaker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bstats.charts.SimplePie;

public final class CircuitBreaker extends JavaPlugin {

    private LagListener lagListener;
    private LagManager lagManager;
    private GUIListener guiListener;

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

        PacketInterceptor packetInterceptor = new PacketInterceptor(this);
        getServer().getPluginManager().registerEvents(packetInterceptor, this);
        Bukkit.getConsoleSender().sendMessage(prefix + "PacketInterceptor registered.");

        this.lagManager = new LagManager(this);

        this.guiListener = new GUIListener(this);
        getServer().getPluginManager().registerEvents(this.guiListener, this);
        Bukkit.getConsoleSender().sendMessage(prefix + "GUIListener registered.");

        // Initialize bStats
        try {
            Metrics metrics = new Metrics(this, 32242);
            metrics.addCustomChart(new SingleLineChart("lag_machines_stopped", () -> this.lagManager.getLagMachinesStopped()));
            metrics.addCustomChart(new SingleLineChart("physics_events_defused", () -> this.lagManager.getPhysicsEventsDefused()));
            metrics.addCustomChart(new SingleLineChart("total_playtime", () -> this.lagManager.getTotalPlaytimeMinutes()));
            metrics.addCustomChart(new SimplePie("physics_lag_enabled", () -> String.valueOf(this.lagManager.isPhysicsLagEnabled())));
            metrics.addCustomChart(new SimplePie("entity_culling_enabled", () -> String.valueOf(this.lagManager.isEntityCullingEnabled())));
            metrics.addCustomChart(new SimplePie("world_count", () -> String.valueOf(Bukkit.getWorlds().size())));
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats: " + e.getMessage());
        }

        CircuitBreakerCommand cbCommand = new CircuitBreakerCommand(this);
        getCommand("circuitbreaker").setExecutor(cbCommand);
        getCommand("circuitbreaker").setTabCompleter(cbCommand);
        Bukkit.getConsoleSender().sendMessage(prefix + "Admin command /cb registered.");

        // Check for updates asynchronously
        new UpdateChecker(this).checkForUpdates();

        // Check for DeMalware-RETRO active agent
        if (System.getProperty("demalware.agent.active") == null) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[CircuitBreaker] [SECURITY] WARNING: DeMalware-RETRO is not installed or early-boot protection is inactive!");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[CircuitBreaker] [SECURITY] Please install it to protect your server from malicious plugins and backdoors.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[CircuitBreaker] [SECURITY] Modrinth: https://modrinth.com/mod/demalware-retro");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[CircuitBreaker] [SECURITY] GitHub: https://github.com/AJARETRO/DeMalware-RETRO");
            Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_RED + "==================================================");
        }

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

    public GUIListener getGuiListener() {
        return guiListener;
    }
}
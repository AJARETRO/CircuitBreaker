package dev.ajaretro.circuitBreaker;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PacketInterceptor implements Listener {

    private final CircuitBreaker plugin;
    private final Map<UUID, AtomicInteger> packetCounts = new ConcurrentHashMap<>();
    private final int packetThreshold;
    private final boolean enabled;

    public PacketInterceptor(CircuitBreaker plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("packet-sentinel.enabled", true);
        this.packetThreshold = plugin.getConfig().getInt("packet-sentinel.threshold-per-second", 600);
        
        if (enabled) {
            // Reset packet rates every second (20 ticks)
            Bukkit.getScheduler().runTaskTimer(plugin, packetCounts::clear, 20L, 20L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!enabled) return;
        removePlayer(event.getPlayer());
    }

    private void injectPlayer(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            
            // Find connection field dynamically by matching object class type
            Object connection = null;
            for (Field field : handle.getClass().getFields()) {
                if (field.getType().getName().contains("ServerGamePacketListenerImpl") || 
                    field.getType().getName().contains("PlayerConnection")) {
                    connection = field.get(handle);
                    break;
                }
            }
            if (connection == null) return;

            // Find networkManager/connection field dynamically
            Object networkManager = null;
            for (Field field : connection.getClass().getFields()) {
                if (field.getType().getName().contains("Connection") || 
                    field.getType().getName().contains("NetworkManager")) {
                    networkManager = field.get(connection);
                    break;
                }
            }
            if (networkManager == null) return;

            // Find Netty Channel field dynamically
            Channel channel = null;
            for (Field field : networkManager.getClass().getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    channel = (Channel) field.get(networkManager);
                    break;
                }
            }

            if (channel == null) return;

            ChannelDuplexHandler handler = new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    UUID uuid = player.getUniqueId();
                    AtomicInteger count = packetCounts.computeIfAbsent(uuid, k -> new AtomicInteger(0));
                    int currentRate = count.incrementAndGet();

                    if (currentRate > packetThreshold) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.kickPlayer(ChatColor.RED + "Kicked for sending too many packets (Packet Spam).");
                            plugin.getServer().getConsoleSender().sendMessage(
                                ChatColor.DARK_RED + "[CircuitBreaker] " + ChatColor.YELLOW + 
                                "Kicked player " + player.getName() + " for packet spam (" + currentRate + " pkts/sec)"
                            );
                        });
                        return;
                    }
                    super.channelRead(ctx, msg);
                }
            };

            channel.pipeline().addBefore("packet_handler", "circuitbreaker_handler", handler);

        } catch (Exception e) {
            // Ignore reflection errors on unsupported servers
        }
    }

    private void removePlayer(Player player) {
        packetCounts.remove(player.getUniqueId());
    }
}

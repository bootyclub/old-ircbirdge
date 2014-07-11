package us.zenyth.ircbridge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class IRCEvents implements Listener {
    private final IRCBridge plugin;
    public IRCEvents(IRCBridge instance) {
          this.plugin = instance;
    }
    private IRCConnection connection;
    private Bridge bridge;
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {

            Player player = event.getPlayer();
            String name = player.getName();
            if (plugin.bridge.connections.containsKey(name)) {
                IRCConnection connection = plugin.bridge.connections.get(name);
                if (connection.isConnected()) {
                    connection.partAndQuit("Re-establishing connection.");
                }
            }
            plugin.log.info("IRCBridge: Connecting " + name + " to IRC.");
            plugin.bridge.connections.put(name, new IRCConnection(plugin, player));
            event.setJoinMessage(null);
        }

        public boolean playerLeft(Player player) {
            IRCConnection connection = plugin.bridge.connections.get(player.getName());
            if (connection != null) {
                connection.partAndQuit("Left Minecraft.");
                return true;
            }
            return false;
        }
        @EventHandler
        public void onPlayerKick(PlayerKickEvent event) {
            if (playerLeft(event.getPlayer())) {
                event.setLeaveMessage(null);
            }
        }
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            if (playerLeft(event.getPlayer())) {
                event.setQuitMessage(null);
            }
        }
         public String colorize(String message){
        return message.replaceAll("&([a-z0-9])", ChatColor.COLOR_CHAR + "$1");
          }
        @EventHandler
        public void OPC(AsyncPlayerChatEvent event) {
          // Try to support color codes!
            if(event.getPlayer().hasPermission("ircbridge.colorize")) {
                event.setMessage(colorize(event.getMessage()));
            }
        }
        @EventHandler
        public void onPlayerChat(AsyncPlayerChatEvent event) {
            String message = event.getMessage();
            Player player = event.getPlayer();
            IRCConnection connection = plugin.bridge.connections.get(player.getName());
             
            if (!message.startsWith("/")
                && connection != null && connection.isConnected()) {
                // We'll handle this ourselves.
                event.setCancelled(true);

                connection.say(message);
            } if(connection == null && !connection.isConnected()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You are not connected to IRC! Please type /reconnect!");
                connection.tellUser(ChatColor.RED + "You are not connected to IRC! Please type /reconnect!");
            }
        }
    	
    	@EventHandler
    	public void onEntityDeath(EntityDeathEvent event)
    	{
    		
    		if(event instanceof PlayerDeathEvent)
    		{
    			PlayerDeathEvent pevent = (PlayerDeathEvent) event;
    			IRCConnection connection = plugin.bridge.connections.get("*CONSOLE*");
    			if(connection != null && connection.isConnected())
    			{
    				String dMessage = pevent.getDeathMessage();
    				dMessage = dMessage.replaceAll("\\u00A7.", "");
        			connection.sendMessage(plugin.default_channel, "Event: " + dMessage);
    			}
    		}
    	}
    }

package us.zenyth.ircbridge;

import java.util.HashMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
public class Bridge implements Listener {
      public Bridge(IRCBridge instance) {
          this.plugin = instance;
      }
        private final IRCBridge plugin;
        public IRCConnection connection;
        
        final HashMap<String,IRCConnection>
            connections = new HashMap<String,IRCConnection>();

        public void connectAll(Player[] players) {
            connectPlayer(null);
            for (Player player : players) {
                connectPlayer(player);
            }
        }

        public void connectPlayer(Player player) {
            String name = "*CONSOLE*";
            if (player != null) {
                name = player.getName();
            }

            plugin.log.info("IRCBridge: Reconnecting " + name + " to IRC.");
            connections.put(name, new IRCConnection(plugin, player));
        }

        public void quitAll() {
            for (IRCConnection connection : connections.values()) {
                connection.quitServer("IRCBridge closing.");
            }
            connections.clear();
        }

    }

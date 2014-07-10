package us.zenyth.ircbridge;

import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class Commands implements CommandExecutor {
      private  final IRCBridge plugin;
      public Commands(IRCBridge plugin) {
          this.plugin = plugin;
      }
      private IRCConnection connection;
      
      
public boolean onCommand(CommandSender sender, Command command,
                             String commandLabel, String[] args) {
        Player player = null;
        String name = "*CONSOLE*";
        String cmd = command.getName();
        if (sender instanceof Player) {
            player = (Player) sender;
            name = player.getName();
        }
        
         IRCConnection connection = plugin.bridge.connections.get(name);
        
        if (connection == null || !connection.isConnected()) {
            if (cmd.equalsIgnoreCase("say") || cmd.equalsIgnoreCase("to") ||
                cmd.equalsIgnoreCase("reconnect")) {
                connection = null;
            } else if (!cmd.equalsIgnoreCase("list")) {
                sender.sendMessage("You're not connected to IRC.  "
                                   + "(try /reconnect)");
                return true;
            }
        }

        if (cmd.equalsIgnoreCase("join")) {
            if (args.length < 1 || args.length > 2) {
                return false;
            }

            if (!args[0].startsWith("#")) {
                connection.tellUser(ChatColor.RED
                                    + "Channel names must start with #", true);
                return true;
            }

            for (Map.Entry<String,String> restricted
                                            : plugin.permission_channels.entrySet()) {
                if (restricted.getValue().equalsIgnoreCase(args[0])) {
                    if (!sender.hasPermission("ircbridge."
                                              + restricted.getKey())) {
                        connection.tellUser(ChatColor.RED
                                            + "Cannot join channel "
                                            + "(Invite only)", true);
                        return true;
                    } else {
                        break;
                    }
                }
            }

            // Get a full user list on join.
            connection.who_target = args[0];
            connection.who_page = 1;
            connection.who_mode = plugin.ALL;

            if (args.length == 1) {
                connection.joinChannel(args[0]);
            } else {
                connection.joinChannel(args[0], args[1]);
            }
        } else if (cmd.equalsIgnoreCase("part")) {
            if (args.length != 1) {
                return false;
            }

            if (!args[0].startsWith("#")) {
                connection.tellUser(ChatColor.RED
                                    + "Channel names must start with #", true);
                return true;
            }

            connection.partChannel(args[0]);
        } else if (cmd.equalsIgnoreCase("switch")) {
            if (args.length > 1) {
                return false;
            }

            String target;
            if (args.length == 0) {
                target = plugin.default_channel;
            } else {
                target = connection.matchUser(args[0]);
            }

            connection.speaking_to = target;
            connection.tellUser(ChatColor.YELLOW
                                + "Your messages will now go to "
                                + connection.convertName(target, false)
                                + ChatColor.YELLOW + ".", true);
        } else if (cmd.equalsIgnoreCase("to")) {
            if (args.length < 2) {
                return false;
            }

            String message = "";
            for (int i=1; i<args.length; i++) {
                message += args[i] + " ";
            }

            if (connection != null) {
                String target = connection.matchUser(args[0]);
                connection.say(message.trim(), target);
            } else {
                List<Player> players = plugin.getServer().matchPlayer(args[0]);
                if (players.size() == 0) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                } else if (players.size() > 1) {
                    sender.sendMessage(ChatColor.RED + "Be more specific.");
                    return true;
                } else {
                    Player target = players.get(0);
                    sender.sendMessage(ChatColor.GREEN + "To "
                                       + target.getName() + ": "
                                       + ChatColor.WHITE + message.trim());
                    target.sendMessage(ChatColor.GREEN + "From " + name
                                       + ": " + ChatColor.WHITE
                                       + message.trim());
                    return true;
                }
            }
        } else if (cmd.equalsIgnoreCase("say")) {
            String message = "";
            for (int i=0; i<args.length; i++) {
                message += args[i] + " ";
            }

            if (connection != null) {
                connection.say(message.trim());
            } else {
                plugin.getServer().broadcastMessage("<" + name + "> " + message);
            }
        } else if (cmd.equalsIgnoreCase("me")) {
            String message = "";
            for (int i=0; i<args.length; i++) {
                message += args[i] + " ";
            }
            connection.say("/me " + message);
            // Will implement this at a later date. connection.sendAction(plugin, message);
        } else if (cmd.equalsIgnoreCase("list")) {
            if (!sender.hasPermission("ircbridge.list")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission."
                                   + "  (Try /who, /irc, or /users.)");
                return true;
            }

            String message = "Connected players: ";
            String sep = "";
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                message += sep + online.getName();
                sep = ", ";
            }
            sender.sendMessage(message);
        } else if (cmd.equalsIgnoreCase("who")) {
            if (args.length > 2) {
                return false;
            }

            connection.who_page = 0;
            connection.who_target = connection.speaking_to;
            if (args.length == 1) {
                if (args[0].startsWith("#")) {
                    // /<command> <#channel>
                    connection.who_target = args[0];
                } else {
                    // /<command> <page>
                    try {
                        connection.who_page = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            } else if (args.length == 2) {
                // /<command> <#channel> <page>
                if (!args[0].startsWith("#")) {
                    return false;
                }

                try {
                    connection.who_page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    return false;
                }

                connection.who_target = args[0];
            }

            String alias = commandLabel.toLowerCase();
            if (alias.startsWith("irc") || alias.startsWith("whoirc")) {
                connection.who_mode = plugin.IRC;
            } else if (   alias.equals("who")
                       || alias.contains("players")) {
                connection.who_mode = plugin.MINECRAFT;
            } else { // /all, /whoall, /names, /users
                connection.who_mode = plugin.ALL;
            }

            if (connection.who_target.startsWith("#")) {
                connection.sendRawLineViaQueue("NAMES "
                                               + connection.who_target);
            } else {
                connection.tellUser(ChatColor.RED + "You are talking to a "
                                    + "user, not a channel.", true);
            }
        } else if (cmd.equalsIgnoreCase("mode")) {
            if (args.length == 0) {
                return false;
            }

            if (args[0].startsWith("#")
                || args[0].equalsIgnoreCase(connection.my_name)) {
                String mode = "";
                for (int i=1; i<args.length; i++) {
                    mode += args[i] + " ";
                }
                connection.setMode(args[0], mode.trim());
            } else {
                String mode = "";
                for (int i=0; i<args.length; i++) {
                    mode += args[i] + " ";
                }

                if (connection.speaking_to.startsWith("#")) {
                    connection.setMode(connection.speaking_to, mode.trim());
                } else {
                    connection.tellUser(ChatColor.RED + "You are talking to a"
                                        + " user, not a channel.", true);
                }
            }
        } else if (cmd.equalsIgnoreCase("irckick")) {
            if (args.length < 1 || args.length > 2) {
                return false;
            }

            String channel;
            String target;
            if (args.length == 2) {
                channel = args[0];
                target = args[1];
            } else {
                channel = connection.speaking_to;
                target = args[0];
            }

            if (connection.speaking_to.startsWith("#")) {
                connection.kick(channel, target);
            } else {
                connection.tellUser(ChatColor.RED
                                    + "Channel names must start with #", true);
            }
        } else if (cmd.equalsIgnoreCase("reconnect")) {
            if (connection != null) {
                connection.tellUser(ChatColor.RED
                                    + "You're already connected!", true);
            } else {
                plugin.bridge.connectPlayer(player);
            }
        } else {
            return false;
        }

        return true;
    }   
}

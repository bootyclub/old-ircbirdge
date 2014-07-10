package us.zenyth.ircbridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;

  public class IRCConnection extends PircBot implements Runnable {
      
      private final IRCBridge plugin;
      public IRCConnection(IRCBridge instance) {
          this.plugin = instance;
      }

        public String my_name = null;
        public String speaking_to;

        public int who_page = 1;
        public String who_target = null;
        public int who_mode = IRCBridge.MINECRAFT;

        private Player player = null;
        ArrayList<String> AllowedChannels;

        public IRCConnection(IRCBridge plugin, CommandSender who) {
            this.plugin = plugin;
            if (who instanceof Player) {
                player = (Player) who;
            }
            AllowedChannels = new ArrayList<String>();
            // Join permission-based channels.
            for (String permission : plugin.permission_channels.keySet()) {
                if (player == null
                    || player.hasPermission("ircbridge." + permission)) {
                	AllowedChannels.add(plugin.permission_channels.get(permission));
                    //joinChannel(plugin.permission_channels.get(permission));
                }
            }
            Thread connection_thread = new Thread(this);
            connection_thread.start();
            //run();
        }

        public void run() {
        	
            // User info for console.
            String host = "localhost";
            String ip = "127.0.0.1";
            String name = "Console";
            String nick = plugin.console_id + "|Console";

            // If we're not console, use the player's info.
            if (player != null) {
                my_name = player.getName();
                ip = player.getAddress().getAddress().getHostAddress();
                host = player.getAddress().getHostName();
                name = my_name;
                nick = my_name + "|" + plugin.console_tag + "|MC";
                if (!Character.isLetter(nick.charAt(0))) {
                    nick = "_" + nick;
                }
            }

            // Set the nickname.
            setName(nick);
            setLogin(name);

            // Pass in the user's info via WEBIRC.
            //setPreNick("WEBIRC " + plugin.webirc_pass + " IRCBridge " + host
              //         + " " + ip);

            // Speak to the default channel.
            speaking_to = plugin.default_channel;

            // Get an initial user list from the default channel.
            who_target = speaking_to;

            // Connect to the server.
            try {
                connect(plugin.server_address, plugin.server_port,
                        plugin.server_pass);
                tellUser(ChatColor.GREEN + "Connected to IRC.", true);
            } catch (IOException e) {
                tellUser(ChatColor.RED + "Unable to connect to IRC.", true);
                plugin.complain("unable to connect to IRC", e);
            } catch (IrcException e) {
                tellUser(ChatColor.RED + "Unable to connect to IRC.", true);
                plugin.complain("unable to connect to IRC", e);
            }

            if (player == null) {
                // Console doesn't need a hostmask.
                setMode(nick, "-x");

                // Join the console channel.
                joinChannel(plugin.console_channel);
            }

            // Join the default channel.
            joinChannel(plugin.default_channel);

            // Join autojoin channels.
            for (String channel : plugin.autojoin_channels) {
                joinChannel(channel);
            }
            
            //Join the allowed channels
            for (String channel : this.AllowedChannels)
            {
            	joinChannel(channel);
            }
            
        }

        protected void partAndQuit(String reason) {
            for (String channel : getChannels()) {
                partChannel(channel, reason);
            }

            quitServer(reason);
        }

        @Override
	protected void onDisconnect() {
            tellUser(ChatColor.GRAY + "Connection to IRC lost.", true);
            tellUser(ChatColor.GRAY
                     + "Your default message target has been reset.", true);
        }

        public boolean isOfficial(String channel) {
            return plugin.official_channels.contains(channel);
        }

        @Override
	protected void onUserList(String channel, User[] users) {
            if (!channel.equalsIgnoreCase(who_target)) {
                // Ignore user lists unless requested.
                return;
            }

            String user_list = "";
            String sep = "";
            String page = "";

            boolean officialChannel = isOfficial(channel);

            int ignored = 0;
            boolean ignore_irc = (who_mode == IRCBridge.MINECRAFT);
            boolean ignore_minecraft = (who_mode == IRCBridge.IRC);

            HashMap<String,String> formats = new HashMap<String,String>();
            for (User user : users) {
                String name = user.getPrefix() + user.getNick();
                boolean is_minecraft = name.toUpperCase().endsWith("|" + plugin.console_tag + "|MC");
                if (   (ignore_irc       && !is_minecraft)
                    || (ignore_minecraft &&  is_minecraft)) {
                    ignored++;
                    continue;
                }

                formats.put(convertNameWithoutColor(name),
                            convertName(name, officialChannel));
            }

            Vector<String> user_names = new Vector<String>(formats.keySet());
            Collections.sort(user_names);

            int count = user_names.size();
            int pages = (int) Math.ceil(count / (float) plugin.WHO_PAGE_SIZE);
            if (pages == 1) {
                for (String user : user_names) {
                    user_list += sep + formats.get(user);
                    sep = ChatColor.WHITE + ", ";
                }
            } else {
                who_page = Math.max(0, Math.min(pages - 1, who_page - 1));
                page = " (page " + (who_page + 1) + "/" + pages + ")";

                for (int i = 0; i < plugin.WHO_PAGE_SIZE; i++) {
                    int user_id = i + (who_page * plugin.WHO_PAGE_SIZE);
                    if (user_id >= count) {
                        break;
                    }

                    String user = user_names.get(user_id);
                    user_list += sep + formats.get(user);
                    sep = ChatColor.WHITE + ", ";
                }
            }

            if ((ignored > 0) && ((pages == 1) || (who_page == (pages-1)))) {
                String s = ignored > 1 ? "s" : "";
                String start = ChatColor.WHITE + (count > 1 ? "," : "");
                if (ignore_irc) {
                    user_list += start + " and " + ignored + " IRC user" + s
                                 + " (see /irc or /users).";
                } else { // ignore_minecraft
                    user_list += start + " and " + ignored + " Minecraft user"
                                 + s + " (see /who or /users).";
                }
            }

            tellUser(formatChannel(channel) + ChatColor.WHITE + "Users" + page
                     + ": " + user_list);
        }

        @Override
	protected void onTopic(String channel, String topic, String setBy,
                               long date, boolean changed) {
            if (topic.trim().equals("")) {
                // Hide empty topics.
                return;
            }
            tellUser(formatChannel(channel) + ChatColor.GREEN + topic, true);
        }

        @Override
	protected void onJoin(String channel, String sender, String login,
                              String Hostname) {
            if (!plugin.big_channels.contains(channel)) {
                tellUser(formatChannel(channel) + ChatColor.YELLOW +
                     convertNameWithoutColor(sender) + " joined the channel.");
            }
        }

        @Override
	protected void onQuit(String sourceNick, String sourceLogin,
                              String sourceHostname, String reason) {
            if (   !sourceNick.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")
                && !sourceNick.toUpperCase().endsWith("|CONSOLE")) {
                tellUser(ChatColor.YELLOW + convertNameWithoutColor(sourceNick)
                         + " left IRC.");
            }
        }

        @Override
	protected void onPart(String channel, String sender, String login,
                              String Hostname) {
            if (!plugin.big_channels.contains(channel)) {
                tellUser(formatChannel(channel) + ChatColor.YELLOW +
                     convertNameWithoutColor(sender) + " left the channel.");
            }
        }

        @Override
	protected void onKick(String channel, String kickerNick,
                              String kickerLogin, String kickerHostname,
                              String recipientNick, String reason) {
            tellUser(formatChannel(channel) + ChatColor.YELLOW +
                     convertNameWithoutColor(recipientNick) + " was kicked by "
                     + convertNameWithoutColor(kickerNick) + ": " + reason, true);
        }

        @Override
	protected void onMode(String channel, String sourceNick,
                              String sourceLogin, String sourceHostname,
                              String mode) {
            if (isOfficial(channel) && sourceNick.equalsIgnoreCase("ChanServ")) {
                // Suppress ChanServ's ramblings in official channels.
                return;
            }
            tellUser(formatChannel(channel) + ChatColor.YELLOW +
                     convertNameWithoutColor(sourceNick) + " set mode " + mode, true);
        }

        @Override
	protected void onNickChange(String oldNick, String login,
                                    String hostname, String newNick) {
            tellUser(ChatColor.YELLOW + convertNameWithoutColor(oldNick)
                     + " is now known as " + convertNameWithoutColor(newNick), true);
        }

        @Override
	protected void onNotice(String sourceNick, String sourceLogin,
                                String sourceHostname, String target,
                                String notice) {
            if (sourceNick.toUpperCase().endsWith("SERV")) {
                if (sourceNick.equalsIgnoreCase("NickServ")
                    && notice.endsWith(" is not a registered nickname.")) {
                    // Shaddup, NickServ.  Minecraft users don't need to
                    // register if the IRC server is set up properly.
                    return;
                }

                tellUser(ChatColor.GOLD + convertNameWithoutColor(sourceNick)
                         + ": " + notice, true);
            }
        }

        @Override
	protected void onServerResponse(int code, String response) {
            if (code >= 400 && code < 500) {
                String message = response.substring(response.indexOf(":") + 1);
                tellUser(ChatColor.RED + message, true);
            }
        }

        @Override
	protected void onMessage(String channel, String sender, String login,
                                 String hostname, String message) {
            heard(sender, channel, message);
        }

        @Override
	protected void onPrivateMessage(String sender, String login,
                                        String hostname, String message) {
            plugin.logMessage(sender + "->" + getName() + ": " + message);
            heard(sender, getName(), message);
    }
        // This will be used soon.
       /* protected void onPrivateMessage(String sender, String message) {
        if (watchers.contains(player.getName())) {
            player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.ITALIC + "KittyWatch: " + ChatColor.RESET + sender + " -> " + getName() + ": " + message);
        }
        } */

        @Override
	protected void onAction(String sender, String login, String hostname,
                                String target, String action) {
            heard(sender, target, "/me " + action);
            if (!target.startsWith("#")) {
                plugin.logMessage(sender + "->" + getName() + ":* " + action);
            }
        }

        public void say(String message) {
            say(message, speaking_to);
        }

        public void say(String message, String target) {
            sendMessage(target, message);
            heard(getName(), target, message);
            if (!target.startsWith("#")) {
                plugin.logMessage(getName() + "->" + target + ": " + message);
            }
        }

        public String revertName(String name) {
            name = ChatColor.stripColor(name);
            if (name.startsWith("#")) {
                return name;
            } else if (name.equalsIgnoreCase("Console")) {
                return plugin.console_channel;
            } else if (name.toUpperCase().endsWith("|IRC")) {
                return name.substring(0, name.length()-4);
            } else if (!Character.isLetter(name.charAt(0))) {
                return "_" + name + "|" + plugin.console_tag + "|MC";
            } else {
                return name + "|" + plugin.console_tag + "|MC";
            }
        }
        
        public String formatName(String name, boolean officialChannel) {
        String worldName = player.getWorld().getName();
        if (name.startsWith("#")) {
            return ChatColor.GREEN + "";
        } else if (name.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")) {
            return "";
        } else if (name.toUpperCase().endsWith("|MC")) {
            return "";
        } else if (name.endsWith("|Console")) {
            return plugin.console_format;
        } else if (name.endsWith("Serv")) {
            return ChatColor.RED + "" + ChatColor.ITALIC + "";
        } else {
            return "";
        }
      }

        public String convertName(String name, boolean officialChannel) {
            IRCConnection connection = plugin.bridge.connections.get(name);
           // if (enhancer != null) {
           //     return formatName(name, officialChannel)
           //            + enhancer.format(convertNameWithoutColor(name));
            // Hide IRC prefixes, they look tacky in minecraft.
        if (!Character.isLetter(name.charAt(0))) {
            name = name.substring(1);
        }
        // begin format.
        if (name.startsWith("#")) {
            return ChatColor.GREEN + "";
        } else if (name.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")) {
            name = convertNameWithoutColor(name);
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(name);
            return ChatColor.translateAlternateColorCodes('&', user.getPrefix() + name + user.getSuffix());
        // Try syncing.
        } else if (name.toUpperCase().endsWith("|MC")) {
            name = convertNameWithoutColor(name);
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(name);
            return ChatColor.translateAlternateColorCodes('&', user.getPrefix() + name + user.getSuffix());
        // Format the console.
        } else if (name.endsWith("|Console")) {
            return plugin.console_format;
        // Format bots such as ChanServ in the /irc list.
        } else if (name.endsWith("Serv")) {
            return ChatColor.RED + "" + ChatColor.ITALIC + name + "|IRC";
         // Ok... a new approach.
        } else {
            PermissionUser user = PermissionsEx.getPermissionManager().getUser(name + "|IRC");
            return ChatColor.translateAlternateColorCodes('&', user.getPrefix() + name + "|IRC");
        }
     }
 

       public String convertNameWithoutColor(String name) {
            if (name.startsWith("#")) {
                return name;
            } else if (!Character.isLetter(name.charAt(0))) {
                // _ is a valid first character for IRC users.
                // Minecraft users fix otherwise-broken usernams with it.
                if (   name.charAt(0) != '_'
                    || name.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")) {
                    name = name.substring(1);
                } else if (   name.charAt(0) != '_'
                    || name.toUpperCase().endsWith("|MC")) {
                    name = name.substring(1);
                }
            }

            if (name.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")) {
                return name.substring(0, name.length()-(4+plugin.console_tag.length()));
            } else if (name.toUpperCase().endsWith("|MC")) {
                return name.substring(0, name.length()-(4+plugin.console_tag.length()));
            } else if (name.endsWith("|Console")) {
                return "Console";
            } else {
                return name + "|IRC";
            }
        }


        public String formatChannel(String channel) {
            if (channel.equalsIgnoreCase(plugin.default_channel)) {
                return "";
            } else {
                return ChatColor.GREEN + channel + " ";
            }
        }

        public String getPrefix(String nick, String channel) {
            for (User user : getUsers(channel)) {
                if (user.getNick().equalsIgnoreCase(nick)) {
                    return user.getPrefix();
                }
            }

            return "";
        }

        private void getMatches(HashMap<String,String> matches, String nick,
                                  String channel) {
            for (User user : getUsers(channel)) {
                String user_nick = user.getNick().toLowerCase();
                if (user_nick.endsWith("|console")) {
                    // Skip the console.
                    continue;
                }

                if (user_nick.startsWith("_") && user_nick.endsWith("|" + plugin.console_tag.toLowerCase() + "|mc")) {
                    user_nick = user_nick.substring(1);
                }

                if (user_nick.startsWith(nick)) {
                    matches.put(user_nick, user.getNick());
                }
            }
        }

        public String matchUser(String rawnick) {
            String reverted = revertName(rawnick);
            // If a full name/channel has been specified, use it.
            if (!reverted.endsWith("|" + plugin.console_tag + "|MC")) {
                return reverted;
            } else if (rawnick.toUpperCase().endsWith("|" + plugin.console_tag + "|MC")) {
                return rawnick;
            }

            String nick = rawnick.toLowerCase();
            HashMap<String,String> matches = new HashMap<String,String>();
            if (speaking_to.startsWith("#")) {
                // Match against users in the current channel.
                getMatches(matches, nick, speaking_to);

                if (matches.size() == 1) {
                    for (String match : matches.keySet()) {
                        // Silly, but an easy way to grab the only element.
                        return matches.get(match);
                    }
                } else if (matches.containsKey(nick + "|" + plugin.console_tag.toLowerCase() + "|mc")) {
                    return matches.get(nick + "|" + plugin.console_tag.toLowerCase() + "|mc");
                } else if (matches.containsKey(nick)) {
                    return matches.get(nick);
                }
            } else if (speaking_to.toLowerCase().startsWith(nick)) {
                return speaking_to;
            }

            for (String channel : getChannels()) {
                getMatches(matches, nick, channel);
            }

            if (matches.size() == 1) {
                for (String match : matches.keySet()) {
                    // Silly, but an easy way to grab the only element.
                    return matches.get(match);
                }
            } else if (matches.containsKey(nick + "|" + plugin.console_tag.toLowerCase() + "|mc")) {
                return matches.get(nick + "|" + plugin.console_tag.toLowerCase() + "|mc");
            } else if (matches.containsKey(nick)) {
                return matches.get(nick);
            }

            return reverted;
        }

        public void heard(String who, String where, String what) {
            String display_who = convertName(getPrefix(who,where) + who,
                                             isOfficial(where));
            if(display_who.endsWith("Console") && what.startsWith("Event:"))
            {
            	return;
            }
            String intro;
            if (where == null) {
                intro = who + "->Console:";
            } else if (!where.startsWith("#")) {
                if (who.equalsIgnoreCase(getName())) {
                    intro = ChatColor.GREEN + "To " + convertName(where, false)
                            + ChatColor.GREEN + ":" + ChatColor.LIGHT_PURPLE;
                } else {
                    intro = ChatColor.GREEN + "From " + display_who
                            + ChatColor.GREEN + ":" + ChatColor.LIGHT_PURPLE;
                }
            } else {
                ChatColor text_color = ChatColor.WHITE;

                if (plugin.permission_channels.containsValue(where)) {
                    text_color = ChatColor.GREEN;
                }

                where = formatChannel(where) + text_color;

                if (what.startsWith("/me ")) {
                    what = what.substring(4);
                    intro = where + "* " + display_who + text_color;
                } else {
                    intro = where + "" + display_who + text_color + ":";
                }
            }

            tellUser(intro + " " + what, true);
        }

        public void tellUser(String message) {
            tellUser(message, false);
        }

        public void tellUser(String message, boolean always) {
            if (!always && plugin.beingQuiet()) {
                // Non-critical messages get suppressed during reloads.
                return;
            }

            if (my_name != null) {
                Player player = plugin.getServer().getPlayer(my_name);
                if (player != null) {
                    player.sendMessage(message);
                }
            } else {
                plugin.console.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }
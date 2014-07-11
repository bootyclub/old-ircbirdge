package us.zenyth.ircbridge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

public class Configuration implements Listener {
   private  final IRCBridge plugin;
   public Configuration(IRCBridge instance) {
       this.plugin = instance;
   }
   public void configure() {
	YamlConfiguration config = new YamlConfiguration();
	try {
	    config.load(plugin.dataFile);
	} catch (Exception e) {
            plugin.log.log(Level.INFO,"[{0}" + "] " + " Error loading IRCBridge configuration!", plugin.pdf.getName());
	}
        

        plugin.log_pms = config.getBoolean("log.pms", false);

        // |Console will be appended to this.
        plugin.console_id = config.getString("console.id", "The");
        plugin.console_tag = config.getString("console.tag", "NA");

        // Channel setup.
        plugin.console_channel = config.getString("channels.console", "#console");
        plugin.default_channel = config.getString("channels.default", "#minecraft");
        plugin.autojoin_channels = config.getStringList("channels.autojoin");
        plugin.big_channels = new HashSet<String>(config.getStringList("channels.big"));
        plugin.watchers = new HashSet<String>(config.getStringList("players.watchers"));

        plugin.permission_channels = new HashMap<String,String>();
        Set<String> channelPermissions = config.getConfigurationSection("channels.permissions").getKeys(false);
	List<String> permissions;
	if (channelPermissions != null) {
	    permissions = Arrays.asList(channelPermissions.toArray(new String[0]));
	    if(permissions == null) {
		config.set("channels.permissions.badass", "#badass");
		permissions = new Vector<String>();
		permissions.add("badass");
	    }
	} else {
	    config.set("channels.permissions.badass", "#badass");
	    permissions = new Vector<String>();
	    permissions.add("badass");
	}

        // Record and register the permissions for permission-based channels.
        PluginManager pm = Bukkit.getServer().getPluginManager();
        for (String permission : permissions) {
            String channel = config.getString("channels.permissions."
                                              + permission);
            plugin.permission_channels.put(permission, channel);
            pm.addPermission(new Permission("ircbridge." + permission,
                                            "Allows access to " + channel,
                                            PermissionDefault.OP));
        }

        // The console channel, default channel, autojoin channels, and
        // permission-based channels are considered official by default, and
        // apply IRC-based nick colors.
        Vector<String> default_official = new Vector<String>();
        default_official.add(plugin.console_channel);
        default_official.add(plugin.default_channel);
        for (String channel : plugin.autojoin_channels) {
            default_official.add(channel);
        }
        for (String channel : plugin.permission_channels.values()) {
            default_official.add(channel);
        }

        plugin.official_channels = new HashSet<String>(config.getStringList("channels.official"));

        plugin.console_format = config.getString("console.format", "&dConsole").replaceAll("(&([a-f0-9]))", "\u00A7$2");

        // Server connection info.
        plugin.server_address = config.getString("server.address", "localhost");
        plugin.server_port = config.getInt("server.port", 6667);
        plugin.server_pass = config.getString("server.password", "");
        plugin.webirc_pass = config.getString("server.webirc_password", "");

        try {
	    config.save(plugin.dataFile);
	} catch (Exception e) {
	    plugin.log.log(Level.INFO,"[{0}" + "] " + "Unable to save the configuration! Check your write permissions.");
	    Bukkit.getLogger().info(e.getCause().getMessage());
	}
    }
}

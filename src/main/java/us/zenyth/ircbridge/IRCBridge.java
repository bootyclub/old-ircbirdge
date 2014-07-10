
package us.zenyth.ircbridge;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;


public class IRCBridge extends JavaPlugin {
    Logger message_log = Logger.getLogger("ircbridge.pms");
    public Logger log = Logger.getLogger("Minecraft");
    FileHandler message_file = null;
    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
    PluginDescriptionFile pdf;
    boolean log_pms;

    final int WHO_PAGE_SIZE=40;

    String console_id;
    String console_tag;
    String default_channel;
    String console_channel;
    String console_format;
    String format;

    // Server connection info.
    String server_address;
    int server_port;
    String server_pass;
    String webirc_pass;

    List<String> autojoin_channels;
    HashSet<String> big_channels;
    HashSet<String> watchers;
    HashSet<String> official_channels;
    HashMap<String,String> permission_channels;

    public PermissionsEx permsPlugin;
    public PermissionManager perms;
    public ChatColor color;
   
    public Bridge bridge;
    public IRCEvents events;
    private IRCConnection connection;
    private String name;
    private long startup_time;
    private boolean shutting_down = false;

    public static final int ALL = 0;
    public static final int MINECRAFT = 1;
    public static final int IRC = 2;
    
    private File dataFile;
    private long last_complaint_time;

      public boolean beingQuiet() {
        if (shutting_down || startup_time + 5000 > System.currentTimeMillis()) {
            return true;
        }

        return false;
    }
    
    @Override
    public void onLoad() {
	this.dataFile = new File(this.getDataFolder(), "config.yml");
    }
    
    @Override
    public void onDisable() {
        this.pdf = getDescription();
        shutting_down = true;
        bridge.quitAll();
        bridge = null;
        perms = null;

        if (message_file != null) {
            message_log.removeHandler(message_file);
            message_log.setUseParentHandlers(true);
            message_file = null;
            log.log(Level.INFO,"[{0}" + "] " + "IRCBridge version {1} by {2} has been disabled.", new Object[]{pdf.getName(), pdf.getVersion(), pdf.getAuthors()});
        }

        super.onDisable();
    }

    @Override
    public void onEnable() {
        this.pdf = getDescription();
        super.onEnable();
        shutting_down = false;
        startup_time = System.currentTimeMillis();

        try {
            message_file = new FileHandler("pms.log", true);
            message_file.setFormatter(new TinyFormatter());
            message_log.addHandler(message_file);
            message_log.setUseParentHandlers(false);
        } catch (Exception e) {
            message_file = null;
            complain("unable to open message log file", e);
        }

        PluginManager pm = getServer().getPluginManager();
        permsPlugin = (PermissionsEx) pm.getPlugin("PermissionsEx");
        if (permsPlugin == null) {
            log.log(Level.INFO,"[{0}" + "] " + "PermissionsEx not found!", pdf.getName());
            log.log(Level.INFO,"[{0}" + "] " + "Group based colors and or channels will not be available.", pdf.getName());
        }
        else if (permsPlugin != null) {
            log.log(Level.INFO,"[{0}" + "] " + "PermissionsEx found, enabling intergration!", pdf.getName());
            perms = PermissionsEx.getPermissionManager();
        }

        log.log(Level.INFO,"[{0}" + "] " + "IRCBridge version {1} by {2} has been disabled.", new Object[]{pdf.getName(), pdf.getVersion(), pdf.getAuthors()});

        configure();

        bridge = new Bridge(this);
        events = new IRCEvents(this);

        pm.registerEvents(bridge, this);
        pm.registerEvents(events, this);
        bridge.connectAll(getServer().getOnlinePlayers());
        /************ COMMANDS ************/
        // Join
        this.getCommand("join").setExecutor(new Commands(this));
        // Part
        this.getCommand("part").setExecutor(new Commands(this));
        // Switch
        this.getCommand("switch").setExecutor(new Commands(this));
        // To
        this.getCommand("to").setExecutor(new Commands(this));
        // Say
        this.getCommand("say").setExecutor(new Commands(this));
        // Me
        this.getCommand("me").setExecutor(new Commands(this));
        // Who
        this.getCommand("who").setExecutor(new Commands(this));
        // List
        this.getCommand("list").setExecutor(new Commands(this));
        // Mode
        this.getCommand("mode").setExecutor(new Commands(this));
        // IRCKick
        this.getCommand("irckick").setExecutor(new Commands(this));
        // Reconnect
        this.getCommand("reconnect").setExecutor(new Commands(this));
    }


    public void configure() {
	YamlConfiguration config = new YamlConfiguration();
	try {
	    config.load(dataFile);
	} catch (Exception e) {
            log.log(Level.INFO,"[{0}" + "] " + " Error loading IRCBridge configuration!", pdf.getName());
	}

        log_pms = config.getBoolean("log.pms", false);

        // |Console will be appended to this.
        console_id = config.getString("console.id", "The");
        console_tag = config.getString("console.tag", "NA");

        // Channel setup.
        console_channel = config.getString("channels.console", "#console");
        default_channel = config.getString("channels.default", "#minecraft");
        autojoin_channels = config.getStringList("channels.autojoin");
        big_channels = new HashSet<String>(config.getStringList("channels.big"));
        watchers = new HashSet<String>(config.getStringList("players.watchers"));

        permission_channels = new HashMap<String,String>();
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
        PluginManager pm = getServer().getPluginManager();
        for (String permission : permissions) {
            String channel = config.getString("channels.permissions."
                                              + permission);
            permission_channels.put(permission, channel);
            pm.addPermission(new Permission("ircbridge." + permission,
                                            "Allows access to " + channel,
                                            PermissionDefault.OP));
        }

        // The console channel, default channel, autojoin channels, and
        // permission-based channels are considered official by default, and
        // apply IRC-based nick colors.
        Vector<String> default_official = new Vector<String>();
        default_official.add(console_channel);
        default_official.add(default_channel);
        for (String channel : autojoin_channels) {
            default_official.add(channel);
        }
        for (String channel : permission_channels.values()) {
            default_official.add(channel);
        }

        official_channels = new HashSet<String>(config.getStringList("channels.official"));

        console_format = config.getString("console.format", "&dConsole").replaceAll("(&([a-f0-9]))", "\u00A7$2");

        // Server connection info.
        server_address = config.getString("server.address", "localhost");
        server_port = config.getInt("server.port", 6667);
        server_pass = config.getString("server.password", "");
        webirc_pass = config.getString("server.webirc_password", "");

        try {
	    config.save(dataFile);
	} catch (Exception e) {
	    log.log(Level.INFO,"[{0}" + "] " + "Unable to save the configuration! Check your write permissions.");
	    Bukkit.getLogger().info(e.getCause().getMessage());
	}
    }

    public void logMessage(String message) {
        if (log_pms) {
            message_log.info(message);
        }
    }

    private class TinyFormatter extends Formatter {
        private final SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        @Override
	public String format(LogRecord record) {
            return timestamp.format(record.getMillis()) + " "
                   + record.getMessage() + "\n";
        }
    }
        public void complain(String message, Exception problem) {
        complain(message, problem, false);
    }

    public void complain(String message, Exception problem, boolean always) {
        if (!always) {
            long time = System.currentTimeMillis();
            if (time > this.last_complaint_time + 60000L) {
                this.last_complaint_time = time;
            } else {
                return;
            }
        }

}
}
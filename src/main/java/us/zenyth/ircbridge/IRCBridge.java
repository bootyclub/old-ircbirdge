
package us.zenyth.ircbridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    public IRCBridge plugin;
    FileHandler message_file = null;
    ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
    public static String format;
    private NickListener nListener = new NickListener(this);
    PluginDescriptionFile pdf;
    boolean log_pms;

    final int WHO_PAGE_SIZE=40;

    String console_id;
    String console_tag;
    String default_channel;
    String console_channel;
    String console_format;

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
    private Configuration configuration;
    private String name;
    private long startup_time;
    private boolean shutting_down = false;

    public static final int ALL = 0;
    public static final int MINECRAFT = 1;
    public static final int IRC = 2;
    
    File dataFile;
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
            log.log(Level.INFO,"[{0}" + "] " + "PermissionsEx not found!", new Object[]{pdf.getName()});
            log.log(Level.INFO,"[{0}" + "] " + "Group based colors and or channels will not be available.", new Object[]{pdf.getName()});
        }
        else if (permsPlugin != null) {
            log.log(Level.INFO,"[{0}" + "] " + "PermissionsEx found, enabling intergration!", new Object[]{pdf.getName()});
            perms = PermissionsEx.getPermissionManager();
        }

        log.log(Level.INFO,"[{0}" + "] " + "IRCBridge version {1} by {2} has been disabled.", new Object[]{pdf.getName(), pdf.getVersion(), pdf.getAuthors()});

    
 
        bridge = new Bridge(this);
        events = new IRCEvents(this);
        configuration = new Configuration(this);

        pm.registerEvents(bridge, this);
        pm.registerEvents(events, this);
        
        configuration.configure();
        
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
       public String format(String player)
   {
     try
     {
         Player p = getServer().getPlayer(player);
       return this.nListener.formatName(p);
     }
     catch (NullPointerException e)
     {
       try
       {
          Player p;
         return player; } catch (NullPointerException ex) {
       }
     }
    return player;
    }
}
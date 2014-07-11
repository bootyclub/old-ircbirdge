package us.zenyth.ircbridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;
import us.zenyth.ircenhancer.IRCEnhancer;

/* Author: LukeFlynn, Zenyth.US */
/* Website: www.lukesthings.com || www.zenyth.us */

public class NickListener implements Listener {
    private final IRCBridge plugin;
    public NickListener(IRCBridge instance) {
        this.plugin = instance;
    }
    @EventHandler
    public void onPlayerChat (PlayerChatEvent e) {
        e.getPlayer().setDisplayName(formatName(e.getPlayer()) + ChatColor.WHITE);
    }
    public String formatName(Player player) {
        String format = IRCBridge.format;
        PermissionUser user = PermissionsEx.getPermissionManager().getUser(player);
        String worldName = player.getWorld().getName();
        format = ChatColor.translateAlternateColorCodes('&', user.getPrefix(worldName) + "Jesus" + player.getName() + user.getSuffix(worldName));
        return format;
    }
 }

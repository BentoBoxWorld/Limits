package bentobox.addon.limits.listeners;

import java.util.Locale;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.objects.IslandBlockCount;

/**
 * Sets block limits based on player permission
 * @author tastybento
 *
 */
public class JoinListener implements Listener {

    private final Limits addon;

    public JoinListener(Limits addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // Check if player has any islands in the game modes
        addon.getGameModes().forEach(gm -> {
            if (addon.getIslands().hasIsland(gm.getOverWorld(), e.getPlayer().getUniqueId())) {
                String islandId = addon.getIslands().getIsland(gm.getOverWorld(), e.getPlayer().getUniqueId()).getUniqueId();
                checkPerms(e.getPlayer(), gm.getPermissionPrefix() + "island.limit.", islandId, gm.getDescription().getName());
            }
        });
    }

    private void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(islandId);
        int limit = -1;
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
            if (perms.getPermission().startsWith(permissionPrefix)) {
                // Get the Material
                String[] split = perms.getPermission().split("\\.");
                if (split.length != 5) {
                    logError(player.getName(), perms.getPermission(), "format must be " + permissionPrefix + "MATERIAL.NUMBER");
                    return;
                }
                Material m = Material.getMaterial(split[3].toUpperCase(Locale.ENGLISH));
                if (m == null) {
                    logError(player.getName(), perms.getPermission(), split[3].toUpperCase(Locale.ENGLISH) + " is not a valid material");
                    return;
                }
                // Get the max value should there be more than one
                if (perms.getPermission().contains(permissionPrefix + ".*")) {
                    logError(player.getName(), perms.getPermission(), "wildcards are not allowed");
                    return;
                }
                if (!NumberUtils.isDigits(split[4])) {
                    logError(player.getName(), perms.getPermission(), "the last part MUST be a number!");
                } else {
                    limit = Math.max(limit, Integer.valueOf(split[4]));
                    // Set the limit
                    if (ibc == null) {
                        ibc = new IslandBlockCount(islandId, gameMode);
                    }
                    ibc.setBlockLimit(m, limit);
                }
            }
        }
        // If any changes have been made then store it
        if (ibc != null) {
            addon.getBlockLimitListener().setIsland(islandId, ibc);
        }

    }

    private void logError(String name, String perm, String error) {
        addon.logError("Player " + name + " has permission: '" + perm + " but " + error + " Ignoring...");
    }

}

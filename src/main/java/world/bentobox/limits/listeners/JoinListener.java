package world.bentobox.limits.listeners;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;

import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamEvent.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.events.LimitsJoinPermCheckEvent;
import world.bentobox.limits.objects.IslandBlockCount;

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

    private void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(islandId);
        // Fire event, so other addons can cancel this permissions change
        LimitsJoinPermCheckEvent e = new LimitsJoinPermCheckEvent(player, gameMode, ibc);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled()) return;
        // Get ibc from event if it has changed
        ibc = e.getIbc();
        // If perms should be ignored, but the IBC given in the event used, then set it and return
        if (e.isIgnorePerms() && ibc != null) {
            addon.getBlockLimitListener().setIsland(islandId, ibc);
            return;
        }
        // Check permissions
        if (ibc != null) {
            // Clear permission limits
            ibc.getEntityLimits().clear();
            ibc.getBlockLimits().clear();
        }
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
            if (!perms.getValue() || !perms.getPermission().startsWith(permissionPrefix)) continue;
            // No wildcards
            if (perms.getPermission().contains(permissionPrefix + "*")) {
                logError(player.getName(), perms.getPermission(), "wildcards are not allowed.");
                return;
            }
            // Check formatting
            String[] split = perms.getPermission().split("\\.");
            if (split.length != 5) {
                logError(player.getName(), perms.getPermission(), "format must be '" + permissionPrefix + "MATERIAL.NUMBER' or '" + permissionPrefix + "ENTITY-TYPE.NUMBER'");
                return;
            }
            // Check value
            if (!NumberUtils.isDigits(split[4])) {
                logError(player.getName(), perms.getPermission(), "the last part MUST be a number!");
                return;
            }
            // Entities & materials
            EntityType et = Arrays.stream(EntityType.values()).filter(t -> t.name().equalsIgnoreCase(split[3])).findFirst().orElse(null);
            Material m = Arrays.stream(Material.values()).filter(t -> t.name().equalsIgnoreCase(split[3])).findFirst().orElse(null);

            if (et == null && m == null) {
                logError(player.getName(), perms.getPermission(), split[3].toUpperCase(Locale.ENGLISH) + " is not a valid material or entity type.");
                break;
            }
            // Make an ibc if required
            if (ibc == null) {
                ibc = new IslandBlockCount(islandId, gameMode);
            }
            if (et != null && m == null) {
                // Entity limit
                ibc.setEntityLimit(et, Math.max(ibc.getEntityLimit(et), Integer.valueOf(split[4])));
            } else if (m != null && et == null) {
                // Material limit
                ibc.setBlockLimit(m, Math.max(ibc.getBlockLimit(m), Integer.valueOf(split[4])));
            } else {
                if (m.isBlock()) {
                    // Material limit
                    ibc.setBlockLimit(m, Math.max(ibc.getBlockLimit(m), Integer.valueOf(split[4])));
                } else {
                    // This is an entity setting
                    ibc.setEntityLimit(et, Math.max(ibc.getEntityLimit(et), Integer.valueOf(split[4])));
                }
            }
        }
        // Check removed permissions

        // If any changes have been made then store it - don't make files unless they are needed
        if (ibc != null) addon.getBlockLimitListener().setIsland(islandId, ibc);
    }

    private void logError(String name, String perm, String error) {
        addon.logError("Player " + name + " has permission: '" + perm + "' but " + error + " Ignoring...");
    }

    /*
     * Event handling
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandEvent e) {
        if (!e.getReason().equals(Reason.CREATED)
                && !e.getReason().equals(Reason.RESETTED)
                && !e.getReason().equals(Reason.REGISTERED)) {
            return;
        }
        setOwnerPerms(e.getIsland(), e.getOwner());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerChange(TeamSetownerEvent e) {
        removeOwnerPerms(e.getIsland());
        setOwnerPerms(e.getIsland(), e.getNewOwner());
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // Check if player has any islands in the game modes
        addon.getGameModes().forEach(gm -> {
            if (addon.getIslands().hasIsland(gm.getOverWorld(), e.getPlayer().getUniqueId())) {
                String islandId = Objects.requireNonNull(addon.getIslands().getIsland(gm.getOverWorld(), e.getPlayer().getUniqueId())).getUniqueId();
                checkPerms(e.getPlayer(), gm.getPermissionPrefix() + "island.limit.", islandId, gm.getDescription().getName());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnregisterIsland(IslandEvent e) {
        if (!e.getReason().equals(Reason.UNREGISTERED)) {
            return;
        }
        removeOwnerPerms(e.getIsland());
    }

    /*
     * Utility methods
     */

    private void removeOwnerPerms(Island island) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
            if (ibc != null) {
                ibc.getBlockLimits().clear();
            }
        }
    }

    private void setOwnerPerms(Island island, UUID ownerUUID) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            // Check if owner is online
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            if (owner.isOnline()) {
                // Set perm-based limits
                String prefix = addon.getGameModePermPrefix(world);
                String name = addon.getGameModeName(world);
                if (!prefix.isEmpty() && !name.isEmpty() && owner.getPlayer() != null) {
                    checkPerms(Objects.requireNonNull(owner.getPlayer()), prefix + "island.limit.", island.getUniqueId(), name);
                }
            }
        }
    }

}

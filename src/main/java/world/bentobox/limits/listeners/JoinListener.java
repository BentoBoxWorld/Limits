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
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings.EntityGroup;
import world.bentobox.limits.events.LimitsJoinPermCheckEvent;
import world.bentobox.limits.events.LimitsPermCheckEvent;
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

    /**
     * Check and set the permissions of the player and how they affect the island limits
     * @param player - player
     * @param permissionPrefix - permission prefix for this game mode
     * @param islandId - island string id
     * @param gameMode - game mode string doing the checking
     */
    public void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(islandId);
        // Check permissions
        if (ibc != null) {
            // Clear permission limits
            ibc.getEntityLimits().clear();
            ibc.getEntityGroupLimits().clear();
            ibc.getBlockLimits().clear();
        }
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
            if (!perms.getValue()
                    || !perms.getPermission().startsWith(permissionPrefix)
                    || badSyntaxCheck(perms, player.getName(), permissionPrefix)) {
                continue;
            }
            // Check formatting
            String[] split = perms.getPermission().split("\\.");
            // Entities & materials
            EntityType et = Arrays.stream(EntityType.values()).filter(t -> t.name().equalsIgnoreCase(split[3])).findFirst().orElse(null);
            Material m = Arrays.stream(Material.values()).filter(t -> t.name().equalsIgnoreCase(split[3])).findFirst().orElse(null);
            EntityGroup entgroup = addon.getSettings().getGroupLimitDefinitions().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(split[3])).findFirst().orElse(null);

            if (entgroup == null && et == null && m == null) {
                logError(player.getName(), perms.getPermission(), split[3].toUpperCase(Locale.ENGLISH) + " is not a valid material or entity type/group.");
                break;
            }
            // Make an ibc if required
            if (ibc == null) {
                ibc = new IslandBlockCount(islandId, gameMode);
            }
            // Get the value
            int value = Integer.parseInt(split[4]);
            // Fire perm check event
            LimitsPermCheckEvent l = new LimitsPermCheckEvent(player, islandId, ibc, entgroup, et, m, value);
            Bukkit.getPluginManager().callEvent(l);
            if (l.isCancelled()) continue;
            // Use event values
            ibc = l.getIbc();
            // Run null checks and set ibc
            runNullCheckAndSet(ibc, l);
        }
        // Check removed permissions
        if (ibc == null) {
            BentoBox.getInstance().logDebug("IBC is still null");
        }
        // If any changes have been made then store it - don't make files unless they are needed
        if (ibc != null) addon.getBlockLimitListener().setIsland(islandId, ibc);
    }

    private boolean badSyntaxCheck(PermissionAttachmentInfo perms, String name, String permissionPrefix) {
        // No wildcards
        if (perms.getPermission().contains(permissionPrefix + "*")) {
            logError(name, perms.getPermission(), "wildcards are not allowed.");
            return true;
        }
        // Check formatting
        String[] split = perms.getPermission().split("\\.");
        if (split.length != 5) {
            logError(name, perms.getPermission(), "format must be '" + permissionPrefix + "MATERIAL.NUMBER', '" + permissionPrefix + "ENTITY-TYPE.NUMBER', or '" + permissionPrefix + "ENTITY-GROUP.NUMBER'");
            return true;
        }
        // Check value
        if (!NumberUtils.isDigits(split[4])) {
            logError(name, perms.getPermission(), "the last part MUST be a number!");
            return true;
        }

        return false;
    }

    private void runNullCheckAndSet(@Nullable IslandBlockCount ibc,  @NonNull LimitsPermCheckEvent l) {
        if (ibc == null) {
            return;
        }
        EntityGroup entgroup = l.getEntityGroup();
        EntityType et = l.getEntityType();
        Material m = l.getMaterial();
        int value = l.getValue();
        if (entgroup != null) {
            // Entity group limit
            ibc.setEntityGroupLimit(entgroup.getName(), Math.max(ibc.getEntityGroupLimit(entgroup.getName()), value));
        } else if (et != null && m == null) {
            // Entity limit
            ibc.setEntityLimit(et, Math.max(ibc.getEntityLimit(et), value));
        } else if (m != null && et == null) {
            // Material limit
            ibc.setBlockLimit(m, Math.max(ibc.getBlockLimit(m), value));
        } else {
            if (m != null && m.isBlock()) {
                // Material limit
                ibc.setBlockLimit(m, Math.max(ibc.getBlockLimit(m), value));
            } else if (et != null){
                // This is an entity setting
                ibc.setEntityLimit(et, Math.max(ibc.getEntityLimit(et), value));
            }
        }

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
        BentoBox.getInstance().logDebug("Island Event " + e.getReason());
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
                IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(islandId);
                if (joinEventCheck(e.getPlayer(), islandId, ibc)) {
                    return;
                }
                checkPerms(e.getPlayer(), gm.getPermissionPrefix() + "island.limit.", islandId, gm.getDescription().getName());
            }
        });
    }

    private boolean joinEventCheck(Player player, String islandId, IslandBlockCount ibc) {
        // Fire event, so other addons can cancel this permissions change
        LimitsJoinPermCheckEvent e = new LimitsJoinPermCheckEvent(player, islandId, ibc);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled()) {
            return true;
        }
        // Get ibc from event if it has changed
        ibc = e.getIbc();
        // If perms should be ignored, but the IBC given in the event used, then set it and return
        if (e.isIgnorePerms() && ibc != null) {
            addon.getBlockLimitListener().setIsland(islandId, ibc);
            return true;
        }
        return false;
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

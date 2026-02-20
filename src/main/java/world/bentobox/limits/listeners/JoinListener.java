package world.bentobox.limits.listeners;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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

import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.Limits;
import world.bentobox.limits.events.LimitsJoinPermCheckEvent;
import world.bentobox.limits.events.LimitsPermCheckEvent;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * This listener handles events related to players joining, island creation, and ownership changes
 * to apply permission-based block, entity, and entity group limits to islands.
 * It checks player permissions and dynamically adjusts the limits for each island they own.
 * 
 * @author tastybento
 *
 */
public class JoinListener implements Listener {

    private final Limits addon;

    /**
     * Constructs the listener.
     * @param addon The Limits addon instance.
     */
    public JoinListener(Limits addon) {
        this.addon = addon;
    }

    /**
     * Checks a player's permissions and applies any limits to a specific island.
     * The permissions are expected to be in the format: [permissionPrefix].[MATERIAL|ENTITY|GROUP].<limit>
     * E.g., "bskyblock.island.limit.COBBLESTONE.100"
     * 
     * @param player           - The player whose permissions are being checked.
     * @param permissionPrefix - The prefix for the limit permissions (e.g., "bskyblock.island.limit.").
     * @param islandId         - The unique ID of the island to apply limits to.
     * @param gameMode         - The name of the game mode the island belongs to.
     */
    public void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        // Get the existing block counts and limits for the island.
        IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(islandId);
        // Check permissions
        if (islandBlockCount != null) {
            // Clear any previously set permission-based limits before re-evaluating.
            // This ensures that limits are fresh on each check and removed permissions are respected.
            islandBlockCount.getEntityLimits().clear();
            islandBlockCount.getEntityGroupLimits().clear();
            islandBlockCount.getBlockLimits().clear();
        }
        // Iterate through all effective permissions of the player.
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            // Skip permissions that are not set to true, don't match the prefix, or have bad syntax.
            if (!permissionInfo.getValue() || !permissionInfo.getPermission().startsWith(permissionPrefix)
                    || badSyntaxCheck(permissionInfo, player.getName(), permissionPrefix)) {
                continue;
            }
            // Split the permission string to parse it. E.g., "bskyblock.island.limit.COBBLESTONE.100"
            String[] permissionParts = permissionInfo.getPermission().split("\\.");
            // Try to match the relevant part of the permission to an EntityType, Material, or a defined EntityGroup.
            EntityType entityType = Arrays.stream(EntityType.values()).filter(type -> type.name().equalsIgnoreCase(permissionParts[3]))
                    .findFirst().orElse(null);
            Material material = Arrays.stream(Material.values()).filter(mat -> mat.name().equalsIgnoreCase(permissionParts[3])).findFirst()
                    .orElse(null);
            EntityGroup entityGroup = addon.getSettings().getGroupLimitDefinitions().stream()
                    .filter(group -> group.getName().equalsIgnoreCase(permissionParts[3])).findFirst().orElse(null);

            // If the permission part is not a valid type, log an error and stop processing this permission.
            if (entityGroup == null && entityType == null && material == null) {
                logError(player.getName(), permissionInfo.getPermission(),
                        permissionParts[3].toUpperCase(Locale.ENGLISH) + " is not a valid material or entity type/group.");
                break;
            }
            // If this is the first limit being applied, create a new IslandBlockCount object.
            if (islandBlockCount == null) {
                islandBlockCount = new IslandBlockCount(islandId, gameMode);
            }
            // The last part of the permission is the limit value.
            int limitValue = Integer.parseInt(permissionParts[4]);
            addon.log("Setting login limit via perm for " + player.getName() + "...");

            // Fire a custom event to allow other plugins to modify or cancel the limit application.
            LimitsPermCheckEvent limitsPermCheckEvent = new LimitsPermCheckEvent(player, islandId, islandBlockCount, entityGroup, entityType, material, limitValue);
            Bukkit.getPluginManager().callEvent(limitsPermCheckEvent);
            if (limitsPermCheckEvent.isCancelled()) {
                addon.log("Permissions not set because another addon/plugin canceled setting.");
                continue;
            }
            // Update local variables with any changes from the event.
            islandBlockCount = limitsPermCheckEvent.getIbc();
            // If the event handler nulled the IslandBlockCount, create a new one.
            if (islandBlockCount == null) {
                islandBlockCount = new IslandBlockCount(islandId, gameMode);
            }
            // Apply the limit to the island's block/entity counts.
            runNullCheckAndSet(islandBlockCount, limitsPermCheckEvent);
        }
        // After checking all permissions, save the updated IslandBlockCount if it has been modified.
        if (islandBlockCount != null)
            addon.getBlockLimitListener().setIsland(islandId, islandBlockCount);
    }

    /**
     * Validates the syntax of a limit permission string.
     * @param permissionInfo The permission to check.
     * @param playerName The name of the player for logging purposes.
     * @param permissionPrefix The required prefix for the permission.
     * @return true if the syntax is bad, false otherwise.
     */
    private boolean badSyntaxCheck(PermissionAttachmentInfo permissionInfo, String playerName, String permissionPrefix) {
        // Wildcard permissions are not supported for limits.
        if (permissionInfo.getPermission().contains(permissionPrefix + "*")) {
            logError(playerName, permissionInfo.getPermission(), "wildcards are not allowed.");
            return true;
        }
        // The permission must have exactly 5 parts separated by dots.
        String[] permissionParts = permissionInfo.getPermission().split("\\.");
        if (permissionParts.length != 5) {
            logError(playerName, permissionInfo.getPermission(), "format must be '" + permissionPrefix + "MATERIAL.NUMBER', '"
                    + permissionPrefix + "ENTITY-TYPE.NUMBER', or '" + permissionPrefix + "ENTITY-GROUP.NUMBER'");
            return true;
        }
        // The last part of the permission must be a valid integer.
        try {
            Integer.parseInt(permissionParts[4]);
        } catch (Exception e) {
            logError(playerName, permissionInfo.getPermission(), "the last part MUST be an integer!");
            return true;
        }
        return false;
    }

    /**
     * Applies the limit from a permission check event to the island's block/entity counts.
     * It ensures that if multiple permissions grant a limit for the same thing, the highest limit is used.
     * @param islandBlockCount The island's count object to modify.
     * @param event The event containing the limit information.
     */
    private void runNullCheckAndSet(@NonNull IslandBlockCount islandBlockCount, @NonNull LimitsPermCheckEvent event) {
        EntityGroup entityGroup = event.getEntityGroup();
        EntityType entityType = event.getEntityType();
        Material material = event.getMaterial();
        int limitValue = event.getValue();
        if (entityGroup != null) {
            // It's a limit for a defined group of entities.
            // Use Math.max to ensure the highest limit from multiple permissions is used.
            int newLimit = Math.max(islandBlockCount.getEntityGroupLimit(entityGroup.getName()), limitValue);
            islandBlockCount.setEntityGroupLimit(entityGroup.getName(), newLimit);
            addon.log("Setting group limit " + entityGroup.getName() + " " + newLimit);
        } else if (entityType != null && material == null) {
            // It's a limit for a specific entity type.
            int newLimit = Math.max(islandBlockCount.getEntityLimit(entityType), limitValue);
            islandBlockCount.setEntityLimit(entityType, newLimit);
            addon.log("Setting entity limit " + entityType + " " + newLimit);
        } else if (material != null && entityType == null) {
            // It's a limit for a specific block material.
            int newLimit = Math.max(islandBlockCount.getBlockLimit(material.getKey()), limitValue);
            addon.log("Setting block limit " + material + " " + newLimit);
            islandBlockCount.setBlockLimit(material.getKey(), newLimit);
        } else {
            // This handles ambiguous cases where a name could be both a material and an entity (e.g., ARMOR_STAND).
            if (material != null && material.isBlock()) {
                // If it's a block, apply a block limit.
                int newLimit = Math.max(islandBlockCount.getBlockLimit(material.getKey()), limitValue);
                addon.log("Setting block limit " + material + " " + newLimit);
                // Material limit
                islandBlockCount.setBlockLimit(material.getKey(), newLimit);
            } else if (entityType != null) {
                // Otherwise, treat it as an entity limit.
                int newLimit = Math.max(islandBlockCount.getEntityLimit(entityType), limitValue);
                addon.log("Setting entity limit " + entityType + " " + newLimit);
                // This is an entity setting
                islandBlockCount.setEntityLimit(entityType, newLimit);
            }
        }

    }

    /**
     * Logs an error message related to a permission.
     * @param playerName The player with the problematic permission.
     * @param permission The permission string.
     * @param errorMessage The error description.
     */
    private void logError(String playerName, String permission, String errorMessage) {
        addon.logError("Player " + playerName + " has permission: '" + permission + "' but " + errorMessage + " Ignoring...");
    }

    /*
     * Event handling
     */

    /**
     * Handles island creation, reset, and registration events.
     * When a new island is made available, set the owner's permission-based limits.
     * @param event The island event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandEvent event) {
        if (!event.getReason().equals(Reason.CREATED) && !event.getReason().equals(Reason.RESETTED)
                && !event.getReason().equals(Reason.REGISTERED)) {
            return;
        }
        setOwnerPerms(event.getIsland(), event.getOwner());
    }

    /**
     * Handles island ownership changes.
     * Removes the old owner's limits and applies the new owner's limits.
     * @param event The team set owner event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerChange(TeamSetownerEvent event) {
        removeOwnerPerms(event.getIsland());
        setOwnerPerms(event.getIsland(), event.getNewOwner());
    }

    /**
     * Handles player join events.
     * When a player joins, iterate through all game modes and check their owned islands
     * to ensure their limits are up-to-date.
     * @param event The player join event.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if player has any islands in the game modes
        addon.getGameModes().forEach(gameMode -> {
            addon.getIslands().getIslands(gameMode.getOverWorld(), event.getPlayer().getUniqueId()).stream()
                    // Filter to only islands they own.
                    .filter(island -> event.getPlayer().getUniqueId().equals(island.getOwner()))
                    .map(Island::getUniqueId).forEach(islandId -> {
                        IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(islandId);
                        // Fire an event to see if this check should be cancelled, then run the permission check.
                        if (!joinEventCheck(event.getPlayer(), islandId, islandBlockCount)) {
                            checkPerms(event.getPlayer(), gameMode.getPermissionPrefix() + "island.limit.", islandId,
                                    gameMode.getDescription().getName());
                        }
                    });
        });
    }

    /**
     * Fires a custom event before applying permissions on player join.
     * This allows other addons to cancel the permission check or provide a custom IslandBlockCount object.
     * 
     * @param player   The player joining.
     * @param islandId The ID of the island being checked.
     * @param islandBlockCount The current IslandBlockCount for the island.
     * @return true if the permission check should be cancelled, false otherwise.
     */
    private boolean joinEventCheck(Player player, String islandId, IslandBlockCount islandBlockCount) {
        // Fire event, so other addons can cancel this permissions change
        LimitsJoinPermCheckEvent event = new LimitsJoinPermCheckEvent(player, islandId, islandBlockCount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return true;
        }
        // Get islandBlockCount from event if it has changed
        islandBlockCount = event.getIbc();
        // If perms should be ignored, but the IBC given in the event used, then set it
        // and return true to cancel the normal permission check.
        if (event.isIgnorePerms() && islandBlockCount != null) {
            addon.getBlockLimitListener().setIsland(islandId, islandBlockCount);
            return true;
        }
        return false;
    }

    /**
     * Handles island un-registration (deletion).
     * Removes any limits associated with the island.
     * @param event The island event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnregisterIsland(IslandEvent event) {
        if (!event.getReason().equals(Reason.UNREGISTERED)) {
            return;
        }
        removeOwnerPerms(event.getIsland());
    }

    /*
     * Utility methods
     */

    /**
     * Removes all permission-based limits from an island.
     * This is typically done when an owner changes or the island is deleted.
     * @param island The island to clear limits from.
     */
    private void removeOwnerPerms(Island island) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(island.getUniqueId());
            if (islandBlockCount != null) {
                // Just clear the maps. This preserves any actual block counts.
                islandBlockCount.getBlockLimits().clear();
                islandBlockCount.getEntityLimits().clear();
                islandBlockCount.getEntityGroupLimits().clear();
            }
        }
    }

    /**
     * Applies permission-based limits for an island's owner.
     * It only runs if the owner is online.
     * @param island The island to apply limits to.
     * @param ownerUUID The UUID of the owner.
     */
    private void setOwnerPerms(Island island, UUID ownerUUID) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            // Check if owner is online
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            if (owner.isOnline()) {
                // Set perm-based limits
                String permissionPrefix = addon.getGameModePermPrefix(world);
                String gameModeName = addon.getGameModeName(world);
                if (!permissionPrefix.isEmpty() && !gameModeName.isEmpty() && owner.getPlayer() != null) {
                    // Run the main permission check logic.
                    checkPerms(Objects.requireNonNull(owner.getPlayer()), permissionPrefix + "island.limit.",
                            island.getUniqueId(), gameModeName);
                }
            }
        }
    }

}

package world.bentobox.limits.listeners;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.events.LimitsJoinPermCheckEvent;
import world.bentobox.limits.events.LimitsPermCheckEvent;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Applies permission-based block, entity, and entity-group limits to islands.
 *
 * <p>By default only the island owner's permissions count. When
 * {@code apply-member-limit-perms} is enabled in the config, team members' permissions
 * are merged in as well: a member's login merges their limits on top (highest value
 * wins), and the owner's login recalculates from scratch and then merges the
 * permissions of all online members.
 *
 * <p>Permission format:
 * <ul>
 *   <li>5-segment: {@code <gm>.island.limit.<KEY>.<N>} — same limit applied independently
 *       to every environment.</li>
 *   <li>6-segment: {@code <gm>.island.limit.<env>.<KEY>.<N>} — applied only to the named
 *       environment, where {@code env} is one of {@code overworld}, {@code nether},
 *       {@code end}.</li>
 * </ul>
 *
 * @author tastybento
 */
public class JoinListener implements Listener {

    private final Limits addon;

    public JoinListener(Limits addon) {
        this.addon = addon;
    }

    /**
     * Reads every limit-shaped permission the player has and re-applies it to the island
     * after first clearing existing permission-based limits.
     */
    public void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(islandId);
        if (islandBlockCount != null) {
            islandBlockCount.clearAllEntityLimits();
            islandBlockCount.clearAllEntityGroupLimits();
            islandBlockCount.clearAllBlockLimits();
        }
        mergePerms(player, permissionPrefix, islandId, gameMode);
    }

    /**
     * Reads every limit-shaped permission the player has and merges it into the island's
     * existing permission-based limits — the highest value wins. Unlike
     * {@link #checkPerms}, nothing is cleared first.
     */
    public void mergePerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(islandId);
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue() || !permissionInfo.getPermission().startsWith(permissionPrefix)) {
                continue;
            }
            islandBlockCount = applyOnePerm(player, permissionInfo, permissionPrefix, islandId, gameMode,
                    islandBlockCount);
        }
        if (islandBlockCount != null) {
            addon.getBlockLimitListener().setIsland(islandId, islandBlockCount);
        }
    }

    private IslandBlockCount applyOnePerm(Player player, PermissionAttachmentInfo permissionInfo,
            String permissionPrefix, String islandId, String gameMode, IslandBlockCount current) {
        ParsedPerm parsed = parsePerm(permissionInfo, player.getName(), permissionPrefix);
        if (parsed == null) return current;

        // Try to match the key part to an EntityType, Material, or EntityGroup
        EntityType entityType = matchEntityType(parsed.key);
        Material material = matchMaterial(parsed.key);
        EntityGroup entityGroup = matchEntityGroup(parsed.key);

        if (entityGroup == null && entityType == null && material == null) {
            logError(player.getName(), permissionInfo.getPermission(),
                    parsed.key.toUpperCase(Locale.ENGLISH) + " is not a valid material or entity type/group.");
            return current;
        }

        IslandBlockCount ibc = current != null ? current : new IslandBlockCount(islandId, gameMode);
        logIfEnabled("Setting login limit via perm for " + player.getName() + "...");

        LimitsPermCheckEvent limitsPermCheckEvent = new LimitsPermCheckEvent(player, islandId, ibc, entityGroup,
                entityType, material, parsed.value);
        Bukkit.getPluginManager().callEvent(limitsPermCheckEvent);
        if (limitsPermCheckEvent.isCancelled()) {
            addon.log("Permissions not set because another addon/plugin canceled setting.");
            return ibc;
        }
        IslandBlockCount eventIbc = limitsPermCheckEvent.getIbc();
        IslandBlockCount finalIbc = eventIbc != null ? eventIbc : new IslandBlockCount(islandId, gameMode);
        applyLimit(finalIbc, parsed.envs, limitsPermCheckEvent);
        return finalIbc;
    }

    /**
     * Parses a limit permission. Returns {@code null} if invalid (and logs).
     */
    private ParsedPerm parsePerm(PermissionAttachmentInfo info, String playerName, String permissionPrefix) {
        String permission = info.getPermission();
        if (permission.contains(permissionPrefix + "*")) {
            logError(playerName, permission, "wildcards are not allowed.");
            return null;
        }
        String[] parts = permission.split("\\.");
        // 5-segment: <gm>.island.limit.<KEY>.<N>
        // 6-segment: <gm>.island.limit.<ENV>.<KEY>.<N>
        if (parts.length != 5 && parts.length != 6) {
            logError(playerName, permission, "format must be '" + permissionPrefix + "[ENV.]KEY.NUMBER' "
                    + "where ENV is overworld|nether|end and KEY is a material, entity type, or group name.");
            return null;
        }
        String numberPart = parts[parts.length - 1];
        int value;
        try {
            value = Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            logError(playerName, permission, "the last part MUST be an integer!");
            return null;
        }
        ParsedPerm out = new ParsedPerm();
        out.value = value;
        if (parts.length == 5) {
            out.key = parts[3];
            out.envs = Settings.ENVIRONMENTS;
        } else {
            Environment env = parseEnv(parts[3]);
            if (env == null) {
                logError(playerName, permission, "'" + parts[3]
                        + "' is not a recognised environment (use overworld, nether, or end).");
                return null;
            }
            out.key = parts[4];
            out.envs = List.of(env);
        }
        return out;
    }

    private static Environment parseEnv(String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "overworld", "normal" -> Environment.NORMAL;
            case "nether" -> Environment.NETHER;
            case "end", "the_end" -> Environment.THE_END;
            default -> null;
        };
    }

    private EntityType matchEntityType(String key) {
        return Arrays.stream(EntityType.values()).filter(t -> t.name().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private Material matchMaterial(String key) {
        return Arrays.stream(Material.values()).filter(m -> m.name().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private EntityGroup matchEntityGroup(String key) {
        return addon.getSettings().getGroupLimitDefinitions().stream()
                .filter(group -> group.getName().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    private void applyLimit(@NonNull IslandBlockCount ibc, List<Environment> envs, @NonNull LimitsPermCheckEvent event) {
        EntityGroup entityGroup = event.getEntityGroup();
        EntityType entityType = event.getEntityType();
        Material material = event.getMaterial();
        int limitValue = event.getValue();

        if (entityGroup != null) {
            for (Environment env : envs) {
                int newLimit = Math.max(ibc.getEntityGroupLimit(env, entityGroup.getName()), limitValue);
                ibc.setEntityGroupLimit(env, entityGroup.getName(), newLimit);
                logIfEnabled("Setting group limit " + entityGroup.getName() + " in " + env + " to " + newLimit);
            }
        } else if (entityType != null && material == null) {
            for (Environment env : envs) {
                int newLimit = Math.max(ibc.getEntityLimit(env, entityType), limitValue);
                ibc.setEntityLimit(env, entityType, newLimit);
                logIfEnabled("Setting entity limit " + entityType + " in " + env + " to " + newLimit);
            }
        } else if (material != null && entityType == null) {
            for (Environment env : envs) {
                int newLimit = Math.max(ibc.getBlockLimit(env, material.getKey()), limitValue);
                ibc.setBlockLimit(env, material.getKey(), newLimit);
                logIfEnabled("Setting block limit " + material + " in " + env + " to " + newLimit);
            }
        } else {
            applyAmbiguousLimit(ibc, envs, entityType, material, limitValue);
        }
    }

    private void applyAmbiguousLimit(@NonNull IslandBlockCount ibc, List<Environment> envs, EntityType entityType,
            Material material, int limitValue) {
        if (material != null && material.isBlock()) {
            for (Environment env : envs) {
                int newLimit = Math.max(ibc.getBlockLimit(env, material.getKey()), limitValue);
                ibc.setBlockLimit(env, material.getKey(), newLimit);
                logIfEnabled("Setting block limit " + material + " in " + env + " to " + newLimit);
            }
        } else if (entityType != null) {
            for (Environment env : envs) {
                int newLimit = Math.max(ibc.getEntityLimit(env, entityType), limitValue);
                ibc.setEntityLimit(env, entityType, newLimit);
                logIfEnabled("Setting entity limit " + entityType + " in " + env + " to " + newLimit);
            }
        }
    }

    private void logIfEnabled(String message) {
        if (addon.getSettings().isLogLimitsOnJoin()) {
            addon.log(message);
        }
    }

    private void logError(String playerName, String permission, String errorMessage) {
        addon.logError("Player " + playerName + " has permission: '" + permission + "' but " + errorMessage
                + " Ignoring...");
    }

    /* =========================================================================
     * Event handlers
     * ========================================================================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandEvent event) {
        if (!event.getReason().equals(Reason.CREATED) && !event.getReason().equals(Reason.RESETTED)
                && !event.getReason().equals(Reason.REGISTERED)) {
            return;
        }
        setOwnerPerms(event.getIsland(), event.getOwner());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerChange(TeamSetownerEvent event) {
        removeOwnerPerms(event.getIsland());
        setOwnerPerms(event.getIsland(), event.getNewOwner());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        addon.getGameModes().forEach(gameMode -> addon.getIslands()
                .getIslands(gameMode.getOverWorld(), playerUUID).stream()
                .filter(island -> playerUUID.equals(island.getOwner())
                        || (addon.getSettings().isApplyMemberLimitPerms()
                                && island.getMemberSet().contains(playerUUID)))
                .forEach(island -> processJoin(event.getPlayer(), island, gameMode)));
    }

    private void processJoin(Player player, Island island, GameModeAddon gameMode) {
        String islandId = island.getUniqueId();
        IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(islandId);
        if (joinEventCheck(player, islandId, islandBlockCount)) {
            return;
        }
        String permissionPrefix = gameMode.getPermissionPrefix() + "island.limit.";
        String gameModeName = gameMode.getDescription().getName();
        if (player.getUniqueId().equals(island.getOwner())) {
            // Owner login recalculates the limits from scratch...
            checkPerms(player, permissionPrefix, islandId, gameModeName);
            // ...then merges in the perms of any online team members
            mergeOnlineMemberPerms(island, permissionPrefix, gameModeName);
        } else {
            // Member login merges their perms on top of whatever is set — highest wins
            mergePerms(player, permissionPrefix, islandId, gameModeName);
        }
    }

    /**
     * Merges the limit permissions of every online team member (excluding the owner)
     * into the island's limits. No-op unless {@code apply-member-limit-perms} is enabled.
     */
    private void mergeOnlineMemberPerms(Island island, String permissionPrefix, String gameModeName) {
        if (!addon.getSettings().isApplyMemberLimitPerms()) {
            return;
        }
        island.getMemberSet().stream()
                .filter(memberUUID -> !memberUUID.equals(island.getOwner()))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(member -> mergePerms(member, permissionPrefix, island.getUniqueId(), gameModeName));
    }

    private boolean joinEventCheck(Player player, String islandId, IslandBlockCount islandBlockCount) {
        LimitsJoinPermCheckEvent event = new LimitsJoinPermCheckEvent(player, islandId, islandBlockCount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return true;
        islandBlockCount = event.getIbc();
        if (event.isIgnorePerms() && islandBlockCount != null) {
            addon.getBlockLimitListener().setIsland(islandId, islandBlockCount);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnregisterIsland(IslandEvent event) {
        if (!event.getReason().equals(Reason.UNREGISTERED)) return;
        removeOwnerPerms(event.getIsland());
    }

    private void removeOwnerPerms(Island island) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            IslandBlockCount islandBlockCount = addon.getBlockLimitListener().getIsland(island.getUniqueId());
            if (islandBlockCount != null) {
                islandBlockCount.clearAllBlockLimits();
                islandBlockCount.clearAllEntityLimits();
                islandBlockCount.clearAllEntityGroupLimits();
            }
        }
    }

    private void setOwnerPerms(Island island, UUID ownerUUID) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            if (owner.isOnline()) {
                String permissionPrefix = addon.getGameModePermPrefix(world);
                String gameModeName = addon.getGameModeName(world);
                if (!permissionPrefix.isEmpty() && !gameModeName.isEmpty() && owner.getPlayer() != null) {
                    checkPerms(Objects.requireNonNull(owner.getPlayer()), permissionPrefix + "island.limit.",
                            island.getUniqueId(), gameModeName);
                    mergeOnlineMemberPerms(island, permissionPrefix + "island.limit.", gameModeName);
                }
            }
        }
    }

    private static class ParsedPerm {
        String key;
        int value;
        List<Environment> envs;
    }
}

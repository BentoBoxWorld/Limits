package world.bentobox.limits;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.eclipse.jdt.annotation.Nullable;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandWorldManager;
import world.bentobox.limits.commands.admin.AdminCommand;
import world.bentobox.limits.commands.player.PlayerCommand;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.EntityLimitListener;
import world.bentobox.limits.listeners.JoinListener;
import world.bentobox.limits.objects.IslandBlockCount;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Addon to BentoBox that monitors and enforces limits
 *
 * @author tastybento
 */
public class Limits extends Addon {

    private static final String LIMIT_NOT_SET = "Limit not set";
    private Settings settings;
    private List<GameModeAddon> gameModes = new ArrayList<>();
    private BlockLimitsListener blockLimitListener;
    private JoinListener joinListener;
    private IslandWorldManager islandWorldManager;

    @Override
    public void onDisable() {
        if (blockLimitListener != null) {
            blockLimitListener.save();
        }
    }

    @Override
    public void onEnable() {
        // Load the plugin's config
        saveDefaultConfig();
        this.islandWorldManager = getPlugin().getIWM();
        // Load settings
        settings = new Settings(this);
        // Register worlds from GameModes
        gameModes = getPlugin().getAddonsManager().getGameModeAddons().stream()
                .filter(gm -> settings.getGameModes().contains(gm.getDescription().getName()))
                .collect(Collectors.toList());
        gameModes.forEach(gm ->
                {
                    // Register commands
                    gm.getAdminCommand().ifPresent(a -> new AdminCommand(this, a));
                    gm.getPlayerCommand().ifPresent(a -> new PlayerCommand(this, a));
                    registerPlaceholders(gm);
                    log("Limits will apply to " + gm.getDescription().getName());
                }
        );
        // Register listener
        blockLimitListener = new BlockLimitsListener(this);
        registerListener(blockLimitListener);
        joinListener = new JoinListener(this);
        registerListener(joinListener);
        registerListener(new EntityLimitListener(this));
        // Done
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @return the gameModes
     */
    public List<GameModeAddon> getGameModes() {
        return gameModes;
    }

    /**
     * @return the blockLimitListener
     */
    public BlockLimitsListener getBlockLimitListener() {
        return blockLimitListener;
    }

    /**
     * Checks if this world is covered by the activated game modes
     *
     * @param world - world
     * @return true or false
     */
    public boolean inGameModeWorld(World world) {
        return gameModes.stream().anyMatch(gm -> gm.inWorld(world));
    }

    /**
     * Get the name of the game mode for this world
     *
     * @param world - world
     * @return game mode name or empty string if none
     */
    public String getGameModeName(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst().map(gm -> gm.getDescription().getName()).orElse("");
    }

    /**
     * Get the permission prefix for this world
     *
     * @param world - world
     * @return permisdsion prefix or empty string if none
     */
    public String getGameModePermPrefix(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst().map(GameModeAddon::getPermissionPrefix).orElse("");
    }


    /**
     * Check if any of the game modes covered have this name
     *
     * @param gameMode - name of game mode
     * @return true or false
     */
    public boolean isCoveredGameMode(String gameMode) {
        return gameModes.stream().anyMatch(gm -> gm.getDescription().getName().equals(gameMode));
    }

    /**
     * @return the joinListener
     */
    public JoinListener getJoinListener() {
        return joinListener;
    }

    private void registerPlaceholders(GameModeAddon gm) {
        if (getPlugin().getPlaceholdersManager() == null) return;
        Registry.MATERIAL.stream()
                .filter(Material::isBlock)
                .forEach(m -> registerCountAndLimitPlaceholders(m, gm));

        Arrays.stream(EntityType.values())
                .forEach(e -> registerCountAndLimitPlaceholders(e, gm));
    }

    /**
     * Registers placeholders for the count and limit of the material
     * in the format of %Limits_(gamemode prefix)_island_(lowercase material name)_count%
     * and %Limits_(gamemode prefix)_island_(lowercase material name)_limit%
     * <p>
     * Example: registerCountAndLimitPlaceholders("HOPPER", gm);
     * Placeholders:
     * "Limits_bskyblock_island_hopper_count"
     * "Limits_bskyblock_island_hopper_limit"
     * "Limits_bskyblock_island_hopper_base_limit"
     * "Limits_bskyblock_island_zombie_limit"
     *
     * @param m  material
     * @param gm game mode
     */
    private void registerCountAndLimitPlaceholders(Material m, GameModeAddon gm) {
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + m.toString().toLowerCase() + "_count",
                user -> String.valueOf(getCount(user, m, gm)));
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + m.toString().toLowerCase() + "_limit",
                user -> getLimit(user, m, gm));
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + m.toString().toLowerCase() + "_base_limit",
                user -> getBaseLimit(user, m, gm));
    }

    private void registerCountAndLimitPlaceholders(EntityType e, GameModeAddon gm) {
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + e.toString().toLowerCase() + "_limit",
                user -> getLimit(user, e, gm));
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + e.toString().toLowerCase() + "_base_limit",
                user -> getBaseLimit(user, e, gm));
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + e.toString().toLowerCase() + "_count",
                user -> String.valueOf(getCount(user, e, gm)));
    }

    /**
     * @param user - Used to identify the island the user belongs to
     * @param m    - The material we are trying to count on the island
     * @param gm   Game Mode Addon
     * @return Number of blocks of the specified material on the given user's island
     */
    private int getCount(@Nullable User user, Material m, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return 0;
        }
        @Nullable IslandBlockCount ibc = getBlockLimitListener().getIsland(is.getUniqueId());
        if (ibc == null) {
            return 0;
        }
        return ibc.getBlockCount(m);
    }

    private long getCount(@Nullable User user, EntityType e, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null || e.getEntityClass() == null) {
            return 0;
        }
        Class<? extends Entity> entityClass = e.getEntityClass();
        long count = is.getWorld().getEntitiesByClass(entityClass).stream()
                .filter(ent -> is.inIslandSpace(ent.getLocation())).count();
        /*  NETHER  */
        if (islandWorldManager.isNetherIslands(is.getWorld()) && islandWorldManager.getNetherWorld(is.getWorld()) != null) {
            count += islandWorldManager.getNetherWorld(is.getWorld()).getEntitiesByClass(entityClass).stream()
                    .filter(ent -> is.inIslandSpace(ent.getLocation())).count();
        }
        /*  END  */
        if (islandWorldManager.isEndIslands(is.getWorld()) && islandWorldManager.getEndWorld(is.getWorld()) != null) {
            count += islandWorldManager.getEndWorld(is.getWorld()).getEntitiesByClass(entityClass).stream()
                    .filter(ent -> is.inIslandSpace(ent.getLocation())).count();
        }
        return count;
    }


    /**
     * @param user - Used to identify the island the user belongs to
     * @param m    - The material whose limit we are querying
     * @param gm   Game Mode Addon
     * @return The limit of the specified material on the given user's island
     */
    private String getLimit(@Nullable User user, Material m, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return LIMIT_NOT_SET;
        }
        if (user != null) {
            // Check the permissions of the user and update
            this.getJoinListener().checkPerms(user.getPlayer(), gm.getPermissionPrefix() + "island.limit.",
                    is.getUniqueId(), gm.getDescription().getName());
        }
        int limit = this.getBlockLimitListener().
                getMaterialLimits(is.getWorld(), is.getUniqueId()).getOrDefault(m, -1);

        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getBaseLimit(@Nullable User user, Material m, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return LIMIT_NOT_SET;
        }

        int limit = this.getBlockLimitListener().
                getMaterialLimits(is.getWorld(), is.getUniqueId()).
                getOrDefault(m, -1);

        if (limit > 0) {
            limit -= this.getBlockLimitListener().getIsland(is).getBlockLimitOffset(m);
        }

        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getLimit(@Nullable User user, EntityType e, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return LIMIT_NOT_SET;
        }

        int limit = this.getBlockLimitListener().getIsland(is).getEntityLimit(e);
        if (limit < 0 && this.getSettings().getLimits().containsKey(e)) {
            limit = this.getSettings().getLimits().get(e);
        }

        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getBaseLimit(@Nullable User user, EntityType e, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null || !this.getSettings().getLimits().containsKey(e)) {
            return LIMIT_NOT_SET;
        }

        int limit = this.getSettings().getLimits().get(e);

        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }


}

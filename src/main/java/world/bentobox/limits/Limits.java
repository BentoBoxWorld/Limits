package world.bentobox.limits;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.World;

import org.eclipse.jdt.annotation.Nullable;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.commands.admin.AdminCommand;
import world.bentobox.limits.commands.player.PlayerCommand;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.EntityLimitListener;
import world.bentobox.limits.listeners.JoinListener;
import world.bentobox.limits.objects.IslandBlockCount;


/**
 * Addon to BentoBox that monitors and enforces limits
 * @author tastybento
 *
 */
public class Limits extends Addon {

    private static final String LIMIT_NOT_SET = "Limit not set";
    private Settings settings;
    private List<GameModeAddon> gameModes;
    private BlockLimitsListener blockLimitListener;
    private JoinListener joinListener;

    @Override
    public void onDisable(){
        if (blockLimitListener  != null) {
            blockLimitListener.save();
        }
    }

    @Override
    public void onEnable() {
        // Load the plugin's config
        saveDefaultConfig();
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
     * @param world - world
     * @return true or false
     */
    public boolean inGameModeWorld(World world) {
        return gameModes.stream().anyMatch(gm -> gm.inWorld(world));
    }

    /**
     * Get the name of the game mode for this world
     * @param world - world
     * @return game mode name or empty string if none
     */
    public String getGameModeName(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst().map(gm -> gm.getDescription().getName()).orElse("");
    }

    /**
     * Get the name of the game mode for this world
     * @param world - world
     * @return game mode name or empty string if none
     */
    public String getGameModePermPrefix(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst().map(GameModeAddon::getPermissionPrefix).orElse("");
    }


    /**
     * Check if any of the game modes covered have this name
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
        Arrays.stream(Material.values())
        .filter(Material::isBlock)
        .forEach(m -> registerCountAndLimitPlaceholders(m, gm));
    }

    /**
     * Registers placeholders for the count and limit of the material
     * in the format of %Limits_(gamemode prefix)_island_(lowercase material name)_count%
     * and %Limits_(gamemode prefix)_island_(lowercase material name)_limit%
     *
     * Example: registerCountAndLimitPlaceholders("HOPPER", gm);
     *  Placeholders:
     *      "Limits_bskyblock_island_hopper_count"
     *      "Limits_bskyblock_island_hopper_limit"
     *
     * @param m material
     * @param gm game mode
     */
    private void registerCountAndLimitPlaceholders(Material m, GameModeAddon gm) {
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + m.toString().toLowerCase() + "_count",
                user -> String.valueOf(getCount(user, m, gm)));
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_" + m.toString().toLowerCase() + "_limit",
                user -> getLimit(user, m, gm));
    }

    /**
     * @param user - Used to identify the island the user belongs to
     * @param m - The material we are trying to count on the island
     * @param gm Game Mode Addon
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

    /**
     * @param user - Used to identify the island the user belongs to
     * @param m - The material whose limit we are querying
     * @param gm Game Mode Addon
     * @return The limit of the specified material on the given user's island
     */
    private String getLimit(@Nullable User user, Material m, GameModeAddon gm) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return LIMIT_NOT_SET;
        }

        int limit = this.getBlockLimitListener().
            getMaterialLimits(is.getWorld(), is.getUniqueId()).
            getOrDefault(m, -1);

        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

}

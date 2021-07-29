package world.bentobox.limits;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.World;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.limits.commands.AdminCommand;
import world.bentobox.limits.commands.PlayerCommand;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.EntityLimitListener;
import world.bentobox.limits.listeners.JoinListener;


/**
 * Addon to BentoBox that monitors and enforces limits
 * @author tastybento
 *
 */
public class Limits extends Addon {

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

        // Hopper count
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_hopper_count",
                user -> String.valueOf(getBlockLimitListener().getIsland(gm.getIslands().
                        getIsland(gm.getOverWorld(), user).getUniqueId()).getBlockCount(Material.HOPPER)));
        // Hopper limit
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_hopper_limit",
                user -> String.valueOf(getBlockLimitListener().getIsland(gm.getIslands().
                        getIsland(gm.getOverWorld(), user).getUniqueId()).getBlockLimit(Material.HOPPER)));
        // Chest count
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_chest_count",
                user -> String.valueOf(getBlockLimitListener().getIsland(gm.getIslands().
                        getIsland(gm.getOverWorld(), user).getUniqueId()).getBlockCount(Material.CHEST)));
        // Chest limit
        getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                gm.getDescription().getName().toLowerCase() + "_island_chest_limit",
                user -> String.valueOf(getBlockLimitListener().getIsland(gm.getIslands().
                        getIsland(gm.getOverWorld(), user).getUniqueId()).getBlockLimit(Material.CHEST)));
    }

}

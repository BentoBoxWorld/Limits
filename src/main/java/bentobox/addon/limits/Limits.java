package bentobox.addon.limits;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.World;

import bentobox.addon.limits.commands.AdminCommand;
import bentobox.addon.limits.commands.PlayerCommand;
import bentobox.addon.limits.listeners.BlockLimitsListener;
import bentobox.addon.limits.listeners.JoinListener;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;


/**
 * Addon to BentoBox that monitors and enforces limits
 * @author tastybento
 *
 */
public class Limits extends Addon {

    private Settings settings;
    private List<GameModeAddon> gameModes;
    private BlockLimitsListener blockLimitListener;

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
            log("Limits will apply to " + gm.getDescription().getName());
        }
                );
        // Register listener
        blockLimitListener = new BlockLimitsListener(this);
        registerListener(blockLimitListener);
        registerListener(new JoinListener(this));
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
    public String getGameMode(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst().map(gm -> gm.getDescription().getName()).orElse("");
    }

    /**
     * Check if any of the game modes covered have this name
     * @param gameMode - name of game mode
     * @return true or false
     */
    public boolean isCoveredGameMode(String gameMode) {
        return gameModes.stream().anyMatch(gm -> gm.getDescription().getName().equals(gameMode));
    }

}

package bentobox.addon.limits;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.World;

import bentobox.addon.limits.listeners.BlockLimitsListener;
import bentobox.addon.limits.listeners.EntityLimitsListener;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;


/**
 * Addon to BentoBox that monitors and enforces limits
 * @author tastybento
 *
 */
public class Limits extends Addon {

    private Settings settings;
    private EntityLimitsListener listener;
    private List<World> worlds;
    private BlockLimitsListener blockLimitListener;

    @Override
    public void onDisable(){
        if (listener != null) {
            worlds.forEach(listener::disable);
        }
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
        worlds = getPlugin().getAddonsManager().getGameModeAddons().stream()
                .filter(gm -> settings.getGameModes().contains(gm.getDescription().getName()))
                .map(GameModeAddon::getOverWorld)
                .collect(Collectors.toList());
        worlds.forEach(w -> log("Limits will apply to " + w.getName()));
        // Register listener
        //listener = new EntityLimitsListener(this);
        //registerListener(listener);
        blockLimitListener = new BlockLimitsListener(this);
        registerListener(blockLimitListener);
        // Done
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }


}

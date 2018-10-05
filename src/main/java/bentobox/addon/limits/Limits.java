package bentobox.addon.limits;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.commands.CompositeCommand;


/**
 * Addon to BSkyBlock that enables island level scoring and top ten functionality
 * @author tastybento
 *
 */
public class Limits extends Addon {

    Settings settings;

    @Override
    public void onDisable(){
    }

    @Override
    public void onEnable() {
        // Load the plugin's config
        saveDefaultConfig();
        // Load settings
        settings = new Settings(this);
        // Register commands
        // AcidIsland hook in
        this.getPlugin().getAddonsManager().getAddonByName("AcidIsland").ifPresent(a -> {
            CompositeCommand acidIslandCmd = getPlugin().getCommandsManager().getCommand(getConfig().getString("acidisland.user-command","ai"));
            if (acidIslandCmd != null) {
                CompositeCommand acidCmd = getPlugin().getCommandsManager().getCommand(getConfig().getString("acidisland.admin-command","acid"));
            }
        });
        // BSkyBlock hook in
        this.getPlugin().getAddonsManager().getAddonByName("BSkyBlock").ifPresent(a -> {
            CompositeCommand bsbIslandCmd = getPlugin().getCommandsManager().getCommand(getConfig().getString("bskyblock.user-command","island"));
            if (bsbIslandCmd != null) {
                CompositeCommand bsbAdminCmd = getPlugin().getCommandsManager().getCommand(getConfig().getString("bskyblock.admin-command","bsbadmin"));
            }
        });

        // Register new island listener
        //registerListener(new NewIslandListener(this));
        //registerListener(new JoinLeaveListener(this));
        // Done

    }

    /**
     * Save the levels to the database
     */
    private void save(){
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }


}

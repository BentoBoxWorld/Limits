package world.bentobox.limits.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.limits.Limits;

/**
 *
 * @author tastybento
 */
public class RecountCommand extends CompositeCommand {

    private final Limits addon;

    /**
     * Player command to do a recount. Has a cooldown
     *
     * @param addon - addon
     */
    public RecountCommand(Limits addon, CompositeCommand parent) {
        super(parent, "recount");
        this.addon = addon;
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#setup()
     */
    @Override
    public void setup() {
        this.setPermission("limits.player.recount");
        this.setOnlyPlayer(true);
        this.setParametersHelp("island.limits.recount.parameters");
        this.setDescription("island.limits.recount.description");
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#execute(world.bentobox.bentobox.api.user.User, java.lang.String, java.util.List)
     */
    @Override
    public boolean canExecute(User user, String label, List<String> args) {
        if (!args.isEmpty()) {
            showHelp(this, user);
            return false;
        }
        if (addon.getIslands().getIsland(getWorld(), user) == null) {
            user.sendMessage("general.errors.no-island");
            return false;
        }
        return !checkCooldown(user);
    }
    @Override
    public boolean execute(User user, String label, List<String> args) {
        // Set cooldown
        setCooldown(user.getUniqueId(), addon.getConfig().getInt("cooldown", 120));
        new LimitsCalc(getWorld(), getPlugin(), user.getUniqueId(), addon, user);
        return true;
    }

}

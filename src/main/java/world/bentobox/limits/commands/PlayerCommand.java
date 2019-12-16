package world.bentobox.limits.commands;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.limits.Limits;

/**
 * User command for limits
 * @author tastybento
 *
 */
public class PlayerCommand extends CompositeCommand {

    private final Limits addon;

    /**
     * Top level command
     * @param addon - addon
     */
    public PlayerCommand(Limits addon, CompositeCommand parent) {
        super(parent, "limits");
        this.addon = addon;
        new RecountCommand(addon, this);
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#setup()
     */
    @Override
    public void setup() {
        this.setPermission("limits.player.limits");
        this.setOnlyPlayer(true);
        this.setParametersHelp("island.limits.parameters");
        this.setDescription("island.limits.description");
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#execute(world.bentobox.bentobox.api.user.User, java.lang.String, java.util.List)
     */
    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (!args.isEmpty()) {
            showHelp(this, user);
            return false;
        } else {
            new LimitPanel(addon).showLimits(getWorld(), user, user.getUniqueId());
            return true;
        }
    }

}

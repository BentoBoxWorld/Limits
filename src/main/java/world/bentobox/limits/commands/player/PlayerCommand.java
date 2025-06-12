package world.bentobox.limits.commands.player;

import java.util.List;
import java.util.Optional;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
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
            // Report the limit for the island, which is governed by the owner of the island
            Optional<Island> opIsland = getIslands().getIslandAt(user.getLocation());
            if (opIsland.isEmpty()) {
                user.sendMessage("island.limits.errors.not-on-island");
                return false;
            }
            Island island = opIsland.get();
            if (!island.getWorld().equals(getWorld())) {
                user.sendMessage("general.errors.wrong-world");
                return false;
            }
            if (island.getOwner() == null) {
                user.sendMessage("island.limits.errors.no-owner");
                return false;
            }
            new LimitPanel(addon).showLimits((GameModeAddon) getAddon(), user, island.getOwner());
            return true;
        }
    }

}

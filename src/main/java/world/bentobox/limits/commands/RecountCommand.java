package world.bentobox.limits.commands;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Pipeliner;

/**
 *
 * @author tastybento
 */
public class RecountCommand extends CompositeCommand {

    private final Limits addon;
    private @Nullable Island island;

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
        island = addon.getIslands().getIsland(getWorld(), user);
        if (island == null) {
            user.sendMessage("general.errors.no-island");
            return false;
        }
        return !checkCooldown(user);
    }
    @Override
    public boolean execute(User user, String label, List<String> args) {
        // Set cooldown
        setCooldown(user.getUniqueId(), addon.getConfig().getInt("cooldown", 120));
        user.sendMessage("island.limits.recount.now-recounting");
        new Pipeliner(addon).addIsland(island).thenAccept(results -> {
            if (results == null) {
                user.sendMessage("island.limits.recount.in-progress");
            } else {
                switch (results.getState()) {
                case TIMEOUT -> user.sendMessage("admin.limits.calc.timeout");
                default -> user.sendMessage("admin.limits.calc.finished");
                }
            }
        });
        return true;
    }

}

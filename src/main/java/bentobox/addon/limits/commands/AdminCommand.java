package bentobox.addon.limits.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import bentobox.addon.limits.Limits;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

/**
 * Admin command for limits
 * @author tastybento
 *
 */
public class AdminCommand extends CompositeCommand {

    private final Limits addon;

    /**
     * Admin command
     * @param addon - addon
     */
    public AdminCommand(Limits addon, CompositeCommand parent) {
        super(parent, "limits");
        this.addon = addon;
        new CalcCommand(addon, this); 
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#setup()
     */
    @Override
    public void setup() {
        this.setPermission("limits.admin.limits");
        this.setOnlyPlayer(true);
        this.setParametersHelp("admin.limits.main.parameters");
        this.setDescription("admin.limits.main.description");    
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#execute(world.bentobox.bentobox.api.user.User, java.lang.String, java.util.List)
     */
    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.size() == 1) {
            // Asking for another player's limits
            // Convert name to a UUID
            final UUID playerUUID = getPlugin().getPlayers().getUUID(args.get(0));
            if (playerUUID == null) {
                user.sendMessage("general.errors.unknown-player", args.get(0));
                return true;
            } else {
                new LimitPanel(addon).showLimits(getWorld(), user, playerUUID);
            }
            return true;
        } else {
            showHelp(this, user);
            return false;
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        String lastArg = !args.isEmpty() ? args.get(args.size()-1) : "";
        if (args.isEmpty()) {
            // Don't show every player on the server. Require at least the first letter
            return Optional.empty();
        }
        List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
        return Optional.of(Util.tabLimit(options, lastArg));
    }

}

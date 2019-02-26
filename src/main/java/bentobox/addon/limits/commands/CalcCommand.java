package bentobox.addon.limits.commands;

import bentobox.addon.limits.Limits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

/**
 *
 * @author YellowZaki
 */
public class CalcCommand extends CompositeCommand {

    private final Limits addon;

    /**
     * Admin command
     *
     * @param addon - addon
     */
    public CalcCommand(Limits addon, CompositeCommand parent) {
        super(parent, "calc");
        this.addon = addon;
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#setup()
     */
    @Override
    public void setup() {
        this.setPermission("limits.admin.limits.calc");
        this.setOnlyPlayer(false);
        this.setParametersHelp("admin.limits.calc.parameters");
        this.setDescription("admin.limits.calc.description");
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.api.commands.BentoBoxCommand#execute(world.bentobox.bentobox.api.user.User, java.lang.String, java.util.List)
     */
    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (args.size() == 1) {
            final UUID playerUUID = getPlugin().getPlayers().getUUID(args.get(0));
            if (playerUUID == null) {
                user.sendMessage("general.errors.unknown-player", args.get(0));
                return true;
            } else {
                //Calculate
                calcLimits(playerUUID, user);
            }
            return true;
        } else {
            showHelp(this, user);
            return false;
        }
    }

    public void calcLimits(UUID targetPlayer, User sender) {
        if (addon.getIslands().getIsland(getWorld(), targetPlayer) != null) {
            new LimitsCalc(getWorld(), getPlugin(), targetPlayer, addon, sender);
        } else {
            sender.sendMessage("general.errors.player-has-no-island");
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";
        if (args.isEmpty()) {
            // Don't show every player on the server. Require at least the first letter
            return Optional.empty();
        }
        List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
        return Optional.of(Util.tabLimit(options, lastArg));
    }

}

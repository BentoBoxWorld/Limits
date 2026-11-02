package world.bentobox.limits.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Pipeliner;
import world.bentobox.limits.calculators.Results;

/**
 * Admin recount of one player's island, or of every island in the world with {@code all}.
 * An optional {@code entities} argument recounts entities only and leaves block counts alone.
 *
 * @author YellowZaki, tastybento
 */
public class CalcCommand extends CompositeCommand {

    private static final String ALL = "all";
    private static final String ENTITIES = "entities";

    private final Limits addon;

    /**
     * Admin command
     *
     * @param addon - addon
     */
    public CalcCommand(Limits addon, CompositeCommand parent) {
        super(parent, "calc", "recount");
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
        if (args.isEmpty() || args.size() > 2 || (args.size() == 2 && !args.get(1).equalsIgnoreCase(ENTITIES))) {
            showHelp(this, user);
            return false;
        }
        boolean entitiesOnly = args.size() == 2;
        if (args.get(0).equalsIgnoreCase(ALL)) {
            recountAll(user, entitiesOnly);
            return true;
        }
        final UUID playerUUID = getPlugin().getPlayers().getUUID(args.get(0));
        if (playerUUID == null) {
            user.sendMessage("general.errors.unknown-player", TextVariables.NAME, args.get(0));
            return false;
        }
        Island island = addon.getIslands().getIsland(getWorld(), playerUUID);
        if (island == null) {
            user.sendMessage("general.errors.player-has-no-island");
            return false;
        }
        user.sendMessage("island.limits.recount.now-recounting");
        queue(island, entitiesOnly).thenAccept(results -> handlePipelineResult(user, results));
        return true;
    }

    /**
     * Queues every owned island in this world. The shared queue recounts them one at a time,
     * so this never loads more than one island's chunks at once.
     */
    private void recountAll(User user, boolean entitiesOnly) {
        List<Island> islands = addon.getIslands().getIslands(getWorld()).stream()
                .filter(i -> !i.isDeleted() && !i.isUnowned()).toList();
        user.sendMessage("admin.limits.calc.all-queued", TextVariables.NUMBER, String.valueOf(islands.size()));
        AtomicInteger timedOut = new AtomicInteger();
        CompletableFuture<?>[] futures = islands.stream()
                .map(i -> queue(i, entitiesOnly).thenAccept(results -> {
                    if (results != null && results.getState() == Results.Result.TIMEOUT) {
                        timedOut.incrementAndGet();
                    }
                }))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).thenRun(() -> user.sendMessage("admin.limits.calc.all-finished",
                TextVariables.NUMBER, String.valueOf(islands.size()), "[timeouts]", String.valueOf(timedOut.get())));
    }

    private CompletableFuture<Results> queue(Island island, boolean entitiesOnly) {
        Pipeliner pipeliner = addon.getPipeliner();
        return entitiesOnly ? pipeliner.addIslandEntitiesOnly(island) : pipeliner.addIsland(island);
    }

    private void handlePipelineResult(User user, Results results) {
        if (results == null || results.getState() == Results.Result.IN_PROGRESS) {
            user.sendMessage("island.limits.recount.in-progress");
        } else if (results.getState() == Results.Result.TIMEOUT) {
            user.sendMessage("island.limits.recount.time-out");
        } else {
            user.sendMessage("admin.limits.calc.finished");
        }
    }

    @Override
    public Optional<List<String>> tabComplete(User user, String alias, List<String> args) {
        String lastArg = !args.isEmpty() ? args.get(args.size() - 1) : "";
        if (args.isEmpty()) {
            // Don't show every player on the server. Require at least the first letter
            return Optional.empty();
        }
        // Tab-complete args include the command labels: limits calc <player|all> [entities]
        if (args.size() == 4) {
            return Optional.of(Util.tabLimit(List.of(ENTITIES), lastArg));
        }
        List<String> options = new ArrayList<>(Util.getOnlinePlayerList(user));
        options.add(ALL);
        return Optional.of(Util.tabLimit(options, lastArg));
    }

}

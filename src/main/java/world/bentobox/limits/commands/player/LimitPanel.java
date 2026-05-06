package world.bentobox.limits.commands.player;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.panels.builders.TabbedPanelBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Shows a panel of the limited blocks/entities and their counts per environment.
 *
 * @author tastybento
 */
public class LimitPanel {

    private final Limits addon;

    public LimitPanel(Limits addon) {
        this.addon = addon;
    }

    public void showLimits(GameModeAddon gm, User user, UUID target) {
        World overWorld = gm.getOverWorld();
        Island island = addon.getIslands().getIsland(overWorld, target);
        if (island == null) {
            user.sendMessage(user.getUniqueId().equals(target)
                    ? "general.errors.no-island"
                    : "general.errors.player-has-no-island");
            return;
        }
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            addon.getJoinListener().checkPerms(targetPlayer, gm.getPermissionPrefix() + "island.limit.",
                    island.getUniqueId(), gm.getDescription().getName());
        }
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());

        TabbedPanelBuilder tpb = new TabbedPanelBuilder()
                .user(user)
                .world(overWorld)
                .startingSlot(0)
                .size(54);

        // Always show overworld; show nether/end only if the gamemode generates and provides them.
        int slot = 0;
        slot = addEnvTab(tpb, gm, overWorld, ibc, island, user, Environment.NORMAL, slot);
        World netherWorld = gm.getNetherWorld();
        if (netherWorld != null && addon.getPlugin().getIWM().isNetherIslands(overWorld)) {
            slot = addEnvTab(tpb, gm, netherWorld, ibc, island, user, Environment.NETHER, slot);
        }
        World endWorld = gm.getEndWorld();
        if (endWorld != null && addon.getPlugin().getIWM().isEndIslands(overWorld)) {
            slot = addEnvTab(tpb, gm, endWorld, ibc, island, user, Environment.THE_END, slot);
        }

        Map<NamespacedKey, Integer> overworldLimits = addon.getBlockLimitListener().getMaterialLimits(overWorld,
                island.getUniqueId());
        if (overworldLimits.isEmpty() && addon.getSettings().getLimits(Environment.NORMAL).isEmpty() && slot == 0) {
            user.sendMessage("island.limits.no-limits");
            return;
        }

        tpb.build().openPanel();
    }

    private int addEnvTab(TabbedPanelBuilder tpb, GameModeAddon gm, World envWorld, IslandBlockCount ibc, Island island,
            User user, Environment env, int slot) {
        Map<NamespacedKey, Integer> matLimits = addon.getBlockLimitListener().getMaterialLimits(envWorld,
                island.getUniqueId());
        // Skip the env tab entirely if there is nothing to show for it.
        if (matLimits.isEmpty() && addon.getSettings().getLimits(env).isEmpty()
                && addon.getSettings().getGroupLimits(env).isEmpty()) {
            return slot;
        }
        tpb.tab(slot, new LimitTab(addon, ibc, matLimits, island, envWorld, user, env));
        return slot + 1;
    }
}

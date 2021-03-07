package world.bentobox.limits.commands;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.panels.builders.TabbedPanelBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Shows a panel of the blocks that are limited and their status
 * @author tastybento
 *
 */
public class LimitPanel {

    private final Limits addon;

    /**
     * @param addon - limit addon
     */
    public LimitPanel(Limits addon) {
        this.addon = addon;
    }

    /**
     * Show the limits panel
     * @param gm - game mode
     * @param user - user asking
     * @param target  - target uuid
     */
    public void showLimits(GameModeAddon gm, User user, UUID target) {
        // Get world
        World world = gm.getOverWorld();
        // Get the island for the target
        Island island = addon.getIslands().getIsland(world, target);
        if (island == null) {
            if (user.getUniqueId().equals(target)) {
                user.sendMessage("general.errors.no-island");
            } else {
                user.sendMessage("general.errors.player-has-no-island");
            }
            return;
        }
        // See if the target is online
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            // Update perms
            addon.getJoinListener().checkPerms(targetPlayer, gm.getPermissionPrefix(), island.getUniqueId(), gm.getDescription().getName());
        }
        // Get the limits for this island
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
        Map<Material, Integer> matLimits = addon.getBlockLimitListener().getMaterialLimits(world, island.getUniqueId());
        if (matLimits.isEmpty() && addon.getSettings().getLimits().isEmpty()) {
            user.sendMessage("island.limits.no-limits");
            return;
        }

        new TabbedPanelBuilder()
        .user(user)
        .world(world)
        .tab(0, new LimitTab(addon, ibc, matLimits, island, world, user, LimitTab.SORT_BY.A2Z))
        .tab(1, new LimitTab(addon, ibc, matLimits, island, world, user, LimitTab.SORT_BY.Z2A))
        .startingSlot(0)
        .size(54)
        .build().openPanel();

    }

}

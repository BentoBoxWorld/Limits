package bentobox.addon.limits.commands;

import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.objects.IslandBlockCount;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.panels.builders.PanelBuilder;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

/**
 * Shows a panel of the blocks that are limited and their status
 * @author tastybento
 *
 */
class LimitPanel {

    private final Limits addon;

    /**
     * @param addon - limit addon
     */
    public LimitPanel(Limits addon) {
        this.addon = addon;
    }

    public void showLimits(World world, User user, UUID target) {
        PanelBuilder pb = new PanelBuilder().name(user.getTranslation(world, "limits.panel-title")).user(user);
        // Get the island for the target
        Island island = addon.getIslands().getIsland(world, target);
        if (island == null) {
            user.sendMessage("general.errors.player-has-no-island");
            return;
        }
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
        Map<Material, Integer> matLimits = addon.getBlockLimitListener().getMaterialLimits(world, island.getUniqueId());
        if (matLimits.isEmpty()) {
            user.sendMessage("island.limits.no-limits");
            return;
        }
        for (Entry<Material, Integer> en : matLimits.entrySet()) {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(en.getKey().toString()));
            pib.icon(en.getKey());
            int count = ibc == null ? 0 : ibc.getBlockCount().getOrDefault(en.getKey(), 0);
            String color = count >= en.getValue() ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            pib.description(color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(en.getValue())));
            pb.item(pib.build());
        }
        pb.build();
    }

}

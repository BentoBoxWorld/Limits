package bentobox.addon.limits.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

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
public class LimitPanel {

    private final Limits addon;
    public final static Map<EntityType, Material> E2M = new HashMap<>();
    static {
        E2M.put(EntityType.PIG_ZOMBIE, Material.ZOMBIE_PIGMAN_SPAWN_EGG);
        E2M.put(EntityType.SNOWMAN, Material.SNOW_BLOCK);
        E2M.put(EntityType.IRON_GOLEM, Material.IRON_BLOCK);
        E2M.put(EntityType.ILLUSIONER, Material.VILLAGER_SPAWN_EGG);
        E2M.put(EntityType.WITHER, Material.WITHER_SKELETON_SKULL);
        E2M.put(EntityType.BOAT, Material.OAK_BOAT);
        E2M.put(EntityType.ARMOR_STAND, Material.ARMOR_STAND);

        // Minecarts
        E2M.put(EntityType.MINECART_TNT, Material.TNT_MINECART);
        E2M.put(EntityType.MINECART_CHEST, Material.CHEST_MINECART);
        E2M.put(EntityType.MINECART_COMMAND, Material.COMMAND_BLOCK_MINECART);
        E2M.put(EntityType.MINECART_FURNACE, Material.FURNACE_MINECART);
        E2M.put(EntityType.MINECART_HOPPER, Material.HOPPER_MINECART);
        E2M.put(EntityType.MINECART_MOB_SPAWNER, Material.MINECART);
        E2M.put(EntityType.MINECART_TNT, Material.TNT_MINECART);
        // Disallowed
        E2M.put(EntityType.PRIMED_TNT, null);
        E2M.put(EntityType.EVOKER_FANGS, null);
        E2M.put(EntityType.LLAMA_SPIT, null);
        E2M.put(EntityType.DRAGON_FIREBALL, null);
        E2M.put(EntityType.AREA_EFFECT_CLOUD, null);
        E2M.put(EntityType.ENDER_SIGNAL, null);
        E2M.put(EntityType.SMALL_FIREBALL, null);
        E2M.put(EntityType.DRAGON_FIREBALL, null);
        E2M.put(EntityType.FIREBALL, null);
        E2M.put(EntityType.THROWN_EXP_BOTTLE, null);
        E2M.put(EntityType.EXPERIENCE_ORB, null);
        E2M.put(EntityType.SHULKER_BULLET, null);
        E2M.put(EntityType.WITHER_SKULL, null);
        E2M.put(EntityType.TRIDENT, null);
        E2M.put(EntityType.ARROW, null);
        E2M.put(EntityType.SPECTRAL_ARROW, null);
        E2M.put(EntityType.SNOWBALL, null);
        E2M.put(EntityType.EGG, null);
        E2M.put(EntityType.LEASH_HITCH, null);
        E2M.put(EntityType.ITEM_FRAME, null);
        E2M.put(EntityType.PAINTING, null);
        E2M.put(EntityType.GIANT, null);
        E2M.put(EntityType.ENDER_CRYSTAL, null);
        E2M.put(EntityType.ENDER_PEARL, null);
        E2M.put(EntityType.ENDER_DRAGON, null);

    }

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
            if (user.getUniqueId().equals(target)) {
                user.sendMessage("general.errors.no-island");
            } else {
                user.sendMessage("general.errors.player-has-no-island");
            }
            return;
        }
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
        Map<Material, Integer> matLimits = addon.getBlockLimitListener().getMaterialLimits(world, island.getUniqueId());
        if (matLimits.isEmpty() && addon.getSettings().getLimits().isEmpty()) {
            user.sendMessage("island.limits.no-limits");
            return;
        }
        for (Entry<Material, Integer> en : matLimits.entrySet()) {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(en.getKey().toString()));
            if (en.getKey() == Material.REDSTONE_WIRE) {
                pib.icon(Material.REDSTONE);
            }
            else {
                pib.icon(en.getKey());
            }
            int count = ibc == null ? 0 : ibc.getBlockCount().getOrDefault(en.getKey(), 0);
            String color = count >= en.getValue() ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            pib.description(color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(en.getValue())));
            pb.item(pib.build());
        }
        addon.getSettings().getLimits().forEach((k,v) -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(k.toString()));
            Material m = Material.BARRIER;
            try {
                if (E2M.containsKey(k)) {
                    m = E2M.get(k);
                } else if (k.isAlive()) {
                    m = Material.valueOf(k.toString() + "_SPAWN_EGG");
                } else {
                    m = Material.valueOf(k.toString());
                }
            } catch (Exception e) {
                m = Material.BARRIER;
            }
            pib.icon(m);
            long count = getCount(island, k);
            String color = count >= v ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            pib.description(color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(v)));
            pb.item(pib.build());
        });
        pb.build();
    }

    private long getCount(Island island, EntityType ent) {
        return island.getWorld().getEntities().stream()
                .filter(e -> e.getType().equals(ent))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
    }
}

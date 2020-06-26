package world.bentobox.limits.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.panels.builders.PanelBuilder;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings;
import world.bentobox.limits.Settings.EntityGroup;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Shows a panel of the blocks that are limited and their status
 * @author tastybento
 *
 */
public class LimitPanel {

    private final Limits addon;
    // This maps the entity types to the icon that should be shown in the panel
    // If the icon is null, then the entity type is not covered by the addon
    private static final Map<EntityType, Material> E2M = ImmutableMap.<EntityType, Material>builder()
            .put(EntityType.MUSHROOM_COW, Material.MOOSHROOM_SPAWN_EGG)
            .put(EntityType.SNOWMAN, Material.SNOW_BLOCK)
            .put(EntityType.IRON_GOLEM, Material.IRON_BLOCK)
            .put(EntityType.ILLUSIONER, Material.VILLAGER_SPAWN_EGG)
            .put(EntityType.WITHER, Material.WITHER_SKELETON_SKULL)
            .put(EntityType.BOAT, Material.OAK_BOAT)
            .put(EntityType.ARMOR_STAND, Material.ARMOR_STAND)
            .put(EntityType.ITEM_FRAME, Material.ITEM_FRAME)
            .put(EntityType.PAINTING, Material.PAINTING)
            // Minecarts
            .put(EntityType.MINECART_TNT, Material.TNT_MINECART)
            .put(EntityType.MINECART_CHEST, Material.CHEST_MINECART)
            .put(EntityType.MINECART_COMMAND, Material.COMMAND_BLOCK_MINECART)
            .put(EntityType.MINECART_FURNACE, Material.FURNACE_MINECART)
            .put(EntityType.MINECART_HOPPER, Material.HOPPER_MINECART)
            .put(EntityType.MINECART_MOB_SPAWNER, Material.MINECART)
            .build();
    // This is a map of blocks to Material
    private static final Map<Material, Material> B2M;
    static {
        ImmutableMap.Builder<Material, Material> builder = ImmutableMap.<Material, Material>builder()
                .put(Material.POTATOES, Material.POTATO)
                .put(Material.CARROTS, Material.CARROT)
                .put(Material.BEETROOTS, Material.BEETROOT)
                .put(Material.REDSTONE_WIRE, Material.REDSTONE);
        // Block to Material icons
        Optional.ofNullable(Material.getMaterial("SWEET_BERRY_BUSH")).ifPresent(material -> builder.put(material, Objects.requireNonNull(Material.getMaterial("SWEET_BERRIES"))));
        Optional.ofNullable(Material.getMaterial("BAMBOO_SAPLING")).ifPresent(material -> builder.put(material, Objects.requireNonNull(Material.getMaterial("BAMBOO"))));
        B2M = builder.build();
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
        // Material limits
        for (Entry<Material, Integer> en : matLimits.entrySet()) {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(en.getKey().toString()));
            // Adjust icon
            pib.icon(B2M.getOrDefault(en.getKey(), en.getKey()));

            int count = ibc == null ? 0 : ibc.getBlockCount().getOrDefault(en.getKey(), 0);
            String color = count >= en.getValue() ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            pib.description(color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(en.getValue())));
            pb.item(pib.build());
        }
        // Entity limits
        Map<EntityType, Integer> map = new HashMap<>(addon.getSettings().getLimits());
        // Merge in any permission-based limits
        if (ibc != null) ibc.getEntityLimits().forEach(map::put);
        map.forEach((k,v) -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(k.toString()));
            Material m;
            try {
                if (E2M.containsKey(k)) {
                    m = E2M.get(k);
                } else if (k.isAlive()) {
                    m = Material.valueOf(k.toString() + "_SPAWN_EGG");
                } else {
                    // Regular material
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
        
        // Entity group limits
        List<Settings.EntityGroup> groupmap = addon.getSettings().getGroupLimitDefinitions();
        // Merge in any permission-based limits
//        if (ibc != null) ibc.getEntityLimits().forEach(map::put);
        groupmap.forEach(v -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            EntityType k = v.getTypes().iterator().next();
            pib.name(v.getName());
            String description = "";
            description += "(" + prettyNames(v) + ")\n";
            Material m;
            try {
                if (E2M.containsKey(k)) {
                    m = E2M.get(k);
                } else if (k.isAlive()) {
                    m = Material.valueOf(k.toString() + "_SPAWN_EGG");
                } else {
                    // Regular material
                    m = Material.valueOf(k.toString());
                }
            } catch (Exception e) {
                m = Material.BARRIER;
            }
            pib.icon(m);
            long count = getCount(island, v);
            String color = count >= v.getLimit() ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            description += color
                            + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(v.getLimit()));
            pib.description(description);
            pb.item(pib.build());
        });
        pb.build();
    }

    long getCount(Island island, EntityType ent) {
        long count = island.getWorld().getEntities().stream()
                .filter(e -> e.getType().equals(ent))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
        // Nether
        if (addon.getPlugin().getIWM().isNetherIslands(island.getWorld()) && addon.getPlugin().getIWM().getNetherWorld(island.getWorld()) != null) {
            count += addon.getPlugin().getIWM().getNetherWorld(island.getWorld()).getEntities().stream()
                    .filter(e -> e.getType().equals(ent))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
        }
        // End
        if (addon.getPlugin().getIWM().isEndIslands(island.getWorld()) && addon.getPlugin().getIWM().getEndWorld(island.getWorld()) != null) {
            count += addon.getPlugin().getIWM().getEndWorld(island.getWorld()).getEntities().stream()
                    .filter(e -> e.getType().equals(ent))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
        }
        return count;
    }
    
    long getCount(Island island, EntityGroup group) {
        long count = island.getWorld().getEntities().stream()
                .filter(e -> group.contains(e.getType()))
                .filter(e -> island.inIslandSpace(e.getLocation())).count();
        // Nether
        if (addon.getPlugin().getIWM().isNetherIslands(island.getWorld()) && addon.getPlugin().getIWM().getNetherWorld(island.getWorld()) != null) {
            count += addon.getPlugin().getIWM().getNetherWorld(island.getWorld()).getEntities().stream()
                    .filter(e -> group.contains(e.getType()))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
        }
        // End
        if (addon.getPlugin().getIWM().isEndIslands(island.getWorld()) && addon.getPlugin().getIWM().getEndWorld(island.getWorld()) != null) {
            count += addon.getPlugin().getIWM().getEndWorld(island.getWorld()).getEntities().stream()
                    .filter(e -> group.contains(e.getType()))
                    .filter(e -> island.inIslandSpace(e.getLocation())).count();
        }
        return count;
    }
    
    private String prettyNames(EntityGroup v) {
        StringBuilder sb = new StringBuilder();
        List<EntityType> l = new ArrayList<>(v.getTypes());
        for(int i = 0; i < l.size(); i++)
        {
            sb.append(Util.prettifyText(l.get(i).toString()));
            if (i + 1 < l.size())
                sb.append(", ");
            if((i+1) % 5 == 0)
                sb.append("\n");
        }
        return sb.toString();
    }
}

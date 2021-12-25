package world.bentobox.limits.commands;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.panels.Tab;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;
import world.bentobox.limits.Settings.EntityGroup;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * @author tastybento
 *
 */
public class LimitTab implements Tab {

    enum SORT_BY {
        A2Z,
        Z2A
    }
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

    private final World world;
    private final User user;
    private final Limits addon;
    private final List<@Nullable PanelItem> result;
    private final SORT_BY sortBy;

    public LimitTab(Limits addon, IslandBlockCount ibc, Map<Material, Integer> matLimits, Island island, World world, User user, SORT_BY sortBy) {
        this.addon = addon;
        this.world = world;
        this.user = user;
        this.sortBy = sortBy;
        result = new ArrayList<>();
        addMaterialIcons(ibc, matLimits);
        addEntityLimits(ibc, island);
        addEntityGroupLimits(ibc, island);
        // Sort
        if (sortBy == SORT_BY.Z2A) {
            result.sort((o1, o2) -> o2.getName().compareTo(o1.getName()));
        } else {
            result.sort(Comparator.comparing(PanelItem::getName));
        }

    }

    private void addEntityGroupLimits(IslandBlockCount ibc, Island island) {
        // Entity group limits
        Map<EntityGroup, Integer> groupMap = addon.getSettings().getGroupLimitDefinitions().stream().collect(Collectors.toMap(e -> e, EntityGroup::getLimit));
        // Group by same loop up map
        Map<String, EntityGroup> groupByName = groupMap.keySet().stream().collect(Collectors.toMap(EntityGroup::getName, e -> e));
        // Merge in any permission-based limits
        if (ibc == null) {
            return;
        }
        ibc.getEntityGroupLimits().entrySet().stream()
        .filter(e -> groupByName.containsKey(e.getKey()))
        .forEach(e -> groupMap.put(groupByName.get(e.getKey()), e.getValue()));
        // Update the group map for each group limit offset. If the value already exists add it
        ibc.getEntityGroupLimitsOffset().forEach((key, value) ->
        groupMap.put(groupByName.get(key), (groupMap.getOrDefault(groupByName.get(key), 0) + value)));
        groupMap.forEach((v, limit) -> {
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
                    m = Material.valueOf(k + "_SPAWN_EGG");
                } else {
                    // Regular material
                    m = Material.valueOf(k.toString());
                }
            } catch (Exception e) {
                m = Material.BARRIER;
            }
            pib.icon(m);
            long count = getCount(island, v);
            String color = count >= limit ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            description += color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(limit));
            pib.description(description);
            result.add(pib.build());
        });
    }

    private void addEntityLimits(IslandBlockCount ibc, Island island) {
        // Entity limits
        Map<EntityType, Integer> map = new HashMap<>(addon.getSettings().getLimits());
        // Merge in any permission-based limits
        if (ibc != null) {
            map.putAll(ibc.getEntityLimits());
            ibc.getEntityLimitsOffset().forEach((k,v) -> map.put(k, map.getOrDefault(k, 0) + v));
        }

        map.forEach((k,v) -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(k.toString()));
            Material m;
            try {
                if (E2M.containsKey(k)) {
                    m = E2M.get(k);
                } else if (k.isAlive()) {
                    m = Material.valueOf(k + "_SPAWN_EGG");
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
            result.add(pib.build());
        });

    }

    private void addMaterialIcons(IslandBlockCount ibc, Map<Material, Integer> matLimits) {
        // Material limits
        for (Entry<Material, Integer> en : matLimits.entrySet()) {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(Util.prettifyText(en.getKey().toString()));
            // Adjust icon
            pib.icon(B2M.getOrDefault(en.getKey(), en.getKey()));

            int count = ibc == null ? 0 : ibc.getBlockCounts().getOrDefault(en.getKey(), 0);
            int value = en.getValue() + (ibc == null ? 0 : ibc.getBlockLimitsOffset().getOrDefault(en.getKey(), 0));
            String color = count >= value ? user.getTranslation("island.limits.max-color") : user.getTranslation("island.limits.regular-color");
            pib.description(color
                    + user.getTranslation("island.limits.block-limit-syntax",
                            TextVariables.NUMBER, String.valueOf(count),
                            "[limit]", String.valueOf(value)));
            result.add(pib.build());
        }
    }

    @Override
    public PanelItem getIcon() {
        return new PanelItemBuilder().icon(Material.MAGENTA_GLAZED_TERRACOTTA).name(this.getName()).build();
    }

    @Override
    public String getName() {
        return user.getTranslation(world, "limits.panel-title") + " " + sortBy.name();
    }

    @Override
    public List<@Nullable PanelItem> getPanelItems() {
        return result;
    }

    @Override
    public String getPermission() {
        return "";
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
}

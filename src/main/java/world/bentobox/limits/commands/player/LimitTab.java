package world.bentobox.limits.commands.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.panels.PanelItem;
import world.bentobox.bentobox.api.panels.Tab;
import world.bentobox.bentobox.api.panels.builders.PanelItemBuilder;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.EntityGroup;
import world.bentobox.limits.Limits;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * One tab of the limits panel. Shows counts and limits for a single environment.
 *
 * @author tastybento
 */
public class LimitTab implements Tab {

    private static final String MAX_COLOR_KEY = "island.limits.max-color";
    private static final String REGULAR_COLOR_KEY = "island.limits.regular-color";
    private static final String BLOCK_LIMIT_SYNTAX_KEY = "island.limits.block-limit-syntax";
    private static final String LIMIT_PLACEHOLDER = "[limit]";

    /** This maps the entity types to the icon that should be shown in the panel */
    private static final Map<EntityType, Material> E2M = ImmutableMap.<EntityType, Material>builder()
            .put(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG).put(EntityType.SNOW_GOLEM, Material.SNOW_BLOCK)
            .put(EntityType.IRON_GOLEM, Material.IRON_BLOCK)
            .put(EntityType.ILLUSIONER, Material.VILLAGER_SPAWN_EGG)
            .put(EntityType.WITHER, Material.WITHER_SKELETON_SKULL)
            .put(EntityType.ARMOR_STAND, Material.ARMOR_STAND)
            .put(EntityType.ITEM_FRAME, Material.ITEM_FRAME)
            .put(EntityType.PAINTING, Material.PAINTING)
            .put(EntityType.TNT_MINECART, Material.TNT_MINECART).put(EntityType.CHEST_MINECART, Material.CHEST_MINECART)
            .put(EntityType.COMMAND_BLOCK_MINECART, Material.COMMAND_BLOCK_MINECART)
            .put(EntityType.FURNACE_MINECART, Material.FURNACE_MINECART)
            .put(EntityType.HOPPER_MINECART, Material.HOPPER_MINECART)
            .put(EntityType.SPAWNER_MINECART, Material.MINECART)
            .build();

    private static final Map<NamespacedKey, NamespacedKey> B2M;
    static {
        B2M = ImmutableMap.<NamespacedKey, NamespacedKey>builder()
                .put(Material.POTATOES.getKey(), Material.POTATO.getKey())
                .put(Material.CARROTS.getKey(), Material.CARROT.getKey())
                .put(Material.BEETROOTS.getKey(), Material.BEETROOT.getKey())
                .put(Material.REDSTONE_WIRE.getKey(), Material.REDSTONE.getKey())
                .put(Material.MELON_STEM.getKey(), Material.MELON.getKey())
                .put(Material.PUMPKIN_STEM.getKey(), Material.PUMPKIN.getKey())
                .put(Material.SWEET_BERRY_BUSH.getKey(), Material.SWEET_BERRIES.getKey())
                .put(Material.BAMBOO_SAPLING.getKey(), Material.BAMBOO.getKey())
                .build();
    }

    private final World world;
    private final User user;
    private final Limits addon;
    private final Environment env;
    private final List<@Nullable PanelItem> result;

    public LimitTab(Limits addon, IslandBlockCount ibc, Map<NamespacedKey, Integer> matLimits,
            World world, User user, Environment env) {
        this.addon = addon;
        this.world = world;
        this.user = user;
        this.env = env;
        result = new ArrayList<>();
        addMaterialIcons(ibc, matLimits);
        addEntityLimits(ibc);
        addEntityGroupLimits(ibc);
        result.sort(Comparator.comparing(PanelItem::getName));
    }

    private void addEntityGroupLimits(IslandBlockCount ibc) {
        Map<String, Integer> envGroupLimits = new HashMap<>(addon.getSettings().getGroupLimits(env));
        Map<EntityGroup, Integer> groupMap = addon.getSettings().getGroupLimitDefinitions().stream()
                .filter(g -> envGroupLimits.containsKey(g.getName()))
                .collect(Collectors.toMap(g -> g, g -> envGroupLimits.get(g.getName())));
        Map<String, EntityGroup> groupByName = addon.getSettings().getGroupLimitDefinitions().stream()
                .collect(Collectors.toMap(EntityGroup::getName, e -> e));
        if (ibc != null) {
            ibc.getEntityGroupLimits(env).forEach((name, limit) -> {
                EntityGroup g = groupByName.get(name);
                if (g != null) groupMap.put(g, limit);
            });
            ibc.getEntityGroupLimitsOffset(env).forEach((name, offset) -> {
                EntityGroup g = groupByName.get(name);
                if (g != null) groupMap.put(g, groupMap.getOrDefault(g, 0) + offset);
            });
        }
        groupMap.forEach((g, limit) -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(user.getTranslation("island.limits.panel.entity-group-name-syntax", TextVariables.NAME,
                    g.getName()));
            String description = "(" + prettyNames(g) + ")\n";
            pib.icon(g.getIcon());
            int count = ibc == null ? 0 : sumGroupCount(ibc, g);
            String color = count >= limit ? user.getTranslation(MAX_COLOR_KEY)
                    : user.getTranslation(REGULAR_COLOR_KEY);
            description += color + user.getTranslation(BLOCK_LIMIT_SYNTAX_KEY,
                    TextVariables.NUMBER, String.valueOf(count),
                    LIMIT_PLACEHOLDER, String.valueOf(limit));
            pib.description(description);
            result.add(pib.build());
        });
    }

    private int sumGroupCount(IslandBlockCount ibc, EntityGroup group) {
        Map<EntityType, Integer> counts = ibc.getEntityCounts(env);
        int total = 0;
        for (EntityType t : group.getTypes()) {
            total += counts.getOrDefault(t, 0);
        }
        return total;
    }

    private void addEntityLimits(IslandBlockCount ibc) {
        Map<EntityType, Integer> map = new EnumMap<>(EntityType.class);
        map.putAll(addon.getSettings().getLimits(env));
        if (ibc != null) {
            map.putAll(ibc.getEntityLimits(env));
            ibc.getEntityLimitsOffset(env).forEach((k, v) -> map.put(k, map.getOrDefault(k, 0) + v));
        }
        map.forEach((k, v) -> {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(user.getTranslation("island.limits.panel.entity-name-syntax", TextVariables.NAME,
                    Util.prettifyText(k.toString())));
            Material m;
            try {
                if (E2M.containsKey(k)) {
                    m = E2M.get(k);
                } else if (k.isAlive()) {
                    m = Material.valueOf(k + "_SPAWN_EGG");
                } else {
                    m = Material.valueOf(k.toString());
                }
            } catch (Exception e) {
                m = Material.BARRIER;
            }
            pib.icon(m);
            int count = ibc == null ? 0 : ibc.getEntityCount(env, k);
            String color = count >= v ? user.getTranslation(MAX_COLOR_KEY)
                    : user.getTranslation(REGULAR_COLOR_KEY);
            pib.description(color + user.getTranslation(BLOCK_LIMIT_SYNTAX_KEY,
                    TextVariables.NUMBER, String.valueOf(count),
                    LIMIT_PLACEHOLDER, String.valueOf(v)));
            result.add(pib.build());
        });
    }

    private void addMaterialIcons(IslandBlockCount ibc, Map<NamespacedKey, Integer> matLimits) {
        for (Entry<NamespacedKey, Integer> en : matLimits.entrySet()) {
            PanelItemBuilder pib = new PanelItemBuilder();
            pib.name(user.getTranslation("island.limits.panel.block-name-syntax", TextVariables.NAME,
                    Util.prettifyText(en.getKey().getKey())));
            Material mat = Registry.MATERIAL.get(B2M.getOrDefault(en.getKey(), en.getKey()));
            pib.icon(Objects.requireNonNullElse(mat, Material.PAPER));

            int count = ibc == null ? 0 : ibc.getBlockCount(env, en.getKey());
            int value = en.getValue();
            String color = count >= value ? user.getTranslation(MAX_COLOR_KEY)
                    : user.getTranslation(REGULAR_COLOR_KEY);
            pib.description(color + user.getTranslation(BLOCK_LIMIT_SYNTAX_KEY,
                    TextVariables.NUMBER, String.valueOf(count),
                    LIMIT_PLACEHOLDER, String.valueOf(value)));
            result.add(pib.build());
        }
    }

    @Override
    public PanelItem getIcon() {
        Material icon = switch (env) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
        return new PanelItemBuilder().icon(icon).name(this.getName()).build();
    }

    @Override
    public String getName() {
        return user.getTranslation(world, "island.limits.panel.title-syntax",
                "[title]", user.getTranslation(world, "limits.panel-title"),
                "[env]", user.getTranslation(world, envKey()));
    }

    private String envKey() {
        return switch (env) {
            case NETHER -> "island.limits.panel.env-nether";
            case THE_END -> "island.limits.panel.env-end";
            default -> "island.limits.panel.env-overworld";
        };
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
        for (int i = 0; i < l.size(); i++) {
            sb.append(Util.prettifyText(l.get(i).toString()));
            if (i + 1 < l.size()) sb.append(", ");
            if ((i + 1) % 5 == 0) sb.append("\n");
        }
        return sb.toString();
    }
}

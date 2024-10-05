package world.bentobox.limits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class Settings {

    enum GeneralGroup {
        ANIMALS, MOBS
    }

    private final Map<GeneralGroup, Integer> general = new EnumMap<>(GeneralGroup.class);
    private final Map<EntityType, Integer> limits = new EnumMap<>(EntityType.class);
    private final Map<EntityType, List<EntityGroup>> groupLimits = new EnumMap<>(EntityType.class);
    private final List<String> gameModes;
    private final boolean asyncGolums;
    private static final List<EntityType> DISALLOWED = Arrays.asList(
            EntityType.TNT,
            EntityType.EVOKER_FANGS,
            EntityType.LLAMA_SPIT,
            EntityType.DRAGON_FIREBALL,
            EntityType.AREA_EFFECT_CLOUD,
            EntityType.END_CRYSTAL,
            EntityType.SMALL_FIREBALL,
            EntityType.FIREBALL,
            EntityType.EXPERIENCE_BOTTLE,
            EntityType.EXPERIENCE_ORB,
            EntityType.SHULKER_BULLET,
            EntityType.WITHER_SKULL,
            EntityType.TRIDENT,
            EntityType.ARROW,
            EntityType.SPECTRAL_ARROW,
            EntityType.SNOWBALL,
            EntityType.EGG,
            EntityType.LEASH_KNOT,
            EntityType.GIANT,
            EntityType.ENDER_PEARL,
            EntityType.ENDER_DRAGON,
            EntityType.ITEM_FRAME,
            EntityType.PAINTING);

    public Settings(Limits addon) {

        // GameModes
        gameModes = addon.getConfig().getStringList("gamemodes");

        ConfigurationSection el = addon.getConfig().getConfigurationSection("entitylimits");
        if (el != null) {
            for (String key : el.getKeys(false)) {
                if (key.equalsIgnoreCase("ANIMALS")) {
                    general.put(GeneralGroup.ANIMALS, el.getInt(key, 0));
                } else if (key.equalsIgnoreCase("MOBS")) {
                    general.put(GeneralGroup.MOBS, el.getInt(key, 0));
                } else {
                    EntityType type = getType(key);
                    if (type != null) {
                        if (DISALLOWED.contains(type)) {
                            addon.logError("Entity type: " + key + " is not supported - skipping...");
                        } else {
                            limits.put(type, el.getInt(key, 0));
                        }
                    } else {
                        addon.logError("Unknown entity type: " + key + " - skipping...");
                    }
                }
            }
        }
        // Async Golums
        asyncGolums = addon.getConfig().getBoolean("async-golums", true);

        addon.log("Entity limits:");
        limits.entrySet().stream().map(e -> "Limit " + e.getKey().toString() + " to " + e.getValue()).forEach(addon::log);

        //group limits
        el = addon.getConfig().getConfigurationSection("entitygrouplimits");
        if (el != null) {
            for (String name : el.getKeys(false)) {
                int limit = el.getInt(name + ".limit");
                String iconName = el.getString(name + ".icon", "BARRIER");
                Material icon = Material.BARRIER;
                try {
                    icon = Material.valueOf(iconName.toUpperCase(Locale.ENGLISH));
                } catch (Exception e) {
                    addon.logError("Invalid group icon name: " + iconName + ". Use a Bukkit Material.");
                    icon = Material.BARRIER;
                }
                Set<EntityType> entities = el.getStringList(name + ".entities").stream().map(s -> {
                    EntityType type = getType(s);
                    if (type != null) {
                        if (DISALLOWED.contains(type)) {
                            addon.logError("Entity type: " + s + " is not supported - skipping...");
                        } else {
                            return type;
                        }
                    } else {
                        addon.logError("Unknown entity type: " + s + " - skipping...");
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
                if (entities.isEmpty())
                    continue;
                EntityGroup group = new EntityGroup(name, entities, limit, icon);
                entities.forEach(e -> {
                    List<EntityGroup> groups = groupLimits.getOrDefault(e, new ArrayList<>());
                    groups.add(group);
                    groupLimits.put(e, groups);
                });
            }
        }

        addon.log("Entity group limits:");
        getGroupLimitDefinitions().stream().map(e -> "Limit " + e.getName() + " (" + e.getTypes().stream().map(Enum::name).collect(Collectors.joining(", ")) + ") to " + e.getLimit()).forEach(addon::log);
    }

    private EntityType getType(String key) {
        return Arrays.stream(EntityType.values()).filter(v -> v.name().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    /**
     * @return the entity limits
     */
    public Map<EntityType, Integer> getLimits() {
        return Collections.unmodifiableMap(limits);
    }

    /**
     * @return the group limits
     */
    public Map<EntityType, List<EntityGroup>> getGroupLimits() {
        return groupLimits;
    }

    /**
     * @return the group limit definitions
     */
    public List<EntityGroup> getGroupLimitDefinitions() {
        return groupLimits.values().stream().flatMap(Collection::stream).distinct().toList();
    }

    /**
     * @return the gameModes
     */
    public List<String> getGameModes() {
        return gameModes;
    }

    /**
     * @return the asyncGolums
     */
    public boolean isAsyncGolums() {
        return asyncGolums;
    }

    /**
     * @return the general coverage map
     */
    public Map<GeneralGroup, Integer> getGeneral() {
        return general;
    }
}

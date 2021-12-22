package world.bentobox.limits;

import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class Settings {

    private final Map<EntityType, Integer> limits = new EnumMap<>(EntityType.class);
    private final Map<EntityType, List<EntityGroup>> groupLimits = new EnumMap<>(EntityType.class);
    private final List<String> gameModes;
    private final boolean asyncGolums;
    private static final List<EntityType> DISALLOWED = Arrays.asList(
            EntityType.PRIMED_TNT,
            EntityType.EVOKER_FANGS,
            EntityType.LLAMA_SPIT,
            EntityType.DRAGON_FIREBALL,
            EntityType.AREA_EFFECT_CLOUD,
            EntityType.ENDER_SIGNAL,
            EntityType.SMALL_FIREBALL,
            EntityType.FIREBALL,
            EntityType.THROWN_EXP_BOTTLE,
            EntityType.EXPERIENCE_ORB,
            EntityType.SHULKER_BULLET,
            EntityType.WITHER_SKULL,
            EntityType.TRIDENT,
            EntityType.ARROW,
            EntityType.SPECTRAL_ARROW,
            EntityType.SNOWBALL,
            EntityType.EGG,
            EntityType.LEASH_HITCH,
            EntityType.GIANT,
            EntityType.ENDER_CRYSTAL,
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
        // Async Golums
        asyncGolums = addon.getConfig().getBoolean("async-golums", true);

        addon.log("Entity limits:");
        limits.entrySet().stream().map(e -> "Limit " + e.getKey().toString() + " to " + e.getValue()).forEach(addon::log);

        //group limits
        el = addon.getConfig().getConfigurationSection("entitygrouplimits");
        if (el != null) {
            for (String name : el.getKeys(false)) {
                int limit = el.getInt(name + ".limit");
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
                EntityGroup group = new EntityGroup(name, entities, limit);
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
        return groupLimits.values().stream().flatMap(Collection::stream).distinct().collect(Collectors.toList());
    }

    /**
     * @return the gameModes
     */
    public List<String> getGameModes() {
        return gameModes;
    }

    /**
     * A named class representing a group of entities and their limits
     *
     */
    public static class EntityGroup {
        private final String name;
        private final Set<EntityType> types;
        private final int limit;

        public EntityGroup(String name, Set<EntityType> types, int limit) {
            this.name = name;
            this.types = types;
            this.limit = limit;
        }

        public boolean contains(EntityType type) {
            return types.contains(type);
        }

        public String getName() {
            return name;
        }

        public Set<EntityType> getTypes() {
            return types;
        }

        public int getLimit() {
            return limit;
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 83 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final EntityGroup other = (EntityGroup) obj;
            return Objects.equals(this.name, other.name);
        }

        @Override
        public String toString()
        {
            return "EntityGroup{" + "name=" + name + ", types=" + types + ", limit=" + limit + '}';
        }
    }

    /**
     * @return the asyncGolums
     */
    public boolean isAsyncGolums() {
        return asyncGolums;
    }
}

package bentobox.addon.limits;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class Settings {

    private final Map<EntityType, Integer> limits = new EnumMap<>(EntityType.class);
    private final List<String> gameModes;
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
        addon.log("Entity limits:");
        limits.entrySet().stream().map(e -> "Limit " + e.getKey().toString() + " to " + e.getValue()).forEach(addon::log);
    }

    private EntityType getType(String key) {
        return Arrays.stream(EntityType.values()).filter(v -> v.name().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    /**
     * @return the limits
     */
    public Map<EntityType, Integer> getLimits() {
        return limits;
    }

    /**
     * @return the gameModes
     */
    public List<String> getGameModes() {
        return gameModes;
    }

}

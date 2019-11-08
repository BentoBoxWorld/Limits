package bentobox.addon.limits;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import bentobox.addon.limits.commands.LimitPanel;

public class Settings {

    private final Map<EntityType, Integer> limits = new EnumMap<>(EntityType.class);
    private final List<String> gameModes;

    public Settings(Limits addon) {

        // GameModes
        gameModes = addon.getConfig().getStringList("gamemodes");

        ConfigurationSection el = addon.getConfig().getConfigurationSection("entitylimits");
        if (el != null) {
            for (String key : el.getKeys(false)) {
                EntityType type = getType(key);
                if (type != null) {
                    if (!type.equals(EntityType.PAINTING) &&
                            !type.equals(EntityType.ITEM_FRAME) &&
                            (!type.isSpawnable() || (LimitPanel.E2M.containsKey(type) && LimitPanel.E2M.get(type) == null))) {
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

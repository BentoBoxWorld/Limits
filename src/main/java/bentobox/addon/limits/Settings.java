package bentobox.addon.limits;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class Settings {

    private Map<EntityType, Integer> limits = new HashMap<>();

    public Settings(Limits addon) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection("entitylimits");
        if (el != null) {
            for (String key : el.getKeys(false)) {
                EntityType type = getType(key);
                if (type != null) {
                    limits.put(type, el.getInt(key, 0));
                }
            }
        }
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

}

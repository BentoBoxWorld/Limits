package world.bentobox.limits.calculators;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class Results {
    public enum Result {
        IN_PROGRESS,
        AVAILABLE,
        TIMEOUT
    }

    /** Block counts per environment, populated by the chunk scan. */
    final Map<Environment, Multiset<NamespacedKey>> envBlockCount = new EnumMap<>(Environment.class);
    /** Entity counts per environment, populated by the entity scan. */
    final Map<Environment, Multiset<EntityType>> envEntityCount = new EnumMap<>(Environment.class);

    final Result state;

    public Results(Result state) {
        this.state = state;
    }

    public Results() {
        this.state = Result.AVAILABLE;
    }

    public Multiset<NamespacedKey> getBlockCount(Environment env) {
        return envBlockCount.computeIfAbsent(env, e -> HashMultiset.create());
    }

    public Multiset<EntityType> getEntityCount(Environment env) {
        return envEntityCount.computeIfAbsent(env, e -> HashMultiset.create());
    }

    public Map<Environment, Multiset<NamespacedKey>> getEnvBlockCount() {
        return envBlockCount;
    }

    public Map<Environment, Multiset<EntityType>> getEnvEntityCount() {
        return envEntityCount;
    }

    public Result getState() {
        return state;
    }
}

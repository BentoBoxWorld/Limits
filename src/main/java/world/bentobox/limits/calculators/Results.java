package world.bentobox.limits.calculators;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class Results {
    public enum Result {
        /**
         * A level calc is already in progress
         */
        IN_PROGRESS,
        /**
         * Results will be available
         */
        AVAILABLE,
        /**
         * Result if calculation timed out
         */
        TIMEOUT
    }
    final Multiset<Material> mdCount = HashMultiset.create();
    final Multiset<EntityType> entityCount = HashMultiset.create();

    final Result state;

    public Results(Result state) {
        this.state = state;
    }

    public Results() {
        this.state = Result.AVAILABLE;
    }
    /**
     * @return the mdCount
     */
    public Multiset<Material> getMdCount() {
        return mdCount;
    }

    /**
     * @return the state
     */
    public Result getState() {
        return state;
    }

    /**
     * @return the entityCount
     */
    public Multiset<EntityType> getEntityCount() {
        return entityCount;
    }

}
package world.bentobox.limits.calculators;

import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;

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
    // AtomicLong and AtomicInteger must be used because they are changed by multiple concurrent threads
    AtomicLong rawBlockCount = new AtomicLong(0);

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

}
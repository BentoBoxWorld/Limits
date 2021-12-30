package world.bentobox.limits.calculators;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.limits.Limits;
import world.bentobox.limits.calculators.Results.Result;

/**
 * A pipeliner that will process one island at a time
 * @author tastybento
 *
 */
public class Pipeliner {

    private static final int START_DURATION = 10; // 10 seconds
    private static final int CONCURRENT_COUNTS = 1;
    private final Queue<RecountCalculator> toProcessQueue;
    private final Map<RecountCalculator, Long> inProcessQueue;
    private final BukkitTask task;
    private final Limits addon;
    private long time;
    private long count;

    /**
     * Construct the pipeliner
     */
    public Pipeliner(Limits addon) {
        this.addon = addon;
        toProcessQueue = new ConcurrentLinkedQueue<>();
        inProcessQueue = new HashMap<>();
        // Loop continuously - check every tick if there is an island to scan
        task = Bukkit.getScheduler().runTaskTimer(BentoBox.getInstance(), () -> {
            if (!BentoBox.getInstance().isEnabled()) {
                cancel();
                return;
            }
            // Complete the current to Process queue first
            if (!inProcessQueue.isEmpty() || toProcessQueue.isEmpty()) return;
            for (int j = 0; j < CONCURRENT_COUNTS && !toProcessQueue.isEmpty(); j++) {
                RecountCalculator iD = toProcessQueue.poll();
                // Ignore deleted or unonwed islands
                if (!iD.getIsland().isDeleted() && !iD.getIsland().isUnowned()) {
                    inProcessQueue.put(iD, System.currentTimeMillis());
                    // Start the scanning of a island with the first chunk
                    scanIsland(iD);
                }
            }
        }, 1L, 10L);
    }

    private void cancel() {
        task.cancel();
    }

    /**
     * @return number of islands currently in the queue or in process
     */
    public int getIslandsInQueue() {
        return inProcessQueue.size() + toProcessQueue.size();
    }

    /**
     * Scans one chunk of an island and adds the results to a results object
     * @param iD
     */
    private void scanIsland(RecountCalculator iD) {
        if (iD.getIsland().isDeleted() || iD.getIsland().isUnowned() || task.isCancelled()) {
            // Island is deleted, so finish early with nothing
            inProcessQueue.remove(iD);
            iD.getR().complete(null);
            return;
        }
        iD.scanIsland(this);
    }


    /**
     * Adds an island to the scanning queue but only if the island is not already in the queue
     * @param island  - the island to scan
     * @return CompletableFuture of the results. Results will be null if the island is already in the queue
     */
    public CompletableFuture<Results> addIsland(Island island) {
        // Check if queue already contains island
        if (inProcessQueue.keySet().parallelStream().map(RecountCalculator::getIsland).anyMatch(island::equals)
                || toProcessQueue.parallelStream().map(RecountCalculator::getIsland).anyMatch(island::equals)) {
            return CompletableFuture.completedFuture(new Results(Result.IN_PROGRESS));
        }
        return addToQueue(island);
    }

    private CompletableFuture<Results> addToQueue(Island island) {
        CompletableFuture<Results> r = new CompletableFuture<>();
        toProcessQueue.add(new RecountCalculator(addon, island, r));
        count++;
        return r;
    }

    /**
     * Get the average time it takes to run a level check
     * @return the average time in seconds
     */
    public int getTime() {
        return time == 0 || count == 0 ? START_DURATION : (int)((double)time/count/1000);
    }

    /**
     * Submit how long a level check took
     * @param time the time to set
     */
    public void setTime(long time) {
        // Running average
        this.time += time;
    }

    /**
     * Stop the current queue.
     */
    public void stop() {
        addon.log("Stopping Level queue");
        task.cancel();
        this.inProcessQueue.clear();
        this.toProcessQueue.clear();
    }

    /**
     * @return the inProcessQueue
     */
    protected Map<RecountCalculator, Long> getInProcessQueue() {
        return inProcessQueue;
    }

    /**
     * @return the task
     */
    protected BukkitTask getTask() {
        return task;
    }




}

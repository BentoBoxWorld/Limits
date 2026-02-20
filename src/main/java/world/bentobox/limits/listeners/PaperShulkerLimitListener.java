package world.bentobox.limits.listeners;

import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.entity.ShulkerDuplicateEvent;
import world.bentobox.limits.Limits;

/**
 * Paper-specific listener to enforce entity limits during shulker duplication.
 * <p>
 * On Paper servers, shulker duplication fires {@code ShulkerDuplicateEvent} before
 * the original shulker teleports and before the new shulker is created. This means
 * the entity count is accurate at event time, unlike the standard
 * {@code CreatureSpawnEvent} which fires after the original shulker has already
 * teleported (potentially outside the island's bounding box), causing the count to
 * read N-1 instead of N and allowing duplication past the configured limit.
 * <p>
 * This class is only instantiated when the Paper API is detected at runtime.
 *
 * @author tastybento
 */
public class PaperShulkerLimitListener implements Listener {

    private final Limits addon;
    private final EntityLimitListener entityLimitListener;

    /**
     * @param addon               - limits addon
     * @param entityLimitListener - entity limit listener, used for shared limit-checking logic
     */
    public PaperShulkerLimitListener(Limits addon, EntityLimitListener entityLimitListener) {
        this.addon = addon;
        this.entityLimitListener = entityLimitListener;
    }

    /**
     * Cancel shulker duplication when the island's entity limit has been reached.
     * <p>
     * This event fires before the original shulker teleports, so the entity count
     * within the island bounding box is still accurate.
     *
     * @param event the ShulkerDuplicateEvent
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShulkerDuplicate(ShulkerDuplicateEvent event) {
        Shulker shulker = event.getEntity();
        if (!addon.inGameModeWorld(shulker.getWorld())) {
            return;
        }
        addon.getIslands().getIslandAt(shulker.getLocation())
                .filter(island -> !island.isSpawn())
                .ifPresent(island -> {
                    var res = entityLimitListener.atLimit(island, shulker);
                    if (res.hit()) {
                        event.setCancelled(true);
                    }
                });
    }
}

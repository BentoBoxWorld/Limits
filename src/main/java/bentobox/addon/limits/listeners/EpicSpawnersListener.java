package bentobox.addon.limits.listeners;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.songoda.epicspawners.api.events.SpawnerBreakEvent;

import bentobox.addon.limits.Limits;

/**
 * @author tastybento
 *
 */
public class EpicSpawnersListener implements Listener {

    Limits addon;

    /**
     * @param addon
     */
    public EpicSpawnersListener(Limits addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(SpawnerBreakEvent e) {
        Block b = e.getSpawner().getLocation().getBlock();
        addon.getBlockLimitListener().handleBreak(e, e.getPlayer(), b);
    }

}

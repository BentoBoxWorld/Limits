package world.bentobox.limits.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.CustomBlockPlaceEvent;
import world.bentobox.limits.Limits;

/**
 * Applies block limits to ItemsAdder custom blocks. Registered only when the
 * ItemsAdder plugin is present. Config keys use the ItemsAdder namespaced id, e.g.
 * {@code "iafestivities:christmas/christmas_tree/green_orb": 5} under blocklimits.
 */
public class ItemsAdderListener implements Listener {

    private final Limits addon;

    public ItemsAdderListener(Limits addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(CustomBlockPlaceEvent e) {
        CustomBlockHandler.place(addon, e, e.getPlayer(), e.getBlock(),
                CustomBlockHandler.toKey(e.getNamespacedID(), "itemsadder"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(CustomBlockBreakEvent e) {
        CustomBlockHandler.remove(addon, e.getBlock(),
                CustomBlockHandler.toKey(e.getNamespacedID(), "itemsadder"));
    }
}

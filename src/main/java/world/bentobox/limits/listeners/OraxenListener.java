package world.bentobox.limits.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockBreakEvent;
import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockPlaceEvent;
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockBreakEvent;
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockPlaceEvent;
import world.bentobox.limits.Limits;

/**
 * Applies block limits to Oraxen custom blocks (note-block and string-block
 * mechanics). Registered only when the Oraxen plugin is present. Config keys use the
 * {@code oraxen:} namespace, e.g. {@code "oraxen:caveblock": 10} under blocklimits.
 */
public class OraxenListener implements Listener {

    private static final String NAMESPACE = "oraxen";

    private final Limits addon;

    public OraxenListener(Limits addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNoteBlockPlace(OraxenNoteBlockPlaceEvent e) {
        CustomBlockHandler.place(addon, e, e.getPlayer(), e.getBlock(),
                CustomBlockHandler.toKey(e.getMechanic().getItemID(), NAMESPACE));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNoteBlockBreak(OraxenNoteBlockBreakEvent e) {
        CustomBlockHandler.remove(addon, e.getBlock(),
                CustomBlockHandler.toKey(e.getMechanic().getItemID(), NAMESPACE));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStringBlockPlace(OraxenStringBlockPlaceEvent e) {
        CustomBlockHandler.place(addon, e, e.getPlayer(), e.getBlock(),
                CustomBlockHandler.toKey(e.getMechanic().getItemID(), NAMESPACE));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStringBlockBreak(OraxenStringBlockBreakEvent e) {
        CustomBlockHandler.remove(addon, e.getBlock(),
                CustomBlockHandler.toKey(e.getMechanic().getItemID(), NAMESPACE));
    }
}

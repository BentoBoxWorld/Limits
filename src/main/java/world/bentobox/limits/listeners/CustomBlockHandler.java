package world.bentobox.limits.listeners;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.Limits;

/**
 * Shared plumbing for custom-block plugin listeners (ItemsAdder, Oraxen). Runs custom
 * block ids through the same counting and limit machinery as vanilla blocks via
 * {@link BlockLimitsListener#processKey}.
 */
final class CustomBlockHandler {

    private CustomBlockHandler() {
    }

    /**
     * Limit-check and count a custom block placement; cancels the event and notifies
     * the player when the limit is hit.
     */
    static void place(Limits addon, Cancellable event, Player player, Block block, NamespacedKey key) {
        if (key == null) {
            return;
        }
        int limit = addon.getBlockLimitListener().processKey(block.getWorld(), block.getLocation(), key, true);
        if (limit > -1) {
            event.setCancelled(true);
            if (player != null) {
                User.getInstance(player).notify("block-limits.hit-limit",
                        "[material]", Util.prettifyText(key.getKey()),
                        TextVariables.NUMBER, String.valueOf(limit));
            }
        }
    }

    /**
     * Decrement the count for a removed custom block.
     */
    static void remove(Limits addon, Block block, NamespacedKey key) {
        if (key != null) {
            addon.getBlockLimitListener().processKey(block.getWorld(), block.getLocation(), key, false);
        }
    }

    /**
     * Parse a custom block id into a namespaced key. ItemsAdder ids already carry a
     * namespace; plain ids get the supplied default namespace (e.g. "oraxen").
     */
    static NamespacedKey toKey(String id, String defaultNamespace) {
        if (id == null) {
            return null;
        }
        String full = id.contains(":") ? id : defaultNamespace + ":" + id;
        return NamespacedKey.fromString(full.toLowerCase(Locale.ROOT));
    }
}

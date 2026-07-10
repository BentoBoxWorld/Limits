package world.bentobox.limits.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class CustomBlockHandlerTest {

    @Test
    void testToKeyNamespacedIdPassesThrough() {
        NamespacedKey key = CustomBlockHandler.toKey("iafestivities:christmas/christmas_tree/green_orb",
                "itemsadder");
        assertEquals("iafestivities", key.getNamespace());
        assertEquals("christmas/christmas_tree/green_orb", key.getKey());
    }

    @Test
    void testToKeyPlainIdGetsDefaultNamespace() {
        NamespacedKey key = CustomBlockHandler.toKey("caveblock", "oraxen");
        assertEquals("oraxen", key.getNamespace());
        assertEquals("caveblock", key.getKey());
    }

    @Test
    void testToKeyUppercaseIsLowercased() {
        NamespacedKey key = CustomBlockHandler.toKey("CaveBlock", "oraxen");
        assertEquals("caveblock", key.getKey());
    }

    @Test
    void testToKeyInvalidReturnsNull() {
        assertNull(CustomBlockHandler.toKey("bad key with spaces", "oraxen"));
        assertNull(CustomBlockHandler.toKey(null, "oraxen"));
    }
}

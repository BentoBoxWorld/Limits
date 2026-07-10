package world.bentobox.limits;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

/**
 * Resolves the display name of a limited material or entity for a user.
 *
 * <p>Locale files may provide manual translations under
 * {@code island.limits.materials.<material>} and {@code island.limits.entities.<entity>}
 * (lowercase Bukkit names). Any key without a translation falls back to the automatic
 * prettified English name, so translations are entirely optional.
 */
public final class DisplayNames {

    private DisplayNames() {
    }

    /**
     * @param user the user whose locale is used
     * @param key the material key, e.g. {@code minecraft:hopper}
     * @return translated material name, or the prettified key if no translation exists
     */
    public static String material(User user, NamespacedKey key) {
        String translated = user.getTranslationOrNothing("island.limits.materials." + key.getKey());
        return translated == null || translated.isEmpty() ? Util.prettifyText(key.getKey()) : translated;
    }

    /**
     * @param user the user whose locale is used
     * @param type the entity type
     * @return translated entity name, or the prettified type name if no translation exists
     */
    public static String entity(User user, EntityType type) {
        String translated = user
                .getTranslationOrNothing("island.limits.entities." + type.toString().toLowerCase(Locale.ROOT));
        return translated == null || translated.isEmpty() ? Util.prettifyText(type.toString()) : translated;
    }
}

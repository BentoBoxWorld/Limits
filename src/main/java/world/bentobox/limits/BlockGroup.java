package world.bentobox.limits;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * A named group of block materials sharing a single limit. The counts of every
 * member material are summed and checked against the group limit.
 */
public class BlockGroup {
    private final String name;
    private final Set<NamespacedKey> keys;
    private final int limit;
    private final Material icon;

    public BlockGroup(String name, Set<NamespacedKey> keys, int limit, Material icon) {
        this.name = name;
        this.keys = keys;
        this.limit = limit;
        this.icon = icon;
    }

    public boolean contains(NamespacedKey key) {
        return keys.contains(key);
    }

    public String getName() {
        return name;
    }

    public Set<NamespacedKey> getKeys() {
        return keys;
    }

    public int getLimit() {
        return limit;
    }

    public Material getIcon() {
        return icon;
    }

    @Override
    public int hashCode() {
        return 83 * 7 + Objects.hashCode(this.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        return Objects.equals(this.name, ((BlockGroup) obj).name);
    }
}

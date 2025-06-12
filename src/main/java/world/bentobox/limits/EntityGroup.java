package world.bentobox.limits;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * A named class representing a group of entities and their limits
 *
 */
public class EntityGroup {
    private final String name;
    private final Set<EntityType> types;
    private final int limit;
    private final Material icon;

    public EntityGroup(String name, Set<EntityType> types, int limit, Material icon) {
        this.name = name;
        this.types = types;
        this.limit = limit;
        this.icon = icon;
    }

    public boolean contains(EntityType type) {
        return types.contains(type);
    }

    public String getName() {
        return name;
    }

    public Set<EntityType> getTypes() {
        return types;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final EntityGroup other = (EntityGroup) obj;
        return Objects.equals(this.name, other.name);
    }

    /**
     * @return the icon
     */
    public Material getIcon() {
        return Objects.requireNonNullElse(icon, Material.BARRIER);
    }

    @Override
    public String toString() {
        return "EntityGroup [name=" + name + ", types=" + types + ", limit=" + limit + ", icon=" + icon + "]";
    }
}
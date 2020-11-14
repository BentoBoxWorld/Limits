package world.bentobox.limits.events;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.limits.Settings.EntityGroup;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Fired when a player joins the server and for each perm-based limit setting.
 * If cancelled, no limit settings will be made.
 * Settings can be adjusted and will be used.
 * @author tastybento
 *
 */
public class LimitsPermCheckEvent extends LimitsJoinPermCheckEvent {

    private @Nullable EntityGroup entityGroup;
    private @Nullable EntityType entityType;
    private @Nullable Material material;
    private int value;

    /**
     * Fired when a player joins the server and for each perm-based limit setting.
     * If cancelled, no limit settings will be made.
     * Settings can be adjusted and will be used.
     * @param player - player joining
     * @param islandId - the unique island id.
     * @param ibc - IslandBlockCount object for this island
     * @param material - material being limited, or null
     * @param entityType - entity type being limited, or null
     * @param entgroup - entity group being limited, or null
     * @param value - numeric limit given by the perm
     */
    public LimitsPermCheckEvent(@NonNull Player player,
            @NonNull String islandId,
            @Nullable IslandBlockCount ibc,
            @Nullable EntityGroup entgroup,
            @Nullable EntityType entityType,
            @Nullable Material material,
            int value) {
        super(player, islandId, ibc);
        this.entityGroup = entgroup;
        this.entityType = entityType;
        this.material = material;
        this.value = value;
    }

    /**
     * @return the entityGroup
     */
    public EntityGroup getEntityGroup() {
        return entityGroup;
    }


    /**
     * @param entityGroup the entityGroup to set
     */
    public void setEntityGroup(EntityGroup entityGroup) {
        this.entityGroup = entityGroup;
    }


    /**
     * @return the entityType
     */
    public EntityType getEntityType() {
        return entityType;
    }


    /**
     * @param entityType the entityType to set
     */
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }


    /**
     * @return the material
     */
    public Material getMaterial() {
        return material;
    }


    /**
     * @param material the material to set
     */
    public void setMaterial(Material material) {
        this.material = material;
    }


    /**
     * @return the value
     */
    public int getValue() {
        return value;
    }


    /**
     * @param value the value to set
     */
    public void setValue(int value) {
        this.value = value;
    }


}

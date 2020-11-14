package world.bentobox.limits.events;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.events.BentoBoxEvent;
import world.bentobox.limits.Settings.EntityGroup;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Fired when a player joins the server and before limit settings for their island are changed based
 * on the player's permissions. If cancelled, no limit settings will be made.
 * @author tastybento
 *
 */
public class LimitsPermCheckEvent extends BentoBoxEvent implements Cancellable {

    private final Player player;
    private final String islandId;
    private IslandBlockCount ibc;
    private boolean ignorePerms;
    private boolean cancel;
    private @Nullable EntityGroup entityGroup;
    private @Nullable EntityType entityType;
    private @Nullable Material material;
    private int value;

    /**
     * Fired when a player joins the server and before limit settings for their island are changed based
     * on the player's permissions. If cancelled, no limit settings will be made.
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
        super();
        this.player = player;
        this.islandId = islandId;
        this.ibc = ibc;
        this.entityGroup = entgroup;
        this.entityType = entityType;
        this.material = material;
        this.value = value;
    }


    /**
     * Get the player joining
     * @return the player
     */
    @NonNull
    public Player getPlayer() {
        return player;
    }


    /**
     * Get the unique island id. Use the islands manager to obtain the island
     * @return the islandId
     */
    @NonNull
    public String getIslandId() {
        return islandId;
    }


    /**
     * Get the island block count
     * @return the ibc
     */
    @Nullable
    public IslandBlockCount getIbc() {
        return ibc;
    }


    /**
     * Set the island block count to a specific setting
     * @param ibc the ibc to set
     */
    public void setIbc(@Nullable IslandBlockCount ibc) {
        this.ibc = ibc;
    }


    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;

    }


    /**
     * Check if player's perms should be considered or not
     * @return the ignorePerms
     */
    public boolean isIgnorePerms() {
        return ignorePerms;
    }


    /**
     * Ignore player's perms. This differs to canceling the event in that the IslandBlockCount will be used if given via
     * {@link setIbc(IslandBlockCount ibc)}
     * @param ignorePerms the ignorePerms to set
     */
    public void setIgnorePerms(boolean ignorePerms) {
        this.ignorePerms = ignorePerms;
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

package world.bentobox.limits.objects;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * @author tastybento
 *
 */
@Table(name = "IslandBlockCount")
public class IslandBlockCount implements DataObject {

    @Expose
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockCounts = new HashMap<>();

    /**
     * Permission based limits
     */
    @Expose
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockLimits = new HashMap<>();

    @Expose
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockLimitsOffset = new HashMap<>();

    private boolean changed;

    @Expose
    private Map<String, Integer> entityGroupLimits = new HashMap<>();
    @Expose
    private Map<String, Integer> entityGroupLimitsOffset = new HashMap<>();
    @Expose
    private Map<EntityType, Integer> entityLimits = new EnumMap<>(EntityType.class);
    @Expose
    private Map<EntityType, Integer> entityLimitsOffset = new EnumMap<>(EntityType.class);
    @Expose
    private String gameMode;
    @Expose
    private String uniqueId;

    /**
     * Create an island block count object
     * 
     * @param islandId - unique Island ID string
     * @param gameMode - Game mode name from gm.getDescription().getName()
     */
    public IslandBlockCount(String islandId, String gameMode) {
        this.uniqueId = islandId;
        this.gameMode = gameMode;
        setChanged();
    }

    /**
     * Add a material to the count
     * 
     * @param material - material
     */
    public void add(NamespacedKey material) {
         getBlockCounts().merge(material, 1, Integer::sum);
        setChanged();
    }

    /**
     * Clear all island-specific entity group limits
     */
    public void clearEntityGroupLimits() {
        entityGroupLimits.clear();
        setChanged();
    }

    /**
     * Clear all island-specific entity type limits
     */
    public void clearEntityLimits() {
        entityLimits.clear();
        setChanged();
    }

    /**
     * Get the block count for this material for this island
     * 
     * @param m - material
     * @return count
     */
    public Integer getBlockCount(NamespacedKey m) {
        return getBlockCounts().getOrDefault(m, 0);
    }

    /**
     * @return the blockCount
     */
    public Map<NamespacedKey, Integer> getBlockCounts() {
        if (blockCounts == null) {
            blockCounts = new HashMap<>();
        }
        return blockCounts;
    }

    /**
     * Get the block limit for this material for this island
     * 
     * @param m - material
     * @return limit or -1 for unlimited
     */
    public int getBlockLimit(NamespacedKey m) {
         return getBlockLimits().getOrDefault(m, -1);
    }

    /**
     * Get the block offset for this material for this island
     * 
     * @param m - material
     * @return offset
     */
    public int getBlockLimitOffset(NamespacedKey m) {
        return getBlockLimitsOffset().getOrDefault(m, 0);
    }

    /**
     * @return the blockLimits
     */
    public Map<NamespacedKey, Integer> getBlockLimits() {
        if (blockLimits == null) {
            blockLimits = new HashMap<>();
        }
        return blockLimits;
    }

    /**
     * @return the blockLimitsOffset
     */
    public Map<NamespacedKey, Integer> getBlockLimitsOffset() {
        if (blockLimitsOffset == null) {
            blockLimitsOffset = new HashMap<>();
        }
        return blockLimitsOffset;
    }

    /**
     * Get the limit for an entity group
     * 
     * @param name - entity group
     * @return limit or -1 for unlimited
     */
    public int getEntityGroupLimit(String name) {
        return getEntityGroupLimits().getOrDefault(name, -1);
    }

    /**
     * Get the offset for an entity group
     * 
     * @param name - entity group
     * @return offset
     */
    public int getEntityGroupLimitOffset(String name) {
        return getEntityGroupLimitsOffset().getOrDefault(name, 0);
    }

    /**
     * @return the entityGroupLimits
     */
    public Map<String, Integer> getEntityGroupLimits() {
        return Objects.requireNonNullElse(entityGroupLimits, new HashMap<>());
    }

    /**
     * @return the entityGroupLimitsOffset
     */
    public Map<String, Integer> getEntityGroupLimitsOffset() {
        if (entityGroupLimitsOffset == null) {
            entityGroupLimitsOffset = new HashMap<>();
        }
        return entityGroupLimitsOffset;
    }

    /**
     * Get the limit for an entity type
     * 
     * @param t - entity type
     * @return limit or -1 for unlimited
     */
    public int getEntityLimit(EntityType t) {
        return getEntityLimits().getOrDefault(t, -1);
    }

    /**
     * Get the limit offset for an entity type
     * 
     * @param t - entity type
     * @return offset
     */
    public int getEntityLimitOffset(EntityType t) {
        return getEntityLimitsOffset().getOrDefault(t, 0);
    }

    /**
     * @return the entityLimits
     */
    public Map<EntityType, Integer> getEntityLimits() {
        return Objects.requireNonNullElse(entityLimits, new EnumMap<>(EntityType.class));
    }

    /**
     * @return the entityLimitsOffset
     */
    public Map<EntityType, Integer> getEntityLimitsOffset() {
        if (entityLimitsOffset == null) {
            entityLimitsOffset = new EnumMap<>(EntityType.class);
        }
        return entityLimitsOffset;
    }

    /**
     * @return the gameMode
     */
    public String getGameMode() {
        return gameMode;
    }

    /*
     * (non-Javadoc)
     * 
     * @see world.bentobox.bentobox.database.objects.DataObject#getUniqueId()
     */
    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Check if no more of this material can be added to this island
     * 
     * @param m - material
     * @return true if no more material can be added
     */
    public boolean isAtLimit(NamespacedKey m) {
        return getBlockLimits().containsKey(m)
                && getBlockCount(m) >= getBlockLimit(m) + this.getBlockLimitOffset(m);
    }

    /**
     * Check if this material is at or over a limit
     * 
     * @param material - block material
     * @param limit    - limit to check
     * @return true if count is >= limit
     */
    public boolean isAtLimit(NamespacedKey material, int limit) {
        return getBlockCount(material) >= limit + this.getBlockLimitOffset(material);
    }

    public boolean isBlockLimited(NamespacedKey m) {
        return getBlockLimits().containsKey(m);
    }

    /**
     * @return the changed
     */
    public boolean isChanged() {
        return changed;
    }

    public boolean isGameMode(String gameMode) {
        return getGameMode().equals(gameMode);
    }

    /**
     * Remove a material from the count
     * 
     * @param material - material
     */
    public void remove(NamespacedKey material) {
        // Check if this material is currently tracked
        boolean existed = getBlockCounts().containsKey(material);
        // Otherwise, decrement/remove individual block
        getBlockCounts().computeIfPresent(material, (m, count) -> {
            int newCount = count - 1;
            return (newCount > 0) ? newCount : null; // null removes the entry
        });
        // Mark this object as changed only if a modification actually occurred
        if (existed) {
            setChanged();
        }
    }

    /**
     * @param blockCounts the blockCount to set
     */
    public void setBlockCounts(Map<NamespacedKey, Integer> blockCounts) {
        this.blockCounts = blockCounts;
        setChanged();
    }

    /**
     * Set the block limit for this material for this island
     * 
     * @param m     - material
     * @param limit - maximum number allowed
     */
    public void setBlockLimit(NamespacedKey m, int limit) {
        getBlockLimits().put(m, limit);
        setChanged();
    }

    /**
     * @param blockLimits the blockLimits to set
     */
    public void setBlockLimits(Map<NamespacedKey, Integer> blockLimits) {
        this.blockLimits = blockLimits;
        setChanged();
    }

    /**
     * Set an offset to a block limit. This will increase/decrease the value of the
     * limit.
     * 
     * @param m                 material
     * @param blockLimitsOffset the blockLimitsOffset to set
     */
    public void setBlockLimitsOffset(NamespacedKey m, Integer blockLimitsOffset) {
        getBlockLimitsOffset().put(m, blockLimitsOffset);
    }

    /**
     * Mark changed
     */
    public void setChanged() {
        this.changed = true;
    }

    /**
     * @param changed the changed to set
     */
    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    /**
     * Set an island-specific entity group limit
     * 
     * @param name  - entity group
     * @param limit - limit
     */
    public void setEntityGroupLimit(String name, int limit) {
        getEntityGroupLimits().put(name, limit);
        setChanged();
    }

    /**
     * @param entityGroupLimits the entityGroupLimits to set
     */
    public void setEntityGroupLimits(Map<String, Integer> entityGroupLimits) {
        this.entityGroupLimits = entityGroupLimits;
        setChanged();
    }

    /**
     * Set an offset to an entity group limit. This will increase/decrease the value
     * of the limit.
     * 
     * @param name                    group name
     * @param entityGroupLimitsOffset the entityGroupLimitsOffset to set
     */
    public void setEntityGroupLimitsOffset(String name, Integer entityGroupLimitsOffset) {
        getEntityGroupLimitsOffset().put(name, entityGroupLimitsOffset);
    }

    /**
     * Set an island-specific entity type limit
     * 
     * @param t     - entity type
     * @param limit - limit
     */
    public void setEntityLimit(EntityType t, int limit) {
        getEntityLimits().put(t, limit);
        setChanged();
    }

    /**
     * @param entityLimits the entityLimits to set
     */
    public void setEntityLimits(Map<EntityType, Integer> entityLimits) {
        this.entityLimits = entityLimits;
        setChanged();
    }

    /**
     * Set an offset to an entity limit. This will increase/decrease the value of
     * the limit.
     * 
     * @param type               Entity Type
     * @param entityLimitsOffset the entityLimitsOffset to set
     */
    public void setEntityLimitsOffset(EntityType type, Integer entityLimitsOffset) {
        this.getEntityLimitsOffset().put(type, entityLimitsOffset);
    }

    /**
     * @param gameMode the gameMode to set
     */
    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
        setChanged();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * world.bentobox.bentobox.database.objects.DataObject#setUniqueId(java.lang.
     * String)
     */
    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
        setChanged();
    }

 }

package world.bentobox.limits.objects;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * @author tastybento
 *
 */
@Table(name = "IslandBlockCount")
public class IslandBlockCount implements DataObject {

    @Expose
    private String uniqueId = "";

    @Expose
    private String gameMode = "";

    @Expose
    private Map<Material, Integer> blockCount = new EnumMap<>(Material.class);

    private boolean changed;
    
    /**
     * Permission based limits
     */
    @Expose
    private Map<Material, Integer> blockLimits = new EnumMap<>(Material.class);
    @Expose
    private Map<EntityType, Integer> entityLimits = new EnumMap<>(EntityType.class);
    @Expose
    private Map<String, Integer> entityGroupLimits = new HashMap<>();

    // Required for YAML database
    public IslandBlockCount() {}

    public IslandBlockCount(String uniqueId2, String gameMode2) {
        this.uniqueId = uniqueId2;
        this.gameMode = gameMode2;
        setChanged();
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.database.objects.DataObject#getUniqueId()
     */
    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    /* (non-Javadoc)
     * @see world.bentobox.bentobox.database.objects.DataObject#setUniqueId(java.lang.String)
     */
    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
        setChanged();
    }

    /**
     * @return the blockCount
     */
    public Map<Material, Integer> getBlockCount() {
        return blockCount;
    }

    /**
     * @param blockCount the blockCount to set
     */
    public void setBlockCount(Map<Material, Integer> blockCount) {
        this.blockCount = blockCount;
        setChanged();
    }

    /**
     * Add a material to the count
     * @param material - material
     */
    public void add(Material material) {
        blockCount.merge(material, 1, Integer::sum);
        setChanged();
    }

    /**
     * Remove a material from the count
     * @param material - material
     */
    public void remove(Material material) {
        blockCount.put(material, blockCount.getOrDefault(material, 0) - 1);
        blockCount.values().removeIf(v -> v <= 0);
        setChanged();
    }

    /**
     * Check if this material is at or over a limit
     * @param material - block material
     * @param limit - limit to check
     * @return true if count is >= limit
     */
    public boolean isAtLimit(Material material, int limit) {
        return blockCount.getOrDefault(material, 0) >= limit;
    }

    /**
     * Check if no more of this material can be added to this island
     * @param m - material
     * @return true if no more material can be added
     */
    public boolean isAtLimit(Material m) {
        // Check island limits first
        return blockLimits.containsKey(m) && blockCount.getOrDefault(m, 0) >= blockLimits.get(m);
    }

    public boolean isBlockLimited(Material m) {
        return blockLimits.containsKey(m);
    }

    /**
     * @return the blockLimits
     */
    public Map<Material, Integer> getBlockLimits() {
        return blockLimits;
    }

    /**
     * @param blockLimits the blockLimits to set
     */
    public void setBlockLimits(Map<Material, Integer> blockLimits) {
        this.blockLimits = blockLimits;
        setChanged();
    }

    /**
     * Get the block limit for this material for this island
     * @param m - material
     * @return limit or -1 for unlimited
     */
    public Integer getBlockLimit(Material m) {
        return blockLimits.getOrDefault(m, -1);
    }

    /**
     * Set the block limit for this material for this island
     * @param m - material
     * @param limit - maximum number allowed
     */
    public void setBlockLimit(Material m, int limit) {
        blockLimits.put(m, limit);
        setChanged();
    }

    /**
     * @return the gameMode
     */
    public String getGameMode() {
        return gameMode;
    }

    public boolean isGameMode(String gameMode) {
        return this.gameMode.equals(gameMode);
    }

    /**
     * @param gameMode the gameMode to set
     */
    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
        setChanged();
    }

    /**
     * @return the entityLimits
     */
    public Map<EntityType, Integer> getEntityLimits() {
        return entityLimits;
    }

    /**
     * @param entityLimits the entityLimits to set
     */
    public void setEntityLimits(Map<EntityType, Integer> entityLimits) {
        this.entityLimits = entityLimits;
        setChanged();
    }

    /**
     * Set an island-specific entity type limit
     * @param t - entity type
     * @param limit - limit
     */
    public void setEntityLimit(EntityType t, int limit) {
        entityLimits.put(t, limit);
        setChanged();
    }

    /**
     * Get the limit for an entity type
     * @param t - entity type
     * @return limit or -1 for unlimited
     */
    public int getEntityLimit(EntityType t) {
        return entityLimits.getOrDefault(t, -1);
    }

    /**
     * Clear all island-specific entity type limits
     */
    public void clearEntityLimits() {
        entityLimits.clear();
        setChanged();
    }
    
    /**
     * @return the entityGroupLimits
     */
    public Map<String, Integer> getEntityGroupLimits() {
        return entityGroupLimits;
    }

    /**
     * @param entityGroupLimits the entityGroupLimits to set
     */
    public void setEntityGroupLimits(Map<String, Integer> entityGroupLimits) {
        this.entityGroupLimits = entityGroupLimits;
        setChanged();
    }
    
    /**
     * Set an island-specific entity group limit
     * @param name - entity group
     * @param limit - limit
     */
    public void setEntityGroupLimit(String name, int limit) {
        entityGroupLimits.put(name, limit);
        setChanged();
    }
    
    /**
     * Get the limit for an entity group
     * @param name - entity group
     * @return limit or -1 for unlimited
     */
    public int getEntityGroupLimit(String name) {
        return entityGroupLimits.getOrDefault(name, -1);
    }
    
    /**
     * Clear all island-specific entity group limits
     */
    public void clearEntityGroupLimits() {
        entityGroupLimits.clear();
        setChanged();
    }

    /**
     * @return the changed
     */
    public boolean isChanged() {
        return changed;
    }

    /**
     * @param changed the changed to set
     */
    public void setChanged(boolean changed) {
        this.changed = changed;
    }
    
    /**
     * Mark changed
     */
    public void setChanged() {
        this.changed = true;
    }
}

/**
 *
 */
package bentobox.addon.limits.objects;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;

/**
 * @author tastybento
 *
 */
public class IslandBlockCount implements DataObject {

    @Expose
    private String uniqueId = "";

    @Expose
    private String gameMode = "";

    @Expose
    private Map<Material, Integer> blockCount = new HashMap<>();

    @Expose
    private Map<Material, Integer> blockLimits = new HashMap<>();

    // Required for YAML database
    public IslandBlockCount() {}

    public IslandBlockCount(String uniqueId2, String gameMode2) {
        this.uniqueId = uniqueId2;
        this.gameMode = gameMode2;
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
    }

    /**
     * Add a material to the count
     * @param material - material
     */
    public void add(Material material) {
        blockCount.merge(material, 1, Integer::sum);
    }

    /**
     * Remove a material from the count
     * @param material - material
     */
    public void remove(Material material) {
        blockCount.put(material, blockCount.getOrDefault(material, 0) - 1);
        blockCount.values().removeIf(v -> v <= 0);
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
        return blockLimits.containsKey(m) ? blockCount.getOrDefault(m, 0) >= blockLimits.get(m) : false;
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
    }
}

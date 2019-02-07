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
    private Map<Material, Integer> blockCount = new HashMap<>();

    public IslandBlockCount(String uniqueId2) {
        this.uniqueId = uniqueId2;
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
}

package world.bentobox.limits.objects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.annotations.Expose;

import world.bentobox.bentobox.database.objects.DataObject;

/**
 * @author tastybento
 *
 */
public class EntityLimitsDO implements DataObject {

    @Expose
    private String uniqueId = "";
    @Expose
    private Map<UUID, String> spawnLoc = new HashMap<>();

    public EntityLimitsDO() {}

    public EntityLimitsDO(String uniqueId) {
        this.uniqueId = uniqueId;
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
     * @return the spawnLoc
     */
    public Map<UUID, String> getSpawnLoc() {
        return spawnLoc;
    }

    /**
     * @param spawnLoc the spawnLoc to set
     */
    public void setSpawnLoc(Map<UUID, String> spawnLoc) {
        this.spawnLoc = spawnLoc;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((uniqueId == null) ? 0 : uniqueId.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof EntityLimitsDO)) {
            return false;
        }
        EntityLimitsDO other = (EntityLimitsDO) obj;
        if (uniqueId == null) {
            return other.uniqueId == null;
        } else return uniqueId.equals(other.uniqueId);
    }


}

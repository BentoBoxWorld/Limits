package world.bentobox.limits.objects;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

/**
 * Per-island, per-environment block and entity tracking.
 *
 * <p>Each map is keyed by {@link Environment} so overworld, nether, and end have
 * independent counts, limits and offsets. Pre-1.x data stored a single map per
 * field (no environment); on first load that legacy data is migrated into the
 * {@link Environment#NORMAL} slot.
 *
 * @author tastybento
 */
@Table(name = "IslandBlockCount")
public class IslandBlockCount implements DataObject {

    /* New env-keyed primary storage. */

    @Expose
    @JsonAdapter(EnvNamespacedKeyMapAdapter.class)
    private Map<Environment, Map<NamespacedKey, Integer>> envBlockCounts = new EnumMap<>(Environment.class);

    @Expose
    @JsonAdapter(EnvNamespacedKeyMapAdapter.class)
    private Map<Environment, Map<NamespacedKey, Integer>> envBlockLimits = new EnumMap<>(Environment.class);

    @Expose
    @JsonAdapter(EnvNamespacedKeyMapAdapter.class)
    private Map<Environment, Map<NamespacedKey, Integer>> envBlockLimitsOffset = new EnumMap<>(Environment.class);

    @Expose
    private Map<Environment, Map<EntityType, Integer>> envEntityCounts = new EnumMap<>(Environment.class);

    @Expose
    private Map<Environment, Map<EntityType, Integer>> envEntityLimits = new EnumMap<>(Environment.class);

    @Expose
    private Map<Environment, Map<EntityType, Integer>> envEntityLimitsOffset = new EnumMap<>(Environment.class);

    @Expose
    private Map<Environment, Map<String, Integer>> envEntityGroupLimits = new EnumMap<>(Environment.class);

    @Expose
    private Map<Environment, Map<String, Integer>> envEntityGroupLimitsOffset = new EnumMap<>(Environment.class);

    /* Legacy fields kept to deserialize pre-env data; never written back. */

    @Expose(serialize = false)
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockCounts;

    @Expose(serialize = false)
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockLimits;

    @Expose(serialize = false)
    @JsonAdapter(NamespacedKeyMapAdapter.class)
    private Map<NamespacedKey, Integer> blockLimitsOffset;

    @Expose(serialize = false)
    private Map<EntityType, Integer> entityLimits;

    @Expose(serialize = false)
    private Map<EntityType, Integer> entityLimitsOffset;

    @Expose(serialize = false)
    private Map<String, Integer> entityGroupLimits;

    @Expose(serialize = false)
    private Map<String, Integer> entityGroupLimitsOffset;

    @Expose
    private String gameMode;

    @Expose
    private String uniqueId;

    private boolean changed;
    private boolean migrated;

    /**
     * Required by Gson.
     */
    public IslandBlockCount() {
    }

    /**
     * @param islandId unique island ID
     * @param gameMode game mode addon name
     */
    public IslandBlockCount(String islandId, String gameMode) {
        this.uniqueId = islandId;
        this.gameMode = gameMode;
        setChanged();
    }

    /* =========================================================================
     * Migration
     * ========================================================================= */

    /**
     * Move any legacy single-map data into Environment.NORMAL. Idempotent.
     */
    private void migrateIfNeeded() {
        if (migrated) return;
        migrated = true;
        moveLegacy(blockCounts, envBlockCounts);
        moveLegacy(blockLimits, envBlockLimits);
        moveLegacy(blockLimitsOffset, envBlockLimitsOffset);
        moveLegacy(entityLimits, envEntityLimits);
        moveLegacy(entityLimitsOffset, envEntityLimitsOffset);
        moveLegacy(entityGroupLimits, envEntityGroupLimits);
        moveLegacy(entityGroupLimitsOffset, envEntityGroupLimitsOffset);
        blockCounts = null;
        blockLimits = null;
        blockLimitsOffset = null;
        entityLimits = null;
        entityLimitsOffset = null;
        entityGroupLimits = null;
        entityGroupLimitsOffset = null;
    }

    private static <K> void moveLegacy(Map<K, Integer> legacy, Map<Environment, Map<K, Integer>> target) {
        if (legacy == null || legacy.isEmpty()) return;
        target.computeIfAbsent(Environment.NORMAL, e -> newInner(legacy)).putAll(legacy);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <K> Map<K, Integer> newInner(Map<K, Integer> sample) {
        Object first = sample.keySet().iterator().next();
        if (first instanceof EntityType) {
            return (Map<K, Integer>) (Map) new EnumMap<EntityType, Integer>(EntityType.class);
        }
        return new HashMap<>();
    }

    /* =========================================================================
     * Block counts
     * ========================================================================= */

    public Map<Environment, Map<NamespacedKey, Integer>> getAllBlockCounts() {
        migrateIfNeeded();
        if (envBlockCounts == null) envBlockCounts = new EnumMap<>(Environment.class);
        return envBlockCounts;
    }

    public Map<NamespacedKey, Integer> getBlockCounts(Environment env) {
        return getAllBlockCounts().computeIfAbsent(env, e -> new HashMap<>());
    }

    public int getBlockCount(Environment env, NamespacedKey key) {
        return getBlockCounts(env).getOrDefault(key, 0);
    }

    public int getBlockCount(NamespacedKey key) {
        int total = 0;
        for (Map<NamespacedKey, Integer> m : getAllBlockCounts().values()) {
            total += m.getOrDefault(key, 0);
        }
        return total;
    }

    public void add(Environment env, NamespacedKey material) {
        getBlockCounts(env).merge(material, 1, Integer::sum);
        setChanged();
    }

    public void remove(Environment env, NamespacedKey material) {
        Map<NamespacedKey, Integer> m = getBlockCounts(env);
        if (m.containsKey(material)) {
            m.computeIfPresent(material, (k, count) -> count - 1 > 0 ? count - 1 : null);
            setChanged();
        }
    }

    public void clearAllBlockCounts() {
        getAllBlockCounts().values().forEach(Map::clear);
        setChanged();
    }

    /* =========================================================================
     * Block limits
     * ========================================================================= */

    public Map<Environment, Map<NamespacedKey, Integer>> getAllBlockLimits() {
        migrateIfNeeded();
        if (envBlockLimits == null) envBlockLimits = new EnumMap<>(Environment.class);
        return envBlockLimits;
    }

    public Map<NamespacedKey, Integer> getBlockLimits(Environment env) {
        return getAllBlockLimits().computeIfAbsent(env, e -> new HashMap<>());
    }

    /**
     * @return -1 if no env has a limit, otherwise the lowest limit defined across envs.
     */
    public int getBlockLimit(Environment env, NamespacedKey key) {
        return getBlockLimits(env).getOrDefault(key, -1);
    }

    public boolean isBlockLimited(Environment env, NamespacedKey key) {
        return getBlockLimits(env).containsKey(key);
    }

    public void setBlockLimit(Environment env, NamespacedKey key, int limit) {
        getBlockLimits(env).put(key, limit);
        setChanged();
    }

    /**
     * Set the same limit for every standard environment (overworld, nether, end).
     * Used by 5-segment, env-unspecified permissions and global config defaults.
     */
    public void setBlockLimitAllEnvs(NamespacedKey key, int limit) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setBlockLimit(env, key, limit);
        }
    }

    public void clearAllBlockLimits() {
        getAllBlockLimits().values().forEach(Map::clear);
        setChanged();
    }

    /* =========================================================================
     * Block limit offsets (env-agnostic in storage; offsets apply per env)
     * ========================================================================= */

    public Map<Environment, Map<NamespacedKey, Integer>> getAllBlockLimitsOffset() {
        migrateIfNeeded();
        if (envBlockLimitsOffset == null) envBlockLimitsOffset = new EnumMap<>(Environment.class);
        return envBlockLimitsOffset;
    }

    public Map<NamespacedKey, Integer> getBlockLimitsOffset(Environment env) {
        return getAllBlockLimitsOffset().computeIfAbsent(env, e -> new HashMap<>());
    }

    public int getBlockLimitOffset(Environment env, NamespacedKey key) {
        return getBlockLimitsOffset(env).getOrDefault(key, 0);
    }

    public void setBlockLimitsOffset(Environment env, NamespacedKey key, int offset) {
        getBlockLimitsOffset(env).put(key, offset);
        setChanged();
    }

    /**
     * Apply the same offset to every standard environment. Used by /offset which is env-agnostic.
     */
    public void setBlockLimitsOffsetAllEnvs(NamespacedKey key, int offset) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setBlockLimitsOffset(env, key, offset);
        }
    }

    /* =========================================================================
     * Entity counts (persistent)
     * ========================================================================= */

    public Map<Environment, Map<EntityType, Integer>> getAllEntityCounts() {
        migrateIfNeeded();
        if (envEntityCounts == null) envEntityCounts = new EnumMap<>(Environment.class);
        return envEntityCounts;
    }

    public Map<EntityType, Integer> getEntityCounts(Environment env) {
        return getAllEntityCounts().computeIfAbsent(env, e -> new EnumMap<>(EntityType.class));
    }

    public int getEntityCount(Environment env, EntityType type) {
        return getEntityCounts(env).getOrDefault(type, 0);
    }

    public int getEntityCount(EntityType type) {
        int total = 0;
        for (Map<EntityType, Integer> m : getAllEntityCounts().values()) {
            total += m.getOrDefault(type, 0);
        }
        return total;
    }

    public void incrementEntity(Environment env, EntityType type) {
        getEntityCounts(env).merge(type, 1, Integer::sum);
        setChanged();
    }

    public void decrementEntity(Environment env, EntityType type) {
        Map<EntityType, Integer> m = getEntityCounts(env);
        if (m.containsKey(type)) {
            m.computeIfPresent(type, (k, c) -> c - 1 > 0 ? c - 1 : null);
            setChanged();
        }
    }

    public void clearAllEntityCounts() {
        getAllEntityCounts().values().forEach(Map::clear);
        setChanged();
    }

    /* =========================================================================
     * Entity limits
     * ========================================================================= */

    public Map<Environment, Map<EntityType, Integer>> getAllEntityLimits() {
        migrateIfNeeded();
        if (envEntityLimits == null) envEntityLimits = new EnumMap<>(Environment.class);
        return envEntityLimits;
    }

    public Map<EntityType, Integer> getEntityLimits(Environment env) {
        return getAllEntityLimits().computeIfAbsent(env, e -> new EnumMap<>(EntityType.class));
    }

    public int getEntityLimit(Environment env, EntityType type) {
        return getEntityLimits(env).getOrDefault(type, -1);
    }

    public void setEntityLimit(Environment env, EntityType type, int limit) {
        getEntityLimits(env).put(type, limit);
        setChanged();
    }

    public void setEntityLimitAllEnvs(EntityType type, int limit) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setEntityLimit(env, type, limit);
        }
    }

    public void clearAllEntityLimits() {
        getAllEntityLimits().values().forEach(Map::clear);
        setChanged();
    }

    /* =========================================================================
     * Entity limit offsets
     * ========================================================================= */

    public Map<Environment, Map<EntityType, Integer>> getAllEntityLimitsOffset() {
        migrateIfNeeded();
        if (envEntityLimitsOffset == null) envEntityLimitsOffset = new EnumMap<>(Environment.class);
        return envEntityLimitsOffset;
    }

    public Map<EntityType, Integer> getEntityLimitsOffset(Environment env) {
        return getAllEntityLimitsOffset().computeIfAbsent(env, e -> new EnumMap<>(EntityType.class));
    }

    public int getEntityLimitOffset(Environment env, EntityType type) {
        return getEntityLimitsOffset(env).getOrDefault(type, 0);
    }

    public void setEntityLimitsOffset(Environment env, EntityType type, int offset) {
        getEntityLimitsOffset(env).put(type, offset);
        setChanged();
    }

    public void setEntityLimitsOffsetAllEnvs(EntityType type, int offset) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setEntityLimitsOffset(env, type, offset);
        }
    }

    /* =========================================================================
     * Entity group limits
     * ========================================================================= */

    public Map<Environment, Map<String, Integer>> getAllEntityGroupLimits() {
        migrateIfNeeded();
        if (envEntityGroupLimits == null) envEntityGroupLimits = new EnumMap<>(Environment.class);
        return envEntityGroupLimits;
    }

    public Map<String, Integer> getEntityGroupLimits(Environment env) {
        return getAllEntityGroupLimits().computeIfAbsent(env, e -> new HashMap<>());
    }

    public int getEntityGroupLimit(Environment env, String name) {
        return getEntityGroupLimits(env).getOrDefault(name, -1);
    }

    public void setEntityGroupLimit(Environment env, String name, int limit) {
        getEntityGroupLimits(env).put(name, limit);
        setChanged();
    }

    public void setEntityGroupLimitAllEnvs(String name, int limit) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setEntityGroupLimit(env, name, limit);
        }
    }

    public void clearAllEntityGroupLimits() {
        getAllEntityGroupLimits().values().forEach(Map::clear);
        setChanged();
    }

    /* =========================================================================
     * Entity group limit offsets
     * ========================================================================= */

    public Map<Environment, Map<String, Integer>> getAllEntityGroupLimitsOffset() {
        migrateIfNeeded();
        if (envEntityGroupLimitsOffset == null) envEntityGroupLimitsOffset = new EnumMap<>(Environment.class);
        return envEntityGroupLimitsOffset;
    }

    public Map<String, Integer> getEntityGroupLimitsOffset(Environment env) {
        return getAllEntityGroupLimitsOffset().computeIfAbsent(env, e -> new HashMap<>());
    }

    public int getEntityGroupLimitOffset(Environment env, String name) {
        return getEntityGroupLimitsOffset(env).getOrDefault(name, 0);
    }

    public void setEntityGroupLimitsOffset(Environment env, String name, int offset) {
        getEntityGroupLimitsOffset(env).put(name, offset);
        setChanged();
    }

    public void setEntityGroupLimitsOffsetAllEnvs(String name, int offset) {
        for (Environment env : Environment.values()) {
            if (env == Environment.CUSTOM) continue;
            setEntityGroupLimitsOffset(env, name, offset);
        }
    }

    /* =========================================================================
     * "At limit" helpers
     * ========================================================================= */

    public boolean isAtLimit(Environment env, NamespacedKey key) {
        if (!isBlockLimited(env, key)) return false;
        return getBlockCount(env, key) >= getBlockLimit(env, key) + getBlockLimitOffset(env, key);
    }

    public boolean isAtLimit(Environment env, NamespacedKey key, int limit) {
        return getBlockCount(env, key) >= limit + getBlockLimitOffset(env, key);
    }

    /* =========================================================================
     * Misc
     * ========================================================================= */

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
        setChanged();
    }

    public boolean isGameMode(String gameMode) {
        return getGameMode().equals(gameMode);
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }

    @Override
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
        setChanged();
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged() {
        this.changed = true;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }
}

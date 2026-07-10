package world.bentobox.limits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public class Settings {

    enum GeneralGroup {
        ANIMALS, MOBS
    }

    /**
     * Standard environments with separated limits. {@link Environment#CUSTOM} is intentionally excluded —
     * limits-aware game modes target the three vanilla environments.
     */
    public static final List<Environment> ENVIRONMENTS = List.of(Environment.NORMAL, Environment.NETHER,
            Environment.THE_END);

    private static final Map<Environment, String> ENV_SUFFIX = Map.of(
            Environment.NORMAL, "",
            Environment.NETHER, "-nether",
            Environment.THE_END, "-end");

    private static final String SKIPPING = " - skipping...";
    private static final String LIMIT_SUFFIX = ".limit";
    private static final String LIMIT_LOG_PREFIX = "Limit ";
    private static final String GROUP_OVERRIDE_PREFIX = "Group override ";

    private final Map<GeneralGroup, Integer> general = new EnumMap<>(GeneralGroup.class);
    /** Per-env entity type limits (env defaults from config). */
    private final Map<Environment, Map<EntityType, Integer>> envLimits = new EnumMap<>(Environment.class);
    /** Group definitions and which groups each entity type belongs to. */
    private final Map<EntityType, List<EntityGroup>> groupLimits = new EnumMap<>(EntityType.class);
    /** Per-env group-limit overrides (env defaults from config). */
    private final Map<Environment, Map<String, Integer>> envGroupLimits = new EnumMap<>(Environment.class);
    /** Block group definitions and which groups each canonical block key belongs to. */
    private final Map<org.bukkit.NamespacedKey, List<BlockGroup>> blockGroups = new java.util.HashMap<>();
    /** Per-env block-group limits (defaults from config plus env overrides). */
    private final Map<Environment, Map<String, Integer>> envBlockGroupLimits = new EnumMap<>(Environment.class);
    private final List<String> gameModes;
    private final boolean logLimitsOnJoin;
    private final boolean asyncGolums;
    private final boolean showLimitMessages;
    private final boolean stackedPlantsCountAsOne;
    private final boolean applyMemberLimitPerms;
    private static final List<EntityType> DISALLOWED = Arrays.asList(
            EntityType.TNT,
            EntityType.EVOKER_FANGS,
            EntityType.LLAMA_SPIT,
            EntityType.DRAGON_FIREBALL,
            EntityType.AREA_EFFECT_CLOUD,
            EntityType.END_CRYSTAL,
            EntityType.SMALL_FIREBALL,
            EntityType.FIREBALL,
            EntityType.EXPERIENCE_BOTTLE,
            EntityType.EXPERIENCE_ORB,
            EntityType.SHULKER_BULLET,
            EntityType.WITHER_SKULL,
            EntityType.TRIDENT,
            EntityType.ARROW,
            EntityType.SPECTRAL_ARROW,
            EntityType.SNOWBALL,
            EntityType.EGG,
            EntityType.LEASH_KNOT,
            EntityType.GIANT,
            EntityType.ENDER_PEARL,
            EntityType.ENDER_DRAGON);

    public Settings(Limits addon) {

        // GameModes
        gameModes = addon.getConfig().getStringList("gamemodes");

        // Initialise empty env maps
        for (Environment env : ENVIRONMENTS) {
            envLimits.put(env, new EnumMap<>(EntityType.class));
            envGroupLimits.put(env, new java.util.HashMap<>());
            envBlockGroupLimits.put(env, new java.util.HashMap<>());
        }

        // Pass 1: parse the unsuffixed entitylimits as the default for every env
        loadEntityLimits(addon, "entitylimits", ENVIRONMENTS);
        // Pass 2: env-suffixed overrides
        loadEntityLimits(addon, "entitylimits-nether", List.of(Environment.NETHER));
        loadEntityLimits(addon, "entitylimits-end", List.of(Environment.THE_END));

        // Log limits on join
        logLimitsOnJoin = addon.getConfig().getBoolean("log-limits-on-join", true);
        // Async Golums
        asyncGolums = addon.getConfig().getBoolean("async-golums", true);
        // Show or suppress the "hit the limit" player notifications
        showLimitMessages = addon.getConfig().getBoolean("show-limit-messages", true);
        // Count a stackable plant column (sugar cane, bamboo) as a single plant
        stackedPlantsCountAsOne = addon.getConfig().getBoolean("stacked-plants-count-as-one", false);
        // Apply team members' limit permissions, not just the owner's
        applyMemberLimitPerms = addon.getConfig().getBoolean("apply-member-limit-perms", false);

        addon.log("Entity limits:");
        envLimits.forEach((env, m) -> m.entrySet().stream()
                .map(e -> LIMIT_LOG_PREFIX + e.getKey() + " in " + env + " to " + e.getValue())
                .forEach(addon::log));

        // Group definitions live in the unsuffixed entitygrouplimits section. The entire
        // group's limit is the "default for all envs"; env-suffixed sections override that
        // limit per-group-name (icon and member set are not env-overridable).
        loadGroupDefinitions(addon);
        loadGroupLimitOverrides(addon, "entitygrouplimits-nether", Environment.NETHER);
        loadGroupLimitOverrides(addon, "entitygrouplimits-end", Environment.THE_END);

        addon.log("Entity group limits:");
        getGroupLimitDefinitions().stream()
                .map(e -> LIMIT_LOG_PREFIX + e.getName() + " ("
                        + e.getTypes().stream().map(Enum::name).collect(Collectors.joining(", ")) + ") to "
                        + e.getLimit())
                .forEach(addon::log);

        // Block groups follow the same pattern as entity groups.
        loadBlockGroupDefinitions(addon);
        loadBlockGroupLimitOverrides(addon, "blockgrouplimits-nether", Environment.NETHER);
        loadBlockGroupLimitOverrides(addon, "blockgrouplimits-end", Environment.THE_END);

        addon.log("Block group limits:");
        getBlockGroupDefinitions().stream()
                .map(g -> LIMIT_LOG_PREFIX + g.getName() + " ("
                        + g.getKeys().stream().map(org.bukkit.NamespacedKey::getKey)
                                .collect(Collectors.joining(", "))
                        + ") to " + g.getLimit())
                .forEach(addon::log);
    }

    private void loadBlockGroupDefinitions(Limits addon) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection("blockgrouplimits");
        if (el == null) return;
        for (String name : el.getKeys(false)) {
            int limit = el.getInt(name + LIMIT_SUFFIX);
            Material icon = parseIcon(addon, el.getString(name + ".icon", "BARRIER"));
            Set<org.bukkit.NamespacedKey> keys = el.getStringList(name + ".materials").stream().map(m -> {
                Material material = Material.matchMaterial(m);
                if (material == null || !material.isBlock()) {
                    addon.logError("Unknown block material in blockgrouplimits." + name + ": " + m + SKIPPING);
                    return null;
                }
                return world.bentobox.limits.listeners.BlockLimitsListener.canonicalKey(material);
            }).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
            if (keys.isEmpty()) continue;
            BlockGroup group = new BlockGroup(name, keys, limit, icon);
            keys.forEach(k -> blockGroups.computeIfAbsent(k, x -> new ArrayList<>()).add(group));
            // Default group limit applies to every env unless overridden.
            ENVIRONMENTS.forEach(env -> envBlockGroupLimits.get(env).put(name, limit));
        }
    }

    private void loadBlockGroupLimitOverrides(Limits addon, String section, Environment env) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection(section);
        if (el == null) return;
        for (String name : el.getKeys(false)) {
            applyBlockGroupOverride(addon, el, section, name, env);
        }
    }

    private void applyBlockGroupOverride(Limits addon, ConfigurationSection el, String section, String name,
            Environment env) {
        Integer limit = resolveGroupLimit(el, name);
        if (limit == null) {
            addon.logError(GROUP_OVERRIDE_PREFIX + section + "." + name + " missing limit - skipping.");
            return;
        }
        // Group must already be defined in the base blockgrouplimits section
        if (getBlockGroupDefinitions().stream().noneMatch(g -> g.getName().equals(name))) {
            addon.logError(GROUP_OVERRIDE_PREFIX + section + "." + name
                    + " refers to an undefined group - define it under blockgrouplimits first.");
            return;
        }
        envBlockGroupLimits.get(env).put(name, limit);
    }

    private Material parseIcon(Limits addon, String iconName) {
        try {
            return Material.valueOf(iconName.toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            addon.logError("Invalid group icon name: " + iconName + ". Use a Bukkit Material.");
            return Material.BARRIER;
        }
    }

    private void loadEntityLimits(Limits addon, String section, List<Environment> targetEnvs) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection(section);
        if (el == null) return;
        for (String key : el.getKeys(false)) {
            if (key.equalsIgnoreCase("ANIMALS")) {
                if (targetEnvs.contains(Environment.NORMAL)) {
                    general.put(GeneralGroup.ANIMALS, el.getInt(key, 0));
                }
            } else if (key.equalsIgnoreCase("MOBS")) {
                if (targetEnvs.contains(Environment.NORMAL)) {
                    general.put(GeneralGroup.MOBS, el.getInt(key, 0));
                }
            } else {
                applyEntityKey(addon, section, el, key, targetEnvs);
            }
        }
    }

    private void applyEntityKey(Limits addon, String section, ConfigurationSection el, String key,
            List<Environment> targetEnvs) {
        EntityType type = getType(key);
        if (type == null) {
            addon.logError("Unknown entity type in " + section + ": " + key + SKIPPING);
            return;
        }
        if (DISALLOWED.contains(type)) {
            addon.logError("Entity type in " + section + " not supported: " + key + SKIPPING);
            return;
        }
        int value = el.getInt(key, 0);
        targetEnvs.forEach(env -> envLimits.get(env).put(type, value));
    }

    private void loadGroupDefinitions(Limits addon) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection("entitygrouplimits");
        if (el == null) return;
        for (String name : el.getKeys(false)) {
            int limit = el.getInt(name + LIMIT_SUFFIX);
            Material icon = parseIcon(addon, el.getString(name + ".icon", "BARRIER"));
            Set<EntityType> entities = el.getStringList(name + ".entities").stream().map(s -> {
                EntityType type = getType(s);
                if (type == null) {
                    addon.logError("Unknown entity type: " + s + SKIPPING);
                    return null;
                }
                if (DISALLOWED.contains(type)) {
                    addon.logError("Entity type: " + s + " is not supported" + SKIPPING);
                    return null;
                }
                return type;
            }).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
            if (entities.isEmpty()) continue;
            EntityGroup group = new EntityGroup(name, entities, limit, icon);
            entities.forEach(e -> groupLimits.computeIfAbsent(e, k -> new ArrayList<>()).add(group));
            // Default group limit applies to every env unless overridden.
            ENVIRONMENTS.forEach(env -> envGroupLimits.get(env).put(name, limit));
        }
    }

    private void loadGroupLimitOverrides(Limits addon, String section, Environment env) {
        ConfigurationSection el = addon.getConfig().getConfigurationSection(section);
        if (el == null) return;
        for (String name : el.getKeys(false)) {
            applyGroupOverride(addon, el, section, name, env);
        }
    }

    private void applyGroupOverride(Limits addon, ConfigurationSection el, String section, String name,
            Environment env) {
        // Accept either a flat int (Monsters: 100) or a nested .limit (Monsters.limit: 100)
        Integer limit = resolveGroupLimit(el, name);
        if (limit == null) {
            addon.logError(GROUP_OVERRIDE_PREFIX + section + "." + name + " missing limit - skipping.");
            return;
        }
        // Group must already be defined in the base entitygrouplimits section
        boolean exists = getGroupLimitDefinitions().stream().anyMatch(g -> g.getName().equals(name));
        if (!exists) {
            addon.logError(GROUP_OVERRIDE_PREFIX + section + "." + name
                    + " refers to an undefined group - define it under entitygrouplimits first.");
            return;
        }
        envGroupLimits.get(env).put(name, limit);
    }

    private static Integer resolveGroupLimit(ConfigurationSection el, String name) {
        if (el.isInt(name)) return el.getInt(name);
        if (el.isInt(name + LIMIT_SUFFIX)) return el.getInt(name + LIMIT_SUFFIX);
        return null;
    }

    private EntityType getType(String key) {
        return Arrays.stream(EntityType.values()).filter(v -> v.name().equalsIgnoreCase(key)).findFirst().orElse(null);
    }

    /**
     * Return the env-specific entity limit map. Mutate via Limits API only.
     */
    public Map<EntityType, Integer> getLimits(Environment env) {
        return Collections.unmodifiableMap(envLimits.getOrDefault(env, Collections.emptyMap()));
    }

    /**
     * Group-name → limit overrides for this environment.
     */
    public Map<String, Integer> getGroupLimits(Environment env) {
        return Collections.unmodifiableMap(envGroupLimits.getOrDefault(env, Collections.emptyMap()));
    }

    /**
     * @return the entity-type → group-list lookup
     */
    public Map<EntityType, List<EntityGroup>> getGroupLimits() {
        return groupLimits;
    }

    /**
     * @return the group definitions
     */
    public List<EntityGroup> getGroupLimitDefinitions() {
        return groupLimits.values().stream().flatMap(Collection::stream).distinct().toList();
    }

    public List<String> getGameModes() {
        return gameModes;
    }

    public boolean isLogLimitsOnJoin() {
        return logLimitsOnJoin;
    }

    public boolean isAsyncGolums() {
        return asyncGolums;
    }

    /**
     * @return true if players should be told when they hit a limit; false to limit silently
     */
    public boolean isShowLimitMessages() {
        return showLimitMessages;
    }

    /**
     * @return true if a column of stackable plants (sugar cane, bamboo) counts as one
     *         plant; false if every segment counts (default)
     */
    public boolean isStackedPlantsCountAsOne() {
        return stackedPlantsCountAsOne;
    }

    /**
     * @return true if team members' limit permissions are applied to the island, not just the owner's
     */
    public boolean isApplyMemberLimitPerms() {
        return applyMemberLimitPerms;
    }

    /**
     * @param key canonical block key
     * @return block groups containing this key; empty if none
     */
    public List<BlockGroup> getBlockGroups(org.bukkit.NamespacedKey key) {
        return blockGroups.getOrDefault(key, Collections.emptyList());
    }

    /**
     * @return true if the key belongs to any block group
     */
    public boolean isInBlockGroup(org.bukkit.NamespacedKey key) {
        return blockGroups.containsKey(key);
    }

    /**
     * @return all defined block groups
     */
    public List<BlockGroup> getBlockGroupDefinitions() {
        return blockGroups.values().stream().flatMap(Collection::stream).distinct().toList();
    }

    /**
     * @return the block group's limit in this environment, or -1 if not defined
     */
    public int getBlockGroupLimit(Environment env, String name) {
        return envBlockGroupLimits.getOrDefault(env, Collections.emptyMap()).getOrDefault(name, -1);
    }

    public Map<GeneralGroup, Integer> getGeneral() {
        return general;
    }

    /**
     * Config-file suffix used for environment-specific sections.
     * @param env environment
     * @return suffix like "-nether", "-end", or "" for overworld
     */
    public static String suffixFor(Environment env) {
        return ENV_SUFFIX.getOrDefault(env, "");
    }
}

package world.bentobox.limits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.eclipse.jdt.annotation.Nullable;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.limits.commands.admin.AdminCommand;
import world.bentobox.limits.commands.player.PlayerCommand;
import world.bentobox.limits.calculators.Pipeliner;
import world.bentobox.limits.listeners.BlockLimitsListener;
import world.bentobox.limits.listeners.EntityLimitListener;
import world.bentobox.limits.listeners.JoinListener;
import world.bentobox.limits.listeners.PaperShulkerLimitListener;
import world.bentobox.limits.objects.IslandBlockCount;

/**
 * Addon to BentoBox that monitors and enforces limits.
 *
 * @author tastybento
 */
public class Limits extends Addon {

    private static final String LIMIT_NOT_SET = "Limit not set";
    private static final String ISLAND_PLACEHOLDER = "_island_";
    private Settings settings;
    private List<GameModeAddon> gameModes = new ArrayList<>();
    private BlockLimitsListener blockLimitListener;
    private JoinListener joinListener;
    /** Shared, rate-limited background queue for island recounts. */
    private Pipeliner pipeliner;
    /** Per-island last recount timestamp, used to throttle automatic recounts. */
    private final Map<String, Long> recountCooldowns = new HashMap<>();
    /** Periodic sweep task that reconciles online islands' entity counts. */
    private BukkitTask periodicRecountTask;

    @Override
    public void onDisable() {
        if (periodicRecountTask != null) {
            periodicRecountTask.cancel();
        }
        if (pipeliner != null) {
            pipeliner.stop();
        }
        if (blockLimitListener != null) {
            blockLimitListener.save();
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        gameModes = getPlugin().getAddonsManager().getGameModeAddons().stream()
                .filter(gm -> settings.getGameModes().contains(gm.getDescription().getName()))
                .toList();
        gameModes.forEach(gm -> {
            gm.getAdminCommand().ifPresent(a -> new AdminCommand(this, a));
            gm.getPlayerCommand().ifPresent(a -> new PlayerCommand(this, a));
            registerPlaceholders(gm);
            log("Limits will apply to " + gm.getDescription().getName());
        });
        blockLimitListener = new BlockLimitsListener(this);
        registerListener(blockLimitListener);
        joinListener = new JoinListener(this);
        registerListener(joinListener);
        EntityLimitListener entityLimitListener = new EntityLimitListener(this);
        registerListener(entityLimitListener);
        pipeliner = new Pipeliner(this);
        startPeriodicRecount();
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            registerListener(new world.bentobox.limits.listeners.ItemsAdderListener(this));
            log("ItemsAdder detected: custom block limits active. Use ItemsAdder ids as blocklimits keys.");
        }
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("Oraxen") != null) {
            registerListener(new world.bentobox.limits.listeners.OraxenListener(this));
            log("Oraxen detected: custom block limits active. Use \"oraxen:<itemid>\" blocklimits keys.");
        }
        try {
            Class.forName("io.papermc.paper.event.entity.ShulkerDuplicateEvent");
            registerListener(new PaperShulkerLimitListener(this, entityLimitListener));
            log("Paper detected: shulker duplication limiting active via ShulkerDuplicateEvent.");
        } catch (ClassNotFoundException e) {
            // Not running on Paper; CreatureSpawnEvent handles duplication on Spigot.
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public List<GameModeAddon> getGameModes() {
        return gameModes;
    }

    public BlockLimitsListener getBlockLimitListener() {
        return blockLimitListener;
    }

    public boolean inGameModeWorld(World world) {
        return gameModes.stream().anyMatch(gm -> gm.inWorld(world));
    }

    public String getGameModeName(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst()
                .map(gm -> gm.getDescription().getName()).orElse("");
    }

    public String getGameModePermPrefix(World world) {
        return gameModes.stream().filter(gm -> gm.inWorld(world)).findFirst()
                .map(GameModeAddon::getPermissionPrefix).orElse("");
    }

    public boolean isCoveredGameMode(String gameMode) {
        return gameModes.stream().anyMatch(gm -> gm.getDescription().getName().equals(gameMode));
    }

    public JoinListener getJoinListener() {
        return joinListener;
    }

    /**
     * Shared background recount queue. Processes one island at a time asynchronously, so
     * scheduling many recounts (e.g. when many owners log in at once) never causes lag spikes.
     */
    public Pipeliner getPipeliner() {
        return pipeliner;
    }

    /**
     * Reconcile an island's stored entity counts against reality by queueing a background
     * entity-only recount. This is the self-healing path for counts that drift away from the true
     * value — e.g. mobs born without a tracked spawn event (sniffer eggs, etc.), or increments lost
     * in a crash before the batched save.
     *
     * <p>No-op unless {@code recount-on-join} is enabled, and throttled so the same island is not
     * recounted more often than {@code recount-on-join-cooldown} seconds.
     *
     * @param island the island to reconcile
     */
    public void maybeRecountIsland(Island island) {
        if (!settings.isRecountOnJoin()) {
            return;
        }
        enqueueEntityRecount(island);
    }

    /**
     * Queue a throttled entity-only recount for an island. The cooldown is shared between the
     * join-triggered and periodic sweep paths, so neither can recount the same island back-to-back.
     */
    private void enqueueEntityRecount(Island island) {
        String id = island.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = settings.getRecountOnJoinCooldown() * 1000L;
        Long last = recountCooldowns.get(id);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        recountCooldowns.put(id, now);
        pipeliner.addIslandEntitiesOnly(island);
    }

    /**
     * Start the periodic sweep that reconciles entity counts of online islands. It rotates through
     * the currently online islands and, each cycle, queues the {@code recount-periodic-batch}
     * most-stale ones — so drift that accumulates while owners stay logged in is corrected without
     * a full block scan or a re-login.
     */
    private void startPeriodicRecount() {
        if (!settings.isRecountPeriodic()) {
            return;
        }
        long intervalTicks = settings.getRecountPeriodicInterval() * 20L;
        periodicRecountTask = Bukkit.getScheduler().runTaskTimer(getPlugin(), this::sweepPeriodicRecount, intervalTicks,
                intervalTicks);
    }

    private void sweepPeriodicRecount() {
        int batch = settings.getRecountPeriodicBatch();
        if (batch <= 0) {
            return;
        }
        Map<String, Island> candidates = new HashMap<>();
        for (GameModeAddon gm : gameModes) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (Island island : getIslands().getIslands(gm.getOverWorld(), player.getUniqueId())) {
                    if (island != null && !island.isDeleted() && !island.isUnowned()) {
                        candidates.put(island.getUniqueId(), island);
                    }
                }
            }
        }
        // Reconcile the most-stale islands first so the sweep rotates fairly over time.
        candidates.values().stream()
                .sorted(Comparator.comparingLong(i -> recountCooldowns.getOrDefault(i.getUniqueId(), 0L)))
                .limit(batch)
                .forEach(this::enqueueEntityRecount);
    }

    /* =========================================================================
     * Placeholders
     * ========================================================================= */

    private void registerPlaceholders(GameModeAddon gm) {
        if (getPlugin().getPlaceholdersManager() == null) return;
        Registry.MATERIAL.stream()
                .filter(Material::isBlock)
                .forEach(m -> registerCountAndLimitPlaceholders(m.getKey(), gm));
        Arrays.stream(EntityType.values())
                .forEach(e -> registerCountAndLimitPlaceholders(e, gm));
        registerReachedLimitsPlaceholders(gm);
    }

    /**
     * Registers {@code Limits_<gm>_island_reached_limits} (plus {@code _overworld},
     * {@code _nether}, {@code _end} variants) — a comma-separated list of every
     * limited block, entity, and entity group currently at or over its limit on the
     * user's island. The unsuffixed variant is the union across all environments.
     */
    private void registerReachedLimitsPlaceholders(GameModeAddon gm) {
        for (String suffix : ENV_SUFFIXES) {
            Environment env = envForSuffix(suffix);
            String name = gm.getDescription().getName().toLowerCase(Locale.ROOT) + ISLAND_PLACEHOLDER
                    + "reached_limits" + suffix;
            getPlugin().getPlaceholdersManager().registerPlaceholder(this, name,
                    user -> String.join(", ", getReachedLimits(user, gm, env)));
        }
    }

    /**
     * Returns the pretty names of every limited block material, entity type, and
     * entity group whose count has reached or exceeded its limit on the user's island.
     * Limits are resolved the same way the {@code _limit} placeholders resolve them
     * (island permission limits and offsets included).
     *
     * @param user the player whose island is checked; may be null
     * @param gm   the game mode to check
     * @param env  the environment to check, or {@code null} for the union across all
     *             environments
     * @return list of names at/over their limit; empty if none or the user has no island
     */
    public List<String> getReachedLimits(@Nullable User user, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) {
            return List.of();
        }
        IslandBlockCount ibc = getBlockLimitListener().getIsland(is.getUniqueId());
        if (ibc == null) {
            return List.of();
        }
        List<Environment> envs = env == null ? Settings.ENVIRONMENTS : List.of(env);
        Set<String> reached = new LinkedHashSet<>();
        for (Environment e : envs) {
            addReachedBlockLimits(gm, is, ibc, e, reached);
            addReachedEntityLimits(ibc, e, reached);
            addReachedGroupLimits(ibc, e, reached);
        }
        return List.copyOf(reached);
    }

    private void addReachedBlockLimits(GameModeAddon gm, Island is, IslandBlockCount ibc, Environment env,
            Set<String> reached) {
        getBlockLimitListener().getMaterialLimits(worldForEnv(gm, env), is.getUniqueId())
                .forEach((key, limit) -> {
                    if (limit >= 0 && ibc.getBlockCount(env, key) >= limit) {
                        reached.add(Util.prettifyText(key.getKey()));
                    }
                });
    }

    private void addReachedEntityLimits(IslandBlockCount ibc, Environment env, Set<String> reached) {
        Map<EntityType, Integer> defaults = getSettings().getLimits(env);
        for (EntityType type : EntityType.values()) {
            int limit = ibc.getEntityLimit(env, type);
            if (limit < 0) {
                limit = defaults.getOrDefault(type, -1);
            }
            if (limit < 0) {
                continue;
            }
            limit += ibc.getEntityLimitOffset(env, type);
            if (ibc.getEntityCount(env, type) >= limit) {
                reached.add(Util.prettifyText(type.toString()));
            }
        }
    }

    private void addReachedGroupLimits(IslandBlockCount ibc, Environment env, Set<String> reached) {
        for (EntityGroup group : getSettings().getGroupLimitDefinitions()) {
            int limit = ibc.getEntityGroupLimit(env, group.getName());
            if (limit < 0) {
                limit = getSettings().getGroupLimits(env).getOrDefault(group.getName(), -1);
            }
            if (limit < 0) {
                continue;
            }
            limit += ibc.getEntityGroupLimitOffset(env, group.getName());
            long count = group.getTypes().stream().mapToLong(t -> ibc.getEntityCount(env, t)).sum();
            if (count >= limit) {
                reached.add(group.getName());
            }
        }
    }

    private static final List<String> ENV_SUFFIXES = List.of("", "_overworld", "_nether", "_end");

    /**
     * Registers placeholders for the count and limit of the material.
     *
     * <p>Env-aware naming:
     * <ul>
     *   <li>{@code Limits_<gm>_island_<key>_count} — total across all envs (back-compat)</li>
     *   <li>{@code Limits_<gm>_island_<key>_overworld_count} — overworld only</li>
     *   <li>{@code Limits_<gm>_island_<key>_nether_count} — nether only</li>
     *   <li>{@code Limits_<gm>_island_<key>_end_count} — end only</li>
     * </ul>
     * Same pattern for {@code _limit} and {@code _base_limit}.
     */
    private void registerCountAndLimitPlaceholders(NamespacedKey m, GameModeAddon gm) {
        for (String suffix : ENV_SUFFIXES) {
            Environment env = envForSuffix(suffix);
            String base = gm.getDescription().getName().toLowerCase(Locale.ROOT) + ISLAND_PLACEHOLDER
                    + placeholderKey(m);
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_count",
                    user -> String.valueOf(getCount(user, m, gm, env)));
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_limit",
                    user -> getLimit(user, m, gm, env));
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_base_limit",
                    user -> getBaseLimit(user, m, gm, env));
        }
    }

    private void registerCountAndLimitPlaceholders(EntityType e, GameModeAddon gm) {
        for (String suffix : ENV_SUFFIXES) {
            Environment env = envForSuffix(suffix);
            String base = gm.getDescription().getName().toLowerCase(Locale.ROOT) + ISLAND_PLACEHOLDER
                    + e.toString().toLowerCase(Locale.ROOT);
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_count",
                    user -> String.valueOf(getCount(user, e, gm, env)));
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_limit",
                    user -> getLimit(user, e, gm, env));
            getPlugin().getPlaceholdersManager().registerPlaceholder(this,
                    base + suffix + "_base_limit",
                    user -> getBaseLimit(user, e, gm, env));
        }
    }

    /**
     * Converts a block key into the segment used in placeholder names.
     *
     * <p>Vanilla blocks use the bare key so that {@code minecraft:spawner} becomes
     * {@code spawner}, matching the documented {@code Limits_<gm>_island_spawner_limit}
     * form. Custom blocks (ItemsAdder, Oraxen) keep their namespace, joined with an
     * underscore, because a colon is not valid inside a PlaceholderAPI placeholder.
     */
    static String placeholderKey(NamespacedKey key) {
        String k = key.getKey().toLowerCase(Locale.ROOT);
        if (NamespacedKey.MINECRAFT.equals(key.getNamespace())) {
            return k;
        }
        return key.getNamespace().toLowerCase(Locale.ROOT) + "_" + k;
    }

    /** {@code null} env means "sum/aggregate across all envs". */
    @Nullable
    private static Environment envForSuffix(String suffix) {
        return switch (suffix) {
            case "_overworld" -> Environment.NORMAL;
            case "_nether" -> Environment.NETHER;
            case "_end" -> Environment.THE_END;
            default -> null;
        };
    }

    private int getCount(@Nullable User user, NamespacedKey m, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) return 0;
        IslandBlockCount ibc = getBlockLimitListener().getIsland(is.getUniqueId());
        if (ibc == null) return 0;
        return env == null ? ibc.getBlockCount(m) : ibc.getBlockCount(env, m);
    }

    private long getCount(@Nullable User user, EntityType e, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) return 0;
        IslandBlockCount ibc = getBlockLimitListener().getIsland(is.getUniqueId());
        if (ibc == null) return 0;
        return env == null ? ibc.getEntityCount(e) : ibc.getEntityCount(env, e);
    }

    private String getLimit(@Nullable User user, NamespacedKey m, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) return LIMIT_NOT_SET;
        if (user != null) {
            getJoinListener().checkPerms(user.getPlayer(), gm.getPermissionPrefix() + "island.limit.",
                    is.getUniqueId(), gm.getDescription().getName());
        }
        World w = worldForEnv(gm, env);
        int limit = getBlockLimitListener().getMaterialLimits(w, is.getUniqueId()).getOrDefault(m, -1);
        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getBaseLimit(@Nullable User user, NamespacedKey m, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) return LIMIT_NOT_SET;
        World w = worldForEnv(gm, env);
        int limit = getBlockLimitListener().getMaterialLimits(w, is.getUniqueId()).getOrDefault(m, -1);
        if (limit > 0) {
            IslandBlockCount ibc = getBlockLimitListener().getIsland(is);
            int offset = ibc.getBlockLimitOffset(env == null ? Environment.NORMAL : env, m);
            limit -= offset;
        }
        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getLimit(@Nullable User user, EntityType e, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        if (is == null) return LIMIT_NOT_SET;
        IslandBlockCount ibc = getBlockLimitListener().getIsland(is);
        Environment effective = env == null ? Environment.NORMAL : env;
        int limit = ibc.getEntityLimit(effective, e);
        if (limit < 0) {
            Map<EntityType, Integer> envLimits = getSettings().getLimits(effective);
            if (envLimits.containsKey(e)) limit = envLimits.get(e);
        }
        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private String getBaseLimit(@Nullable User user, EntityType e, GameModeAddon gm, @Nullable Environment env) {
        Island is = gm.getIslands().getIsland(gm.getOverWorld(), user);
        Environment effective = env == null ? Environment.NORMAL : env;
        Map<EntityType, Integer> envLimits = getSettings().getLimits(effective);
        if (is == null || !envLimits.containsKey(e)) return LIMIT_NOT_SET;
        int limit = envLimits.get(e);
        return limit == -1 ? LIMIT_NOT_SET : String.valueOf(limit);
    }

    private World worldForEnv(GameModeAddon gm, @Nullable Environment env) {
        if (env == null) return gm.getOverWorld();
        return switch (env) {
            case NETHER -> gm.getNetherWorld() != null ? gm.getNetherWorld() : gm.getOverWorld();
            case THE_END -> gm.getEndWorld() != null ? gm.getEndWorld() : gm.getOverWorld();
            default -> gm.getOverWorld();
        };
    }
}

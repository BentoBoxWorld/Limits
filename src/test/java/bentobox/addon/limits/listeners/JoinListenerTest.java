package bentobox.addon.limits.listeners;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;


import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.objects.IslandBlockCount;
import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.team.TeamEvent.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * @author tastybento
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {Bukkit.class} )
public class JoinListenerTest {

    @Mock
    private Limits addon;
    @Mock
    private GameModeAddon bskyblock;
    @Mock
    private Player player;

    private JoinListener jl;
    @Mock
    private IslandsManager im;
    @Mock
    private BlockLimitsListener bll;
    @Mock
    private IslandBlockCount ibc;
    @Mock
    private OfflinePlayer owner;
    @Mock
    private Island island;

    @Before
    public void setUp() {
        jl = new JoinListener(addon);
        // Setup addon
        when(addon.getGameModes()).thenReturn(Collections.singletonList(bskyblock));
        when(addon.getGameModeName(any())).thenReturn("bskyblock");
        when(addon.getGameModePermPrefix(any())).thenReturn("bskyblock.");
        // Island Manager
        when(im.hasIsland(any(), any(UUID.class))).thenReturn(true);
        when(island.getUniqueId()).thenReturn("unique_id");
        when(im.getIsland(any(), any(UUID.class))).thenReturn(island);
        // Default is that player has island
        when(addon.getIslands()).thenReturn(im);
        // Player
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("tastybento");
        // No permissions by default
        when(player.getEffectivePermissions()).thenReturn(Collections.emptySet());
        // bsKyBlock
        when(bskyblock.getPermissionPrefix()).thenReturn("bskyblock.");
        AddonDescription desc = new AddonDescription.Builder("main", "BSkyBlock", "1.0").build();
        when(bskyblock.getDescription()).thenReturn(desc);

        // Block limit listener
        when(addon.getBlockLimitListener()).thenReturn(bll);
        when(bll.getIsland(anyString())).thenReturn(ibc);

        // bukkit
        PowerMockito.mockStatic(Bukkit.class);
        // default is that owner is online
        when(owner.isOnline()).thenReturn(true);
        when(owner.getPlayer()).thenReturn(player);
        when(Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(owner);

        // Island
        when(island.getOwner()).thenReturn(UUID.randomUUID());
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandWrongReason() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.BAN);
        jl.onNewIsland(e);
        verify(island, never()).getWorld();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandRegistered() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.REGISTERED);
        jl.onNewIsland(e);
        verify(island).getWorld();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandResetted() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.RESETTED);
        jl.onNewIsland(e);
        verify(island).getWorld();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandCreated() {
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        verify(island).getWorld();
        verify(owner, times(2)).getPlayer();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandCreatedOffline() {
        when(owner.isOnline()).thenReturn(false);
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        verify(island).getWorld();
        verify(owner, never()).getPlayer();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onNewIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnNewIslandCreatedNoNameOrPermPrefix() {
        when(addon.getGameModeName(any())).thenReturn("");
        when(addon.getGameModePermPrefix(any())).thenReturn("bskyblock.");
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.CREATED);
        jl.onNewIsland(e);
        when(addon.getGameModeName(any())).thenReturn("bskyblock");
        when(addon.getGameModePermPrefix(any())).thenReturn("");
        jl.onNewIsland(e);
        verify(owner, never()).getPlayer();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onOwnerChange(world.bentobox.bentobox.api.events.team.TeamEvent.TeamSetownerEvent)}.
     */
    @Test
    public void testOnOwnerChange() {
        TeamSetownerEvent e = mock(TeamSetownerEvent.class);
        when(e.getIsland()).thenReturn(island);
        when(e.getNewOwner()).thenReturn(UUID.randomUUID());
        jl.onOwnerChange(e);
        verify(e, Mockito.times(2)).getIsland();
        verify(e).getNewOwner();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoin() {
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll).setIsland("unique_id", ibc);
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinIBCNull() {
        ibc = null;
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll, never()).setIsland("unique_id", ibc);
    }


    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermNotLimits() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.my.perm.for.game");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).getGameModes();
        verify(bll).setIsland("unique_id", ibc);
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsWrongSize() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.my.perm.for.game");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).logError("Player tastybento has permission: 'bskyblock.island.limit.my.perm.for.game' but format must be 'bskyblock.island.limit.MATERIAL.NUMBER' Ignoring...");
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsInvalidMaterial() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.mumbo.34");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).logError("Player tastybento has permission: 'bskyblock.island.limit.mumbo.34' but MUMBO is not a valid material. Ignoring...");
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsWildcard() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.*");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).logError("Player tastybento has permission: 'bskyblock.island.limit.*' but wildcards are not allowed. Ignoring...");
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsNotNumber() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.abc");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon).logError("Player tastybento has permission: 'bskyblock.island.limit.STONE.abc' but the last part MUST be a number! Ignoring...");
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsSuccess() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        perms.add(permAtt);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setBlockLimit(eq(Material.STONE), eq(24));
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsMultiPerms() {
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        perms.add(permAtt);
        PermissionAttachmentInfo permAtt2 = mock(PermissionAttachmentInfo.class);
        when(permAtt2.getPermission()).thenReturn("bskyblock.island.limit.grass.14");
        perms.add(permAtt2);
        PermissionAttachmentInfo permAtt3 = mock(PermissionAttachmentInfo.class);
        when(permAtt3.getPermission()).thenReturn("bskyblock.island.limit.dirt.34");
        perms.add(permAtt3);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        verify(ibc).setBlockLimit(eq(Material.STONE), eq(24));
        verify(ibc).setBlockLimit(eq(Material.GRASS), eq(14));
        verify(ibc).setBlockLimit(eq(Material.DIRT), eq(34));
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent)}.
     */
    @Test
    public void testOnPlayerJoinWithPermLimitsMultiPermsSameMaterial() {
        // IBC - set the block limit for STONE to be 25 already
        when(ibc.getBlockLimit(any())).thenReturn(25);
        Set<PermissionAttachmentInfo> perms = new HashSet<>();
        PermissionAttachmentInfo permAtt = mock(PermissionAttachmentInfo.class);
        when(permAtt.getPermission()).thenReturn("bskyblock.island.limit.STONE.24");
        perms.add(permAtt);
        PermissionAttachmentInfo permAtt2 = mock(PermissionAttachmentInfo.class);
        when(permAtt2.getPermission()).thenReturn("bskyblock.island.limit.STONE.14");
        perms.add(permAtt2);
        PermissionAttachmentInfo permAtt3 = mock(PermissionAttachmentInfo.class);
        when(permAtt3.getPermission()).thenReturn("bskyblock.island.limit.STONE.34");
        perms.add(permAtt3);
        when(player.getEffectivePermissions()).thenReturn(perms);
        PlayerJoinEvent e = new PlayerJoinEvent(player, "welcome");
        jl.onPlayerJoin(e);
        verify(addon, never()).logError(anyString());
        // Only the limit over 25 should be set
        verify(ibc, never()).setBlockLimit(eq(Material.STONE), eq(24));
        verify(ibc, never()).setBlockLimit(eq(Material.STONE), eq(14));
        verify(ibc).setBlockLimit(eq(Material.STONE), eq(34));
    }



    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnUnregisterIslandNotUnregistered() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.BAN);
        jl.onUnregisterIsland(e);
        verify(island, never()).getWorld();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnUnregisterIslandNotInWorld() {
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon, never()).getBlockLimitListener();
    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnUnregisterIslandInWorld() {
        @SuppressWarnings("unchecked")
        Map<Material, Integer> map = mock(Map.class);
        when(ibc.getBlockLimits()).thenReturn(map);
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon).getBlockLimitListener();
        verify(map).clear();

    }

    /**
     * Test method for {@link bentobox.addon.limits.listeners.JoinListener#onUnregisterIsland(world.bentobox.bentobox.api.events.island.IslandEvent)}.
     */
    @Test
    public void testOnUnregisterIslandInWorldNullIBC() {
        when(bll.getIsland(anyString())).thenReturn(null);
        @SuppressWarnings("unchecked")
        Map<Material, Integer> map = mock(Map.class);
        when(ibc.getBlockLimits()).thenReturn(map);
        when(addon.inGameModeWorld(any())).thenReturn(true);
        IslandEvent e = new IslandEvent(island, null, false, null, IslandEvent.Reason.UNREGISTERED);
        jl.onUnregisterIsland(e);
        verify(island).getWorld();
        verify(addon).getBlockLimitListener();
        verify(map, never()).clear();

    }



}

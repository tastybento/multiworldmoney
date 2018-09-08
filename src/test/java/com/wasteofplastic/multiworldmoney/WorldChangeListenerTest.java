/**
 * Test class
 */
package com.wasteofplastic.multiworldmoney;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

/**
 * @author tastybento
 *
 */
public class WorldChangeListenerTest {

    private MultiWorldMoney plugin;
    private World world;
    private World nether;
    private World end;
    private Player player;
    private PlayerCache pc;
    private Settings settings;
    private Economy eco;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        // Plugin
        plugin = mock(MultiWorldMoney.class);

        // Round down
        when(plugin.roundDown(Mockito.anyDouble(), Mockito.anyInt())).thenCallRealMethod();

        // Settings
        settings = mock(Settings.class);
        when(settings.isShowBalance()).thenReturn(true);
        when(settings.getNewWorldMessage()).thenReturn("message [balance]");
        when(plugin.getSettings()).thenReturn(settings);

        // Player cache
        pc = mock(PlayerCache.class);
        when(plugin.getPlayers()).thenReturn(pc);
        when(pc.getBalance(Mockito.any(), Mockito.any())).thenReturn(500D);

        // Vault Helper & Economy
        VaultHelper vh = mock(VaultHelper.class);
        eco = mock(Economy.class);
        when(eco.getBalance(Mockito.any(OfflinePlayer.class))).thenReturn(123D);
        Answer<String> answer = new Answer<String>() {

            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                double value = invocation.getArgumentAt(0, Double.class);
                return "$" + value;
            }};

            when(eco.format(Mockito.anyDouble())).thenAnswer(answer );
            when(vh.getEcon()).thenReturn(eco);


            when(plugin.getVh()).thenReturn(vh);

            // Worlds
            world = mock(World.class);
            nether = mock(World.class);
            end = mock(World.class);
            List<World> group = new ArrayList<>();
            group.add(world);
            group.add(nether);
            group.add(end);
            when(plugin.getGroupWorlds(Mockito.eq(world))).thenReturn(group);

            // Player
            player = mock(Player.class);
            when(player.getWorld()).thenReturn(world);
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#WorldChangeListener(com.wasteofplastic.multiworldmoney.MultiWorldMoney)}.
     */
    @Test
    public void testWorldChangeListener() {
        new WorldChangeListener(plugin);
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#onWorldLoad(org.bukkit.event.player.PlayerChangedWorldEvent)}.
     */
    @Test
    public void testOnWorldLoadNullWorld() {
        WorldChangeListener l = new WorldChangeListener(plugin);
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, null);
        l.onWorldLoad(event);
        Mockito.verify(pc, Mockito.never()).setBalance(player, world, 0D);
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#onWorldLoad(org.bukkit.event.player.PlayerChangedWorldEvent)}.
     */
    @Test
    public void testOnWorldLoadMoveToGroupWorld() {
        WorldChangeListener l = new WorldChangeListener(plugin);
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, world);
        l.onWorldLoad(event);
        // The world they are moving to is in the same group, so keep the balance, but remove it from the
        // last world otherwise there will be a dupe
        Mockito.verify(pc).setBalance(player, world, 0D);
        Mockito.verify(player).sendMessage("§6message $123.0");
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#onWorldLoad(org.bukkit.event.player.PlayerChangedWorldEvent)}.
     */
    @Test
    public void testOnWorldLoadNewWorldNotInGroupLargerNewBalance() {
        WorldChangeListener l = new WorldChangeListener(plugin);
        // New world
        World newWorld = mock(World.class);
        when(player.getWorld()).thenReturn(newWorld);
        List<World> newWorldGroup = new ArrayList<>();
        // New world group
        newWorldGroup.add(newWorld);
        newWorldGroup.add(mock(World.class));
        newWorldGroup.add(mock(World.class));
        when(plugin.getGroupWorlds(Mockito.eq(newWorld))).thenReturn(newWorldGroup);

        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, world);
        l.onWorldLoad(event);
        // Verifications
        double newBalance = 1500D;
        double oldBalance = 123D;

        Mockito.verify(pc, Mockito.times(2)).setBalance(Mockito.eq(player), Mockito.any(World.class), Mockito.eq(0D));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(world), Mockito.eq(oldBalance));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(newWorld), Mockito.eq(newBalance));


        // Balance is larger in new world, so let's verify
        Mockito.verify(eco).depositPlayer(Mockito.eq(player), Mockito.eq(newBalance - oldBalance));
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#onWorldLoad(org.bukkit.event.player.PlayerChangedWorldEvent)}.
     */
    @Test
    public void testOnWorldLoadNewWorldNotInGroupSmallerNewBalance() {
        WorldChangeListener l = new WorldChangeListener(plugin);
        // New world
        World newWorld = mock(World.class);
        when(player.getWorld()).thenReturn(newWorld);
        List<World> newWorldGroup = new ArrayList<>();
        // New world group
        newWorldGroup.add(newWorld);
        newWorldGroup.add(mock(World.class));
        newWorldGroup.add(mock(World.class));
        when(plugin.getGroupWorlds(Mockito.eq(newWorld))).thenReturn(newWorldGroup);

        // new world balance
        when(pc.getBalance(Mockito.any(), Mockito.any())).thenReturn(5D);

        EconomyResponse resp = mock(EconomyResponse.class);
        when(resp.transactionSuccess()).thenReturn(true);
        when(eco.withdrawPlayer(Mockito.any(OfflinePlayer.class), Mockito.anyDouble())).thenReturn(resp);

        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, world);
        l.onWorldLoad(event);
        // Verifications
        double newBalance = 15D;
        double oldBalance = 123D;

        Mockito.verify(pc, Mockito.times(2)).setBalance(Mockito.eq(player), Mockito.any(World.class), Mockito.eq(0D));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(world), Mockito.eq(oldBalance));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(newWorld), Mockito.eq(newBalance));


        // Balance is larger in new world, so let's verify
        Mockito.verify(eco).withdrawPlayer(Mockito.eq(player), Mockito.eq(oldBalance - newBalance));
    }

    /**
     * Test method for {@link com.wasteofplastic.multiworldmoney.WorldChangeListener#onWorldLoad(org.bukkit.event.player.PlayerChangedWorldEvent)}.
     */
    @Test
    public void testOnWorldLoadNewWorldNotInGroupSmallerNewBalanceTxFailure() {
        WorldChangeListener l = new WorldChangeListener(plugin);
        // New world
        World newWorld = mock(World.class);
        when(player.getWorld()).thenReturn(newWorld);
        List<World> newWorldGroup = new ArrayList<>();
        // New world group
        newWorldGroup.add(newWorld);
        newWorldGroup.add(mock(World.class));
        newWorldGroup.add(mock(World.class));
        when(plugin.getGroupWorlds(Mockito.eq(newWorld))).thenReturn(newWorldGroup);

        // new world balance
        when(pc.getBalance(Mockito.any(), Mockito.any())).thenReturn(5D);

        EconomyResponse resp = mock(EconomyResponse.class);
        when(resp.transactionSuccess()).thenReturn(false);

        when(eco.withdrawPlayer(Mockito.any(OfflinePlayer.class), Mockito.anyDouble())).thenReturn(resp);

        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, world);
        l.onWorldLoad(event);
        // Verifications
        double newBalance = 15D;
        double oldBalance = 123D;

        Mockito.verify(pc, Mockito.times(2)).setBalance(Mockito.eq(player), Mockito.any(World.class), Mockito.eq(0D));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(world), Mockito.eq(oldBalance));
        Mockito.verify(pc).setBalance(Mockito.eq(player), Mockito.eq(newWorld), Mockito.eq(newBalance));


        // Balance is larger in new world, so let's verify
        Mockito.verify(eco).withdrawPlayer(Mockito.eq(player), Mockito.eq(oldBalance - newBalance));
    }


}

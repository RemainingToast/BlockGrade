package org.aussieanarchy.shuffleladder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bukkit.Material.*;

public final class ShuffleLadder extends JavaPlugin implements Listener {

    public HashMap<Material, Material> materials = Maps.newHashMap();
    public ItemStack DIRT = new ItemStack(Material.DIRT);
    public BukkitScheduler scheduler = getServer().getScheduler();
    public List<Player> spectators = Lists.newArrayList();


    public BossBar bar;
    public int schedulerId;
    public long timeStarted;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        setMaterialsInLadder();

        startGame();
    }

    @Override
    public void onDisable() {
        endGame();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerDeathEvent e) {
        Player player = e.getEntity().getPlayer();

        if(player != null) {
            spectators.add(player);

            player.setGameMode(GameMode.SPECTATOR);
            player.getInventory().clear();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!spectators.contains(p) && (Bukkit.getServer().getOnlinePlayers().size() - spectators.size() == 1)) {
                    handleVictory(p);
                }
            }
        }
    }

    private void endGame() {
        bar.removeAll();

        if (bar != null) {
            bar = null;
        }

        scheduler.cancelTask(schedulerId);
    }

    private void startGame() {
        timeStarted = System.currentTimeMillis();

        // Bossbar
        bar = Bukkit.createBossBar("1x Dirt", BarColor.GREEN, BarStyle.SEGMENTED_20);

        // Inventory Generator
        AtomicInteger count = new AtomicInteger(0);

        schedulerId = scheduler.scheduleSyncRepeatingTask(this, () -> {
            int i = count.incrementAndGet();

            bar.setProgress(i / 300f);

            for (Player p : getServer().getOnlinePlayers()) {
                bar.addPlayer(p);

                if (i >= 300) {
                    p.getInventory().addItem(DIRT);
                }
            }

            if (i >= 300) {
                bar.setProgress(1);
                count.set(0);
            }
        }, 0L, 1L);

        // duration 5 minutes
        gracePeriod(6000L);

        setupItemGenerator(BEACON, 50L);
        setupItemGenerator(ENCHANTMENT_TABLE, 100L);
    }

    private void handleVictory(Player victor) {
        getServer().broadcastMessage(ChatColor.GOLD + victor.getDisplayName() + " won! Resetting Arena!");

        victor.getInventory().clear();
        victor.setHealth(victor.getMaxHealth());
        victor.setFoodLevel(20);

        for (Player p : Bukkit.getOnlinePlayers()) {
            spectators.remove(p);

            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
//            p.setHealth(p.getMaxHealth());
//            p.getLocation().setY(5);
        }

        Location pos1 = new Location(victor.getWorld(),-25,0,-25);
        Location pos2 = new Location(victor.getWorld(),25,256,25);

        for (Block b : ArenaUtil.select(pos1, pos2, victor.getWorld())) {
            if (b.getType() != AIR && b.getType() != BEDROCK && b.getType() != BEACON) {
                b.setType(AIR);
            }
        }

        for (Entity e : victor.getWorld().getEntities()) {
            if (!(e instanceof Player)) e.remove();
        }

        endGame();

        ArenaUtil.scatter(victor.getWorld());

        startGame();
    }


    @EventHandler
    public void on(BlockBreakEvent e) {
        Block b = e.getBlock();
        World w = b.getWorld();

        if (b.getType() == BEACON) {
            e.setCancelled(true);
            return;
        }

        if (materials.containsKey(b.getType()) && !e.getPlayer().isSneaking()) {
            e.setExpToDrop(0);
            e.setDropItems(false);

            w.dropItemNaturally(b.getLocation().add(0, 1, 0), new ItemStack(materials.get(b.getType())));
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {

        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - timeStarted;

        if(e.getEntity() instanceof Player && timePassed < 300000 && e.getDamager() instanceof Player) {
            e.setCancelled(true);
        }
    }

    public void gracePeriod(Long time) {
        AtomicInteger minutes = new AtomicInteger(Math.toIntExact(time) / 20 / 60);

        scheduler.scheduleSyncRepeatingTask(this, () -> {

            if(minutes.get() == 0) {
                getServer().broadcastMessage(ChatColor.GOLD + "[Grace Period] Ended, pvp is enabled.");
                minutes.set(-1);
            } else if (minutes.get() != -1) {
                getServer().broadcastMessage(ChatColor.GOLD + "[Grace Period] " + minutes + " minutes remaining.");
                minutes.getAndDecrement();
            }

        }, 0L, 1200L);
    }

    private void setupItemGenerator(Material generator, Long time) {
        scheduler.runTaskTimerAsynchronously(this, () -> {
            for (World w : getServer().getWorlds()) {
                if (w.getEnvironment() != World.Environment.NORMAL) continue;

                for (Chunk c : w.getLoadedChunks()) {
                    int cx = c.getX() << 4;
                    int cz = c.getZ() << 4;

                    for(int x = cx; x < cx + 16; ++x) {
                        for(int z = cz; z < cz + 16; ++z) {
                            for(int y = 0; y < 256; ++y) {
                                Block b = c.getBlock(x, y, z);
                                Location l = b.getLocation().add(0, 1, 0);

                                if (b.getType() == generator) {
                                    scheduler.scheduleSyncDelayedTask(this, () -> {
                                        w.dropItemNaturally(l, DIRT);
                                        w.spawnParticle(Particle.CRIT, l, 5);
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, time);
    }

    /**
     * Beacon -> Dirt -> Wood -> Wool ->
     * Bookshelves -> Cobblestone -> Coal Ore ->
     * Glass -> Iron Ore -> Gold Ore ->
     * Redstone Ore -> Lapis Ore -> Diamond Ore ->
     * Obsidian -> Crystals
     *
     * SHIFT + MINE = Doesn't get next ladder item
     *
     * Enchantment Table drops Dirt 1/4 rate
     * Beacons drops Dirt every 1 second
     */
    private void setMaterialsInLadder() {
        materials.put(Material.DIRT, WOOD);
        materials.put(WOOD, WOOL);
        materials.put(WOOL, BOOKSHELF);
        materials.put(BOOKSHELF, COBBLESTONE);
        materials.put(COBBLESTONE, GLASS);
        materials.put(GLASS, MELON_BLOCK);
        materials.put(MELON_BLOCK, COAL_ORE);
        materials.put(COAL_ORE, IRON_ORE);
        materials.put(IRON_ORE, GOLD_ORE);
        materials.put(GOLD_ORE, REDSTONE_ORE);
        materials.put(REDSTONE_BLOCK, LAPIS_ORE);
        materials.put(LAPIS_BLOCK, DIAMOND_ORE);
        materials.put(DIAMOND_BLOCK, OBSIDIAN);
        materials.put(OBSIDIAN, END_CRYSTAL);
    }


}

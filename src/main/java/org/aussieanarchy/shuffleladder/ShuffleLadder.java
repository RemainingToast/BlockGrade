package org.aussieanarchy.shuffleladder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    public BossBar bar = Bukkit.createBossBar("1x Dirt", BarColor.GREEN, BarStyle.SEGMENTED_20);
    public List<Player> spectators = Lists.newArrayList();

    @Override
    public void onEnable() {
        setMaterialsInLadder();

        getServer().getPluginManager().registerEvents(this, this);

        AtomicInteger count = new AtomicInteger(0);

        // duration 5 minutes
        for(Player p : getServer().getOnlinePlayers()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 300, 60));
        }

        // Inventory Generator
        scheduler.scheduleSyncRepeatingTask(this, () -> {
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

        gracePeriod(6000L);

//        setupItemGenerator(BEACON, 10L);
//        setupItemGenerator(ENCHANTMENT_TABLE, 100L);
    }

    @Override
    public void onDisable() {
        bar.removeAll();
        bar = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerDeathEvent e) {
        Player player = e.getEntity().getPlayer();

        if (player != null) {
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

    private void handleVictory(Player victor) {
        getServer().broadcastMessage(ChatColor.GOLD + victor.getDisplayName() + " won! Resetting Arena!");

        for (Player p : Bukkit.getOnlinePlayers()) {
            spectators.remove(p);

            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
//            p.setHealth(p.getMaxHealth());
//            p.getLocation().setY(5);
        }

        Location pos1 = new Location(victor.getWorld(),-25,0,-25);
        Location pos2 = new Location(victor.getWorld(),25,256,25);

        for (Block b : select(pos1, pos2, victor.getWorld())) {
            if (b.getType() != AIR && b.getType() != BEDROCK && b.getType() != BEACON) {
                b.setType(AIR);
            }
        }

        for (Entity e : victor.getWorld().getEntities()) {
            if (!(e instanceof Player)) e.remove();
        }

        victor.setHealth(victor.getMaxHealth());
        victor.setFoodLevel(20);

        scatter(victor.getWorld());
    }

    public void scatter(World world) {

        Random r = new Random();
        int min = -25;
        int max = 25;

        for (Player p : world.getPlayers()) {
            int x = r.nextInt(max - min) + min;
            int z = r.nextInt(max - min) + min;
            int y = world.getHighestBlockYAt(x, z);
            Location location = new Location(p.getWorld(), x, y, z);
            p.teleport(location);
        }
    }

    public static List<Block> select(Location loc1, Location loc2, World w){

        //First of all, we create the list:
        List<Block> blocks = new ArrayList<>();

        //Next we will name each coordinate
        int x1 = loc1.getBlockX();
        int y1 = loc1.getBlockY();
        int z1 = loc1.getBlockZ();

        int x2 = loc2.getBlockX();
        int y2 = loc2.getBlockY();
        int z2 = loc2.getBlockZ();

        //Then we create the following integers
        int xMin, yMin, zMin;
        int xMax, yMax, zMax;
        int x, y, z;

        //Now we need to make sure xMin is always lower then xMax
        if(x1 > x2){ //If x1 is a higher number then x2
            xMin = x2;
            xMax = x1;
        }else{
            xMin = x1;
            xMax = x2;
        }

        //Same with Y
        if(y1 > y2){
            yMin = y2;
            yMax = y1;
        }else{
            yMin = y1;
            yMax = y2;
        }

        //And Z
        if(z1 > z2){
            zMin = z2;
            zMax = z1;
        }else{
            zMin = z1;
            zMax = z2;
        }

        //Now it's time for the loop
        for(x = xMin; x <= xMax; x ++){
            for(y = yMin; y <= yMax; y ++){
                for(z = zMin; z <= zMax; z ++){
                    Block b = new Location(w, x, y, z).getBlock();
                    blocks.add(b);
                }
            }
        }

        //And last but not least, we return with the list
        return blocks;
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

    public void gracePeriod(Long time) {
        scheduler.scheduleSyncRepeatingTask(this, () -> {
            AtomicInteger count = new AtomicInteger(0);

            int i = count.incrementAndGet();

            if (i >= time) {
                getServer().broadcastMessage(ChatColor.GOLD + "Grace period ended.");
            }

        }, 0L, 1L);
    }

    private void setupItemGenerator(Material generator, Long time) {
        scheduler.scheduleSyncRepeatingTask(this, () -> {
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
                                    w.dropItemNaturally(l, DIRT);
                                    w.spawnParticle(Particle.CRIT, l, 5);
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

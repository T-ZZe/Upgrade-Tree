package fr.arbres;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Better Tree : remplace les arbres du monde par des arbres stylés et plus grands,
 * ajoute des rochers à minerais dans les plaines et forêts. Tout est automatique sur les nouveaux chunks.
 */
public class BetterTreePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private volatile boolean replaceNew = true;
    private volatile boolean replaceSaplings = true;
    private volatile boolean rocksOn = true;
    private volatile double scale = 1.0;
    private volatile double rockChance = 0.10;
    private volatile boolean mushroomsOn = true;
    private volatile boolean iceRemoveVanilla = true;
    private volatile boolean noDaySpawn = true;
    private volatile boolean noDaySpawnPassive = false;
    private volatile boolean noDaySpawnForest = true;
    private volatile boolean paleOnlyCreaking = true;
    private volatile boolean jungleBushOn = true;
    private volatile double jungleBushChance = 0.9;
    private volatile boolean caveIceOn = false;
    private volatile double caveIceDensity = 0.07;
    private volatile boolean caveIceFloor = true;
    private volatile boolean dappledOn = true;
    private volatile double dappledChance = 0.9;
    private volatile boolean iceOn = true;
    private volatile double iceChance = 0.85;
    private volatile boolean stoneOn = true;
    private volatile double stoneChance = 0.4;
    private volatile List<String> stoneBiomes = List.of("stony_shore", "stony_peaks");
    private volatile boolean netherTrees = true;
    private volatile boolean endTowers = true;
    private volatile boolean oasisOn = true;
    private volatile double oasisChance = 0.015;
    private volatile double campChance = 0.4;
    private volatile boolean sandRocksOn = true;
    private volatile double sandRockChance = 0.12;
    private volatile boolean ruinsDesertOn = true;
    private volatile double ruinsDesertChance = 0.02;
    private volatile double ruinsDesertChest = 0.6;
    private volatile boolean ruinsJungleOn = true;
    private volatile double ruinsJungleChance = 0.02;
    private volatile double ruinsJungleChest = 0.5;
    private volatile Set<String> worldNames = new HashSet<>();
    private volatile String packUrl = "";
    private volatile String packSha1 = "";
    private volatile boolean packForce = false;
    private volatile boolean hostOn = true;
    private volatile int hostPort = 8123;
    private volatile String hostAddress = "";
    private PackHost host;

    private final Set<UUID> hooked = new HashSet<>();
    // coffres des campements à remplir (la génération se fait hors du thread principal)
    private final ConcurrentLinkedQueue<String> chestQueue = new ConcurrentLinkedQueue<>();
    private final List<String> pendingChests = new ArrayList<>();
    private final Map<String, Integer> chestTries = new HashMap<>();
    private volatile boolean warned = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadCfg();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("arbres") != null) {
            getCommand("arbres").setExecutor(this);
            getCommand("arbres").setTabCompleter(this);
        }
        for (World w : Bukkit.getWorlds()) hook(w);
        loadPending();
        new BukkitRunnable() {
            @Override
            public void run() {
                fillChests();
            }
        }.runTaskTimer(this, 40L, 40L);
        startHost();
        getLogger().info("Better Tree actif sur " + hooked.size() + " monde(s).");
    }

    @Override
    public void onDisable() {
        String s;
        while ((s = chestQueue.poll()) != null) pendingChests.add(s);
        savePending();
        stopHost();
    }

    private void loadCfg() {
        reloadConfig();
        replaceNew = getConfig().getBoolean("replace-new-chunks", true);
        replaceSaplings = getConfig().getBoolean("replace-saplings", true);
        scale = getConfig().getDouble("scale", 1.0);
        rocksOn = getConfig().getBoolean("rocks.enabled", true);
        rockChance = getConfig().getDouble("rocks.chance", 0.10);
        Cleaner.enabled = getConfig().getBoolean("cleanup.enabled", true);
        Cleaner.orphanLogs = getConfig().getBoolean("cleanup.orphan-logs", true);
        mushroomsOn = getConfig().getBoolean("mushrooms.enabled", true);
        Replacer.heartChance = getConfig().getDouble("pale.heart-chance", 0.45);
        iceRemoveVanilla = getConfig().getBoolean("ice-spikes.remove-vanilla", true);
        Replacer.removeUnfit = getConfig().getBoolean("trees.remove-unfit", true);
        noDaySpawn = getConfig().getBoolean("no-day-spawn-under-leaves.enabled", true);
        noDaySpawnForest = getConfig().getBoolean("no-day-spawn-in-forests.enabled", true);
        paleOnlyCreaking = getConfig().getBoolean("pale-garden-only-creakings.enabled", true);
        noDaySpawnPassive = getConfig().getBoolean("no-day-spawn-under-leaves.include-passive", false);
        jungleBushOn = getConfig().getBoolean("jungle-bushes.enabled", true);
        jungleBushChance = getConfig().getDouble("jungle-bushes.chance", 0.9);
        caveIceOn = getConfig().getBoolean("cave-ice.enabled", false);
        caveIceDensity = getConfig().getDouble("cave-ice.density", 0.07);
        caveIceFloor = getConfig().getBoolean("cave-ice.floor-spikes", true);
        dappledOn = getConfig().getBoolean("dappled-forest.enabled", true);
        dappledChance = getConfig().getDouble("dappled-forest.chance", 0.9);
        iceOn = getConfig().getBoolean("ice-spikes.enabled", true);
        iceChance = getConfig().getDouble("ice-spikes.chance", 0.85);
        stoneOn = getConfig().getBoolean("stone-spikes.enabled", true);
        stoneChance = getConfig().getDouble("stone-spikes.chance", 0.4);
        List<String> sb = getConfig().getStringList("stone-spikes.biomes");
        stoneBiomes = sb.isEmpty() ? List.of("stony_shore", "stony_peaks") : new ArrayList<>(sb);
        netherTrees = getConfig().getBoolean("mushrooms.nether-trees", true);
        endTowers = getConfig().getBoolean("end.towers", true);
        oasisOn = getConfig().getBoolean("oasis.enabled", true);
        oasisChance = getConfig().getDouble("oasis.chance", 0.015);
        campChance = getConfig().getDouble("oasis.camp-chance", 0.4);
        sandRocksOn = getConfig().getBoolean("sandstone-rocks.enabled", true);
        sandRockChance = getConfig().getDouble("sandstone-rocks.chance", 0.12);
        ruinsDesertOn = getConfig().getBoolean("ruins.desert.enabled", true);
        ruinsDesertChance = getConfig().getDouble("ruins.desert.chance", 0.02);
        ruinsDesertChest = getConfig().getDouble("ruins.desert.chest-chance", 0.6);
        ruinsJungleOn = getConfig().getBoolean("ruins.jungle.enabled", true);
        ruinsJungleChance = getConfig().getDouble("ruins.jungle.chance", 0.02);
        ruinsJungleChest = getConfig().getDouble("ruins.jungle.chest-chance", 0.5);
        worldNames = new HashSet<>(getConfig().getStringList("worlds"));
        packUrl = getConfig().getString("resource-pack.url", "");
        packSha1 = getConfig().getString("resource-pack.sha1", "");
        packForce = getConfig().getBoolean("resource-pack.force", false);
        hostOn = getConfig().getBoolean("resource-pack.host.enabled", true);
        hostPort = (int) getConfig().getDouble("resource-pack.host.port", 8123);
        hostAddress = getConfig().getString("resource-pack.host.address", "");
    }

    // ---------------------------------------------------------------- génération automatique

    private void hook(World w) {
        World.Environment env = w.getEnvironment();
        if (env != World.Environment.NORMAL && env != World.Environment.NETHER && env != World.Environment.THE_END) return;
        if (!worldNames.isEmpty() && !worldNames.contains(w.getName())) return;
        if (!hooked.add(w.getUID())) return;
        w.getPopulators().add(new Pop());
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        hook(e.getWorld());
    }

    /** Appelé par le jeu pour chaque chunk NOUVEAU : on remplace ses arbres et on ajoute des rochers. */
    private final class Pop extends BlockPopulator {
        @Override
        public void populate(WorldInfo wi, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            try {
                Canvas cv = new RegionCanvas(region, wi.getMinHeight(), wi.getMaxHeight());
                process(cv, wi.getEnvironment(), wi.getName(), chunkX, chunkZ, wi.getSeed());
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    getLogger().warning("Erreur pendant la génération d'un chunk : " + t);
                }
            }
        }
    }

    /** Tout ce qui est généré / converti dans un chunk. Retourne {arbres et champignons, autres ajouts}. */
    private int[] process(Canvas cv, World.Environment env, String wname, int cx, int cz, long seed) {
        int trees = 0, extra = 0;
        int x0 = cx * 16, z0 = cz * 16, x1 = x0 + 15, z1 = z0 + 15;
        if (env == World.Environment.NORMAL) {
            if (replaceNew) trees += Replacer.replaceTrees(cv, x0, z0, x1, z1, seed, scale);
            if (mushroomsOn) trees += MushroomGen.island(cv, x0, z0, x1, z1, seed, scale);
            if (rocksOn) extra += RockGen.rocks(cv, cx, cz, seed, rockChance);
            if (oasisOn) {
                extra += OasisGen.oasis(cv, wname, cx, cz, seed, oasisChance, campChance,
                        (wn, x, y, z) -> chestQueue.add(wn + ";" + x + ";" + y + ";" + z));
            }
            if (sandRocksOn) extra += RuinGen.sandRocks(cv, cx, cz, seed, sandRockChance);
            if (ruinsDesertOn || ruinsJungleOn) {
                extra += RuinGen.ruins(cv, wname, cx, cz, seed,
                        ruinsDesertOn ? ruinsDesertChance : 0, ruinsDesertChest,
                        ruinsJungleOn ? ruinsJungleChance : 0, ruinsJungleChest,
                        (wn, x, y, z, kind) -> chestQueue.add(wn + ";" + x + ";" + y + ";" + z + ";" + kind));
            }
            if (iceOn) {
                if (iceRemoveVanilla) ExtraGen.clearVanillaSpikes(cv, x0, z0, x1, z1);
                extra += ExtraGen.iceSpikes(cv, cx, cz, seed, iceChance);
            }
            if (dappledOn) extra += DappledGen.decorate(cv, cx, cz, seed, dappledChance);
            if (jungleBushOn) extra += JungleGen.bushes(cv, cx, cz, seed, jungleBushChance);
            if (caveIceOn) extra += CaveIceGen.icicles(cv, cx, cz, seed, caveIceDensity, caveIceFloor);
            if (stoneOn) extra += ExtraGen.stoneSpikes(cv, cx, cz, seed, stoneChance, stoneBiomes);
        } else if (env == World.Environment.NETHER) {
            if (mushroomsOn) {
                if (netherTrees) trees += MushroomGen.nether(cv, x0, z0, x1, z1, seed, scale);
                extra += MushroomGen.netherDecor(cv, x0, z0, x1, z1, seed);
            }
        } else if (env == World.Environment.THE_END) {
            if (endTowers) extra += EndGen.towers(cv, cx, cz, seed);
        }
        return new int[] {trees, extra};
    }

    // ---------------------------------------------------------------- pas de monstres sous les feuilles le jour

    private static String biomeAt(World w, int x, int y, int z) {
        try {
            return w.getBiome(x, y, z).getKey().getKey();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Apparitions naturelles :
     * - forêt de bois pâle (pale_garden) : seuls les creakings peuvent naître ;
     * - le jour : aucun monstre dans les forêts, ni sous un feuillage (feuilles au-dessus, jusqu'à 32 blocs).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason why = e.getSpawnReason();
        if (why != CreatureSpawnEvent.SpawnReason.NATURAL && !why.name().equals("CHUNK_GEN")) return;
        Location loc = e.getLocation();
        World w = loc.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) return;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;

        if (paleOnlyCreaking && biomeAt(w, x, y, z).equals("pale_garden")) {
            if (!e.getEntityType().name().equals("CREAKING")) e.setCancelled(true);
            return;
        }

        if (!noDaySpawn && !noDaySpawnForest) return;
        if (!noDaySpawnPassive && !(e.getEntity() instanceof org.bukkit.entity.Enemy)) return;
        long t = w.getTime();
        if (t >= 12300 && t < 23850) return; // nuit : on ne touche à rien

        if (noDaySpawnForest && biomeAt(w, x, y, z).contains("forest")) {
            e.setCancelled(true);
            return;
        }
        if (!noDaySpawn) return;
        int top = Math.min(w.getMaxHeight() - 1, y + 32);
        for (int yy = y + 1; yy <= top; yy++) {
            if (Blocks.leaf(w.getBlockAt(x, yy, z).getType())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- pousses

    private static TreeGen.Species speciesOf(String treeType) {
        return switch (treeType) {
            case "TREE", "BIG_TREE", "SWAMP" -> TreeGen.Species.OAK;
            case "REDWOOD", "TALL_REDWOOD", "MEGA_REDWOOD" -> TreeGen.Species.SPRUCE;
            case "BIRCH", "TALL_BIRCH" -> TreeGen.Species.BIRCH;
            case "JUNGLE", "SMALL_JUNGLE", "COCOA_TREE" -> TreeGen.Species.JUNGLE;
            case "ACACIA" -> TreeGen.Species.ACACIA;
            case "DARK_OAK" -> TreeGen.Species.DARK_OAK;
            case "CHERRY" -> TreeGen.Species.CHERRY;
            case "PALE_OAK", "PALE_OAK_CREAKING" -> TreeGen.Species.PALE_OAK;
            default -> null;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        if (!replaceSaplings) return;
        String type = e.getSpecies().name();
        if (mushroomsOn && (type.equals("RED_MUSHROOM") || type.equals("BROWN_MUSHROOM"))) {
            growMushroom(e);
            return;
        }
        TreeGen.Species sp = speciesOf(type);
        if (sp == null || !sp.ok()) return;
        Location loc = e.getLocation();
        World w = loc.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) return;
        if (!worldNames.isEmpty() && !worldNames.contains(w.getName())) return;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (!w.getBlockAt(x, y, z).getType().name().endsWith("_SAPLING")) return;

        // il faut de la place au-dessus, sinon on laisse l'arbre normal pousser
        for (int k = 1; k <= 8; k++) {
            org.bukkit.Material t = w.getBlockAt(x, y + k, z).getType();
            if (!Blocks.replaceable(t)) return;
        }
        boolean big = type.equals("JUNGLE") || type.equals("MEGA_REDWOOD") || type.equals("DARK_OAK")
                || type.startsWith("PALE_OAK");
        int ox = x, oz = z, th = 1;
        if (big) {
            th = 2;
            outer:
            for (int ax = x - 1; ax <= x; ax++) {
                for (int az = z - 1; az <= z; az++) {
                    boolean all = true;
                    for (int i = 0; i < 2 && all; i++) {
                        for (int j = 0; j < 2 && all; j++) {
                            all = w.getBlockAt(ax + i, y, az + j).getType().name().endsWith("_SAPLING");
                        }
                    }
                    if (all) {
                        ox = ax;
                        oz = az;
                        break outer;
                    }
                }
            }
        }
        e.setCancelled(true);
        Random rnd = new Random(TreeGen.mix(System.nanoTime() ^ (x * 341873128712L) ^ (z * 132897987541L)));
        TreeGen.Shape shape = TreeGen.build(sp, rnd, ox, y, oz, th, scale, rnd.nextLong());
        if (sp == TreeGen.Species.PALE_OAK && (type.equals("PALE_OAK_CREAKING") || rnd.nextDouble() < Replacer.heartChance)) {
            shape.heart = TreeGen.pickHeart(shape, rnd);
        }
        WorldCanvas cv = new WorldCanvas(w);
        TreeGen.apply(shape, sp, cv, Replacer.snowy(cv.biome(ox, y, oz)));
    }

    private void growMushroom(StructureGrowEvent e) {
        Location loc = e.getLocation();
        World w = loc.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL) return;
        if (!worldNames.isEmpty() && !worldNames.contains(w.getName())) return;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        if (!w.getBlockAt(x, y, z).getType().name().endsWith("_MUSHROOM")) return;
        Material below = w.getBlockAt(x, y - 1, z).getType();
        if (below != Material.MYCELIUM && below != Material.PODZOL) return;
        for (int k = 1; k <= 8; k++) {
            if (!Blocks.replaceable(w.getBlockAt(x, y + k, z).getType())) return;
        }
        if (!MushroomGen.fits(new WorldCanvas(w), x, y, z, scale)) return; // chunk voisin non chargé : pousse vanilla
        e.setCancelled(true);
        Random rnd = new Random(TreeGen.mix(System.nanoTime() ^ (x * 341873128712L) ^ (z * 132897987541L)));
        WorldCanvas cv = new WorldCanvas(w);
        cv.set(x, y, z, Material.AIR.createBlockData());
        MushroomGen.grow(cv, rnd, x, y, z, scale);
    }

    // ---------------------------------------------------------------- coffres des campements

    private File pendingFile() {
        return new File(getDataFolder(), "pending-chests.yml");
    }

    private void loadPending() {
        File f = pendingFile();
        if (!f.exists()) return;
        pendingChests.addAll(YamlConfiguration.loadConfiguration(f).getStringList("chests"));
    }

    private void savePending() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("chests", new ArrayList<>(pendingChests));
        try {
            getDataFolder().mkdirs();
            cfg.save(pendingFile());
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde des coffres impossible : " + ex);
        }
    }

    /** Remplit les coffres des campements une fois leur chunk chargé (thread principal). */
    private void fillChests() {
        String s;
        boolean changed = false;
        while ((s = chestQueue.poll()) != null) {
            pendingChests.add(s);
            changed = true;
        }
        if (pendingChests.isEmpty()) return;
        Iterator<String> it = pendingChests.iterator();
        while (it.hasNext()) {
            String e = it.next();
            String[] p = e.split(";");
            if (p.length != 4 && p.length != 5) {
                it.remove();
                changed = true;
                continue;
            }
            World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            int x, y, z;
            try {
                x = Integer.parseInt(p[1]);
                y = Integer.parseInt(p[2]);
                z = Integer.parseInt(p[3]);
            } catch (NumberFormatException ex) {
                it.remove();
                changed = true;
                continue;
            }
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == Material.CHEST && b.getState() instanceof org.bukkit.block.Chest chest) {
                Random lr = new Random(TreeGen.mix(w.getSeed() ^ (x * 341873128712L) ^ (z * 132897987541L) ^ y));
                String kind = p.length == 5 ? p[4] : "camp";
                if (kind.equals("desert") || kind.equals("jungle")) fillRuinLoot(chest.getBlockInventory(), lr, kind);
                else fillLoot(chest.getBlockInventory(), lr);
                it.remove();
                changed = true;
            } else if (chestTries.merge(e, 1, Integer::sum) >= 5) { // le coffre n'est plus là
                chestTries.remove(e);
                it.remove();
                changed = true;
            }
        }
        if (changed) savePending();
    }

    private static void addItem(List<ItemStack> out, String mat, int amount) {
        Material m = Material.getMaterial(mat);
        if (m != null && amount > 0) out.add(new ItemStack(m, amount));
    }

    /** Butin des ruines enterrées : un peu d'or, de fer, d'émeraudes, et rarement un diamant. */
    private static void fillRuinLoot(Inventory inv, Random r, String kind) {
        List<ItemStack> items = new ArrayList<>();
        if (kind.equals("desert")) {
            if (r.nextInt(100) < 70) addItem(items, "BONE", 2 + r.nextInt(5));
            if (r.nextInt(100) < 60) addItem(items, "ROTTEN_FLESH", 3 + r.nextInt(6));
            if (r.nextInt(100) < 45) addItem(items, "GUNPOWDER", 2 + r.nextInt(4));
            if (r.nextInt(100) < 40) addItem(items, "STRING", 2 + r.nextInt(3));
            if (r.nextInt(100) < 10) addItem(items, "SADDLE", 1);
            if (r.nextInt(100) < 8) addItem(items, "TNT", 1 + r.nextInt(2));
        } else {
            if (r.nextInt(100) < 70) addItem(items, "BAMBOO", 4 + r.nextInt(7));
            if (r.nextInt(100) < 60) addItem(items, "COCOA_BEANS", 3 + r.nextInt(6));
            if (r.nextInt(100) < 50) addItem(items, "MELON_SEEDS", 3 + r.nextInt(4));
            if (r.nextInt(100) < 50) addItem(items, "JUNGLE_SAPLING", 1 + r.nextInt(3));
            if (r.nextInt(100) < 40) addItem(items, "ARROW", 4 + r.nextInt(7));
            if (r.nextInt(100) < 8) addItem(items, "SPYGLASS", 1);
        }
        addItem(items, "GOLD_INGOT", 2 + r.nextInt(4));
        if (r.nextInt(100) < 65) addItem(items, "IRON_INGOT", 2 + r.nextInt(4));
        if (r.nextInt(100) < 50) addItem(items, "EMERALD", 1 + r.nextInt(3));
        if (r.nextInt(100) < 12) addItem(items, "DIAMOND", 1);
        if (r.nextInt(100) < 10) addItem(items, "GOLDEN_APPLE", 1);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        java.util.Collections.shuffle(slots, r);
        for (int i = 0; i < items.size() && i < slots.size(); i++) inv.setItem(slots.get(i), items.get(i));
    }

    /** Ressources simples : nourriture, torches, bois, charbon, un peu de fer... */
    private static void fillLoot(Inventory inv, Random r) {
        List<ItemStack> items = new ArrayList<>();
        addItem(items, "BREAD", 3 + r.nextInt(4));
        if (r.nextInt(2) == 0) addItem(items, "COOKED_MUTTON", 2 + r.nextInt(3));
        if (r.nextInt(3) == 0) addItem(items, "MELON_SLICE", 3 + r.nextInt(5));
        addItem(items, "TORCH", 4 + r.nextInt(7));
        addItem(items, "STICK", 3 + r.nextInt(6));
        addItem(items, "OAK_PLANKS", 6 + r.nextInt(11));
        addItem(items, "COAL", 3 + r.nextInt(6));
        if (r.nextInt(100) < 45) addItem(items, "IRON_INGOT", 1 + r.nextInt(4));
        if (r.nextInt(100) < 15) addItem(items, "GOLD_INGOT", 1 + r.nextInt(2));
        if (r.nextInt(100) < 35) addItem(items, "ARROW", 6 + r.nextInt(7));
        if (r.nextInt(100) < 35) addItem(items, "LEATHER", 1 + r.nextInt(3));
        if (r.nextInt(100) < 30) addItem(items, "STRING", 2 + r.nextInt(4));
        if (r.nextInt(100) < 25) addItem(items, "BUCKET", 1);
        if (r.nextInt(100) < 20) addItem(items, "WATER_BUCKET", 1);
        if (r.nextInt(100) < 25) addItem(items, "EMERALD", 1 + r.nextInt(3));
        if (r.nextInt(100) < 12) addItem(items, "GOLDEN_APPLE", 1);
        if (r.nextInt(100) < 10) addItem(items, "COMPASS", 1);
        if (r.nextInt(100) < 8) addItem(items, "SPYGLASS", 1);
        if (r.nextInt(100) < 30) addItem(items, "DRIED_KELP", 2 + r.nextInt(6));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        java.util.Collections.shuffle(slots, r);
        for (int i = 0; i < items.size() && i < slots.size(); i++) inv.setItem(slots.get(i), items.get(i));
    }

    // ---------------------------------------------------------------- pack de textures

    private void startHost() {
        stopHost();
        if (!hostOn || (packUrl != null && !packUrl.isBlank())) return;
        try (java.io.InputStream in = getResource("BetterTreePack.zip")) {
            if (in == null) {
                getLogger().warning("BetterTreePack.zip introuvable dans le plugin.");
                return;
            }
            host = PackHost.start(in.readAllBytes(), hostPort);
            getLogger().info("Pack de textures hébergé sur le port " + hostPort + " (TCP).");
            if (effectiveUrl() == null) {
                getLogger().warning("Pour envoyer le pack aux joueurs, mets l'adresse de ton serveur dans "
                        + "config.yml (resource-pack.host.address) puis tape /arbres reload.");
            }
        } catch (Throwable ex) {
            getLogger().warning("Impossible d'héberger le pack (port " + hostPort + " occupé ?) : " + ex);
            host = null;
        }
    }

    private void stopHost() {
        if (host != null) {
            host.stop();
            host = null;
        }
    }

    private String effectiveUrl() {
        if (packUrl != null && !packUrl.isBlank()) return packUrl;
        if (host == null) return null;
        String a = hostAddress == null ? "" : hostAddress.trim();
        if (a.isEmpty()) a = Bukkit.getIp();
        if (a == null || a.isBlank()) return null;
        return "http://" + a + ":" + hostPort + PackHost.PATH;
    }

    private byte[] sha1Bytes() {
        String h;
        if (packUrl != null && !packUrl.isBlank()) h = packSha1 == null ? "" : packSha1.trim();
        else h = host == null ? "" : host.sha1();
        if (h.length() != 40) return null;
        byte[] out = new byte[20];
        try {
            for (int i = 0; i < 20; i++) out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        } catch (NumberFormatException ex) {
            return null;
        }
        return out;
    }

    private void sendPack(Player p) {
        String url = effectiveUrl();
        if (url == null) return;
        byte[] hash = sha1Bytes();
        p.setResourcePack(url, hash, packForce);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sendPack(e.getPlayer());
    }

    // ---------------------------------------------------------------- commande

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("arbres.admin")) {
            sender.sendMessage("§cCette commande est réservée aux opérateurs.");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                loadCfg();
                startHost();
                for (World w : Bukkit.getWorlds()) hook(w);
                sender.sendMessage("§aConfiguration rechargée.");
            }
            case "pack" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cCommande réservée aux joueurs.");
                    return true;
                }
                if (effectiveUrl() == null) {
                    p.sendMessage("§cMets l'adresse de ton serveur dans config.yml (resource-pack.host.address).");
                } else {
                    sendPack(p);
                    p.sendMessage("§aPack envoyé.");
                }
            }
            case "convert" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cCommande réservée aux joueurs.");
                    return true;
                }
                int radius = 3;
                if (args.length >= 2) {
                    try {
                        radius = Math.max(1, Math.min(10, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ex) {
                        p.sendMessage("§cRayon invalide (1 à 10 chunks).");
                        return true;
                    }
                }
                convert(p, radius);
            }
            default -> sender.sendMessage("§c/arbres convert [rayon] §7| §c/arbres reload §7| §c/arbres pack");
        }
        return true;
    }

    /** Convertit les arbres (et ajoute des rochers) des chunks déjà générés autour du joueur, un chunk par tick. */
    private void convert(Player p, int radius) {
        World w = p.getWorld();
        int pcx = p.getLocation().getBlockX() >> 4;
        int pcz = p.getLocation().getBlockZ() >> 4;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) q.add(new int[] {pcx + dx, pcz + dz});
        }
        int total = q.size();
        p.sendMessage("§aConversion de " + total + " chunks en cours...");
        int[] done = new int[3];
        long seed = w.getSeed();
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (!q.isEmpty() && n < 2) {
                    int[] c = q.poll();
                    if (!w.isChunkLoaded(c[0], c[1])) continue;
                    WorldCanvas cv = new WorldCanvas(w);
                    int[] res = process(cv, w.getEnvironment(), w.getName(), c[0], c[1], seed);
                    done[0] += res[0];
                    done[1] += res[1];
                    done[2]++;
                    n++;
                }
                if (q.isEmpty()) {
                    cancel();
                    if (p.isOnline()) {
                        p.sendMessage("§aTerminé : §f" + done[0] + " §aarbres remplacés, §f" + done[1]
                                + " §arochers/oasis ajoutés (" + done[2] + " chunks chargés traités).");
                    }
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("arbres.admin") || args.length != 1) return out;
        for (String s : List.of("convert", "reload", "pack")) {
            if (s.startsWith(args[0].toLowerCase())) out.add(s);
        }
        return out;
    }
}

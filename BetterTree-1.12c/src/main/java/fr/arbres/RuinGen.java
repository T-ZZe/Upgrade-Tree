package fr.arbres;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Chest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rochers de grès dans le désert, et ruines dans le désert (grès) et la jungle (pierre moussue, lianes).
 * Chaque ruine peut cacher un coffre enterré sous une dalle repère : il faut creuser pour le trouver.
 */
final class RuinGen {
    private RuinGen() {}

    interface Sink {
        void add(String world, int x, int y, int z, String kind);
    }

    private static final int NONE = Integer.MIN_VALUE;
    private static final BlockFace[] HORIZ = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int[][] D4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static double hash(long seed, int x, int y, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 27) * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static Material mat(String name, Material fallback) {
        Material m = Material.getMaterial(name);
        return m != null ? m : fallback;
    }

    /** Y du premier bloc « plein » en descendant (on traverse air, feuilles, troncs et plantes). */
    private static int surface(Canvas cv, int x, int z) {
        int y = cv.topY(x, z);
        while (y > cv.minY()) {
            Material m = cv.type(x, y, z);
            if (m == null) return NONE;
            if (m.isAir() || Blocks.leaf(m) || Blocks.log(m) || Blocks.replaceable(m)) {
                y--;
                continue;
            }
            return y;
        }
        return NONE;
    }

    private static boolean okGround(Material m, boolean jungle) {
        if (jungle) return Blocks.ground(m);
        return m == Material.SAND || m == Material.SANDSTONE;
    }

    private static BlockFace face(int dx, int dz) {
        if (dx > 0) return BlockFace.EAST;
        if (dx < 0) return BlockFace.WEST;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Pose un bloc seulement dans l'air ou une plante (jamais sur un tronc ou un bloc plein). */
    private static boolean put(Canvas cv, int x, int y, int z, Material m) {
        Material t = cv.type(x, y, z);
        if (t == null || !(t.isAir() || Blocks.replaceable(t))) return false;
        cv.set(x, y, z, m.createBlockData());
        return true;
    }

    /** Remplace le bloc de surface (sable, grès, terre...) par un dallage. */
    private static void floor(Canvas cv, int x, int y, int z, Material m, boolean jungle) {
        Material t = cv.type(x, y, z);
        if (t == null || t == Material.ROOTED_DIRT || !okGround(t, jungle)) return;
        cv.set(x, y, z, m.createBlockData());
    }

    // ================================================================== rochers de grès

    /** Génère (éventuellement) des rochers de grès dans ce chunk de désert. Retourne le nombre de rochers posés. */
    static int sandRocks(Canvas cv, int chunkX, int chunkZ, long worldSeed, double chance) {
        Random r = new Random(TreeGen.mix(worldSeed * 43L + chunkX * 341873128712L + chunkZ * 132897987541L + 5051L));
        if (r.nextDouble() >= chance) return 0;
        int n = 1 + (r.nextDouble() < 0.3 ? 1 : 0);
        int made = 0;
        for (int i = 0; i < n; i++) {
            int x = chunkX * 16 + 3 + r.nextInt(10);
            int z = chunkZ * 16 + 3 + r.nextInt(10);
            if (rock(cv, r, x, z, true, worldSeed)) {
                made++;
                int pebbles = 2 + r.nextInt(3);
                for (int k = 0; k < pebbles; k++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double d = 4.5 + r.nextDouble() * 3;
                    rock(cv, r, x + (int) Math.round(Math.cos(a) * d), z + (int) Math.round(Math.sin(a) * d), false, worldSeed);
                }
            }
        }
        return made;
    }

    private static Material rockMat(double u) {
        if (u < 0.50) return Material.SANDSTONE;
        if (u < 0.70) return Material.SMOOTH_SANDSTONE;
        if (u < 0.84) return Material.CUT_SANDSTONE;
        if (u < 0.96) return Material.SAND;
        return Material.CHISELED_SANDSTONE;
    }

    private static boolean rock(Canvas cv, Random r, int x, int z, boolean big, long worldSeed) {
        int y = surface(cv, x, z);
        if (y == NONE) return false;
        Material m = cv.type(x, y, z);
        if (m == null || !okGround(m, false)) return false;
        if (!cv.biome(x, y, z).contains("desert")) return false;
        Material above = cv.type(x, y + 1, z);
        if (above == null || !(above.isAir() || Blocks.replaceable(above))) return false;

        double rx = big ? 2.0 + r.nextDouble() * 2.6 : 0.9 + r.nextDouble() * 0.9;
        double rz = rx * (0.75 + r.nextDouble() * 0.5);
        double ry = rx * (0.55 + r.nextDouble() * 0.35);
        double cy = y + ry * 0.35;
        int ix = (int) Math.ceil(rx + 1), iz = (int) Math.ceil(rz + 1), iy = (int) Math.ceil(ry + 1);
        long seed = worldSeed ^ (x * 73856093L) ^ (z * 19349663L);
        Map<TreeGen.P, Material> blocks = new HashMap<>();
        int cyi = (int) Math.round(cy);
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    int bx = x + dx, by = cyi + dy, bz = z + dz;
                    if (by < y - 1) continue;
                    double ex = dx / rx, ey = (by - cy) / ry, ez = dz / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    double n = hash(seed, bx, by, bz);
                    if (d + (n - 0.5) * 0.6 > 1.0) continue;
                    blocks.put(new TreeGen.P(bx, by, bz), rockMat(hash(seed + 1, bx, by, bz)));
                }
            }
        }
        if (blocks.isEmpty()) return false;
        for (Map.Entry<TreeGen.P, Material> e : blocks.entrySet()) {
            TreeGen.P p = e.getKey();
            Material t = cv.type(p.x(), p.y(), p.z());
            if (t == null) continue;
            if (t.isAir() || Blocks.replaceable(t) || okGround(t, false)) {
                cv.set(p.x(), p.y(), p.z(), e.getValue().createBlockData());
            }
        }
        return true;
    }

    // ================================================================== ruines

    /** Génère (éventuellement) une ruine dans ce chunk de désert ou de jungle. Retourne le nombre de ruines posées. */
    static int ruins(Canvas cv, String world, int chunkX, int chunkZ, long worldSeed,
                     double desertChance, double desertChest, double jungleChance, double jungleChest, Sink sink) {
        if (desertChance <= 0 && jungleChance <= 0) return 0;
        Random r = new Random(TreeGen.mix(worldSeed * 37L + chunkX * 341873128712L + chunkZ * 132897987541L + 99991L));
        double u = r.nextDouble();
        int mx = chunkX * 16 + 8, mz = chunkZ * 16 + 8;
        int sy = surface(cv, mx, mz);
        if (sy == NONE) return 0;
        String biome = cv.biome(mx, sy, mz);
        boolean jungle = biome.contains("jungle");
        boolean desert = biome.contains("desert");
        if (!jungle && !desert) return 0;
        if (u >= (jungle ? jungleChance : desertChance)) return 0;
        double chestChance = jungle ? jungleChest : desertChest;
        for (int attempt = 0; attempt < 6; attempt++) {
            int cx = chunkX * 16 + 6 + r.nextInt(5);
            int cz = chunkZ * 16 + 6 + r.nextInt(5);
            if (ruin(cv, r, world, cx, cz, jungle, chestChance, sink, worldSeed)) return 1;
        }
        return 0;
    }

    private static Material wallMat(double u, boolean jungle) {
        if (jungle) {
            if (u < 0.30) return Material.STONE_BRICKS;
            if (u < 0.60) return Material.MOSSY_STONE_BRICKS;
            if (u < 0.85) return Material.CRACKED_STONE_BRICKS;
            if (u < 0.93) return Material.MOSSY_COBBLESTONE;
            return Material.CHISELED_STONE_BRICKS;
        }
        if (u < 0.45) return Material.SANDSTONE;
        if (u < 0.70) return Material.CUT_SANDSTONE;
        if (u < 0.88) return Material.SMOOTH_SANDSTONE;
        return Material.CHISELED_SANDSTONE;
    }

    private static Material floorMat(double u, boolean jungle) {
        if (jungle) {
            if (u < 0.40) return Material.MOSSY_STONE_BRICKS;
            if (u < 0.70) return Material.STONE_BRICKS;
            if (u < 0.88) return Material.CRACKED_STONE_BRICKS;
            return mat("MOSS_BLOCK", Material.MOSSY_COBBLESTONE);
        }
        if (u < 0.45) return Material.SMOOTH_SANDSTONE;
        if (u < 0.80) return Material.CUT_SANDSTONE;
        return Material.SANDSTONE;
    }

    private static boolean ruin(Canvas cv, Random r, String world, int cx, int cz, boolean jungle,
                                double chestChance, Sink sink, long worldSeed) {
        int yc = surface(cv, cx, cz);
        if (yc == NONE) return false;
        Material gc = cv.type(cx, yc, cz);
        if (gc == null || !okGround(gc, jungle)) return false;

        int hw = 3 + r.nextInt(3), hd = 3 + r.nextInt(3);
        // terrain assez plat, du bon type, dans le bon biome
        int[][] samples = {{hw, hd}, {-hw, hd}, {hw, -hd}, {-hw, -hd}, {0, hd}, {0, -hd}, {hw, 0}, {-hw, 0}};
        for (int[] s : samples) {
            int y = surface(cv, cx + s[0], cz + s[1]);
            if (y == NONE || Math.abs(y - yc) > 2) return false;
            Material m = cv.type(cx + s[0], y, cz + s[1]);
            if (m == null || !okGround(m, jungle)) return false;
        }
        String key = jungle ? "jungle" : "desert";
        if (!cv.biome(cx + hw, yc, cz + hd).contains(key) || !cv.biome(cx - hw, yc, cz - hd).contains(key)) return false;

        long seed = worldSeed ^ (cx * 73856093L) ^ (cz * 19349663L);
        int baseH = 2 + r.nextInt(3);
        int doorSide = r.nextInt(4);

        // coffre enterré : cellule choisie avant de construire (on retient la hauteur d'origine du sol)
        boolean chest = r.nextDouble() < chestChance;
        int chx = cx + r.nextInt(3) - 1, chz = cz + r.nextInt(3) - 1;
        int chSy = chest ? surface(cv, chx, chz) : NONE;
        if (chSy == NONE) chest = false;

        List<int[]> wallBlocks = new ArrayList<>();
        Material slab = jungle ? Material.MOSSY_STONE_BRICK_SLAB : Material.CUT_SANDSTONE_SLAB;
        Material carpet = mat("MOSS_CARPET", null);

        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                int x = cx + dx, z = cz + dz;
                int sy = surface(cv, x, z);
                if (sy == NONE) continue;
                double n = hash(seed, x, 0, z);
                boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hd;
                boolean isChest = chest && x == chx && z == chz;
                if (edge) {
                    boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hd;
                    boolean door = (doorSide == 0 && dz == hd && Math.abs(dx) <= 1)
                            || (doorSide == 1 && dz == -hd && Math.abs(dx) <= 1)
                            || (doorSide == 2 && dx == hw && Math.abs(dz) <= 1)
                            || (doorSide == 3 && dx == -hw && Math.abs(dz) <= 1);
                    int h;
                    if (door) h = 0;
                    else if (corner) h = 3 + (int) (n * 3);
                    else if (n < 0.15) h = 0;
                    else h = (int) Math.round(baseH * (0.2 + n));
                    h = Math.min(h, 5);
                    for (int y = sy + 1; y <= yc + h; y++) {
                        if (put(cv, x, y, z, wallMat(hash(seed + 1, x, y, z), jungle))) wallBlocks.add(new int[] {x, y, z});
                    }
                    if (h == 0) floor(cv, x, sy, z, floorMat(hash(seed + 5, x, 0, z), jungle), jungle);
                } else {
                    if (n < 0.62 || isChest) floor(cv, x, sy, z, floorMat(hash(seed + 5, x, 0, z), jungle), jungle);
                    if (isChest) continue;
                    double n2 = hash(seed + 2, x, 0, z);
                    if (n2 < 0.10) put(cv, x, sy + 1, z, slab);
                    else if (!jungle && n2 < 0.20) put(cv, x, sy + 1, z, Material.SAND);
                    else if (jungle && carpet != null && n2 < 0.32) put(cv, x, sy + 1, z, carpet);
                }
            }
        }

        // quelques colonnes brisées à l'intérieur
        int stubs = 1 + r.nextInt(3);
        for (int i = 0; i < stubs; i++) {
            int x = cx + r.nextInt(2 * hw - 1) - (hw - 1);
            int z = cz + r.nextInt(2 * hd - 1) - (hd - 1);
            if (chest && x == chx && z == chz) continue;
            int sy = surface(cv, x, z);
            if (sy == NONE) continue;
            int hh = 1 + r.nextInt(3);
            for (int y = sy + 1; y <= sy + hh; y++) {
                if (put(cv, x, y, z, wallMat(hash(seed + 6, x, y, z), jungle))) wallBlocks.add(new int[] {x, y, z});
            }
        }

        // jungle : lianes qui retombent le long des murs
        if (jungle) {
            for (int[] p : wallBlocks) {
                for (int[] d : D4) {
                    int nx = p[0] + d[0], nz = p[2] + d[1];
                    Material t = cv.type(nx, p[1], nz);
                    if (t == null || !t.isAir()) continue;
                    if (hash(seed + 3, nx, p[1], nz) > 0.22) continue;
                    BlockFace f = face(-d[0], -d[1]);
                    int len = 1 + (int) (hash(seed + 4, nx, p[1], nz) * 3);
                    for (int k = 0; k < len; k++) {
                        int yy = p[1] - k;
                        Material tt = cv.type(nx, yy, nz);
                        if (tt == null || !tt.isAir()) break;
                        BlockData v = Material.VINE.createBlockData();
                        if (v instanceof MultipleFacing mf) {
                            mf.setFace(f, true);
                            cv.set(nx, yy, nz, mf);
                        }
                    }
                }
            }
        }

        // coffre : repère en pierre sculptée au sol, deux blocs plus bas le coffre, sous un bloc de sable / terre
        if (chest && chSy - 2 > cv.minY() + 2) {
            Material below = cv.type(chx, chSy - 2, chz);
            if (below != null && !below.isAir()) {
                cv.set(chx, chSy, chz, (jungle ? Material.CHISELED_STONE_BRICKS : Material.CHISELED_SANDSTONE).createBlockData());
                cv.set(chx, chSy - 1, chz, (jungle ? Material.DIRT : Material.SAND).createBlockData());
                BlockData cd = Material.CHEST.createBlockData();
                if (cd instanceof Chest ch) ch.setFacing(HORIZ[r.nextInt(4)]);
                cv.set(chx, chSy - 2, chz, cd);
                sink.add(world, chx, chSy - 2, chz, jungle ? "jungle" : "desert");
            }
        }
        return true;
    }
}

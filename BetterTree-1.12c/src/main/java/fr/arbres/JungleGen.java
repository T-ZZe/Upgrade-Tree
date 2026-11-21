package fr.arbres;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;

import java.util.Random;

/**
 * Buissons de jungle : touffes de feuilles rondes sur une souche de tronc, avec fougères autour et quelques lianes.
 * Chaque buisson reste à l'intérieur de son chunk (aucun problème de bord de chunk).
 */
final class JungleGen {
    private JungleGen() {}

    private static final int NONE = Integer.MIN_VALUE;

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

    /** Premier sol sous (x, z), à travers feuillages et plantes ; NONE sur l'eau. */
    private static int ground(Canvas cv, int x, int z) {
        int y = cv.topY(x, z);
        while (y > cv.minY()) {
            Material m = cv.type(x, y, z);
            if (m == null) return NONE;
            if (m.isAir() || Blocks.leaf(m) || Blocks.log(m) || Blocks.replaceable(m)) {
                y--;
                continue;
            }
            if (Blocks.liquid(m)) return NONE;
            return y;
        }
        return NONE;
    }

    private static boolean soil(Material m) {
        return m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.PODZOL || m == Material.COARSE_DIRT
                || m == Material.MOSS_BLOCK;
    }

    /** Case libre pour un buisson : de l'air ou une plante, jamais une feuille ou un tronc d'un arbre. */
    private static boolean free(Material m) {
        return m != null && (m.isAir() || (Blocks.replaceable(m) && !Blocks.leaf(m)));
    }

    static int bushes(Canvas cv, int chunkX, int chunkZ, long seed, double chance) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        Random r = new Random(TreeGen.mix(seed * 43L + chunkX * 341873128712L + chunkZ * 132897987541L + 8484L));
        if (r.nextDouble() >= chance) return 0;
        int made = 0;
        int clusters = 2 + r.nextInt(4);
        for (int c = 0; c < clusters; c++) {
            int px = x0 + 3 + r.nextInt(10), pz = z0 + 3 + r.nextInt(10);
            int n = 1 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                int x = Math.max(x0 + 3, Math.min(x0 + 12, px + r.nextInt(5) - 2));
                int z = Math.max(z0 + 3, Math.min(z0 + 12, pz + r.nextInt(5) - 2));
                double size = r.nextDouble() < 0.2 ? 2.4 + r.nextDouble() * 0.9 : 1.5 + r.nextDouble() * 1.0;
                long bs = r.nextLong();
                int y = ground(cv, x, z);
                if (y == NONE || !cv.biome(x, y, z).contains("jungle")) continue;
                Material g = cv.type(x, y, z);
                if (g == null || !soil(g)) continue;
                if (!free(cv.type(x, y + 1, z)) || !free(cv.type(x, y + 2, z))) continue;
                bush(cv, new Random(TreeGen.mix(bs)), bs, x, y, z, size);
                made++;
            }
        }
        return made;
    }

    private static void bush(Canvas cv, Random r, long s, int bx, int g, int bz, double R) {
        int y0 = g + 1;
        BlockData leaf = Material.JUNGLE_LEAVES.createBlockData();
        if (leaf instanceof Leaves l) {
            l.setPersistent(true);
            l.setDistance(1);
        }
        BlockData log = Material.JUNGLE_LOG.createBlockData();
        if (log instanceof Orientable o) o.setAxis(Axis.Y);
        // souche au centre
        cv.set(bx, y0, bz, log);
        if (R > 2.0) cv.set(bx, y0 + 1, bz, log);
        double[] radii = {R, R - 0.6, R - 1.5, R - 2.2};
        int layers = R >= 2.4 ? 4 : (R >= 2.0 ? 3 : 2);
        for (int k = 0; k < layers; k++) {
            double rad = Math.max(0.5, radii[k]);
            int ir = (int) Math.ceil(rad) + 1;
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    int x = bx + dx, z = bz + dz;
                    double d = (dx * dx + dz * dz) / (rad * rad);
                    double n = hash(s, x, y0 + k, z);
                    if (d + (n - 0.5) * 0.8 > 1.0) continue;
                    if (dx == 0 && dz == 0 && k < 2 && R > 2.0) continue; // souche
                    if (dx == 0 && dz == 0 && k == 0) continue;
                    Material cur = cv.type(x, y0 + k, z);
                    if (cur == null || !free(cur)) continue;
                    cv.set(x, y0 + k, z, leaf);
                }
            }
        }
        // lianes qui pendent sur les côtés
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockFace[] faces = {BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH};
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = bx + dx, z = bz + dz;
                if (cv.type(x, y0, z) != Material.JUNGLE_LEAVES) continue;
                for (int i = 0; i < 4; i++) {
                    int nx = x + dirs[i][0], nz = z + dirs[i][1];
                    if (hash(s + 7, nx, y0, nz) > 0.16) continue;
                    int len = 1 + (int) (hash(s + 8, nx, y0, nz) * 3);
                    for (int k = 0; k < len; k++) {
                        Material c = cv.type(nx, y0 - k, nz);
                        if (c == null || !c.isAir()) break;
                        BlockData v = Material.VINE.createBlockData();
                        if (v instanceof MultipleFacing mf) {
                            mf.setFace(faces[i], true);
                            cv.set(nx, y0 - k, nz, mf);
                        }
                    }
                }
            }
        }
        // fougères et herbes autour
        BlockData fern = Material.FERN.createBlockData();
        BlockData grass = Material.SHORT_GRASS.createBlockData();
        int ring = (int) Math.ceil(R) + 2;
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                double dd = Math.sqrt(dx * dx + dz * dz);
                if (dd < R - 0.3 || dd > R + 2.0) continue;
                double n = hash(s + 3, bx + dx, g, bz + dz);
                if (n > 0.4) continue;
                int x = bx + dx, z = bz + dz;
                Material gr = cv.type(x, g, z);
                Material up = cv.type(x, g + 1, z);
                if (gr == null || !soil(gr) || up == null || !(up.isAir() || Blocks.replaceable(up)) || Blocks.leaf(up)) continue;
                Material up2 = cv.type(x, g + 2, z);
                double k = hash(s + 4, x, g, z);
                if (k < 0.25 && up2 != null && up2.isAir()) {
                    BlockData lo = Material.LARGE_FERN.createBlockData();
                    BlockData hi = Material.LARGE_FERN.createBlockData();
                    if (lo instanceof Bisected bl && hi instanceof Bisected bh) {
                        bl.setHalf(Bisected.Half.BOTTOM);
                        bh.setHalf(Bisected.Half.TOP);
                        cv.set(x, g + 1, z, lo);
                        cv.set(x, g + 2, z, hi);
                    }
                } else if (k < 0.65) {
                    cv.set(x, g + 1, z, fern);
                } else {
                    cv.set(x, g + 1, z, grass);
                }
            }
        }
    }
}

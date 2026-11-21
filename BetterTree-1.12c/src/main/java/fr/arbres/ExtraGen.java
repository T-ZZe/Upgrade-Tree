package fr.arbres;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pics de glace, pics de pierre moussus au bord de l'eau. */
final class ExtraGen {
    private ExtraGen() {}

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

    /** Premier bloc solide en descendant (feuillage, herbes... ignorés). Eau => NONE. */
    static int surface(Canvas cv, int x, int z) {
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

    private static boolean nearWater(Canvas cv, int x, int y, int z, int maxD) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int d = 2; d <= maxD; d += 2) {
            for (int[] v : dirs) {
                int px = x + v[0] * d, pz = z + v[1] * d;
                for (int dy = -2; dy <= 0; dy++) {
                    if (cv.type(px, y + dy, pz) == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ pics de glace

    private static boolean iceGround(Material m) {
        String n = m.name();
        return n.equals("SNOW_BLOCK") || n.equals("PACKED_ICE") || n.equals("ICE") || n.equals("BLUE_ICE")
                || n.equals("POWDER_SNOW");
    }

    private static boolean iceish(Material m) {
        String n = m.name();
        return n.equals("PACKED_ICE") || n.equals("ICE") || n.equals("BLUE_ICE");
    }

    /**
     * Supprime les pics de glace de la génération normale (tout ce qui est glace au-dessus du sol du biome),
     * pour qu'il ne reste que la version améliorée. Retourne le nombre de blocs retirés.
     */
    static int clearVanillaSpikes(Canvas cv, int x0, int z0, int x1, int z1) {
        int[][] samples = {{8, 8}, {2, 2}, {13, 2}, {2, 13}, {13, 13}};
        boolean ice = false;
        for (int[] sp : samples) {
            if (cv.biome(x0 + sp[0], 72, z0 + sp[1]).contains("ice_spikes")) ice = true;
        }
        if (!ice) return 0;
        int pad = 8;
        int w = (x1 - x0 + 1) + 2 * pad;
        int[][] top = new int[w][w];
        boolean[][] isIce = new boolean[w][w];
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < w; dz++) {
                int y = surface(cv, x0 - pad + dx, z0 - pad + dz);
                top[dx][dz] = y;
                if (y != NONE) {
                    Material m = cv.type(x0 - pad + dx, y, z0 - pad + dz);
                    isIce[dx][dz] = m != null && iceish(m);
                }
            }
        }
        BlockData air = Material.AIR.createBlockData();
        int removed = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int ix = x - x0 + pad, iz = z - z0 + pad;
                if (top[ix][iz] == NONE || !isIce[ix][iz]) continue;
                // niveau du sol = surface de la colonne voisine la plus proche qui n'est pas de la glace
                int gl = NONE;
                for (int rr = 1; rr <= pad && gl == NONE; rr++) {
                    for (int dx = -rr; dx <= rr && gl == NONE; dx++) {
                        for (int dz = -rr; dz <= rr; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != rr) continue;
                            int jx = ix + dx, jz = iz + dz;
                            if (jx < 0 || jz < 0 || jx >= w || jz >= w) continue;
                            if (top[jx][jz] != NONE && !isIce[jx][jz]) {
                                gl = top[jx][jz];
                                break;
                            }
                        }
                    }
                }
                if (gl == NONE) continue;
                for (int y = top[ix][iz]; y > gl; y--) {
                    Material m = cv.type(x, y, z);
                    if (m != null && iceish(m)) {
                        cv.set(x, y, z, air);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Sol réel sous (x, z) : comme surface(), mais la glace des pics (pics vanilla ou les nôtres) est traversée.
     * Ainsi un pic ne démarre jamais SUR un autre pic, et le résultat ne dépend pas de l'ordre de génération des chunks.
     * Une mare gelée (glace au-dessus de l'eau) donne NONE.
     */
    private static int ground(Canvas cv, int x, int z) {
        int y = cv.topY(x, z);
        while (y > cv.minY()) {
            Material m = cv.type(x, y, z);
            if (m == null) return NONE;
            if (m.isAir() || Blocks.leaf(m) || Blocks.log(m) || Blocks.replaceable(m) || iceish(m)) {
                y--;
                continue;
            }
            if (Blocks.liquid(m)) return NONE;
            return y;
        }
        return NONE;
    }

    /**
     * Pics de glace. Chaque chunk dessine la part des pics de ses voisins qui entre dans ses propres colonnes
     * (tout est calculé à partir de la graine) : un pic à cheval sur deux chunks est donc toujours complet, quel que soit
     * l'ordre de génération, et le nettoyage des pics vanilla ne peut plus le couper.
     */
    static int iceSpikes(Canvas cv, int chunkX, int chunkZ, long seed, double chance) {
        int made = 0;
        for (int dcx = -1; dcx <= 1; dcx++) {
            for (int dcz = -1; dcz <= 1; dcz++) {
                made += iceSpikesOf(cv, chunkX, chunkZ, chunkX + dcx, chunkZ + dcz, seed, chance, dcx == 0 && dcz == 0);
            }
        }
        return made;
    }

    /** Pics « possédés » par le chunk (ox, oz), dessinés uniquement dans les colonnes du chunk (tx, tz). */
    private static int iceSpikesOf(Canvas cv, int tx, int tz, int ox, int oz, long seed, double chance, boolean own) {
        Random r = new Random(TreeGen.mix(seed * 19L + ox * 341873128712L + oz * 132897987541L + 5151L));
        if (r.nextDouble() >= chance) return 0;
        int cx0 = tx * 16, cz0 = tz * 16;
        int clusters = 1 + (r.nextDouble() < 0.45 ? 1 : 0);
        // tous les paramètres sont tirés d'abord : le résultat ne dépend jamais de l'état du terrain
        List<double[]> spikes = new ArrayList<>(); // {x, z, h, baseR, leanMax, seed, rayon d'emprise}
        double[] first = null;
        for (int c = 0; c < clusters; c++) {
            int x = ox * 16 + 4 + r.nextInt(8), z = oz * 16 + 4 + r.nextInt(8);
            int h = 18 + r.nextInt(22);
            double br = 3.0 + r.nextDouble() * 2.0;
            long ps = r.nextLong();
            int sat = 2 + r.nextInt(4);
            double[][] sp = new double[sat][];
            for (int i = 0; i < sat; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = 4 + r.nextDouble() * 4;
                int sh = 6 + r.nextInt(11);
                double sbr = 1.4 + r.nextDouble() * 1.4;
                long sps = r.nextLong();
                sp[i] = new double[] {x + Math.round(Math.cos(a) * d), z + Math.round(Math.sin(a) * d), sh, sbr, 0.35, sps, sbr + 1.5};
            }
            // deux groupes trop proches : le second est abandonné (sinon les pics se fondent les uns dans les autres)
            if (first != null && Math.hypot(first[0] - x, first[1] - z) < 13) continue;
            double[] main = {x, z, h, br, 0.30, ps, br + 2.0};
            if (first == null) first = main;
            spikes.add(main);
            List<double[]> placed = new ArrayList<>();
            placed.add(main);
            for (double[] s : sp) {
                boolean clash = false;
                for (double[] o : placed) {
                    if (Math.hypot(o[0] - s[0], o[1] - s[1]) < o[6] + s[6]) {
                        clash = true;
                        break;
                    }
                }
                if (clash) continue;
                placed.add(s);
                spikes.add(s);
            }
        }
        int made = 0;
        for (double[] s : spikes) {
            int x = (int) s[0], z = (int) s[1];
            // le pic peut-il toucher le chunk cible ? (emprise + inclinaison)
            double reach = s[3] + 1.5 + s[4] * s[2];
            if (x + reach < cx0 || x - reach > cx0 + 15 || z + reach < cz0 || z - reach > cz0 + 15) continue;
            int y = ground(cv, x, z);
            if (y == NONE || !cv.biome(x, y, z).contains("ice_spikes")) continue;
            Material g = cv.type(x, y, z);
            if (g == null || !iceGround(g)) continue;
            iceSpike(cv, new Random(TreeGen.mix((long) s[5])), seed, x, y, z, (int) s[2], s[3], s[4],
                    cx0, cz0, cx0 + 15, cz0 + 15);
            if (own) made++;
        }
        return made;
    }

    private static void iceSpike(Canvas cv, Random r, long seed, int x, int y, int z, int h, double baseR, double leanMax,
                                 int clipX0, int clipZ0, int clipX1, int clipZ1) {
        double ang = r.nextDouble() * Math.PI * 2;
        double lean = r.nextDouble() * leanMax;
        double lx = Math.cos(ang) * lean, lz = Math.sin(ang) * lean;
        BlockData packed = Material.PACKED_ICE.createBlockData();
        BlockData ice = Material.ICE.createBlockData();
        BlockData blue = Material.BLUE_ICE.createBlockData();
        long s = seed ^ (x * 73856093L) ^ (z * 19349663L);
        for (int k = -2; k <= h; k++) {
            double t = Math.max(0, k) / (double) h;
            double rad = baseR * Math.pow(1.0 - t, 0.9) + 0.45;
            double ccx = x + lx * Math.max(0, k), ccz = z + lz * Math.max(0, k);
            int ir = (int) Math.ceil(rad) + 1;
            int cxi = (int) Math.round(ccx), czi = (int) Math.round(ccz);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    int px = cxi + dx, pz = czi + dz;
                    if (px < clipX0 || px > clipX1 || pz < clipZ0 || pz > clipZ1) continue;
                    double ddx = px - ccx, ddz = pz - ccz;
                    double d2 = (ddx * ddx + ddz * ddz) / (rad * rad);
                    double n = hash(s, px, y + k, pz);
                    if (d2 + (n - 0.5) * 0.5 > 1.0) continue;
                    Material cur = cv.type(px, y + k, pz);
                    if (cur == null) continue;
                    boolean ok = cur.isAir() || Blocks.replaceable(cur) || (k <= 0 ? iceGround(cur) : iceish(cur));
                    if (!ok) continue;
                    double n2 = hash(s + 1, px, y + k, pz);
                    BlockData d;
                    if (k <= 0) d = packed;
                    else if (k == h) d = ice;
                    else if (d2 < 0.35) d = n2 < 0.4 ? blue : packed;
                    else d = n2 < 0.55 ? packed : ice;
                    cv.set(px, y + k, pz, d);
                }
            }
        }
    }

    // ------------------------------------------------------------------ pics de pierre moussus

    private static boolean rockGround(Material m) {
        String n = m.name();
        return n.equals("STONE") || n.equals("ANDESITE") || n.equals("DIORITE") || n.equals("GRANITE")
                || n.equals("TUFF") || n.equals("GRAVEL") || n.equals("COBBLESTONE") || n.equals("DIRT")
                || n.equals("GRASS_BLOCK") || n.equals("COARSE_DIRT") || n.equals("SAND");
    }

    static int stoneSpikes(Canvas cv, int chunkX, int chunkZ, long seed, double chance, List<String> biomes) {
        Random r = new Random(TreeGen.mix(seed * 23L + chunkX * 341873128712L + chunkZ * 132897987541L + 3131L));
        if (r.nextDouble() >= chance) return 0;
        int wanted = 1 + r.nextInt(3);
        int made = 0;
        for (int attempt = 0; attempt < 12 && made < wanted; attempt++) {
            int x = chunkX * 16 + 3 + r.nextInt(10), z = chunkZ * 16 + 3 + r.nextInt(10);
            int y = surface(cv, x, z);
            if (y == NONE) continue;
            Material g = cv.type(x, y, z);
            if (g == null || !rockGround(g)) continue;
            String biome = cv.biome(x, y, z);
            boolean okBiome = false;
            for (String b : biomes) {
                if (biome.contains(b)) okBiome = true;
            }
            if (!okBiome || !nearWater(cv, x, y, z, 9)) continue;
            stoneSpike(cv, r, seed, x, y, z, 5 + r.nextInt(12), 1.3 + r.nextDouble() * 1.6);
            made++;
        }
        return made;
    }

    private static void stoneSpike(Canvas cv, Random r, long seed, int x, int y, int z, int h, double baseR) {
        double ang = r.nextDouble() * Math.PI * 2;
        double lean = r.nextDouble() * 0.22;
        double lx = Math.cos(ang) * lean, lz = Math.sin(ang) * lean;
        long s = seed ^ (x * 73856093L) ^ (z * 19349663L);
        Map<Long, Integer> tops = new HashMap<>();
        Map<Long, int[]> where = new HashMap<>();
        BlockData stone = Material.STONE.createBlockData();
        BlockData andesite = Material.ANDESITE.createBlockData();
        BlockData cobble = Material.COBBLESTONE.createBlockData();
        BlockData mossyCobble = Material.MOSSY_COBBLESTONE.createBlockData();
        BlockData moss = Material.MOSS_BLOCK.createBlockData();
        for (int k = -1; k <= h; k++) {
            double t = Math.max(0, k) / (double) h;
            double rad = baseR * (1.0 - Math.pow(t, 1.15)) + 0.35;
            double ccx = x + lx * Math.max(0, k), ccz = z + lz * Math.max(0, k);
            int ir = (int) Math.ceil(rad) + 1;
            int cxi = (int) Math.round(ccx), czi = (int) Math.round(ccz);
            for (int dx = -ir; dx <= ir; dx++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    int px = cxi + dx, pz = czi + dz;
                    double ddx = px - ccx, ddz = pz - ccz;
                    double d2 = (ddx * ddx + ddz * ddz) / (rad * rad);
                    double n = hash(s, px, y + k, pz);
                    if (d2 + (n - 0.5) * 0.5 > 1.0) continue;
                    Material cur = cv.type(px, y + k, pz);
                    if (cur == null) continue;
                    boolean ok = cur.isAir() || Blocks.replaceable(cur) || (k <= 0 && rockGround(cur));
                    if (!ok) continue;
                    double n2 = hash(s + 1, px, y + k, pz);
                    BlockData d;
                    if (k >= h - 1 || (k > h * 0.4 && n2 < 0.12)) d = moss;
                    else if (n2 < 0.50) d = stone;
                    else if (n2 < 0.70) d = andesite;
                    else if (n2 < 0.85) d = cobble;
                    else d = mossyCobble;
                    cv.set(px, y + k, pz, d);
                    long key = ((long) px << 32) ^ (pz & 0xffffffffL);
                    Integer old = tops.get(key);
                    if (old == null || old < y + k) {
                        tops.put(key, y + k);
                        where.put(key, new int[] {px, pz});
                    }
                }
            }
        }
        // mousse au sommet de chaque colonne : bloc de mousse + tapis, et parfois une azalée
        BlockData carpet = mat("MOSS_CARPET");
        Material az = Material.getMaterial(r.nextBoolean() ? "AZALEA" : "FLOWERING_AZALEA");
        int best = Integer.MIN_VALUE;
        int[] bestPos = null;
        for (Map.Entry<Long, Integer> e : tops.entrySet()) {
            int[] p = where.get(e.getKey());
            int ty = e.getValue();
            if (ty > best) {
                best = ty;
                bestPos = p;
            }
            Material cur = cv.type(p[0], ty, p[1]);
            if (cur == null) continue;
            cv.set(p[0], ty, p[1], moss);
            Material above = cv.type(p[0], ty + 1, p[1]);
            if (above != null && above.isAir() && carpet != null && hash(s + 2, p[0], ty, p[1]) < 0.75) {
                cv.set(p[0], ty + 1, p[1], carpet);
            }
        }
        if (bestPos != null && az != null) {
            Material above = cv.type(bestPos[0], best + 1, bestPos[1]);
            if (above != null && (above.isAir() || Blocks.replaceable(above)) && r.nextDouble() < 0.6) {
                cv.set(bestPos[0], best + 1, bestPos[1], az.createBlockData());
            }
        }
    }

    private static BlockData mat(String name) {
        Material m = Material.getMaterial(name);
        return m == null ? null : m.createBlockData();
    }
}

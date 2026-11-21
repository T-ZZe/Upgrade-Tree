package fr.arbres;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Random;

/**
 * Grottes des biomes froids : stalactites de glace accrochées aux plafonds (et petites pointes de glace au sol).
 * Chaque chunk ne touche que ses propres colonnes : aucun problème de bord de chunk.
 */
final class CaveIceGen {
    private CaveIceGen() {}

    private static boolean iceType(Material m) {
        return m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.ICE;
    }

    /** Roche solide (plafond / sol de grotte). Les arbres, l'eau et la glace ne comptent pas. */
    private static boolean rock(Material m) {
        return m != null && m.isSolid() && !Blocks.liquid(m) && !Blocks.leaf(m) && !Blocks.anyLog(m) && !iceType(m);
    }

    private static boolean coldChunk(Canvas cv, int x0, int z0) {
        int[] ys = {-40, 0, 40, 80, 120};
        int[][] pos = {{4, 4}, {12, 4}, {4, 12}, {12, 12}, {8, 8}};
        for (int y : ys) {
            if (y < cv.minY() || y >= cv.maxY()) continue;
            for (int[] p : pos) {
                if (Replacer.snowy(cv.biome(x0 + p[0], y, z0 + p[1]))) return true;
            }
        }
        return false;
    }

    static int icicles(Canvas cv, int chunkX, int chunkZ, long seed, double density, boolean floor) {
        int x0 = chunkX * 16, z0 = chunkZ * 16, x1 = x0 + 15, z1 = z0 + 15;
        if (!coldChunk(cv, x0, z0)) return 0;
        Random r = new Random(TreeGen.mix(seed * 47L + chunkX * 341873128712L + chunkZ * 132897987541L + 9595L));
        BlockData packed = Material.PACKED_ICE.createBlockData();
        BlockData blue = Material.BLUE_ICE.createBlockData();
        int low = Math.max(cv.minY() + 4, -52);
        int made = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int top = Math.min(cv.maxY() - 2, cv.topY(x, z) + 1);
                int[] ceil = new int[6];
                int[] airBelow = new int[6];
                int[] flo = new int[6];
                int[] airAbove = new int[6];
                int nc = 0, nf = 0;
                int rockRun = 0;
                int airRun = 0;
                boolean underRoof = false;
                for (int y = top; y >= low; y--) {
                    Material m = cv.type(x, y, z);
                    if (m == null) break;
                    if (m.isAir()) {
                        if (airRun == 0) {
                            underRoof = rockRun >= 3;
                            if (underRoof && nc < ceil.length) {
                                int a = 0;
                                while (a < 10 && y - a >= low) {
                                    Material b = cv.type(x, y - a, z);
                                    if (b == null || !b.isAir()) break;
                                    a++;
                                }
                                if (a >= 3) {
                                    ceil[nc] = y + 1;
                                    airBelow[nc] = a;
                                    nc++;
                                }
                            }
                        }
                        airRun++;
                        rockRun = 0;
                    } else {
                        if (floor && underRoof && airRun >= 4 && rock(m) && nf < flo.length) {
                            flo[nf] = y;
                            airAbove[nf] = airRun;
                            nf++;
                        }
                        airRun = 0;
                        if (rock(m)) rockRun++;
                        else rockRun = 0;
                        if (!rock(m)) underRoof = false;
                    }
                }
                for (int i = 0; i < nc; i++) {
                    if (r.nextDouble() >= density) continue;
                    int yc = ceil[i];
                    if (!Replacer.snowy(cv.biome(x, yc, z))) continue;
                    int len = 1 + (int) (Math.pow(r.nextDouble(), 2.0) * 7);
                    len = Math.min(len, airBelow[i] - 2);
                    if (len < 1) continue;
                    hang(cv, r, packed, blue, x, yc, z, len, x0, z0, x1, z1);
                    made++;
                }
                for (int i = 0; i < nf; i++) {
                    if (r.nextDouble() >= density * 0.45) continue;
                    int yf = flo[i];
                    if (!Replacer.snowy(cv.biome(x, yf, z))) continue;
                    int len = 1 + (int) (Math.pow(r.nextDouble(), 2.0) * 4);
                    len = Math.min(len, airAbove[i] - 3);
                    if (len < 1) continue;
                    stand(cv, r, packed, blue, x, yf, z, len, x0, z0, x1, z1);
                    made++;
                }
            }
        }
        return made;
    }

    private static boolean emptyCell(Canvas cv, int x, int y, int z) {
        Material m = cv.type(x, y, z);
        return m != null && m.isAir();
    }

    /** Stalactite : colonne accrochée au plafond (yc = dernier bloc de roche), plus épaisse en haut quand elle est longue. */
    private static void hang(Canvas cv, Random r, BlockData packed, BlockData blue, int x, int yc, int z, int len,
                             int x0, int z0, int x1, int z1) {
        for (int k = 1; k <= len; k++) {
            cv.set(x, yc - k, z, (k < len && r.nextDouble() < 0.18) ? blue : packed);
        }
        if (len < 3) return;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] v : d) {
            int nx = x + v[0], nz = z + v[1];
            if (nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
            if (!rock(cv.type(nx, yc, nz))) continue;
            if (r.nextDouble() < 0.55 && emptyCell(cv, nx, yc - 1, nz)) {
                cv.set(nx, yc - 1, nz, packed);
                if (len >= 5 && r.nextDouble() < 0.4 && emptyCell(cv, nx, yc - 2, nz)) cv.set(nx, yc - 2, nz, packed);
            }
        }
    }

    /** Pointe de glace au sol (yf = bloc de roche du sol). */
    private static void stand(Canvas cv, Random r, BlockData packed, BlockData blue, int x, int yf, int z, int len,
                              int x0, int z0, int x1, int z1) {
        for (int k = 1; k <= len; k++) {
            cv.set(x, yf + k, z, (k < len && r.nextDouble() < 0.18) ? blue : packed);
        }
        if (len < 3) return;
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] v : d) {
            int nx = x + v[0], nz = z + v[1];
            if (nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
            if (!rock(cv.type(nx, yf, nz))) continue;
            if (r.nextDouble() < 0.5 && emptyCell(cv, nx, yf + 1, nz)) cv.set(nx, yf + 1, nz, packed);
        }
    }
}

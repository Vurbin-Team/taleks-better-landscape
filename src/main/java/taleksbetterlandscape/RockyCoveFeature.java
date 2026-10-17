package taleksbetterlandscape;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Rare crescent cove: a bay is cut into the coast, with a sand beach and cliff walls made of
 * andesite and packed mud. The wall face recedes in 3-block terraces and is bent by 3D noise,
 * so it reads as rock, not as a flat wall.
 * Writes stay inside the 3x3 chunk window around the origin chunk.
 */
public class RockyCoveFeature extends Feature<NoneFeatureConfiguration> {
	private static final int SEA = 63;
	private static final int PAD = 32;
	private static final int W = 64; // window: origin chunk +- 1 chunk
	private static final int WALL = 32; // wall ring width

	public RockyCoveFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		WorldGenLevel level = ctx.level();
		RandomSource rnd = ctx.random();
		int ox = ctx.origin().getX() & ~15;
		int oz = ctx.origin().getZ() & ~15;
		int wx = ox - PAD;
		int wz = oz - PAD;

		// snapshot of the terrain (edits below must not change what we measure)
		int[][] top = new int[W][W];
		boolean[][] water = new boolean[W][W];
		boolean[][] known = new boolean[W][W];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				int x = wx + i;
				int z = wz + j;
				if (!level.hasChunk(x >> 4, z >> 4)) continue;
				ChunkAccess c = level.getChunk(p.set(x, 0, z));
				int f = c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
				int s = c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				top[i][j] = f - 1;
				water[i][j] = s > f;
				known[i][j] = true;
			}
		}

		// find a shore point: land column with ocean water in a straight line within 10 blocks
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		double dx = 0;
		double dz = 0;
		int sx = 0;
		int sz = 0;
		boolean found = false;
		for (int t = 0; t < 40 && !found; t++) {
			int lx = ox + 3 + rnd.nextInt(10);
			int lz = oz + 3 + rnd.nextInt(10);
			int i = lx - wx;
			int j = lz - wz;
			if (!known[i][j] || water[i][j] || top[i][j] < SEA) continue;
			int[] dir = dirs[rnd.nextInt(8)];
			double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
			for (int s = 1; s <= 10; s++) {
				int ax = (int) Math.round(dir[0] / len * s);
				int az = (int) Math.round(dir[1] / len * s);
				int a = i + ax;
				int b = j + az;
				if (a < 0 || b < 0 || a >= W || b >= W || !known[a][b]) break;
				if (water[a][b]) {
					if (level.getBiome(p.set(lx + ax, SEA - 1, lz + az)).is(ShoreShelfFeature.OCEAN)) {
						dx = dir[0] / len;
						dz = dir[1] / len;
						sx = lx + ax;
						sz = lz + az;
						found = true;
					}
					break;
				}
			}
		}
		if (!found) return false;

		double rc = 9 + rnd.nextInt(5);
		double hmax = 12 + rnd.nextInt(10);
		double cx = sx - dx * 0.3 * rc;
		double cz = sz - dz * 0.3 * rc;
		long ns = rnd.nextLong();
		int reach = (int) Math.ceil(rc + WALL + 4);

		for (int x = (int) cx - reach; x <= (int) cx + reach; x++) {
			for (int z = (int) cz - reach; z <= (int) cz + reach; z++) {
				int i = x - wx;
				int j = z - wz;
				if (i < 0 || j < 0 || i >= W || j >= W || !known[i][j]) continue;
				double ddx = x - cx;
				double ddz = z - cz;
				double dist = Math.sqrt(ddx * ddx + ddz * ddz);
				if (dist >= reach) continue;
				double q = dist + 3.0 * noise(ns, x * 0.08, 0, z * 0.08);
				double inland = dist < 1e-6 ? 1 : -(ddx * dx + ddz * dz) / dist;
				int t0 = top[i][j];

				if (q < rc) {
					// cove interior: beach in the middle, shallow sand ring, 1-block steps down
					double t = q / rc;
					int yf = t < .25 ? 64 : t < .5 ? 63 : t < .62 ? 62 : t < .74 ? 61 : t < .86 ? 60 : t < .95 ? 59 : 58;
					if (water[i][j] && (t >= .75 || t0 >= yf)) continue;
					for (int y = yf + 1; y <= t0; y++) {
						level.setBlock(p.set(x, y, z), (y > SEA - 1 ? Blocks.AIR : Blocks.WATER).defaultBlockState(), 2);
					}
					sandOverMud(level, p, x, z, yf, t0);
				} else if (inland > -0.3 && q < rc + WALL + 4) {
					double w = Math.min(1, (q - rc) / WALL);
					double ht = SEA + hmax * Math.pow(1 - w, 1.3) * (0.75 + 0.5 * (noise(ns, x * 0.06, 7, z * 0.06) * 0.5 + 0.5));
					int yMax = (int) Math.ceil(Math.max(ht, t0));
					int yTop = Integer.MIN_VALUE;
					for (int y = SEA - 2; y <= yMax; y++) {
						double face = face(ns, rc, x, y, z);
						BlockState st = level.getBlockState(p.set(x, y, z));
						boolean air = st.isAir();
						boolean wat = st.is(Blocks.WATER);
						if (q >= face) {
							if (y > ht) continue;
							double e = q - face;
							if (e < 2.5) {
								level.setBlock(p, mix(ns, x, y, z), 2);
								yTop = y;
							} else if (air || wat) {
								level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
								yTop = y;
							} else if (!air) {
								yTop = y;
							}
						} else if (!air && !wat) {
							level.setBlock(p, (y > SEA - 1 ? Blocks.AIR : Blocks.WATER).defaultBlockState(), 2);
						}
					}
					// grass on the flat top of the rock mass, away from the cliff face
					if (yTop >= 64 && q - face(ns, rc, x, yTop, z) >= 2) {
						level.setBlock(p.set(x, yTop, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
						level.setBlock(p.set(x, yTop - 1, z), Blocks.DIRT.defaultBlockState(), 2);
					}
				}
			}
		}
		return true;
	}

	/** Radius of the cliff face at height y: recedes 1.5 blocks every 3 blocks up, bent by 3D noise. */
	private static double face(long ns, double rc, int x, int y, int z) {
		return rc + 1.5 * Math.floor(Math.max(0, y - SEA) / 3.0) + 1.8 * noise(ns, x * 0.15, y * 0.15, z * 0.15);
	}

	/** Andesite / packed mud: 3D noise plus horizontal strata every 2 layers. */
	private static BlockState mix(long ns, int x, int y, int z) {
		double m = noise(ns + 1, x * 0.3, y * 0.45, z * 0.3) + 0.7 * hash(ns + 2, 0, Math.floorDiv(y, 2), 0);
		return (m > 0 ? Blocks.ANDESITE : Blocks.PACKED_MUD).defaultBlockState();
	}

	private static void sandOverMud(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int top, int floor) {
		for (int y = Math.min(floor + 1, top - 3); y < top; y++) {
			level.setBlock(p.set(x, y, z), (y >= top - 3 ? Blocks.PACKED_MUD : Blocks.STONE).defaultBlockState(), 2);
		}
		level.setBlock(p.set(x, top, z), Blocks.SAND.defaultBlockState(), 2);
	}

	private static double hash(long s, int x, int y, int z) {
		long v = s ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
		v = v * 6364136223846793005L + 1442695040888963407L;
		v ^= v >>> 29;
		v *= 0xBF58476D1CE4E5B9L;
		v ^= v >>> 32;
		return ((v >>> 11) * 0x1.0p-53) * 2 - 1;
	}

	/** Smooth 3D value noise in [-1, 1]. */
	private static double noise(long s, double x, double y, double z) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c00 = lerp(hash(s, x0, y0, z0), hash(s, x0 + 1, y0, z0), fx);
		double c10 = lerp(hash(s, x0, y0 + 1, z0), hash(s, x0 + 1, y0 + 1, z0), fx);
		double c01 = lerp(hash(s, x0, y0, z0 + 1), hash(s, x0 + 1, y0, z0 + 1), fx);
		double c11 = lerp(hash(s, x0, y0 + 1, z0 + 1), hash(s, x0 + 1, y0 + 1, z0 + 1), fx);
		return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}

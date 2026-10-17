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
 * Rocky cove: a bay is bitten into the coast and opens towards the sea. Around the water there is a sand
 * beach, then a cliff face of granite and packed mud that rises smoothly to the surrounding terrain and
 * fades into it without a seam. Everything on the cove floor is replaced with sand.
 *
 * The geometry (where, which way, how big, the new height of every column) lives in {@link CoveMath};
 * this class only reads the terrain, asks the biome source and writes blocks.
 * Writes stay inside the 3x3 chunk window around the origin chunk.
 */
public class RockyCoveFeature extends Feature<NoneFeatureConfiguration> {
	private static final int SEA = CoveMath.SEA;
	private static final int PAD = 32;
	private static final int W = CoveMath.W;
	private static final int CLEAR_ABOVE = 28; // how far above the surface plants / trees are wiped
	private static final int MAX_Y = 300;

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

		// cheap early exit: the feature is registered in all overworld biomes, so skip chunks far from any ocean
		if (!oceanNearby(level, ox + 8, oz + 8)) return false;

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

		boolean[][] sea = CoveMath.seaMask(water, known, 300);
		int[][] edge = CoveMath.distToUnknown(known);
		CoveMath.Site site = CoveMath.findSite(wx, wz, PAD, PAD, water, known, sea, top, edge,
			bound -> rnd.nextInt(bound), (x, z, ax, az) -> oceanAhead(level, x, z, ax, az));
		if (site == null) return false;

		long ns = rnd.nextLong();
		double hc = 6 + rnd.nextInt(5);
		CoveMath.Plan plan = CoveMath.plan(ns, wx, wz, top, water, known, edge, site.cx, site.cz, site.ax, site.az, site.rb, hc);
		apply(level, ns, wx, wz, top, known, plan);
		return true;
	}

	private static void apply(WorldGenLevel level, long ns, int wx, int wz, int[][] top, boolean[][] known, CoveMath.Plan plan) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				byte kind = plan.kind[i][j];
				if (kind == CoveMath.NONE) continue;
				int x = wx + i;
				int z = wz + j;
				int s = plan.surface[i][j];
				int t0 = top[i][j];
				if (kind == CoveMath.SAND) {
					placeSand(level, p, x, z, s, t0);
				} else {
					placeTerrain(level, p, ns, x, z, s, t0, lowestNeighbour(plan, top, known, i, j, s));
				}
				clearAbove(level, p, x, z, s, Math.max(s, t0));
			}
		}
	}

	/** Sand on 3 layers of packed mud on stone: the same build-up as {@link ShoreShelfFeature}. */
	private static void placeSand(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int s, int t0) {
		for (int y = Math.min(t0 + 1, s - 3); y < s; y++) {
			level.setBlock(p.set(x, y, z), (y >= s - 3 ? Blocks.PACKED_MUD : Blocks.STONE).defaultBlockState(), 2);
		}
		level.setBlock(p.set(x, s, z), Blocks.SAND.defaultBlockState(), 2);
	}

	/** Cliff face (rock strata) where the surface drops steeply, grass over dirt everywhere else. */
	private static void placeTerrain(WorldGenLevel level, BlockPos.MutableBlockPos p, long ns, int x, int z, int s, int t0, int lowest) {
		if (s - lowest >= 2) {
			int yLo = Math.max(lowest, s - 8);
			if (s > t0) yLo = Math.min(yLo, t0 + 1);
			for (int y = yLo; y <= s; y++) {
				level.setBlock(p.set(x, y, z), mix(ns, x, y, z), 2);
			}
		} else {
			if (s > t0) {
				for (int y = t0 + 1; y <= s - 4; y++) {
					level.setBlock(p.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
				}
			}
			for (int y = s - 3; y < s; y++) {
				level.setBlock(p.set(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
			}
			level.setBlock(p.set(x, s, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
	}

	/** Wipes everything above the new surface (grass, flowers, bushes, trees, boulders), refills water below sea level. */
	private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos p, int x, int z, int s, int highest) {
		int yMax = Math.min(MAX_Y, highest + CLEAR_ABOVE);
		for (int y = s + 1; y <= yMax; y++) {
			p.set(x, y, z);
			BlockState st = level.getBlockState(p);
			if (y < SEA) {
				if (!st.is(Blocks.WATER)) level.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
			} else if (!st.isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static int lowestNeighbour(CoveMath.Plan plan, int[][] top, boolean[][] known, int i, int j, int self) {
		int low = self;
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : n4) {
			int a = i + d[0];
			int b = j + d[1];
			if (a < 0 || b < 0 || a >= W || b >= W || !known[a][b]) continue;
			low = Math.min(low, plan.kind[a][b] != CoveMath.NONE ? plan.surface[a][b] : top[a][b]);
		}
		return low;
	}

	/** Granite / packed mud: 3D noise plus horizontal strata every 2 layers. */
	private static BlockState mix(long ns, int x, int y, int z) {
		double m = CoveMath.noise(ns + 1, x * 0.3, y * 0.45, z * 0.3) + 0.7 * CoveMath.hash(ns + 2, 0, Math.floorDiv(y, 2), 0);
		return (m > 0 ? Blocks.GRANITE : Blocks.PACKED_MUD).defaultBlockState();
	}

	/**
	 * Biome lookup that works outside the generation region: WorldGenRegion.getBiome throws
	 * "Requested chunk unavailable" for chunks it does not hold, the uncached lookup asks the biome source directly.
	 */
	private static boolean isOceanAt(WorldGenLevel level, int x, int z) {
		return level.getUncachedNoiseBiome(x >> 2, (SEA - 1) >> 2, z >> 2).is(ShoreShelfFeature.OCEAN);
	}

	/** Is there an ocean biome seaward along the ray (the biome border lies offshore of the real coastline)? */
	private static boolean oceanAhead(WorldGenLevel level, int x, int z, double dx, double dz) {
		for (int s = 0; s <= 96; s += 8) {
			if (isOceanAt(level, x + (int) Math.round(dx * s), z + (int) Math.round(dz * s))) return true;
		}
		return false;
	}

	/** Cheap pre-check: any ocean biome in a ring of 32/64/96 blocks around the chunk centre? */
	private static boolean oceanNearby(WorldGenLevel level, int cx, int cz) {
		if (isOceanAt(level, cx, cz)) return true;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		for (int r = 32; r <= 96; r += 32) {
			for (int[] d : dirs) {
				if (isOceanAt(level, cx + d[0] * r, cz + d[1] * r)) return true;
			}
		}
		return false;
	}
}

package taleksbetterlandscape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Sand shelf around ocean shores: 3 steps, each 3 blocks wide (horizontal distance to the nearest
 * non-water column), each 1 block lower: tops at y=62, 61, 60. One sand layer on 3 packed_mud layers.
 */
public class ShoreShelfFeature extends Feature<NoneFeatureConfiguration> {
	static final TagKey<Biome> OCEAN = TagKey.create(Registries.BIOME, Identifier.withDefaultNamespace("is_ocean"));
	private static final int SEA = 63;
	private static final int STEP = 3;
	private static final int STEPS = 3;
	private static final int R = STEP * STEPS;
	private static final int W = 16 + 2 * R;

	public ShoreShelfFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
		WorldGenLevel level = ctx.level();
		int ox = ctx.origin().getX() & ~15;
		int oz = ctx.origin().getZ() & ~15;

		// snapshot first: edits below must not change the distances we measure
		int[][] floor = new int[W][W];
		boolean[][] water = new boolean[W][W];
		boolean[][] known = new boolean[W][W];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				int x = ox - R + i;
				int z = oz - R + j;
				if (!level.hasChunk(x >> 4, z >> 4)) continue;
				ChunkAccess c = level.getChunk(p.set(x, 0, z));
				int f = c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
				int s = c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				floor[i][j] = f - 1;
				water[i][j] = s > f;
				known[i][j] = true;
			}
		}

		for (int i = R; i < R + 16; i++) {
			for (int j = R; j < R + 16; j++) {
				if (!water[i][j]) continue;
				int x = ox - R + i;
				int z = oz - R + j;
				if (!level.getBiome(p.set(x, SEA - 1, z)).is(OCEAN)) continue;

				int best = R * R + 1;
				for (int di = -R; di <= R; di++) {
					for (int dj = -R; dj <= R; dj++) {
						int d2 = di * di + dj * dj;
						if (d2 >= best) continue;
						int a = i + di;
						int b = j + dj;
						if (a < 0 || b < 0 || a >= W || b >= W) continue;
						if (known[a][b] && !water[a][b]) best = d2;
					}
				}
				if (best > R * R) continue;

				int k = Math.min(STEPS, (int) Math.ceil(Math.sqrt(best) / STEP));
				int top = Math.max(floor[i][j], SEA - k);
				for (int y = Math.min(floor[i][j] + 1, top - 3); y < top; y++) {
					level.setBlock(p.set(x, y, z), (y >= top - 3 ? Blocks.PACKED_MUD : Blocks.STONE).defaultBlockState(), 2);
				}
				level.setBlock(p.set(x, top, z), Blocks.SAND.defaultBlockState(), 2);
			}
		}
		return true;
	}
}

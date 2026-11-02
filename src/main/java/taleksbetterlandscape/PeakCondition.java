package taleksbetterlandscape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.worldgen.lithostitched.duck.ContextAccessor;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
import taleksbetterlandscape.PeakMath.Mode;
import taleksbetterlandscape.PeakMath.Relief;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Surface rule condition "taleks-better-landscape:peak": is this column in [min, max] blocks below its
 * mountain's peak (mode "drop"), or in [min, max] of the way from the foot to the ice line (mode "fraction")?
 * The maths lives in {@link PeakMath}; this class only feeds it heights from the vanilla noise router.
 *
 * Heights come from the router's preliminary_surface_level: a smooth estimate without the final jaggedness.
 * Peak and foot are measured once per 4x4 block cell, on rings around the cell centre.
 * ponytail: nothing is cached across chunks, so cells on a chunk border are measured twice.
 */
public record PeakCondition(Mode mode, double min, double max, double iceOffset, double minRelief, int radius)
        implements SurfaceRules.ConditionSource {

    private static final int CELL = 4;

    private static final Codec<Mode> MODE = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Mode.parse(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }, Mode::id);

    public static final KeyDispatchDataCodec<PeakCondition> CODEC = KeyDispatchDataCodec.of(
        RecordCodecBuilder.<PeakCondition>mapCodec(i -> i.group(
            MODE.fieldOf("mode").forGetter(PeakCondition::mode),
            Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(PeakCondition::min),
            Codec.DOUBLE.optionalFieldOf("max", 1.0e9).forGetter(PeakCondition::max),
            Codec.DOUBLE.optionalFieldOf("ice_offset", 15.0).forGetter(PeakCondition::iceOffset),
            Codec.DOUBLE.optionalFieldOf("min_relief", 32.0).forGetter(PeakCondition::minRelief),
            Codec.intRange(3, 256).optionalFieldOf("radius", 48).forGetter(PeakCondition::radius)
        ).apply(i, PeakCondition::new)).validate(c -> c.min <= c.max
            ? DataResult.success(c)
            : DataResult.error(() -> "peak: min must be <= max, got " + c.min + " > " + c.max)));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return new Condition(context, this);
    }

    private record CellKey(int radius, int cellX, int cellZ) {
    }

    /** What every peak condition of one chunk shares, so a rule with many peak checks measures each spot once. */
    private static final class Shared {
        final double[] own = new double[256];
        final Map<CellKey, Relief> cells = new HashMap<>();

        Shared() {
            Arrays.fill(own, Double.NaN);
        }
    }

    // One Context per chunk and rule, dropped with the Context. Chunks are built on several threads.
    private static final Map<SurfaceRules.Context, Shared> SHARED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private static double height(DensityFunction f, int x, int z) {
        return f.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    private static final class Condition extends SurfaceRules.LazyXZCondition {
        private final PeakCondition source;
        private final ContextAccessor ctx;
        private final Shared shared;

        private Condition(SurfaceRules.Context context, PeakCondition source) {
            super(context);
            this.source = source;
            this.ctx = (ContextAccessor) (Object) context;
            this.shared = SHARED.computeIfAbsent(context, c -> new Shared());
        }

        @Override
        public boolean compute() {
            try {
                DensityFunction f = this.ctx.lithostitched$getRandomState().router().preliminarySurfaceLevel();
                int x = this.ctx.getPos().getX();
                int z = this.ctx.getPos().getZ();
                int slot = (x & 15) + (z & 15) * 16;
                double own = this.shared.own[slot];
                if (Double.isNaN(own)) {
                    own = height(f, x, z);
                    this.shared.own[slot] = own;
                }
                Relief relief = this.shared.cells.computeIfAbsent(
                    new CellKey(this.source.radius, Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)),
                    k -> measure(f, k));
                return PeakMath.matches(relief, own, this.source.mode, this.source.min, this.source.max,
                    this.source.iceOffset, this.source.minRelief);
            } catch (RuntimeException e) {
                // leave the surface vanilla instead of crashing world generation
                if (WARNED.compareAndSet(false, true)) {
                    TalekSBetterLandscape.LOGGER.error("peak condition failed, mountain blocks are skipped", e);
                }
                return false;
            }
        }

        private static Relief measure(DensityFunction f, CellKey k) {
            int cx = k.cellX() * CELL + CELL / 2;
            int cz = k.cellZ() * CELL + CELL / 2;
            int[][] offsets = PeakMath.offsets(k.radius());
            double[] heights = new double[offsets.length + 1];
            heights[0] = height(f, cx, cz);
            for (int i = 0; i < offsets.length; i++) {
                heights[i + 1] = height(f, cx + offsets[i][0], cz + offsets[i][1]);
            }
            return Relief.of(heights);
        }
    }
}

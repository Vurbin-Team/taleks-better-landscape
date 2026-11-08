package taleksbetterlandscape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
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
 * MaterialRuleContext.densitySamplers and .system are opened by taleks-better-landscape.accesswidener, so this
 * class does not depend on Lithostitched internals.
 * Heights come from the material system's preliminary surface function (chunk_surface_level): a smooth estimate without the final jaggedness.
 * Peak and foot are measured once per 4x4 block cell, on rings around the cell centre.
 * ponytail: nothing is cached across chunks, so cells on a chunk border are measured twice.
 */
public record PeakCondition(Mode mode, double min, double max, double iceOffset, double minRelief, int radius)
        implements MaterialCondition {

    private static final int CELL = 4;

    private static final Codec<Mode> MODE = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Mode.parse(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }, Mode::id);

    public static final MapCodec<PeakCondition> CODEC =
        RecordCodecBuilder.<PeakCondition>mapCodec(i -> i.group(
            MODE.fieldOf("mode").forGetter(PeakCondition::mode),
            Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(PeakCondition::min),
            Codec.DOUBLE.optionalFieldOf("max", 1.0e9).forGetter(PeakCondition::max),
            Codec.DOUBLE.optionalFieldOf("ice_offset", 15.0).forGetter(PeakCondition::iceOffset),
            Codec.DOUBLE.optionalFieldOf("min_relief", 32.0).forGetter(PeakCondition::minRelief),
            Codec.intRange(3, 256).optionalFieldOf("radius", 48).forGetter(PeakCondition::radius)
        ).apply(i, PeakCondition::new)).validate(c -> c.min <= c.max
            ? DataResult.success(c)
            : DataResult.error(() -> "peak: min must be <= max, got " + c.min + " > " + c.max));

    @Override
    public MapCodec<? extends MaterialCondition> codec() {
        return CODEC;
    }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
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
    private static final Map<MaterialRuleContext, Shared> SHARED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    /** Surface height estimate at a column; backed by the context's density samplers. */
    @FunctionalInterface
    private interface Height {
        double at(int x, int z);
    }

    private static final class Condition extends MaterialRuleContext.LazyXZCondition {
        private final PeakCondition source;
        private final Shared shared;

        private Condition(MaterialRuleContext context, PeakCondition source) {
            super(context);
            this.source = source;
            this.shared = SHARED.computeIfAbsent(context, c -> new Shared());
        }

        @Override
        public boolean compute() {
            try {
                // densitySamplers and system are opened by the access widener
                var samplers = this.context.densitySamplers;
                var surface = this.context.system.preliminarySurfaceFunction();
                Height f = (px, pz) -> samplers.sampleValue(surface, px, 0, pz);
                int x = this.context.blockX();
                int z = this.context.blockZ();
                int slot = (x & 15) + (z & 15) * 16;
                double own = this.shared.own[slot];
                if (Double.isNaN(own)) {
                    own = f.at(x, z);
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

        private static Relief measure(Height f, CellKey k) {
            int cx = k.cellX() * CELL + CELL / 2;
            int cz = k.cellZ() * CELL + CELL / 2;
            int[][] offsets = PeakMath.offsets(k.radius());
            double[] heights = new double[offsets.length + 1];
            heights[0] = f.at(cx, cz);
            for (int i = 0; i < offsets.length; i++) {
                heights[i + 1] = f.at(cx + offsets[i][0], cz + offsets[i][1]);
            }
            return Relief.of(heights);
        }
    }
}

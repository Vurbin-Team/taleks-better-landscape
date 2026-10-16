package taleksbetterlandscape.mc;

import taleksbetterlandscape.core.Features;
import taleksbetterlandscape.core.TerrainConfig;
import taleksbetterlandscape.core.TerrainFeature;
import java.lang.reflect.RecordComponent;
import java.util.List;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * Adds the features' density on top of vanilla's final density. This is the ONLY class that bridges
 * Minecraft's density-function API to the pure-Java features, so a Minecraft API change touches
 * just this file and the mixin.
 *
 * Runtime-only: it is never written to disk, so codec() is unsupported.
 */
public record TerrainDensity(
        DensityFunction inner,
        DensityFunction continents,
        DensityFunction erosion,
        DensityFunction ridges,
        int seaLevel,
        List<TerrainFeature> features) implements DensityFunction {

    /** Called by the mixin for every noise-router lookup. Returns the router untouched when nothing applies. */
    public static NoiseRouter wrap(int seaLevel, NoiseRouter router) {
        TerrainConfig config = FantasyTerrainMod.config();
        // ponytail: sea level 63 = overworld, large biomes and amplified. Nether (32) and End (0) skipped.
        if (config == null || seaLevel != 63) {
            return router;
        }
        List<TerrainFeature> features = Features.build(config);
        if (features.isEmpty()) {
            return router;
        }
        DensityFunction shaped = new TerrainDensity(router.finalDensity(), router.continents(),
                router.erosion(), router.ridges(), seaLevel, features);
        return withComponent(router, "finalDensity", shaped);
    }

    /**
     * Copy a record with one component replaced, via reflection on the canonical constructor, so we
     * do not hard-code NoiseRouter's field list (Mojang adds and removes fields between versions).
     */
    private static NoiseRouter withComponent(NoiseRouter router, String name, DensityFunction value) {
        try {
            RecordComponent[] parts = NoiseRouter.class.getRecordComponents();
            Class<?>[] types = new Class<?>[parts.length];
            Object[] args = new Object[parts.length];
            boolean found = false;
            for (int i = 0; i < parts.length; i++) {
                types[i] = parts[i].getType();
                if (parts[i].getName().equals(name)) {
                    args[i] = value;
                    found = true;
                } else {
                    args[i] = parts[i].getAccessor().invoke(router);
                }
            }
            if (!found) {
                FantasyTerrainMod.LOG.error("NoiseRouter has no '{}' component; terrain left vanilla", name);
                return router;
            }
            return NoiseRouter.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            FantasyTerrainMod.LOG.error("cannot rebuild NoiseRouter; terrain left vanilla", e);
            return router;
        }
    }

    @Override
    public double compute(FunctionContext context) {
        return inner.compute(context) + extra(context);
    }

    @Override
    public void fillArray(double[] out, ContextProvider provider) {
        inner.fillArray(out, provider);
        for (int i = 0; i < out.length; i++) {
            out[i] += extra(provider.forIndex(i));
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        // Map the helper functions too: they hold noise that the game must wire up per world.
        return visitor.apply(new TerrainDensity(inner.mapAll(visitor), continents.mapAll(visitor),
                erosion.mapAll(visitor), ridges.mapAll(visitor), seaLevel, features));
    }

    @Override
    public double minValue() {
        double min = inner.minValue();
        for (TerrainFeature f : features) {
            min += f.minDensity();
        }
        return min;
    }

    @Override
    public double maxValue() {
        double max = inner.maxValue();
        for (TerrainFeature f : features) {
            max += f.maxDensity();
        }
        return max;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException("TerrainDensity is runtime-only");
    }

    private double extra(FunctionContext c) {
        TerrainFeature.Sample sample = new TerrainFeature.Sample() {
            public int x() { return c.blockX(); }
            public int y() { return c.blockY(); }
            public int z() { return c.blockZ(); }
            public double continents() { return TerrainDensity.this.continents.compute(c); }
            public double erosion() { return TerrainDensity.this.erosion.compute(c); }
            public double ridges() { return TerrainDensity.this.ridges.compute(c); }
            public int seaLevel() { return TerrainDensity.this.seaLevel; }
        };
        double sum = 0;
        for (TerrainFeature f : features) {
            sum += f.density(sample);
        }
        return sum;
    }
}

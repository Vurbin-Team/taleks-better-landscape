package taleksbetterlandscape.core;

import static taleksbetterlandscape.core.TerrainFeature.clamp01;
import static taleksbetterlandscape.core.TerrainFeature.smoothstep;

/**
 * Rock towers in shallow water near the coast (the lone spire in the reference picture).
 * Where a 2D noise peaks inside the coastal band, add density in a column up to a top that grows
 * with the noise strength, so towers taper to a point. Never adds negative density, so it cannot
 * carve real coastline.
 */
public final class SeaStacks implements TerrainFeature {
    private final double height;
    private final double threshold;
    private final double edge;
    private final double scale;
    private final double coastMin;
    private final double coastMax;
    private final double strength;
    private final double band;
    private final Noise2D noise;

    SeaStacks(double height, double threshold, double edge, double scale,
              double coastMin, double coastMax, double strength, double band, long seed) {
        this.height = height;
        this.threshold = threshold;
        this.edge = edge;
        this.scale = scale;
        this.coastMin = coastMin;
        this.coastMax = coastMax;
        this.strength = strength;
        this.band = band;
        this.noise = new Noise2D(seed);
    }

    public static SeaStacks fromConfig(TerrainConfig c) {
        double min = c.number("sea_stacks.coast_min", -1, 1);
        double max = c.number("sea_stacks.coast_max", -1, 1);
        if (min >= max) {
            throw new IllegalArgumentException("sea_stacks.coast_min must be < sea_stacks.coast_max");
        }
        return new SeaStacks(
                c.number("sea_stacks.height", 1, 200),
                c.number("sea_stacks.threshold", 0, 1),
                c.number("sea_stacks.edge", 0.01, 1),
                c.number("sea_stacks.scale", 0.0005, 1),
                min, max,
                c.number("sea_stacks.strength", 0.1, 20),
                c.number("sea_stacks.band", 1, 64),
                c.integer("sea_stacks.seed"));
    }

    @Override
    public double density(Sample s) {
        double fade = (coastMax - coastMin) / 4;
        double c = s.continents();
        double zone = smoothstep(coastMin, coastMin + fade, c) * (1 - smoothstep(coastMax - fade, coastMax, c));
        if (zone <= 0) {
            return 0;
        }
        double n = noise.sample(s.x() * scale, s.z() * scale);
        double st = zone * smoothstep(threshold, threshold + edge, n);
        if (st <= 0) {
            return 0;
        }
        double top = s.seaLevel() + height * st;
        return strength * st * clamp01((top - s.y()) / band);
    }

    @Override
    public double maxDensity() {
        return strength;
    }
}

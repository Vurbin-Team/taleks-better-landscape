package taleksbetterlandscape.core;

/**
 * One terrain-shaping rule. Returns extra density at a block: positive makes it more solid,
 * zero leaves vanilla alone. Pure Java on purpose: no Minecraft types here, so features are
 * testable without the game. To add a feature: implement this, then Features.register(id, factory).
 */
public interface TerrainFeature {

    /** Extra density at the sampled block. Must stay within [minDensity(), maxDensity()]. */
    double density(Sample s);

    /** Lower bound of density(). Minecraft uses it to skip work, so it must be honest. */
    default double minDensity() {
        return 0;
    }

    /** Upper bound of density(). */
    double maxDensity();

    /** What a feature may look at. The game fills this in from the vanilla noise router. */
    interface Sample {
        int x();

        int y();

        int z();

        /** Vanilla continentalness, about -1 (deep ocean) .. 1 (far inland). */
        double continents();

        double erosion();

        double ridges();

        int seaLevel();
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3 - 2 * t);
    }
}

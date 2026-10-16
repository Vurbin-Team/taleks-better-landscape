package taleksbetterlandscape.core;

/**
 * Tiny seeded 2D value noise, output in [0, 1].
 * ponytail: the seed comes from config, not from the world seed, so tower layout is the same in
 * every world (continentalness still differs per world). Pass the world seed in if that matters.
 */
public final class Noise2D {
    private final long seed;

    public Noise2D(long seed) {
        this.seed = seed;
    }

    public double sample(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = lerp(corner(x0, z0), corner(x0 + 1, z0), fx);
        double b = lerp(corner(x0, z0 + 1), corner(x0 + 1, z0 + 1), fx);
        return lerp(a, b, fz);
    }

    private double corner(int x, int z) {
        long h = seed ^ (x * 0x632BE59BD9B4E019L) ^ (z * 0x9E3779B97F4A7C15L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}

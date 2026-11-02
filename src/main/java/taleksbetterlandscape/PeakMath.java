package taleksbetterlandscape;

/**
 * Where a column sits on its mountain: how far below the local peak, and how far up from the local foot.
 * Pure Java, no Minecraft types, so it can be checked with plain javac (see PeakMathCheck).
 * All heights must come from the SAME height function, otherwise peak and own are biased against each other.
 */
public final class PeakMath {
    private static final int RINGS = 3;
    private static final int DIRECTIONS = 16;

    private PeakMath() {
    }

    public enum Mode {
        /** Blocks below the local peak. */
        DROP,
        /** 0 at the foot .. 1 at the ice line (peak minus ice_offset). */
        FRACTION;

        public static Mode parse(String s) {
            return switch (s) {
                case "drop" -> DROP;
                case "fraction" -> FRACTION;
                default -> throw new IllegalArgumentException("peak mode must be 'drop' or 'fraction', got '" + s + "'");
            };
        }

        public String id() {
            return this == DROP ? "drop" : "fraction";
        }
    }

    /** Highest and lowest height seen around a spot. */
    public record Relief(double peak, double foot) {
        public static Relief of(double[] heights) {
            double hi = Double.NEGATIVE_INFINITY;
            double lo = Double.POSITIVE_INFINITY;
            for (double h : heights) {
                hi = Math.max(hi, h);
                lo = Math.min(lo, h);
            }
            return new Relief(hi, lo);
        }

        /** Blocks below the peak. The column itself counts as a sample, so this is never negative. */
        public double drop(double own) {
            return Math.max(peak, own) - own;
        }

        /** 0 at the foot, 1 at the ice line, clamped. The column itself counts as a sample. */
        public double fraction(double own, double iceOffset) {
            double lo = Math.min(foot, own);
            double hi = Math.max(peak, own) - iceOffset;
            return hi <= lo ? 1 : Math.min(1, Math.max(0, (own - lo) / (hi - lo)));
        }

        /** Too low to be a mountain: snow, ice and the gradient are skipped. */
        public boolean tooLow(double own, double minRelief) {
            return Math.max(peak, own) - Math.min(foot, own) < minRelief;
        }
    }

    /**
     * The one decision the surface rule asks: does this column fall in [min, max] for the chosen mode?
     * Both ends are inclusive.
     */
    public static boolean matches(Relief r, double own, Mode mode, double min, double max,
                                  double iceOffset, double minRelief) {
        if (r.tooLow(own, minRelief)) {
            return false;
        }
        double v = mode == Mode.DROP ? r.drop(own) : r.fraction(own, iceOffset);
        return v >= min && v <= max;
    }

    /**
     * Sample offsets {dx, dz} around a point: RINGS rings out to radius, DIRECTIONS points on each.
     * ponytail: one set of rings, so a peak that falls between two rings can be missed by a few blocks.
     */
    public static int[][] offsets(int radius) {
        int[][] out = new int[RINGS * DIRECTIONS][];
        int n = 0;
        for (int ring = 1; ring <= RINGS; ring++) {
            double r = radius * (double) ring / RINGS;
            for (int d = 0; d < DIRECTIONS; d++) {
                double a = 2 * Math.PI * d / DIRECTIONS;
                out[n++] = new int[] {(int) Math.round(r * Math.cos(a)), (int) Math.round(r * Math.sin(a))};
            }
        }
        return out;
    }
}

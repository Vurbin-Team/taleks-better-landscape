package taleksbetterlandscape;

import taleksbetterlandscape.PeakMath.Mode;
import taleksbetterlandscape.PeakMath.Relief;

/**
 * One runnable check for PeakMath (pure Java, no Minecraft).
 * Run: javac -d /tmp/pm src/main/java/taleksbetterlandscape/PeakMath.java src/test/java/taleksbetterlandscape/PeakMathCheck.java
 *      java -cp /tmp/pm taleksbetterlandscape.PeakMathCheck
 */
public final class PeakMathCheck {
    static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }

    static boolean drop(Relief r, double own, double min, double max) {
        return PeakMath.matches(r, own, Mode.DROP, min, max, 15, 32);
    }

    static boolean frac(Relief r, double own, double min, double max) {
        return PeakMath.matches(r, own, Mode.FRACTION, min, max, 15, 32);
    }

    public static void main(String[] args) {
        // sample points: 3 rings x 16 directions, never farther than the radius
        int[][] o = PeakMath.offsets(48);
        check(o.length == 48, "expected 48 offsets, got " + o.length);
        boolean east = false;
        for (int[] p : o) {
            check(Math.hypot(p[0], p[1]) <= 48.5, "offset too far: " + p[0] + "," + p[1]);
            east |= p[0] == 48 && p[1] == 0;
        }
        check(east, "outer ring must reach (48,0)");

        Relief r = Relief.of(new double[] {10, 90, 40});
        check(r.peak() == 90 && r.foot() == 10, "peak/foot from samples");

        // drop: blocks below the peak, never negative
        check(r.drop(80) == 10, "drop 80");
        check(r.drop(95) == 0, "own above peak gives 0");

        // snow zone = top 10 blocks, ice = next 5 (boundaries inclusive)
        Relief m = Relief.of(new double[] {10, 110}); // relief 100
        check(drop(m, 110, 0, 10), "summit is snow");
        check(drop(m, 100, 0, 10), "10 below summit still snow");
        check(!drop(m, 99, 0, 10), "11 below summit is not snow");
        check(drop(m, 99, 10, 15), "11 below summit is ice");
        check(drop(m, 95, 10, 15), "15 below summit is ice");
        check(!drop(m, 94, 10, 15), "16 below summit is not ice");

        // low hill: relief 20 < min_relief 32, nothing applies
        Relief hill = Relief.of(new double[] {70, 90});
        check(!drop(hill, 90, 0, 10), "low hill gets no snow");
        check(!frac(hill, 80, 0, 1), "low hill gets no gradient");
        // relief exactly 32 counts as a mountain
        check(drop(Relief.of(new double[] {58, 90}), 90, 0, 10), "relief 32 is enough");

        // gradient fraction: foot 10 .. ice line 110-15=95
        check(frac(m, 10, 0, 0), "foot is fraction 0");
        check(frac(m, 52.5, 0.5, 0.5), "middle is fraction 0.5");
        check(frac(m, 95, 1, 1), "ice line is fraction 1");
        check(frac(m, 105, 1, 1), "above ice line clamps to 1");
        check(frac(m, 5, 0, 0), "below foot clamps to 0");
        check(frac(m, 35, 0.25, 0.5), "35 is inside [0.25, 0.5]");
        check(!frac(m, 35, 0.5, 1), "35 is not inside [0.5, 1]");

        // own point outside the sampled cell still behaves (peak/foot widen to include it)
        check(Relief.of(new double[] {10, 110}).drop(120) == 0, "own above sampled peak");

        // mode names used in JSON
        check(Mode.parse("drop") == Mode.DROP && Mode.parse("fraction") == Mode.FRACTION, "mode names");
        try {
            Mode.parse("nope");
            throw new AssertionError("unknown mode must fail");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("nope"), "error names the bad value");
        }
        System.out.println("PeakMathCheck OK");
    }
}

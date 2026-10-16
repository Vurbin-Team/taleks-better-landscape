package fantasyterrain.core;

import taleksbetterlandscape.core.Features;
import taleksbetterlandscape.core.TerrainConfig;
import taleksbetterlandscape.core.TerrainFeature;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/** One runnable check for the pure-Java core. Run: gradlew selfCheck (or plain java). Throws on failure. */
public final class CoreSelfCheck {
    static final String DEFAULTS = """
            features=sea_stacks
            sea_stacks.height=28
            sea_stacks.threshold=0.72
            sea_stacks.edge=0.08
            sea_stacks.scale=0.012
            sea_stacks.coast_min=-0.30
            sea_stacks.coast_max=-0.12
            sea_stacks.strength=3.0
            sea_stacks.band=6
            sea_stacks.seed=1337
            """;

    record S(int x, int y, int z, double continents, double erosion, double ridges, int seaLevel)
            implements TerrainFeature.Sample {
    }

    static List<TerrainFeature> build(String userText) throws IOException {
        return Features.build(TerrainConfig.parse(new StringReader(DEFAULTS), new StringReader(userText)));
    }

    static void expectFail(String userText, String messagePart) throws IOException {
        try {
            build(userText);
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains(messagePart),
                    "wrong message for '" + userText + "': " + e.getMessage());
            return;
        }
        throw new AssertionError("expected failure for: " + userText);
    }

    static void check(boolean ok, String msg) {
        if (!ok) {
            throw new AssertionError(msg);
        }
    }

    public static void main(String[] args) throws IOException {
        // config: defaults load, empty list = vanilla, typos and bad values fail loudly
        check(build("").size() == 1, "defaults should enable sea_stacks");
        check(build("features=").isEmpty(), "empty features must mean vanilla");
        expectFail("features=typo", "unknown feature 'typo'; known: sea_stacks");
        expectFail("sea_stacks.height=-5", "sea_stacks.height");
        expectFail("sea_stacks.height=abc", "must be a number");
        expectFail("sea_stacks.coast_min=0.5", "coast_min");

        // behaviour: find a strong tower spot, then check shape and bounds
        TerrainFeature f = build("").get(0);
        double max = f.maxDensity();
        int bx = 0;
        int bz = 0;
        double best = 0;
        for (int x = 0; x < 6000; x += 3) {
            for (int z = 0; z < 6000; z += 3) {
                double d = f.density(new S(x, 40, z, -0.21, 0, 0, 63));
                check(d >= f.minDensity() && d <= max + 1e-9, "density out of bounds: " + d);
                if (d > best) {
                    best = d;
                    bx = x;
                    bz = z;
                }
            }
        }
        check(best > 0.9 * max, "no strong tower found in scan; defaults too rare? best=" + best);
        check(f.density(new S(bx, 63 + 10, bz, -0.21, 0, 0, 63)) > 0, "tower should be solid above sea level");
        check(f.density(new S(bx, 63 + 28 + 10, bz, -0.21, 0, 0, 63)) == 0, "nothing above tower top");
        check(f.density(new S(bx, 63 + 10, bz, 0.5, 0, 0, 63)) == 0, "no towers inland");
        check(f.density(new S(bx, 63 + 10, bz, -0.9, 0, 0, 63)) == 0, "no towers in deep ocean");
        check(f.density(new S(bx, 70, bz, -0.21, 0, 0, 63)) == f.density(new S(bx, 70, bz, -0.21, 0, 0, 63)),
                "must be deterministic");
        System.out.println("CoreSelfCheck OK (strongest tower at " + bx + "," + bz + ")");
    }
}

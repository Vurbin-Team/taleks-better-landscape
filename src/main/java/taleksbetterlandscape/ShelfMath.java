package taleksbetterlandscape;

/**
 * Pure geometry for {@link ShoreShelfFeature}. No Minecraft types here, so it can be tested offline.
 *
 * The shelf is a sand cap on a short "pedestal" (packed mud, a little stone). The cap height follows the
 * ideal shelf profile (3 steps, 1 block lower each), but the pedestal may never be thicker than {@link #MAX_FILL}
 * above the real sea floor, and it thins out to 1 block at the outer edge of the shelf. So over a steep seabed
 * the shelf turns into a thin skin that copies the relief instead of a flat-sided column standing on it.
 */
final class ShelfMath {
	static final int SEA = 63;
	/** Horizontal width of one shelf step, in blocks. */
	static final int STEP = 3;
	static final int STEPS = 3;
	/** Shelf radius: distance from the shore at which the shelf ends. */
	static final int R = STEP * STEPS;
	/** Most blocks the shelf may rise above the natural floor: sand + 3 packed mud + 1 stone. */
	static final int MAX_FILL = 5;

	private ShelfMath() {
	}

	/** Ideal (flat) shelf top at a horizontal distance of sqrt(distSq) from the nearest land: 62, 61, 60. */
	static int profile(int distSq) {
		int k = Math.min(STEPS, (int) Math.ceil(Math.sqrt(distSq) / STEP));
		return SEA - k;
	}

	/** How far above the floor the shelf may stand: MAX_FILL inside, tapering to 1 at the outer edge. */
	static int maxFill(int distSq) {
		double d = Math.sqrt(distSq);
		return Math.max(1, Math.min(MAX_FILL, 1 + (int) Math.floor(R - d + 1e-6)));
	}

	/**
	 * Height of the sand layer for a water column.
	 *
	 * @param floor  y of the highest solid block of the column before the feature
	 * @param distSq squared horizontal distance to the nearest land column (1..R*R)
	 * @return new y of the sand block; never below {@code floor}
	 */
	static int top(int floor, int distSq) {
		int ideal = profile(distSq);
		if (ideal <= floor) return floor;
		return Math.min(ideal, floor + maxFill(distSq));
	}
}

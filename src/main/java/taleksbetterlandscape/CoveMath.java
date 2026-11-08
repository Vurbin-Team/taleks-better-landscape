package taleksbetterlandscape;

import java.util.function.IntUnaryOperator;

/**
 * Pure geometry for {@link RockyCoveFeature}. No Minecraft types here, so it can be tested offline.
 *
 * All arrays are indexed [i][j] in "window" coordinates: world x = wx + i, world z = wz + j.
 */
final class CoveMath {
	static final int SEA = 63;
	static final int W = 64;
	/** Top of the flat cove sand: one block below sea level, flush with the water surface. */
	static final int BEACH_Y = SEA - 1;

	/** Column kinds of a {@link Plan}. */
	static final byte NONE = 0;
	static final byte SAND = 1;
	static final byte TERRAIN = 2;

	private CoveMath() {
	}

	/** The result of {@link #plan}: new surface height and kind for every column the cove touches. */
	static final class Plan {
		final int[][] surface = new int[W][W];
		final byte[][] kind = new byte[W][W];
	}

	// ------------------------------------------------------------------ analysis of the snapshot

	/** Water columns that belong to a large water body. Ponds and puddles are not "sea". */
	static boolean[][] seaMask(boolean[][] water, boolean[][] known, int minArea) {
		boolean[][] sea = new boolean[W][W];
		boolean[][] seen = new boolean[W][W];
		int[] qi = new int[W * W];
		int[] qj = new int[W * W];
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				if (!known[i][j] || !water[i][j] || seen[i][j]) continue;
				int head = 0;
				int tail = 0;
				qi[tail] = i;
				qj[tail++] = j;
				seen[i][j] = true;
				while (head < tail) {
					int ci = qi[head];
					int cj = qj[head++];
					for (int[] d : n4) {
						int a = ci + d[0];
						int b = cj + d[1];
						if (a < 0 || b < 0 || a >= W || b >= W) continue;
						if (!known[a][b] || !water[a][b] || seen[a][b]) continue;
						seen[a][b] = true;
						qi[tail] = a;
						qj[tail++] = b;
					}
				}
				if (tail >= minArea) {
					for (int k = 0; k < tail; k++) sea[qi[k]][qj[k]] = true;
				}
			}
		}
		return sea;
	}

	/** Land flood fill: a landmass with fewer than minArea columns in the window is an islet. */
	static boolean isMainland(boolean[][] water, boolean[][] known, int si, int sj, int minArea) {
		boolean[][] seen = new boolean[W][W];
		int[] qi = new int[W * W];
		int[] qj = new int[W * W];
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		int head = 0;
		int tail = 0;
		qi[tail] = si;
		qj[tail++] = sj;
		seen[si][sj] = true;
		while (head < tail) {
			int ci = qi[head];
			int cj = qj[head++];
			if (head >= minArea) return true;
			for (int[] d : n4) {
				int a = ci + d[0];
				int b = cj + d[1];
				if (a < 0 || b < 0 || a >= W || b >= W) continue;
				if (!known[a][b] || water[a][b] || seen[a][b]) continue;
				seen[a][b] = true;
				qi[tail] = a;
				qj[tail++] = b;
			}
		}
		return false;
	}

	/**
	 * Unit vector from shore column (i, j) towards the open sea: the mean direction of all sea columns
	 * in a disc around it. Null if the shore is too irregular (headland tip, strait, nothing around).
	 */
	static double[] seaNormal(boolean[][] sea, boolean[][] known, int i, int j, int radius) {
		double sx = 0;
		double sz = 0;
		int n = 0;
		for (int di = -radius; di <= radius; di++) {
			for (int dj = -radius; dj <= radius; dj++) {
				if (di == 0 && dj == 0) continue;
				if (di * di + dj * dj > radius * radius) continue;
				int a = i + di;
				int b = j + dj;
				if (a < 0 || b < 0 || a >= W || b >= W || !known[a][b] || !sea[a][b]) continue;
				double d = Math.sqrt(di * di + dj * dj);
				sx += di / d;
				sz += dj / d;
				n++;
			}
		}
		if (n < 10) return null;
		double len = Math.sqrt(sx * sx + sz * sz);
		if (len < 0.4 * n) return null;
		return new double[] {sx / len, sz / len};
	}

	/** Manhattan distance of every column to the nearest unknown column (or to the edge of the window). */
	static int[][] distToUnknown(boolean[][] known) {
		int inf = 1 << 20;
		int[][] dist = new int[W][W];
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				dist[i][j] = known[i][j] ? Math.min(Math.min(i + 1, W - i), Math.min(j + 1, W - j)) : 0;
			}
		}
		for (int i = 0; i < W; i++) {
			for (int j = 0; j < W; j++) {
				if (i > 0) dist[i][j] = Math.min(dist[i][j], dist[i - 1][j] + 1);
				if (j > 0) dist[i][j] = Math.min(dist[i][j], dist[i][j - 1] + 1);
			}
		}
		for (int i = W - 1; i >= 0; i--) {
			for (int j = W - 1; j >= 0; j--) {
				if (i < W - 1) dist[i][j] = Math.min(dist[i][j], dist[i + 1][j] + 1);
				if (j < W - 1) dist[i][j] = Math.min(dist[i][j], dist[i][j + 1] + 1);
			}
		}
		return dist;
	}

	// ------------------------------------------------------------------ choosing where to put the cove

	/** Size range of a bay: k is the radius of a circle with the same area. The smallest is the original cove. */
	static final double K_MIN = 7;
	static final double K_MAX = 12;
	/** Width of the flat sand band around the bay, of the cliff face behind it, and of the fade-out behind that. */
	static final double BEACH_W = 4.9;
	static final double CLIFF_W = 4.2;
	static final double BLEND_W = 6.3;
	/** Safety margin for the boundary noise. */
	static final double MARGIN = 3;

	/** Reasons findSite rejected a candidate, for debugging ({@code -Dtaleks.coveDebug=true}). */
	static final int WHY_NO_SHORE = 0;
	static final int WHY_NORMAL = 1;
	static final int WHY_STRAIT = 2;
	static final int WHY_FIT = 3;
	static final int WHY_ISLET = 4;
	static final int WHY_OCEAN = 5;
	static final int WHY_N = 6;

	/** Asks the world whether there is a (non-frozen) ocean biome seaward of a world position. */
	interface OceanProbe {
		boolean oceanAhead(int worldX, int worldZ, double ax, double az);
	}

	/** Where the cove goes: bay centre (window coordinates), direction to the sea, shape and size. */
	static final class Site {
		double cx;
		double cz;
		double ax;
		double az;
		CoveShape shape;
		double k;
	}

	/** Total reach of the cut (bay + beach + cliff face) from the bay centre. */
	static double cutReach(double bound) {
		return bound + BEACH_W + CLIFF_W + MARGIN;
	}

	/**
	 * Picks a shore column near the middle of the origin chunk where a cove fits: real sea in front (not a
	 * pond), a real landmass behind (not an islet), a regular shore, and enough known terrain around it.
	 * A random shape and target size are drawn; the cove is shrunk (down to K_MIN) or its centre is moved
	 * towards the middle of the 3x3 chunk window until the whole cut fits inside it (at most 48x48 blocks).
	 *
	 * @param chunkI,chunkJ window coordinates of the origin chunk's corner
	 * @param rnd           bound -> random int in [0, bound)
	 * @param why           counters per rejection reason (see WHY_*), may be null
	 */
	static Site findSite(int wx, int wz, int chunkI, int chunkJ, boolean[][] water, boolean[][] known,
						 boolean[][] sea, int[][] top, int[][] edge, IntUnaryOperator rnd, OceanProbe probe, int[] why) {
		int[] ci = new int[256];
		int[] cj = new int[256];
		int n = 0;
		for (int i = chunkI + 2; i < chunkI + 14; i++) {
			for (int j = chunkJ + 2; j < chunkJ + 14; j++) {
				if (!known[i][j] || water[i][j] || top[i][j] < SEA) continue;
				if (touchesSea(sea, known, i, j)) {
					ci[n] = i;
					cj[n++] = j;
				}
			}
		}
		if (n == 0) {
			count(why, WHY_NO_SHORE);
			return null;
		}
		for (int k = n - 1; k > 0; k--) {
			int r = rnd.applyAsInt(k + 1);
			int ti = ci[k];
			int tj = cj[k];
			ci[k] = ci[r];
			cj[k] = cj[r];
			ci[r] = ti;
			cj[r] = tj;
		}
		double target = K_MIN + (K_MAX - K_MIN) * rnd.applyAsInt(1000) / 1000.0;
		int type = CoveShape.randomType(rnd);
		Site best = null;
		for (int c = 0; c < Math.min(n, 24); c++) {
			int i = ci[c];
			int j = cj[c];
			double[] a = seaNormal(sea, known, i, j, 16);
			if (a == null) {
				count(why, WHY_NORMAL);
				continue;
			}
			if (!seaAhead(sea, known, i, j, a[0], a[1], 16)) {
				count(why, WHY_STRAIT);
				continue;
			}
			CoveShape shape = CoveShape.random(type, rnd, a[0], a[1]);
			Site site = fit(shape, i, j, a, target, sea, known, edge);
			if (site == null) {
				count(why, WHY_FIT);
				continue;
			}
			if (best != null && site.k <= best.k) continue;
			if (!isMainland(water, known, i, j, 700)) {
				count(why, WHY_ISLET);
				continue;
			}
			if (!probe.oceanAhead(wx + i, wz + j, a[0], a[1])) {
				count(why, WHY_OCEAN);
				continue;
			}
			best = site;
			if (best.k >= target - 1e-6) break;
		}
		return best;
	}

	private static void count(int[] why, int reason) {
		if (why != null) why[reason]++;
	}

	/**
	 * Largest size (not above target, not below K_MIN) at which the shape, hung on shore column (i, j), has its
	 * whole cut inside the known window. The centre is moved towards the window middle when needed, as long as the
	 * shoreline is still where the bay expects it.
	 */
	private static Site fit(CoveShape shape, int i, int j, double[] a, double target, boolean[][] sea,
							boolean[][] known, int[][] edge) {
		for (double k = target; k >= K_MIN - 1e-9; k -= 0.5) {
			double h = cutReach(shape.bound(k));
			double lo = 15 + h;
			double hi = W - h;
			if (lo > hi) continue;
			// the bay reaches 1.25 k inland of the shore column; the centre sits off blocks inland of it
			double off = 1.25 * k - shape.landExtent(k);
			double cx = i - a[0] * off;
			double cz = j - a[1] * off;
			double fx = Math.max(lo, Math.min(hi, cx));
			double fz = Math.max(lo, Math.min(hi, cz));
			if (Math.hypot(fx - cx, fz - cz) > 8) continue;
			int ei = (int) Math.round(fx);
			int ej = (int) Math.round(fz);
			if (edge[ei][ej] < h) continue;
			if (!shoreWhereExpected(sea, known, fx, fz, a[0], a[1], off)) continue;
			Site site = new Site();
			site.cx = fx;
			site.cz = fz;
			site.ax = a[0];
			site.az = a[1];
			site.shape = shape;
			site.k = k;
			return site;
		}
		return null;
	}

	/** Along the sea axis from the centre, the water must start about {@code off} blocks away (+-3). */
	private static boolean shoreWhereExpected(boolean[][] sea, boolean[][] known, double cx, double cz,
											  double ax, double az, double off) {
		boolean centreSea = isSea(sea, known, (int) Math.round(cx), (int) Math.round(cz));
		for (int s = 0; s <= 40; s++) {
			int x = (int) Math.round(cx + (centreSea ? -ax : ax) * s);
			int z = (int) Math.round(cz + (centreSea ? -az : az) * s);
			if (x < 0 || z < 0 || x >= W || z >= W || !known[x][z]) return false;
			if (sea[x][z] != centreSea) return Math.abs((centreSea ? -s : s) - off) <= 3;
		}
		return false;
	}

	private static boolean isSea(boolean[][] sea, boolean[][] known, int i, int j) {
		return i >= 0 && j >= 0 && i < W && j < W && known[i][j] && sea[i][j];
	}

	/** Does open sea continue straight ahead for a while? Rejects rivers and narrow straits. */
	private static boolean seaAhead(boolean[][] sea, boolean[][] known, int i, int j, double ax, double az, int len) {
		int land = 0;
		for (int s = 2; s <= len; s++) {
			int a = (int) Math.round(i + ax * s);
			int b = (int) Math.round(j + az * s);
			if (a < 0 || b < 0 || a >= W || b >= W || !known[a][b]) return true;
			if (!sea[a][b] && ++land >= 3) return false;
		}
		return true;
	}

	private static boolean touchesSea(boolean[][] sea, boolean[][] known, int i, int j) {
		int[][] n4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : n4) {
			int a = i + d[0];
			int b = j + d[1];
			if (a >= 0 && b >= 0 && a < W && b < W && known[a][b] && sea[a][b]) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ the cove itself

	/**
	 * Plans the new surface. Layout around the bay, by signed distance sd from its outline:
	 * <ol>
	 *   <li>sd &lt; beach width: one flat sand plane at BEACH_Y (bay + beach), only on columns that were land;</li>
	 *   <li>cliff face: smooth S-curve from the beach up to the terrain (plus a lift on the inland side);</li>
	 *   <li>blend: the lift fades out to the original terrain, so there is no seam.</li>
	 * </ol>
	 * Columns that were water are never touched, so the sand ends at the original shoreline.
	 *
	 * @param site bay centre (window coordinates), sea direction, shape and size
	 * @param hc   extra cliff height above the beach where the terrain is low
	 */
	static Plan plan(long ns, int wx, int wz, int[][] top, boolean[][] water, boolean[][] known, int[][] edge,
					 Site site, double hc) {
		Plan pl = new Plan();
		CoveShape shape = site.shape;
		double cx = site.cx;
		double cz = site.cz;
		double ax = site.ax;
		double az = site.az;
		double reach = shape.bound(site.k) + BEACH_W + CLIFF_W + BLEND_W + MARGIN;
		int i0 = Math.max(0, (int) Math.floor(cx - reach));
		int i1 = Math.min(W - 1, (int) Math.ceil(cx + reach));
		int j0 = Math.max(0, (int) Math.floor(cz - reach));
		int j1 = Math.min(W - 1, (int) Math.ceil(cz + reach));

		for (int i = i0; i <= i1; i++) {
			for (int j = j0; j <= j1; j++) {
				if (!known[i][j]) continue;
				double dx = i - cx;
				double dz = j - cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d >= reach) continue;
				int x = wx + i;
				int z = wz + j;
				double sd = shape.sdf(dx, dz, site.k) + shape.noiseAmp * noise(ns, x * 0.1, 0, z * 0.1);
				double u = dx * ax + dz * az; // > 0 towards the sea
				double inl = d < 1e-6 ? 1 : -u / d; // +1 straight inland, -1 straight to the sea
				double cw = smoothstep(-0.5, 0.35, inl);
				int t0 = top[i][j];
				boolean wat = water[i][j];
				double fade = Math.min(1, edge[i][j] / 6.0);
				double hcv = hc * (0.8 + 0.2 * noise(ns + 3, x * 0.07, 5, z * 0.07));
				double lift = cw * fade * Math.max(0, BEACH_Y + 1 + hcv - t0);

				int s;
				byte k;
				if (sd < BEACH_W) {
					// bay + beach: one flat sand plane, only on columns that were land
					// (water columns are never touched, so the plane ends exactly at the original shoreline)
					if (wat) continue;
					s = BEACH_Y;
					k = SAND;
				} else if (sd < BEACH_W + CLIFF_W) {
					if (wat) continue;
					double f = smoothstep(0, 1, (sd - BEACH_W) / CLIFF_W);
					double base = BEACH_Y + 1;
					s = (int) Math.round(base + (t0 + lift - base) * f);
					k = TERRAIN;
				} else {
					if (wat) continue;
					double f = smoothstep(0, 1, (sd - BEACH_W - CLIFF_W) / BLEND_W);
					s = t0 + (int) Math.round(lift * (1 - f));
					k = TERRAIN;
				}
				if (k == TERRAIN && s == t0) continue;
				pl.surface[i][j] = s;
				pl.kind[i][j] = k;
			}
		}
		return pl;
	}

	// ------------------------------------------------------------------ noise helpers

	static double smoothstep(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	static double hash(long s, int x, int y, int z) {
		long v = s ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 42317861L);
		v = v * 6364136223846793005L + 1442695040888963407L;
		v ^= v >>> 29;
		v *= 0xBF58476D1CE4E5B9L;
		v ^= v >>> 32;
		return ((v >>> 11) * 0x1.0p-53) * 2 - 1;
	}

	/** Smooth 3D value noise in [-1, 1]. */
	static double noise(long s, double x, double y, double z) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		int z0 = (int) Math.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c00 = lerp(hash(s, x0, y0, z0), hash(s, x0 + 1, y0, z0), fx);
		double c10 = lerp(hash(s, x0, y0 + 1, z0), hash(s, x0 + 1, y0 + 1, z0), fx);
		double c01 = lerp(hash(s, x0, y0, z0 + 1), hash(s, x0 + 1, y0, z0 + 1), fx);
		double c11 = lerp(hash(s, x0, y0 + 1, z0 + 1), hash(s, x0 + 1, y0 + 1, z0 + 1), fx);
		return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}

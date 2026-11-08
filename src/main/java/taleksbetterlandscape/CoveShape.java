package taleksbetterlandscape;

import java.util.function.IntUnaryOperator;

/**
 * Outline of a cove's bay as a signed distance field (negative inside, 0 on the outline, positive outside).
 * Pure maths, no Minecraft types.
 *
 * The shape is defined once at unit size and scaled by k, so one random description can be tried at several
 * sizes. All shapes have the same area as a circle of radius k (the blob a bit more), so k means "size"
 * whatever the shape.
 *
 * <ul>
 *   <li>CIRCLE: a round bay;</li>
 *   <li>BOX: a slightly rotated rectangle with rounded corners, wide or deep;</li>
 *   <li>BLOB: a circle merged with 2-3 smaller circles: an irregular bay with lobes.</li>
 * </ul>
 */
final class CoveShape {
	static final int CIRCLE = 0;
	static final int BOX = 1;
	static final int BLOB = 2;

	final int type;
	/** Boundary wobble (blocks) the caller adds as noise to the distance. */
	final double noiseAmp;
	private final double ux; // axis "towards the sea" of the shape, world coordinates
	private final double uz;
	private final double hu; // box half sizes along u / v (unit scale) and corner radius
	private final double hv;
	private final double rc;
	private final double[] bu; // blob circles (unit scale): centre in (u, v) and radius
	private final double[] bv;
	private final double[] br;
	private final double smoothK;
	/** Scale that makes the area of the unit shape equal to a unit circle's, so k means the same for all shapes. */
	private final double norm;
	/** Largest distance from the centre to the outline at k = 1. */
	private final double bound1;
	/** Distance from the centre to the outline towards the land (opposite of the sea axis) at k = 1. */
	private final double land1;

	private CoveShape(int type, double ax, double az, double rot, double hu, double hv, double rc,
					  double[] bu, double[] bv, double[] br, double smoothK, double noiseAmp) {
		this.type = type;
		this.noiseAmp = noiseAmp;
		double c = Math.cos(rot);
		double s = Math.sin(rot);
		this.ux = ax * c - az * s;
		this.uz = ax * s + az * c;
		this.hu = hu;
		this.hv = hv;
		this.rc = rc;
		this.bu = bu;
		this.bv = bv;
		this.br = br;
		this.smoothK = smoothK;
		// area of the raw unit shape on a grid -> normalisation
		int cells = 0;
		for (int i = 0; i < 160; i++) {
			for (int j = 0; j < 160; j++) {
				if (unit(-2.4 + (i + 0.5) * 0.03, -2.4 + (j + 0.5) * 0.03) <= 0) cells++;
			}
		}
		this.norm = Math.sqrt(Math.PI / (cells * 0.03 * 0.03));
		double b = 0;
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2 / 48;
			b = Math.max(b, extent(Math.cos(a), Math.sin(a)));
		}
		this.bound1 = b * norm;
		this.land1 = extent(-ax, -az) * norm;
	}

	/** Random shape type, weighted: 25% round, 35% rounded rectangle, 40% irregular. */
	static int randomType(IntUnaryOperator rnd) {
		int roll = rnd.applyAsInt(100);
		return roll < 25 ? CIRCLE : roll < 60 ? BOX : BLOB;
	}

	/** Random shape of the given type for a bay that opens towards the unit vector (ax, az). */
	static CoveShape random(int type, IntUnaryOperator rnd, double ax, double az) {
		if (type == CIRCLE) {
			return new CoveShape(CIRCLE, ax, az, 0, 1, 1, 0, null, null, null, 0, 1.6);
		}
		if (type == BOX) {
			double rho = 1.0 + 0.9 * rnd.applyAsInt(1000) / 1000.0; // aspect ratio 1 .. 1.9
			boolean deep = rnd.applyAsInt(2) == 0;
			double hu = Math.sqrt(0.785 * (deep ? rho : 1 / rho));
			double hv = 0.785 / hu;
			double rc = (0.12 + 0.18 * rnd.applyAsInt(1000) / 1000.0) * Math.min(hu, hv);
			double rot = (rnd.applyAsInt(1000) / 1000.0 - 0.5) * 0.5; // +-0.25 rad
			return new CoveShape(BOX, ax, az, rot, hu, hv, rc, null, null, null, 0, 2.0);
		}
		int n = 2 + rnd.applyAsInt(2);
		double[] bu = new double[n];
		double[] bv = new double[n];
		double[] br = new double[n];
		double a0 = Math.PI * 2 * rnd.applyAsInt(1000) / 1000.0;
		for (int i = 0; i < n; i++) {
			double ang = a0 + Math.PI * 2 * i / n + (rnd.applyAsInt(1000) / 1000.0 - 0.5) * 0.9;
			double dist = 0.75 + 0.35 * rnd.applyAsInt(1000) / 1000.0;
			br[i] = 0.55 + 0.25 * rnd.applyAsInt(1000) / 1000.0;
			bu[i] = Math.cos(ang) * dist;
			bv[i] = Math.sin(ang) * dist;
		}
		return new CoveShape(BLOB, ax, az, 0, 1, 1, 0, bu, bv, br, 0.5, 1.6);
	}

	/** Largest distance from the centre to the outline at size k. */
	double bound(double k) {
		return bound1 * k;
	}

	/** Distance from the centre to the outline towards the land at size k. */
	double landExtent(double k) {
		return land1 * k;
	}

	/** Signed distance from the outline at size k; (dx, dz) is the offset from the centre in world axes. */
	double sdf(double dx, double dz, double k) {
		double kk = k * norm;
		return unit((dx * ux + dz * uz) / kk, (-dx * uz + dz * ux) / kk) * kk;
	}

	private double unit(double u, double v) {
		switch (type) {
			case CIRCLE:
				return Math.sqrt(u * u + v * v) - 1;
			case BOX: {
				double qx = Math.abs(u) - hu + rc;
				double qz = Math.abs(v) - hv + rc;
				double ox = Math.max(qx, 0);
				double oz = Math.max(qz, 0);
				return Math.sqrt(ox * ox + oz * oz) + Math.min(Math.max(qx, qz), 0) - rc;
			}
			default: {
				double d = Math.sqrt(u * u + v * v) - 1;
				for (int i = 0; i < br.length; i++) {
					double e = Math.sqrt((u - bu[i]) * (u - bu[i]) + (v - bv[i]) * (v - bv[i])) - br[i];
					double h = Math.max(smoothK - Math.abs(d - e), 0) / smoothK;
					d = Math.min(d, e) - h * h * smoothK * 0.25;
				}
				return d;
			}
		}
	}

	/** Distance at unit scale from the centre to the outline in a world direction (marching, last point inside). */
	private double extent(double wx, double wz) {
		double last = 0;
		for (double t = 0; t <= 4; t += 0.01) {
			if (unit(t * (wx * ux + wz * uz), t * (-wx * uz + wz * ux)) <= 0) last = t;
		}
		return last;
	}
}

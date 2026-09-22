package net.tfminecraft.ArmourShop.pack.model;

/**
 * Continuous grip height for large_handheld (thirdperson translation Y).
 * Former presets: bottom=2.5, middle=4.0, top=5.5.
 */
public final class GripY {

	public static final double MIN = 0.0;
	public static final double MAX = 16.0;
	public static final double DEFAULT = 4.0;

	/** Absolute TP→FP map anchors from the old grip templates. */
	private static final double LEGACY_TP_LOW = 2.5;
	private static final double LEGACY_FP_LOW = 4.2;
	private static final double LEGACY_TP_SPAN = 3.0; // 5.5 - 2.5
	private static final double LEGACY_FP_SPAN = 2.0; // 6.2 - 4.2

	private GripY() {}

	/**
	 * Parse API {@code grip_preset}: numeric Y, or legacy bottom/middle/top.
	 */
	public static double parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("grip_preset is required");
		}
		String text = raw.trim();
		String lower = text.toLowerCase();
		if ("bottom".equals(lower)) {
			return 2.5;
		}
		if ("middle".equals(lower)) {
			return DEFAULT;
		}
		if ("top".equals(lower)) {
			return 5.5;
		}
		double value;
		try {
			value = Double.parseDouble(text);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Unknown grip_preset: " + raw, e);
		}
		if (value < MIN || value > MAX || Double.isNaN(value)) {
			throw new IllegalArgumentException(
				"grip_preset must be between " + MIN + " and " + MAX + ": " + raw
			);
		}
		return value;
	}

	/**
	 * First-person Y from thirdperson using the old template slope
	 * (independent of slider min/max).
	 */
	public static double firstPersonY(double thirdPersonY) {
		double y = clamp(thirdPersonY);
		return LEGACY_FP_LOW + (y - LEGACY_TP_LOW) * (LEGACY_FP_SPAN / LEGACY_TP_SPAN);
	}

	public static double clamp(double y) {
		if (Double.isNaN(y)) {
			return DEFAULT;
		}
		if (y < MIN) {
			return MIN;
		}
		if (y > MAX) {
			return MAX;
		}
		return y;
	}

	public static String format(double y) {
		double v = clamp(y);
		if (v == Math.rint(v)) {
			return String.format(java.util.Locale.ROOT, "%.1f", v);
		}
		return String.format(java.util.Locale.ROOT, "%s", v);
	}
}

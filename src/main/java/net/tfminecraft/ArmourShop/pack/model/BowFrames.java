package net.tfminecraft.ArmourShop.pack.model;

/**
 * Multi-frame stems for bow / large_bow / crossbow pack writes.
 */
public final class BowFrames {

	public static final String STANDBY = "texture";
	public static final String PULL_0 = "pull_0";
	public static final String PULL_1 = "pull_1";
	public static final String PULL_2 = "pull_2";
	public static final String CHARGED = "charged";

	public static final String[] BOW_STEMS = {
		STANDBY, PULL_0, PULL_1, PULL_2
	};

	public static final String[] CROSSBOW_STEMS = {
		STANDBY, PULL_0, PULL_1, PULL_2, CHARGED
	};

	private BowFrames() {}

	/** Disk / IA texture basename suffix after slug (empty for standby). */
	public static String fileSuffix(String stem) {
		if (STANDBY.equals(stem)) {
			return "";
		}
		if (PULL_0.equals(stem)) {
			return "_0";
		}
		if (PULL_1.equals(stem)) {
			return "_1";
		}
		if (PULL_2.equals(stem)) {
			return "_2";
		}
		if (CHARGED.equals(stem)) {
			return "_charged";
		}
		throw new IllegalArgumentException("unknown bow stem: " + stem);
	}

	public static String textureFileName(String slug, String stem) {
		return slug + fileSuffix(stem) + ".png";
	}
}

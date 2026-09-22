package net.tfminecraft.armourshop.pack.writer.armor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable ItemsAdder {@code armors_rendering.color} for one pack slug
 * ({@code submissionId_tier}). The worn layer is chosen by this hex, so
 * every slug needs its own value.
 */
public final class ArmorRenderColor {

	private ArmorRenderColor() {}

	public static String forSlug(String slug) {
		if (slug == null || slug.isBlank()) {
			throw new IllegalArgumentException("slug is required");
		}
		int rgb = hash24(slug.trim());
		while (rgb == 0x000000 || rgb == 0xFFFFFF) {
			rgb = (rgb + 1) & 0xFFFFFF;
		}
		return String.format("#%06x", rgb);
	}

	private static int hash24(String slug) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(slug.getBytes(StandardCharsets.UTF_8));
			return ((hash[0] & 0xFF) << 16) | ((hash[1] & 0xFF) << 8) | (hash[2] & 0xFF);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}

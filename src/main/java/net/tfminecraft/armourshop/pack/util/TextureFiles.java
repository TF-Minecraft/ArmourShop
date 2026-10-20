package net.tfminecraft.armourshop.pack.util;

import net.tfminecraft.armourshop.pack.model.PackSubmission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Writes submission PNGs together with their optional {@code .png.mcmeta} animation.
 * A texture stem {@code S} has its animation under stem {@code S.mcmeta}.
 */
public final class TextureFiles {

	public static final String MCMETA_SUFFIX = ".mcmeta";

	private TextureFiles() {}

	public static String mcmetaStem(String stem) {
		return stem + MCMETA_SUFFIX;
	}

	public static boolean isMcmetaStem(String stem) {
		return stem != null && stem.trim().toLowerCase(Locale.ROOT).endsWith(MCMETA_SUFFIX);
	}

	/** {@code foo.png} → {@code foo.png.mcmeta} beside it. */
	public static Path mcmetaPath(Path png) {
		return png.resolveSibling(png.getFileName().toString() + MCMETA_SUFFIX);
	}

	/** Writes the stem's PNG to {@code png}, then its animation (if any). */
	public static void writePng(
		PackSubmission submission,
		String stem,
		Path png,
		List<Path> written
	) throws IOException {
		Files.write(png, submission.requireFile(stem));
		written.add(png);
		writeMcmeta(submission, stem, png, written);
	}

	/**
	 * Writes {@code png.mcmeta} when the submission animates {@code stem}; otherwise
	 * removes a stale one left by an earlier apply of the same slug.
	 */
	public static void writeMcmeta(
		PackSubmission submission,
		String stem,
		Path png,
		List<Path> written
	) throws IOException {
		Path meta = mcmetaPath(png);
		byte[] data = submission.files().get(mcmetaStem(stem));
		if (data == null || data.length == 0) {
			Files.deleteIfExists(meta);
			return;
		}
		Files.write(meta, data);
		written.add(meta);
	}

	/** Deletes {@code png} and its {@code .mcmeta}, recording what was removed. */
	public static void deleteWithMcmeta(Path png, List<Path> removed) throws IOException {
		if (Files.deleteIfExists(png)) {
			removed.add(png);
		}
		Path meta = mcmetaPath(png);
		if (Files.deleteIfExists(meta)) {
			removed.add(meta);
		}
	}
}

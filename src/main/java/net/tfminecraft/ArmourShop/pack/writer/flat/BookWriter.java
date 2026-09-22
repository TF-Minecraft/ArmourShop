package net.tfminecraft.ArmourShop.pack.writer.flat;


import net.tfminecraft.ArmourShop.pack.model.PackKind;
import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import net.tfminecraft.ArmourShop.pack.model.PackSubmission;
import net.tfminecraft.ArmourShop.pack.util.YamlUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes book skins: unsigned (writable) + signed (written) 16×16 covers.
 */
public final class BookWriter {

	public static final String UNSIGNED_STEM = "unsigned";
	public static final String SIGNED_STEM = "signed";

	private BookWriter() {}

	public static List<Path> write(Path contentsRoot, PackSubmission submission)
		throws IOException
	{
		return write(contentsRoot, submission, PackPaths.playerNamespace());
	}

	public static List<Path> write(
		Path contentsRoot,
		PackSubmission submission,
		String namespace
	) throws IOException {
		if (submission.kind() != PackKind.BOOK) {
			throw new IllegalArgumentException(
				"BookWriter requires BOOK, got " + submission.kind()
			);
		}

		String slug = submission.slug();
		YamlUtil.validateSlug(slug);
		String ns = requireNs(namespace);

		Path itemTexDir = PackPaths.itemTexturesDir(contentsRoot, ns);
		Path configsDir = PackPaths.configsDir(contentsRoot, ns);
		Files.createDirectories(itemTexDir);
		Files.createDirectories(configsDir);

		List<Path> written = new ArrayList<>();

		Path unsignedPng = itemTexDir.resolve(slug + "_unsigned.png");
		Files.write(unsignedPng, submission.requireFile(UNSIGNED_STEM));
		written.add(unsignedPng);

		Path signedPng = itemTexDir.resolve(slug + "_signed.png");
		Files.write(signedPng, submission.requireFile(SIGNED_STEM));
		written.add(signedPng);

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(yamlPath, buildYaml(submission, ns), StandardCharsets.UTF_8);
		written.add(yamlPath);
		return written;
	}

	/**
	 * Best-effort delete of book pack files under {@code namespace}.
	 */
	public static List<Path> remove(Path contentsRoot, String namespace, String slug)
		throws IOException
	{
		String ns = namespace == null || namespace.isBlank()
			? PackPaths.playerNamespace()
			: namespace.trim();
		String s = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			throw new IllegalArgumentException("slug is blank");
		}
		List<Path> removed = new ArrayList<>();
		deleteQuiet(PackPaths.configsDir(contentsRoot, ns).resolve(s + ".yml"), removed);
		Path itemTex = PackPaths.itemTexturesDir(contentsRoot, ns);
		deleteQuiet(itemTex.resolve(s + "_unsigned.png"), removed);
		deleteQuiet(itemTex.resolve(s + "_signed.png"), removed);
		return removed;
	}

	static String buildYaml(PackSubmission submission, String namespace) {
		String slug = submission.slug();
		String name = YamlUtil.escapeDoubleQuoted(submission.displayName());
		String ns = requireNs(namespace);
		String signedId = slug + "_signed";

		StringBuilder sb = new StringBuilder();
		sb.append("info:\n");
		sb.append("  namespace: ").append(ns).append('\n');
		sb.append("items:\n");
		appendItem(sb, slug, name, slug, "WRITABLE_BOOK", slug + "_unsigned");
		appendItem(sb, signedId, name, slug, "WRITTEN_BOOK", slug + "_signed");
		return sb.toString();
	}

	private static void appendItem(
		StringBuilder sb,
		String id,
		String displayName,
		String permission,
		String material,
		String textureStem
	) {
		sb.append("  ").append(id).append(":\n");
		sb.append("    display_name: \"").append(displayName).append("\"\n");
		sb.append("    permission: ").append(permission).append('\n');
		sb.append("    resource:\n");
		sb.append("      material: ").append(material).append('\n');
		sb.append("      generate: true\n");
		sb.append("      parent: item/generated\n");
		sb.append("      textures:\n");
		sb.append("      - item/").append(textureStem).append('\n');
	}

	private static void deleteQuiet(Path path, List<Path> removed) throws IOException {
		if (Files.deleteIfExists(path)) {
			removed.add(path);
		}
	}

	private static String requireNs(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace is required");
		}
		return namespace.trim();
	}
}

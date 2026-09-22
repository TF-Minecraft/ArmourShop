package net.tfminecraft.ArmourShop.pack.writer.bow;


import net.tfminecraft.ArmourShop.pack.model.BowFrames;
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

/**
 * Writes bow skins (BOW + generate: true + pull frame PNGs).
 */
public final class BowWriter {

	private BowWriter() {}

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
		if (submission.kind() != PackKind.BOW) {
			throw new IllegalArgumentException(
				"BowWriter requires BOW, got " + submission.kind()
			);
		}
		return writeGenerateTrue(
			contentsRoot,
			submission,
			"BOW",
			BowFrames.BOW_STEMS,
			namespace
		);
	}

	/**
	 * Writes crossbow skins (CROSSBOW + generate: true + pull/charged frames).
	 */
	public static List<Path> writeCrossbow(Path contentsRoot, PackSubmission submission)
		throws IOException
	{
		return writeCrossbow(contentsRoot, submission, PackPaths.playerNamespace());
	}

	public static List<Path> writeCrossbow(
		Path contentsRoot,
		PackSubmission submission,
		String namespace
	) throws IOException {
		if (submission.kind() != PackKind.CROSSBOW) {
			throw new IllegalArgumentException(
				"writeCrossbow requires CROSSBOW, got " + submission.kind()
			);
		}
		return writeGenerateTrue(
			contentsRoot,
			submission,
			"CROSSBOW",
			BowFrames.CROSSBOW_STEMS,
			namespace
		);
	}

	static List<Path> writeGenerateTrue(
		Path contentsRoot,
		PackSubmission submission,
		String material,
		String[] stems
	) throws IOException {
		return writeGenerateTrue(
			contentsRoot, submission, material, stems, PackPaths.playerNamespace()
		);
	}

	static List<Path> writeGenerateTrue(
		Path contentsRoot,
		PackSubmission submission,
		String material,
		String[] stems,
		String namespace
	) throws IOException {
		String slug = submission.slug();
		YamlUtil.validateSlug(slug);
		String ns = requireNs(namespace);

		Path itemTexDir = PackPaths.itemTexturesDir(contentsRoot, ns);
		Path configsDir = PackPaths.configsDir(contentsRoot, ns);
		Files.createDirectories(itemTexDir);
		Files.createDirectories(configsDir);

		List<Path> written = new ArrayList<>();
		for (String stem : stems) {
			byte[] png = submission.requireFile(stem);
			Path pngPath = itemTexDir.resolve(BowFrames.textureFileName(slug, stem));
			Files.write(pngPath, png);
			written.add(pngPath);
			// IA CROSSBOW charged often keyed as *_arrow
			if (BowFrames.CHARGED.equals(stem)) {
				Path arrow = itemTexDir.resolve(slug + "_arrow.png");
				Files.write(arrow, png);
				written.add(arrow);
			}
		}

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(
			yamlPath,
			buildYaml(submission, material, ns),
			StandardCharsets.UTF_8
		);
		written.add(yamlPath);
		return written;
	}

	static String buildYaml(PackSubmission submission, String material) {
		return buildYaml(submission, material, PackPaths.playerNamespace());
	}

	static String buildYaml(PackSubmission submission, String material, String namespace) {
		String slug = submission.slug();
		String name = YamlUtil.escapeDoubleQuoted(submission.displayName());
		StringBuilder sb = new StringBuilder();
		sb.append("info:\n");
		sb.append("  namespace: ").append(requireNs(namespace)).append('\n');
		sb.append("items:\n");
		sb.append("  ").append(slug).append(":\n");
		sb.append("    display_name: \"").append(name).append("\"\n");
		sb.append("    permission: ").append(slug).append('\n');
		sb.append("    resource:\n");
		sb.append("      material: ").append(material).append('\n');
		sb.append("      generate: true\n");
		sb.append("      textures:\n");
		sb.append("      - item/").append(slug).append('\n');
		return sb.toString();
	}

	private static String requireNs(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace is required");
		}
		return namespace.trim();
	}
}

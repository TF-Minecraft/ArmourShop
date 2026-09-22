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

/**
 * Writes item / handheld skins (generate: true + vanilla parent).
 */
public final class FlatItemWriter {

	public static final String TEXTURE_STEM = "texture";

	private FlatItemWriter() {}

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
		PackKind kind = submission.kind();
		if (kind != PackKind.ITEM && kind != PackKind.HANDHELD) {
			throw new IllegalArgumentException(
				"FlatItemWriter requires ITEM or HANDHELD, got " + kind
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

		Path pngPath = itemTexDir.resolve(slug + ".png");
		Files.write(pngPath, submission.requireFile(TEXTURE_STEM));
		written.add(pngPath);

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(yamlPath, buildYaml(submission, ns), StandardCharsets.UTF_8);
		written.add(yamlPath);
		return written;
	}

	static String buildYaml(PackSubmission submission) {
		return buildYaml(submission, PackPaths.playerNamespace());
	}

	static String buildYaml(PackSubmission submission, String namespace) {
		String slug = submission.slug();
		String name = YamlUtil.escapeDoubleQuoted(submission.displayName());
		String parent = submission.kind() == PackKind.HANDHELD
			? "item/handheld"
			: "item/generated";

		StringBuilder sb = new StringBuilder();
		sb.append("info:\n");
		sb.append("  namespace: ").append(requireNs(namespace)).append('\n');
		sb.append("items:\n");
		sb.append("  ").append(slug).append(":\n");
		sb.append("    display_name: \"").append(name).append("\"\n");
		sb.append("    permission: ").append(slug).append('\n');
		sb.append("    resource:\n");
		sb.append("      material: PAPER\n");
		sb.append("      generate: true\n");
		sb.append("      parent: ").append(parent).append('\n');
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

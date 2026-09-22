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
 * Writes large_bow skins from web-built thin models (passthrough).
 */
public final class LargeBowWriter {

	/** Stem prefix for frame model JSON ({@code model}, {@code model_0}, …). */
	public static final String MODEL_STEM_PREFIX = "model";

	private LargeBowWriter() {}

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
		if (submission.kind() != PackKind.LARGE_BOW) {
			throw new IllegalArgumentException(
				"LargeBowWriter requires LARGE_BOW, got " + submission.kind()
			);
		}

		String slug = submission.slug();
		YamlUtil.validateSlug(slug);
		String ns = requireNs(namespace);

		Path modelsDir = PackPaths.itemModelsDir(contentsRoot, ns);
		Path itemTexDir = PackPaths.itemTexturesDir(contentsRoot, ns);
		Path configsDir = PackPaths.configsDir(contentsRoot, ns);
		Files.createDirectories(modelsDir);
		Files.createDirectories(itemTexDir);
		Files.createDirectories(configsDir);

		List<Path> written = new ArrayList<>();
		for (String stem : BowFrames.BOW_STEMS) {
			byte[] png = submission.requireFile(stem);
			Path pngPath = itemTexDir.resolve(BowFrames.textureFileName(slug, stem));
			Files.write(pngPath, png);
			written.add(pngPath);

			String modelStem = modelStemFor(stem);
			byte[] modelJson = submission.requireFile(modelStem);
			String modelName = slug + BowFrames.fileSuffix(stem) + ".json";
			Path modelPath = modelsDir.resolve(modelName);
			Files.write(modelPath, modelJson);
			written.add(modelPath);
		}

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(yamlPath, buildYaml(submission, ns), StandardCharsets.UTF_8);
		written.add(yamlPath);
		return written;
	}

	/** Map bow frame stem → pack submission model stem. */
	public static String modelStemFor(String bowStem) {
		if (BowFrames.STANDBY.equals(bowStem)) {
			return MODEL_STEM_PREFIX;
		}
		if (BowFrames.PULL_0.equals(bowStem)) {
			return MODEL_STEM_PREFIX + "_0";
		}
		if (BowFrames.PULL_1.equals(bowStem)) {
			return MODEL_STEM_PREFIX + "_1";
		}
		if (BowFrames.PULL_2.equals(bowStem)) {
			return MODEL_STEM_PREFIX + "_2";
		}
		throw new IllegalArgumentException("unknown bow stem: " + bowStem);
	}

	static String buildYaml(PackSubmission submission) {
		return buildYaml(submission, PackPaths.playerNamespace());
	}

	static String buildYaml(PackSubmission submission, String namespace) {
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
		sb.append("      material: BOW\n");
		sb.append("      generate: false\n");
		sb.append("      model_path: item/").append(slug).append('\n');
		return sb.toString();
	}

	private static String requireNs(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace is required");
		}
		return namespace.trim();
	}
}

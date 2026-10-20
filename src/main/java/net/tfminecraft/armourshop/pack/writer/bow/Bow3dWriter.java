package net.tfminecraft.armourshop.pack.writer.bow;

import net.tfminecraft.armourshop.pack.model.PackKind;
import net.tfminecraft.armourshop.pack.model.PackPaths;
import net.tfminecraft.armourshop.pack.model.PackSubmission;
import net.tfminecraft.armourshop.pack.util.Model3dUtil;
import net.tfminecraft.armourshop.pack.util.TextureFiles;
import net.tfminecraft.armourshop.pack.util.YamlUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes bow_3d / crossbow_3d skins: one shared texture and a player-made model per
 * draw state (passthrough). ItemsAdder ({@code generate: false}) reads
 * {@code model_path} plus {@code _0/_1/_2} for BOW, and also {@code _charged} and
 * {@code _firework} for CROSSBOW.
 */
public final class Bow3dWriter {

	public static final String MODEL_0_STEM = Model3dUtil.MODEL_STEM + "_0";
	public static final String MODEL_1_STEM = Model3dUtil.MODEL_STEM + "_1";
	public static final String MODEL_2_STEM = Model3dUtil.MODEL_STEM + "_2";
	public static final String MODEL_CHARGED_STEM = Model3dUtil.MODEL_STEM + "_charged";

	/** Model stem → pack file suffix after the slug, for bow_3d. */
	public static final String[][] BOW_MODELS = {
		{Model3dUtil.MODEL_STEM, ""},
		{MODEL_0_STEM, "_0"},
		{MODEL_1_STEM, "_1"},
		{MODEL_2_STEM, "_2"},
	};

	/** crossbow_3d adds the loaded model, written again as the firework model. */
	public static final String[][] CROSSBOW_MODELS = {
		{Model3dUtil.MODEL_STEM, ""},
		{MODEL_0_STEM, "_0"},
		{MODEL_1_STEM, "_1"},
		{MODEL_2_STEM, "_2"},
		{MODEL_CHARGED_STEM, "_charged"},
		{MODEL_CHARGED_STEM, "_firework"},
	};

	private Bow3dWriter() {}

	public static List<Path> write(
		Path contentsRoot,
		PackSubmission submission,
		String namespace
	) throws IOException {
		if (submission.kind() == PackKind.BOW_3D) {
			return write(contentsRoot, submission, "BOW", BOW_MODELS, namespace);
		}
		if (submission.kind() == PackKind.CROSSBOW_3D) {
			return write(contentsRoot, submission, "CROSSBOW", CROSSBOW_MODELS, namespace);
		}
		throw new IllegalArgumentException(
			"Bow3dWriter requires BOW_3D or CROSSBOW_3D, got " + submission.kind()
		);
	}

	/** Model stems the submission must carry for a kind. */
	public static String[] requiredModelStems(PackKind kind) {
		String[][] models = kind == PackKind.CROSSBOW_3D ? CROSSBOW_MODELS : BOW_MODELS;
		return Arrays.stream(models).map(m -> m[0]).distinct().toArray(String[]::new);
	}

	private static List<Path> write(
		Path contentsRoot,
		PackSubmission submission,
		String material,
		String[][] models,
		String namespace
	) throws IOException {
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
		TextureFiles.writePng(
			submission, Model3dUtil.TEXTURE_STEM, itemTexDir.resolve(slug + ".png"), written
		);
		for (String[] model : models) {
			Path modelPath = modelsDir.resolve(slug + model[1] + ".json");
			Files.write(modelPath, submission.requireFile(model[0]));
			written.add(modelPath);
		}

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(yamlPath, buildYaml(submission, material, ns), StandardCharsets.UTF_8);
		written.add(yamlPath);
		return written;
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

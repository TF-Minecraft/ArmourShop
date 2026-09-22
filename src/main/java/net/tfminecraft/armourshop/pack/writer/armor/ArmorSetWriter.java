package net.tfminecraft.armourshop.pack.writer.armor;


import net.tfminecraft.armourshop.pack.model.PackKind;
import net.tfminecraft.armourshop.pack.model.PackPaths;
import net.tfminecraft.armourshop.pack.model.PackSubmission;
import net.tfminecraft.armourshop.pack.util.Model3dUtil;
import net.tfminecraft.armourshop.pack.util.YamlUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes an armor_set into an IA pack namespace (YAML + PNGs; optional 3D helmet).
 */
public final class ArmorSetWriter {

	private static final String[] BODY_ICON_STEMS = {
		"chestplate", "leggings", "boots"
	};
	private static final String[] BODY_ICON_SLOTS = {
		"chest", "legs", "feet"
	};
	private static final String[] BODY_ICON_SUFFIXES = {
		"Chestplate", "Leggings", "Boots"
	};
	private static final String[] LAYER_STEMS = {
		"layer_1", "layer_2"
	};

	private ArmorSetWriter() {}

	/**
	 * @return list of paths written (relative-friendly absolute paths)
	 */
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
		if (submission.kind() != PackKind.ARMOR_SET) {
			throw new IllegalArgumentException(
				"ArmorSetWriter requires ARMOR_SET, got " + submission.kind()
			);
		}

		String slug = submission.slug();
		YamlUtil.validateSlug(slug);
		String ns = requireNs(namespace);

		boolean helmet3d = submission.files().containsKey(Model3dUtil.HELMET_MODEL_STEM);

		Path iconsDir = PackPaths.armorIconsDir(contentsRoot, ns);
		Path layersDir = PackPaths.armorLayersDir(contentsRoot, ns);
		Path configsDir = PackPaths.configsDir(contentsRoot, ns);
		Path modelsDir = PackPaths.itemModelsDir(contentsRoot, ns);
		Path itemTexDir = PackPaths.itemTexturesDir(contentsRoot, ns);
		Files.createDirectories(iconsDir);
		Files.createDirectories(layersDir);
		Files.createDirectories(configsDir);

		List<Path> written = new ArrayList<>();

		if (helmet3d) {
			Files.createDirectories(modelsDir);
			Files.createDirectories(itemTexDir);
			String helmetId = slug + "_helmet";
			Path tex = itemTexDir.resolve(helmetId + ".png");
			Files.write(tex, submission.requireFile(Model3dUtil.HELMET_TEXTURE_STEM));
			written.add(tex);
			Path modelPath = modelsDir.resolve(helmetId + ".json");
			Files.write(modelPath, submission.requireFile(Model3dUtil.HELMET_MODEL_STEM));
			written.add(modelPath);
		} else {
			Path out = iconsDir.resolve(slug + "_helmet.png");
			Files.write(out, submission.requireFile("helmet"));
			written.add(out);
		}

		for (String stem : BODY_ICON_STEMS) {
			Path out = iconsDir.resolve(slug + "_" + stem + ".png");
			Files.write(out, submission.requireFile(stem));
			written.add(out);
		}
		for (String stem : LAYER_STEMS) {
			Path out = layersDir.resolve(slug + "_" + stem + ".png");
			Files.write(out, submission.requireFile(stem));
			written.add(out);
		}

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(
			yamlPath,
			buildYaml(submission, helmet3d, ns),
			StandardCharsets.UTF_8
		);
		written.add(yamlPath);
		return written;
	}

	static String buildYaml(PackSubmission submission, boolean helmet3d) {
		return buildYaml(submission, helmet3d, PackPaths.playerNamespace());
	}

	static String buildYaml(PackSubmission submission, boolean helmet3d, String namespace) {
		String slug = submission.slug();
		String name = YamlUtil.escapeDoubleQuoted(submission.displayName());
		String ns = requireNs(namespace);

		StringBuilder sb = new StringBuilder();
		sb.append("info:\n");
		sb.append("  namespace: ").append(ns).append('\n');
		sb.append("armors_rendering:\n");
		sb.append("  ").append(slug).append(":\n");
		sb.append("    color: '").append(ArmorRenderColor.forSlug(slug)).append("'\n");
		sb.append("    layer_1: armor_layers/").append(slug).append("_layer_1\n");
		sb.append("    layer_2: armor_layers/").append(slug).append("_layer_2\n");
		sb.append("    use_color: false\n");
		sb.append("items:\n");

		String helmetId = slug + "_helmet";
		sb.append("  ").append(helmetId).append(":\n");
		sb.append("    display_name: \"").append(name).append(" Helmet\"\n");
		sb.append("    permission: ").append(slug).append('\n');
		sb.append("    resource:\n");
		if (helmet3d) {
			sb.append("      material: CARVED_PUMPKIN\n");
			sb.append("      generate: false\n");
			sb.append("      model_path: item/").append(helmetId).append('\n');
			sb.append("    behaviours:\n");
			sb.append("      hat: true\n");
		} else {
			sb.append("      generate: true\n");
			sb.append("      textures:\n");
			sb.append("      - armor_icons/").append(slug).append("_helmet\n");
			sb.append("    specific_properties:\n");
			sb.append("      armor:\n");
			sb.append("        slot: head\n");
			sb.append("        custom_armor: ").append(slug).append('\n');
		}

		for (int i = 0; i < BODY_ICON_STEMS.length; i++) {
			String stem = BODY_ICON_STEMS[i];
			String itemId = slug + "_" + stem;
			sb.append("  ").append(itemId).append(":\n");
			sb.append("    display_name: \"").append(name).append(' ')
				.append(BODY_ICON_SUFFIXES[i]).append("\"\n");
			sb.append("    permission: ").append(slug).append('\n');
			sb.append("    resource:\n");
			sb.append("      generate: true\n");
			sb.append("      textures:\n");
			sb.append("      - armor_icons/").append(slug).append('_').append(stem).append('\n');
			sb.append("    specific_properties:\n");
			sb.append("      armor:\n");
			sb.append("        slot: ").append(BODY_ICON_SLOTS[i]).append('\n');
			sb.append("        custom_armor: ").append(slug).append('\n');
		}
		return sb.toString();
	}

	private static String requireNs(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace is required");
		}
		return namespace.trim();
	}
}

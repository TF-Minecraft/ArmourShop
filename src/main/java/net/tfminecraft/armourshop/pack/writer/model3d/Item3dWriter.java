package net.tfminecraft.armourshop.pack.writer.model3d;


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
 * Writes item_3d / helmet_3d skins from web-normalized model JSON (passthrough).
 */
public final class Item3dWriter {

	private Item3dWriter() {}

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
		if (submission.kind() != PackKind.ITEM_3D) {
			throw new IllegalArgumentException(
				"Item3dWriter requires ITEM_3D, got " + submission.kind()
			);
		}
		return writeModelItem(
			contentsRoot,
			submission,
			"PAPER",
			null,
			false,
			namespace
		);
	}

	/**
	 * Writes standalone helmet_3d skins as an unplaceable carved pumpkin hat.
	 */
	public static List<Path> writeHelmet3d(Path contentsRoot, PackSubmission submission)
		throws IOException
	{
		return writeHelmet3d(contentsRoot, submission, PackPaths.playerNamespace());
	}

	public static List<Path> writeHelmet3d(
		Path contentsRoot,
		PackSubmission submission,
		String namespace
	) throws IOException {
		if (submission.kind() != PackKind.HELMET_3D) {
			throw new IllegalArgumentException(
				"writeHelmet3d requires HELMET_3D, got " + submission.kind()
			);
		}
		return writeModelItem(
			contentsRoot,
			submission,
			"CARVED_PUMPKIN",
			null,
			true,
			namespace
		);
	}

	/**
	 * Shared pack write for standalone 3D items (item_3d / helmet_3d).
	 *
	 * @param material IA material name
	 * @param armorSlot if non-null, adds armor slot specific_properties (e.g. {@code head})
	 * @param hat when true, appends {@code behaviours.hat} so a carved pumpkin cannot be placed
	 */
	static List<Path> writeModelItem(
		Path contentsRoot,
		PackSubmission submission,
		String material,
		String armorSlot,
		boolean hat
	) throws IOException {
		return writeModelItem(
			contentsRoot, submission, material, armorSlot, hat, PackPaths.playerNamespace()
		);
	}

	static List<Path> writeModelItem(
		Path contentsRoot,
		PackSubmission submission,
		String material,
		String armorSlot,
		boolean hat,
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

		Path pngPath = itemTexDir.resolve(slug + ".png");
		Files.write(pngPath, submission.requireFile(Model3dUtil.TEXTURE_STEM));
		written.add(pngPath);

		Path modelPath = modelsDir.resolve(slug + ".json");
		Files.write(modelPath, submission.requireFile(Model3dUtil.MODEL_STEM));
		written.add(modelPath);

		Path yamlPath = configsDir.resolve(slug + ".yml");
		Files.writeString(
			yamlPath,
			buildYaml(submission, material, armorSlot, hat, ns),
			StandardCharsets.UTF_8
		);
		written.add(yamlPath);
		return written;
	}

	static String buildYaml(
		PackSubmission submission,
		String material,
		String armorSlot,
		boolean hat
	) {
		return buildYaml(submission, material, armorSlot, hat, PackPaths.playerNamespace());
	}

	static String buildYaml(
		PackSubmission submission,
		String material,
		String armorSlot,
		boolean hat,
		String namespace
	) {
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
		if (armorSlot != null && !armorSlot.isBlank()) {
			sb.append("    specific_properties:\n");
			sb.append("      armor:\n");
			sb.append("        slot: ").append(armorSlot.trim()).append('\n');
		}
		if (hat) {
			sb.append("    behaviours:\n");
			sb.append("      hat: true\n");
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

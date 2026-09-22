package net.tfminecraft.ArmourShop.pack.writer.gun;


import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Append / remove GunsAndGadgets {@code skins.yml} entries (Bukkit-free).
 * Submission skins use {@code ia.tfmc_submissions:{slug}_*} paths.
 */
public final class GunsSkinsYml {

	private static final Pattern ROOT_KEY = Pattern.compile(
		"(?m)^([A-Za-z0-9_\\-]+):\\s*$"
	);

	private GunsSkinsYml() {}

	public static void upsert(Path skinsYml, String slug, String baseSet)
		throws IOException
	{
		upsert(skinsYml, slug, baseSet, PackPaths.playerNamespace());
	}

	public static void upsert(
		Path skinsYml,
		String slug,
		String baseSet,
		String namespace
	) throws IOException {
		if (skinsYml == null) {
			throw new IllegalArgumentException("skinsYml is null");
		}
		String key = require(slug, "slug");
		String type = gagType(baseSet);
		Path parent = skinsYml.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		String ns = namespace == null || namespace.isBlank()
			? PackPaths.playerNamespace()
			: namespace.trim();
		String existing = Files.isRegularFile(skinsYml)
			? Files.readString(skinsYml, StandardCharsets.UTF_8)
			: "";
		String without = removeKeyBlock(existing, key);
		StringBuilder block = new StringBuilder();
		block.append(key).append(":\n");
		block.append("  carry: ia.").append(ns).append(':').append(key).append("_carry\n");
		block.append("  reload: ia.").append(ns).append(':').append(key).append("_reload\n");
		block.append("  aim: ia.").append(ns).append(':').append(key).append("_aim\n");
		block.append("  types:\n");
		block.append("    - ").append(type).append('\n');

		String body = without.stripTrailing();
		String out;
		if (body.isEmpty()) {
			out = block.toString();
		} else {
			out = body + "\n\n" + block;
		}
		if (!out.endsWith("\n")) {
			out = out + "\n";
		}
		Files.writeString(skinsYml, out, StandardCharsets.UTF_8);
	}

	public static boolean remove(Path skinsYml, String slug) throws IOException {
		if (skinsYml == null || !Files.isRegularFile(skinsYml)) {
			return false;
		}
		String key = require(slug, "slug");
		String existing = Files.readString(skinsYml, StandardCharsets.UTF_8);
		String without = removeKeyBlock(existing, key);
		if (without.equals(existing)) {
			return false;
		}
		String out = without.stripTrailing();
		if (!out.isEmpty() && !out.endsWith("\n")) {
			out = out + "\n";
		}
		Files.writeString(skinsYml, out, StandardCharsets.UTF_8);
		return true;
	}

	/** Maps shop {@code base_set} to GaG {@code types} singular. */
	public static String gagType(String baseSet) {
		String s = baseSet == null ? "" : baseSet.trim().toLowerCase(Locale.ROOT);
		return switch (s) {
			case "rifles" -> "rifle";
			case "pistols" -> "pistol";
			case "shotguns" -> "shotgun";
			case "launchers" -> "launcher";
			default -> throw new IllegalArgumentException(
				"unsupported gun base_set: " + baseSet
			);
		};
	}

	static String removeKeyBlock(String yaml, String key) {
		if (yaml == null || yaml.isEmpty()) {
			return "";
		}
		Matcher m = ROOT_KEY.matcher(yaml);
		List<int[]> roots = new ArrayList<>();
		while (m.find()) {
			roots.add(new int[] { m.start(), m.end(), m.group(1).equals(key) ? 1 : 0 });
		}
		for (int i = 0; i < roots.size(); i++) {
			int[] root = roots.get(i);
			if (root[2] != 1) {
				continue;
			}
			int start = root[0];
			int end = i + 1 < roots.size() ? roots.get(i + 1)[0] : yaml.length();
			String before = yaml.substring(0, start);
			String after = yaml.substring(end);
			return (before + after).replaceAll("\\n{3,}", "\n\n");
		}
		return yaml;
	}

	private static String require(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("missing " + field);
		}
		return value.trim();
	}
}

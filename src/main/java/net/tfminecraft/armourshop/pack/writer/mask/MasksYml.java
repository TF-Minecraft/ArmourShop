package net.tfminecraft.armourshop.pack.writer.mask;

import net.tfminecraft.armourshop.pack.util.YamlUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Upsert / remove RPCharacters {@code custom-masks.yml} entries (Bukkit-free).
 * Each entry is the ItemsAdder path of an approved mask skin.
 */
public final class MasksYml {

	private MasksYml() {}

	public static void upsert(Path masksYml, String slug, String namespace)
		throws IOException
	{
		if (masksYml == null) {
			throw new IllegalArgumentException("masksYml is null");
		}
		String key = requireSlug(slug);
		String item = "ia." + requireNamespace(namespace) + ":" + key;
		Path parent = masksYml.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Map<String, String> entries = read(masksYml);
		entries.put(key, item);
		write(masksYml, entries);
	}

	public static boolean remove(Path masksYml, String slug) throws IOException {
		if (masksYml == null || !Files.isRegularFile(masksYml)) {
			return false;
		}
		String key = requireSlug(slug);
		Map<String, String> entries = read(masksYml);
		if (entries.remove(key) == null) {
			return false;
		}
		write(masksYml, entries);
		return true;
	}

	static Map<String, String> read(Path masksYml) throws IOException {
		Map<String, String> entries = new LinkedHashMap<>();
		if (masksYml == null || !Files.isRegularFile(masksYml)) {
			return entries;
		}
		String current = null;
		for (String raw : Files.readString(masksYml, StandardCharsets.UTF_8).split("\\R")) {
			String line = raw.stripTrailing();
			if (line.startsWith("  ") && !line.startsWith("    ") && line.endsWith(":")) {
				String key = line.substring(2, line.length() - 1).trim();
				current = key.isEmpty() ? null : key;
				continue;
			}
			if (current != null && line.startsWith("    item:")) {
				String item = line.substring("    item:".length()).trim();
				if (!item.isEmpty()) {
					entries.put(current, item);
				}
				current = null;
			}
		}
		return entries;
	}

	private static void write(Path masksYml, Map<String, String> entries) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("# Custom RP masks written by ArmourShop. Loaded by RPCharacters with masks.yml.\n");
		sb.append("masks:\n");
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			sb.append("  ").append(entry.getKey()).append(":\n");
			sb.append("    item: ").append(entry.getValue()).append('\n');
		}
		Files.writeString(masksYml, sb.toString(), StandardCharsets.UTF_8);
	}

	private static String requireSlug(String slug) {
		if (slug == null || slug.isBlank()) {
			throw new IllegalArgumentException("missing slug");
		}
		String key = slug.trim();
		YamlUtil.validateSlug(key);
		return key;
	}

	private static String requireNamespace(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("missing namespace");
		}
		String ns = namespace.trim();
		for (int i = 0; i < ns.length(); i++) {
			char c = ns.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z')
				|| (c >= '0' && c <= '9')
				|| c == '_'
				|| c == '-';
			if (!ok) {
				throw new IllegalArgumentException("invalid namespace: " + namespace);
			}
		}
		return ns;
	}
}

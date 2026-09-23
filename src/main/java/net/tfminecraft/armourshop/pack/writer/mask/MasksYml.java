package net.tfminecraft.armourshop.pack.writer.mask;

import net.tfminecraft.armourshop.pack.util.YamlUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if ("masks:".equals(trimmed)) {
				current = null;
				continue;
			}
			if (trimmed.endsWith(":") && !trimmed.contains(" ") && !trimmed.contains("\t")) {
				current = trimmed.substring(0, trimmed.length() - 1);
				continue;
			}
			if (current != null && trimmed.startsWith("item:")) {
				String item = trimmed.substring("item:".length()).trim();
				if (item.isEmpty()) {
					throw new IOException("mask '" + current + "' is missing an item path");
				}
				entries.put(current, item);
				current = null;
				continue;
			}
			throw new IOException("unrecognized custom-masks.yml line: " + trimmed);
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
		Path parent = masksYml.getParent();
		Path tmp = parent == null
			? Path.of(masksYml.getFileName().toString() + ".tmp")
			: parent.resolve(masksYml.getFileName().toString() + ".tmp");
		Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
		try {
			Files.move(
				tmp,
				masksYml,
				StandardCopyOption.ATOMIC_MOVE,
				StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, masksYml, StandardCopyOption.REPLACE_EXISTING);
		}
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

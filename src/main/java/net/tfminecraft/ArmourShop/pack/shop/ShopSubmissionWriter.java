package net.tfminecraft.ArmourShop.pack.shop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.ArmourShop.pack.model.PackPaths;

/**
 * Upserts SkinSets after pack write — player {@code ps_*} lane or staff category lane.
 */
public final class ShopSubmissionWriter {

	private ShopSubmissionWriter() {}

	public static void write(ApprovedSubmission sub, Logger log) throws IOException {
		if (sub == null) {
			throw new IllegalArgumentException("submission is null");
		}
		if (sub.staff) {
			writeStaff(sub, log);
		} else {
			writePlayer(sub, log);
		}
	}

	private static void writePlayer(ApprovedSubmission sub, Logger log) throws IOException {
		String categoriesDir = Cache.categoriesPath;
		if (categoriesDir == null || categoriesDir.isBlank()) {
			throw new IllegalStateException("pack-apply.categories-path is not set in config.yml");
		}

		Path categoriesPath = Path.of(categoriesDir.trim());
		Files.createDirectories(categoriesPath);

		File categoriesYml = categoriesPath.getParent().resolve("categories.yml").toFile();
		ensureCategoriesIndex(categoriesYml);

		File psArmor = categoriesPath.resolve("ps_armor.yml").toFile();
		File psItems = categoriesPath.resolve("ps_items.yml").toFile();
		ensureCategoryFile(psArmor);
		ensureCategoryFile(psItems);

		String kind = sub.kind == null ? "" : sub.kind.trim().toLowerCase(Locale.ROOT);
		String slug = require(sub.slug, "slug");
		String display = sub.displayName == null || sub.displayName.isBlank()
			? slug
			: sub.displayName.trim();
		String permission = "armourshop.submission." + slug;
		List<String> colours = sub.nameColours == null ? List.of() : sub.nameColours;
		List<String> styles = sub.nameStyles == null ? List.of() : sub.nameStyles;
		boolean addName = sub.addName;
		String ns = PackPaths.playerNamespace();

		if ("armor_set".equals(kind)) {
			List<String> tiers = sub.tiers != null && !sub.tiers.isEmpty()
				? sub.tiers
				: (sub.baseSet != null && !sub.baseSet.isBlank()
					? List.of(sub.baseSet.trim())
					: List.of());
			if (tiers.isEmpty()) {
				throw new IllegalStateException(
					"missing tiers (and base_set fallback) for armor submission"
				);
			}
			for (String tier : tiers) {
				String rootKey = slug + "_" + tier;
				String tierDisplay = sub.displayNameForTier(tier);
				upsertArmor(
					psArmor,
					rootKey,
					tierDisplay,
					tier,
					permission,
					null,
					colours,
					styles,
					addName,
					ns
				);
			}
			if (log != null) {
				log.info("[shop] upserted armor_set slug=" + slug + " tiers=" + tiers
					+ " add-name=" + addName);
			}
			return;
		} else if (isItemKind(kind)) {
			String baseSet = require(sub.baseSet, "base_set");
			upsertItem(
				psItems,
				slug,
				display,
				baseSet,
				permission,
				null,
				colours,
				styles,
				addName,
				kind,
				ns
			);
		} else {
			throw new IllegalStateException("unsupported shop kind: " + kind);
		}

		if (log != null) {
			log.info("[shop] upserted " + kind + " slug=" + slug + " add-name=" + addName);
		}
	}

	private static void writeStaff(ApprovedSubmission sub, Logger log) throws IOException {
		String categoriesDir = Cache.categoriesPath;
		if (categoriesDir == null || categoriesDir.isBlank()) {
			throw new IllegalStateException("pack-apply.categories-path is not set in config.yml");
		}
		String category = require(sub.category, "category");
		Path categoriesPath = Path.of(categoriesDir.trim());
		Files.createDirectories(categoriesPath);
		File categoryFile = categoriesPath.resolve(category + ".yml").toFile();
		ensureCategoryFile(categoryFile);

		String kind = sub.kind == null ? "" : sub.kind.trim().toLowerCase(Locale.ROOT);
		String slug = require(sub.slug, "slug");
		String display = sub.displayName == null || sub.displayName.isBlank()
			? slug
			: sub.displayName.trim();
		List<String> colours = sub.nameColours == null ? List.of() : sub.nameColours;
		List<String> styles = sub.nameStyles == null ? List.of() : sub.nameStyles;
		boolean addName = sub.addName;
		String ns = sub.resolveNamespace();
		String permission = "none";

		if ("armor_set".equals(kind)) {
			List<String> tiers = sub.tiers != null && !sub.tiers.isEmpty()
				? sub.tiers
				: (sub.baseSet != null && !sub.baseSet.isBlank()
					? List.of(sub.baseSet.trim())
					: List.of());
			if (tiers.isEmpty()) {
				throw new IllegalStateException(
					"missing tiers (and base_set fallback) for armor submission"
				);
			}
			Map<String, String> tierScrolls = sub.tierScrolls == null
				? Map.of()
				: sub.tierScrolls;
			for (String tier : tiers) {
				String rootKey = slug + "_" + tier;
				String tierDisplay = sub.displayNameForTier(tier);
				String scroll = tierScrolls.get(tier.trim().toLowerCase(Locale.ROOT));
				if (scroll == null || scroll.isBlank()) {
					scroll = sub.scroll;
				}
				if (scroll == null || scroll.isBlank()) {
					throw new IllegalStateException(
						"missing scroll for staff armor tier '" + tier + "'"
					);
				}
				upsertArmor(
					categoryFile,
					rootKey,
					tierDisplay,
					tier,
					permission,
					scroll,
					colours,
					styles,
					addName,
					ns
				);
			}
			if (log != null) {
				log.info("[shop] staff armor_set slug=" + slug + " category=" + category
					+ " tiers=" + tiers + " ns=" + ns);
			}
			return;
		} else if (isItemKind(kind)) {
			String baseSet = require(sub.baseSet, "base_set");
			String scroll = require(sub.scroll, "scroll");
			upsertItem(
				categoryFile,
				slug,
				display,
				baseSet,
				permission,
				scroll,
				colours,
				styles,
				addName,
				kind,
				ns
			);
		} else {
			throw new IllegalStateException("unsupported shop kind: " + kind);
		}

		if (log != null) {
			log.info("[shop] staff " + kind + " slug=" + slug + " category=" + category
				+ " ns=" + ns);
		}
	}

	private static boolean isItemKind(String kind) {
		return "handheld".equals(kind)
			|| "large_handheld".equals(kind)
			|| "bow".equals(kind)
			|| "large_bow".equals(kind)
			|| "crossbow".equals(kind)
			|| "item_3d".equals(kind)
			|| "shield".equals(kind)
			|| "helmet_3d".equals(kind)
			|| "gun".equals(kind)
			|| "book".equals(kind);
	}

	/**
	 * Remove SkinSet key(s) for a submission. For armor, removes each per-tier key
	 * ({@code slug_tier}) from ps_armor plus the bare {@code slug} key (legacy single-pack
	 * submissions). For items, removes the bare {@code slug} key from ps_items.
	 */
	public static void remove(String slug, String kind, List<String> tiers, Logger log)
		throws IOException
	{
		String categoriesDir = Cache.categoriesPath;
		if (categoriesDir == null || categoriesDir.isBlank()) {
			throw new IllegalStateException("pack-apply.categories-path is not set in config.yml");
		}
		String key = require(slug, "slug");
		Path categoriesPath = Path.of(categoriesDir.trim());
		File psArmor = categoriesPath.resolve("ps_armor.yml").toFile();
		File psItems = categoriesPath.resolve("ps_items.yml").toFile();
		boolean changed = false;
		String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);

		if ("armor_set".equals(k)) {
			if (psArmor.exists()) {
				List<String> tierList = tiers == null ? List.of() : tiers;
				for (String tier : tierList) {
					if (tier == null || tier.isBlank()) {
						continue;
					}
					changed |= clearRoot(psArmor, key + "_" + tier.trim());
				}
				// legacy: submissions written before per-tier packs used the bare slug
				changed |= clearRoot(psArmor, key);
			}
		} else if (psItems.exists()) {
			changed |= clearRoot(psItems, key);
		}

		if (log != null) {
			log.info("[shop] removed SkinSet key(s) slug=" + key + " kind=" + k
				+ " changed=" + changed);
		}
	}

	/**
	 * Remove staff SkinSet key(s) from {@code Categories/{category}.yml}.
	 * Does not touch legacy {@code tfmc_armor} or player {@code ps_*} files.
	 */
	public static void removeStaff(
		String slug,
		String kind,
		List<String> tiers,
		String category,
		Logger log
	) throws IOException {
		String categoriesDir = Cache.categoriesPath;
		if (categoriesDir == null || categoriesDir.isBlank()) {
			throw new IllegalStateException("pack-apply.categories-path is not set in config.yml");
		}
		String key = require(slug, "slug");
		String cat = require(category, "category");
		File file = Path.of(categoriesDir.trim()).resolve(cat + ".yml").toFile();
		if (!file.exists()) {
			if (log != null) {
				log.warning("[shop] staff remove: category file missing "
					+ file.getAbsolutePath());
			}
			return;
		}
		boolean changed = false;
		String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
		if ("armor_set".equals(k)) {
			List<String> tierList = tiers == null ? List.of() : tiers;
			for (String tier : tierList) {
				if (tier == null || tier.isBlank()) {
					continue;
				}
				changed |= clearRoot(file, key + "_" + tier.trim());
			}
			changed |= clearRoot(file, key);
		} else {
			changed |= clearRoot(file, key);
		}
		if (log != null) {
			log.info("[shop] staff removed SkinSet key(s) slug=" + key
				+ " kind=" + k + " category=" + cat + " changed=" + changed);
		}
	}

	/** Legacy overload: remove SkinSet key from both ps_armor and ps_items (no-op if missing). */
	public static void remove(String slug, Logger log) throws IOException {
		String categoriesDir = Cache.categoriesPath;
		if (categoriesDir == null || categoriesDir.isBlank()) {
			throw new IllegalStateException("pack-apply.categories-path is not set in config.yml");
		}
		String key = require(slug, "slug");
		Path categoriesPath = Path.of(categoriesDir.trim());
		File psArmor = categoriesPath.resolve("ps_armor.yml").toFile();
		File psItems = categoriesPath.resolve("ps_items.yml").toFile();
		boolean changed = false;
		if (psArmor.exists()) {
			changed |= clearRoot(psArmor, key);
		}
		if (psItems.exists()) {
			changed |= clearRoot(psItems, key);
		}
		if (log != null) {
			log.info("[shop] removed SkinSet key=" + key + " changed=" + changed);
		}
	}

	private static boolean clearRoot(File file, String root) throws IOException {
		FileConfiguration config = loadOrEmpty(file);
		if (!config.contains(root)) {
			return false;
		}
		config.set(root, null);
		config.save(file);
		return true;
	}

	private static void ensureCategoriesIndex(File file) throws IOException {
		YamlConfiguration config = new YamlConfiguration();
		if (file.exists()) {
			try {
				config.load(file);
			} catch (InvalidConfigurationException e) {
				throw new IOException("invalid yaml: " + file.getAbsolutePath(), e);
			}
		}
		boolean dirty = false;
		if (!config.contains("ps_armor")) {
			config.set("ps_armor.name", "Player Armor");
			config.set("ps_armor.colour", "#a0a0a0");
			config.set("ps_armor.item", "ia.tfmc_armor:decorated_iron_chestplate");
			dirty = true;
		}
		if (!config.contains("ps_items")) {
			config.set("ps_items.name", "Player Items");
			config.set("ps_items.colour", "#a0a0a0");
			config.set("ps_items.item", "m.sword.iron_sword");
			config.set("ps_items.is-item", true);
			dirty = true;
		}
		if (dirty) {
			File parent = file.getParentFile();
			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}
			config.save(file);
		}
	}

	private static void ensureCategoryFile(File file) throws IOException {
		if (!file.exists()) {
			File parent = file.getParentFile();
			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}
			new YamlConfiguration().save(file);
		}
	}

	/**
	 * Upserts one tier's armor SkinSet. {@code rootKey} is {@code {slug}_{tier}} and is
	 * also the pack slug used for the IA piece ids (matches the per-tier pack write).
	 */
	private static void upsertArmor(
		File file,
		String rootKey,
		String display,
		String tier,
		String permission,
		String scroll,
		List<String> colours,
		List<String> styles,
		boolean addName,
		String namespace
	) throws IOException {
		FileConfiguration config = loadOrEmpty(file);
		String root = rootKey;
		String ns = namespace == null || namespace.isBlank()
			? PackPaths.playerNamespace()
			: namespace.trim();
		config.set(root + ".name", display);
		writeColour(config, root, colours);
		writeStyles(config, root, styles);
		config.set(root + ".add-name", addName);
		config.set(root + ".set", tier);
		config.set(root + ".permission", permission);
		config.set(root + ".scroll", scroll);
		config.set(root + ".helmet", "ia." + ns + ":" + rootKey + "_helmet");
		config.set(root + ".chestplate", "ia." + ns + ":" + rootKey + "_chestplate");
		config.set(root + ".leggings", "ia." + ns + ":" + rootKey + "_leggings");
		config.set(root + ".boots", "ia." + ns + ":" + rootKey + "_boots");
		config.set(root + ".item", null);
		config.save(file);
	}

	private static void upsertItem(
		File file,
		String slug,
		String display,
		String baseSet,
		String permission,
		String scroll,
		List<String> colours,
		List<String> styles,
		boolean addName,
		String kind,
		String namespace
	) throws IOException {
		FileConfiguration config = loadOrEmpty(file);
		String root = slug;
		String ns = namespace == null || namespace.isBlank()
			? PackPaths.playerNamespace()
			: namespace.trim();
		config.set(root + ".name", display);
		writeColour(config, root, colours);
		writeStyles(config, root, styles);
		config.set(root + ".add-name", addName);
		config.set(root + ".set", baseSet);
		config.set(root + ".permission", permission);
		config.set(root + ".scroll", scroll);
		if ("gun".equals(kind)) {
			config.set(root + ".item", "gunskin(" + slug + ")");
		} else {
			config.set(root + ".item", "ia." + ns + ":" + slug);
		}
		config.set(root + ".helmet", null);
		config.set(root + ".chestplate", null);
		config.set(root + ".leggings", null);
		config.set(root + ".boots", null);
		config.save(file);
	}

	private static void writeColour(FileConfiguration config, String root, List<String> colours) {
		if (colours == null || colours.isEmpty()) {
			config.set(root + ".colour", null);
			return;
		}
		if (colours.size() == 1) {
			config.set(root + ".colour", colours.get(0));
		} else {
			config.set(root + ".colour", new ArrayList<>(colours));
		}
	}

	private static void writeStyles(FileConfiguration config, String root, List<String> styles) {
		if (styles == null || styles.isEmpty()) {
			config.set(root + ".styles", null);
		} else {
			config.set(root + ".styles", new ArrayList<>(styles));
		}
	}

	private static FileConfiguration loadOrEmpty(File file) throws IOException {
		YamlConfiguration config = new YamlConfiguration();
		if (file.exists()) {
			try {
				config.load(file);
			} catch (InvalidConfigurationException e) {
				throw new IOException("invalid yaml: " + file.getAbsolutePath(), e);
			}
		}
		return config;
	}

	private static String require(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("missing " + field);
		}
		return value.trim();
	}
}

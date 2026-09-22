package net.tfminecraft.ArmourShop.pack.delete;


import net.tfminecraft.ArmourShop.pack.model.BowFrames;
import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import net.tfminecraft.ArmourShop.pack.writer.flat.BookWriter;
import net.tfminecraft.ArmourShop.pack.writer.gun.GunWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import net.tfminecraft.ArmourShop.Cache;

/**
 * Deletes pack files for a slug under a given ItemsAdder namespace (best-effort).
 */
public final class PackSubmissionRemover {

	private static final String[] ARMOR_ICON_STEMS = {
		"helmet", "chestplate", "leggings", "boots"
	};

	private PackSubmissionRemover() {}

	/**
	 * Removes pack files for a submission under the player namespace
	 * ({@link PackPaths#NAMESPACE}).
	 */
	public static List<Path> remove(
		Path contentsRoot,
		String kind,
		String slug,
		List<String> tiers,
		Logger log
	) throws IOException {
		return remove(contentsRoot, PackPaths.playerNamespace(), kind, slug, tiers, log);
	}

	/**
	 * Removes pack files for a submission. For armor_set, deletes files for each
	 * per-tier pack slug ({@code slug_tier}) plus a legacy bare-slug attempt (for
	 * submissions written before per-tier packs existed). Non-armor kinds are
	 * unchanged (single bare-slug pack).
	 */
	public static List<Path> remove(
		Path contentsRoot,
		String namespace,
		String kind,
		String slug,
		List<String> tiers,
		Logger log
	) throws IOException {
		if (contentsRoot == null) {
			throw new IllegalArgumentException("contentsRoot is null");
		}
		String ns = namespace == null || namespace.isBlank()
			? PackPaths.playerNamespace()
			: namespace.trim();
		String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
		String s = slug == null ? "" : slug.trim();
		if (s.isEmpty()) {
			throw new IllegalArgumentException("slug is blank");
		}

		List<Path> removed = new ArrayList<>();

		if ("armor_set".equals(k)) {
			List<String> tierList = tiers == null ? List.of() : tiers;
			for (String tier : tierList) {
				if (tier == null || tier.isBlank()) {
					continue;
				}
				removeArmorPack(contentsRoot, ns, s + "_" + tier.trim(), removed);
			}
			// legacy: submissions written before per-tier packs used the bare slug
			removeArmorPack(contentsRoot, ns, s, removed);
		} else if ("gun".equals(k)) {
			removed.addAll(GunWriter.remove(contentsRoot, ns, s, gunsSkinsYmlOrNull()));
		} else if ("book".equals(k)) {
			removed.addAll(BookWriter.remove(contentsRoot, ns, s));
		} else {
			removeNonArmorPack(contentsRoot, ns, s, removed);
		}

		if (log != null) {
			log.info("[pack-delete] removed " + removed.size()
				+ " path(s) for slug=" + s + " kind=" + k + " ns=" + ns
				+ (tiers != null && !tiers.isEmpty() ? " tiers=" + tiers : ""));
		}
		return removed;
	}

	/** @deprecated use {@link #remove(Path, String, String, List, Logger)} */
	@Deprecated
	public static List<Path> remove(Path contentsRoot, String kind, String slug, Logger log)
		throws IOException
	{
		return remove(contentsRoot, kind, slug, List.of(), log);
	}

	private static void removeArmorPack(
		Path contentsRoot,
		String namespace,
		String packSlug,
		List<Path> removed
	) throws IOException {
		Path configs = PackPaths.configsDir(contentsRoot, namespace);
		Path icons = PackPaths.armorIconsDir(contentsRoot, namespace);
		Path layers = PackPaths.armorLayersDir(contentsRoot, namespace);

		deleteQuiet(configs.resolve(packSlug + ".yml"), removed);
		for (String stem : ARMOR_ICON_STEMS) {
			deleteQuiet(icons.resolve(packSlug + "_" + stem + ".png"), removed);
		}
		deleteQuiet(layers.resolve(packSlug + "_layer_1.png"), removed);
		deleteQuiet(layers.resolve(packSlug + "_layer_2.png"), removed);
		// 3D helmet assets (when present)
		String helmetId = packSlug + "_helmet";
		deleteQuiet(
			PackPaths.itemTexturesDir(contentsRoot, namespace).resolve(helmetId + ".png"),
			removed
		);
		deleteQuiet(
			PackPaths.itemModelsDir(contentsRoot, namespace).resolve(helmetId + ".json"),
			removed
		);
	}

	private static void removeNonArmorPack(
		Path contentsRoot,
		String namespace,
		String s,
		List<Path> removed
	) throws IOException {
		Path configs = PackPaths.configsDir(contentsRoot, namespace);
		Path itemTex = PackPaths.itemTexturesDir(contentsRoot, namespace);
		Path models = PackPaths.itemModelsDir(contentsRoot, namespace);

		deleteQuiet(configs.resolve(s + ".yml"), removed);
		deleteQuiet(itemTex.resolve(s + ".png"), removed);
		deleteQuiet(models.resolve(s + ".json"), removed);
		deleteQuiet(models.resolve(s + "_blocking.json"), removed);
		for (String stem : BowFrames.CROSSBOW_STEMS) {
			deleteQuiet(
				itemTex.resolve(BowFrames.textureFileName(s, stem)),
				removed
			);
			// large_bow frame models (and any stray pull models)
			deleteQuiet(
				models.resolve(s + BowFrames.fileSuffix(stem) + ".json"),
				removed
			);
		}
		deleteQuiet(itemTex.resolve(s + "_arrow.png"), removed);
	}

	private static void deleteQuiet(Path path, List<Path> removed) throws IOException {
		if (Files.deleteIfExists(path)) {
			removed.add(path);
		}
	}

	private static Path gunsSkinsYmlOrNull() {
		String skins = Cache.gunsSkinsYmlPath;
		if (skins == null || skins.isBlank()) {
			return null;
		}
		return Path.of(skins.trim());
	}
}

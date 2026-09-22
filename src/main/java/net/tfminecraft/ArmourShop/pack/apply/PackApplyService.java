package net.tfminecraft.ArmourShop.pack.apply;


import net.tfminecraft.ArmourShop.pack.model.BowFrames;
import net.tfminecraft.ArmourShop.pack.model.PackKind;
import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import net.tfminecraft.ArmourShop.pack.model.PackSubmission;
import net.tfminecraft.ArmourShop.pack.util.Model3dUtil;
import net.tfminecraft.ArmourShop.pack.writer.armor.ArmorSetWriter;
import net.tfminecraft.ArmourShop.pack.writer.bow.BowWriter;
import net.tfminecraft.ArmourShop.pack.writer.bow.LargeBowWriter;
import net.tfminecraft.ArmourShop.pack.writer.flat.BookWriter;
import net.tfminecraft.ArmourShop.pack.writer.flat.FlatItemWriter;
import net.tfminecraft.ArmourShop.pack.writer.gun.GunWriter;
import net.tfminecraft.ArmourShop.pack.writer.large.LargeHandheldWriter;
import net.tfminecraft.ArmourShop.pack.writer.model3d.Item3dWriter;
import net.tfminecraft.ArmourShop.pack.writer.model3d.ShieldWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.DownloadResult;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ListResult;

/**
 * Pull approved submissions and write IA pack files.
 * Shop YAML + LuckPerms run on the main thread after this returns.
 */
public final class PackApplyService {

	private static final String[] ARMOR_BODY_STEMS = {
		"chestplate", "leggings", "boots", "layer_1", "layer_2"
	};

	private PackApplyService() {}

	public static final class ApplySummary {
		public final int written;
		public final int skipped;
		public final int failed;
		public final List<String> messages;
		/** Submissions that successfully wrote pack files (for shop/LP on main thread). */
		public final List<ApprovedSubmission> writtenSubmissions;

		public ApplySummary(
			int written,
			int skipped,
			int failed,
			List<String> messages,
			List<ApprovedSubmission> writtenSubmissions
		) {
			this.written = written;
			this.skipped = skipped;
			this.failed = failed;
			this.messages = messages;
			this.writtenSubmissions = writtenSubmissions == null
				? List.of()
				: List.copyOf(writtenSubmissions);
		}
	}

	public static ApplySummary pullAndWrite(Logger log) {
		List<String> messages = new ArrayList<>();
		String contents = Cache.iaContentsPath;
		if (contents == null || contents.isBlank()) {
			String msg = "pack-apply.ia-contents-path is not set in config.yml";
			messages.add(msg);
			if (log != null) {
				log.warning("[pack] " + msg);
			}
			return new ApplySummary(0, 0, 1, messages, List.of());
		}
		Path contentsRoot = Path.of(contents.trim());

		ListResult list = ProvinceSystemClient.listApproved();
		if (!list.ok) {
			String msg = "list approved failed: " + list.error;
			messages.add(msg);
			if (log != null) {
				log.warning("[pack] " + msg);
			}
			return new ApplySummary(0, 0, 1, messages, List.of());
		}

		int written = 0;
		int skipped = 0;
		int failed = 0;
		List<ApprovedSubmission> writtenSubs = new ArrayList<>();

		if (list.submissions.isEmpty()) {
			messages.add("No approved submissions pending apply.");
			if (log != null) {
				log.info("[pack] No approved submissions pending apply.");
			}
			return new ApplySummary(0, 0, 0, messages, List.of());
		}

		for (ApprovedSubmission sub : list.submissions) {
			String kind = sub.kind == null ? "" : sub.kind.trim().toLowerCase(Locale.ROOT);
			try {
				if ("armor_set".equals(kind)) {
					List<String> tiers = resolveTiers(sub);
					Map<String, Map<String, byte[]>> tierFiles = downloadArmorFiles(sub, tiers);
					for (String tier : tiers) {
						writeArmorTier(contentsRoot, sub, tier, tierFiles.get(tier));
					}

					written++;
					writtenSubs.add(sub);
					String msg = "wrote armor_set id=" + sub.id
						+ " tiers=" + tiers
						+ " uuid=" + sub.playerUuid;
					messages.add(msg);
					if (log != null) {
						log.info("[pack] " + msg);
					}
				} else {
					Map<String, byte[]> files = downloadFiles(sub, kind);
					writeKind(contentsRoot, sub, kind, files);

					written++;
					writtenSubs.add(sub);
					String msg = "wrote " + kind + " slug=" + sub.slug
						+ " base_set=" + sub.baseSet
						+ " uuid=" + sub.playerUuid
						+ " id=" + sub.id;
					messages.add(msg);
					if (log != null) {
						log.info("[pack] " + msg);
					}
				}
			} catch (Exception e) {
				failed++;
				String msg = "fail " + sub.id + " (" + kind + "): " + e.getMessage();
				messages.add(msg);
				if (log != null) {
					log.warning("[pack] " + msg);
				}
			}
		}

		return new ApplySummary(written, skipped, failed, messages, writtenSubs);
	}

	/** Armor tiers from the submission, falling back to a single-tier list from base_set. */
	static List<String> resolveTiers(ApprovedSubmission sub) {
		if (sub.tiers != null && !sub.tiers.isEmpty()) {
			return sub.tiers;
		}
		if (sub.baseSet != null && !sub.baseSet.isBlank()) {
			return List.of(sub.baseSet.trim());
		}
		throw new IllegalStateException("no tiers (and no base_set fallback) for armor submission");
	}

	/**
	 * Downloads per-tier armor files. Flat helmet → {@code {tier}_helmet.png};
	 * 3D helmet → {@code {tier}_helmet_model.json} + {@code {tier}_helmet_texture.png}.
	 */
	private static Map<String, Map<String, byte[]>> downloadArmorFiles(
		ApprovedSubmission sub,
		List<String> tiers
	) throws Exception {
		if (sub.files == null || sub.files.isEmpty()) {
			throw new IllegalStateException("no files listed");
		}
		Set<String> available = new HashSet<>(sub.files);
		Map<String, Map<String, byte[]>> out = new LinkedHashMap<>();
		for (String tier : tiers) {
			Map<String, byte[]> tierFiles = new LinkedHashMap<>();
			boolean h3d = sub.isHelmet3dTier(tier);
			if (h3d) {
				String modelName = tier + "_helmet_model.json";
				String texName = tier + "_helmet_texture.png";
				if (!available.contains(modelName)) {
					throw new IllegalStateException("missing armor file: " + modelName);
				}
				if (!available.contains(texName)) {
					throw new IllegalStateException("missing armor file: " + texName);
				}
				DownloadResult modelDl = ProvinceSystemClient.downloadSubmissionFile(
					sub.id, modelName
				);
				if (!modelDl.ok) {
					throw new IllegalStateException("download " + modelName + ": " + modelDl.error);
				}
				DownloadResult texDl = ProvinceSystemClient.downloadSubmissionFile(
					sub.id, texName
				);
				if (!texDl.ok) {
					throw new IllegalStateException("download " + texName + ": " + texDl.error);
				}
				tierFiles.put(Model3dUtil.HELMET_MODEL_STEM, modelDl.data);
				tierFiles.put(Model3dUtil.HELMET_TEXTURE_STEM, texDl.data);
			} else {
				String filename = tier + "_helmet.png";
				if (!available.contains(filename)) {
					throw new IllegalStateException("missing armor file: " + filename);
				}
				DownloadResult dl = ProvinceSystemClient.downloadSubmissionFile(
					sub.id, filename
				);
				if (!dl.ok) {
					throw new IllegalStateException("download " + filename + ": " + dl.error);
				}
				tierFiles.put("helmet", dl.data);
			}
			for (String stem : ARMOR_BODY_STEMS) {
				String filename = tier + "_" + stem + ".png";
				if (!available.contains(filename)) {
					throw new IllegalStateException("missing armor file: " + filename);
				}
				DownloadResult dl = ProvinceSystemClient.downloadSubmissionFile(sub.id, filename);
				if (!dl.ok) {
					throw new IllegalStateException("download " + filename + ": " + dl.error);
				}
				tierFiles.put(stem, dl.data);
			}
			out.put(tier, tierFiles);
		}
		return out;
	}

	/** Writes one tier's armor set as its own pack slug ({@code id_tier}). */
	private static void writeArmorTier(
		Path contentsRoot,
		ApprovedSubmission sub,
		String tier,
		Map<String, byte[]> files
	) throws Exception {
		if (files == null) {
			throw new IllegalStateException("missing files for tier " + tier);
		}
		boolean h3d = files.containsKey(Model3dUtil.HELMET_MODEL_STEM);
		if (h3d) {
			if (!files.containsKey(Model3dUtil.HELMET_TEXTURE_STEM)) {
				throw new IllegalStateException(
					"missing helmet_texture for tier " + tier
				);
			}
		} else if (!files.containsKey("helmet")) {
			throw new IllegalStateException("missing helmet for tier " + tier);
		}
		for (String stem : ARMOR_BODY_STEMS) {
			if (!files.containsKey(stem)) {
				throw new IllegalStateException(
					"missing armor stem: " + stem + " for tier " + tier
				);
			}
		}
		String ns = sub.resolveNamespace();
		Map<String, byte[]> packFiles = rewriteModelFiles(files, ns);
		String packSlug = sub.id + "_" + tier;
		String display = sub.displayNameForTier(tier);
		ArmorSetWriter.write(
			contentsRoot,
			new PackSubmission(packSlug, display, PackKind.ARMOR_SET, packFiles),
			ns
		);
	}

	private static Map<String, byte[]> downloadFiles(ApprovedSubmission sub, String kind)
		throws Exception
	{
		if (sub.files == null || sub.files.isEmpty()) {
			throw new IllegalStateException("no files listed");
		}
		Map<String, byte[]> out = new LinkedHashMap<>();
		String slug = sub.slug == null ? "" : sub.slug.trim();
		for (String filename : sub.files) {
			String stem = stemFromFilename(kind, slug, filename);
			if (stem == null) {
				// e.g. slug_arrow.png duplicate of charged — skip unknown extras
				continue;
			}
			DownloadResult dl = ProvinceSystemClient.downloadSubmissionFile(sub.id, filename);
			if (!dl.ok) {
				throw new IllegalStateException(
					"download " + filename + ": " + dl.error
				);
			}
			out.put(stem, dl.data);
		}
		if (out.isEmpty()) {
			throw new IllegalStateException("no recognized files");
		}
		return out;
	}

	/**
	 * Maps a downloaded filename to its stem for non-armor kinds. armor_set files are
	 * tier-prefixed ({@code {tier}_helmet.png}) and are handled separately by
	 * {@link #downloadArmorFiles}, so armor_set is not resolved here.
	 */
	static String stemFromFilename(String kind, String slug, String filename) {
		if (filename == null) {
			return null;
		}
		String name = filename.trim();
		if ("book".equals(kind)) {
			String prefix = slug + "_";
			if (name.startsWith(prefix) && name.endsWith(".png")) {
				String suffix = name.substring(prefix.length(), name.length() - 4);
				if (BookWriter.UNSIGNED_STEM.equals(suffix)
					|| BookWriter.SIGNED_STEM.equals(suffix)) {
					return suffix;
				}
			}
			return null;
		}
		if ("bow".equals(kind) || "large_bow".equals(kind) || "crossbow".equals(kind)) {
			if ("large_bow".equals(kind) && name.endsWith(".json")) {
				if (name.equals(slug + ".json")) {
					return LargeBowWriter.MODEL_STEM_PREFIX;
				}
				String prefix = slug + "_";
				if (name.startsWith(prefix) && name.endsWith(".json")) {
					String suffix = name.substring(prefix.length(), name.length() - 5);
					if ("0".equals(suffix)) {
						return LargeBowWriter.MODEL_STEM_PREFIX + "_0";
					}
					if ("1".equals(suffix)) {
						return LargeBowWriter.MODEL_STEM_PREFIX + "_1";
					}
					if ("2".equals(suffix)) {
						return LargeBowWriter.MODEL_STEM_PREFIX + "_2";
					}
				}
				return null;
			}
			if (name.equals(slug + ".png")) {
				return BowFrames.STANDBY;
			}
			String prefix = slug + "_";
			if (!name.startsWith(prefix) || !name.endsWith(".png")) {
				return null;
			}
			String suffix = name.substring(prefix.length(), name.length() - 4);
			if ("0".equals(suffix)) {
				return BowFrames.PULL_0;
			}
			if ("1".equals(suffix)) {
				return BowFrames.PULL_1;
			}
			if ("2".equals(suffix)) {
				return BowFrames.PULL_2;
			}
			if ("charged".equals(suffix) || "arrow".equals(suffix)) {
				return BowFrames.CHARGED;
			}
			return null;
		}
		// single-texture / 3D kinds: {slug}.png → texture, {slug}.json → model
		if (name.equals(slug + ".png")) {
			return FlatItemWriter.TEXTURE_STEM;
		}
		if ("gun".equals(kind)) {
			String prefix = slug + "_";
			if (name.startsWith(prefix) && name.endsWith(".json")) {
				String suffix = name.substring(prefix.length(), name.length() - 5);
				if (GunWriter.CARRY_STEM.equals(suffix)
					|| GunWriter.RELOAD_STEM.equals(suffix)
					|| GunWriter.AIM_STEM.equals(suffix)
					|| GunWriter.AIM_CHARGED_STEM.equals(suffix)) {
					return suffix;
				}
			}
			return null;
		}
		if ("shield".equals(kind) && name.equals(slug + "_blocking.json")) {
			return ShieldWriter.MODEL_BLOCKING_STEM;
		}
		if (name.equals(slug + ".json")) {
			return Model3dUtil.MODEL_STEM;
		}
		return null;
	}

	private static void writeKind(
		Path contentsRoot,
		ApprovedSubmission sub,
		String kind,
		Map<String, byte[]> files
	) throws Exception {
		String slug = sub.slug;
		String display = sub.displayName == null || sub.displayName.isBlank()
			? slug
			: sub.displayName;
		String ns = sub.resolveNamespace();
		Map<String, byte[]> packFiles = rewriteModelFiles(files, ns);

		switch (kind) {
			case "handheld":
				if (!packFiles.containsKey(FlatItemWriter.TEXTURE_STEM)) {
					throw new IllegalStateException("missing texture");
				}
				FlatItemWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.HANDHELD, packFiles),
					ns
				);
				return;
			case "large_handheld":
				if (!packFiles.containsKey(LargeHandheldWriter.TEXTURE_STEM)) {
					throw new IllegalStateException("missing texture");
				}
				if (!packFiles.containsKey(Model3dUtil.MODEL_STEM)) {
					throw new IllegalStateException("missing model");
				}
				LargeHandheldWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.LARGE_HANDHELD, packFiles),
					ns
				);
				return;
			case "bow":
				requireStems(packFiles, BowFrames.BOW_STEMS);
				BowWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.BOW, packFiles),
					ns
				);
				return;
			case "large_bow":
				requireStems(packFiles, BowFrames.BOW_STEMS);
				requireStems(packFiles, new String[]{
					LargeBowWriter.MODEL_STEM_PREFIX,
					LargeBowWriter.MODEL_STEM_PREFIX + "_0",
					LargeBowWriter.MODEL_STEM_PREFIX + "_1",
					LargeBowWriter.MODEL_STEM_PREFIX + "_2"
				});
				LargeBowWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.LARGE_BOW, packFiles),
					ns
				);
				return;
			case "crossbow":
				requireStems(packFiles, BowFrames.CROSSBOW_STEMS);
				BowWriter.writeCrossbow(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.CROSSBOW, packFiles),
					ns
				);
				return;
			case "item_3d":
				requireModel3d(packFiles);
				Item3dWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.ITEM_3D, packFiles),
					ns
				);
				return;
			case "shield":
				requireModel3d(packFiles);
				if (!packFiles.containsKey(ShieldWriter.MODEL_BLOCKING_STEM)) {
					throw new IllegalStateException("missing model_blocking");
				}
				ShieldWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.SHIELD, packFiles),
					ns
				);
				return;
			case "helmet_3d":
				requireModel3d(packFiles);
				Item3dWriter.writeHelmet3d(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.HELMET_3D, packFiles),
					ns
				);
				return;
			case "gun":
				requireGun(packFiles);
				GunWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.GUN, packFiles),
					sub.baseSet,
					requireGunsSkinsYml(),
					ns
				);
				return;
			case "book":
				if (!packFiles.containsKey(BookWriter.UNSIGNED_STEM)) {
					throw new IllegalStateException("missing unsigned");
				}
				if (!packFiles.containsKey(BookWriter.SIGNED_STEM)) {
					throw new IllegalStateException("missing signed");
				}
				BookWriter.write(
					contentsRoot,
					new PackSubmission(slug, display, PackKind.BOOK, packFiles),
					ns
				);
				return;
			default:
				throw new IllegalStateException("unsupported kind: " + kind);
		}
	}

	/** Rewrite player-ns prefixes in model JSON when writing a staff namespace. */
	static Map<String, byte[]> rewriteModelFiles(
		Map<String, byte[]> files,
		String namespace
	) {
		if (files == null || files.isEmpty()) {
			return files;
		}
		if (namespace == null
			|| namespace.isBlank()
			|| PackPaths.playerNamespace().equals(namespace.trim())) {
			return files;
		}
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			String stem = e.getKey();
			byte[] data = e.getValue();
			if (data != null && isModelStem(stem)) {
				out.put(stem, Model3dUtil.rewriteNamespacePrefix(data, namespace));
			} else {
				out.put(stem, data);
			}
		}
		return out;
	}

	private static boolean isModelStem(String stem) {
		if (stem == null) {
			return false;
		}
		String s = stem.trim().toLowerCase(Locale.ROOT);
		return Model3dUtil.MODEL_STEM.equals(s)
			|| Model3dUtil.HELMET_MODEL_STEM.equals(s)
			|| ShieldWriter.MODEL_BLOCKING_STEM.equals(s)
			|| s.startsWith("model")
			|| GunWriter.CARRY_STEM.equals(s)
			|| GunWriter.RELOAD_STEM.equals(s)
			|| GunWriter.AIM_STEM.equals(s)
			|| GunWriter.AIM_CHARGED_STEM.equals(s);
	}

	private static void requireGun(Map<String, byte[]> files) {
		if (!files.containsKey(GunWriter.TEXTURE_STEM)) {
			throw new IllegalStateException("missing texture");
		}
		for (String stem : GunWriter.MODEL_STEMS) {
			if (!files.containsKey(stem)) {
				throw new IllegalStateException("missing stem: " + stem);
			}
		}
		if (!files.containsKey(GunWriter.AIM_CHARGED_STEM)) {
			throw new IllegalStateException("missing stem: aim_charged");
		}
	}

	static Path requireGunsSkinsYml() {
		String skins = Cache.gunsSkinsYmlPath;
		if (skins == null || skins.isBlank()) {
			throw new IllegalStateException(
				"pack-apply.guns-skins-yml is not set in config.yml"
			);
		}
		return Path.of(skins.trim());
	}

	private static void requireModel3d(Map<String, byte[]> files) {
		if (!files.containsKey(Model3dUtil.TEXTURE_STEM)) {
			throw new IllegalStateException("missing texture");
		}
		if (!files.containsKey(Model3dUtil.MODEL_STEM)) {
			throw new IllegalStateException("missing model");
		}
	}

	private static void requireStems(Map<String, byte[]> files, String[] stems) {
		for (String stem : stems) {
			if (!files.containsKey(stem)) {
				throw new IllegalStateException("missing stem: " + stem);
			}
		}
	}
}

package net.tfminecraft.armourshop.pack.reload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.pack.model.PackPaths;

/**
 * Decides which queued submission ids may be marked applied after a pack zip.
 *
 * <p>The pending-reload queue is only ids whose pack files this server already
 * wrote. An id is acked when it is still approved and its ItemsAdder config
 * exists. Approved ids that were never written are not added here.
 */
public final class ApplyAckPlanner {

	private ApplyAckPlanner() {}

	public static final class Plan {
		private final List<String> ack;
		private final List<String> missingFiles;
		private final List<String> notApproved;

		Plan(List<String> ack, List<String> missingFiles, List<String> notApproved) {
			this.ack = List.copyOf(ack);
			this.missingFiles = List.copyOf(missingFiles);
			this.notApproved = List.copyOf(notApproved);
		}

		/** Written, still approved, config file present. Safe to mark applied. */
		public List<String> ack() {
			return ack;
		}

		/**
		 * Still approved, but the pack config is not on disk. Do not ack.
		 * Drop from the local queue so a later zip cannot mark them applied.
		 * They stay approved until a pack pull writes them.
		 */
		public List<String> missingFiles() {
			return missingFiles;
		}

		/** No longer approved (revoked or already applied). Drop. Do not ack. */
		public List<String> notApproved() {
			return notApproved;
		}
	}

	public static Plan plan(
		List<String> queued,
		List<ApprovedSubmission> approved,
		Path contentsRoot
	) {
		Map<String, ApprovedSubmission> byId = new LinkedHashMap<>();
		if (approved != null) {
			for (ApprovedSubmission sub : approved) {
				if (sub == null || sub.id == null || sub.id.isBlank()) {
					continue;
				}
				byId.put(sub.id.trim(), sub);
			}
		}

		List<String> ack = new ArrayList<>();
		List<String> missingFiles = new ArrayList<>();
		List<String> notApproved = new ArrayList<>();
		if (queued == null) {
			return new Plan(ack, missingFiles, notApproved);
		}
		for (String raw : queued) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String id = raw.trim();
			ApprovedSubmission sub = byId.get(id);
			if (sub == null) {
				notApproved.add(id);
				continue;
			}
			if (contentsRoot != null && hasPackConfig(contentsRoot, sub)) {
				ack.add(id);
			} else {
				missingFiles.add(id);
			}
		}
		return new Plan(ack, missingFiles, notApproved);
	}

	/**
	 * True when the ItemsAdder config this submission's writer would emit is
	 * present. Armor needs one config per tier ({@code {slug}_{tier}.yml}).
	 * Every other kind needs {@code {slug}.yml}.
	 */
	static boolean hasPackConfig(Path contentsRoot, ApprovedSubmission sub) {
		if (contentsRoot == null || sub == null) {
			return false;
		}
		String namespace = sub.resolveNamespace();
		Path configs = PackPaths.configsDir(contentsRoot, namespace);
		String kind = sub.kind == null ? "" : sub.kind.trim().toLowerCase(Locale.ROOT);
		if ("armor_set".equals(kind)) {
			if (sub.tiers == null || sub.tiers.isEmpty() || sub.slug == null || sub.slug.isBlank()) {
				return false;
			}
			for (String tier : sub.tiers) {
				if (tier == null || tier.isBlank()) {
					return false;
				}
				String name = sub.slug.trim() + "_" + tier.trim() + ".yml";
				if (!Files.isRegularFile(configs.resolve(name))) {
					return false;
				}
			}
			return true;
		}
		String slug = sub.slug == null ? "" : sub.slug.trim();
		if (slug.isEmpty()) {
			return false;
		}
		return Files.isRegularFile(configs.resolve(slug + ".yml"));
	}
}

package net.tfminecraft.ArmourShop.pack.delete;


import net.tfminecraft.ArmourShop.pack.shop.LuckPermsGrant;
import net.tfminecraft.ArmourShop.pack.shop.ShopSubmissionWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.ArmourShop.ArmourShop;
import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.PluginSubmission;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.PluginSubmissionResult;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.SimpleResult;

/**
 * Player-lane delete: best-effort pack/shop/LP clear, then hard-delete on the skins API.
 */
public final class SubmissionDeleteRunner {

	private SubmissionDeleteRunner() {}

	public static String run(String submissionId) {
		Logger log = JavaPlugin.getPlugin(ArmourShop.class).getLogger();
		String id = submissionId == null ? "" : submissionId.trim();
		if (id.isEmpty()) {
			return "Submission id is required.";
		}

		PluginSubmissionResult fetched = ProvinceSystemClient.getSubmission(id);
		if (!fetched.ok || fetched.submission == null) {
			return fetched.error != null ? fetched.error : "Could not load submission.";
		}
		PluginSubmission sub = fetched.submission;
		if (sub.staff) {
			return "That id is a staff skin. Use /armourshop skin delete "
				+ id + " instead.";
		}

		String contents = Cache.iaContentsPath;
		List<String> tiers = sub.tiers == null ? List.of() : sub.tiers;

		if (contents != null && !contents.isBlank()) {
			try {
				PackSubmissionRemover.remove(
					Path.of(contents.trim()),
					sub.kind,
					sub.slug,
					tiers,
					log
				);
			} catch (Exception e) {
				log.warning("[submission-delete] pack remove failed (continuing): "
					+ e.getMessage());
			}
		} else {
			log.warning("[submission-delete] ia-contents-path unset; skipping pack remove");
		}

		try {
			ShopSubmissionWriter.remove(sub.slug, sub.kind, tiers, log);
		} catch (Exception e) {
			log.warning("[submission-delete] shop remove failed (continuing): "
				+ e.getMessage());
		}

		// Shared LP grant lives on the submission id/slug (one node for all tiers).
		if (sub.playerUuid != null && !sub.playerUuid.isBlank()) {
			try {
				UUID uuid = UUID.fromString(sub.playerUuid.trim());
				LuckPermsGrant.revokeSubmission(uuid, sub.slug, log);
			} catch (IllegalArgumentException e) {
				log.warning("[submission-delete] invalid player uuid: " + sub.playerUuid);
			}
		}

		SimpleResult deleted = ProvinceSystemClient.revokeSubmission(id);
		if (!deleted.ok) {
			return "Local cleanup done but API delete failed: "
				+ (deleted.error != null ? deleted.error : "unknown");
		}

		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		Bukkit.getScheduler().runTask(plugin, () -> {
			plugin.reload();
			// Do not enqueue revoked ids (website sync would drop them and they
			// can never be marked applied). Still refresh IA so pack files clear.
			plugin.getDeferredIaReloadService().requestFlush(false, true);
		});

		String label = sub.displayName != null && !sub.displayName.isBlank()
			? sub.displayName
			: sub.slug;
		return "Deleted submission " + id + " (" + label + "). Shop/pack cleared; IA refresh queued.";
	}
}

package net.tfminecraft.ArmourShop.pack.delete;


import net.tfminecraft.ArmourShop.pack.shop.ShopSubmissionWriter;
import java.nio.file.Path;
import java.util.List;
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
 * Staff-lane delete: clear tfmc_armorshop pack + category.yml keys, then API hard-delete.
 * Never touches legacy tfmc_armor or player ps_* / LP.
 */
public final class SkinDeleteRunner {

	private SkinDeleteRunner() {}

	public static String run(String submissionId) {
		Logger log = JavaPlugin.getPlugin(ArmourShop.class).getLogger();
		String id = submissionId == null ? "" : submissionId.trim();
		if (id.isEmpty()) {
			return "Skin id is required.";
		}

		PluginSubmissionResult fetched = ProvinceSystemClient.getSubmission(id);
		if (!fetched.ok || fetched.submission == null) {
			return fetched.error != null ? fetched.error : "Could not load skin.";
		}
		PluginSubmission sub = fetched.submission;
		if (!sub.staff) {
			return "That id is a player submission. Use /armourshop submission delete "
				+ id + " instead.";
		}
		if (sub.category == null || sub.category.isBlank()) {
			return "Staff skin " + id + " is missing category; cannot remove shop keys.";
		}

		String contents = Cache.iaContentsPath;
		List<String> tiers = sub.tiers == null ? List.of() : sub.tiers;
		String ns = sub.resolveNamespace();

		if (contents != null && !contents.isBlank()) {
			try {
				PackSubmissionRemover.remove(
					Path.of(contents.trim()),
					ns,
					sub.kind,
					sub.slug,
					tiers,
					log
				);
			} catch (Exception e) {
				log.warning("[skin-delete] pack remove failed (continuing): "
					+ e.getMessage());
			}
		} else {
			log.warning("[skin-delete] ia-contents-path unset; skipping pack remove");
		}

		try {
			ShopSubmissionWriter.removeStaff(
				sub.slug,
				sub.kind,
				tiers,
				sub.category,
				log
			);
		} catch (Exception e) {
			log.warning("[skin-delete] shop remove failed (continuing): "
				+ e.getMessage());
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
		return "Deleted staff skin " + id + " (" + label + "). Shop/pack cleared; IA refresh queued.";
	}
}

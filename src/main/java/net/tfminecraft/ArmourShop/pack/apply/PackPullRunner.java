package net.tfminecraft.ArmourShop.pack.apply;


import net.tfminecraft.ArmourShop.pack.reload.DeferredIaReloadService;
import net.tfminecraft.ArmourShop.pack.shop.LuckPermsGrant;
import net.tfminecraft.ArmourShop.pack.shop.ShopSubmissionWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.ArmourShop.ArmourShop;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ApprovedSubmission;

/**
 * Shared approved-skin pull: write pack + shop + LP, enqueue, then IA flush.
 */
public final class PackPullRunner {

	private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

	private PackPullRunner() {}

	public static boolean isRunning() {
		return RUNNING.get();
	}

	/**
	 * @param forceReload if true, IA refresh even with players online
	 * @param onDone optional callback on main thread with a short summary line (may be null)
	 * @return false if a pull is already in progress
	 */
	public static boolean run(boolean forceReload, Consumer<PullResult> onDone) {
		ArmourShop plugin = JavaPlugin.getPlugin(ArmourShop.class);
		if (!RUNNING.compareAndSet(false, true)) {
			plugin.getLogger().info("[pack] pull already running — skipped");
			if (onDone != null) {
				Bukkit.getScheduler().runTask(plugin, () ->
					onDone.accept(PullResult.skippedBusy())
				);
			}
			return false;
		}

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			Logger log = plugin.getLogger();
			try {
				PackApplyService.ApplySummary summary = PackApplyService.pullAndWrite(log);

				int shopOk = 0;
				int shopFail = 0;
				int lpOk = 0;
				int lpFail = 0;
				List<String> readyForReload = new ArrayList<>();
				List<String> messages = new ArrayList<>(summary.messages);

				for (ApprovedSubmission sub : summary.writtenSubmissions) {
					boolean shopSucceeded = false;
					try {
						ShopSubmissionWriter.write(sub, log);
						shopOk++;
						shopSucceeded = true;
					} catch (Exception e) {
						shopFail++;
						log.warning("[shop] fail " + sub.id + ": " + e.getMessage());
						messages.add("shop fail " + sub.slug + ": " + e.getMessage());
					}

					boolean lpSucceeded = false;
					if (sub.staff) {
						// Staff curated skins use scroll consume — no submission LP.
						lpSucceeded = true;
					} else {
						UUID uuid = parseUuid(sub.playerUuid);
						if (uuid == null) {
							lpFail++;
							log.warning("[lp] invalid uuid for " + sub.id + ": " + sub.playerUuid);
							messages.add("lp fail " + sub.slug + ": invalid uuid");
						} else if (LuckPermsGrant.grantSubmission(uuid, sub.slug, log)) {
							lpOk++;
							lpSucceeded = true;
						} else {
							lpFail++;
							messages.add("lp fail " + sub.slug);
						}
					}

					if (shopSucceeded && lpSucceeded && sub.id != null && !sub.id.isBlank()) {
						readyForReload.add(sub.id.trim());
					}
				}

				final int fShopOk = shopOk;
				final int fShopFail = shopFail;
				final int fLpOk = lpOk;
				final int fLpFail = lpFail;
				final boolean hadWrites = !summary.writtenSubmissions.isEmpty();

				Bukkit.getScheduler().runTask(plugin, () -> {
					try {
						if (hadWrites) {
							plugin.reload();
							log.info("[pack] ArmourShop reloaded after shop write");
						}

						DeferredIaReloadService reloadService = plugin.getDeferredIaReloadService();
						if (reloadService != null) {
							if (!readyForReload.isEmpty()) {
								reloadService.queue().enqueue(readyForReload);
							}
							if (!reloadService.queue().isEmpty()) {
								reloadService.requestFlush(forceReload);
							}
						}

						PullResult result = new PullResult(
							false,
							summary.written,
							summary.skipped,
							summary.failed,
							fShopOk,
							fShopFail,
							fLpOk,
							fLpFail,
							readyForReload.size(),
							messages
						);
						if (onDone != null) {
							onDone.accept(result);
						}
					} finally {
						RUNNING.set(false);
					}
				});
			} catch (Exception e) {
				log.severe("[pack] pull failed: " + e.getMessage());
				Bukkit.getScheduler().runTask(plugin, () -> {
					try {
						if (onDone != null) {
							onDone.accept(PullResult.failed(e.getMessage()));
						}
					} finally {
						RUNNING.set(false);
					}
				});
			}
		});
		return true;
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw.trim());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static final class PullResult {
		public final boolean busy;
		public final boolean failed;
		public final String error;
		public final int written;
		public final int skipped;
		public final int failedCount;
		public final int shopOk;
		public final int shopFail;
		public final int lpOk;
		public final int lpFail;
		public final int queued;
		public final List<String> messages;

		private PullResult(
			boolean busy,
			int written,
			int skipped,
			int failedCount,
			int shopOk,
			int shopFail,
			int lpOk,
			int lpFail,
			int queued,
			List<String> messages
		) {
			this.busy = busy;
			this.failed = false;
			this.error = null;
			this.written = written;
			this.skipped = skipped;
			this.failedCount = failedCount;
			this.shopOk = shopOk;
			this.shopFail = shopFail;
			this.lpOk = lpOk;
			this.lpFail = lpFail;
			this.queued = queued;
			this.messages = messages == null ? List.of() : List.copyOf(messages);
		}

		private PullResult(boolean busy, boolean failed, String error) {
			this.busy = busy;
			this.failed = failed;
			this.error = error;
			this.written = 0;
			this.skipped = 0;
			this.failedCount = 0;
			this.shopOk = 0;
			this.shopFail = 0;
			this.lpOk = 0;
			this.lpFail = 0;
			this.queued = 0;
			this.messages = List.of();
		}

		public static PullResult skippedBusy() {
			return new PullResult(true, false, null);
		}

		public static PullResult failed(String error) {
			return new PullResult(false, true, error);
		}

		public String summaryLine() {
			if (busy) {
				return "Pack pull already running.";
			}
			if (failed) {
				return "Pack pull failed: " + (error != null ? error : "unknown");
			}
			return "Pack pull done: wrote=" + written
				+ " skipped=" + skipped
				+ " failed=" + failedCount
				+ " shop=" + shopOk + "/" + (shopOk + shopFail)
				+ " lp=" + lpOk + "/" + (lpOk + lpFail)
				+ " queued=" + queued;
		}
	}
}

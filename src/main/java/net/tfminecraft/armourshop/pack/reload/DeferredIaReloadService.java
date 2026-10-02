package net.tfminecraft.armourshop.pack.reload;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.AppliedResult;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ListResult;
import net.tfminecraft.armourshop.pack.apply.PackPullRunner;
import net.tfminecraft.armourshop.pack.reload.ApplyAckPlanner.Plan;

/**
 * Defers ItemsAdder refresh until the server is empty (or force), runs
 * {@code iareload} then delayed {@code iazip}, and acks applied after that zip.
 *
 * <p>The pending queue holds ids whose pack files were already written.
 * A flush drops ids that are no longer approved. It never imports other
 * approved ids. After {@code iazip}, an id is marked applied only when its
 * ItemsAdder config is on disk.
 */
public final class DeferredIaReloadService implements Listener {

	private final JavaPlugin plugin;
	private final PendingReloadQueue queue;
	private volatile boolean inFlight;
	/** True only after this flush has dispatched {@code iazip}. */
	private volatile boolean awaitingZipEvent;
	/** Only ids present when iareload began belong to this compressed pack. */
	private Map<String, Long> flushingVersions = Map.of();
	private final Object flightLock = new Object();

	public DeferredIaReloadService(JavaPlugin plugin, PendingReloadQueue queue) {
		this.plugin = plugin;
		this.queue = queue;
	}

	public PendingReloadQueue queue() {
		return queue;
	}

	/** Flush only when no players are online. */
	public void requestFlush() {
		requestFlush(false, false);
	}

	/**
	 * @param force if true, run even when players are online
	 */
	public void requestFlush(boolean force) {
		requestFlush(force, false);
	}

	/**
	 * @param force if true, run even when players are online
	 * @param refreshEvenIfEmpty if true, still run IA refresh when the queue is empty
	 *        after website sync (e.g. local pack delete with no pending applies)
	 */
	public void requestFlush(boolean force, boolean refreshEvenIfEmpty) {
		if (inFlight) {
			return;
		}

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			Logger log = plugin.getLogger();
			SyncResult sync = pruneQueueToApproved(log);
			Bukkit.getScheduler().runTask(plugin, () ->
				beginFlush(force, refreshEvenIfEmpty, sync)
			);
		});
	}

	/**
	 * Drop queued ids that ProvinceSystem no longer lists as approved.
	 * Does not add approved ids that were never written. Call off the main
	 * thread (HTTP). Thread-safe for the queue.
	 */
	public SyncResult pruneQueueToApproved(Logger log) {
		Map<String, Long> versions = queue.snapshotVersions();
		int before = versions.size();
		ListResult list = ProvinceSystemClient.listApproved();
		if (!list.ok) {
			String err = list.error != null ? list.error : "unknown error";
			if (log != null) {
				log.warning("[ia-reload] pre-flush prune failed: " + err
					+ " — using local queue");
			}
			return SyncResult.failed(err);
		}
		List<String> stillApproved = new ArrayList<>();
		for (ApprovedSubmission sub : list.submissions) {
			if (sub != null && sub.id != null && !sub.id.isBlank()) {
				stillApproved.add(sub.id.trim());
			}
		}
		List<String> dropped = new ArrayList<>(versions.keySet());
		dropped.removeAll(stillApproved);
		queue.clearIfUnchanged(dropped, versions);
		if (log != null) {
			log.info("[ia-reload] pending queue kept written ids still approved: "
				+ before + " → " + queue.size());
		}
		return SyncResult.ok(before, queue.size());
	}

	private void beginFlush(boolean force, boolean refreshEvenIfEmpty, SyncResult sync) {
		if (inFlight) {
			return;
		}
		if (queue.isEmpty() && !refreshEvenIfEmpty) {
			Logger log = plugin.getLogger();
			if (sync != null && sync.ok && sync.before > 0) {
				log.info("[ia-reload] queue empty after prune — skipping IA refresh");
			}
			return;
		}
		if (!force && !Bukkit.getOnlinePlayers().isEmpty()) {
			Logger log = plugin.getLogger();
			log.info("[ia-reload] " + queue.size()
				+ " submission(s) pending IA refresh — waiting for empty server");
			return;
		}

		synchronized (flightLock) {
			inFlight = true;
			awaitingZipEvent = false;
			flushingVersions = queue.snapshotVersions();
		}
		Logger log = plugin.getLogger();
		int delaySec = Math.max(0, Cache.iaReloadDelaySeconds);
		log.info("[ia-reload] running iareload then iazip in " + delaySec
			+ "s for " + queue.size() + " pending submission(s) (force=" + force
			+ ", refreshEvenIfEmpty=" + refreshEvenIfEmpty + ")");

		try {
			boolean reloadOk = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iareload");
			if (!reloadOk) {
				log.warning("[ia-reload] failed to dispatch iareload — continuing to iazip anyway");
			}

			long delayTicks = delaySec * 20L;
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				try {
					synchronized (flightLock) {
						awaitingZipEvent = true;
					}
					boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
					if (!ok) {
						resetFlight();
						log.severe("[ia-reload] failed to dispatch iazip — will retry later");
					}
				} catch (RuntimeException | Error failure) {
					resetFlight();
					log.warning("[ia-reload] iazip failed — pending ids retained: " + failure.getMessage());
					throw failure;
				}
			}, delayTicks);
		} catch (RuntimeException | Error failure) {
			resetFlight();
			log.warning("[ia-reload] refresh could not start — pending ids retained: " + failure.getMessage());
			throw failure;
		}
	}

	private void resetFlight() {
		synchronized (flightLock) {
			awaitingZipEvent = false;
			flushingVersions = Map.of();
			inFlight = false;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPackCompressed(ItemsAdderPackCompressedEvent event) {
		Map<String, Long> versions;
		synchronized (flightLock) {
			if (!inFlight || !awaitingZipEvent) {
				return;
			}
			versions = flushingVersions;
			resetFlight();
		}
		if (versions.isEmpty()) {
			return;
		}

		Logger log = plugin.getLogger();
		log.info("[ia-reload] pack compressed — checking " + versions.size()
			+ " written submission(s) before ack");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ackWritten(log, versions));
	}

	private void ackWritten(Logger log, Map<String, Long> versions) {
		String contents = Cache.iaContentsPath;
		if (contents == null || contents.isBlank()) {
			log.severe("[ia-reload] pack-apply.ia-contents-path is unset — not acking; "
				+ "ids remain queued");
			return;
		}
		ListResult list = ProvinceSystemClient.listApproved();
		if (!list.ok) {
			String err = list.error != null ? list.error : "unknown error";
			log.warning("[ia-reload] could not confirm approvals (" + err
				+ ") — not acking; ids remain queued");
			return;
		}
		Plan plan = ApplyAckPlanner.plan(queue.matchingIds(versions), list.submissions, Path.of(contents.trim()));
		AppliedResult result = plan.ack().isEmpty()
			? AppliedResult.success(List.of())
			: ProvinceSystemClient.markApplied(plan.ack());
		Bukkit.getScheduler().runTask(plugin, () -> {
			queue.clearIfUnchanged(plan.notApproved(), versions);
			queue.clearIfUnchanged(plan.missingFiles(), versions);
			if (!plan.notApproved().isEmpty()) {
				log.info("[ia-reload] dropped " + plan.notApproved().size()
					+ " queued id(s) that are no longer approved");
			}
			for (String id : plan.missingFiles()) {
				log.warning("[ia-reload] not acking " + id
					+ " — pack config missing; still approved until pack pull");
			}
			if (!result.ok) {
				log.warning("[ia-reload] applied ack failed: " + result.error
					+ " — written ids remain queued");
				return;
			}
			queue.clearIfUnchanged(result.applied, versions);
			log.info("[ia-reload] applied ack ok: " + result.applied.size()
				+ " id(s); remaining queued=" + queue.size());
			if (result.applied.size() < plan.ack().size()) {
				log.warning("[ia-reload] some written ids were not marked applied by API; still queued");
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!Bukkit.getOnlinePlayers().isEmpty()) {
				return;
			}
			plugin.getLogger().info("[pack] server empty — running pull (force=false)");
			PackPullRunner.run(false, null);
		});
	}

	public static final class SyncResult {
		public final boolean ok;
		public final int before;
		public final int after;
		public final String error;

		private SyncResult(boolean ok, int before, int after, String error) {
			this.ok = ok;
			this.before = before;
			this.after = after;
			this.error = error;
		}

		public static SyncResult ok(int before, int after) {
			return new SyncResult(true, before, after, null);
		}

		public static SyncResult failed(String error) {
			return new SyncResult(false, 0, 0, error);
		}
	}
}

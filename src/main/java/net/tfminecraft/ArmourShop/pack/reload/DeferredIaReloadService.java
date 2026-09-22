package net.tfminecraft.ArmourShop.pack.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.AppliedResult;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.ListResult;
import net.tfminecraft.ArmourShop.pack.apply.PackPullRunner;

/**
 * Defers ItemsAdder refresh until the server is empty (or force), runs
 * {@code iareload} then delayed {@code iazip}, and acks applied after pack compression.
 * Syncs {@code pending-reload.yml} from ProvinceSystem before every flush.
 */
public final class DeferredIaReloadService implements Listener {

	private final JavaPlugin plugin;
	private final PendingReloadQueue queue;
	private volatile boolean inFlight;
	private BukkitTask delayedZipTask;

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
			SyncResult sync = syncQueueFromWebsite(log);
			Bukkit.getScheduler().runTask(plugin, () ->
				beginFlush(force, refreshEvenIfEmpty, sync)
			);
		});
	}

	/** @deprecated use {@link #requestFlush()} */
	@Deprecated
	public void tryFlush() {
		requestFlush(false, false);
	}

	/** @deprecated use {@link #requestFlush(boolean)} */
	@Deprecated
	public void tryFlush(boolean force) {
		requestFlush(force, false);
	}

	/**
	 * Replace the pending-reload queue with ProvinceSystem approved-not-applied ids.
	 * Call off the main thread (HTTP). Thread-safe for the queue.
	 */
	public SyncResult syncQueueFromWebsite(Logger log) {
		ListResult list = ProvinceSystemClient.listApproved();
		if (!list.ok) {
			String err = list.error != null ? list.error : "unknown error";
			if (log != null) {
				log.warning("[ia-reload] pre-flush sync failed: " + err
					+ " — using local queue");
			}
			return SyncResult.failed(err);
		}
		int before = queue.size();
		List<String> ids = new ArrayList<>();
		for (ApprovedSubmission sub : list.submissions) {
			if (sub != null && sub.id != null && !sub.id.isBlank()) {
				ids.add(sub.id.trim());
			}
		}
		queue.replaceAll(ids);
		if (log != null) {
			log.info("[ia-reload] synced pending queue from website: "
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
				log.info("[ia-reload] queue empty after website sync — skipping IA refresh");
			}
			return;
		}
		if (!force && !Bukkit.getOnlinePlayers().isEmpty()) {
			Logger log = plugin.getLogger();
			log.info("[ia-reload] " + queue.size()
				+ " submission(s) pending IA refresh — waiting for empty server");
			return;
		}

		inFlight = true;
		Logger log = plugin.getLogger();
		int delaySec = Math.max(0, Cache.iaReloadDelaySeconds);
		log.info("[ia-reload] running iareload then iazip in " + delaySec
			+ "s for " + queue.size() + " pending submission(s) (force=" + force
			+ ", refreshEvenIfEmpty=" + refreshEvenIfEmpty + ")");

		boolean reloadOk = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iareload");
		if (!reloadOk) {
			log.warning("[ia-reload] failed to dispatch iareload — continuing to iazip anyway");
		}

		if (delayedZipTask != null) {
			delayedZipTask.cancel();
			delayedZipTask = null;
		}

		long delayTicks = delaySec * 20L;
		delayedZipTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			delayedZipTask = null;
			boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
			if (!ok) {
				inFlight = false;
				log.severe("[ia-reload] failed to dispatch iazip — will retry later");
			}
		}, delayTicks);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPackCompressed(ItemsAdderPackCompressedEvent event) {
		if (!inFlight) {
			return;
		}
		inFlight = false;
		List<String> ids = queue.snapshot();
		if (ids.isEmpty()) {
			return;
		}

		Logger log = plugin.getLogger();
		log.info("[ia-reload] pack compressed — acking " + ids.size() + " submission(s)");
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			AppliedResult result = ProvinceSystemClient.markApplied(ids);
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!result.ok) {
					log.warning("[ia-reload] applied ack failed: " + result.error
						+ " — ids remain queued");
					return;
				}
				queue.clear(result.applied);
				log.info("[ia-reload] applied ack ok: " + result.applied.size()
					+ " id(s); remaining queued=" + queue.size());
				if (result.applied.size() < ids.size()) {
					log.warning("[ia-reload] some ids were not marked applied by API; still queued");
				}
			});
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

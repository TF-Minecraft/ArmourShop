package net.tfminecraft.armourshop.pack.delete;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.DeletableListResult;

/**
 * Shared asynchronous ID cache; invalidations supersede in-flight fetches.
 */
final class GenerationalIdCache {

	private static final long TTL_MS = 30_000L;

	private final AtomicReference<List<String>> IDS =
		new AtomicReference<>(Collections.emptyList());
	private final AtomicLong FETCHED_AT = new AtomicLong(0L);
	private final Object REFRESH_LOCK = new Object();
	private volatile boolean refreshInFlight;
	private long generation;

	private final Supplier<DeletableListResult> fetch;

	GenerationalIdCache(Supplier<DeletableListResult> fetch) {
		this.fetch = fetch;
	}

	List<String> snapshot() {
		maybeRefreshAsync(false);
		return IDS.get();
	}

	/** Call after a successful delete so tab-complete drops the id soon. */
	void invalidate() {
		maybeRefreshAsync(true);
	}

	private void maybeRefreshAsync(boolean force) {
		final long fetchGeneration;
		synchronized (REFRESH_LOCK) {
			if (force) {
				generation++;
				FETCHED_AT.set(0L);
			}
			if (!force && System.currentTimeMillis() - FETCHED_AT.get() < TTL_MS) {
				return;
			}
			if (refreshInFlight) {
				return;
			}
			refreshInFlight = true;
			fetchGeneration = generation;
		}
		try {
			JavaPlugin plugin = JavaPlugin.getPlugin(ArmourShop.class);
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					DeletableListResult result = fetch.get();
					synchronized (REFRESH_LOCK) {
						if (result.ok && fetchGeneration == generation) {
							IDS.set(List.copyOf(result.ids));
							FETCHED_AT.set(System.currentTimeMillis());
						}
					}
				} finally {
					boolean followup;
					synchronized (REFRESH_LOCK) {
						refreshInFlight = false;
						followup = fetchGeneration != generation;
					}
					if (followup) {
						maybeRefreshAsync(false);
					}
				}
			});
		} catch (RuntimeException e) {
			synchronized (REFRESH_LOCK) {
				refreshInFlight = false;
			}
			throw e;
		}
	}
}

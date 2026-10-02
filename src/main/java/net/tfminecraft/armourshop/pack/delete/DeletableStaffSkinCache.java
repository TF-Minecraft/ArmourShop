package net.tfminecraft.armourshop.pack.delete;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.DeletableListResult;

/**
 * Cached deletable staff skin ids for tab-complete (refreshed async).
 */
public final class DeletableStaffSkinCache {

	private static final long TTL_MS = 30_000L;

	private static final AtomicReference<List<String>> IDS =
		new AtomicReference<>(Collections.emptyList());
	private static final AtomicLong FETCHED_AT = new AtomicLong(0L);
	private static final Object REFRESH_LOCK = new Object();
	private static volatile boolean refreshInFlight;
	private static long generation;

	private DeletableStaffSkinCache() {}

	public static List<String> snapshot() {
		maybeRefreshAsync(false);
		return IDS.get();
	}

	/** Call after a successful delete so tab-complete drops the id soon. */
	public static void invalidate() {
		maybeRefreshAsync(true);
	}

	private static void maybeRefreshAsync(boolean force) {
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
					DeletableListResult result = ProvinceSystemClient.listDeletableStaffSkinIds();
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

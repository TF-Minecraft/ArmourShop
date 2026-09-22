package net.tfminecraft.ArmourShop.pack.delete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.ArmourShop.ArmourShop;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient;
import net.tfminecraft.ArmourShop.api.ProvinceSystemClient.DeletableListResult;

/**
 * Cached deletable submission ids for tab-complete (refreshed async).
 */
public final class DeletableSubmissionCache {

	private static final long TTL_MS = 30_000L;

	private static final AtomicReference<List<String>> IDS =
		new AtomicReference<>(Collections.emptyList());
	private static final AtomicLong FETCHED_AT = new AtomicLong(0L);
	private static volatile boolean refreshInFlight;

	private DeletableSubmissionCache() {}

	public static List<String> snapshot() {
		maybeRefreshAsync(false);
		return IDS.get();
	}

	/** Call after a successful delete so tab-complete drops the id soon. */
	public static void invalidate() {
		FETCHED_AT.set(0L);
		maybeRefreshAsync(true);
	}

	private static void maybeRefreshAsync(boolean force) {
		long now = System.currentTimeMillis();
		if (!force && now - FETCHED_AT.get() < TTL_MS) {
			return;
		}
		if (refreshInFlight) {
			return;
		}
		refreshInFlight = true;
		JavaPlugin plugin = JavaPlugin.getPlugin(ArmourShop.class);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				DeletableListResult result =
					ProvinceSystemClient.listDeletableSubmissionIds();
				if (result.ok) {
					IDS.set(new ArrayList<>(result.ids));
					FETCHED_AT.set(System.currentTimeMillis());
				}
			} finally {
				refreshInFlight = false;
			}
		});
	}
}

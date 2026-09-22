package net.tfminecraft.ArmourShop.pack.apply;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import net.tfminecraft.ArmourShop.Cache;

/**
 * Daily force pack pull + IA refresh at configured local HH:mm.
 */
public final class PackPullScheduler {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	private final JavaPlugin plugin;
	private BukkitTask task;
	private LocalDate lastForcedDate;

	public PackPullScheduler(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		stop();
		// Every 60s — catch the configured minute without missing it
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30, 20L * 60);
		Logger log = plugin.getLogger();
		String configured = Cache.forceReloadTime;
		if (configured == null || configured.isBlank()) {
			log.info("[pack] force-reload-time disabled");
		} else {
			log.info("[pack] force-reload-time scheduled at " + configured.trim() + " (server local)");
		}
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	private void tick() {
		String raw = Cache.forceReloadTime;
		if (raw == null || raw.isBlank()) {
			return;
		}
		LocalTime target;
		try {
			target = LocalTime.parse(raw.trim(), HH_MM);
		} catch (DateTimeParseException e) {
			plugin.getLogger().warning("[pack] invalid force-reload-time '" + raw
				+ "' (expected HH:mm)");
			return;
		}

		LocalDate today = LocalDate.now();
		LocalTime now = LocalTime.now();
		if (lastForcedDate != null && lastForcedDate.equals(today)) {
			return;
		}
		// Fire once when current clock is at or past target within the same minute window
		if (now.getHour() != target.getHour() || now.getMinute() != target.getMinute()) {
			return;
		}

		lastForcedDate = today;
		plugin.getLogger().info("[pack] force-reload-time matched — running pull (force=true)");
		PackPullRunner.run(true, null);
	}
}

package net.tfminecraft.ArmourShop.pack.reload;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists submission ids waiting for ItemsAdder reload + applied ack.
 */
public final class PendingReloadQueue {

	private static final String FILE_NAME = "pending-reload.yml";
	private static final String KEY = "submission-ids";

	private final JavaPlugin plugin;
	private final LinkedHashSet<String> ids = new LinkedHashSet<>();
	private final Object lock = new Object();

	public PendingReloadQueue(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load() {
		synchronized (lock) {
			ids.clear();
			File file = file();
			if (!file.exists()) {
				return;
			}
			FileConfiguration config = YamlConfiguration.loadConfiguration(file);
			List<String> list = config.getStringList(KEY);
			for (String id : list) {
				if (id != null && !id.isBlank()) {
					ids.add(id.trim());
				}
			}
		}
	}

	public void enqueue(Collection<String> submissionIds) {
		if (submissionIds == null || submissionIds.isEmpty()) {
			return;
		}
		synchronized (lock) {
			boolean changed = false;
			for (String id : submissionIds) {
				if (id == null || id.isBlank()) {
					continue;
				}
				if (ids.add(id.trim())) {
					changed = true;
				}
			}
			if (changed) {
				saveUnlocked();
			}
		}
	}

	public List<String> snapshot() {
		synchronized (lock) {
			return new ArrayList<>(ids);
		}
	}

	public boolean isEmpty() {
		synchronized (lock) {
			return ids.isEmpty();
		}
	}

	public int size() {
		synchronized (lock) {
			return ids.size();
		}
	}

	public void clear(Collection<String> acked) {
		if (acked == null || acked.isEmpty()) {
			return;
		}
		synchronized (lock) {
			boolean changed = false;
			for (String id : acked) {
				if (id != null && ids.remove(id.trim())) {
					changed = true;
				}
			}
			if (changed) {
				saveUnlocked();
			}
		}
	}

	/** Replace queue contents with website pending-apply ids (may be empty). */
	public void replaceAll(Collection<String> submissionIds) {
		synchronized (lock) {
			ids.clear();
			if (submissionIds != null) {
				for (String id : submissionIds) {
					if (id == null || id.isBlank()) {
						continue;
					}
					ids.add(id.trim());
				}
			}
			saveUnlocked();
		}
	}

	private File file() {
		return new File(plugin.getDataFolder(), FILE_NAME);
	}

	private void saveUnlocked() {
		FileConfiguration config = new YamlConfiguration();
		config.set(KEY, new ArrayList<>(ids));
		try {
			config.save(file());
		} catch (IOException e) {
			Logger log = plugin.getLogger();
			log.warning("[reload-queue] failed to save " + FILE_NAME + ": " + e.getMessage());
		}
	}
}

package net.tfminecraft.armourshop.pack.reload;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
	private final LinkedHashMap<String, Long> ids = new LinkedHashMap<>();
	private long generation;
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
					ids.put(id.trim(), ++generation);
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
				if (ids.put(id.trim(), ++generation) == null) {
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
			return new ArrayList<>(ids.keySet());
		}
	}

	/** Captures queue generations without changing the persisted ID-only format. */
	Map<String, Long> snapshotVersions() {
		synchronized (lock) {
			return new LinkedHashMap<>(ids);
		}
	}

	/** Returns unchanged captured entries in their original snapshot order. */
	List<String> matchingIds(Map<String, Long> versions) {
		if (versions == null || versions.isEmpty()) {
			return List.of();
		}
		synchronized (lock) {
			List<String> matching = new ArrayList<>();
			for (Map.Entry<String, Long> entry : versions.entrySet()) {
				if (entry.getValue() != null && entry.getValue().equals(ids.get(entry.getKey()))) {
					matching.add(entry.getKey());
				}
			}
			return matching;
		}
	}

	/** Removes acknowledged entries only if no newer enqueue replaced them. */
	void clearIfUnchanged(Collection<String> acked, Map<String, Long> versions) {
		if (acked == null || acked.isEmpty() || versions == null || versions.isEmpty()) {
			return;
		}
		synchronized (lock) {
			boolean changed = false;
			for (String id : acked) {
				if (id == null) {
					continue;
				}
				String key = id.trim();
				Long version = versions.get(key);
				if (version != null && ids.remove(key, version)) {
					changed = true;
				}
			}
			if (changed) {
				saveUnlocked();
			}
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
				if (id != null && ids.remove(id.trim()) != null) {
					changed = true;
				}
			}
			if (changed) {
				saveUnlocked();
			}
		}
	}

	/**
	 * Keep only ids that are in {@code stillApproved}. Never adds ids.
	 * Returns the size before the prune.
	 */
	public int retainAll(Collection<String> stillApproved) {
		synchronized (lock) {
			int before = ids.size();
			LinkedHashSet<String> keep = new LinkedHashSet<>();
			if (stillApproved != null) {
				for (String id : stillApproved) {
					if (id != null && !id.isBlank()) {
						keep.add(id.trim());
					}
				}
			}
			if (ids.keySet().retainAll(keep)) {
				saveUnlocked();
			}
			return before;
		}
	}

	private File file() {
		return new File(plugin.getDataFolder(), FILE_NAME);
	}

	private void saveUnlocked() {
		FileConfiguration config = new YamlConfiguration();
		config.set(KEY, new ArrayList<>(ids.keySet()));
		try {
			config.save(file());
		} catch (IOException e) {
			Logger log = plugin.getLogger();
			log.warning("[reload-queue] failed to save " + FILE_NAME + ": " + e.getMessage());
		}
	}
}

package net.tfminecraft.ArmourShop.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.objects.PermissionGroupDefinition;

public final class PermissionGroupsLoader implements LoaderInterface {

	private static final Set<String> META_KEYS = Set.of(
		"permission", "display-name", "tier", "visible",
		"skin-kinds", "allow-armor-3d-helmet"
	);

	@Override
	public void load(File configFile) {
		FileConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		Map<String, Integer> defaults = new HashMap<>();
		List<String> defaultKinds = new ArrayList<>();
		boolean defaultAllowHelmet = false;
		if (config.isConfigurationSection("defaults")) {
			ConfigurationSection section = config.getConfigurationSection("defaults");
			if (section != null) {
				for (String key : section.getKeys(false)) {
					if ("skin-kinds".equals(key) || "allow-armor-3d-helmet".equals(key)) {
						continue;
					}
					defaults.put(key, section.getInt(key));
				}
				defaultKinds = readKindList(section, "skin-kinds");
				defaultAllowHelmet = section.getBoolean("allow-armor-3d-helmet", false);
			}
		}
		if (!defaults.containsKey(PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS)) {
			defaults.put(PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS, -1);
		}
		Cache.permissionGroupDefaults = defaults;
		Cache.permissionGroupDefaultSkinKinds = defaultKinds;
		Cache.permissionGroupDefaultAllowArmor3dHelmet = defaultAllowHelmet;

		List<PermissionGroupDefinition> groups = new ArrayList<>();
		if (config.isConfigurationSection("groups")) {
			ConfigurationSection groupsSection = config.getConfigurationSection("groups");
			if (groupsSection != null) {
				int autoTier = 0;
				for (String id : groupsSection.getKeys(false)) {
					ConfigurationSection groupSection = groupsSection.getConfigurationSection(id);
					if (groupSection == null) {
						continue;
					}
					String permission = groupSection.getString("permission", "");
					String displayName = groupSection.getString("display-name", id);
					int tier = groupSection.contains("tier")
						? groupSection.getInt("tier")
						: autoTier++;
					boolean visible = groupSection.getBoolean("visible", true);
					Map<String, Integer> perks = new HashMap<>();
					for (String key : groupSection.getKeys(false)) {
						if (META_KEYS.contains(key)) {
							continue;
						}
						perks.put(key, groupSection.getInt(key));
					}
					List<String> kinds = readKindList(groupSection, "skin-kinds");
					Boolean allowHelmet = groupSection.contains("allow-armor-3d-helmet")
						? Boolean.valueOf(groupSection.getBoolean("allow-armor-3d-helmet"))
						: null;
					groups.add(new PermissionGroupDefinition(
						id, permission, displayName, tier, visible, perks, kinds, allowHelmet
					));
				}
				groups.sort(java.util.Comparator.comparingInt(PermissionGroupDefinition::getTier));
			}
		}
		Cache.permissionGroups = groups;
	}

	private static List<String> readKindList(ConfigurationSection section, String key) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		if (section == null || !section.contains(key)) {
			return new ArrayList<>();
		}
		List<?> raw = section.getList(key);
		if (raw == null) {
			return new ArrayList<>();
		}
		for (Object item : raw) {
			if (item == null) {
				continue;
			}
			String kind = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
			if (!kind.isEmpty()) {
				out.add(kind);
			}
		}
		return new ArrayList<>(out);
	}
}

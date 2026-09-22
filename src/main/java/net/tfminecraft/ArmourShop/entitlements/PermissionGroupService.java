package net.tfminecraft.ArmourShop.entitlements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.entity.Player;

import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.objects.PermissionGroupDefinition;

/**
 * Resolve skin-upload perks from permission-groups.yml.
 * Int colour/pair budgets use MAX across matching LP nodes.
 * Cooldown / kinds / armor-3d-helmet use highest matching tier T with inherit.
 */
public final class PermissionGroupService {

	private PermissionGroupService() {}

	public static int getNameColourStops(Player player) {
		return resolvePerkMax(player, PermissionGroupDefinition.KEY_NAME_COLOUR_STOPS);
	}

	public static int getMax3dPairBytes(Player player) {
		return resolvePerkMax(player, PermissionGroupDefinition.KEY_MAX_3D_PAIR_BYTES);
	}

	/**
	 * Days between skin token mints. {@code -1} means mint disallowed.
	 */
	public static int getSkinTokenCooldownDays(Player player) {
		int fallback = getDefaultSkinTokenCooldownDays();
		PermissionGroupDefinition highest = highestMatchingGroup(player);
		if (highest == null) {
			return fallback;
		}
		return walkDownInt(
			highest.getTier(),
			PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS,
			fallback
		);
	}

	/** Union of skin-kinds on all groups with tier &lt;= highest matching tier. */
	public static List<String> getSkinKinds(Player player) {
		PermissionGroupDefinition highest = highestMatchingGroup(player);
		if (highest == null) {
			return new ArrayList<>(Cache.permissionGroupDefaultSkinKinds);
		}
		LinkedHashSet<String> kinds = new LinkedHashSet<>();
		for (String kind : Cache.permissionGroupDefaultSkinKinds) {
			if (kind != null && !kind.isBlank()) {
				kinds.add(kind.trim().toLowerCase(Locale.ROOT));
			}
		}
		int maxTier = highest.getTier();
		for (PermissionGroupDefinition group : Cache.permissionGroups) {
			if (group == null || group.getTier() > maxTier) {
				continue;
			}
			for (String kind : group.getSkinKinds()) {
				if (kind != null && !kind.isBlank()) {
					kinds.add(kind.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		return new ArrayList<>(kinds);
	}

	public static boolean getAllowArmor3dHelmet(Player player) {
		boolean fallback = Cache.permissionGroupDefaultAllowArmor3dHelmet;
		PermissionGroupDefinition highest = highestMatchingGroup(player);
		if (highest == null) {
			return fallback;
		}
		return walkDownAllowArmor3dHelmet(highest.getTier(), fallback);
	}

	public static int getDefaultNameColourStops() {
		return Cache.permissionGroupDefaults.getOrDefault(
			PermissionGroupDefinition.KEY_NAME_COLOUR_STOPS, 0
		);
	}

	public static int getDefaultMax3dPairBytes() {
		return Cache.permissionGroupDefaults.getOrDefault(
			PermissionGroupDefinition.KEY_MAX_3D_PAIR_BYTES, 30720
		);
	}

	public static int getDefaultSkinTokenCooldownDays() {
		return Cache.permissionGroupDefaults.getOrDefault(
			PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS, -1
		);
	}

	public static List<String> getDefaultSkinKinds() {
		return new ArrayList<>(Cache.permissionGroupDefaultSkinKinds);
	}

	public static boolean getDefaultAllowArmor3dHelmet() {
		return Cache.permissionGroupDefaultAllowArmor3dHelmet;
	}

	/**
	 * Effective perk for a group row: explicit value, else defaults.
	 */
	public static int groupPerkOrDefault(PermissionGroupDefinition group, String perkKey) {
		int fallback = Cache.permissionGroupDefaults.getOrDefault(perkKey, 0);
		if (perkKey.equals(PermissionGroupDefinition.KEY_SKIN_TOKEN_COOLDOWN_DAYS)) {
			fallback = getDefaultSkinTokenCooldownDays();
		}
		if (group == null) {
			return fallback;
		}
		if (group.hasPerk(perkKey)) {
			return group.getPerk(perkKey, fallback);
		}
		return fallback;
	}

	public static boolean groupAllowArmor3dHelmetOrDefault(PermissionGroupDefinition group) {
		boolean fallback = Cache.permissionGroupDefaultAllowArmor3dHelmet;
		if (group == null) {
			return fallback;
		}
		return group.getAllowArmor3dHelmet(fallback);
	}

	private static PermissionGroupDefinition highestMatchingGroup(Player player) {
		if (player == null) {
			return null;
		}
		PermissionGroupDefinition best = null;
		for (PermissionGroupDefinition group : Cache.permissionGroups) {
			String permission = group.getPermission();
			if (permission == null || permission.isBlank()) {
				continue;
			}
			if (!player.hasPermission(permission)) {
				continue;
			}
			if (best == null || group.getTier() > best.getTier()) {
				best = group;
			}
		}
		return best;
	}

	private static int walkDownInt(int maxTier, String perkKey, int fallback) {
		PermissionGroupDefinition found = null;
		for (PermissionGroupDefinition group : Cache.permissionGroups) {
			if (group == null || group.getTier() > maxTier) {
				continue;
			}
			if (!group.hasPerk(perkKey)) {
				continue;
			}
			if (found == null || group.getTier() > found.getTier()) {
				found = group;
			}
		}
		if (found == null) {
			return fallback;
		}
		return found.getPerk(perkKey, fallback);
	}

	private static boolean walkDownAllowArmor3dHelmet(int maxTier, boolean fallback) {
		PermissionGroupDefinition found = null;
		for (PermissionGroupDefinition group : Cache.permissionGroups) {
			if (group == null || group.getTier() > maxTier) {
				continue;
			}
			if (!group.hasAllowArmor3dHelmet()) {
				continue;
			}
			if (found == null || group.getTier() > found.getTier()) {
				found = group;
			}
		}
		if (found == null) {
			return fallback;
		}
		return found.getAllowArmor3dHelmet(fallback);
	}

	private static int resolvePerkMax(Player player, String perkKey) {
		int value = Cache.permissionGroupDefaults.getOrDefault(perkKey, 0);
		if (player == null) {
			return value;
		}
		for (PermissionGroupDefinition group : Cache.permissionGroups) {
			String permission = group.getPermission();
			if (permission == null || permission.isBlank()) {
				continue;
			}
			if (!player.hasPermission(permission)) {
				continue;
			}
			int groupValue = group.hasPerk(perkKey)
				? group.getPerk(perkKey, value)
				: value;
			value = Math.max(value, groupValue);
		}
		return value;
	}
}

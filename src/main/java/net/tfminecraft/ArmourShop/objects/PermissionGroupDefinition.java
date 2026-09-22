package net.tfminecraft.ArmourShop.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One rank row from permission-groups.yml (skin-upload entitlements).
 */
public final class PermissionGroupDefinition {

	public static final String KEY_NAME_COLOUR_STOPS = "name-colour-stops";
	public static final String KEY_MAX_3D_PAIR_BYTES = "max-3d-pair-bytes";
	public static final String KEY_SKIN_TOKEN_COOLDOWN_DAYS = "skin-token-cooldown-days";

	private final String id;
	private final String permission;
	private final String displayName;
	private final int tier;
	private final boolean visible;
	private final Map<String, Integer> perks;
	/** Additive kinds declared on this group row (may be empty). */
	private final List<String> skinKinds;
	/** Null = not set on this row (walk down / defaults). */
	private final Boolean allowArmor3dHelmet;

	public PermissionGroupDefinition(
		String id,
		String permission,
		String displayName,
		int tier,
		boolean visible,
		Map<String, Integer> perks,
		List<String> skinKinds,
		Boolean allowArmor3dHelmet
	) {
		this.id = id;
		this.permission = permission;
		this.displayName = displayName;
		this.tier = tier;
		this.visible = visible;
		this.perks = perks != null
			? Collections.unmodifiableMap(new HashMap<>(perks))
			: Map.of();
		this.skinKinds = skinKinds != null
			? Collections.unmodifiableList(new ArrayList<>(skinKinds))
			: List.of();
		this.allowArmor3dHelmet = allowArmor3dHelmet;
	}

	public String getId() {
		return id;
	}

	public String getPermission() {
		return permission;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getTier() {
		return tier;
	}

	public boolean isVisible() {
		return visible;
	}

	public Map<String, Integer> getPerks() {
		return perks;
	}

	public int getPerk(String key, int fallback) {
		return perks.getOrDefault(key, fallback);
	}

	public boolean hasPerk(String key) {
		return perks.containsKey(key);
	}

	public List<String> getSkinKinds() {
		return skinKinds;
	}

	public boolean hasAllowArmor3dHelmet() {
		return allowArmor3dHelmet != null;
	}

	public boolean getAllowArmor3dHelmet(boolean fallback) {
		return allowArmor3dHelmet != null ? allowArmor3dHelmet : fallback;
	}
}

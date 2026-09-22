package net.tfminecraft.ArmourShop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.tfminecraft.ArmourShop.objects.PermissionGroupDefinition;
import net.tfminecraft.ArmourShop.objects.ScrollOption;

public class Cache {
	public static List<Integer> points = new ArrayList<>();
	public static List<Integer> itemPoints = new ArrayList<>();

	/** Absolute path to ItemsAdder contents/ (parent of namespace folders). */
	public static String iaContentsPath = "";
	/** Absolute path to ArmourShop Categories/ (Step 8; may be empty). */
	public static String categoriesPath = "";

	/** GunsAndGadgets skins.yml (Step 15). */
	public static String gunsSkinsYmlPath = "";

	/** Daily force pull+reload at HH:mm server local; blank disables. */
	public static String forceReloadTime = "06:00";
	/** Seconds between iareload and iazip. */
	public static int iaReloadDelaySeconds = 5;

	/** Scroll ids+labels from config.yml (staff skins catalog). */
	public static List<ScrollOption> scrolls = new ArrayList<>();

	/** Defaults from permission-groups.yml (int perks). */
	public static Map<String, Integer> permissionGroupDefaults = new HashMap<>();
	/** Default skin-kinds list from permission-groups.yml. */
	public static List<String> permissionGroupDefaultSkinKinds = new ArrayList<>();
	/** Default allow-armor-3d-helmet from permission-groups.yml. */
	public static boolean permissionGroupDefaultAllowArmor3dHelmet = false;
	/** Rank groups from permission-groups.yml (sorted by tier). */
	public static List<PermissionGroupDefinition> permissionGroups = new ArrayList<>();
}

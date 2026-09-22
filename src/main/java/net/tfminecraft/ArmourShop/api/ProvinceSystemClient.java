package net.tfminecraft.ArmourShop.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.tfminecraft.ArmourShop.pack.model.PackPaths;

/**
 * Skins plugin routes via TFMCWeb {@link GatewayClient}.
 */
public class ProvinceSystemClient {


	/** Result for unlink (and similar ok/error POSTs). */
	public static final class SimpleResult {
		public final boolean ok;
		public final String error;

		private SimpleResult(boolean ok, String error) {
			this.ok = ok;
			this.error = error;
		}

		public static SimpleResult success() {
			return new SimpleResult(true, null);
		}

		public static SimpleResult fail(String error) {
			return new SimpleResult(false, error);
		}
	}

	/** Active unused skins code from GET /plugin/codes/active. */
	public static final class ActiveCode {
		public final String code;
		public final String playerUuid;
		public final String minecraftName;
		public final String createdAt;
		public final String expiresAt;

		public ActiveCode(
			String code,
			String playerUuid,
			String minecraftName,
			String createdAt,
			String expiresAt
		) {
			this.code = code;
			this.playerUuid = playerUuid;
			this.minecraftName = minecraftName;
			this.createdAt = createdAt;
			this.expiresAt = expiresAt;
		}
	}

	public static final class ActiveCodesResult {
		public final boolean ok;
		public final List<ActiveCode> codes;
		public final String error;

		private ActiveCodesResult(boolean ok, List<ActiveCode> codes, String error) {
			this.ok = ok;
			this.codes = codes == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(codes));
			this.error = error;
		}

		public static ActiveCodesResult success(List<ActiveCode> codes) {
			return new ActiveCodesResult(true, codes, null);
		}

		public static ActiveCodesResult fail(String error) {
			return new ActiveCodesResult(false, Collections.emptyList(), error);
		}
	}

	/** One approved-not-yet-applied submission from GET /plugin/approved. */
	public static final class ApprovedSubmission {
		public final String id;
		public final String playerUuid;
		public final String slug;
		public final String kind;
		public final String displayName;
		public final String gripPreset;
		public final String baseSet;
		/** Armor tier list (1-6): iron|steel|abyssalite|mythril|mage|infantry. Empty for non-armor kinds. */
		public final List<String> tiers;
		/**
		 * Armor tiers that use a 3D helmet model instead of a 16×16 icon.
		 * Empty for non-armor or all-flat armor.
		 */
		public final List<String> helmet3dTiers;
		/**
		 * Optional per-tier display suffix ({@code iron → Scout}). Empty map means
		 * default capitalized tier id (Iron, Steel, …).
		 */
		public final Map<String, String> tierAliases;
		public final boolean addName;
		public final List<String> nameColours;
		public final List<String> nameStyles;
		public final List<String> files;
		/** Staff curated lane (auto-approved; lands in tfmc_armorshop + category). */
		public final boolean staff;
		public final String category;
		public final String scroll;
		public final Map<String, String> tierScrolls;
		public final String iaNamespace;

		public ApprovedSubmission(
			String id,
			String playerUuid,
			String slug,
			String kind,
			String displayName,
			String gripPreset,
			String baseSet,
			List<String> tiers,
			Map<String, String> tierAliases,
			boolean addName,
			List<String> nameColours,
			List<String> nameStyles,
			List<String> files
		) {
			this(
				id,
				playerUuid,
				slug,
				kind,
				displayName,
				gripPreset,
				baseSet,
				tiers,
				List.of(),
				tierAliases,
				addName,
				nameColours,
				nameStyles,
				files,
				false,
				null,
				null,
				null,
				null
			);
		}

		public ApprovedSubmission(
			String id,
			String playerUuid,
			String slug,
			String kind,
			String displayName,
			String gripPreset,
			String baseSet,
			List<String> tiers,
			List<String> helmet3dTiers,
			Map<String, String> tierAliases,
			boolean addName,
			List<String> nameColours,
			List<String> nameStyles,
			List<String> files
		) {
			this(
				id,
				playerUuid,
				slug,
				kind,
				displayName,
				gripPreset,
				baseSet,
				tiers,
				helmet3dTiers,
				tierAliases,
				addName,
				nameColours,
				nameStyles,
				files,
				false,
				null,
				null,
				null,
				null
			);
		}

		public ApprovedSubmission(
			String id,
			String playerUuid,
			String slug,
			String kind,
			String displayName,
			String gripPreset,
			String baseSet,
			List<String> tiers,
			List<String> helmet3dTiers,
			Map<String, String> tierAliases,
			boolean addName,
			List<String> nameColours,
			List<String> nameStyles,
			List<String> files,
			boolean staff,
			String category,
			String scroll,
			Map<String, String> tierScrolls,
			String iaNamespace
		) {
			this.id = id;
			this.playerUuid = playerUuid;
			this.slug = slug;
			this.kind = kind;
			this.displayName = displayName;
			this.gripPreset = gripPreset;
			this.baseSet = baseSet;
			List<String> tierList = tiers == null
				? Collections.emptyList()
				: new ArrayList<>(tiers);
			if (tierList.isEmpty()
				&& "armor_set".equals(kind)
				&& baseSet != null
				&& !baseSet.isBlank()) {
				tierList = List.of(baseSet.trim());
			}
			this.tiers = Collections.unmodifiableList(tierList);
			List<String> h3d = new ArrayList<>();
			if (helmet3dTiers != null) {
				for (String t : helmet3dTiers) {
					if (t == null || t.isBlank()) {
						continue;
					}
					h3d.add(t.trim().toLowerCase(Locale.ROOT));
				}
			}
			this.helmet3dTiers = Collections.unmodifiableList(h3d);
			Map<String, String> aliasMap = new LinkedHashMap<>();
			if (tierAliases != null) {
				for (Map.Entry<String, String> e : tierAliases.entrySet()) {
					if (e.getKey() == null || e.getValue() == null) {
						continue;
					}
					String k = e.getKey().trim().toLowerCase(Locale.ROOT);
					String v = e.getValue().trim();
					if (!k.isEmpty() && !v.isEmpty()) {
						aliasMap.put(k, v);
					}
				}
			}
			this.tierAliases = Collections.unmodifiableMap(aliasMap);
			this.addName = addName;
			this.nameColours = nameColours == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(nameColours));
			this.nameStyles = nameStyles == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(nameStyles));
			this.files = files == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(files));
			this.staff = staff;
			this.category = category == null || category.isBlank() ? null : category.trim();
			this.scroll = scroll == null || scroll.isBlank() ? null : scroll.trim();
			Map<String, String> scrolls = new LinkedHashMap<>();
			if (tierScrolls != null) {
				for (Map.Entry<String, String> e : tierScrolls.entrySet()) {
					if (e.getKey() == null || e.getValue() == null) {
						continue;
					}
					String k = e.getKey().trim().toLowerCase(Locale.ROOT);
					String v = e.getValue().trim();
					if (!k.isEmpty() && !v.isEmpty()) {
						scrolls.put(k, v);
					}
				}
			}
			this.tierScrolls = Collections.unmodifiableMap(scrolls);
			this.iaNamespace = iaNamespace == null || iaNamespace.isBlank()
				? null
				: iaNamespace.trim();
		}

		/** IA pack namespace for this submission. */
		public String resolveNamespace() {
			if (iaNamespace != null && !iaNamespace.isBlank()) {
				return iaNamespace.trim();
			}
			return staff
				? PackPaths.STAFF_NAMESPACE
				: PackPaths.playerNamespace();
		}

		public boolean isHelmet3dTier(String tier) {
			if (tier == null || tier.isBlank()) {
				return false;
			}
			return helmet3dTiers.contains(tier.trim().toLowerCase(Locale.ROOT));
		}

		/**
		 * Shop/IA display prefix for one armor tier: {@code Norain Iron} or
		 * {@code Norain Scout} when an alias is set.
		 */
		public String displayNameForTier(String tier) {
			String base = displayName == null || displayName.isBlank()
				? (slug == null ? "" : slug.trim())
				: displayName.trim();
			String t = tier == null ? "" : tier.trim().toLowerCase(Locale.ROOT);
			String alias = tierAliases.get(t);
			if (alias == null || alias.isBlank()) {
				alias = capitalizeTier(t);
			}
			if (base.isEmpty()) {
				return alias;
			}
			if (alias.isEmpty()) {
				return base;
			}
			return base + " " + alias;
		}

		private static String capitalizeTier(String tier) {
			if (tier == null || tier.isEmpty()) {
				return "";
			}
			return Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
		}
	}

	public static final class ListResult {
		public final boolean ok;
		public final List<ApprovedSubmission> submissions;
		public final String error;

		private ListResult(boolean ok, List<ApprovedSubmission> submissions, String error) {
			this.ok = ok;
			this.submissions = submissions == null
				? Collections.emptyList()
				: Collections.unmodifiableList(submissions);
			this.error = error;
		}

		public static ListResult success(List<ApprovedSubmission> submissions) {
			return new ListResult(true, submissions, null);
		}

		public static ListResult fail(String error) {
			return new ListResult(false, Collections.emptyList(), error);
		}
	}

	/** Result for POST /plugin/applied. */
	public static final class AppliedResult {
		public final boolean ok;
		public final List<String> applied;
		public final String error;

		private AppliedResult(boolean ok, List<String> applied, String error) {
			this.ok = ok;
			this.applied = applied == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(applied));
			this.error = error;
		}

		public static AppliedResult success(List<String> applied) {
			return new AppliedResult(true, applied, null);
		}

		public static AppliedResult fail(String error) {
			return new AppliedResult(false, Collections.emptyList(), error);
		}
	}

	public static final class DownloadResult {
		public final boolean ok;
		public final byte[] data;
		public final String error;

		private DownloadResult(boolean ok, byte[] data, String error) {
			this.ok = ok;
			this.data = data;
			this.error = error;
		}

		public static DownloadResult success(byte[] data) {
			return new DownloadResult(true, data, null);
		}

		public static DownloadResult fail(String error) {
			return new DownloadResult(false, null, error);
		}
	}

	public static ActiveCodesResult listActiveCodes() {
		GatewayClient.Result raw = getJson("/skins/plugin/codes/active");
		if (!raw.ok) {
			return ActiveCodesResult.fail(raw.error);
		}
		return ActiveCodesResult.success(parseActiveCodes(raw.body));
	}

	public static SimpleResult revokeCode(String code) {
		String raw = code == null ? "" : code.trim();
		if (raw.isEmpty()) {
			return SimpleResult.fail("code is required");
		}
		String body = "{\"code\":\"" + escapeJson(raw) + "\"}";
		return postSimple("/skins/plugin/codes/revoke", body);
	}

	public static ListResult listApproved() {
		GatewayClient.Result raw = getJson("/skins/plugin/approved");
		if (!raw.ok) {
			return ListResult.fail(raw.error);
		}
		return ListResult.success(parseApprovedSubmissions(raw.body));
	}

	/**
	 * POST /skins/plugin/applied — ack submissions after IA reload.
	 */
	public static AppliedResult markApplied(List<String> submissionIds) {
		if (submissionIds == null || submissionIds.isEmpty()) {
			return AppliedResult.success(Collections.emptyList());
		}
		StringBuilder sb = new StringBuilder("{\"submission_ids\":[");
		boolean first = true;
		for (String id : submissionIds) {
			if (id == null || id.isBlank()) {
				continue;
			}
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(escapeJson(id.trim())).append('"');
		}
		sb.append("]}");
		if (first) {
			return AppliedResult.success(Collections.emptyList());
		}

		GatewayClient.Result raw = GatewayClient.request(
			"POST",
			"/skins/plugin/applied",
			sb.toString()
		);
		if (!raw.ok) {
			return AppliedResult.fail(raw.error);
		}
		return AppliedResult.success(jsonStringArray(raw.body, "applied"));
	}

	/** Result of PUT /skins/plugin/catalog. */
	public static final class CatalogPushResult {
		public final boolean ok;
		public final int categories;
		public final int skinSets;
		public final int scrolls;
		public final String updatedAt;
		public final String error;

		private CatalogPushResult(
			boolean ok,
			int categories,
			int skinSets,
			int scrolls,
			String updatedAt,
			String error
		) {
			this.ok = ok;
			this.categories = categories;
			this.skinSets = skinSets;
			this.scrolls = scrolls;
			this.updatedAt = updatedAt;
			this.error = error;
		}

		public static CatalogPushResult success(
			int categories,
			int skinSets,
			int scrolls,
			String updatedAt
		) {
			return new CatalogPushResult(true, categories, skinSets, scrolls, updatedAt, null);
		}

		public static CatalogPushResult fail(String error) {
			return new CatalogPushResult(false, 0, 0, 0, null, error);
		}
	}

	/**
	 * PUT /skins/plugin/catalog — full-replace categories + scrolls snapshot.
	 */
	public static CatalogPushResult pushCatalog(String jsonBody) {
		if (jsonBody == null || jsonBody.isBlank()) {
			return CatalogPushResult.fail("Catalog payload is empty.");
		}
		GatewayClient.Result raw = GatewayClient.request(
			"PUT",
			"/skins/plugin/catalog",
			jsonBody
		);
		if (!raw.ok) {
			return CatalogPushResult.fail(raw.error);
		}
		return CatalogPushResult.success(
			jsonInt(raw.body, "categories"),
			jsonInt(raw.body, "skin_sets"),
			jsonInt(raw.body, "scrolls"),
			jsonString(raw.body, "updated_at")
		);
	}

	/** One submission from GET /plugin/submissions/{id}. */
	public static final class PluginSubmission {
		public final String id;
		public final String playerUuid;
		public final String slug;
		public final String kind;
		public final String displayName;
		public final String status;
		public final String baseSet;
		/** Armor tier list (1-6). Empty for non-armor kinds. */
		public final List<String> tiers;
		public final boolean staff;
		public final String category;
		public final String iaNamespace;

		public PluginSubmission(
			String id,
			String playerUuid,
			String slug,
			String kind,
			String displayName,
			String status,
			String baseSet,
			List<String> tiers,
			boolean staff,
			String category,
			String iaNamespace
		) {
			this.id = id;
			this.playerUuid = playerUuid;
			this.slug = slug;
			this.kind = kind;
			this.displayName = displayName;
			this.status = status;
			this.baseSet = baseSet;
			List<String> tierList = tiers == null
				? Collections.emptyList()
				: new ArrayList<>(tiers);
			if (tierList.isEmpty()
				&& "armor_set".equals(kind)
				&& baseSet != null
				&& !baseSet.isBlank()) {
				tierList = List.of(baseSet.trim());
			}
			this.tiers = Collections.unmodifiableList(tierList);
			this.staff = staff;
			this.category = category;
			this.iaNamespace = iaNamespace;
		}

		public String resolveNamespace() {
			if (iaNamespace != null && !iaNamespace.isBlank()) {
				return iaNamespace.trim();
			}
			return staff
				? PackPaths.STAFF_NAMESPACE
				: PackPaths.playerNamespace();
		}
	}

	public static final class PluginSubmissionResult {
		public final boolean ok;
		public final PluginSubmission submission;
		public final String error;

		private PluginSubmissionResult(boolean ok, PluginSubmission submission, String error) {
			this.ok = ok;
			this.submission = submission;
			this.error = error;
		}

		public static PluginSubmissionResult success(PluginSubmission submission) {
			return new PluginSubmissionResult(true, submission, null);
		}

		public static PluginSubmissionResult fail(String error) {
			return new PluginSubmissionResult(false, null, error);
		}
	}

	public static PluginSubmissionResult getSubmission(String submissionId) {
		String id = submissionId == null ? "" : submissionId.trim();
		if (id.isEmpty()) {
			return PluginSubmissionResult.fail("submission id is required");
		}
		GatewayClient.Result raw = getJson("/skins/plugin/submissions/" + id);
		if (!raw.ok) {
			return PluginSubmissionResult.fail(raw.error);
		}
		String response = raw.body;
		String sid = jsonString(response, "id");
		String slug = jsonString(response, "slug");
		if (sid == null || sid.isBlank() || slug == null || slug.isBlank()) {
			return PluginSubmissionResult.fail("submission response missing id/slug");
		}
		return PluginSubmissionResult.success(new PluginSubmission(
			sid,
			jsonString(response, "player_uuid"),
			slug,
			jsonString(response, "kind"),
			jsonString(response, "display_name"),
			jsonString(response, "status"),
			jsonString(response, "base_set"),
			jsonStringArray(response, "tiers"),
			jsonTruthy(response, "staff"),
			jsonString(response, "category"),
			jsonString(response, "ia_namespace")
		));
	}

	public static SimpleResult revokeSubmission(String submissionId) {
		String id = submissionId == null ? "" : submissionId.trim();
		if (id.isEmpty()) {
			return SimpleResult.fail("submission id is required");
		}
		return postSimple("/skins/plugin/submissions/" + id + "/revoke", "{}");
	}

	public static final class DeletableListResult {
		public final boolean ok;
		public final List<String> ids;
		public final String error;

		private DeletableListResult(boolean ok, List<String> ids, String error) {
			this.ok = ok;
			this.ids = ids == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(ids));
			this.error = error;
		}

		public static DeletableListResult success(List<String> ids) {
			return new DeletableListResult(true, ids, null);
		}

		public static DeletableListResult fail(String error) {
			return new DeletableListResult(false, Collections.emptyList(), error);
		}
	}

	/** GET /plugin/submissions/deletable — player-lane ids for tab-complete. */
	public static DeletableListResult listDeletableSubmissionIds() {
		return listDeletableIds("/skins/plugin/submissions/deletable", "submissions");
	}

	/** GET /plugin/skins/deletable — staff-lane ids for tab-complete. */
	public static DeletableListResult listDeletableStaffSkinIds() {
		return listDeletableIds("/skins/plugin/skins/deletable", "skins");
	}

	private static DeletableListResult listDeletableIds(String path, String arrayKey) {
		GatewayClient.Result raw = getJson(path);
		if (!raw.ok) {
			return DeletableListResult.fail(raw.error);
		}
		List<String> ids = new ArrayList<>();
		String array = jsonArrayBody(raw.body, arrayKey);
		if (array != null && !array.isBlank()) {
			for (String obj : splitJsonObjects(array)) {
				String sid = jsonString(obj, "id");
				if (sid != null && !sid.isBlank()) {
					ids.add(sid.trim());
				}
			}
		}
		return DeletableListResult.success(ids);
	}

	public static DownloadResult downloadSubmissionFile(String submissionId, String filename) {
		String id = submissionId == null ? "" : submissionId.trim();
		String name = filename == null ? "" : filename.trim();
		if (id.isEmpty() || name.isEmpty()) {
			return DownloadResult.fail("submission id and filename are required");
		}
		if (name.contains("..") || name.contains("/") || name.contains("\\")) {
			return DownloadResult.fail("invalid filename");
		}

		String path = "/skins/plugin/submissions/"
			+ id
			+ "/files/"
			+ java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)
				.replace("+", "%20");
		GatewayClient.BytesDownload dl = GatewayClient.download(path);
		if (!dl.ok) {
			return DownloadResult.fail(dl.error);
		}
		if (dl.data == null || dl.data.length == 0) {
			return DownloadResult.fail("Empty file: " + name);
		}
		return DownloadResult.success(dl.data);
	}

	private static SimpleResult postSimple(String path, String jsonBody) {
		return requestSimple("POST", path, jsonBody);
	}

	private static SimpleResult putSimple(String path, String jsonBody) {
		return requestSimple("PUT", path, jsonBody);
	}

	private static SimpleResult requestSimple(String method, String path, String jsonBody) {
		GatewayClient.Result raw = GatewayClient.request(method, path, jsonBody);
		if (raw.ok) {
			return SimpleResult.success();
		}
		return SimpleResult.fail(raw.error);
	}

	/** GET JSON body via TFMCWeb gateway. */
	private static GatewayClient.Result getJson(String path) {
		return GatewayClient.request("GET", path, null);
	}

	static List<ApprovedSubmission> parseApprovedSubmissions(String json) {
		List<ApprovedSubmission> out = new ArrayList<>();
		if (json == null || json.isEmpty()) {
			return out;
		}
		String array = jsonArrayBody(json, "submissions");
		if (array == null) {
			return out;
		}
		for (String obj : splitJsonObjects(array)) {
			String id = jsonString(obj, "id");
			if (id == null || id.isEmpty()) {
				continue;
			}
			out.add(new ApprovedSubmission(
				id,
				jsonString(obj, "player_uuid"),
				jsonString(obj, "slug"),
				jsonString(obj, "kind"),
				jsonString(obj, "display_name"),
				jsonString(obj, "grip_preset"),
				jsonString(obj, "base_set"),
				jsonStringArray(obj, "tiers"),
				jsonStringArray(obj, "helmet_3d_tiers"),
				jsonStringMap(obj, "tier_aliases"),
				"true".equalsIgnoreCase(jsonString(obj, "add_name")),
				jsonStringArray(obj, "name_colours"),
				jsonStringArray(obj, "name_styles"),
				jsonStringArray(obj, "files"),
				jsonTruthy(obj, "staff"),
				jsonString(obj, "category"),
				jsonString(obj, "scroll"),
				jsonStringMap(obj, "tier_scrolls"),
				jsonString(obj, "ia_namespace")
			));
		}
		return out;
	}

	static List<ActiveCode> parseActiveCodes(String json) {
		List<ActiveCode> out = new ArrayList<>();
		if (json == null || json.isEmpty()) {
			return out;
		}
		String array = jsonArrayBody(json, "codes");
		if (array == null) {
			return out;
		}
		for (String obj : splitJsonObjects(array)) {
			String code = jsonString(obj, "code");
			if (code == null || code.isEmpty()) {
				continue;
			}
			out.add(new ActiveCode(
				code,
				jsonString(obj, "player_uuid"),
				jsonString(obj, "minecraft_name"),
				jsonString(obj, "created_at"),
				jsonString(obj, "expires_at")
			));
		}
		return out;
	}

	/** Content inside the named JSON array (without brackets), or null. */
	static String jsonArrayBody(String json, String key) {
		if (json == null || key == null) {
			return null;
		}
		String needle = "\"" + key + "\"";
		int keyIdx = json.indexOf(needle);
		if (keyIdx < 0) {
			return null;
		}
		int colon = json.indexOf(':', keyIdx + needle.length());
		if (colon < 0) {
			return null;
		}
		int i = colon + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		if (i >= json.length() || json.charAt(i) != '[') {
			return null;
		}
		int start = i + 1;
		int depth = 1;
		boolean inString = false;
		boolean escape = false;
		for (i = start; i < json.length(); i++) {
			char ch = json.charAt(i);
			if (inString) {
				if (escape) {
					escape = false;
				} else if (ch == '\\') {
					escape = true;
				} else if (ch == '"') {
					inString = false;
				}
				continue;
			}
			if (ch == '"') {
				inString = true;
			} else if (ch == '[') {
				depth++;
			} else if (ch == ']') {
				depth--;
				if (depth == 0) {
					return json.substring(start, i);
				}
			}
		}
		return null;
	}

	static List<String> splitJsonObjects(String arrayBody) {
		List<String> objects = new ArrayList<>();
		if (arrayBody == null) {
			return objects;
		}
		int depth = 0;
		int objStart = -1;
		boolean inString = false;
		boolean escape = false;
		for (int i = 0; i < arrayBody.length(); i++) {
			char ch = arrayBody.charAt(i);
			if (inString) {
				if (escape) {
					escape = false;
				} else if (ch == '\\') {
					escape = true;
				} else if (ch == '"') {
					inString = false;
				}
				continue;
			}
			if (ch == '"') {
				inString = true;
			} else if (ch == '{') {
				if (depth == 0) {
					objStart = i;
				}
				depth++;
			} else if (ch == '}') {
				depth--;
				if (depth == 0 && objStart >= 0) {
					objects.add(arrayBody.substring(objStart, i + 1));
					objStart = -1;
				}
			}
		}
		return objects;
	}

	static List<String> jsonStringArray(String json, String key) {
		List<String> out = new ArrayList<>();
		String body = jsonArrayBody(json, key);
		if (body == null) {
			return out;
		}
		boolean inString = false;
		boolean escape = false;
		StringBuilder cur = null;
		for (int i = 0; i < body.length(); i++) {
			char ch = body.charAt(i);
			if (!inString) {
				if (ch == '"') {
					inString = true;
					escape = false;
					cur = new StringBuilder();
				}
				continue;
			}
			if (escape) {
				cur.append(ch);
				escape = false;
				continue;
			}
			if (ch == '\\') {
				escape = true;
				continue;
			}
			if (ch == '"') {
				inString = false;
				out.add(cur.toString());
				cur = null;
				continue;
			}
			cur.append(ch);
		}
		return out;
	}

	/** Content inside the named JSON object (without braces), or null. */
	static String jsonObjectBody(String json, String key) {
		if (json == null || key == null) {
			return null;
		}
		String needle = "\"" + key + "\"";
		int keyIdx = json.indexOf(needle);
		if (keyIdx < 0) {
			return null;
		}
		int colon = json.indexOf(':', keyIdx + needle.length());
		if (colon < 0) {
			return null;
		}
		int i = colon + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		if (i >= json.length() || json.charAt(i) != '{') {
			return null;
		}
		int start = i + 1;
		int depth = 1;
		boolean inString = false;
		boolean escape = false;
		for (i = start; i < json.length(); i++) {
			char ch = json.charAt(i);
			if (inString) {
				if (escape) {
					escape = false;
				} else if (ch == '\\') {
					escape = true;
				} else if (ch == '"') {
					inString = false;
				}
				continue;
			}
			if (ch == '"') {
				inString = true;
			} else if (ch == '{') {
				depth++;
			} else if (ch == '}') {
				depth--;
				if (depth == 0) {
					return json.substring(start, i);
				}
			}
		}
		return null;
	}

	/** Parse a JSON object of string→string values (e.g. tier_aliases). */
	static Map<String, String> jsonStringMap(String json, String key) {
		Map<String, String> out = new LinkedHashMap<>();
		String body = jsonObjectBody(json, key);
		if (body == null || body.isBlank()) {
			return out;
		}
		boolean inString = false;
		boolean escape = false;
		StringBuilder cur = null;
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < body.length(); i++) {
			char ch = body.charAt(i);
			if (!inString) {
				if (ch == '"') {
					inString = true;
					escape = false;
					cur = new StringBuilder();
				}
				continue;
			}
			if (escape) {
				cur.append(ch);
				escape = false;
				continue;
			}
			if (ch == '\\') {
				escape = true;
				continue;
			}
			if (ch == '"') {
				inString = false;
				tokens.add(cur.toString());
				cur = null;
				continue;
			}
			cur.append(ch);
		}
		for (int i = 0; i + 1 < tokens.size(); i += 2) {
			String k = tokens.get(i);
			String v = tokens.get(i + 1);
			if (k != null && !k.isBlank() && v != null) {
				out.put(k.trim().toLowerCase(Locale.ROOT), v.trim());
			}
		}
		return out;
	}

	/** Extract a JSON string field value (simple, no nested objects). */
	static String jsonString(String json, String key) {
		if (json == null || key == null) {
			return null;
		}
		String needle = "\"" + key + "\"";
		int keyIdx = json.indexOf(needle);
		if (keyIdx < 0) {
			return null;
		}
		int colon = json.indexOf(':', keyIdx + needle.length());
		if (colon < 0) {
			return null;
		}
		int i = colon + 1;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		if (i >= json.length()) {
			return null;
		}
		char c = json.charAt(i);
		if (c == '"') {
			StringBuilder out = new StringBuilder();
			i++;
			while (i < json.length()) {
				char ch = json.charAt(i++);
				if (ch == '\\' && i < json.length()) {
					out.append(json.charAt(i++));
					continue;
				}
				if (ch == '"') {
					break;
				}
				out.append(ch);
			}
			return out.toString();
		}
		if (c == 'n' && json.startsWith("null", i)) {
			return null;
		}
		int start = i;
		while (i < json.length()) {
			char ch = json.charAt(i);
			if (ch == ',' || ch == '}' || ch == ']') {
				break;
			}
			i++;
		}
		return json.substring(start, i).trim();
	}

	/** True for JSON boolean true or string "true". */
	static boolean jsonTruthy(String json, String key) {
		String raw = jsonString(json, key);
		return raw != null && "true".equalsIgnoreCase(raw.trim());
	}

	/** Extract a top-level JSON integer field. */
	static int jsonInt(String json, String key) {
		String raw = jsonString(json, key);
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static String escapeJson(String raw) {
		if (raw == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(raw.length() + 8);
		for (int i = 0; i < raw.length(); i++) {
			char ch = raw.charAt(i);
			switch (ch) {
				case '\\':
				case '"':
					sb.append('\\').append(ch);
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					sb.append(ch);
			}
		}
		return sb.toString();
	}
}

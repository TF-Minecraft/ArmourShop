package net.tfminecraft.ArmourShop.pack.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Input for pack writers (Bukkit-free).
 */
public final class PackSubmission {

	private final String slug;
	private final String displayName;
	private final PackKind kind;
	/** Optional legacy field; pack model JSON is built by ProvinceSystem. */
	private final Double gripY;
	/** File stem → PNG/JSON bytes (e.g. helmet, texture, model). */
	private final Map<String, byte[]> files;

	public PackSubmission(
		String slug,
		String displayName,
		PackKind kind,
		Map<String, byte[]> files
	) {
		this(slug, displayName, kind, null, files);
	}

	public PackSubmission(
		String slug,
		String displayName,
		PackKind kind,
		Double gripY,
		Map<String, byte[]> files
	) {
		this.slug = Objects.requireNonNull(slug, "slug").trim();
		this.displayName = Objects.requireNonNull(displayName, "displayName").trim();
		this.kind = Objects.requireNonNull(kind, "kind");
		if (this.slug.isEmpty()) {
			throw new IllegalArgumentException("slug is required");
		}
		if (this.displayName.isEmpty()) {
			throw new IllegalArgumentException("displayName is required");
		}
		// gripY retained for API/history only; model JSON is web-built.
		this.gripY = gripY == null ? null : GripY.clamp(gripY);
		Map<String, byte[]> copy = new LinkedHashMap<>();
		if (files != null) {
			for (Map.Entry<String, byte[]> e : files.entrySet()) {
				if (e.getKey() == null || e.getValue() == null) {
					continue;
				}
				copy.put(e.getKey().trim(), e.getValue());
			}
		}
		this.files = Collections.unmodifiableMap(copy);
	}

	public String slug() {
		return slug;
	}

	public String displayName() {
		return displayName;
	}

	public PackKind kind() {
		return kind;
	}

	/** Optional; unused by pack writers (model JSON is web-built). */
	public Double gripY() {
		return gripY;
	}

	public Map<String, byte[]> files() {
		return files;
	}

	public byte[] requireFile(String stem) {
		byte[] data = files.get(stem);
		if (data == null || data.length == 0) {
			throw new IllegalArgumentException("Missing file for stem: " + stem);
		}
		return data;
	}
}

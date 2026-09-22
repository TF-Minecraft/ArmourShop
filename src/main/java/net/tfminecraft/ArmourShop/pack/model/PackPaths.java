package net.tfminecraft.ArmourShop.pack.model;

import java.nio.file.Path;

/**
 * Paths under ItemsAdder contents/ for pack namespaces.
 */
public final class PackPaths {

	/** Main-realm player submissions namespace (legacy / default). */
	public static final String NAMESPACE = "tfmc_submissions";
	public static final String STAFF_NAMESPACE = "tfmc_armorshop";

	private PackPaths() {}

	/**
	 * Player pack namespace for this box's realm.
	 * {@code main} → {@link #NAMESPACE}; otherwise {@code tfmc_submissions_<realm>}.
	 */
	public static String playerNamespace() {
		String realm = currentRealm();
		if (realm == null || realm.isBlank() || "main".equals(realm)) {
			return NAMESPACE;
		}
		return NAMESPACE + "_" + realm;
	}

	private static String currentRealm() {
		try {
			Class<?> cls = Class.forName("net.tfminecraft.TFMCWeb.TFMCWeb");
			Object realm = cls.getMethod("getRealmId").invoke(null);
			if (realm == null) {
				return "main";
			}
			String text = String.valueOf(realm).trim().toLowerCase();
			return text.isEmpty() ? "main" : text;
		} catch (Throwable ignored) {
			return "main";
		}
	}

	/** `{contents}/tfmc_submissions` (or realm-scoped player namespace). */
	public static Path namespaceRoot(Path contentsRoot) {
		return namespaceRoot(contentsRoot, playerNamespace());
	}

	public static Path namespaceRoot(Path contentsRoot, String namespace) {
		return contentsRoot.resolve(requireNamespace(namespace));
	}

	public static Path configsDir(Path contentsRoot) {
		return configsDir(contentsRoot, playerNamespace());
	}

	public static Path configsDir(Path contentsRoot, String namespace) {
		return namespaceRoot(contentsRoot, namespace).resolve("configs");
	}

	public static Path assetsRoot(Path contentsRoot) {
		return assetsRoot(contentsRoot, playerNamespace());
	}

	public static Path assetsRoot(Path contentsRoot, String namespace) {
		String ns = requireNamespace(namespace);
		return namespaceRoot(contentsRoot, ns)
			.resolve("resourcepack")
			.resolve("assets")
			.resolve(ns);
	}

	public static Path texturesRoot(Path contentsRoot) {
		return texturesRoot(contentsRoot, playerNamespace());
	}

	public static Path texturesRoot(Path contentsRoot, String namespace) {
		return assetsRoot(contentsRoot, namespace).resolve("textures");
	}

	public static Path armorIconsDir(Path contentsRoot) {
		return armorIconsDir(contentsRoot, playerNamespace());
	}

	public static Path armorIconsDir(Path contentsRoot, String namespace) {
		return texturesRoot(contentsRoot, namespace).resolve("armor_icons");
	}

	public static Path armorLayersDir(Path contentsRoot) {
		return armorLayersDir(contentsRoot, playerNamespace());
	}

	public static Path armorLayersDir(Path contentsRoot, String namespace) {
		return texturesRoot(contentsRoot, namespace).resolve("armor_layers");
	}

	public static Path itemTexturesDir(Path contentsRoot) {
		return itemTexturesDir(contentsRoot, playerNamespace());
	}

	public static Path itemTexturesDir(Path contentsRoot, String namespace) {
		return texturesRoot(contentsRoot, namespace).resolve("item");
	}

	public static Path itemModelsDir(Path contentsRoot) {
		return itemModelsDir(contentsRoot, playerNamespace());
	}

	public static Path itemModelsDir(Path contentsRoot, String namespace) {
		return assetsRoot(contentsRoot, namespace).resolve("models").resolve("item");
	}

	private static String requireNamespace(String namespace) {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("namespace is required");
		}
		return namespace.trim();
	}
}

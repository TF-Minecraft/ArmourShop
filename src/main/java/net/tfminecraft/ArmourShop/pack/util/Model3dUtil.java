package net.tfminecraft.ArmourShop.pack.util;


import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Shared helpers for donor Blockbench models ({@code generate: false}).
 * Texture rewrite for pack apply is owned by ProvinceSystem pack_models;
 * writers place web-built JSON as-is. {@link #normalizeModel} is tooling-only.
 */
public final class Model3dUtil {

	public static final String TEXTURE_STEM = "texture";
	public static final String MODEL_STEM = "model";
	public static final String HELMET_TEXTURE_STEM = "helmet_texture";
	public static final String HELMET_MODEL_STEM = "helmet_model";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Model3dUtil() {}

	/**
	 * Rewrite every textures.* string to {@code namespace:item/{textureId}} and
	 * return pretty-printed JSON bytes.
	 */
	public static byte[] normalizeModel(byte[] modelJson, String textureId) {
		if (modelJson == null || modelJson.length == 0) {
			throw new IllegalArgumentException("model JSON is empty");
		}
		String text = new String(modelJson, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(text).getAsJsonObject();
		String texPath = PackPaths.playerNamespace() + ":item/" + textureId;
		JsonObject textures = root.has("textures") && root.get("textures").isJsonObject()
			? root.getAsJsonObject("textures")
			: new JsonObject();
		if (textures.size() == 0) {
			textures.addProperty("0", texPath);
			textures.addProperty("particle", texPath);
		} else {
			for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
				if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
					textures.addProperty(e.getKey(), texPath);
				}
			}
		}
		root.add("textures", textures);
		return (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Replace player-pack namespace prefixes in model JSON when writing staff packs.
	 * No-op when {@code targetNamespace} is already {@link PackPaths#NAMESPACE}.
	 */
	public static byte[] rewriteNamespacePrefix(byte[] modelJson, String targetNamespace) {
		if (modelJson == null || modelJson.length == 0) {
			return modelJson;
		}
		String ns = targetNamespace == null ? "" : targetNamespace.trim();
		if (ns.isEmpty() || PackPaths.NAMESPACE.equals(ns)) {
			return modelJson;
		}
		String text = new String(modelJson, StandardCharsets.UTF_8);
		String from = PackPaths.NAMESPACE + ":";
		String to = ns + ":";
		if (!text.contains(from)) {
			return modelJson;
		}
		return text.replace(from, to).getBytes(StandardCharsets.UTF_8);
	}

	public static JsonObject parseObject(byte[] modelJson) {
		String text = new String(modelJson, StandardCharsets.UTF_8);
		return JsonParser.parseString(text).getAsJsonObject();
	}

	public static byte[] toBytes(JsonObject root) {
		return (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
	}
}

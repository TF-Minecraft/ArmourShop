package net.tfminecraft.armourshop.pack.writer.vehicle;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.armourshop.pack.util.YamlUtil;

/**
 * Website vehicle skins: a copy of the vehicle's ModelEngine model with the player's textures.
 *
 * <p>Only textures change. The copy keeps every cube, bone and animation of the vehicle's
 * current default model, so VehicleFramework finds the same seats, weapons and effects. Each
 * uploaded PNG must have the exact size of the texture it replaces. The copy goes to
 * {@code <blueprints>/tfmc_submissions/vskin_<slug>.bbmodel}, and VehicleFramework's
 * {@code player-skins.yml} gets an owner-only entry ({@code armourshop.submission.<slug>}).
 */
public final class VehicleSkinWriter {

	public static final String SUBFOLDER = "tfmc_submissions";
	public static final String MODEL_PREFIX = "vskin_";
	private static final String DATA_URI = "data:image/png;base64,";
	private static final Object LOCK = new Object();
	/** Bumped on every write or removal; the reload service runs {@code meg reload} when it moved. */
	private static final AtomicLong GENERATION = new AtomicLong();

	private VehicleSkinWriter() {}

	public static long generation() {
		return GENERATION.get();
	}

	public static String modelId(String slug) {
		return MODEL_PREFIX + slug;
	}

	public static Path blueprintPath(Path blueprintsRoot, String slug) {
		return blueprintsRoot.resolve(SUBFOLDER).resolve(modelId(slug) + ".bbmodel");
	}

	/**
	 * Writes the skin's model copy and its player-skins.yml entry.
	 *
	 * @param textures one PNG per texture of the base model, in the model's texture order
	 */
	public static void write(
		Path blueprintsRoot,
		Path vehiclesDir,
		Path playerSkinsYml,
		String vehicleId,
		String slug,
		String displayName,
		String permission,
		List<byte[]> textures
	) throws IOException {
		YamlUtil.validateSlug(require(slug, "slug"));
		String vehicle = require(vehicleId, "vehicle (base_set)");
		String baseModel = defaultModel(vehiclesDir, vehicle);
		Path base = findBlueprint(blueprintsRoot, baseModel)
			.orElseThrow(() -> new IllegalStateException(
				"no ModelEngine blueprint " + baseModel + ".bbmodel for vehicle " + vehicle));
		String id = modelId(slug);
		byte[] copy = reskin(Files.readAllBytes(base), id, textures);
		synchronized (LOCK) {
			Path out = blueprintPath(blueprintsRoot, slug);
			Files.createDirectories(out.getParent());
			writeAtomically(out, copy);
			upsertEntry(playerSkinsYml, id, vehicle, displayName, permission);
			GENERATION.incrementAndGet();
		}
	}

	/** Deletes the model copy and the player-skins.yml entry (best effort, either may be absent). */
	public static boolean remove(Path blueprintsRoot, Path playerSkinsYml, String slug) throws IOException {
		YamlUtil.validateSlug(require(slug, "slug"));
		synchronized (LOCK) {
			boolean removed = Files.deleteIfExists(blueprintPath(blueprintsRoot, slug));
			if (playerSkinsYml != null && Files.isRegularFile(playerSkinsYml)) {
				YamlConfiguration yml = load(playerSkinsYml);
				if (yml.contains(modelId(slug))) {
					yml.set(modelId(slug), null);
					writeAtomically(playerSkinsYml, yml.saveToString().getBytes(StandardCharsets.UTF_8));
					removed = true;
				}
			}
			if (removed) {
				GENERATION.incrementAndGet();
			}
			return removed;
		}
	}

	/** The ModelEngine model of the vehicle's default skin, read from VehicleFramework's vehicle YAML. */
	static String defaultModel(Path vehiclesDir, String vehicleId) throws IOException {
		if (vehiclesDir == null || !Files.isDirectory(vehiclesDir)) {
			throw new IllegalStateException("VehicleFramework vehicles folder not found: " + vehiclesDir);
		}
		try (Stream<Path> files = Files.list(vehiclesDir)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
				if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
					continue;
				}
				YamlConfiguration yml;
				try {
					yml = load(file);
				} catch (IOException e) {
					continue;
				}
				ConfigurationSection vehicle = yml.getConfigurationSection(vehicleId);
				if (vehicle == null) {
					continue;
				}
				String defaultSkin = vehicle.getString("model");
				String model = defaultSkin == null ? null : vehicle.getString("skins." + defaultSkin + ".model");
				if (model == null || model.isBlank()) {
					throw new IllegalStateException("vehicle " + vehicleId + " has no default skin model");
				}
				return model.trim();
			}
		}
		throw new IllegalStateException("unknown vehicle: " + vehicleId);
	}

	/** Finds {@code <model>.bbmodel} anywhere under the blueprints folder, outside our own subfolder. */
	static Optional<Path> findBlueprint(Path blueprintsRoot, String model) throws IOException {
		if (blueprintsRoot == null || !Files.isDirectory(blueprintsRoot)) {
			throw new IllegalStateException("ModelEngine blueprints folder not found: " + blueprintsRoot);
		}
		String wanted = model.toLowerCase(Locale.ROOT) + ".bbmodel";
		Path own = blueprintsRoot.resolve(SUBFOLDER);
		try (Stream<Path> files = Files.walk(blueprintsRoot)) {
			return files
				.filter(p -> !p.startsWith(own))
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).equals(wanted))
				.sorted()
				.findFirst();
		}
	}

	/**
	 * Returns the base model with each texture swapped for the matching upload and renamed after
	 * the skin, so its pack textures never clash with the vehicle's own.
	 */
	static byte[] reskin(byte[] baseModel, String modelId, List<byte[]> uploads) {
		JsonObject model = JsonParser.parseString(new String(baseModel, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonElement texturesElement = model.get("textures");
		if (texturesElement == null || !texturesElement.isJsonArray() || texturesElement.getAsJsonArray().isEmpty()) {
			throw new IllegalStateException("base model has no textures");
		}
		JsonArray textures = texturesElement.getAsJsonArray();
		int count = uploads == null ? 0 : uploads.size();
		if (count != textures.size()) {
			throw new IllegalStateException("vehicle has " + textures.size() + " texture(s), upload has " + count);
		}
		for (int i = 0; i < textures.size(); i++) {
			JsonObject texture = textures.get(i).getAsJsonObject();
			int[] want = size(decodeSource(texture, i), "base texture " + i);
			byte[] upload = uploads.get(i);
			int[] got = size(upload, "texture " + i);
			if (want[0] != got[0] || want[1] != got[1]) {
				throw new IllegalStateException("texture " + i + " must be " + want[0] + "x" + want[1]
					+ ", upload is " + got[0] + "x" + got[1]);
			}
			texture.addProperty("source", DATA_URI + Base64.getEncoder().encodeToString(upload));
			texture.addProperty("name", modelId + "_" + i + ".png");
			// Blockbench keeps the author's disk paths; ModelEngine reads only the embedded source.
			texture.remove("path");
			texture.remove("relative_path");
		}
		model.addProperty("name", modelId);
		if (model.has("model_identifier")) {
			model.addProperty("model_identifier", modelId);
		}
		return (model.toString() + "\n").getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] decodeSource(JsonObject texture, int index) {
		JsonElement source = texture.get("source");
		String value = source == null || !source.isJsonPrimitive() ? "" : source.getAsString();
		int comma = value.indexOf(',');
		if (!value.startsWith("data:image/png;base64") || comma < 0) {
			throw new IllegalStateException("base texture " + index + " is not an embedded PNG");
		}
		return Base64.getDecoder().decode(value.substring(comma + 1));
	}

	private static int[] size(byte[] png, String what) {
		BufferedImage image;
		try {
			image = png == null ? null : ImageIO.read(new ByteArrayInputStream(png));
		} catch (IOException e) {
			image = null;
		}
		if (image == null) {
			throw new IllegalStateException(what + " is not a readable PNG");
		}
		return new int[]{image.getWidth(), image.getHeight()};
	}

	private static void upsertEntry(
		Path playerSkinsYml,
		String id,
		String vehicle,
		String displayName,
		String permission
	) throws IOException {
		YamlConfiguration yml = Files.isRegularFile(playerSkinsYml) ? load(playerSkinsYml) : new YamlConfiguration();
		if (!yml.contains(id)) {
			yml.options().setHeader(List.of(
				"Website vehicle skins, written by ArmourShop. Edit on the website, not here.",
				"VehicleFramework reads this file at startup and on /vf skins reload."
			));
		}
		yml.set(id + ".vehicle", vehicle);
		yml.set(id + ".name", displayName == null || displayName.isBlank() ? id : displayName.trim());
		yml.set(id + ".model", id);
		yml.set(id + ".permission", require(permission, "permission"));
		Files.createDirectories(playerSkinsYml.toAbsolutePath().getParent());
		writeAtomically(playerSkinsYml, yml.saveToString().getBytes(StandardCharsets.UTF_8));
	}

	private static YamlConfiguration load(Path file) throws IOException {
		YamlConfiguration yml = new YamlConfiguration();
		try {
			yml.load(file.toFile());
		} catch (InvalidConfigurationException e) {
			throw new IOException("cannot read " + file.getFileName() + ": " + e.getMessage(), e);
		}
		return yml;
	}

	private static void writeAtomically(Path target, byte[] data) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.write(tmp, data);
		// Same folder, so this is a rename: readers see the old file or the new one, never half.
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String require(String value, String what) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(what + " is required");
		}
		return value.trim();
	}
}

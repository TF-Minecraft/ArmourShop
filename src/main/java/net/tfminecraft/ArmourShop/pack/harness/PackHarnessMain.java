package net.tfminecraft.ArmourShop.pack.harness;


import net.tfminecraft.ArmourShop.pack.model.BowFrames;
import net.tfminecraft.ArmourShop.pack.model.GripY;
import net.tfminecraft.ArmourShop.pack.model.PackKind;
import net.tfminecraft.ArmourShop.pack.model.PackPaths;
import net.tfminecraft.ArmourShop.pack.model.PackSubmission;
import net.tfminecraft.ArmourShop.pack.util.Model3dUtil;
import net.tfminecraft.ArmourShop.pack.util.PngUtil;
import net.tfminecraft.ArmourShop.pack.writer.armor.ArmorSetWriter;
import net.tfminecraft.ArmourShop.pack.writer.bow.BowWriter;
import net.tfminecraft.ArmourShop.pack.writer.bow.LargeBowWriter;
import net.tfminecraft.ArmourShop.pack.writer.flat.BookWriter;
import net.tfminecraft.ArmourShop.pack.writer.flat.FlatItemWriter;
import net.tfminecraft.ArmourShop.pack.writer.gun.GunWriter;
import net.tfminecraft.ArmourShop.pack.writer.large.LargeHandheldWriter;
import net.tfminecraft.ArmourShop.pack.writer.model3d.Item3dWriter;
import net.tfminecraft.ArmourShop.pack.writer.model3d.ShieldWriter;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot harness: write all MVP kinds and assert expected outputs.
 *
 * Usage: java -cp target/classes net.tfminecraft.ArmourShop.pack.harness.PackHarnessMain [contentsPath]
 */
public final class PackHarnessMain {

	private static final String DEFAULT_CONTENTS =
		"D:/Documents/TFMC/ItemsAdder Copy/ItemsAdder/contents";

	private PackHarnessMain() {}

	public static void main(String[] args) {
		try {
			Path contents = Path.of(
				args.length > 0 && !args[0].isBlank() ? args[0].trim() : DEFAULT_CONTENTS
			);
			if (!Files.isDirectory(contents)) {
				fail("Contents path is not a directory: " + contents);
			}

			writeAll(contents);
			assertAll(contents);

			System.out.println("OK — pack harness wrote and verified all MVP kinds under:");
			System.out.println("  " + PackPaths.namespaceRoot(contents));
		} catch (AssertionError e) {
			System.err.println("FAIL: " + e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	static void writeAll(Path contents) throws Exception {
		writeArmor(contents);
		writeFlat(contents, "harness_item", "Harness Item", PackKind.ITEM, new Color(0x88, 0x44, 0xcc));
		writeFlat(contents, "harness_handheld", "Harness Handheld", PackKind.HANDHELD, new Color(0xcc, 0x88, 0x22));
		writeLarge(contents, "harness_large_bottom", GripY.MIN, new Color(0x22, 0xaa, 0x66));
		writeLarge(contents, "harness_large_middle", GripY.DEFAULT, new Color(0xaa, 0xaa, 0x22));
		writeLarge(contents, "harness_large_top", GripY.MAX, new Color(0xaa, 0x44, 0x22));
		writeBow(contents, "harness_bow", "Harness Bow", PackKind.BOW, 16, new Color(0xaa, 0x66, 0x22));
		writeBow(contents, "harness_large_bow", "Harness Large Bow", PackKind.LARGE_BOW, 32, new Color(0x66, 0xaa, 0x22));
		writeBow(contents, "harness_crossbow", "Harness Crossbow", PackKind.CROSSBOW, 16, new Color(0x22, 0x66, 0xaa));
		writeModel3d(contents, "harness_item_3d", "Harness Item 3D", PackKind.ITEM_3D, new Color(0xcc, 0x44, 0x88));
		writeModel3d(contents, "harness_helmet_3d", "Harness Helmet 3D", PackKind.HELMET_3D, new Color(0x88, 0x44, 0xcc));
		writeShield(contents, "harness_shield", "Harness Shield", new Color(0x44, 0x88, 0x44));
		writeArmorHelmet3d(contents);
		writeGun(contents);
		writeBook(contents);
	}

	private static void writeArmor(Path contents) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put("helmet", PngUtil.solidPng(16, 16, new Color(0x44, 0xaa, 0xff)));
		files.put("chestplate", PngUtil.solidPng(16, 16, new Color(0x33, 0x88, 0xee)));
		files.put("leggings", PngUtil.solidPng(16, 16, new Color(0x22, 0x66, 0xcc)));
		files.put("boots", PngUtil.solidPng(16, 16, new Color(0x11, 0x44, 0xaa)));
		files.put("layer_1", PngUtil.solidPng(64, 32, new Color(0x55, 0xbb, 0xff)));
		files.put("layer_2", PngUtil.solidPng(64, 32, new Color(0x88, 0xdd, 0xff)));
		List<Path> written = ArmorSetWriter.write(
			contents,
			new PackSubmission("harness_armor", "Harness Armor", PackKind.ARMOR_SET, files)
		);
		System.out.println("Wrote ARMOR_SET (" + written.size() + " files)");
	}

	private static void writeFlat(
		Path contents,
		String slug,
		String name,
		PackKind kind,
		Color color
	) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(FlatItemWriter.TEXTURE_STEM, PngUtil.solidPng(16, 16, color));
		List<Path> written = FlatItemWriter.write(
			contents,
			new PackSubmission(slug, name, kind, files)
		);
		System.out.println("Wrote " + kind + " (" + written.size() + " files)");
	}

	private static void writeLarge(
		Path contents,
		String slug,
		double gripY,
		Color color
	) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(LargeHandheldWriter.TEXTURE_STEM, PngUtil.solidPng(32, 32, color));
		files.put(Model3dUtil.MODEL_STEM, largeHandheldModelJson(slug, gripY));
		List<Path> written = LargeHandheldWriter.write(
			contents,
			new PackSubmission(
				slug,
				"Harness Large " + GripY.format(gripY),
				PackKind.LARGE_HANDHELD,
				files
			)
		);
		System.out.println(
			"Wrote LARGE_HANDHELD grip=" + GripY.format(gripY) + " (" + written.size() + " paths)"
		);
	}

	private static void writeBow(
		Path contents,
		String slug,
		String name,
		PackKind kind,
		int size,
		Color color
	) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		String[] stems = kind == PackKind.CROSSBOW
			? BowFrames.CROSSBOW_STEMS
			: BowFrames.BOW_STEMS;
		int i = 0;
		for (String stem : stems) {
			Color c = new Color(
				Math.min(255, color.getRed() + i * 20),
				Math.min(255, color.getGreen() + i * 10),
				Math.min(255, color.getBlue() + i * 15)
			);
			files.put(stem, PngUtil.solidPng(size, size, c));
			i++;
		}
		List<Path> written;
		if (kind == PackKind.BOW) {
			written = BowWriter.write(
				contents,
				new PackSubmission(slug, name, PackKind.BOW, files)
			);
		} else if (kind == PackKind.LARGE_BOW) {
			for (String stem : BowFrames.BOW_STEMS) {
				files.put(
					LargeBowWriter.modelStemFor(stem),
					largeBowModelJson(slug, stem)
				);
			}
			written = LargeBowWriter.write(
				contents,
				new PackSubmission(slug, name, PackKind.LARGE_BOW, files)
			);
		} else {
			written = BowWriter.writeCrossbow(
				contents,
				new PackSubmission(slug, name, PackKind.CROSSBOW, files)
			);
		}
		System.out.println("Wrote " + kind + " (" + written.size() + " files)");
	}

	private static void writeModel3d(
		Path contents,
		String slug,
		String name,
		PackKind kind,
		Color color
	) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(Model3dUtil.TEXTURE_STEM, PngUtil.solidPng(16, 16, color));
		files.put(Model3dUtil.MODEL_STEM, normalizedModelJson(slug));
		List<Path> written;
		if (kind == PackKind.ITEM_3D) {
			written = Item3dWriter.write(
				contents,
				new PackSubmission(slug, name, PackKind.ITEM_3D, files)
			);
		} else {
			written = Item3dWriter.writeHelmet3d(
				contents,
				new PackSubmission(slug, name, PackKind.HELMET_3D, files)
			);
		}
		System.out.println("Wrote " + kind + " (" + written.size() + " files)");
	}

	private static void writeShield(Path contents, String slug, String name, Color color)
		throws Exception
	{
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(Model3dUtil.TEXTURE_STEM, PngUtil.solidPng(16, 16, color));
		files.put(Model3dUtil.MODEL_STEM, shieldIdleModelJson(slug));
		files.put(ShieldWriter.MODEL_BLOCKING_STEM, shieldBlockingModelJson(slug));
		List<Path> written = ShieldWriter.write(
			contents,
			new PackSubmission(slug, name, PackKind.SHIELD, files)
		);
		System.out.println("Wrote SHIELD (" + written.size() + " files)");
	}

	private static void writeArmorHelmet3d(Path contents) throws Exception {
		Map<String, byte[]> files = new LinkedHashMap<>();
		String packSlug = "harness_armor_h3d";
		files.put(
			Model3dUtil.HELMET_MODEL_STEM,
			normalizedModelJson(packSlug + "_helmet")
		);
		files.put(Model3dUtil.HELMET_TEXTURE_STEM, PngUtil.solidPng(32, 32, new Color(0xff, 0xaa, 0x22)));
		files.put("chestplate", PngUtil.solidPng(16, 16, new Color(0x33, 0x88, 0xee)));
		files.put("leggings", PngUtil.solidPng(16, 16, new Color(0x22, 0x66, 0xcc)));
		files.put("boots", PngUtil.solidPng(16, 16, new Color(0x11, 0x44, 0xaa)));
		files.put("layer_1", PngUtil.solidPng(64, 32, new Color(0x55, 0xbb, 0xff)));
		files.put("layer_2", PngUtil.solidPng(64, 32, new Color(0x88, 0xdd, 0xff)));
		List<Path> written = ArmorSetWriter.write(
			contents,
			new PackSubmission(
				packSlug,
				"Harness Armor 3D Helm",
				PackKind.ARMOR_SET,
				files
			)
		);
		System.out.println("Wrote ARMOR_SET 3D helmet (" + written.size() + " files)");
	}

	private static void writeGun(Path contents) throws Exception {
		Path harnessDir = contents.resolve("_harness_gun");
		Files.createDirectories(harnessDir);
		Path skinsYml = harnessDir.resolve("skins.yml");
		Files.writeString(skinsYml, "", StandardCharsets.UTF_8);

		String slug = "harness_gun";
		byte[] model = normalizedModelJson(slug);
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(GunWriter.TEXTURE_STEM, PngUtil.solidPng(32, 32, new Color(0x66, 0x44, 0x22)));
		files.put(GunWriter.CARRY_STEM, model);
		files.put(GunWriter.RELOAD_STEM, model);
		files.put(GunWriter.AIM_STEM, model);
		files.put(GunWriter.AIM_CHARGED_STEM, model);
		List<Path> written = GunWriter.write(
			contents,
			new PackSubmission(
				slug,
				"Harness Gun",
				PackKind.GUN,
				files
			),
			"rifles",
			skinsYml
		);
		System.out.println("Wrote GUN (" + written.size() + " files)");
	}

	private static void writeBook(Path contents) throws Exception {
		String slug = "harness_book";
		Map<String, byte[]> files = new LinkedHashMap<>();
		files.put(
			BookWriter.UNSIGNED_STEM,
			PngUtil.solidPng(16, 16, new Color(0x88, 0x55, 0x22))
		);
		files.put(
			BookWriter.SIGNED_STEM,
			PngUtil.solidPng(16, 16, new Color(0x55, 0x88, 0x33))
		);
		List<Path> written = BookWriter.write(
			contents,
			new PackSubmission(slug, "Harness Book", PackKind.BOOK, files)
		);
		System.out.println("Wrote BOOK (" + written.size() + " files)");
	}

	private static byte[] normalizedModelJson(String textureId) {
		return (
			"{\n"
				+ "  \"textures\": { \"0\": \"tfmc_submissions:item/" + textureId + "\" },\n"
				+ "  \"elements\": [],\n"
				+ "  \"display\": {\n"
				+ "    \"thirdperson_righthand\": { \"scale\": [0.5, 0.5, 0.5] },\n"
				+ "    \"gui\": { \"rotation\": [30, 225, 0] }\n"
				+ "  }\n"
				+ "}\n"
		).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] shieldIdleModelJson(String slug) {
		return (
			"{\n"
				+ "  \"textures\": { \"0\": \"tfmc_submissions:item/" + slug + "\" },\n"
				+ "  \"elements\": [],\n"
				+ "  \"overrides\": [{\n"
				+ "    \"predicate\": { \"blocking\": 1 },\n"
				+ "    \"model\": \"tfmc_submissions:item/" + slug + "_blocking\"\n"
				+ "  }],\n"
				+ "  \"display\": {\n"
				+ "    \"thirdperson_righthand\": {\n"
				+ "      \"rotation\": [0, -90, 0],\n"
				+ "      \"translation\": [2, -2, 1],\n"
				+ "      \"scale\": [1.01, 1.01, 1.01]\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n"
		).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] shieldBlockingModelJson(String slug) {
		return (
			"{\n"
				+ "  \"textures\": { \"0\": \"tfmc_submissions:item/" + slug + "\" },\n"
				+ "  \"elements\": [],\n"
				+ "  \"display\": {\n"
				+ "    \"thirdperson_righthand\": {\n"
				+ "      \"rotation\": [30, -35, 0],\n"
				+ "      \"translation\": [1, -1, -1],\n"
				+ "      \"scale\": [1.01, 1.01, 1.01]\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n"
		).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] largeHandheldModelJson(String slug, double gripY) {
		String y = GripY.format(gripY);
		return (
			"{\n"
				+ "  \"parent\": \"minecraft:item/handheld\",\n"
				+ "  \"textures\": { \"layer0\": \"tfmc_submissions:item/" + slug + "\" },\n"
				+ "  \"display\": {\n"
				+ "    \"thirdperson_righthand\": {\n"
				+ "      \"rotation\": [0, -90, 55],\n"
				+ "      \"translation\": [0, " + y + ", 0.5],\n"
				+ "      \"scale\": [1.5, 1.5, 1.5]\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n"
		).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] largeBowModelJson(String slug, String stem) {
		String tex = "tfmc_submissions:item/" + slug + BowFrames.fileSuffix(stem);
		return (
			"{\n"
				+ "  \"parent\": \"minecraft:item/bow\",\n"
				+ "  \"display\": {\n"
				+ "    \"thirdperson_righthand\": {\n"
				+ "      \"rotation\": [-80, 260, -40],\n"
				+ "      \"translation\": [-1, -2, 5.8],\n"
				+ "      \"scale\": [1.8, 1.8, 0.9]\n"
				+ "    }\n"
				+ "  },\n"
				+ "  \"textures\": { \"layer0\": \"" + tex + "\" }\n"
				+ "}\n"
		).getBytes(StandardCharsets.UTF_8);
	}

	static void assertAll(Path contents) throws Exception {
		assertArmor(contents);
		assertFlat(contents, "harness_item", "item/generated");
		assertFlat(contents, "harness_handheld", "item/handheld");
		assertLarge(contents, "harness_large_bottom");
		assertLarge(contents, "harness_large_middle");
		assertLarge(contents, "harness_large_top");
		assertBow(contents, "harness_bow", true);
		assertLargeBow(contents, "harness_large_bow");
		assertBow(contents, "harness_crossbow", true);
		assertFile(PackPaths.itemTexturesDir(contents).resolve("harness_crossbow_charged.png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve("harness_crossbow_arrow.png"));
		assertModel3d(contents, "harness_item_3d", false);
		assertModel3d(contents, "harness_helmet_3d", true);
		assertShield(contents, "harness_shield");
		assertArmorHelmet3d(contents);
		assertGun(contents);
		assertBook(contents);
	}

	private static void assertArmor(Path contents) throws Exception {
		String yaml = read(PackPaths.configsDir(contents).resolve("harness_armor.yml"));
		assertContains(yaml, "armors_rendering");
		assertContains(yaml, "generate: true");
		String slug = "harness_armor";
		assertFile(PackPaths.armorIconsDir(contents).resolve(slug + "_helmet.png"));
		assertFile(PackPaths.armorIconsDir(contents).resolve(slug + "_chestplate.png"));
		assertFile(PackPaths.armorIconsDir(contents).resolve(slug + "_leggings.png"));
		assertFile(PackPaths.armorIconsDir(contents).resolve(slug + "_boots.png"));
		assertFile(PackPaths.armorLayersDir(contents).resolve(slug + "_layer_1.png"));
		assertFile(PackPaths.armorLayersDir(contents).resolve(slug + "_layer_2.png"));
	}

	private static void assertFlat(Path contents, String slug, String parent) throws Exception {
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "generate: true");
		assertContains(yaml, "parent: " + parent);
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		Path model = PackPaths.itemModelsDir(contents).resolve(slug + ".json");
		if (Files.isRegularFile(model)) {
			fail("Expected no model JSON for flat kind: " + model);
		}
	}

	private static void assertLarge(Path contents, String slug) throws Exception {
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "generate: false");
		assertContains(yaml, "model_path: item/" + slug);
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		Path modelPath = PackPaths.itemModelsDir(contents).resolve(slug + ".json");
		assertFile(modelPath);
		String model = read(modelPath);
		assertContains(model, "minecraft:item/handheld");
		assertContains(model, "\"display\"");
		assertContains(model, "1.5");
	}

	private static void assertBow(Path contents, String slug, boolean generateTrue)
		throws Exception
	{
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		if (generateTrue) {
			assertContains(yaml, "generate: true");
		}
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_0.png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_1.png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_2.png"));
	}

	private static void assertLargeBow(Path contents, String slug) throws Exception {
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "generate: false");
		assertContains(yaml, "material: BOW");
		assertContains(yaml, "model_path: item/" + slug);
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_0.png"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + ".json"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_0.json"));
		String model = read(PackPaths.itemModelsDir(contents).resolve(slug + ".json"));
		assertContains(model, "\"display\"");
		assertContains(model, "1.8");
	}

	private static void assertModel3d(Path contents, String slug, boolean hat)
		throws Exception
	{
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "generate: false");
		assertContains(yaml, "model_path: item/" + slug);
		if (hat) {
			assertContains(yaml, "material: CARVED_PUMPKIN");
			assertContains(yaml, "hat: true");
			if (yaml.contains("slot: head")) {
				fail("3D hat must not use an armor head slot: " + slug);
			}
		} else {
			assertContains(yaml, "material: PAPER");
			if (yaml.contains("hat: true")) {
				fail("item_3d must not use hat behaviour: " + slug);
			}
		}
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		String model = read(PackPaths.itemModelsDir(contents).resolve(slug + ".json"));
		assertContains(model, "tfmc_submissions:item/" + slug);
	}

	private static void assertShield(Path contents, String slug) throws Exception {
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "material: SHIELD");
		assertContains(yaml, "generate: false");
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		String idle = read(PackPaths.itemModelsDir(contents).resolve(slug + ".json"));
		assertContains(idle, "\"blocking\"");
		assertContains(idle, slug + "_blocking");
		String blocking = read(PackPaths.itemModelsDir(contents).resolve(slug + "_blocking.json"));
		assertContains(blocking, "30");
		assertContains(blocking, "-35");
	}

	private static void assertArmorHelmet3d(Path contents) throws Exception {
		String slug = "harness_armor_h3d";
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "generate: false");
		assertContains(yaml, "material: CARVED_PUMPKIN");
		assertContains(yaml, "hat: true");
		assertContains(yaml, "model_path: item/" + slug + "_helmet");
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_helmet.png"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_helmet.json"));
		assertFile(PackPaths.armorIconsDir(contents).resolve(slug + "_chestplate.png"));
	}

	private static void assertGun(Path contents) throws Exception {
		String slug = "harness_gun";
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + ".png"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_carry.json"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_reload.json"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_aim.json"));
		assertFile(PackPaths.itemModelsDir(contents).resolve(slug + "_aim_charged.json"));
		String carry = read(PackPaths.itemModelsDir(contents).resolve(slug + "_carry.json"));
		assertContains(carry, "tfmc_submissions:item/" + slug);

		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "material: STONE_HOE");
		assertContains(yaml, "material: CROSSBOW");
		assertContains(yaml, "model_path: item/" + slug + "_carry");
		assertContains(yaml, "model_path: item/" + slug + "_reload");
		assertContains(yaml, "model_path: item/" + slug + "_aim");

		Path harnessDir = contents.resolve("_harness_gun");
		String skins = read(harnessDir.resolve("skins.yml"));
		assertContains(skins, slug + ":");
		assertContains(skins, "ia.tfmc_submissions:" + slug + "_carry");
		assertContains(skins, "ia.tfmc_submissions:" + slug + "_reload");
		assertContains(skins, "ia.tfmc_submissions:" + slug + "_aim");
		assertContains(skins, "rifle");
	}

	private static void assertBook(Path contents) throws Exception {
		String slug = "harness_book";
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_unsigned.png"));
		assertFile(PackPaths.itemTexturesDir(contents).resolve(slug + "_signed.png"));
		String yaml = read(PackPaths.configsDir(contents).resolve(slug + ".yml"));
		assertContains(yaml, "  " + slug + ":");
		assertContains(yaml, "  " + slug + "_signed:");
		assertContains(yaml, "material: WRITABLE_BOOK");
		assertContains(yaml, "material: WRITTEN_BOOK");
		assertContains(yaml, "item/" + slug + "_unsigned");
		assertContains(yaml, "item/" + slug + "_signed");
		assertContains(yaml, "generate: true");
		assertContains(yaml, "parent: item/generated");
	}

	private static String read(Path path) throws Exception {
		assertFile(path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static void assertFile(Path path) {
		if (!Files.isRegularFile(path)) {
			fail("Missing file: " + path);
		}
	}

	private static void assertContains(String haystack, String needle) {
		if (!haystack.contains(needle)) {
			fail("Expected YAML to contain: " + needle);
		}
	}

	private static void fail(String message) {
		throw new AssertionError(message);
	}
}

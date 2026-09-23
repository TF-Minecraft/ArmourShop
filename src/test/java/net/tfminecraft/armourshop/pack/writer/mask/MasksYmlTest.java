package net.tfminecraft.armourshop.pack.writer.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasksYmlTest {

	@Test
	void upsertAndRemoveKeepOtherMasks(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("custom-masks.yml");
		MasksYml.upsert(file, "oni_mask", "tfmc_submissions");
		MasksYml.upsert(file, "ghost_mask", "tfmc_armorshop");

		assertEquals(
			"ia.tfmc_submissions:oni_mask",
			MasksYml.read(file).get("oni_mask")
		);
		assertEquals(
			"ia.tfmc_armorshop:ghost_mask",
			MasksYml.read(file).get("ghost_mask")
		);

		assertTrue(MasksYml.remove(file, "oni_mask"));
		assertFalse(MasksYml.read(file).containsKey("oni_mask"));
		assertEquals(
			"ia.tfmc_armorshop:ghost_mask",
			MasksYml.read(file).get("ghost_mask")
		);
		assertFalse(MasksYml.remove(file, "oni_mask"));
	}

	@Test
	void rewriteReplacesTheSameSlug(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("custom-masks.yml");
		MasksYml.upsert(file, "oni_mask", "tfmc_submissions");
		MasksYml.upsert(file, "oni_mask", "tfmc_armorshop");
		assertEquals(1, MasksYml.read(file).size());
		assertEquals(
			"ia.tfmc_armorshop:oni_mask",
			MasksYml.read(file).get("oni_mask")
		);
		assertTrue(Files.readString(file).contains("masks:"));
	}
}

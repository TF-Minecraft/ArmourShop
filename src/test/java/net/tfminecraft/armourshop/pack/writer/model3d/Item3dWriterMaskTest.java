package net.tfminecraft.armourshop.pack.writer.model3d;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.tfminecraft.armourshop.pack.model.PackKind;
import net.tfminecraft.armourshop.pack.model.PackSubmission;

class Item3dWriterMaskTest {

	@Test
	void maskYamlIsAnUnplaceableHat() {
		String yaml = Item3dWriter.buildYaml(
			new PackSubmission("oni_mask", "Oni", PackKind.MASK, Map.of()),
			"CARVED_PUMPKIN",
			null,
			true,
			"tfmc_submissions"
		);
		assertTrue(yaml.contains("material: CARVED_PUMPKIN"));
		assertTrue(yaml.contains("generate: false"));
		assertTrue(yaml.contains("hat: true"));
		assertTrue(yaml.contains("model_path: item/oni_mask"));
	}
}

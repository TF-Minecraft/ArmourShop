package net.tfminecraft.ArmourShop.pack.writer.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.tfminecraft.ArmourShop.pack.model.PackKind;
import net.tfminecraft.ArmourShop.pack.model.PackSubmission;
import org.junit.jupiter.api.Test;

class ArmorRenderColorTest {

	@Test
	void sameSlugKeepsTheSameColor() {
		assertEquals(
			ArmorRenderColor.forSlug("player_iron"),
			ArmorRenderColor.forSlug("player_iron")
		);
	}

	@Test
	void differentSlugsGetDifferentColors() {
		assertNotEquals(
			ArmorRenderColor.forSlug("player_iron"),
			ArmorRenderColor.forSlug("player_steel")
		);
	}

	@Test
	void neverUsesWhiteOrBlack() {
		for (String slug : new String[] {"a", "b", "ffffff", "000000", "player_iron"}) {
			String color = ArmorRenderColor.forSlug(slug);
			assertTrue(color.matches("#[0-9a-f]{6}"), color);
			assertNotEquals("#ffffff", color);
			assertNotEquals("#000000", color);
		}
	}

	@Test
	void yamlUsesSlugColorInsteadOfSharedWhite() {
		String slug = "kleinnovac_june_iron";
		PackSubmission submission = new PackSubmission(
			slug,
			"June",
			PackKind.ARMOR_SET,
			Map.of()
		);
		String yaml = ArmorSetWriter.buildYaml(submission, false, "tfmc_submissions");
		String color = ArmorRenderColor.forSlug(slug);
		assertTrue(yaml.contains("color: '" + color + "'"));
		assertFalse(yaml.contains("#ffffff"));
		assertTrue(yaml.contains("use_color: false"));
	}
}

package net.tfminecraft.armourshop.pack.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.pack.reload.ApplyAckPlanner.Plan;

class ApplyAckPlannerTest {

	@TempDir
	Path contents;

	@Test
	void acksOnlyQueuedIdsWhoseConfigExists() throws Exception {
		writeConfig("tfmc_submissions", "euryonice_roslyn_s_fiddle.yml");
		Plan plan = ApplyAckPlanner.plan(
			List.of("euryonice_roslyn_s_fiddle", "mabwy_solar_flare"),
			List.of(
				item("euryonice_roslyn_s_fiddle", "item_3d"),
				item("mabwy_solar_flare", "handheld"),
				item("never_queued", "handheld")
			),
			contents
		);

		assertEquals(List.of("euryonice_roslyn_s_fiddle"), plan.ack());
		assertEquals(List.of("mabwy_solar_flare"), plan.missingFiles());
		assertTrue(plan.notApproved().isEmpty());
	}

	@Test
	void dropsIdsThatAreNoLongerApproved() throws Exception {
		writeConfig("tfmc_submissions", "still_here.yml");
		Plan plan = ApplyAckPlanner.plan(
			List.of("still_here", "revoked_one"),
			List.of(item("still_here", "handheld")),
			contents
		);

		assertEquals(List.of("still_here"), plan.ack());
		assertEquals(List.of("revoked_one"), plan.notApproved());
		assertTrue(plan.missingFiles().isEmpty());
	}

	@Test
	void armorRequiresEveryTierConfig() throws Exception {
		writeConfig("tfmc_submissions", "geo_armor_iron.yml");
		ApprovedSubmission armor = submission(
			"geo_armor",
			"geo_armor",
			"armor_set",
			List.of("iron", "steel"),
			false,
			"tfmc_submissions"
		);

		Plan missingTier = ApplyAckPlanner.plan(List.of("geo_armor"), List.of(armor), contents);
		assertTrue(missingTier.ack().isEmpty());
		assertEquals(List.of("geo_armor"), missingTier.missingFiles());

		writeConfig("tfmc_submissions", "geo_armor_steel.yml");
		Plan complete = ApplyAckPlanner.plan(List.of("geo_armor"), List.of(armor), contents);
		assertEquals(List.of("geo_armor"), complete.ack());
	}

	@Test
	void staffSkinUsesItsNamespace() throws Exception {
		writeConfig("tfmc_armorshop", "oak_wand.yml");
		ApprovedSubmission staff = submission(
			"oak_wand",
			"oak_wand",
			"handheld",
			List.of(),
			true,
			"tfmc_armorshop"
		);

		Plan plan = ApplyAckPlanner.plan(List.of("oak_wand"), List.of(staff), contents);
		assertEquals(List.of("oak_wand"), plan.ack());
	}

	@Test
	void blankContentsPathDoesNotAck() throws Exception {
		writeConfig("tfmc_submissions", "euryonice_roslyn_s_fiddle.yml");
		Plan plan = ApplyAckPlanner.plan(
			List.of("euryonice_roslyn_s_fiddle"),
			List.of(item("euryonice_roslyn_s_fiddle", "item_3d")),
			null
		);

		assertTrue(plan.ack().isEmpty());
		assertEquals(List.of("euryonice_roslyn_s_fiddle"), plan.missingFiles());
	}

	private void writeConfig(String namespace, String fileName) throws Exception {
		Path dir = contents.resolve(namespace).resolve("configs");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(fileName), "info:\n  namespace: " + namespace + "\n");
	}

	private static ApprovedSubmission item(String id, String kind) {
		return submission(id, id, kind, List.of(), false, "tfmc_submissions");
	}

	private static ApprovedSubmission submission(
		String id,
		String slug,
		String kind,
		List<String> tiers,
		boolean staff,
		String namespace
	) {
		return new ApprovedSubmission(
			id,
			null,
			slug,
			kind,
			slug,
			null,
			null,
			tiers,
			List.of(),
			Map.of(),
			false,
			List.of(),
			List.of(),
			List.of(),
			staff,
			null,
			null,
			null,
			namespace
		);
	}
}

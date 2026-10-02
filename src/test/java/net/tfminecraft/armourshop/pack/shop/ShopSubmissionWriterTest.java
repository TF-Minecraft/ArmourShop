package net.tfminecraft.armourshop.pack.shop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.pack.model.PackPaths;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class ShopSubmissionWriterTest {
    @TempDir Path temp;
    private String previousPath;
    private MockedStatic<PackPaths> paths;
    private final Logger log = Logger.getAnonymousLogger();

    @BeforeEach void setup() {
        previousPath = Cache.categoriesPath;
        Cache.categoriesPath = temp.resolve("Categories").toString();
        paths = mockStatic(PackPaths.class);
        paths.when(PackPaths::playerNamespace).thenReturn("players_dev");
    }
    @AfterEach void restore() { paths.close(); Cache.categoriesPath = previousPath; }

    static class Submission {
        String slug = "skin", kind = "handheld", display = " Skin ", base = "iron", category = "hats", scroll = "scroll", namespace;
        boolean staff, addName = true;
        List<String> tiers = List.of(), colours = List.of("red"), styles = List.of("bold");
        Map<String, String> aliases = Map.of(), scrolls = Map.of();
        ApprovedSubmission build() {
            return new ApprovedSubmission("id", "uuid", slug, kind, display, "grip", base, tiers,
                null, aliases, addName, colours, styles, null, staff, category, scroll, scrolls, namespace);
        }
    }
    private YamlConfiguration category(String name) throws Exception {
        var yaml = new YamlConfiguration(); yaml.load(Path.of(Cache.categoriesPath).resolve(name + ".yml").toFile()); return yaml;
    }
    private void write(Submission sub) throws Exception { ShopSubmissionWriter.write(sub.build(), log); }

    @Test void playerItemsCreateIndexAndReplaceStaleArmorMetadata() throws Exception {
        var sub = new Submission();
        write(sub);
        var yaml = category("ps_items");
        assertEquals("Skin", yaml.getString("skin.name")); assertEquals("iron", yaml.getString("skin.set"));
        assertEquals("armourshop.submission.skin", yaml.getString("skin.permission"));
        assertEquals("ia.players_dev:skin", yaml.getString("skin.item")); assertNull(yaml.get("skin.scroll"));
        assertEquals("red", yaml.getString("skin.colour")); assertEquals(List.of("bold"), yaml.getStringList("skin.styles"));
        assertTrue(yaml.getBoolean("skin.add-name"));
        var index = YamlConfiguration.loadConfiguration(temp.resolve("categories.yml").toFile());
        assertEquals("Player Armor", index.getString("ps_armor.name"));
        assertEquals("Player Items", index.getString("ps_items.name")); assertTrue(index.getBoolean("ps_items.is-item"));
        yaml.set("skin.helmet", "old"); yaml.set("skin.chestplate", "old"); yaml.set("skin.leggings", "old"); yaml.set("skin.boots", "old");
        yaml.save(Path.of(Cache.categoriesPath).resolve("ps_items.yml").toFile());
        sub.colours = List.of("red", "blue"); sub.styles = List.of(); sub.display = " "; sub.addName = false;
        ShopSubmissionWriter.write(sub.build(), null);
        yaml = category("ps_items");
        assertEquals(List.of("red", "blue"), yaml.getStringList("skin.colour"));
        assertEquals("skin", yaml.getString("skin.name")); assertNull(yaml.get("skin.styles"));
        assertNull(yaml.get("skin.helmet")); assertNull(yaml.get("skin.chestplate"));
        assertNull(yaml.get("skin.leggings")); assertNull(yaml.get("skin.boots")); assertFalse(yaml.getBoolean("skin.add-name"));
        sub.colours = null; write(sub); assertNull(category("ps_items").get("skin.colour"));
    }

    @Test void everyItemKindUsesAnItemEntryAndGunsUseGunskinSyntax() throws Exception {
        for (String kind : List.of("handheld", "large_handheld", "bow", "large_bow", "crossbow", "item_3d", "shield", "helmet_3d", "mask", "gun", "book")) {
            var sub = new Submission(); sub.kind = kind; sub.slug = kind; write(sub);
            assertEquals(kind.equals("gun") ? "gunskin(gun)" : "ia.players_dev:" + kind, category("ps_items").getString(kind + ".item"));
        }
    }

    @Test void playerArmorWritesEachTierAndPreservesExistingIndexSettings() throws Exception {
        Files.writeString(temp.resolve("categories.yml"), "ps_armor:\n  name: Custom Armor\nps_items:\n  name: Custom Items\nother: keep\n");
        var sub = new Submission(); sub.kind = "armor_set"; sub.tiers = List.of("iron", "steel");
        sub.aliases = Map.of("iron", "Scout"); write(sub);
        var yaml = category("ps_armor");
        assertEquals("Skin Scout", yaml.getString("skin_iron.name")); assertEquals("Skin Steel", yaml.getString("skin_steel.name"));
        for (String tier : sub.tiers) {
            assertEquals(tier, yaml.getString("skin_" + tier + ".set"));
            assertEquals("armourshop.submission.skin", yaml.getString("skin_" + tier + ".permission"));
            for (String piece : List.of("helmet", "chestplate", "leggings", "boots"))
                assertEquals("ia.players_dev:skin_" + tier + "_" + piece, yaml.getString("skin_" + tier + "." + piece));
            assertNull(yaml.get("skin_" + tier + ".item")); assertNull(yaml.get("skin_" + tier + ".scroll"));
        }
        assertEquals("Custom Armor", YamlConfiguration.loadConfiguration(temp.resolve("categories.yml").toFile()).getString("ps_armor.name"));
        sub.kind = " ARMOR_SET "; sub.tiers = List.of(); sub.base = "mythril"; ShopSubmissionWriter.write(sub.build(), null);
        assertTrue(category("ps_armor").contains("skin_mythril"));
    }

    @Test void staffArmorAndItemsUseCategoryScrollsAndNamespace() throws Exception {
        var sub = new Submission(); sub.staff = true; sub.kind = "armor_set";
        sub.tiers = List.of("iron", "steel"); sub.scrolls = Map.of("iron", "iron_scroll"); sub.namespace = " curated "; write(sub);
        var yaml = category("hats");
        assertEquals("iron_scroll", yaml.getString("skin_iron.scroll")); assertEquals("scroll", yaml.getString("skin_steel.scroll"));
        assertEquals("none", yaml.getString("skin_iron.permission"));
        assertEquals("ia.curated:skin_iron_helmet", yaml.getString("skin_iron.helmet"));
        assertFalse(Files.exists(temp.resolve("categories.yml")));
        sub.kind = "book"; sub.namespace = null; sub.display = null; sub.colours = List.of(); sub.styles = null;
        ShopSubmissionWriter.write(sub.build(), null);
        yaml = category("hats");
        assertEquals("ia.tfmc_armorshop:skin", yaml.getString("skin.item"));
        assertEquals("scroll", yaml.getString("skin.scroll")); assertEquals("skin", yaml.getString("skin.name"));
        assertEquals("none", yaml.getString("skin.permission")); assertNull(yaml.get("skin.colour")); assertNull(yaml.get("skin.styles"));
        sub.kind = "gun"; write(sub); assertEquals("gunskin(skin)", category("hats").getString("skin.item"));
        sub.kind = " ARMOR_SET "; sub.tiers = List.of(); ShopSubmissionWriter.write(sub.build(), null);
        assertEquals("iron", category("hats").getString("skin_iron.set"));
    }

    @Test void missingAndUnsupportedSubmissionsHaveUsefulErrors() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ShopSubmissionWriter.write(null, log));
        for (boolean staff : List.of(false, true)) {
            var sub = new Submission(); sub.staff = staff;
            for (String missing : Arrays.asList(null, " ")) {
                sub.slug = missing; assertThrows(IllegalStateException.class, () -> write(sub));
            }
            sub.slug = "skin"; sub.kind = "armor_set"; sub.base = null;
            assertThrows(IllegalStateException.class, () -> write(sub));
            sub.kind = "handheld"; assertThrows(IllegalStateException.class, () -> write(sub));
            sub.base = "iron"; sub.kind = "unknown"; assertThrows(IllegalStateException.class, () -> write(sub));
            sub.kind = null; assertThrows(IllegalStateException.class, () -> write(sub));
        }
        var staff = new Submission(); staff.staff = true; staff.category = null;
        assertThrows(IllegalStateException.class, () -> write(staff));
        staff.category = "hats"; staff.scroll = null;
        assertThrows(IllegalStateException.class, () -> write(staff));
        staff.kind = "armor_set"; staff.tiers = List.of("iron");
        assertThrows(IllegalStateException.class, () -> write(staff));
    }

    @Test void missingCategoriesPathIsRejectedByAllEntryPoints() {
        for (String missing : Arrays.asList(null, " ")) {
            Cache.categoriesPath = missing;
            var sub = new Submission(); assertThrows(IllegalStateException.class, () -> write(sub));
            sub.staff = true; assertThrows(IllegalStateException.class, () -> write(sub));
            assertThrows(IllegalStateException.class, () -> ShopSubmissionWriter.remove("skin", "book", null, log));
            assertThrows(IllegalStateException.class, () -> ShopSubmissionWriter.removeStaff("skin", "book", null, "hats", log));
        }
    }

    @Test void playerRemovalDeletesRequestedKeysAndLegacyArmorOnly() throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.tiers = List.of("iron", "steel"); write(sub);
        var armor = category("ps_armor"); armor.set("skin.name", "legacy"); armor.set("other.name", "keep");
        armor.save(Path.of(Cache.categoriesPath).resolve("ps_armor.yml").toFile());
        ShopSubmissionWriter.remove(" skin ", " ARMOR_SET ", Arrays.asList(null, " ", " iron ", "steel"), log);
        armor = category("ps_armor"); assertFalse(armor.contains("skin")); assertFalse(armor.contains("skin_iron")); assertFalse(armor.contains("skin_steel")); assertEquals("keep", armor.getString("other.name"));
        ShopSubmissionWriter.remove("skin", "armor_set", null, null);
        sub.kind = "book"; write(sub); ShopSubmissionWriter.remove("skin", null, null, log);
        assertFalse(category("ps_items").contains("skin"));
        ShopSubmissionWriter.remove("missing", "book", null, null);
    }

    @Test void staffRemovalUsesOwnCategoryAndLeavesUnrelatedData() throws Exception {
        var sub = new Submission(); sub.staff = true; sub.kind = "armor_set"; sub.tiers = List.of("iron"); write(sub);
        var yaml = category("hats"); yaml.set("skin.name", "legacy"); yaml.set("other.name", "keep");
        yaml.save(Path.of(Cache.categoriesPath).resolve("hats.yml").toFile());
        ShopSubmissionWriter.removeStaff(" skin ", " ARMOR_SET ", Arrays.asList(null, " ", " iron "), " hats ", log);
        yaml = category("hats"); assertFalse(yaml.contains("skin")); assertFalse(yaml.contains("skin_iron")); assertEquals("keep", yaml.getString("other.name"));
        ShopSubmissionWriter.removeStaff("skin", "armor_set", null, "hats", null);
        sub.kind = "book"; write(sub); ShopSubmissionWriter.removeStaff("skin", null, null, "hats", null);
        assertFalse(category("hats").contains("skin"));
        ShopSubmissionWriter.removeStaff("skin", "book", null, "absent", log);
        ShopSubmissionWriter.removeStaff("skin", "book", null, "absent", null);
        ShopSubmissionWriter.remove("skin", "armor_set", null, log);
        ShopSubmissionWriter.remove("skin", "book", null, log);
    }

    @Test void malformedYamlAndFilesystemFailuresAreReported() throws Exception {
        var sub = new Submission();
        Files.writeString(temp.resolve("categories.yml"), "x: [\n");
        assertThrows(IOException.class, () -> write(sub));
        Files.delete(temp.resolve("categories.yml"));
        Files.createDirectories(Path.of(Cache.categoriesPath));
        Files.writeString(Path.of(Cache.categoriesPath).resolve("ps_items.yml"), "x: [\n");
        assertThrows(IOException.class, () -> write(sub));
        assertThrows(IOException.class, () -> ShopSubmissionWriter.remove("skin", "handheld", null, log));
        Path notDirectory = temp.resolve("not-directory"); Files.writeString(notDirectory, "file");
        Cache.categoriesPath = notDirectory.toString(); assertThrows(IOException.class, () -> write(sub));
    }

    @Test void playerShopUsesTheSameExplicitNamespaceAsThePackWriter() throws Exception {
        var sub = new Submission(); sub.namespace = "legacy_player_namespace"; write(sub);
        assertEquals("ia.legacy_player_namespace:skin", category("ps_items").getString("skin.item"));
        sub.kind = "armor_set"; write(sub);
        assertEquals("ia.legacy_player_namespace:skin_iron_helmet", category("ps_armor").getString("skin_iron.helmet"));
    }

    @Test void singleComponentRelativeCategoriesDirectoryIsSupported() throws Exception {
        Path relative = Path.of("armourshop-test-" + UUID.randomUUID());
        Path index = Path.of("categories.yml");
        byte[] before = Files.exists(index) ? Files.readAllBytes(index) : null;
        Cache.categoriesPath = relative.toString();
        try {
            write(new Submission());
            assertEquals("ia.players_dev:skin", category("ps_items").getString("skin.item"));
        } finally {
            if (Files.exists(relative)) try (var children = Files.walk(relative)) {
                for (Path path : children.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
            if (before == null) Files.deleteIfExists(index); else Files.write(index, before);
        }
    }
}

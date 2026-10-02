package net.tfminecraft.armourshop.pack.apply;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.*;
import net.tfminecraft.armourshop.pack.model.PackPaths;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class PackApplyServiceTest {
    @TempDir Path temp;
    private String oldContents, oldGuns, oldMasks;
    private MockedStatic<ProvinceSystemClient> client;
    private final Logger log = Logger.getAnonymousLogger();
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jJ1sAAAAASUVORK5CYII=");
    private static final byte[] MODEL = "{\"textures\":{\"0\":\"tfmc_submissions:item/skin\"}}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void setup() {
        oldContents = Cache.iaContentsPath; oldGuns = Cache.gunsSkinsYmlPath; oldMasks = Cache.masksYmlPath;
        Cache.iaContentsPath = temp.resolve("contents").toString();
        Cache.gunsSkinsYmlPath = temp.resolve("guns/skins.yml").toString();
        Cache.masksYmlPath = temp.resolve("masks/custom.yml").toString();
        client = mockStatic(ProvinceSystemClient.class);
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile(anyString(), anyString()))
            .thenAnswer(i -> DownloadResult.success(i.<String>getArgument(1).endsWith(".json") ? MODEL : PNG));
    }
    @AfterEach void restore() {
        client.close(); Cache.iaContentsPath = oldContents; Cache.gunsSkinsYmlPath = oldGuns; Cache.masksYmlPath = oldMasks;
    }
    static class Submission {
        String id = "skin", slug = "skin", kind = "handheld", base = "iron", display = "Skin", namespace;
        List<String> files = List.of("skin.png"), tiers = List.of(), helmet3d = List.of();
        ApprovedSubmission build() {
            return new ApprovedSubmission(id, "owner", slug, kind, display, "middle", base, tiers, helmet3d,
                Map.of(), false, null, null, files, false, null, null, null, namespace);
        }
    }
    private PackApplyService.ApplySummary apply(Submission sub) {
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of(sub.build())));
        return PackApplyService.pullAndWrite(log);
    }
    private static List<String> armorFiles(String tier, boolean helmet3d) {
        List<String> names = new ArrayList<>();
        if (helmet3d) names.addAll(List.of(tier + "_helmet_model.json", tier + "_helmet_texture.png"));
        else names.add(tier + "_helmet.png");
        for (String stem : List.of("chestplate", "leggings", "boots", "layer_1", "layer_2")) names.add(tier + "_" + stem + ".png");
        return names;
    }
    private static List<String> filesFor(String kind) {
        return switch (kind) {
            case "large_handheld", "item_3d", "helmet_3d", "mask" -> List.of("skin.png", "skin.json");
            case "shield" -> List.of("skin.png", "skin.json", "skin_blocking.json");
            case "bow" -> List.of("skin.png", "skin_0.png", "skin_1.png", "skin_2.png");
            case "large_bow" -> List.of("skin.png", "skin_0.png", "skin_1.png", "skin_2.png", "skin.json", "skin_0.json", "skin_1.json", "skin_2.json");
            case "crossbow" -> List.of("skin.png", "skin_0.png", "skin_1.png", "skin_2.png", "skin_charged.png");
            case "gun" -> List.of("skin.png", "skin_carry.json", "skin_reload.json", "skin_aim.json", "skin_aim_charged.json");
            case "book" -> List.of("skin_unsigned.png", "skin_signed.png");
            default -> List.of("skin.png");
        };
    }
    private void assertFailure(Submission sub, String message) {
        var result = apply(sub); assertEquals(1, result.failed); assertEquals(0, result.written);
        assertTrue(result.writtenSubmissions.isEmpty()); assertTrue(result.messages.getFirst().contains(message), result.messages.toString());
    }

    @Test void missingConfigurationRemoteFailureAndEmptyQueueReturnSummaries() {
        for (String missing : Arrays.asList(null, " ")) {
            Cache.iaContentsPath = missing;
            assertEquals(1, PackApplyService.pullAndWrite(log).failed);
            assertEquals(1, PackApplyService.pullAndWrite(null).failed);
        }
        client.verify(() -> ProvinceSystemClient.listApproved(), never());
        Cache.iaContentsPath = temp.toString();
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.fail("offline"));
        assertEquals("list approved failed: offline", PackApplyService.pullAndWrite(log).messages.getFirst());
        assertEquals(1, PackApplyService.pullAndWrite(null).failed);
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of()));
        assertEquals(0, PackApplyService.pullAndWrite(log).written);
        assertEquals(0, PackApplyService.pullAndWrite(null).failed);
        var summary = new PackApplyService.ApplySummary(1, 2, 3, List.of("message"), null);
        assertEquals(2, summary.skipped); assertTrue(summary.writtenSubmissions.isEmpty());
        var source = new ArrayList<>(List.of(new Submission().build()));
        summary = new PackApplyService.ApplySummary(1, 0, 0, List.of(), source); source.clear();
        assertEquals(1, summary.writtenSubmissions.size());
    }

    @Test void everyNonArmorKindWritesRealPackFilesAndRegistries() throws Exception {
        for (String kind : List.of("handheld", "large_handheld", "bow", "large_bow", "crossbow", "item_3d", "shield", "helmet_3d", "mask", "gun", "book")) {
            var sub = new Submission(); sub.kind = kind; sub.files = filesFor(kind); sub.namespace = "curated";
            if (kind.equals("gun")) sub.base = "rifles";
            if (kind.equals("handheld")) sub.display = null;
            var result = apply(sub);
            assertEquals(1, result.written, kind + " " + result.messages); assertEquals(0, result.failed);
            assertEquals(1, result.writtenSubmissions.size()); assertEquals(sub.kind, result.writtenSubmissions.getFirst().kind);
            Path config = Path.of(Cache.iaContentsPath).resolve("curated/configs/skin.yml");
            assertTrue(Files.isRegularFile(config), kind); assertTrue(Files.readString(config).contains("namespace: curated"));
            if (kind.equals("large_handheld")) {
                String model = Files.readString(Path.of(Cache.iaContentsPath).resolve("curated/resourcepack/assets/curated/models/item/skin.json"));
                assertTrue(model.contains("curated:item/skin")); assertFalse(model.contains("tfmc_submissions:"));
            }
        }
        assertTrue(Files.readString(Path.of(Cache.gunsSkinsYmlPath)).contains("ia.curated:skin_carry"));
        assertTrue(Files.readString(Path.of(Cache.masksYmlPath)).contains("skin"));
    }

    @Test void mixedBatchContinuesAfterFailureAndDoesNotDownloadUnknownExtras() {
        var good = new Submission(); good.display = " "; good.files = Arrays.asList(null, "ignored.txt", "skin.png");
        var bad = new Submission(); bad.kind = null;
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of(bad.build(), good.build())));
        var summary = PackApplyService.pullAndWrite(null);
        assertEquals(1, summary.failed); assertEquals(1, summary.written); assertEquals(2, summary.messages.size());
        client.verify(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "ignored.txt"), never());
        client.verify(() -> ProvinceSystemClient.downloadSubmissionFile("skin", (String) null), never());
    }

    @Test void flatAndThreeDimensionalArmorWriteEveryTier() throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.tiers = List.of("iron", "steel"); sub.helmet3d = List.of("steel"); sub.namespace = "curated";
        var files = armorFiles("iron", false); files.addAll(armorFiles("steel", true)); sub.files = files;
        var summary = apply(sub); assertEquals(1, summary.written, summary.messages.toString());
        Path root = Path.of(Cache.iaContentsPath).resolve("curated");
        assertTrue(Files.exists(root.resolve("configs/skin_iron.yml"))); assertTrue(Files.exists(root.resolve("configs/skin_steel.yml")));
        assertArrayEquals(PNG, Files.readAllBytes(root.resolve("resourcepack/assets/curated/textures/armor_icons/skin_iron_helmet.png")));
        assertTrue(Files.readString(root.resolve("resourcepack/assets/curated/models/item/skin_steel_helmet.json")).contains("curated:item/skin"));
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of(sub.build())));
        assertEquals(1, PackApplyService.pullAndWrite(null).written);
    }

    @Test void armorPackIdentifiersMustMatchShopSlugWhenSubmissionIdDiffers() throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.id = "7d294886-978e-42b1-903a-6207e87d9548";
        sub.files = armorFiles("iron", false);
        var result = apply(sub);
        assertEquals(1, result.written, result.messages.toString());
        Path root = Path.of(Cache.iaContentsPath).resolve(PackPaths.playerNamespace());
        assertTrue(Files.isRegularFile(root.resolve("configs/skin_iron.yml")));
        assertArrayEquals(PNG, Files.readAllBytes(root.resolve("resourcepack/assets/" + PackPaths.playerNamespace() + "/textures/armor_icons/skin_iron_helmet.png")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tfmc_submissions", "curated", "tfmc_submissions_dev"})
    void legacyArmorModelsReferenceInstalledSlugTexturesAndPreserveOtherJson(String namespace) throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.id = "7d294886-978e-42b1-903a-6207e87d9548";
        sub.namespace = namespace; sub.tiers = List.of("iron", "steel"); sub.helmet3d = sub.tiers;
        sub.files = armorFiles("iron", true); sub.files.addAll(armorFiles("steel", true));
        for (String tier : sub.tiers) {
            byte[] downloaded = downloadedArmorModel(sub.id, tier);
            client.when(() -> ProvinceSystemClient.downloadSubmissionFile(sub.id, tier + "_helmet_model.json"))
                .thenReturn(DownloadResult.success(downloaded));
        }
        var summary = apply(sub); assertEquals(1, summary.written, summary.messages.toString()); assertEquals(0, summary.failed);
        Path assets = Path.of(Cache.iaContentsPath).resolve(namespace + "/resourcepack/assets/" + namespace);
        for (String tier : sub.tiers) {
            JsonObject actual = JsonParser.parseString(Files.readString(assets.resolve("models/item/skin_" + tier + "_helmet.json"))).getAsJsonObject();
            JsonObject expected = JsonParser.parseString(new String(downloadedArmorModel(sub.id, tier), StandardCharsets.UTF_8)).getAsJsonObject();
            expected.getAsJsonObject("textures").addProperty("0", namespace + ":item/skin_" + tier + "_helmet");
            expected.getAsJsonObject("textures").addProperty("particle", namespace + ":item/skin_" + tier + "_helmet");
            assertEquals(expected, actual, "Only this tier's generated helmet texture references should change");
            assertArrayEquals(PNG, Files.readAllBytes(assets.resolve("textures/item/skin_" + tier + "_helmet.png")));
            assertFalse(Files.exists(assets.resolve("textures/item/" + sub.id + "_" + tier + "_helmet.png")));
        }
    }

    @Test void matchingArmorIdsKeepDownloadedModelsByteForByte() throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.tiers = List.of("iron"); sub.helmet3d = sub.tiers;
        sub.namespace = "tfmc_submissions"; sub.files = armorFiles("iron", true);
        byte[] model = downloadedArmorModel(sub.id, "iron");
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile(sub.id, "iron_helmet_model.json")).thenReturn(DownloadResult.success(model));
        assertEquals(1, apply(sub).written);
        Path installed = Path.of(Cache.iaContentsPath).resolve("tfmc_submissions/resourcepack/assets/tfmc_submissions/models/item/skin_iron_helmet.json");
        assertArrayEquals(model, Files.readAllBytes(installed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"textures\": []}", "{\"textures\": {\"0\": \"tfmc_submissions:item/skin_iron_helmet\", \"alias\": \"#0\"}}"})
    void legacyArmorModelsWithoutTheOldTextureReferenceArePreserved(String json) throws Exception {
        var sub = new Submission(); sub.kind = "armor_set"; sub.id = "legacy-id"; sub.tiers = List.of("iron"); sub.helmet3d = sub.tiers;
        sub.namespace = "tfmc_submissions"; sub.files = armorFiles("iron", true);
        byte[] model = json.getBytes(StandardCharsets.UTF_8);
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile(sub.id, "iron_helmet_model.json")).thenReturn(DownloadResult.success(model));
        assertEquals(1, apply(sub).written);
        Path installed = Path.of(Cache.iaContentsPath).resolve("tfmc_submissions/resourcepack/assets/tfmc_submissions/models/item/skin_iron_helmet.json");
        assertArrayEquals(model, Files.readAllBytes(installed));
    }

    private static byte[] downloadedArmorModel(String submissionId, String tier) {
        // ProvinceSystem storage.py and pack_models/regen.py normalize generated helmet refs using the submission ID.
        String textureId = submissionId + "_" + tier + "_helmet";
        return ("""
            {
              "parent": "minecraft:item/generated",
              "credit": "legacy texture %s",
              "textures": {
                "0": "tfmc_submissions:item/%s",
                "particle": "tfmc_submissions:item/%s",
                "external": "other_namespace:item/%s",
                "alias": "#0",
                "metadata": 5,
                "unused": null,
                "extension": {"enabled": true}
              },
              "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {"north": {"texture": "#0"}}}],
              "display": {"head": {"rotation": [0, 180, 0]}}
            }
            """).formatted(textureId, textureId, textureId, textureId).getBytes(StandardCharsets.UTF_8);
    }

    @Test void realmScopedPlayerPacksRewriteWebModelTextures() throws Exception {
        try (var paths = mockStatic(PackPaths.class, CALLS_REAL_METHODS)) {
            paths.when(PackPaths::playerNamespace).thenReturn("tfmc_submissions_dev");
            var sub = new Submission(); sub.kind = "item_3d"; sub.files = filesFor(sub.kind);
            var result = apply(sub); assertEquals(1, result.written, result.messages.toString());
            Path model = Path.of(Cache.iaContentsPath).resolve("tfmc_submissions_dev/resourcepack/assets/tfmc_submissions_dev/models/item/skin.json");
            assertTrue(Files.readString(model).contains("tfmc_submissions_dev:item/skin"));
            assertFalse(Files.readString(model).contains("tfmc_submissions:item/skin"));
        }
    }

    @Test void armorDownloadsRejectMissingAndFailedFilesBeforeWriting() {
        var sub = new Submission(); sub.kind = "armor_set"; sub.tiers = List.of("iron");
        sub.files = List.of(); assertFailure(sub, "no files listed");
        sub.files = List.of("other.png"); assertFailure(sub, "missing armor file: iron_helmet.png");
        sub.files = List.of("iron_helmet.png"); assertFailure(sub, "missing armor file: iron_chestplate.png");
        sub.files = armorFiles("iron", false);
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_helmet.png")).thenReturn(DownloadResult.fail("offline"));
        assertFailure(sub, "download iron_helmet.png: offline");
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_helmet.png")).thenReturn(DownloadResult.success(PNG));
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_boots.png")).thenReturn(DownloadResult.fail("offline"));
        assertFailure(sub, "download iron_boots.png: offline");
        sub.helmet3d = List.of("iron"); sub.files = List.of("other.png");
        assertFailure(sub, "missing armor file: iron_helmet_model.json");
        sub.files = List.of("iron_helmet_model.json"); assertFailure(sub, "missing armor file: iron_helmet_texture.png");
        sub.files = armorFiles("iron", true);
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_helmet_model.json")).thenReturn(DownloadResult.fail("offline"));
        assertFailure(sub, "download iron_helmet_model.json: offline");
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_helmet_model.json")).thenReturn(DownloadResult.success(MODEL));
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "iron_helmet_texture.png")).thenReturn(DownloadResult.fail("offline"));
        assertFailure(sub, "download iron_helmet_texture.png: offline");
    }

    @Test void nonArmorFilesAndRequiredStemsAreValidated() {
        var sub = new Submission(); sub.files = List.of(); assertFailure(sub, "no files listed");
        sub.slug = null; sub.files = List.of("other.png"); assertFailure(sub, "no recognized files"); sub.slug = "skin";
        sub.files = List.of("skin.png");
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "skin.png")).thenReturn(DownloadResult.fail("offline"));
        assertFailure(sub, "download skin.png: offline");
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile("skin", "skin.png")).thenReturn(DownloadResult.success(PNG));
        sub.files = List.of("skin.json"); assertFailure(sub, "missing texture");
        sub.kind = "large_handheld"; assertFailure(sub, "missing texture");
        sub.files = List.of("skin.png"); assertFailure(sub, "missing model");
        sub.kind = "bow"; assertFailure(sub, "missing stem: pull_0");
        sub.kind = "large_bow"; sub.files = filesFor("bow"); assertFailure(sub, "missing stem: model");
        sub.kind = "item_3d"; sub.files = List.of("skin.json"); assertFailure(sub, "missing texture");
        sub.files = List.of("skin.png"); assertFailure(sub, "missing model");
        sub.kind = "shield"; sub.files = filesFor("item_3d"); assertFailure(sub, "missing model_blocking");
        sub.kind = "gun"; sub.files = List.of("skin_carry.json"); assertFailure(sub, "missing texture");
        sub.files = List.of("skin.png"); assertFailure(sub, "missing stem: carry");
        sub.files = List.of("skin.png", "skin_carry.json", "skin_reload.json", "skin_aim.json"); assertFailure(sub, "missing stem: aim_charged");
        sub.kind = "book"; sub.files = List.of("skin_signed.png"); assertFailure(sub, "missing unsigned");
        sub.files = List.of("skin_unsigned.png"); assertFailure(sub, "missing signed");
        sub.kind = "unknown"; sub.files = List.of("skin.png"); assertFailure(sub, "unsupported kind: unknown");
    }

    @Test void tierFallbacksAndOptionalRegistryPathsAreValidated() {
        var sub = new Submission(); sub.kind = " ARMOR_SET ";
        assertEquals(List.of("iron"), PackApplyService.resolveTiers(sub.build()));
        sub.tiers = List.of("steel"); assertEquals(List.of("steel"), PackApplyService.resolveTiers(sub.build()));
        sub.tiers = List.of(); sub.base = null;
        assertThrows(IllegalStateException.class, () -> PackApplyService.resolveTiers(sub.build()));
        sub.base = " "; assertThrows(IllegalStateException.class, () -> PackApplyService.resolveTiers(sub.build()));
        for (String missing : Arrays.asList(null, " ")) {
            Cache.masksYmlPath = missing; assertThrows(IllegalStateException.class, PackApplyService::requireMasksYml);
            Cache.gunsSkinsYmlPath = missing; assertThrows(IllegalStateException.class, PackApplyService::requireGunsSkinsYml);
        }
        Cache.masksYmlPath = " file.yml "; assertEquals(Path.of("file.yml"), PackApplyService.requireMasksYml());
        Cache.gunsSkinsYmlPath = " guns.yml "; assertEquals(Path.of("guns.yml"), PackApplyService.requireGunsSkinsYml());
    }

    @Test void filenamesMapOnlySupportedFramesAndModelStems() {
        assertNull(PackApplyService.stemFromFilename("book", "skin", null));
        assertEquals("unsigned", PackApplyService.stemFromFilename("book", "skin", " skin_unsigned.png "));
        assertEquals("signed", PackApplyService.stemFromFilename("book", "skin", "skin_signed.png"));
        assertNull(PackApplyService.stemFromFilename("book", "skin", "skin_other.png"));
        assertNull(PackApplyService.stemFromFilename("book", "skin", "other_unsigned.png"));
        for (String kind : List.of("bow", "large_bow", "crossbow")) {
            assertEquals("texture", PackApplyService.stemFromFilename(kind, "skin", "skin.png"));
            for (String frame : List.of("0", "1", "2")) assertEquals("pull_" + frame, PackApplyService.stemFromFilename(kind, "skin", "skin_" + frame + ".png"));
            assertEquals("charged", PackApplyService.stemFromFilename(kind, "skin", "skin_arrow.png"));
            assertEquals("charged", PackApplyService.stemFromFilename(kind, "skin", "skin_charged.png"));
            assertNull(PackApplyService.stemFromFilename(kind, "skin", "skin_other.png"));
            assertNull(PackApplyService.stemFromFilename(kind, "skin", "other.png"));
            assertNull(PackApplyService.stemFromFilename(kind, "skin", "skin_other.txt"));
        }
        assertEquals("model", PackApplyService.stemFromFilename("large_bow", "skin", "skin.json"));
        for (String frame : List.of("0", "1", "2")) assertEquals("model_" + frame, PackApplyService.stemFromFilename("large_bow", "skin", "skin_" + frame + ".json"));
        assertNull(PackApplyService.stemFromFilename("large_bow", "skin", "skin_3.json"));
        assertNull(PackApplyService.stemFromFilename("large_bow", "skin", "other.json"));
        for (String frame : List.of("carry", "reload", "aim", "aim_charged")) assertEquals(frame, PackApplyService.stemFromFilename("gun", "skin", "skin_" + frame + ".json"));
        assertNull(PackApplyService.stemFromFilename("gun", "skin", "skin_other.json"));
        assertNull(PackApplyService.stemFromFilename("gun", "skin", "other.txt"));
        assertEquals("model_blocking", PackApplyService.stemFromFilename("shield", "skin", "skin_blocking.json"));
        assertEquals("model", PackApplyService.stemFromFilename("item_3d", "skin", "skin.json"));
        assertEquals("texture", PackApplyService.stemFromFilename("item_3d", "skin", "skin.png"));
        assertNull(PackApplyService.stemFromFilename("handheld", "skin", "other.txt"));
    }

    @Test void namespaceRewritesOnlyModelBytesAndPreservesNoopInputs() {
        assertNull(PackApplyService.rewriteModelFiles(null, "custom"));
        var empty = Map.<String, byte[]>of(); assertSame(empty, PackApplyService.rewriteModelFiles(empty, "custom"));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String stem : Arrays.asList(null, "texture", "model", "helmet_model", "model_blocking", "model_2", "carry", "reload", "aim", "aim_charged")) files.put(stem, MODEL);
        files.put("no_data", null);
        for (String ns : Arrays.asList(null, " ", PackPaths.playerNamespace())) assertSame(files, PackApplyService.rewriteModelFiles(files, ns));
        var result = PackApplyService.rewriteModelFiles(files, " custom ");
        assertSame(MODEL, result.get(null)); assertSame(MODEL, result.get("texture")); assertNull(result.get("no_data"));
        for (String stem : List.of("model", "helmet_model", "model_blocking", "model_2", "carry", "reload", "aim", "aim_charged"))
            assertTrue(new String(result.get(stem), StandardCharsets.UTF_8).contains("custom:item/skin"), stem);
        assertTrue(new String(files.get("model"), StandardCharsets.UTF_8).contains("tfmc_submissions:"));
    }
}

package net.tfminecraft.armourshop.pack.apply;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.DownloadResult;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ListResult;
import net.tfminecraft.armourshop.pack.delete.PackSubmissionRemover;
import net.tfminecraft.armourshop.pack.shop.ShopSubmissionWriter;
import net.tfminecraft.armourshop.pack.util.PngUtil;
import net.tfminecraft.armourshop.pack.writer.vehicle.VehicleSkinWriter;

class VehiclePackApplyTest {
    @TempDir Path temp;
    private MockedStatic<ProvinceSystemClient> client;
    private final Logger log = Logger.getAnonymousLogger();
    private String oldContents, oldBlueprints, oldVehicles, oldSkins, oldCategories;
    private final Map<String, byte[]> downloads = new HashMap<>();
    private Path blueprints, skinsYml;

    @BeforeEach void setup() throws Exception {
        oldContents = Cache.iaContentsPath; oldBlueprints = Cache.modelEngineBlueprintsPath; oldVehicles = Cache.vfVehiclesPath;
        oldSkins = Cache.vfPlayerSkinsYmlPath; oldCategories = Cache.categoriesPath;
        blueprints = Files.createDirectories(temp.resolve("ModelEngine/blueprints"));
        Path vehicles = Files.createDirectories(temp.resolve("VehicleFramework/vehicles"));
        skinsYml = temp.resolve("VehicleFramework/player-skins.yml");
        Cache.iaContentsPath = temp.resolve("contents").toString();
        Cache.categoriesPath = temp.resolve("ArmourShop/Categories").toString();
        Cache.modelEngineBlueprintsPath = blueprints.toString(); Cache.vfVehiclesPath = vehicles.toString();
        Cache.vfPlayerSkinsYmlPath = skinsYml.toString();
        Files.writeString(vehicles.resolve("karve.yml"), "karve:\n  model: karve\n  skins:\n    karve: {model: karve}\n");
        byte[] hull = PngUtil.solidPng(4, 4, Color.RED), sail = PngUtil.solidPng(2, 2, Color.WHITE);
        Files.writeString(blueprints.resolve("karve.bbmodel"), "{\"textures\":["
            + "{\"name\":\"karve.png\",\"source\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(hull) + "\"},"
            + "{\"name\":\"sail.png\",\"source\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(sail) + "\"}]}");
        downloads.put("ship_0.png", PngUtil.solidPng(4, 4, Color.BLUE));
        downloads.put("ship_1.png", PngUtil.solidPng(2, 2, Color.GREEN));
        client = mockStatic(ProvinceSystemClient.class);
        client.when(() -> ProvinceSystemClient.downloadSubmissionFile(anyString(), anyString()))
            .thenAnswer(i -> DownloadResult.success(downloads.get(i.<String>getArgument(1))));
    }

    @AfterEach void restore() {
        client.close();
        Cache.iaContentsPath = oldContents; Cache.modelEngineBlueprintsPath = oldBlueprints; Cache.vfVehiclesPath = oldVehicles;
        Cache.vfPlayerSkinsYmlPath = oldSkins; Cache.categoriesPath = oldCategories;
    }

    private static ApprovedSubmission vehicle(List<String> files) {
        return new ApprovedSubmission("id1", "owner", "ship", "vehicle", "Sea Wolf", null, "karve", List.of(), List.of(),
            Map.of(), false, null, null, files, false, null, null, null, null);
    }

    private PackApplyService.ApplySummary apply(ApprovedSubmission sub) {
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of(sub)));
        return PackApplyService.pullAndWrite(log);
    }

    @Test void approvedVehicleSkinBecomesAModelCopyAndAPlayerSkinEntry() throws Exception {
        var result = apply(vehicle(List.of("ship_1.png", "ship_0.png", "ship_preview.png", "ship.png")));
        assertEquals(1, result.written, result.messages.toString());
        assertTrue(Files.isRegularFile(VehicleSkinWriter.blueprintPath(blueprints, "ship")));
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(skinsYml.toFile());
        assertEquals("karve", yml.getString("vskin_ship.vehicle"));
        assertEquals("Sea Wolf", yml.getString("vskin_ship.name"));
        assertEquals("armourshop.submission.ship", yml.getString("vskin_ship.permission"));
        client.verify(() -> ProvinceSystemClient.downloadSubmissionFile("id1", "ship_preview.png"), never());
        // No shop entry: vehicle skins are picked with Vehicle Paint.
        ShopSubmissionWriter.write(vehicle(List.of()), log);
        assertFalse(Files.exists(temp.resolve("ArmourShop/Categories/ps_items.yml")));
        assertTrue(ShopSubmissionWriter.isVehicle(vehicle(List.of())));
        assertFalse(ShopSubmissionWriter.isVehicle(null));
        assertEquals("armourshop.submission.ship", ShopSubmissionWriter.permissionFor("ship"));
        // Deleting the submission removes both again.
        assertEquals(List.of(VehicleSkinWriter.blueprintPath(blueprints, "ship")),
            PackSubmissionRemover.remove(temp.resolve("contents"), "vehicle", "ship", List.of(), log));
        assertFalse(YamlConfiguration.loadConfiguration(skinsYml.toFile()).contains("vskin_ship"));
        assertEquals(List.of(), PackSubmissionRemover.remove(temp.resolve("contents"), "vehicle", "ship", List.of(), log));
    }

    @Test void gapsAndWrongSizesFailWithoutWriting() {
        var gap = apply(vehicle(List.of("ship_1.png")));
        assertEquals(1, gap.failed); assertTrue(gap.messages.getFirst().contains("numbered 0..0 without gaps"), gap.messages.toString());
        downloads.put("ship_1.png", downloads.get("ship_0.png"));
        var size = apply(vehicle(List.of("ship_0.png", "ship_1.png")));
        assertEquals(1, size.failed); assertTrue(size.messages.getFirst().contains("texture 1 must be 2x2"), size.messages.toString());
        assertFalse(Files.exists(VehicleSkinWriter.blueprintPath(blueprints, "ship")));
    }

    @Test void textureStemsOnlyAcceptNumberedPngs() {
        assertEquals("vehicle_texture_0", PackApplyService.stemFromFilename("vehicle", "ship", "ship_0.png"));
        assertEquals("vehicle_texture_12", PackApplyService.stemFromFilename("vehicle", "ship", "ship_012.png"));
        for (String name : List.of("ship.png", "ship_.png", "ship_a.png", "ship_1234.png", "ship_0.json", "other_0.png", "ship_0.png.mcmeta")) {
            assertNull(PackApplyService.stemFromFilename("vehicle", "ship", name), name);
        }
        assertThrows(IllegalStateException.class, () -> PackApplyService.vehicleTextures(Map.of()));
        assertEquals(2, PackApplyService.vehicleTextures(Map.of("vehicle_texture_0", new byte[1], "vehicle_texture_1", new byte[1], "other", new byte[1])).size());
    }
}

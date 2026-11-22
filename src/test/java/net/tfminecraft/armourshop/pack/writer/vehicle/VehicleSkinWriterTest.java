package net.tfminecraft.armourshop.pack.writer.vehicle;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.tfminecraft.armourshop.pack.util.PngUtil;

class VehicleSkinWriterTest {
    @TempDir Path temp;
    private Path blueprints, vehicles, skinsYml;
    private byte[] hull4, sail2, other4, other2;

    @BeforeEach void setup() throws Exception {
        blueprints = Files.createDirectories(temp.resolve("ModelEngine/blueprints"));
        vehicles = Files.createDirectories(temp.resolve("VehicleFramework/vehicles"));
        skinsYml = temp.resolve("VehicleFramework/player-skins.yml");
        hull4 = PngUtil.solidPng(4, 4, Color.RED); sail2 = PngUtil.solidPng(2, 2, Color.WHITE);
        other4 = PngUtil.solidPng(4, 4, Color.BLUE); other2 = PngUtil.solidPng(2, 2, Color.GREEN);
        Files.writeString(vehicles.resolve("karve.yml"),
            "karve:\n  model: karve\n  skins:\n    karve: {name: Karve, model: karve_model}\n");
        Files.writeString(vehicles.resolve("notes.txt"), "not yaml");
        Files.writeString(vehicles.resolve("broken.yml"), "broken: [unterminated\n");
        Files.createDirectories(vehicles.resolve("nested"));
        Files.createDirectories(blueprints.resolve("ships"));
        Files.writeString(blueprints.resolve("ships/Karve_Model.bbmodel"), baseModel());
    }

    private String baseModel() {
        return "{\"name\":\"karve_model\",\"model_identifier\":\"karve_model\",\"elements\":[{\"faces\":{\"north\":{\"texture\":0}}}],"
            + "\"textures\":[" + texture("karve", hull4) + "," + texture("sail", sail2) + "]}";
    }

    private static String texture(String name, byte[] png) {
        return "{\"name\":\"" + name + ".png\",\"path\":\"C:/Users/someone/" + name + ".png\",\"relative_path\":\"../" + name
            + ".png\",\"source\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(png) + "\"}";
    }

    private static final java.util.UUID OWNER = java.util.UUID.fromString("b58b582f-c305-3ce8-8e43-02a2a84b297d");

    private void write(List<byte[]> textures) throws Exception {
        VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "drumleaf_sunrise", "Sunrise", OWNER, textures);
    }

    @Test void writesAReskinnedCopyAndAnOwnerOnlyEntry() throws Exception {
        long before = VehicleSkinWriter.generation();
        write(List.of(other4, other2));
        assertTrue(VehicleSkinWriter.generation() > before);
        Path out = VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise");
        assertEquals(blueprints.resolve("tfmc_submissions/vskin_drumleaf_sunrise.bbmodel"), out);
        JsonObject model = JsonParser.parseString(Files.readString(out)).getAsJsonObject();
        assertEquals("vskin_drumleaf_sunrise", model.get("name").getAsString());
        assertEquals("vskin_drumleaf_sunrise", model.get("model_identifier").getAsString());
        assertEquals(1, model.getAsJsonArray("elements").size(), "geometry is kept as is");
        JsonArray textures = model.getAsJsonArray("textures");
        for (int i = 0; i < 2; i++) {
            JsonObject t = textures.get(i).getAsJsonObject();
            assertEquals("vskin_drumleaf_sunrise_" + i + ".png", t.get("name").getAsString());
            assertFalse(t.has("path")); assertFalse(t.has("relative_path"));
            String source = t.get("source").getAsString();
            assertArrayEquals(i == 0 ? other4 : other2, Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1)));
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(skinsYml.toFile());
        assertEquals("karve", yml.getString("vskin_drumleaf_sunrise.vehicle"));
        assertEquals("Sunrise", yml.getString("vskin_drumleaf_sunrise.name"));
        assertEquals("vskin_drumleaf_sunrise", yml.getString("vskin_drumleaf_sunrise.model"));
        assertEquals(OWNER.toString(), yml.getString("vskin_drumleaf_sunrise.owner"));
        assertFalse(yml.contains("vskin_drumleaf_sunrise.permission"));
        assertTrue(Files.readString(skinsYml).startsWith("# Website vehicle skins"));
        // A second skin keeps the first; a rewrite of the same skin replaces it.
        VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "other_skin", " ", OWNER, List.of(hull4, sail2));
        VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "other_skin", null, OWNER, List.of(hull4, sail2));
        yml = YamlConfiguration.loadConfiguration(skinsYml.toFile());
        assertEquals("vskin_other_skin", yml.getString("vskin_other_skin.name"));
        assertTrue(yml.contains("vskin_drumleaf_sunrise"));
    }

    @Test void removeDeletesTheCopyAndTheEntry() throws Exception {
        write(List.of(other4, other2));
        long before = VehicleSkinWriter.generation();
        assertTrue(VehicleSkinWriter.remove(blueprints, skinsYml, "drumleaf_sunrise"));
        long afterRemove = VehicleSkinWriter.generation();
        assertTrue(afterRemove > before);
        assertFalse(Files.exists(VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise")));
        assertFalse(YamlConfiguration.loadConfiguration(skinsYml.toFile()).contains("vskin_drumleaf_sunrise"));
        assertFalse(VehicleSkinWriter.remove(blueprints, skinsYml, "drumleaf_sunrise"));
        assertEquals(afterRemove, VehicleSkinWriter.generation(), "nothing removed, nothing to reload");
        assertFalse(VehicleSkinWriter.remove(blueprints, null, "drumleaf_sunrise"));
        // Only the entry left (copy deleted by hand): still a change.
        write(List.of(other4, other2));
        Files.delete(VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise"));
        assertTrue(VehicleSkinWriter.remove(blueprints, skinsYml, "drumleaf_sunrise"));
        Files.writeString(skinsYml, "broken: [unterminated\n");
        assertThrows(java.io.IOException.class, () -> VehicleSkinWriter.remove(blueprints, skinsYml, "drumleaf_sunrise"));
        // A model changed before a broken registry stopped the edit still counts as a change.
        long beforeBroken = VehicleSkinWriter.generation();
        assertThrows(java.io.IOException.class, () -> write(List.of(other4, other2)));
        assertTrue(Files.exists(VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise")));
        assertEquals(beforeBroken + 1, VehicleSkinWriter.generation());
        assertThrows(java.io.IOException.class, () -> VehicleSkinWriter.remove(blueprints, skinsYml, "drumleaf_sunrise"));
        assertFalse(Files.exists(VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise")));
        assertEquals(beforeBroken + 2, VehicleSkinWriter.generation());
        assertThrows(IllegalArgumentException.class, () -> VehicleSkinWriter.remove(blueprints, skinsYml, "Bad-Slug"));
        assertThrows(IllegalArgumentException.class, () -> VehicleSkinWriter.remove(blueprints, skinsYml, " "));
    }

    @Test void uploadsMustMatchEveryBaseTextureExactly() {
        IllegalStateException count = assertThrows(IllegalStateException.class, () -> write(List.of(other4)));
        assertEquals("vehicle has 2 texture(s), upload has 1", count.getMessage());
        assertEquals("vehicle has 2 texture(s), upload has 0", assertThrows(IllegalStateException.class, () -> write(null)).getMessage());
        IllegalStateException size = assertThrows(IllegalStateException.class, () -> write(List.of(other4, other4)));
        assertEquals("texture 1 must be 2x2, upload is 4x4", size.getMessage());
        IllegalStateException width = assertThrows(IllegalStateException.class, () -> write(List.of(sail2, other2)));
        assertEquals("texture 0 must be 4x4, upload is 2x2", width.getMessage());
        assertEquals("texture 0 is not a readable PNG",
            assertThrows(IllegalStateException.class, () -> write(List.of("nope".getBytes(StandardCharsets.UTF_8), other2))).getMessage());
        byte[] truncated = java.util.Arrays.copyOf(other4, 40);
        assertEquals("texture 0 is not a readable PNG",
            assertThrows(IllegalStateException.class, () -> write(List.of(truncated, other2))).getMessage());
        java.util.List<byte[]> withNull = new java.util.ArrayList<>(); withNull.add(null); withNull.add(other2);
        assertEquals("texture 0 is not a readable PNG", assertThrows(IllegalStateException.class, () -> write(withNull)).getMessage());
        assertFalse(Files.exists(VehicleSkinWriter.blueprintPath(blueprints, "drumleaf_sunrise")), "nothing is written on a bad upload");
        assertFalse(Files.exists(skinsYml));
    }

    @Test void baseModelProblemsAreReported() {
        String noTextures = "{\"name\":\"x\"}";
        assertEquals("base model has no textures", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.reskin(noTextures.getBytes(StandardCharsets.UTF_8), "vskin_x", List.of())).getMessage());
        assertEquals("base model has no textures", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.reskin("{\"textures\":{}}".getBytes(StandardCharsets.UTF_8), "vskin_x", List.of())).getMessage());
        assertEquals("base model has no textures", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.reskin("{\"textures\":[]}".getBytes(StandardCharsets.UTF_8), "vskin_x", List.of())).getMessage());
        for (String source : List.of("{}", "{\"source\":{}}", "{\"source\":\"data:image/png;base64\"}", "{\"source\":\"http://x/y.png\"}")) {
            byte[] model = ("{\"textures\":[" + source + "]}").getBytes(StandardCharsets.UTF_8);
            assertEquals("base texture 0 is not an embedded PNG", assertThrows(IllegalStateException.class,
                () -> VehicleSkinWriter.reskin(model, "vskin_x", List.of(other4))).getMessage(), source);
        }
        byte[] garbage = ("{\"textures\":[{\"source\":\"data:image/png;base64,AAAA\"}]}").getBytes(StandardCharsets.UTF_8);
        assertEquals("base texture 0 is not a readable PNG", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.reskin(garbage, "vskin_x", List.of(other4))).getMessage());
        byte[] noIdentifier = ("{\"textures\":[" + texture("t", hull4) + "]}").getBytes(StandardCharsets.UTF_8);
        JsonObject out = JsonParser.parseString(new String(VehicleSkinWriter.reskin(noIdentifier, "vskin_x", List.of(other4)), StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(out.has("model_identifier")); assertEquals("vskin_x", out.get("name").getAsString());
    }

    @Test void theVehicleAndItsDefaultModelMustExist() throws Exception {
        assertEquals("karve_model", VehicleSkinWriter.defaultModel(vehicles, "karve"));
        assertEquals("unknown vehicle: zeppelin", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.defaultModel(vehicles, "zeppelin")).getMessage());
        Files.writeString(vehicles.resolve("raft.yaml"), "raft:\n  model: raft\n  skins:\n    raft: {name: Raft}\n");
        assertEquals("vehicle raft has no default skin model", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.defaultModel(vehicles, "raft")).getMessage());
        Files.writeString(vehicles.resolve("cart.yml"), "cart:\n  skins:\n    cart: {model: cart}\n");
        assertThrows(IllegalStateException.class, () -> VehicleSkinWriter.defaultModel(vehicles, "cart"));
        Files.writeString(vehicles.resolve("bare.yml"), "bare:\n  model: bare\n  skins:\n    bare: {model: ' '}\n");
        assertThrows(IllegalStateException.class, () -> VehicleSkinWriter.defaultModel(vehicles, "bare"));
        assertEquals("VehicleFramework vehicles folder not found: null", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.defaultModel(null, "karve")).getMessage());
        assertThrows(IllegalStateException.class, () -> VehicleSkinWriter.defaultModel(temp.resolve("missing"), "karve"));
        IllegalStateException noBlueprint = assertThrows(IllegalStateException.class, () ->
            VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "s", "S", OWNER, List.of()));
        assertNotNull(noBlueprint.getMessage());
        Files.delete(blueprints.resolve("ships/Karve_Model.bbmodel"));
        assertEquals("no ModelEngine blueprint karve_model.bbmodel for vehicle karve",
            assertThrows(IllegalStateException.class, () -> write(List.of(other4, other2))).getMessage());
    }

    @Test void blueprintSearchSkipsOurOwnCopies() throws Exception {
        Files.createDirectories(blueprints.resolve("tfmc_submissions"));
        Files.writeString(blueprints.resolve("tfmc_submissions/vskin_x.bbmodel"), "{}");
        assertTrue(VehicleSkinWriter.findBlueprint(blueprints, "vskin_x").isEmpty());
        assertEquals(blueprints.resolve("ships/Karve_Model.bbmodel"), VehicleSkinWriter.findBlueprint(blueprints, "karve_model").orElseThrow());
        assertEquals("ModelEngine blueprints folder not found: null", assertThrows(IllegalStateException.class,
            () -> VehicleSkinWriter.findBlueprint(null, "karve_model")).getMessage());
        assertThrows(IllegalStateException.class, () -> VehicleSkinWriter.findBlueprint(temp.resolve("missing"), "karve_model"));
    }

    @Test void inputsAreValidated() throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
            VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "Bad-Slug", "x", OWNER, List.of(other4, other2)));
        assertEquals("vehicle (base_set) is required", assertThrows(IllegalArgumentException.class, () ->
            VehicleSkinWriter.write(blueprints, vehicles, skinsYml, " ", "s", "x", OWNER, List.of(other4, other2))).getMessage());
        assertEquals("slug is required", assertThrows(IllegalArgumentException.class, () ->
            VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", null, "x", OWNER, List.of(other4, other2))).getMessage());
        assertEquals("owner (player uuid) is required", assertThrows(IllegalArgumentException.class, () ->
            VehicleSkinWriter.write(blueprints, vehicles, skinsYml, "karve", "s", "x", null, List.of(other4, other2))).getMessage());
        Files.writeString(skinsYml, "broken: [unterminated\n");
        assertThrows(java.io.IOException.class, () -> write(List.of(other4, other2)));
        assertEquals("vskin_abc", VehicleSkinWriter.modelId("abc"));
    }
}

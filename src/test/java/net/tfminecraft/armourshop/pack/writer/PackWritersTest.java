package net.tfminecraft.armourshop.pack.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.tfminecraft.armourshop.pack.model.*;
import net.tfminecraft.armourshop.pack.util.*;
import net.tfminecraft.armourshop.pack.writer.armor.*;
import net.tfminecraft.armourshop.pack.writer.bow.*;
import net.tfminecraft.armourshop.pack.writer.flat.*;
import net.tfminecraft.armourshop.pack.writer.gun.*;
import net.tfminecraft.armourshop.pack.writer.large.*;
import net.tfminecraft.armourshop.pack.writer.mask.*;
import net.tfminecraft.armourshop.pack.writer.model3d.*;
import net.tfminecraft.tfmcweb.TFMCWeb;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PackWritersTest {
    @TempDir Path temp;
    private static final byte[] MODEL = "{\"textures\":{\"0\":\"tfmc_submissions:item/skin\"}}".getBytes(StandardCharsets.UTF_8);
    private byte[] png;
    private String oldRealm;
    private RuntimeException oldFailure;
    @BeforeEach void setup() throws Exception {
        oldRealm = TFMCWeb.realmId; oldFailure = TFMCWeb.failure; TFMCWeb.realmId = "main"; TFMCWeb.failure = null;
        png = PngUtil.solidPng(2, 3, new Color(10, 20, 30, 127));
    }
    @AfterEach void restore() {TFMCWeb.realmId = oldRealm; TFMCWeb.failure = oldFailure;}
    interface Writer {List<Path> write(PackSubmission submission) throws Exception;}
    interface NamedWriter {List<Path> write(PackSubmission submission, String namespace) throws Exception;}
    record Spec(PackKind kind, int files, Writer normal, NamedWriter named) {}
    private List<Spec> specs() {
        Path guns = temp.resolve("gag/skins.yml");
        return List.of(
            new Spec(PackKind.ARMOR_SET, 7, s -> ArmorSetWriter.write(temp, s), (s,n) -> ArmorSetWriter.write(temp,s,n)),
            new Spec(PackKind.ITEM, 2, s -> FlatItemWriter.write(temp,s), (s,n) -> FlatItemWriter.write(temp,s,n)),
            new Spec(PackKind.HANDHELD, 2, s -> FlatItemWriter.write(temp,s), (s,n) -> FlatItemWriter.write(temp,s,n)),
            new Spec(PackKind.LARGE_HANDHELD, 3, s -> LargeHandheldWriter.write(temp,s), (s,n) -> LargeHandheldWriter.write(temp,s,n)),
            new Spec(PackKind.BOW, 5, s -> BowWriter.write(temp,s), (s,n) -> BowWriter.write(temp,s,n)),
            new Spec(PackKind.LARGE_BOW, 9, s -> LargeBowWriter.write(temp,s), (s,n) -> LargeBowWriter.write(temp,s,n)),
            new Spec(PackKind.CROSSBOW, 7, s -> BowWriter.writeCrossbow(temp,s), (s,n) -> BowWriter.writeCrossbow(temp,s,n)),
            new Spec(PackKind.ITEM_3D, 3, s -> Item3dWriter.write(temp,s), (s,n) -> Item3dWriter.write(temp,s,n)),
            new Spec(PackKind.HELMET_3D, 3, s -> Item3dWriter.writeHelmet3d(temp,s), (s,n) -> Item3dWriter.writeHelmet3d(temp,s,n)),
            new Spec(PackKind.MASK, 3, s -> Item3dWriter.writeMask(temp,s,PackPaths.playerNamespace()), (s,n) -> Item3dWriter.writeMask(temp,s,n)),
            new Spec(PackKind.SHIELD, 4, s -> ShieldWriter.write(temp,s), (s,n) -> ShieldWriter.write(temp,s,n)),
            new Spec(PackKind.GUN, 6, s -> GunWriter.write(temp,s,"rifles",guns), (s,n) -> GunWriter.write(temp,s,"rifles",guns,n)),
            new Spec(PackKind.BOOK, 3, s -> BookWriter.write(temp,s), (s,n) -> BookWriter.write(temp,s,n)));
    }
    private Map<String,byte[]> files() {
        Map<String,byte[]> files = new LinkedHashMap<>();
        for (String key : List.of("helmet","chestplate","leggings","boots","layer_1","layer_2","texture","pull_0","pull_1","pull_2","charged","unsigned","signed","helmet_texture")) files.put(key,png);
        for (String key : List.of("model","model_0","model_1","model_2","model_blocking","carry","reload","aim","aim_charged")) files.put(key,MODEL);
        return files;
    }
    private PackSubmission submission(PackKind kind) {return new PackSubmission(kind.name().toLowerCase(Locale.ROOT), "Skin \"quoted\"", kind, files());}
    private YamlConfiguration yaml(Path path) throws Exception {var yaml = new YamlConfiguration(); yaml.load(path.toFile()); return yaml;}

    @Test void everyWriterEmitsItsExpectedFilesYamlAndUntouchedWebModels() throws Exception {
        for (Spec spec : specs()) {
            var sub = submission(spec.kind); var written = spec.normal.write(sub); assertEquals(spec.files, written.size(), spec.kind.toString());
            for (Path path : written) {
                assertTrue(Files.isRegularFile(path));
                if (path.toString().endsWith(".png")) assertArrayEquals(png,Files.readAllBytes(path));
                if (path.toString().endsWith(".json")) assertArrayEquals(MODEL,Files.readAllBytes(path));
            }
            var y = yaml(PackPaths.configsDir(temp).resolve(sub.slug()+".yml")); assertEquals(PackPaths.NAMESPACE,y.getString("info.namespace")); assertTrue(y.isConfigurationSection("items"));
            String item = sub.slug() + (spec.kind == PackKind.ARMOR_SET ? "_helmet" : spec.kind == PackKind.GUN ? "_carry" : "");
            assertTrue(y.getString("items."+item+".display_name").startsWith(sub.displayName()));
            var custom = spec.named.write(sub," custom "); assertEquals(spec.files,custom.size()); for(Path path:custom) assertTrue(path.startsWith(temp.resolve("custom")));
            assertEquals("custom",yaml(PackPaths.configsDir(temp,"custom").resolve(sub.slug()+".yml")).getString("info.namespace"));
        }
        assertEquals("item/handheld",yaml(PackPaths.configsDir(temp).resolve("handheld.yml")).getString("items.handheld.resource.parent"));
        assertTrue(yaml(PackPaths.configsDir(temp).resolve("helmet_3d.yml")).getBoolean("items.helmet_3d.behaviours.hat"));
        assertEquals("WRITTEN_BOOK",yaml(PackPaths.configsDir(temp).resolve("book.yml")).getString("items.book_signed.resource.material"));
        Map<String,byte[]> armor=files(); armor.put("helmet_model",MODEL);
        var written=ArmorSetWriter.write(temp,new PackSubmission("three_d","3D",PackKind.ARMOR_SET,armor)); assertEquals(8,written.size());
        assertTrue(yaml(PackPaths.configsDir(temp).resolve("three_d.yml")).getBoolean("items.three_d_helmet.behaviours.hat"));
    }

    @Test void writersRejectWrongKindNamespaceAndMissingRequiredAssets() throws Exception {
        for (Spec spec:specs()) {
            var wrong=submission(spec.kind==PackKind.ARMOR_SET ? PackKind.ITEM : PackKind.ARMOR_SET);
            assertThrows(IllegalArgumentException.class,()->spec.normal.write(wrong),spec.kind.toString());
            for(String ns:Arrays.asList(null," ")) assertThrows(IllegalArgumentException.class,()->spec.named.write(submission(spec.kind),ns));
            assertThrows(IllegalArgumentException.class,()->spec.normal.write(new PackSubmission("missing","Missing",spec.kind,Map.of())));
        }
        assertThrows(IllegalArgumentException.class,()->GunWriter.write(temp,submission(PackKind.GUN),"rifles",null));
        assertThrows(IllegalArgumentException.class,()->LargeBowWriter.modelStemFor("charged"));
        assertThrows(IllegalArgumentException.class,()->BowFrames.fileSuffix("unknown"));
    }

    @Test void gunAndBookRemovalHandleDefaultsMissingFilesAndInvalidSlugs() throws Exception {
        var gun=submission(PackKind.GUN); Path skins=temp.resolve("skins.yml"); var written=GunWriter.write(temp,gun,"rifles",skins);
        assertEquals(new HashSet<>(written),new HashSet<>(GunWriter.remove(temp," GUN ",skins))); assertTrue(GunWriter.remove(temp,null,"gun",null).isEmpty());
        var book=submission(PackKind.BOOK); written=BookWriter.write(temp,book);
        assertEquals(new HashSet<>(written),new HashSet<>(BookWriter.remove(temp," "," BOOK "))); assertTrue(BookWriter.remove(temp,null,"book").isEmpty());
        for(String slug:Arrays.asList(null," ")) {
            assertThrows(IllegalArgumentException.class,()->GunWriter.remove(temp,slug,skins)); assertThrows(IllegalArgumentException.class,()->BookWriter.remove(temp,null,slug));
        }
    }

    @Test void gunRegistryPreservesOtherBlocksReplacesInPlaceAndMapsAllBaseSets() throws Exception {
        Path registry=temp.resolve("gag/skins.yml");
        for(var pair:Map.of("rifles","rifle","pistols","pistol","shotguns","shotgun","launchers","launcher").entrySet()) assertEquals(pair.getValue(),GunsSkinsYml.gagType(" "+pair.getKey().toUpperCase(Locale.ROOT)+" "));
        for(String invalid:Arrays.asList(null," ","bows")) assertThrows(IllegalArgumentException.class,()->GunsSkinsYml.gagType(invalid));
        assertThrows(IllegalArgumentException.class,()->GunsSkinsYml.upsert(null,"skin","rifles"));
        for(String invalid:Arrays.asList(null," ")) assertThrows(IllegalArgumentException.class,()->GunsSkinsYml.upsert(registry,invalid,"rifles"));
        GunsSkinsYml.upsert(registry,"first","rifles"); GunsSkinsYml.upsert(registry,"middle","pistols",null); GunsSkinsYml.upsert(registry,"last","shotguns"," custom ");
        GunsSkinsYml.upsert(registry,"middle","launchers"," ");
        assertEquals("launcher",yaml(registry).getStringList("middle.types").getFirst()); assertEquals("ia.custom:last_carry",yaml(registry).getString("last.carry"));
        assertTrue(GunsSkinsYml.remove(registry,"first")); assertTrue(GunsSkinsYml.remove(registry,"middle")); assertFalse(GunsSkinsYml.remove(registry,"unknown"));
        assertTrue(GunsSkinsYml.remove(registry,"last")); assertEquals("",Files.readString(registry)); assertFalse(GunsSkinsYml.remove(registry,"last"));
        assertFalse(GunsSkinsYml.remove(null,"skin")); assertFalse(GunsSkinsYml.remove(temp.resolve("none"),"skin"));
    }

    @Test void masksRegistryHandlesCommentsValidationAtomicFallbackAndDeletion() throws Exception {
        Path registry=temp.resolve("masks/custom.yml"); Files.createDirectories(registry.getParent());
        Files.writeString(registry,"# existing\n\nmasks:\n  old:\n    item: ia.old:old\n"); MasksYml.upsert(registry," new_9 "," ns-9 ");
        assertEquals("ia.old:old",yaml(registry).getString("masks.old.item")); assertEquals("ia.ns-9:new_9",yaml(registry).getString("masks.new_9.item"));
        assertTrue(MasksYml.remove(registry,"old")); assertFalse(MasksYml.remove(registry,"unknown")); assertFalse(MasksYml.remove(null,"skin")); assertFalse(MasksYml.remove(temp.resolve("none"),"skin"));
        assertThrows(IllegalArgumentException.class,()->MasksYml.upsert(null,"skin","ns"));
        for(String slug:Arrays.asList(null," ","BAD")) assertThrows(IllegalArgumentException.class,()->MasksYml.upsert(registry,slug,"ns"));
        for(String ns:Arrays.asList(null," ","bad:name")) assertThrows(IllegalArgumentException.class,()->MasksYml.upsert(registry,"skin",ns));
        for(String broken:List.of("masks:\n  bad:\n    item: \n","masks:\n  bad value\n")) {
            Files.writeString(registry,broken); assertThrows(IOException.class,()->MasksYml.upsert(registry,"skin","ns")); assertEquals(broken,Files.readString(registry));
        }
        Files.delete(registry);
        try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(()->Files.move(any(Path.class),eq(registry),eq(StandardCopyOption.ATOMIC_MOVE),eq(StandardCopyOption.REPLACE_EXISTING)))
                .thenThrow(new AtomicMoveNotSupportedException("temporary","registry","filesystem"));
            MasksYml.upsert(registry,"skin","ns"); assertEquals("ia.ns:skin",yaml(registry).getString("masks.skin.item"));
        }
        Path relative=Path.of("test-mask-"+UUID.randomUUID()+".yml");
        try {MasksYml.upsert(relative,"skin","ns"); assertTrue(MasksYml.remove(relative,"skin"));}
        finally {Files.deleteIfExists(relative); Files.deleteIfExists(Path.of(relative+".tmp"));}
    }

    @Test void pngPixelsDimensionsAndMissingEncoderHaveObservableOutcomes() throws Exception {
        var image=ImageIO.read(new ByteArrayInputStream(png)); assertEquals(2,image.getWidth()); assertEquals(3,image.getHeight()); assertEquals(127,image.getRGB(0,0)>>>24);
        assertEquals(image.getRGB(0,0),image.getRGB(1,2));
        try(var io=mockStatic(ImageIO.class)) {
            io.when(()->ImageIO.write(any(java.awt.image.RenderedImage.class),eq("png"),any(OutputStream.class))).thenReturn(false);
            assertThrows(IllegalStateException.class,()->PngUtil.solidPng(1,1,Color.RED));
        }
    }

    @Test void modelNormalizationAndNamespaceRewritePreserveUnrelatedStructure() {
        for(byte[] invalid:Arrays.asList(null,new byte[0])) assertThrows(IllegalArgumentException.class,()->Model3dUtil.normalizeModel(invalid,"skin"));
        for(String raw:List.of("{}","{\"textures\":false}","{\"textures\":{}}")) {
            var root=Model3dUtil.parseObject(Model3dUtil.normalizeModel(raw.getBytes(StandardCharsets.UTF_8),"skin"));
            assertEquals("tfmc_submissions:item/skin",root.getAsJsonObject("textures").get("particle").getAsString()); assertEquals(2,root.getAsJsonObject("textures").size());
        }
        var root=Model3dUtil.parseObject(Model3dUtil.normalizeModel("{\"textures\":{\"0\":\"old\",\"num\":4,\"nested\":{}},\"parent\":\"item/generated\"}".getBytes(StandardCharsets.UTF_8),"skin"));
        assertEquals(4,root.getAsJsonObject("textures").get("num").getAsInt()); assertTrue(root.getAsJsonObject("textures").get("nested").isJsonObject());
        assertEquals("item/generated",root.get("parent").getAsString()); assertEquals(root,Model3dUtil.parseObject(Model3dUtil.toBytes(root)));
        assertNull(Model3dUtil.rewriteNamespacePrefix(null,"custom")); byte[] empty={}; assertSame(empty,Model3dUtil.rewriteNamespacePrefix(empty,"custom"));
        for(String ns:Arrays.asList(null," ",PackPaths.NAMESPACE)) assertSame(MODEL,Model3dUtil.rewriteNamespacePrefix(MODEL,ns));
        byte[] untouched="{}".getBytes(StandardCharsets.UTF_8); assertSame(untouched,Model3dUtil.rewriteNamespacePrefix(untouched,"custom"));
        assertTrue(new String(Model3dUtil.rewriteNamespacePrefix(MODEL," custom "),StandardCharsets.UTF_8).contains("custom:item/skin"));
    }

    @Test void packSubmissionValidatesValuesCopiesFileMapAndClampsLegacyGrip() {
        assertThrows(NullPointerException.class,()->new PackSubmission(null,"name",PackKind.ITEM,null));
        assertThrows(IllegalArgumentException.class,()->new PackSubmission(" ","name",PackKind.ITEM,null));
        assertThrows(IllegalArgumentException.class,()->new PackSubmission("slug"," ",PackKind.ITEM,null));
        var source=new LinkedHashMap<String,byte[]>(); source.put(null,png); source.put("null",null); source.put(" texture ",png); source.put("empty",new byte[0]);
        var sub=new PackSubmission(" slug "," name ",PackKind.ITEM,20.0,source); source.clear();
        assertEquals("slug",sub.slug()); assertEquals("name",sub.displayName()); assertEquals(16.0,sub.gripY()); assertEquals(Set.of("texture","empty"),sub.files().keySet()); assertSame(png,sub.requireFile("texture"));
        assertThrows(UnsupportedOperationException.class,()->sub.files().clear()); assertThrows(IllegalArgumentException.class,()->sub.requireFile("missing")); assertThrows(IllegalArgumentException.class,()->sub.requireFile("empty"));
        assertNull(new PackSubmission("slug","name",PackKind.ITEM,null).gripY());
    }

    @Test void legacyGripParsingClampingAndFormattingCoverBoundaryValues() {
        assertEquals(2.5,GripY.parse(" BOTTOM ")); assertEquals(4.0,GripY.parse("middle")); assertEquals(5.5,GripY.parse("TOP")); assertEquals(16.0,GripY.parse("16")); assertEquals(0.0,GripY.parse("0"));
        for(String invalid:Arrays.asList(null," ","unknown","NaN","-1","17","Infinity")) assertThrows(IllegalArgumentException.class,()->GripY.parse(invalid));
        assertEquals(4.0,GripY.clamp(Double.NaN)); assertEquals(0.0,GripY.clamp(-1)); assertEquals(16.0,GripY.clamp(Double.POSITIVE_INFINITY)); assertEquals(2.5,GripY.clamp(2.5));
        assertEquals(4.2,GripY.firstPersonY(2.5),0.00001); assertEquals(6.2,GripY.firstPersonY(5.5),0.00001); assertEquals("4.0",GripY.format(4)); assertEquals("2.5",GripY.format(2.5));
    }

    @Test void realmPathsUseOptionalApiDefaultsAndEveryAssetDirectory() {
        for(String realm:Arrays.asList(null," "," Main ")) {TFMCWeb.realmId=realm; assertEquals(PackPaths.NAMESPACE,PackPaths.playerNamespace());}
        TFMCWeb.realmId=" DEV "; String ns="tfmc_submissions_dev"; assertEquals(ns,PackPaths.playerNamespace());
        assertEquals(temp.resolve(ns),PackPaths.namespaceRoot(temp)); assertEquals(temp.resolve(ns+"/configs"),PackPaths.configsDir(temp));
        Path assets=temp.resolve(ns+"/resourcepack/assets/"+ns); assertEquals(assets,PackPaths.assetsRoot(temp)); assertEquals(assets.resolve("textures"),PackPaths.texturesRoot(temp));
        assertEquals(assets.resolve("textures/armor_icons"),PackPaths.armorIconsDir(temp)); assertEquals(assets.resolve("textures/armor_layers"),PackPaths.armorLayersDir(temp));
        assertEquals(assets.resolve("textures/item"),PackPaths.itemTexturesDir(temp)); assertEquals(assets.resolve("models/item"),PackPaths.itemModelsDir(temp));
        for(String invalid:Arrays.asList(null," ")) assertThrows(IllegalArgumentException.class,()->PackPaths.namespaceRoot(temp,invalid));
        TFMCWeb.failure=new IllegalStateException("not enabled"); assertEquals(PackPaths.NAMESPACE,PackPaths.playerNamespace());
    }

    @Test void realmAndLegacyGripIdentifiersAreIndependentOfServerLocale() {
        Locale old=Locale.getDefault(); try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TFMCWeb.realmId="MAIN";
            assertAll(()->assertEquals(PackPaths.NAMESPACE,PackPaths.playerNamespace()),()->assertEquals(4.0,GripY.parse("MIDDLE")));
        } finally {Locale.setDefault(old);}
    }

    @Test void namespacePathsCannotEscapeContentsDirectory() {
        for(String ns:List.of("../outside","/tmp/outside","a/b","bad:name")) assertThrows(IllegalArgumentException.class,()->PackPaths.namespaceRoot(temp,ns));
        assertEquals(temp.resolve("ns_9-test"),PackPaths.namespaceRoot(temp," ns_9-test "));
    }

    @Test void yamlStringEscapingRoundTripsNamesIncludingControlCharacters() throws Exception {
        assertEquals("",YamlUtil.escapeDoubleQuoted(null)); YamlUtil.validateSlug("skin_9"); assertThrows(IllegalArgumentException.class,()->YamlUtil.validateSlug("Bad"));
        String name="slash\\quote\"\n\r\ttab\b\fform\u0000\u0001\u001f\u007f\u0085\u009f\u2028\u2029";
        var yaml=new YamlConfiguration(); assertDoesNotThrow(()->yaml.loadFromString("name: \""+YamlUtil.escapeDoubleQuoted(name)+"\"\n")); assertEquals(name,yaml.getString("name"));
    }

    @Test void armorColorIsStableAndAvoidsReservedBlackAndWhiteWithDigestFailuresReported() throws Exception {
        assertEquals(ArmorRenderColor.forSlug("skin"),ArmorRenderColor.forSlug(" skin ")); assertTrue(ArmorRenderColor.forSlug("skin").matches("#[0-9a-f]{6}"));
        for(String invalid:Arrays.asList(null," ")) assertThrows(IllegalArgumentException.class,()->ArmorRenderColor.forSlug(invalid));
        MessageDigest digest=mock(MessageDigest.class); when(digest.digest(any(byte[].class))).thenReturn(new byte[]{0,0,0},new byte[]{-1,-1,-1});
        try(var digests=mockStatic(MessageDigest.class)) {
            digests.when(()->MessageDigest.getInstance("SHA-256")).thenReturn(digest);
            assertEquals("#000001",ArmorRenderColor.forSlug("black")); assertEquals("#000001",ArmorRenderColor.forSlug("white"));
            digests.when(()->MessageDigest.getInstance("SHA-256")).thenThrow(new NoSuchAlgorithmException("missing"));
            assertInstanceOf(NoSuchAlgorithmException.class,assertThrows(IllegalStateException.class,()->ArmorRenderColor.forSlug("skin")).getCause());
        }
    }
}

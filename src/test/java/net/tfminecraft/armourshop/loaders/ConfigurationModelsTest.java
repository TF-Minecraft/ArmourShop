package net.tfminecraft.armourshop.loaders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.enums.ArmorType;
import net.tfminecraft.armourshop.objects.*;
import net.tfminecraft.armourshop.utils.NameDisplay;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

class ConfigurationModelsTest {
    @TempDir Path directory;
    final Map<Field, Object> previousCache = new HashMap<>();
    List<BaseSet> previousBases;
    List<SkinCategory> previousCategories;
    final Map<String, ItemStack> items = new HashMap<>();
    final Map<ItemStack, NBTItem> itemData = new IdentityHashMap<>();
    MockedStatic<TLibs> tlibs;
    MockedStatic<NBTItem> nbt;

    @BeforeEach void setup() throws Exception {
        for (Field field : Cache.class.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers())) previousCache.put(field, field.get(null));
        previousBases = BaseSetLoader.oList; BaseSetLoader.oList = new ArrayList<>();
        previousCategories = CategoryLoader.oList; CategoryLoader.oList = new ArrayList<>();
        MockBukkit.mock();
        var api = mock(ItemAPI.class); var creator = mock(ItemCreator.class);
        when(api.getCreator()).thenReturn(creator);
        when(creator.getItemFromPath(anyString())).thenAnswer(call -> items.computeIfAbsent(call.getArgument(0), key -> tagged("ARMOR", key)));
        tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        nbt = mockStatic(NBTItem.class); nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenAnswer(call -> itemData.get(call.getArgument(0)));
    }

    @AfterEach void restore() throws Exception {
        nbt.close(); tlibs.close(); MockBukkit.unmock();
        BaseSetLoader.oList = previousBases; CategoryLoader.oList = previousCategories;
        for (var entry : previousCache.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    File yaml(String name, String body) throws Exception { return Files.writeString(directory.resolve(name), body).toFile(); }

    ItemStack tagged(String type, String id) {
        ItemStack stack = new ItemStack(Material.STONE); NBTItem tag = mock(NBTItem.class);
        when(tag.hasType()).thenReturn(type != null); when(tag.getType()).thenReturn(type);
        when(tag.getString("MMOITEMS_ITEM_ID")).thenReturn(id); when(tag.getItem()).thenReturn(stack);
        itemData.put(stack, tag); return stack;
    }

    @Test void configLoadsTrimmedPathsPointsAndUsableScrollRows() throws Exception {
        new ConfigLoader().load(yaml("config.yml", """
            start-points: [9, 18]
            item-start-points: [10, 11]
            pack-apply:
              ia-contents-path: ' /srv/contents '
              categories-path: ' /srv/categories '
              guns-skins-yml: ' /srv/guns.yml '
              masks-yml: ' /srv/masks.yml '
              force-reload-time: ' 04:30 '
              ia-reload-delay-seconds: -5
            scrolls:
              - {id: ' basic ', label: ' Common Scroll '}
              - {id: rare}
              - {id: blank, label: ' '}
              - {id: 12, label: 13}
              - {label: missing}
              - {id: ' '}
            """));
        assertEquals(List.of(9, 18), Cache.points); assertEquals(List.of(10, 11), Cache.itemPoints);
        assertEquals("/srv/contents", Cache.iaContentsPath); assertEquals("/srv/categories", Cache.categoriesPath);
        assertEquals("/srv/guns.yml", Cache.gunsSkinsYmlPath); assertEquals("/srv/masks.yml", Cache.masksYmlPath);
        assertEquals("04:30", Cache.forceReloadTime); assertEquals(0, Cache.iaReloadDelaySeconds);
        assertEquals(List.of("basic", "rare", "blank", "12"), Cache.scrolls.stream().map(ScrollOption::getId).toList());
        assertEquals(List.of("Common Scroll", "rare", "blank", "13"), Cache.scrolls.stream().map(ScrollOption::getLabel).toList());
        var empty = new ScrollOption(null, null); assertEquals("", empty.getId()); assertEquals("", empty.getLabel());
    }

    @Test void configFallsBackForMissingSettingsAndDefensivelySkipsNullReaderRows() throws Exception {
        new ConfigLoader().load(yaml("empty.yml", "pack-apply:\n  masks-yml: ' '\n"));
        assertEquals("plugins/RPCharacters/custom-masks.yml", Cache.masksYmlPath);
        assertEquals("06:00", Cache.forceReloadTime); assertEquals(5, Cache.iaReloadDelaySeconds);
        assertTrue(Cache.points.isEmpty()); assertTrue(Cache.scrolls.isEmpty());
        // Exercise the loader's defensive boundary if a configuration provider returns a null row.
        try (var configurations = mockConstruction(YamlConfiguration.class, (config, context) ->
                when(config.getMapList("scrolls")).thenReturn(Arrays.asList(null, Map.of("id", "kept"))))) {
            new ConfigLoader().load(directory.resolve("reader.yml").toFile());
            assertEquals(List.of("kept"), Cache.scrolls.stream().map(ScrollOption::getId).toList());
            assertEquals("", Cache.iaContentsPath); assertEquals("", Cache.categoriesPath);
            assertEquals("", Cache.gunsSkinsYmlPath); assertEquals("", Cache.forceReloadTime);
        }
    }

    @Test void permissionGroupsLoadDefaultsMetadataSortedTiersAndNormalizedKinds() throws Exception {
        new PermissionGroupsLoader().load(yaml("groups.yml", """
            defaults:
              name-colour-stops: 2
              skin-token-cooldown-days: 9
              skin-kinds: [' BOOK ', book, null, ' ', 12]
              allow-armor-3d-helmet: true
            groups:
              gold:
                tier: 5
                permission: ranks.gold
                display-name: Gold
                visible: false
                name-colour-stops: 4
                skin-kinds: [' ITEM_3D ', item_3d]
                allow-armor-3d-helmet: false
              ignored: scalar
              first:
                custom: 7
              second:
                skin-kinds: not-a-list
            """));
        assertEquals(Map.of("name-colour-stops", 2, "skin-token-cooldown-days", 9), Cache.permissionGroupDefaults);
        assertEquals(List.of("book", "12"), Cache.permissionGroupDefaultSkinKinds); assertTrue(Cache.permissionGroupDefaultAllowArmor3dHelmet);
        assertEquals(List.of("first", "second", "gold"), Cache.permissionGroups.stream().map(PermissionGroupDefinition::getId).toList());
        var first = Cache.permissionGroups.get(0); assertEquals(0, first.getTier()); assertEquals("", first.getPermission());
        assertEquals("first", first.getDisplayName()); assertTrue(first.isVisible()); assertEquals(Map.of("custom", 7), first.getPerks());
        assertEquals(List.of(), first.getSkinKinds()); assertFalse(first.hasAllowArmor3dHelmet());
        var gold = Cache.permissionGroups.get(2); assertEquals("ranks.gold", gold.getPermission()); assertEquals("Gold", gold.getDisplayName());
        assertFalse(gold.isVisible()); assertEquals(List.of("item_3d"), gold.getSkinKinds());
        assertEquals(Map.of("name-colour-stops", 4), gold.getPerks()); assertTrue(gold.hasAllowArmor3dHelmet());
        assertFalse(gold.getAllowArmor3dHelmet(true));
    }

    @Test void baseSetsResolveEachArmorSlotAndMatchMmoTypeAndId() throws Exception {
        new BaseSetLoader().load(yaml("bases.yml", """
            iron:
              helmet: [helmets.iron]
              chestplate: [armors.chest]
              leggings: [armors.legs]
              boots: [armors.boots]
              item: [tools.pick]
            empty: {}
            """));
        assertEquals(2, BaseSetLoader.get().size()); BaseSet base = BaseSetLoader.getByString("IRON");
        assertEquals("iron", base.getId()); assertNull(BaseSetLoader.getByString("absent"));
        List<List<ArmorPiece>> armor = List.of(base.getHelmets(), base.getChestplates(), base.getLeggings(), base.getBoots());
        for (List<ArmorPiece> slot : armor) {
            ArmorPiece piece = slot.getFirst(); assertTrue(base.contains(piece.getItem().getItem(), piece.getType()));
            assertFalse(base.contains(tagged("OTHER", "other"), piece.getType()));
            assertTrue(BaseSetLoader.getByString("empty").getHelmets().isEmpty());
        }
        assertTrue(base.contains(items.get("m.tools.pick"), ArmorType.ITEM));
        for (ArmorType type : ArmorType.values()) assertFalse(base.contains(tagged("ARMOR", "unlisted"), type));
        ArmorPiece helmet = base.getHelmets().getFirst();
        assertTrue(helmet.is(tagged("armor", "M.HELMETS.IRON")));
        assertFalse(helmet.is(tagged(null, "ordinary")));
        ArmorPiece ordinary = new ArmorPiece("ordinary", tagged(null, "ordinary"), ArmorType.ITEM);
        assertEquals(ArmorType.ITEM, ordinary.getType()); assertNotNull(ordinary.getItem());
        BaseSetLoader.clear(); assertTrue(BaseSetLoader.get().isEmpty());
    }

    @Test void categoriesAndSkinFilesLoadAllFieldsAndLookUpSets() throws Exception {
        BaseSet base = new BaseSet("iron", new YamlConfiguration()); BaseSetLoader.get().add(base);
        new CategoryLoader().load(yaml("categories.yml", """
            armor:
              name: Armor
              item: v.IRON_CHESTPLATE
            tools:
              name: Tools
              item: v.IRON_PICKAXE
              is-item: true
              permission: skins.tools
            """));
        assertEquals(2, CategoryLoader.get().size()); SkinCategory category = CategoryLoader.getByString("TOOLS");
        assertSame(category, CategoryLoader.getByName("tools")); assertNull(CategoryLoader.getByString("missing")); assertNull(CategoryLoader.getByName("missing"));
        assertEquals("tools", category.getId()); assertEquals("v.IRON_PICKAXE", category.getItem()); assertTrue(category.isItem());
        assertTrue(category.hasPermission()); assertEquals("skins.tools", category.getPermission());
        assertFalse(CategoryLoader.getByString("armor").hasPermission()); assertFalse(CategoryLoader.getByString("armor").isItem());
        new SkinSetLoader().load(yaml("tools.yml", """
            blue:
              name: Blue Knight
              set: iron
              scroll: m.scroll.blue
              helmet: ia.blue:helmet
              chestplate: ia.blue:chest
              leggings: ia.blue:legs
              boots: ia.blue:boots
              item: ia.blue:pick
              add-name: true
              permission: skins.blue
            plain:
              name: Plain
            """));
        SkinSet skin = CategoryLoader.getByContainsSet("BLUE"); assertSame(skin, category.getSets().getFirst());
        assertNull(CategoryLoader.getByContainsSet("missing")); assertEquals("blue", skin.getId()); assertEquals("Blue Knight", skin.getName());
        assertSame(base, skin.getSet()); assertTrue(skin.hasScroll()); assertEquals("m.scroll.blue", skin.getScroll());
        assertTrue(skin.hasHelmet()); assertEquals("ia.blue:helmet", skin.getHelmet());
        assertTrue(skin.hasChestplate()); assertEquals("ia.blue:chest", skin.getChestplate());
        assertTrue(skin.hasLeggings()); assertEquals("ia.blue:legs", skin.getLeggings());
        assertTrue(skin.hasBoots()); assertEquals("ia.blue:boots", skin.getBoots());
        assertTrue(skin.hasItem()); assertEquals("ia.blue:pick", skin.getItem()); assertTrue(skin.addName());
        assertTrue(skin.hasPermission()); assertEquals("skins.blue", skin.getPermission());
        assertEquals("Blue Knight Helmet", skin.getFormattedPieceName(ArmorType.HELMET));
        assertEquals("Blue Knight", skin.getFormattedPieceName(ArmorType.ITEM)); assertEquals("Blue Knight", skin.getFormattedPieceName(null));
        SkinSet plain = category.getSets().get(1); assertFalse(plain.addName()); assertFalse(plain.hasPermission()); assertNull(plain.getSet());
        assertFalse(plain.hasScroll()); assertFalse(plain.hasHelmet()); assertFalse(plain.hasChestplate());
        assertFalse(plain.hasLeggings()); assertFalse(plain.hasBoots()); assertFalse(plain.hasItem());
        new SkinSetLoader().load(yaml("unknown.yml", "ignored: {}\n")); assertEquals(2, category.getSets().size());
        SkinCategory restrictedCopy = new SkinCategory(category); assertEquals(category.getPermission(), restrictedCopy.getPermission());
        assertEquals(category.getId(), restrictedCopy.getId()); assertEquals(category.getItem(), restrictedCopy.getItem());
        assertEquals(category.getName(), restrictedCopy.getName()); assertTrue(restrictedCopy.isItem());
        CategoryLoader.clear(); assertTrue(CategoryLoader.get().isEmpty());
    }

    @Test void copyingPublicCategoriesPreservesTheirLackOfPermission() {
        var config = new YamlConfiguration(); config.set("name", "Public"); config.set("item", "v.STONE");
        SkinCategory copy = assertDoesNotThrow(() -> new SkinCategory(new SkinCategory("public", config)));
        assertFalse(copy.hasPermission()); assertEquals("Public", copy.getName());
    }

    @Test void pieceSuffixesAreIndependentOfServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var config = new YamlConfiguration(); config.set("name", "Blue Knight");
            assertEquals("Blue Knight Leggings", new SkinSet("blue", config).getFormattedPieceName(ArmorType.LEGGINGS));
        } finally { Locale.setDefault(previous); }
    }

    @Test void displayNamesParseEmbeddedColorsExplicitOverridesAndStyleAliases() {
        var config = new YamlConfiguration();
        config.set("name", "#AABBCC Knight");
        var parsed = NameDisplay.parseFromConfig(config, "name");
        assertEquals("Knight", parsed.plain); assertEquals(List.of("#aabbcc"), parsed.colours);
        config.set("colour", " #123456 ");
        assertEquals(List.of("#123456"), NameDisplay.parseFromConfig(config, "name").colours);
        assertEquals("Knight", NameDisplay.parseFromConfig(config, "name").plain);
        config.set("name", "Plain"); assertEquals("Plain", NameDisplay.parseFromConfig(config, "name").plain);
        config.set("colour", null); config.set("name", "&A Archer");
        assertEquals("Archer", NameDisplay.parseFromConfig(config, "name").plain);
        assertEquals(List.of("§a"), NameDisplay.parseFromConfig(config, "name").colours);
        config.set("name", "#ABCDEF"); assertEquals("#ABCDEF", NameDisplay.parseFromConfig(config, "name").plain);
        config.set("name", "&a"); assertEquals("&a", NameDisplay.parseFromConfig(config, "name").plain);
        config.set("name", ""); assertEquals("", NameDisplay.parseFromConfig(config, "name").plain);
        config.set("styles", Arrays.asList(null, " BOLD ", "italic", "underline", "underlined", "strikethrough", "strike", "unknown"));
        assertEquals(List.of("bold", "italic", "underline", "underlined", "strikethrough", "strike"), NameDisplay.readStyles(config));
        assertThrows(UnsupportedOperationException.class, () -> NameDisplay.parseFromConfig(config, "name").styles.add("bold"));
        config.set("styles", null); config.set("name", "Plain"); assertEquals("Plain", NameDisplay.formatFromConfig(config, "name"));
    }

    @Test void colorAliasesAndMissingNameDataHavePredictableDefaults() {
        assertTrue(NameDisplay.readColours(null).isEmpty()); assertTrue(NameDisplay.readStyles(null).isEmpty());
        assertEquals("", NameDisplay.parseFromConfig(null, "name").plain);
        var empty = new NameDisplay.ParsedName(null, null, null);
        assertEquals("", empty.plain); assertTrue(empty.colours.isEmpty()); assertTrue(empty.styles.isEmpty());
        var config = new YamlConfiguration();
        config.set("colour", Arrays.asList(" #123456 ", null, 12)); assertEquals(List.of("#123456", "12"), NameDisplay.readColours(config));
        config.set("colour", null); config.set("colors", Arrays.asList(null, " red ", " blue "));
        assertEquals(List.of("red", "blue"), NameDisplay.readColours(config));
        config.set("colors", null); config.set("colour", " "); assertTrue(NameDisplay.readColours(config).isEmpty());
        config.set("colour", null); config.set("color", " "); assertTrue(NameDisplay.readColours(config).isEmpty());
        config.set("color", " green "); assertEquals(List.of("green"), NameDisplay.readColours(config));
        config.set("color", null); assertTrue(NameDisplay.readColours(config).isEmpty()); assertTrue(NameDisplay.readStyles(config).isEmpty());
        var provider = mock(org.bukkit.configuration.ConfigurationSection.class);
        assertEquals("", NameDisplay.parseFromConfig(provider, "name").plain);
    }

    @Test void unreadableAndInvalidFilesAreContainedAndResetLoaderState() throws Exception {
        File missing = directory.resolve("missing.yml").toFile();
        new ConfigLoader().load(missing); assertTrue(Cache.scrolls.isEmpty());
        new PermissionGroupsLoader().load(missing); assertTrue(Cache.permissionGroups.isEmpty());
        assertEquals(Map.of("skin-token-cooldown-days", -1), Cache.permissionGroupDefaults);
        assertTrue(Cache.permissionGroupDefaultSkinKinds.isEmpty()); assertFalse(Cache.permissionGroupDefaultAllowArmor3dHelmet);
        new BaseSetLoader().load(missing); assertTrue(BaseSetLoader.get().isEmpty());
        new CategoryLoader().load(missing); assertTrue(CategoryLoader.get().isEmpty());
        var config = new YamlConfiguration(); config.set("name", "Missing");
        SkinCategory category = new SkinCategory("missing", config); CategoryLoader.get().add(category);
        new SkinSetLoader().load(missing); assertTrue(category.getSets().isEmpty());
        File invalid = yaml("invalid.yml", "broken: [\n");
        new PermissionGroupsLoader().load(invalid); assertTrue(Cache.permissionGroups.isEmpty());
    }
}

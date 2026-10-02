package net.tfminecraft.armourshop.pack.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.CatalogPushResult;
import net.tfminecraft.armourshop.loaders.CategoryLoader;
import net.tfminecraft.armourshop.objects.*;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

class CatalogSyncServiceTest {
    private List<ScrollOption> oldScrolls;
    private List<PermissionGroupDefinition> oldGroups;
    private List<String> oldKinds;
    private Map<String, Integer> oldDefaults;
    private boolean oldHelmet;
    private ArmourShop oldPlugin;
    private MockedStatic<CategoryLoader> categories;

    @BeforeEach void setup() {
        oldScrolls = Cache.scrolls; oldGroups = Cache.permissionGroups; oldKinds = Cache.permissionGroupDefaultSkinKinds;
        oldDefaults = Cache.permissionGroupDefaults; oldHelmet = Cache.permissionGroupDefaultAllowArmor3dHelmet; oldPlugin = ArmourShop.plugin;
        Cache.scrolls = List.of(); Cache.permissionGroups = List.of(); Cache.permissionGroupDefaultSkinKinds = List.of();
        Cache.permissionGroupDefaults = Map.of("name-colour-stops", 2, "max-3d-pair-bytes", 40000, "skin-token-cooldown-days", 7);
        Cache.permissionGroupDefaultAllowArmor3dHelmet = false;
        categories = mockStatic(CategoryLoader.class); categories.when(CategoryLoader::get).thenReturn(List.of());
    }
    @AfterEach void restore() {
        categories.close(); Cache.scrolls = oldScrolls; Cache.permissionGroups = oldGroups; Cache.permissionGroupDefaultSkinKinds = oldKinds;
        Cache.permissionGroupDefaults = oldDefaults; Cache.permissionGroupDefaultAllowArmor3dHelmet = oldHelmet; ArmourShop.plugin = oldPlugin;
    }
    private static SkinCategory category(String id, String name, boolean item, List<SkinSet> sets) {
        var category = mock(SkinCategory.class); when(category.getId()).thenReturn(id); when(category.getName()).thenReturn(name);
        when(category.isItem()).thenReturn(item); when(category.getSets()).thenReturn(sets); return category;
    }
    private static SkinSet skin(String id) { var skin = mock(SkinSet.class); when(skin.getId()).thenReturn(id); return skin; }
    private static PermissionGroupDefinition group(String id, String permission, String name, List<String> kinds) {
        return new PermissionGroupDefinition(id, permission, name, 3, true, Map.of("name-colour-stops", 5), kinds, true);
    }
    private static JsonObject payload() { return JsonParser.parseString(CatalogSyncService.buildPayloadJson()).getAsJsonObject(); }

    @Test void serializesCategoriesScrollsAndEntitlementsWhileSkippingInvalidRows() {
        var rows = Arrays.asList(null, category(null, "invalid", false, null),
            category(" ", "invalid", false, null), category("armor", "§aArmor", false, Arrays.asList(null, skin(null), skin(" "), skin("first"), skin("second"))),
            category("items", null, true, null));
        categories.when(CategoryLoader::get).thenReturn(rows);
        Cache.scrolls = Arrays.asList(null, new ScrollOption(null, null), new ScrollOption("one", "One"), new ScrollOption("two", null));
        Cache.permissionGroupDefaultSkinKinds = Arrays.asList(null, " ", " BOW ", "BOOK");
        Cache.permissionGroups = Arrays.asList(null, group(null, null, null, null), group(" ", null, null, null),
            group("supporter", "rank.supporter", "Supporter", List.of("ITEM_3D")), group("donor", null, null, null));
        var root = payload();
        assertEquals(2, root.getAsJsonArray("categories").size());
        var armor = root.getAsJsonArray("categories").get(0).getAsJsonObject();
        assertEquals("armor", armor.get("id").getAsString()); assertEquals("Armor", armor.get("name").getAsString());
        assertFalse(armor.get("is_item").getAsBoolean()); assertEquals(JsonParser.parseString("[\"first\",\"second\"]"), armor.get("skin_sets"));
        var items = root.getAsJsonArray("categories").get(1).getAsJsonObject();
        assertEquals("items", items.get("name").getAsString()); assertTrue(items.get("is_item").getAsBoolean()); assertTrue(items.getAsJsonArray("skin_sets").isEmpty());
        assertEquals(2, root.getAsJsonArray("scrolls").size());
        assertEquals("two", root.getAsJsonArray("scrolls").get(1).getAsJsonObject().get("label").getAsString());
        var entitlements = root.getAsJsonObject("entitlements"); var defaults = entitlements.getAsJsonObject("defaults");
        assertEquals(2, defaults.get("name_colour_stops").getAsInt()); assertEquals(40000, defaults.get("max_3d_pair_bytes").getAsInt());
        assertEquals(7, defaults.get("skin_token_cooldown_days").getAsInt()); assertFalse(defaults.get("allow_armor_3d_helmet").getAsBoolean());
        assertEquals(JsonParser.parseString("[\"bow\",\"book\"]"), defaults.get("skin_kinds"));
        var groups = entitlements.getAsJsonArray("groups"); assertEquals(2, groups.size());
        var supporter = groups.get(0).getAsJsonObject();
        assertEquals("supporter", supporter.get("id").getAsString()); assertEquals("rank.supporter", supporter.get("permission").getAsString());
        assertEquals("Supporter", supporter.get("display_name").getAsString()); assertEquals(3, supporter.get("tier").getAsInt());
        assertEquals(5, supporter.get("name_colour_stops").getAsInt()); assertEquals(40000, supporter.get("max_3d_pair_bytes").getAsInt());
        assertEquals(7, supporter.get("skin_token_cooldown_days").getAsInt()); assertTrue(supporter.get("allow_armor_3d_helmet").getAsBoolean());
        assertEquals(JsonParser.parseString("[\"item_3d\"]"), supporter.get("skin_kinds"));
        assertEquals("donor", groups.get(1).getAsJsonObject().get("display_name").getAsString());
        assertEquals("", groups.get(1).getAsJsonObject().get("permission").getAsString());
    }

    @Test void nullOptionalCollectionsProduceEmptyArrays() {
        Cache.scrolls = null; Cache.permissionGroups = null;
        var root = payload(); assertTrue(root.getAsJsonArray("categories").isEmpty()); assertTrue(root.getAsJsonArray("scrolls").isEmpty());
        assertTrue(root.getAsJsonObject("entitlements").getAsJsonArray("groups").isEmpty());
    }

    @Test void escapesQuotesSlashesWhitespaceAndControlCharactersInJson() {
        String value = "a\\b\"c\nd\re\tf\u0001g";
        var row = category(value, value, true, List.of(skin(value)));
        categories.when(CategoryLoader::get).thenReturn(List.of(row));
        Cache.scrolls = List.of(new ScrollOption(value, value));
        Cache.permissionGroups = List.of(group(value, value, value, List.of(value)));
        var root = payload();
        assertEquals(value, root.getAsJsonArray("categories").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(value, root.getAsJsonArray("categories").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(value, root.getAsJsonArray("scrolls").get(0).getAsJsonObject().get("label").getAsString());
        assertEquals(value, root.getAsJsonObject("entitlements").getAsJsonArray("groups").get(0).getAsJsonObject().get("permission").getAsString());
    }

    @Test void skinKindNormalizationDoesNotDependOnServerLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Cache.permissionGroupDefaultSkinKinds = List.of("ITEM_3D");
            Cache.permissionGroups = List.of(group("donor", "rank.donor", "Donor", List.of("ITEM_3D")));
            var root = payload().getAsJsonObject("entitlements");
            assertEquals("item_3d", root.getAsJsonObject("defaults").getAsJsonArray("skin_kinds").get(0).getAsString());
            assertEquals("item_3d", root.getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("skin_kinds").get(0).getAsString());
        } finally { Locale.setDefault(old); }
    }

    @Test void synchronousPushForwardsCompletePayloadAndReturnsGatewayResult() {
        try (var client = mockStatic(ProvinceSystemClient.class)) {
            var expected = CatalogPushResult.success(1, 2, 3, "now");
            client.when(() -> ProvinceSystemClient.pushCatalog(anyString())).thenReturn(expected);
            assertSame(expected, CatalogSyncService.pushNow());
            client.verify(() -> ProvinceSystemClient.pushCatalog(argThat(json -> JsonParser.parseString(json).getAsJsonObject().has("entitlements"))));
        }
    }

    @Test void asynchronousPushSchedulesWorkAndLogsSuccessOrFailure() {
        try (var bukkit = mockStatic(Bukkit.class); var client = mockStatic(ProvinceSystemClient.class)) {
            var scheduler = mock(BukkitScheduler.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var plugin = mock(ArmourShop.class); var logger = mock(Logger.class); when(plugin.getLogger()).thenReturn(logger);
            List<Runnable> tasks = new ArrayList<>();
            when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(i -> { tasks.add(i.getArgument(1)); return null; });
            CatalogSyncService.pushAsync(null); verifyNoInteractions(scheduler);
            ArmourShop.plugin = null; CatalogSyncService.pushAsyncFromPlugin(); verifyNoInteractions(scheduler);
            client.when(() -> ProvinceSystemClient.pushCatalog(anyString())).thenReturn(CatalogPushResult.success(1, 2, 3, "now"), CatalogPushResult.fail("offline"));
            CatalogSyncService.pushAsync(plugin); assertEquals(1, tasks.size()); client.verifyNoInteractions();
            tasks.removeFirst().run(); verify(logger).info("[catalog] synced to ProvinceSystem: categories=1 skin_sets=2 scrolls=3");
            ArmourShop.plugin = plugin; CatalogSyncService.pushAsyncFromPlugin(); assertEquals(1, tasks.size());
            tasks.removeFirst().run(); verify(logger).warning("[catalog] sync failed: offline");
        }
    }
}

package net.tfminecraft.armourshop.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.*;
import net.tfminecraft.armourshop.enums.ArmorType;
import net.tfminecraft.armourshop.holder.ASInventoryHolder;
import net.tfminecraft.armourshop.loaders.*;
import net.tfminecraft.armourshop.objects.*;
import net.tfminecraft.gunsandgadgets.guns.skins.*;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class ShopMenusTest {
    private ServerMock server;
    private PlayerMock player;
    private ArmourShop plugin, previousPlugin;
    private Logger logger;
    private InventoryManager menus;
    private SkinManager listener;
    private ItemCreator creator;
    private ItemChecker checker;
    private ArmorMerger merger;
    private BaseSet base;
    private List<SkinCategory> categories;
    private List<Integer> oldPoints, oldItemPoints;
    private MockedStatic<TLibs> tlibs;
    private MockedStatic<CategoryLoader> categoryLoader;
    private MockedStatic<BaseSetLoader> baseLoader;

    @BeforeEach void setup() {
        server = MockBukkit.mock(); player = server.addPlayer();
        previousPlugin = ArmourShop.plugin; plugin = mock(ArmourShop.class); logger = mock(Logger.class);
        when(plugin.getName()).thenReturn("ArmourShop"); when(plugin.namespace()).thenReturn("armourshop"); when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger); when(plugin.isEnabled()).thenReturn(true); ArmourShop.plugin = plugin;
        oldPoints = Cache.points; oldItemPoints = Cache.itemPoints;
        Cache.points = List.of(9, 18); Cache.itemPoints = List.of(9, 10);
        var api = mock(ItemAPI.class); creator = mock(ItemCreator.class); checker = mock(ItemChecker.class); merger = mock(ArmorMerger.class);
        when(api.getCreator()).thenReturn(creator); when(api.getChecker()).thenReturn(checker); when(api.getArmorMerger()).thenReturn(merger);
        when(creator.getItemFromPath(anyString())).thenAnswer(i -> new ItemStack(Material.PAPER));
        when(creator.getItemsAdderItem(anyString())).thenAnswer(i -> new ItemStack(Material.ARROW));
        tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        base = mock(BaseSet.class); when(base.getId()).thenReturn("iron");
        baseLoader = mockStatic(BaseSetLoader.class); baseLoader.when(() -> BaseSetLoader.getByString("iron")).thenReturn(base);
        categories = new ArrayList<>(); categoryLoader = mockStatic(CategoryLoader.class);
        categoryLoader.when(CategoryLoader::get).thenAnswer(i -> categories);
        categoryLoader.when(() -> CategoryLoader.getByName(anyString())).thenAnswer(i -> categories.stream().filter(c -> c.getName().equalsIgnoreCase(i.getArgument(0))).findFirst().orElse(null));
        categoryLoader.when(() -> CategoryLoader.getByContainsSet(anyString())).thenAnswer(i -> categories.stream().flatMap(c -> c.getSets().stream()).filter(s -> s != null && s.getId().equals(i.getArgument(0))).findFirst().orElse(null));
        menus = new InventoryManager(); listener = new SkinManager(); player.openInventory(server.createInventory(null, 9));
    }
    @AfterEach void teardown() {
        categoryLoader.close(); baseLoader.close(); tlibs.close(); Cache.points = oldPoints; Cache.itemPoints = oldItemPoints;
        player.openInventory(server.createInventory(null, 9)); MockBukkit.unmock(); ArmourShop.plugin = previousPlugin;
    }
    private SkinCategory category(String id, boolean item, String permission) {
        var yaml = new YamlConfiguration(); yaml.set("name", id); yaml.set("item", "icon"); yaml.set("is-item", item); yaml.set("permission", permission);
        var cat = new SkinCategory(id, yaml); categories.add(cat); return cat;
    }
    private SkinSet skin(SkinCategory cat, String id, String permission, String scroll, boolean named, String... pieces) {
        var yaml = new YamlConfiguration(); yaml.set("name", id); yaml.set("set", "iron"); yaml.set("permission", permission == null ? "none" : permission);
        yaml.set("scroll", scroll); yaml.set("add-name", named);
        for (String piece : pieces) yaml.set(piece, "path_" + piece);
        var skin = new SkinSet(id, yaml); cat.addSet(skin); return skin;
    }
    private Inventory top() { return player.getOpenInventory().getTopInventory(); }
    private InventoryClickEvent click(int rawSlot) {
        return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }
    private ItemStack tagged(String value) {
        var item = new ItemStack(Material.PAPER); var meta = item.getItemMeta(); meta.setDisplayName("Skin");
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "set"), PersistentDataType.STRING, value); item.setItemMeta(meta); return item;
    }

    @Test void typeAndCategoryMenusRenderIconsAndRespectCategoryPermissions() {
        var armor = category("Armor", false, null); var items = category("Items", true, null);
        category("Hidden", false, "hidden"); var allowed = category("Allowed", false, "allowed");
        player.addAttachment(plugin, "allowed", true);
        menus.typeView(player); assertEquals(9, top().getSize()); assertEquals(Material.IRON_CHESTPLATE, top().getItem(0).getType());
        assertEquals(Material.IRON_SWORD, top().getItem(1).getType());
        var holder = (ASInventoryHolder) top().getHolder(); assertFalse(holder.isItem()); assertNull(holder.getInventory());
        menus.categoryView(player, false); assertEquals("Armor", top().getItem(0).getItemMeta().getDisplayName());
        assertEquals("Allowed", top().getItem(1).getItemMeta().getDisplayName()); assertEquals(Material.BARRIER, top().getItem(53).getType());
        menus.categoryView(player, true); assertEquals("Items", top().getItem(0).getItemMeta().getDisplayName()); assertTrue(((ASInventoryHolder) top().getHolder()).isItem());
        when(creator.getItemFromPath("icon")).thenReturn(null); assertEquals(Material.DIRT, menus.createCategoryItem(armor).getType());
        assertTrue(menus.createCategoryItem(items).getItemMeta().getLore().getFirst().contains("Items"));
        assertTrue(menus.createCategoryItem(allowed).getItemMeta().getLore().getFirst().contains("Armor Sets"));
    }

    @Test void categoriesStopAtInventoryCapacityAndSkipDeniedOppositeKinds() {
        category("Denied", false, "no"); category("Opposite", true, null); category("StillDenied", false, "no");
        menus.categoryView(player, false); assertNull(top().getItem(0));
        categories.clear(); for (int i = 0; i < 60; i++) category("Category" + i, false, null);
        assertDoesNotThrow(() -> menus.categoryView(player, false)); assertEquals(Material.BARRIER, top().getItem(53).getType());
    }

    @Test void skinViewsPackVisibleEntriesRenderPiecesAndNavigatePages() {
        var cat = category("Armor", false, null); cat.addSet(null);
        skin(cat, "Denied", "denied", null, false, "helmet");
        var bad = mock(SkinSet.class); when(bad.getId()).thenReturn("broken"); cat.addSet(bad);
        skin(cat, "First", null, null, false, "helmet", "chestplate", "leggings", "boots");
        skin(cat, "Second", null, null, false, "helmet"); skin(cat, "Third", null, null, false, "helmet");
        menus.skinView(player, cat, -1, false);
        assertTrue(top().getItem(9).getItemMeta().getDisplayName().contains("First Helmet"));
        assertTrue(top().getItem(10).getItemMeta().getDisplayName().contains("First Chestplate"));
        assertTrue(top().getItem(11).getItemMeta().getDisplayName().contains("First Leggings"));
        assertTrue(top().getItem(12).getItemMeta().getDisplayName().contains("First Boots"));
        assertTrue(top().getItem(18).getItemMeta().getDisplayName().contains("Second"));
        assertEquals(Material.ARROW, top().getItem(5).getType()); assertEquals(Material.BARRIER, top().getItem(4).getType());
        menus.skinView(player, cat, 1, false); assertTrue(top().getItem(9).getItemMeta().getDisplayName().contains("Third"));
        assertEquals(1, top().getItem(3).getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "page"), PersistentDataType.INTEGER));
        var items = category("Items", true, null); skin(items, "Item", null, null, false, "item");
        menus.skinView(player, items, 0, true); assertTrue(top().getItem(9).getItemMeta().getDisplayName().contains("Item"));
        when(creator.getItemFromPath(anyString())).thenReturn(null); menus.skinView(player, cat, 0, false); assertNull(top().getItem(9));
        menus.skinView(player, items, 0, true); assertNull(top().getItem(9));
    }

    @Test void skinIconsHandleInvalidPathsLocalModelsGunskinsAndScrollLabels() {
        var cat = category("Items", true, null); var skin = skin(cat, "Fancy", null, "scroll", true, "item");
        assertNull(menus.createSkinItem(null, "path", ArmorType.ITEM)); assertNull(menus.createSkinItem(skin, null, ArmorType.ITEM));
        assertNull(menus.createSkinItem(skin, " ", ArmorType.ITEM)); assertNull(menus.createSkinItem(mock(SkinSet.class), "path", ArmorType.ITEM));
        ItemStack item = menus.createSkinItem(skin, "localmodel(stick.45)", ArmorType.ITEM);
        assertEquals(Material.STICK, item.getType()); assertEquals(45, item.getItemMeta().getCustomModelData());
        assertEquals("Fancy.item", item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "set"), PersistentDataType.STRING));
        assertEquals(Material.DIRT, menus.createSkinItem(skin, "localmodel(no_such_material.4)", ArmorType.ITEM).getType());
        try (var guns = mockStatic(SkinLoader.class)) {
            assertEquals(Material.DIRT, menus.createSkinItem(skin, "gunskin(missing)", ArmorType.ITEM).getType());
            var gun = mock(SkinData.class); when(gun.parseModel(SkinState.CARRY)).thenReturn(new ItemStack(Material.CROSSBOW));
            guns.when(() -> SkinLoader.getByString("rifle")).thenReturn(gun);
            assertEquals(Material.CROSSBOW, menus.createSkinItem(skin, "gunskin(rifle)", ArmorType.ITEM).getType());
        }
        when(creator.getItemFromPath("missing")).thenReturn(null); assertNull(menus.createSkinItem(skin, "missing", ArmorType.ITEM));
        when(creator.getItemFromPath("air")).thenReturn(new ItemStack(Material.AIR)); assertNull(menus.createSkinItem(skin, "air", ArmorType.ITEM));
        var noMeta = mock(ItemStack.class); when(noMeta.getType()).thenReturn(Material.STICK); when(creator.getItemFromPath("custom-no-meta")).thenReturn(noMeta);
        assertNull(menus.createSkinItem(skin, "custom-no-meta", ArmorType.ITEM));
        var scroll = new ItemStack(Material.PAPER); var meta = scroll.getItemMeta(); meta.setDisplayName("Special Scroll"); scroll.setItemMeta(meta);
        when(creator.getItemFromPath("scroll")).thenReturn(scroll);
        assertTrue(menus.createSkinItem(skin, "path", ArmorType.ITEM).getItemMeta().getLore().contains("§7Scroll: Special Scroll"));
        when(creator.getItemFromPath("scroll")).thenReturn(null);
        assertTrue(menus.createSkinItem(skin, "path", ArmorType.ITEM).getItemMeta().getLore().contains("§7Scroll: scroll"));
    }

    @Test void categoryAndTypeClicksNavigateOnlyValidEntries() {
        var cat = category("Armor", false, null); skin(cat, "Skin", null, null, false, "helmet");
        menus.categoryView(player, false); var event = click(53); listener.invenClick(event); assertTrue(event.isCancelled());
        assertEquals("§7Armourshop Type", player.getOpenInventory().getTitle());
        listener.invenClick(click(1)); assertTrue(((ASInventoryHolder) top().getHolder()).isItem());
        menus.typeView(player); listener.invenClick(click(0)); assertFalse(((ASInventoryHolder) top().getHolder()).isItem());
        listener.invenClick(click(0)); assertEquals("Armor", player.getOpenInventory().getTitle());
        listener.invenClick(click(0)); assertEquals("Armor", player.getOpenInventory().getTitle());
        listener.invenClick(click(4)); assertEquals("§7Armourshop Categories", player.getOpenInventory().getTitle());
        top().setItem(2, new ItemStack(Material.PAPER)); assertDoesNotThrow(() -> listener.invenClick(click(2)));
        listener.invenClick(click(10)); listener.invenClick(click(-999));
        player.openInventory(server.createInventory(null, 9, "Other")); top().setItem(0, new ItemStack(Material.PAPER));
        var unrelated = click(0); listener.invenClick(unrelated); assertFalse(unrelated.isCancelled());
        player.openInventory(server.createInventory(new ASInventoryHolder(false), 9, "Unknown")); top().setItem(0, new ItemStack(Material.PAPER));
        assertDoesNotThrow(() -> listener.invenClick(click(0)));
    }

    @Test void validPageButtonsSelectAdjacentPages() {
        var cat = category("Items", true, null);
        for (int i = 0; i < 3; i++) skin(cat, "skin" + i, null, null, false, "item");
        menus.skinView(player, cat, 0, true); listener.invenClick(click(5));
        assertTrue(top().getItem(9).getItemMeta().getDisplayName().contains("skin2"));
        listener.invenClick(click(3)); assertTrue(top().getItem(9).getItemMeta().getDisplayName().contains("skin0"));
    }

    @Test void matchingInventoryItemsReceiveEachPieceAndOptionalName() {
        var cat = category("Armor", false, null); var set = skin(cat, "skin", null, null, true, "helmet", "chestplate", "leggings", "boots", "item");
        for (ArmorType type : ArmorType.values()) {
            player.getInventory().clear(); player.getInventory().setItem(2, new ItemStack(Material.STICK));
            when(base.contains(nullable(ItemStack.class), eq(type))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.STICK);
            when(merger.merge(any(ItemStack.class), any(), eq("path_" + type.name().toLowerCase(Locale.ROOT)))).thenReturn(new ItemStack(Material.DIAMOND));
            menus.skinView(player, cat, 0, false); top().setItem(9, tagged("skin." + type.name().toLowerCase(Locale.ROOT)));
            listener.invenClick(click(9)); assertEquals(Material.DIAMOND, player.getInventory().getItem(2).getType());
        }
    }

    @Test void applyingRequiresUsableMetadataScrollAndMatchingBaseItem() {
        var cat = category("Items", true, null); skin(cat, "skin", null, "scroll", false, "item"); menus.skinView(player, cat, 0, true);
        top().setItem(9, new ItemStack(Material.PAPER)); listener.invenClick(click(9)); verifyNoInteractions(merger);
        top().setItem(9, tagged("unknown.item")); listener.invenClick(click(9)); verifyNoInteractions(merger);
        top().setItem(9, tagged("skin.item")); listener.invenClick(click(9)); assertTrue(player.nextMessage().contains("right scroll"));
        var scroll = new ItemStack(Material.PAPER, 2); player.getInventory().setItem(1, scroll);
        when(checker.checkItemWithPath(nullable(ItemStack.class), eq("scroll"))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.PAPER);
        listener.invenClick(click(9)); assertTrue(player.nextMessage().contains("carry nothing"));
        player.getInventory().setItem(2, new ItemStack(Material.STICK));
        when(base.contains(nullable(ItemStack.class), eq(ArmorType.ITEM))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.STICK);
        when(merger.merge(any(), eq(Optional.empty()), eq("path_item"))).thenReturn(new ItemStack(Material.DIAMOND));
        listener.invenClick(click(9)); assertEquals(1, player.getInventory().getItem(1).getAmount()); assertEquals(Material.DIAMOND, player.getInventory().getItem(2).getType());
    }

    @Test void placingCursorItemsInEmptyShopSlotsIsCancelled() {
        var cat = category("Items", true, null); menus.skinView(player, cat, 0, true);
        var carried = new ItemStack(Material.DIAMOND, 3); player.setItemOnCursor(carried);
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            9, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertNull(event.getCurrentItem()); assertEquals(carried, event.getCursor());
        listener.invenClick(event);
        assertTrue(event.isCancelled(), "the menu must reject placing real items into empty display slots");
        assertEquals(carried, player.getItemOnCursor()); assertNull(top().getItem(9));
    }

    @Test void playerInventorySlotsNeverTriggerShopPageActions() {
        var cat = category("Items", true, null); skin(cat, "skin", null, null, false, "item"); menus.skinView(player, cat, 0, true);
        for (int slot : List.of(3, 5)) {
            player.getInventory().setItem(slot, new ItemStack(Material.STICK));
            // MockBukkit getItem(rawSlot) omits hotbar conversion; emulate Bukkit's current item.
            var event = spy(click(top().getSize() + 27 + slot));
            doReturn(player.getInventory().getItem(slot)).when(event).getCurrentItem();
            assertNotNull(event.getCurrentItem());
            assertEquals(slot, event.getSlot()); assertSame(player.getInventory(), event.getClickedInventory());
            assertDoesNotThrow(() -> listener.invenClick(event)); assertEquals("Items", player.getOpenInventory().getTitle());
        }
    }

    @Test void missingPageMetadataDoesNotCrashMenuClick() {
        var cat = category("Items", true, null); menus.skinView(player, cat, 0, true); top().setItem(3, new ItemStack(Material.ARROW));
        assertDoesNotThrow(() -> listener.invenClick(click(3)));
        top().setItem(5, new ItemStack(Material.ARROW)); assertDoesNotThrow(() -> listener.invenClick(click(5)));
    }

    @Test void malformedSkinMetadataCannotCrashOrApplyAnUnrelatedSet() {
        var cat = category("Items", true, null); skin(cat, "skin", null, null, false, "item"); menus.skinView(player, cat, 0, true);
        for (String tag : List.of("skin", "skin.unknown", "skin.item.extra")) {
            top().setItem(9, tagged(tag)); assertDoesNotThrow(() -> listener.invenClick(click(9)));
        }
        verifyNoInteractions(merger);
    }

    @Test void permissionRevocationAfterOpeningMenuPreventsApplyingSkin() {
        var cat = category("Items", true, null); skin(cat, "skin", "skin.owned", null, false, "item");
        var permission = player.addAttachment(plugin, "skin.owned", true); menus.skinView(player, cat, 0, true);
        permission.unsetPermission("skin.owned"); player.recalculatePermissions(); player.getInventory().setItem(2, new ItemStack(Material.STICK));
        when(base.contains(nullable(ItemStack.class), eq(ArmorType.ITEM))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.STICK);
        when(merger.merge(any(), any(), anyString())).thenReturn(new ItemStack(Material.DIAMOND));
        listener.invenClick(click(9)); assertEquals(Material.STICK, player.getInventory().getItem(2).getType()); verifyNoInteractions(merger);
    }

    @Test void failedMergePreservesItemAndDoesNotConsumeScroll() {
        var cat = category("Items", true, null); skin(cat, "skin", null, "scroll", false, "item"); menus.skinView(player, cat, 0, true);
        player.getInventory().setItem(1, new ItemStack(Material.PAPER, 2)); player.getInventory().setItem(2, new ItemStack(Material.STICK));
        when(checker.checkItemWithPath(nullable(ItemStack.class), eq("scroll"))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.PAPER);
        when(base.contains(nullable(ItemStack.class), eq(ArmorType.ITEM))).thenAnswer(i -> i.getArgument(0) != null && ((ItemStack) i.getArgument(0)).getType() == Material.STICK);
        when(merger.merge(any(), any(), anyString())).thenReturn(null, new ItemStack(Material.AIR)).thenAnswer(i -> {
            i.<ItemStack>getArgument(0).setType(Material.DIRT); throw new IllegalStateException("bad model");
        });
        for (int failure = 0; failure < 3; failure++) {
            assertDoesNotThrow(() -> listener.invenClick(click(9))); assertNotNull(player.getInventory().getItem(2));
            assertEquals(Material.STICK, player.getInventory().getItem(2).getType()); assertEquals(2, player.getInventory().getItem(1).getAmount());
        }
    }

    @Test void malformedConfiguredModelPathsFailWithoutThrowing() {
        var cat = category("Items", true, null); var skin = skin(cat, "skin", null, null, false, "item");
        assertAll(
            () -> assertDoesNotThrow(() -> menus.createSkinItem(skin, "localmodel", ArmorType.ITEM)),
            () -> assertDoesNotThrow(() -> menus.createSkinItem(skin, "gunskin", ArmorType.ITEM)));
    }

    @Test void fixesMmoItemsAdderStacksOnlyWhenNamespaceTagMissing() {
        player.openInventory(server.createInventory(null, 9)); var original = new ItemStack(Material.PAPER); top().setItem(0, original);
        try (var nbt = mockStatic(NBTItem.class); var custom = mockStatic(CustomStack.class)) {
            var wrapper = mock(NBTItem.class); nbt.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(wrapper);
            listener.fixItem(click(1)); listener.fixItem(click(0)); custom.verifyNoInteractions();
            when(wrapper.hasType()).thenReturn(true); listener.fixItem(click(0)); verify(wrapper, never()).addTag(any(io.lumine.mythic.lib.api.item.ItemTag[].class));
            var stack = mock(CustomStack.class); when(stack.getNamespace()).thenReturn("namespace"); when(stack.getId()).thenReturn("item");
            custom.when(() -> CustomStack.byItemStack(any(ItemStack.class))).thenReturn(stack);
            when(wrapper.hasTag("ia")).thenReturn(true); listener.fixItem(click(0)); verify(wrapper, never()).addTag(any(io.lumine.mythic.lib.api.item.ItemTag[].class));
            when(wrapper.hasTag("ia")).thenReturn(false); when(wrapper.toItem()).thenReturn(new ItemStack(Material.DIAMOND));
            listener.fixItem(click(0)); assertEquals(Material.DIAMOND, top().getItem(0).getType()); verify(wrapper).addTag(any(io.lumine.mythic.lib.api.item.ItemTag[].class));
        }
    }
}

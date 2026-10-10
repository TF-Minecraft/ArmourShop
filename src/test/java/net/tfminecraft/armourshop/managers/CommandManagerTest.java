package net.tfminecraft.armourshop.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.*;
import net.tfminecraft.armourshop.loaders.CategoryLoader;
import net.tfminecraft.armourshop.objects.*;
import net.tfminecraft.armourshop.pack.apply.*;
import net.tfminecraft.armourshop.pack.catalog.CatalogSyncService;
import net.tfminecraft.armourshop.pack.delete.*;
import net.tfminecraft.armourshop.pack.reload.DeferredIaReloadService;
import net.tfminecraft.armourshop.utils.ChatMessages;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ArmorMerger;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

class CommandManagerTest {
    private ServerMock server;
    private PlayerMock player;
    private ArmourShop plugin, oldPlugin;
    private Logger log;
    private CommandSender console;
    private Command command;
    private CommandManager manager;
    private BukkitScheduler scheduler;
    private ArmorMerger merger;
    private Deque<Runnable> workers, main;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<JavaPlugin> plugins;
    private MockedStatic<TLibs> tlibs;
    private MockedStatic<CategoryLoader> categories;
    private MockedStatic<ProvinceSystemClient> client;

    @BeforeEach void setup() {
        server = MockBukkit.mock(); player = server.addPlayer(); oldPlugin = ArmourShop.plugin;
        plugin = mock(ArmourShop.class); log = mock(Logger.class); when(plugin.getName()).thenReturn("ArmourShop"); when(plugin.namespace()).thenReturn("armourshop");
        when(plugin.getServer()).thenReturn(server); when(plugin.getLogger()).thenReturn(log); when(plugin.isEnabled()).thenReturn(true); ArmourShop.plugin = plugin;
        player.addAttachment(plugin, "armourshop.admin", true); player.openInventory(server.createInventory(null, 9));
        console = mock(CommandSender.class); when(console.hasPermission("armourshop.admin")).thenReturn(true);
        command = mock(Command.class); when(command.getName()).thenReturn("armourshop"); manager = new CommandManager();
        scheduler = mock(BukkitScheduler.class); workers = new ArrayDeque<>(); main = new ArrayDeque<>();
        bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(i -> { workers.add(i.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> { main.add(i.getArgument(1)); return mock(BukkitTask.class); });
        plugins = mockStatic(JavaPlugin.class); plugins.when(() -> JavaPlugin.getPlugin(ArmourShop.class)).thenReturn(plugin);
        var api = mock(ItemAPI.class); merger = mock(ArmorMerger.class); when(api.getArmorMerger()).thenReturn(merger);
        tlibs = mockStatic(TLibs.class); tlibs.when(TLibs::getItemAPI).thenReturn(api);
        categories = mockStatic(CategoryLoader.class); categories.when(CategoryLoader::get).thenReturn(List.of());
        client = mockStatic(ProvinceSystemClient.class);
    }
    @AfterEach void restore() {
        client.close(); categories.close(); tlibs.close(); plugins.close(); bukkit.close();
        player.openInventory(server.createInventory(null, 9)); MockBukkit.unmock(); ArmourShop.plugin = oldPlugin;
    }
    private boolean run(CommandSender sender, String... args) { return manager.onCommand(sender, command, "as", args); }
    private void drain() { while (!workers.isEmpty()) workers.removeFirst().run(); while (!main.isEmpty()) main.removeFirst().run(); }
    private SkinSet set(String id) { var set = mock(SkinSet.class); when(set.getId()).thenReturn(id); when(set.getName()).thenReturn("Fancy"); return set; }
    private SkinCategory category(String id, SkinSet... sets) {
        var cat = mock(SkinCategory.class); when(cat.getId()).thenReturn(id); when(cat.getSets()).thenReturn(Arrays.asList(sets));
        categories.when(() -> CategoryLoader.getByString(id)).thenReturn(cat); return cat;
    }
    private void held(Material type, int amount) { player.getInventory().setItemInMainHand(new ItemStack(type, amount)); }
    private String message() { return player.nextMessage(); }

    @Test void recognizesOnlyOwnCommandAndOpensPlayerMenuWithoutArguments() {
        when(command.getName()).thenReturn("other"); assertFalse(run(console, "reload"));
        when(command.getName()).thenReturn("ArmourShop"); assertFalse(run(console)); assertTrue(run(player));
        assertEquals("§7Armourshop Type", player.getOpenInventory().getTitle()); assertFalse(run(console, "unknown"));
    }

    @Test void reloadAndAdministrativeOperationsEnforcePermissions() {
        assertTrue(run(player, "reload")); verify(plugin).reloadMessage(player);
        assertTrue(run(console, "reload")); verify(plugin).reload();
        var denied = mock(CommandSender.class);
        for (String[] args : List.of(new String[]{"reload"}, new String[]{"token", "delete", "code"}, new String[]{"listtokens"},
            new String[]{"pack", "pull"}, new String[]{"pack", "sync"}, new String[]{"catalog", "sync"}, new String[]{"submission", "delete", "id"},
            new String[]{"skin", "delete", "id"}, new String[]{"model", "apply", "cat", "skin"})) assertTrue(run(denied, args));
        assertTrue(workers.isEmpty());
        player.addAttachment(plugin, "armourshop.admin", false); assertTrue(run(player, "reload")); assertTrue(message().contains("do not have access"));
    }

    @Test void usageAndTokenMigrationMessagesGuidePlayers() {
        assertTrue(run(player, "token", "create")); assertTrue(message().contains("/token create skin"));
        for (String[] args : List.of(new String[]{"token", "delete"}, new String[]{"submission", "delete"}, new String[]{"skin", "delete"}, new String[]{"model"}, new String[]{"model", "unknown"})) {
            assertTrue(run(player, args)); assertTrue(message().contains("Usage:"));
        }
        assertTrue(run(player, "model", "apply", "cat", "skin")); assertTrue(message().contains("Hold the item"));
        assertTrue(run(console, "model", "apply", "cat", "skin")); verify(console).sendMessage(contains("Only a player"));
    }

    @Test void deletesSubmissionAndStaffSkinOnWorkerThenRepliesOnMain() {
        try (var submissions = mockStatic(SubmissionDeleteRunner.class); var skins = mockStatic(SkinDeleteRunner.class);
             var submissionCache = mockStatic(DeletableSubmissionCache.class); var skinCache = mockStatic(DeletableStaffSkinCache.class)) {
            submissions.when(() -> SubmissionDeleteRunner.run("one")).thenReturn("submission removed");
            skins.when(() -> SkinDeleteRunner.run("two")).thenReturn("skin removed");
            assertTrue(run(console, "submission", "delete", "one")); assertEquals(1, workers.size()); submissions.verifyNoInteractions();
            drain(); submissions.verify(() -> SubmissionDeleteRunner.run("one")); submissionCache.verify(DeletableSubmissionCache::invalidate);
            verify(console).sendMessage(contains("submission removed"));
            assertTrue(run(console, "skin", "delete", "two")); drain(); skins.verify(() -> SkinDeleteRunner.run("two")); skinCache.verify(DeletableStaffSkinCache::invalidate);
            verify(console).sendMessage(contains("skin removed"));
        }
    }

    @Test void tokenDeletionNormalizesQuotedCodeAndReportsApiOutcomes() {
        for (String code : Arrays.asList(null, " ", "\" \"")) { assertTrue(run(player, "token", "delete", code)); assertTrue(message().contains("Usage:")); }
        client.when(() -> ProvinceSystemClient.revokeCode("code")).thenReturn(SimpleResult.success(), SimpleResult.fail(null), SimpleResult.fail("rejected"));
        assertTrue(run(player, "token", "delete", " \" code \" ")); drain(); assertTrue(message().contains("Deleted token"));
        run(player, "token", "delete", "code"); drain(); assertTrue(message().contains("Could not delete token"));
        run(player, "token", "delete", "code"); drain(); assertTrue(message().contains("rejected"));
        client.verify(() -> ProvinceSystemClient.revokeCode("code"), times(3));
    }

    @Test void tokenListsUseNamesOfflineLookupAndUuidFallbacks() {
        client.when(ProvinceSystemClient::listActiveCodes).thenReturn(ActiveCodesResult.fail(null), ActiveCodesResult.fail("offline"), ActiveCodesResult.success(List.of()));
        run(console, "listtokens"); drain(); verify(console).sendMessage(contains("Could not list tokens"));
        run(console, "listtokens"); drain(); verify(console).sendMessage(contains("offline"));
        run(console, "listtokens"); drain(); verify(console).sendMessage(contains("No active unused tokens"));
        UUID known = UUID.randomUUID(), unknown = UUID.randomUUID(); var offline = mock(OfflinePlayer.class); when(offline.getName()).thenReturn("Cached");
        var nameless = mock(OfflinePlayer.class); when(nameless.getName()).thenReturn(" ");
        bukkit.when(() -> Bukkit.getOfflinePlayer(known)).thenReturn(offline); bukkit.when(() -> Bukkit.getOfflinePlayer(unknown)).thenReturn(nameless);
        List<ActiveCode> codes = List.of(new ActiveCode("one", null, " Named ", null, null), new ActiveCode("two", null, null, null, null),
            new ActiveCode("three", " " + known + " ", " ", null, null), new ActiveCode("four", "not-a-uuid", null, null, null), new ActiveCode("five", unknown.toString(), null, null, null));
        client.when(ProvinceSystemClient::listActiveCodes).thenReturn(ActiveCodesResult.success(codes));
        run(console, "listtokens"); drain(); verify(console).sendMessage(contains("Active tokens (5)")); verify(console).sendMessage(contains("Cached"));
        try (var chat = mockStatic(ChatMessages.class)) {
            run(player, "listtokens"); drain(); chat.verify(() -> ChatMessages.sendTokenListLine(player, "one", "Named"));
            chat.verify(() -> ChatMessages.sendTokenListLine(player, "two", "?")); chat.verify(() -> ChatMessages.sendTokenListLine(player, "three", "Cached"));
            chat.verify(() -> ChatMessages.sendTokenListLine(player, "four", "not-a-uuid")); chat.verify(() -> ChatMessages.sendTokenListLine(player, "five", unknown.toString()));
        }
    }

    @Test void catalogAndPackSyncReportBothSuccessAndFailure() {
        var reload = mock(DeferredIaReloadService.class); when(plugin.getDeferredIaReloadService()).thenReturn(reload);
        when(reload.pruneQueueToApproved(log)).thenReturn(DeferredIaReloadService.SyncResult.ok(4, 2), DeferredIaReloadService.SyncResult.failed(null), DeferredIaReloadService.SyncResult.failed("offline"));
        run(console, "pack", "sync"); drain(); verify(console).sendMessage(contains("4 → 2"));
        run(console, "pack", "sync"); drain(); verify(console).sendMessage(contains("Pack sync failed: unknown error"));
        run(console, "pack", "sync"); drain(); verify(console).sendMessage(contains("Pack sync failed: offline"));
        try (var catalog = mockStatic(CatalogSyncService.class)) {
            catalog.when(CatalogSyncService::pushNow).thenReturn(CatalogPushResult.success(2, 3, 4, "now"), CatalogPushResult.fail("offline"));
            run(console, "catalog", "sync"); drain(); verify(console).sendMessage(contains("categories=2 skin_sets=3 scrolls=4"));
            run(console, "catalog", "sync"); drain(); verify(console).sendMessage(contains("Catalog sync failed: offline"));
        }
    }

    @Test void packPullReportsBusyFailureAndTruncatesLongSuccessfulDetails() {
        try (var pulls = mockStatic(PackPullRunner.class)) {
            pulls.when(() -> PackPullRunner.run(eq(true), any())).thenAnswer(i -> { i.<Consumer<PackPullRunner.PullResult>>getArgument(1).accept(PackPullRunner.PullResult.skippedBusy()); return false; });
            run(console, "pack", "pull"); verify(console, times(2)).sendMessage(contains("Pack pull already running"));
            pulls.when(() -> PackPullRunner.run(eq(true), any())).thenAnswer(i -> { i.<Consumer<PackPullRunner.PullResult>>getArgument(1).accept(PackPullRunner.PullResult.failed("offline")); return true; });
            run(console, "pack", "pull"); verify(console).sendMessage(contains("Pack pull failed: offline"));
        }
        try (var apply = mockStatic(PackApplyService.class)) {
            List<String> messages = new ArrayList<>(); for (int i = 0; i < 10; i++) messages.add("detail " + i);
            apply.when(() -> PackApplyService.pullAndWrite(log)).thenReturn(new PackApplyService.ApplySummary(0, 0, 0, messages, List.of()), new PackApplyService.ApplySummary(0, 0, 0, List.of(), List.of()));
            run(console, "pack", "pull"); drain(); verify(console).sendMessage(contains("2 more (see console)"));
            verify(console).sendMessage("§7detail 7"); verify(console, never()).sendMessage("§7detail 8");
            run(console, "pack", "pull"); drain(); assertFalse(PackPullRunner.isRunning());
        }
    }

    @Test void modelApplyReportsUnknownCategorySkinAndMissingPieces() {
        held(Material.STICK, 1); run(player, "model", "apply", "missing", "skin"); assertTrue(message().contains("Unknown category"));
        var other = set("other"); category("cat", other);
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Unknown skin"));
        run(player, "model", "apply", "cat", "other"); assertTrue(message().contains("Pieces: none"));
        when(other.hasHelmet()).thenReturn(true); when(other.hasChestplate()).thenReturn(true); when(other.hasLeggings()).thenReturn(true); when(other.hasBoots()).thenReturn(true);
        run(player, "model", "apply", "cat", "other"); assertTrue(message().contains("helmet, chestplate, leggings, boots"));
    }

    @Test void modelApplySelectsCorrectArmorPieceAndPreservesStackSize() {
        var set = set("skin"); category("cat", set);
        when(set.hasHelmet()).thenReturn(true); when(set.getHelmet()).thenReturn("helmet");
        when(set.hasChestplate()).thenReturn(true); when(set.getChestplate()).thenReturn("chestplate");
        when(set.hasLeggings()).thenReturn(true); when(set.getLeggings()).thenReturn("leggings");
        when(set.hasBoots()).thenReturn(true); when(set.getBoots()).thenReturn("boots");
        for (Material material : List.of(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS)) {
            String path = material.name().substring(5).toLowerCase(Locale.ROOT); held(material, 2);
            when(merger.merge(any(), eq(Optional.empty()), eq(path))).thenReturn(new ItemStack(Material.DIAMOND));
            run(player, "model", "apply", "cat", "SKIN"); assertTrue(message().contains("Applied skin"));
            assertEquals(2, player.getInventory().getItemInMainHand().getAmount()); assertEquals(Material.DIAMOND, player.getInventory().getItemInMainHand().getType());
            verify(merger).merge(any(), eq(Optional.empty()), eq(path));
        }
        when(set.hasItem()).thenReturn(true); when(set.getItem()).thenReturn("item"); when(set.addName()).thenReturn(true);
        held(Material.STICK, 1); when(merger.merge(any(), eq(Optional.of("Fancy")), eq("item"))).thenReturn(new ItemStack(Material.EMERALD));
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Applied skin")); assertEquals(Material.EMERALD, player.getInventory().getItemInMainHand().getType());
    }

    @Test void modelFailuresLeaveOriginalItemUntouched() {
        var set = set("skin"); when(set.hasItem()).thenReturn(true); when(set.getItem()).thenReturn("item"); category("cat", set); held(Material.STICK, 3);
        when(merger.merge(any(), any(), anyString())).thenThrow(new IllegalStateException("bad model"));
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Could not apply")); verify(log).warning(contains("bad model"));
        doReturn(null, new ItemStack(Material.AIR)).when(merger).merge(any(), any(), anyString());
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Could not apply"));
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Could not apply"));
        assertEquals(Material.STICK, player.getInventory().getItemInMainHand().getType()); assertEquals(3, player.getInventory().getItemInMainHand().getAmount());
    }

    @Test void bookModelsPreserveWritingLoreTagsAndOptionalCustomName() {
        var set = set("skin"); when(set.hasItem()).thenReturn(true); when(set.getItem()).thenReturn("item"); category("cat", set);
        var original = new ItemStack(Material.WRITTEN_BOOK); var old = (BookMeta) original.getItemMeta();
        old.setPages("Original page"); old.setTitle("Title"); old.setAuthor("Author"); old.setGeneration(BookMeta.Generation.COPY_OF_ORIGINAL);
        old.setDisplayName("Personal name"); old.setLore(List.of("Lore")); old.getPersistentDataContainer().set(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING, "owner"); original.setItemMeta(old);
        player.getInventory().setItemInMainHand(original); when(merger.merge(any(), any(), anyString())).thenReturn(new ItemStack(Material.WRITTEN_BOOK));
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Applied"));
        var actual = (BookMeta) player.getInventory().getItemInMainHand().getItemMeta();
        assertEquals(List.of("Original page"), actual.getPages()); assertEquals("Title", actual.getTitle()); assertEquals("Author", actual.getAuthor());
        assertEquals(BookMeta.Generation.COPY_OF_ORIGINAL, actual.getGeneration()); assertEquals("Personal name", actual.getDisplayName()); assertEquals(List.of("Lore"), actual.getLore());
        assertEquals("owner", actual.getPersistentDataContainer().get(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING));
        when(set.addName()).thenReturn(true); var renamed = new ItemStack(Material.WRITTEN_BOOK); var meta = renamed.getItemMeta(); meta.setDisplayName("Skin name"); renamed.setItemMeta(meta);
        when(merger.merge(any(), any(), anyString())).thenReturn(renamed); run(player, "model", "apply", "cat", "skin"); message();
        assertEquals("Skin name", player.getInventory().getItemInMainHand().getItemMeta().getDisplayName());
        player.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK)); when(merger.merge(any(), any(), anyString())).thenReturn(new ItemStack(Material.PAPER));
        run(player, "model", "apply", "cat", "skin"); message(); assertEquals(Material.PAPER, player.getInventory().getItemInMainHand().getType());
    }

    @Test void originalBooksNeverTakeTheTemplatesTatteredGeneration() {
        var set = set("skin"); when(set.hasItem()).thenReturn(true); when(set.getItem()).thenReturn("item"); category("cat", set);
        var original = new ItemStack(Material.WRITTEN_BOOK); var old = (BookMeta) original.getItemMeta();
        old.setPages("Original page"); old.setTitle("Title"); old.setAuthor("Author"); original.setItemMeta(old);
        player.getInventory().setItemInMainHand(original);
        var template = new ItemStack(Material.WRITTEN_BOOK); var tattered = (BookMeta) template.getItemMeta();
        tattered.setGeneration(BookMeta.Generation.TATTERED); template.setItemMeta(tattered);
        when(merger.merge(any(), any(), anyString())).thenReturn(template);
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Applied"));
        assertNotEquals(BookMeta.Generation.TATTERED, ((BookMeta) player.getInventory().getItemInMainHand().getItemMeta()).getGeneration());
        player.getInventory().setItemInMainHand(new ItemStack(Material.WRITABLE_BOOK)); when(merger.merge(any(), any(), anyString())).thenReturn(new ItemStack(Material.WRITABLE_BOOK));
        run(player, "model", "apply", "cat", "skin"); assertTrue(message().contains("Applied"));
        assertEquals(Material.WRITABLE_BOOK, player.getInventory().getItemInMainHand().getType());
    }

    @Test void bookModelsStillRestoreWritingWhenLegacyPdcCopyIsUnsupported() {
        var set = set("skin"); when(set.hasItem()).thenReturn(true); when(set.getItem()).thenReturn("item"); category("cat", set);
        for (Throwable failure : List.of(new UnsupportedOperationException("old server"), new NoSuchMethodError("copyTo"))) {
            var old = mock(BookMeta.class); when(old.clone()).thenReturn(old); when(old.getPages()).thenReturn(List.of("Legacy page"));
            var data = mock(PersistentDataContainer.class); when(old.getPersistentDataContainer()).thenReturn(data);
            doThrow(failure).when(data).copyTo(any(PersistentDataContainer.class), eq(true));
            var held = mock(ItemStack.class); when(held.getType()).thenReturn(Material.WRITTEN_BOOK); when(held.getAmount()).thenReturn(1);
            when(held.getItemMeta()).thenReturn(old); when(held.clone()).thenReturn(held);
            var legacyPlayer = mock(Player.class); when(legacyPlayer.hasPermission("armourshop.admin")).thenReturn(true);
            var inventory = mock(PlayerInventory.class); when(legacyPlayer.getInventory()).thenReturn(inventory); when(inventory.getItemInMainHand()).thenReturn(held);
            var result = new ItemStack(Material.WRITTEN_BOOK); when(merger.merge(any(), any(), anyString())).thenReturn(result);
            assertTrue(run(legacyPlayer, "model", "apply", "cat", "skin")); verify(inventory).setItemInMainHand(result);
            assertEquals(List.of("Legacy page"), ((BookMeta) result.getItemMeta()).getPages());
        }
    }

    @Test void tabCompletionFiltersCommandsCategoriesSkinsAndDeletionCaches() {
        var cat = category("armor", set("Alpha"), set("Beta")); categories.when(CategoryLoader::get).thenReturn(List.of(cat));
        assertEquals(List.of("token", "reload", "pack", "catalog", "listtokens", "submission", "skin", "model"), manager.onTabComplete(console, command, "as", new String[]{""}));
        assertEquals(List.of("pack"), manager.onTabComplete(console, command, "as", new String[]{"P"}));
        for (var e : Map.of("token", List.of("delete"), "pack", List.of("pull", "sync"), "catalog", List.of("sync"), "submission", List.of("delete"), "skin", List.of("delete"), "model", List.of("apply")).entrySet())
            assertEquals(e.getValue(), manager.onTabComplete(console, command, "as", new String[]{e.getKey(), ""}));
        assertEquals(List.of("armor"), manager.onTabComplete(console, command, "as", new String[]{"model", "apply", "A"}));
        assertEquals(List.of("Alpha"), manager.onTabComplete(console, command, "as", new String[]{"model", "apply", "armor", "al"}));
        assertTrue(manager.onTabComplete(console, command, "as", new String[]{"model", "apply", "missing", ""}).isEmpty());
        try (var submissions = mockStatic(DeletableSubmissionCache.class); var skins = mockStatic(DeletableStaffSkinCache.class)) {
            submissions.when(DeletableSubmissionCache::snapshot).thenReturn(List.of("one", "two")); skins.when(DeletableStaffSkinCache::snapshot).thenReturn(List.of("staff"));
            assertEquals(List.of("one"), manager.onTabComplete(console, command, "as", new String[]{"submission", "delete", "o"}));
            assertEquals(List.of("staff"), manager.onTabComplete(console, command, "as", new String[]{"skin", "delete", null}));
        }
        var denied = mock(CommandSender.class); assertTrue(manager.onTabComplete(denied, command, "as", new String[]{""}).isEmpty());
        assertTrue(manager.onTabComplete(denied, command, "as", new String[]{"token", ""}).isEmpty());
        assertTrue(manager.onTabComplete(console, command, "as", new String[0]).isEmpty());
        when(command.getName()).thenReturn("other"); assertTrue(manager.onTabComplete(console, command, "as", new String[]{""}).isEmpty());
    }

    @Test void tabCompletionUsesLocaleIndependentIdentifiers() {
        var cat = category("ITEMS"); categories.when(CategoryLoader::get).thenReturn(List.of(cat)); Locale old = Locale.getDefault();
        try { Locale.setDefault(Locale.forLanguageTag("tr-TR")); assertEquals(List.of("ITEMS"), manager.onTabComplete(console, command, "as", new String[]{"model", "apply", "i"})); }
        finally { Locale.setDefault(old); }
    }
}

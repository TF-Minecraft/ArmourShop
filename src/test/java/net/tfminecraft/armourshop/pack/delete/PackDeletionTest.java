package net.tfminecraft.armourshop.pack.delete;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.*;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.*;
import net.tfminecraft.armourshop.pack.model.*;
import net.tfminecraft.armourshop.pack.reload.DeferredIaReloadService;
import net.tfminecraft.armourshop.pack.shop.*;
import net.tfminecraft.armourshop.pack.writer.mask.MasksYml;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class PackDeletionTest {
    @TempDir Path temp;
    private ArmourShop plugin;
    private Logger log;
    private BukkitScheduler scheduler;
    private DeferredIaReloadService reload;
    private Deque<Runnable> workers, main;
    private MockedStatic<JavaPlugin> plugins;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ProvinceSystemClient> client;
    private String oldContents, oldMasks, oldGuns;
    private record CacheField(Object owner, Field field) {}
    private final Map<CacheField, Object> state = new LinkedHashMap<>();

    @BeforeEach void setup() throws Exception {
        oldContents = Cache.iaContentsPath; oldMasks = Cache.masksYmlPath; oldGuns = Cache.gunsSkinsYmlPath;
        Cache.iaContentsPath = temp.toString(); Cache.masksYmlPath = null; Cache.gunsSkinsYmlPath = null;
        plugin = mock(ArmourShop.class); log = mock(Logger.class); when(plugin.getLogger()).thenReturn(log);
        reload = mock(DeferredIaReloadService.class); when(plugin.getDeferredIaReloadService()).thenReturn(reload);
        scheduler = mock(BukkitScheduler.class); workers = new ArrayDeque<>(); main = new ArrayDeque<>();
        plugins = mockStatic(JavaPlugin.class); plugins.when(() -> JavaPlugin.getPlugin(ArmourShop.class)).thenReturn(plugin);
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(i -> {workers.add(i.getArgument(1)); return mock(BukkitTask.class);});
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> {main.add(i.getArgument(1)); return mock(BukkitTask.class);});
        client = mockStatic(ProvinceSystemClient.class); client.when(() -> ProvinceSystemClient.revokeSubmission(anyString())).thenReturn(SimpleResult.success());
        for (Class<?> type : List.of(DeletableSubmissionCache.class, DeletableStaffSkinCache.class)) {
            Object cache = field(type, "CACHE").get(null);
            for (String name : List.of("IDS", "FETCHED_AT", "refreshInFlight", "generation")) {
                Field field = field(GenerationalIdCache.class, name);
                Object value = field.get(cache);
                CacheField key = new CacheField(cache, field);
                if (value instanceof AtomicReference<?> ref) {
                    state.put(key, ref.get()); ((AtomicReference<List<String>>) ref).set(List.of());
                } else if (value instanceof AtomicLong number) {
                    state.put(key, number.get()); number.set(0L);
                } else {
                    state.put(key, value); field.set(cache, value instanceof Boolean ? false : 0L);
                }
            }
        }
    }
    @AfterEach @SuppressWarnings("unchecked") void restore() throws Exception {
        for (var entry : state.entrySet()) {
            CacheField key = entry.getKey();
            Object value = key.field().get(key.owner());
            if (value instanceof AtomicReference<?> ref) ((AtomicReference<Object>) ref).set(entry.getValue());
            else if (value instanceof AtomicLong n) n.set((Long) entry.getValue());
            else key.field().set(key.owner(), entry.getValue());
        }
        client.close(); bukkit.close(); plugins.close(); Cache.iaContentsPath = oldContents; Cache.masksYmlPath = oldMasks; Cache.gunsSkinsYmlPath = oldGuns;
    }
    private static Field field(Class<?> type, String name) throws Exception {Field f = type.getDeclaredField(name); f.setAccessible(true); return f;}
    private static Object cacheValue(Class<?> type, String name) throws Exception {
        return field(GenerationalIdCache.class, name).get(field(type, "CACHE").get(null));
    }
    private PluginSubmission submission(boolean staff, String owner, String display, String category, String namespace) {
        return new PluginSubmission("id", owner, "skin", "item", display, "approved", null, null, staff, category, namespace);
    }
    private void fetched(PluginSubmission sub) {client.when(() -> ProvinceSystemClient.getSubmission("id")).thenReturn(PluginSubmissionResult.success(sub));}
    private Path put(Path path) throws IOException {Files.createDirectories(path.getParent()); Files.writeString(path, "fixture"); return path;}

    @Test void cacheRefreshesCoalesceRespectTtlKeepOldIdsOnFailureAndReleaseAfterException() throws Exception {
        for (boolean staff : List.of(false, true)) {
            Supplier<List<String>> snapshot = staff ? DeletableStaffSkinCache::snapshot : DeletableSubmissionCache::snapshot;
            Runnable invalidate = staff ? DeletableStaffSkinCache::invalidate : DeletableSubmissionCache::invalidate;
            MockedStatic.Verification call = staff ? ProvinceSystemClient::listDeletableStaffSkinIds : ProvinceSystemClient::listDeletableSubmissionIds;
            client.when(call).thenReturn(DeletableListResult.success(List.of("one", "two")));
            assertTrue(snapshot.get().isEmpty()); assertTrue(snapshot.get().isEmpty()); invalidate.run(); assertEquals(1, workers.size());
            workers.remove().run(); assertEquals(1, workers.size(), "invalidation queues a fresh generation after the original worker");
            workers.remove().run(); assertEquals(List.of("one", "two"), snapshot.get()); assertTrue(workers.isEmpty());
            client.when(call).thenReturn(DeletableListResult.fail("offline")); invalidate.run(); workers.remove().run();
            assertEquals(List.of("one", "two"), snapshot.get()); workers.remove().run();
            client.when(call).thenThrow(new IllegalStateException("network")); invalidate.run(); assertThrows(IllegalStateException.class, () -> workers.remove().run());
            client.reset(); client.when(call).thenReturn(DeletableListResult.success(List.of("new"))); invalidate.run(); workers.remove().run(); assertEquals(List.of("new"), snapshot.get());
        }
    }

    @Test void forcedInvalidationDuringFetchDiscardsStaleResultsAndSchedulesOneFollowup() {
        assertAll(() -> invalidationDuringFetch(false, false), () -> invalidationDuringFetch(true, false));
    }

    @Test void forcedInvalidationStillSchedulesFollowupWhenTheOldFetchThrows() {
        assertAll(() -> invalidationDuringFetch(false, true), () -> invalidationDuringFetch(true, true));
    }

    private void invalidationDuringFetch(boolean staff, boolean fail) throws Exception {
        workers.clear();
        Class<?> type = staff ? DeletableStaffSkinCache.class : DeletableSubmissionCache.class;
        Supplier<List<String>> snapshot = staff ? DeletableStaffSkinCache::snapshot : DeletableSubmissionCache::snapshot;
        Runnable invalidate = staff ? DeletableStaffSkinCache::invalidate : DeletableSubmissionCache::invalidate;
        MockedStatic.Verification call = staff ? ProvinceSystemClient::listDeletableStaffSkinIds : ProvinceSystemClient::listDeletableSubmissionIds;
        client.when(call).thenAnswer(invocation -> {
            invalidate.run(); invalidate.run();
            if (fail) throw new IllegalStateException("old fetch failed");
            return DeletableListResult.success(List.of("deleted-stale-id"));
        }).thenReturn(DeletableListResult.success(List.of("current-id")));
        assertEquals(List.of(), snapshot.get());
        Runnable oldWorker = workers.remove();
        if (fail) assertThrows(IllegalStateException.class, oldWorker::run); else oldWorker.run();
        assertAll(type.getSimpleName(),
            () -> assertEquals(List.of(), ((AtomicReference<?>) cacheValue(type, "IDS")).get(), "discard superseded response"),
            () -> assertEquals(0L, ((AtomicLong) cacheValue(type, "FETCHED_AT")).get(), "stale results must not refresh the TTL"),
            () -> assertEquals(1, workers.size(), "coalesce forced invalidations into one fresh fetch"));
        workers.remove().run();
        assertEquals(List.of("current-id"), snapshot.get()); assertTrue(workers.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get().clear());
    }

    @Test void concurrentSnapshotCallersShareOneAdmittedFetch() throws Exception {
        for (boolean staff : List.of(false, true)) {
            Supplier<List<String>> snapshot = staff ? DeletableStaffSkinCache::snapshot : DeletableSubmissionCache::snapshot;
            MockedStatic.Verification call = staff ? ProvinceSystemClient::listDeletableStaffSkinIds : ProvinceSystemClient::listDeletableSubmissionIds;
            client.when(call).thenReturn(DeletableListResult.success(List.of("shared")));
            Queue<Runnable> admitted = new ConcurrentLinkedQueue<>();
            doAnswer(i -> { admitted.add(i.getArgument(1)); return mock(BukkitTask.class); }).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            CountDownLatch ready = new CountDownLatch(6), start = new CountDownLatch(1);
            ExecutorService callers = Executors.newFixedThreadPool(6);
            try {
                List<Future<List<String>>> calls = new ArrayList<>();
                for (int i = 0; i < 6; i++) calls.add(callers.submit(() -> {
                    // Mockito static boundaries are scoped to the caller thread.
                    try (var threadPlugins = mockStatic(JavaPlugin.class); var threadBukkit = mockStatic(Bukkit.class)) {
                        threadPlugins.when(() -> JavaPlugin.getPlugin(ArmourShop.class)).thenReturn(plugin);
                        threadBukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                        ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return snapshot.get();
                    }
                }));
                assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
                for (Future<List<String>> callResult : calls) assertEquals(List.of(), callResult.get(10, TimeUnit.SECONDS));
                assertEquals(1, admitted.size(), "concurrent snapshots must admit only one cache fetch");
                admitted.remove().run(); assertEquals(List.of("shared"), snapshot.get()); assertTrue(admitted.isEmpty());
            } finally { start.countDown(); callers.shutdownNow(); assertTrue(callers.awaitTermination(10, TimeUnit.SECONDS)); }
        }
    }

    @Test void cacheCanRetryAfterSchedulerRejection() {
        for (boolean staff : List.of(false, true)) {
            Runnable invalidate = staff ? DeletableStaffSkinCache::invalidate : DeletableSubmissionCache::invalidate;
            doThrow(new IllegalStateException("disabled")).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            assertThrows(IllegalStateException.class, invalidate::run);
            doAnswer(i -> {workers.add(i.getArgument(1)); return mock(BukkitTask.class);}).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            invalidate.run(); assertEquals(1, workers.size(), "refresh must retry after scheduler rejected it"); workers.clear();
        }
    }

    @Test void cacheSnapshotsCannotBeMutatedByTabCompleters() {
        client.when(ProvinceSystemClient::listDeletableSubmissionIds).thenReturn(DeletableListResult.success(List.of("one")));
        client.when(ProvinceSystemClient::listDeletableStaffSkinIds).thenReturn(DeletableListResult.success(List.of("one")));
        DeletableSubmissionCache.snapshot(); DeletableStaffSkinCache.snapshot(); while (!workers.isEmpty()) workers.remove().run();
        assertAll(() -> assertThrows(UnsupportedOperationException.class, () -> DeletableSubmissionCache.snapshot().clear()),
            () -> assertThrows(UnsupportedOperationException.class, () -> DeletableStaffSkinCache.snapshot().clear()));
    }

    @Test void deleteRunnersValidateInputApiResultsLaneAndStaffCategory() {
        for (Function<String, String> run : List.<Function<String, String>>of(SubmissionDeleteRunner::run, SkinDeleteRunner::run)) {
            assertTrue(run.apply(null).contains("required")); assertTrue(run.apply(" ").contains("required"));
            client.when(() -> ProvinceSystemClient.getSubmission("id")).thenReturn(PluginSubmissionResult.fail("offline"), PluginSubmissionResult.fail(null), PluginSubmissionResult.success(null));
            assertEquals("offline", run.apply("id")); assertTrue(run.apply("id").contains("Could not load")); assertTrue(run.apply("id").contains("Could not load"));
        }
        fetched(submission(true, null, null, "category", null)); assertTrue(SubmissionDeleteRunner.run("id").contains("staff skin"));
        fetched(submission(false, null, null, null, null)); assertTrue(SkinDeleteRunner.run("id").contains("player submission"));
        for (String category : Arrays.asList(null, " ")) {fetched(submission(true, null, null, category, null)); assertTrue(SkinDeleteRunner.run("id").contains("missing category"));}
        client.verify(() -> ProvinceSystemClient.revokeSubmission(anyString()), never());
    }

    @Test void playerDeleteCleansFilesShopAndPermissionThenQueuesOnlyFlush() throws Exception {
        var owner = UUID.randomUUID(); fetched(submission(false, owner.toString(), "Display", null, null));
        Path file = put(PackPaths.configsDir(temp).resolve("skin.yml"));
        try (var shops = mockStatic(ShopSubmissionWriter.class); var grants = mockStatic(LuckPermsGrant.class)) {
            assertTrue(SubmissionDeleteRunner.run(" id ").contains("(Display)")); assertFalse(Files.exists(file));
            shops.verify(() -> ShopSubmissionWriter.remove("skin", "item", List.of(), log)); grants.verify(() -> LuckPermsGrant.revokeSubmission(owner, "skin", log));
            assertEquals(1, main.size()); verifyNoInteractions(reload); main.remove().run(); verify(plugin).reload(); verify(reload).requestFlush(false, true); verifyNoMoreInteractions(reload);
        }
    }

    @Test void playerDeleteUsesTheSameExplicitNamespaceAsPackWriter() throws Exception {
        fetched(submission(false, null, null, null, "custom")); Path custom = put(PackPaths.configsDir(temp, "custom").resolve("skin.yml"));
        Path unrelated = put(PackPaths.configsDir(temp).resolve("skin.yml"));
        try (var shops = mockStatic(ShopSubmissionWriter.class)) {
            SubmissionDeleteRunner.run("id"); assertFalse(Files.exists(custom), "remove the namespace supplied by the submission"); assertTrue(Files.exists(unrelated));
        }
    }

    @Test void staffDeleteUsesStaffNamespaceAndCategoryThenReloadsAndFlushes() throws Exception {
        fetched(submission(true, null, " ", "category", "custom")); Path file = put(PackPaths.configsDir(temp, "custom").resolve("skin.yml"));
        try (var shops = mockStatic(ShopSubmissionWriter.class)) {
            assertTrue(SkinDeleteRunner.run("id").contains("(skin)")); assertFalse(Files.exists(file));
            shops.verify(() -> ShopSubmissionWriter.removeStaff("skin", "item", List.of(), "category", log)); main.remove().run(); verify(plugin).reload(); verify(reload).requestFlush(false, true);
            fetched(submission(true, null, "Named skin", "category", "custom")); assertTrue(SkinDeleteRunner.run("id").contains("(Named skin)"));
        }
    }

    @Test void deletionContinuesBestEffortCleanupButReportsApiFailureWithoutReload() throws Exception {
        try (var shops = mockStatic(ShopSubmissionWriter.class); var remover = mockStatic(PackSubmissionRemover.class); var grants = mockStatic(LuckPermsGrant.class)) {
            remover.when(() -> PackSubmissionRemover.remove(any(), anyString(), anyString(), anyList(), eq(log))).thenThrow(new IOException("pack"));
            remover.when(() -> PackSubmissionRemover.remove(any(), anyString(), anyString(), anyString(), anyList(), eq(log))).thenThrow(new IOException("pack"));
            shops.when(() -> ShopSubmissionWriter.remove(anyString(), anyString(), anyList(), eq(log))).thenThrow(new IOException("shop"));
            shops.when(() -> ShopSubmissionWriter.removeStaff(anyString(), anyString(), anyList(), anyString(), eq(log))).thenThrow(new IOException("shop"));
            for (boolean staff : List.of(false, true)) {
                fetched(submission(staff, "bad-uuid", null, "category", null));
                client.when(() -> ProvinceSystemClient.revokeSubmission("id")).thenReturn(SimpleResult.fail("offline"), SimpleResult.fail(null), SimpleResult.success());
                assertTrue((staff ? SkinDeleteRunner.run("id") : SubmissionDeleteRunner.run("id")).contains("offline"));
                Cache.iaContentsPath = " "; assertTrue((staff ? SkinDeleteRunner.run("id") : SubmissionDeleteRunner.run("id")).contains("unknown")); assertTrue(main.isEmpty());
                Cache.iaContentsPath = null; assertTrue((staff ? SkinDeleteRunner.run("id") : SubmissionDeleteRunner.run("id")).contains("(skin)")); main.clear(); Cache.iaContentsPath = temp.toString();
            }
            verify(log, atLeastOnce()).warning(contains("pack remove failed")); verify(log, atLeastOnce()).warning(contains("shop remove failed")); verify(log, atLeastOnce()).warning(contains("invalid player uuid"));
        }
    }

    @Test void removerDeletesAllArmorTiersLegacyAndThreeDimensionalHelmets() throws Exception {
        List<Path> expected = new ArrayList<>();
        for (String slug : List.of("skin_iron", "skin")) {
            expected.add(put(PackPaths.configsDir(temp, "custom").resolve(slug + ".yml")));
            for (String stem : List.of("helmet", "chestplate", "leggings", "boots")) expected.add(put(PackPaths.armorIconsDir(temp, "custom").resolve(slug + "_" + stem + ".png")));
            for (int layer : List.of(1, 2)) expected.add(put(PackPaths.armorLayersDir(temp, "custom").resolve(slug + "_layer_" + layer + ".png")));
            expected.add(put(PackPaths.itemTexturesDir(temp, "custom").resolve(slug + "_helmet.png"))); expected.add(put(PackPaths.itemModelsDir(temp, "custom").resolve(slug + "_helmet.json")));
        }
        var removed = PackSubmissionRemover.remove(temp, " custom ", " ARMOR_SET ", " skin ", Arrays.asList(null, " ", " iron "), log);
        assertEquals(new HashSet<>(expected), new HashSet<>(removed)); for (Path path : expected) assertFalse(Files.exists(path));
        assertTrue(PackSubmissionRemover.remove(temp, "custom", "armor_set", "skin", null, null).isEmpty());
    }

    @Test void removerDeletesNonArmorVariantsRegistriesAndToleratesMissingFiles() throws Exception {
        for (String kind : Arrays.asList("bow", "book", "gun", "mask", null)) {
            String ns = PackPaths.playerNamespace(); List<Path> expected = new ArrayList<>(); expected.add(put(PackPaths.configsDir(temp).resolve("skin.yml")));
            if ("book".equals(kind)) for (String suffix : List.of("_unsigned", "_signed")) expected.add(put(PackPaths.itemTexturesDir(temp).resolve("skin" + suffix + ".png")));
            else {
                expected.add(put(PackPaths.itemTexturesDir(temp).resolve("skin.png")));
                if ("gun".equals(kind)) for (String suffix : List.of("_carry", "_reload", "_aim", "_aim_charged")) expected.add(put(PackPaths.itemModelsDir(temp).resolve("skin" + suffix + ".json")));
                else {
                    for (String suffix : List.of("", "_0", "_1", "_2", "_charged", "_blocking")) expected.add(put(PackPaths.itemModelsDir(temp).resolve("skin" + suffix + ".json")));
                    for (String suffix : List.of("_0", "_1", "_2", "_charged", "_arrow")) expected.add(put(PackPaths.itemTexturesDir(temp).resolve("skin" + suffix + ".png")));
                }
            }
            Cache.gunsSkinsYmlPath = temp.resolve("skins.yml").toString(); Files.writeString(Path.of(Cache.gunsSkinsYmlPath), "skin:\n  types:\n  - rifle\n");
            Cache.masksYmlPath = temp.resolve("masks.yml").toString(); Files.writeString(Path.of(Cache.masksYmlPath), "masks:\n  skin:\n    item: ia." + ns + ":skin\n");
            var removed = PackSubmissionRemover.remove(temp, " ", kind, "skin", List.of(), log); assertEquals(new HashSet<>(expected), new HashSet<>(removed), String.valueOf(kind));
            assertTrue(PackSubmissionRemover.remove(temp, null, kind, "skin", List.of(), null).isEmpty());
        }
        Cache.gunsSkinsYmlPath = " "; assertTrue(PackSubmissionRemover.remove(temp, "gun", "none", null, null).isEmpty());
        Cache.masksYmlPath = null; assertTrue(PackSubmissionRemover.remove(temp, "mask", "none", null, null).isEmpty());
        try (var masks = mockStatic(MasksYml.class)) {
            masks.when(() -> MasksYml.remove(null, "skin")).thenThrow(new IllegalStateException("registry"));
            assertTrue(PackSubmissionRemover.remove(temp, "mask", "skin", null, log).isEmpty()); verify(log).warning(contains("registry"));
            assertTrue(PackSubmissionRemover.remove(temp, "mask", "skin", null, null).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> PackSubmissionRemover.remove(null, "item", "skin", null, log));
        for (String slug : Arrays.asList(null, " ")) assertThrows(IllegalArgumentException.class, () -> PackSubmissionRemover.remove(temp, "item", slug, null, log));
    }

    @Test void removerRejectsTraversalBeforeTouchingFiles() throws Exception {
        Path victim = put(PackPaths.namespaceRoot(temp).resolve("victim.yml"));
        assertThrows(IllegalArgumentException.class, () -> PackSubmissionRemover.remove(temp, "item", "../victim", List.of(), log)); assertTrue(Files.exists(victim));
        Path validTier = put(PackPaths.configsDir(temp).resolve("skin_iron.yml"));
        assertThrows(IllegalArgumentException.class, () -> PackSubmissionRemover.remove(temp, "armor_set", "skin", List.of("iron", "../../victim"), log)); assertTrue(Files.exists(validTier));
        assertThrows(IllegalArgumentException.class, () -> PackSubmissionRemover.remove(temp, "../outside", "item", "skin", List.of(), log));
    }
}

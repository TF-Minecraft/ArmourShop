package net.tfminecraft.armourshop.pack.reload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.*;
import net.tfminecraft.armourshop.pack.apply.PackPullRunner;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class DeferredIaReloadServiceTest {
    @TempDir Path temp;
    private JavaPlugin plugin;
    private Logger log;
    private BukkitScheduler scheduler;
    private ConsoleCommandSender console;
    private PendingReloadQueue queue;
    private DeferredIaReloadService service;
    private Deque<Runnable> workers, main, delayed;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ProvinceSystemClient> client;
    private MockedStatic<PackPullRunner> pulls;
    private String oldContents;
    private int oldDelay;

    @BeforeEach void setup() {
        oldContents = Cache.iaContentsPath; oldDelay = Cache.iaReloadDelaySeconds;
        Cache.iaContentsPath = temp.resolve("contents").toString(); Cache.iaReloadDelaySeconds = 2;
        plugin = mock(JavaPlugin.class); log = mock(Logger.class); scheduler = mock(BukkitScheduler.class); console = mock(ConsoleCommandSender.class);
        when(plugin.getLogger()).thenReturn(log); when(plugin.getDataFolder()).thenReturn(temp.resolve("plugin").toFile());
        workers = new ArrayDeque<>(); main = new ArrayDeque<>(); delayed = new ArrayDeque<>();
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getConsoleSender).thenReturn(console); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        bukkit.when(() -> Bukkit.dispatchCommand(eq(console), anyString())).thenReturn(true);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(i -> { workers.add(i.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> { main.add(i.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(i -> { delayed.add(i.getArgument(1)); return mock(BukkitTask.class); });
        client = mockStatic(ProvinceSystemClient.class); client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(List.of(item("a"))));
        client.when(() -> ProvinceSystemClient.markApplied(anyList())).thenAnswer(i -> AppliedResult.success(i.getArgument(0)));
        pulls = mockStatic(PackPullRunner.class);
        queue = new PendingReloadQueue(plugin); service = new DeferredIaReloadService(plugin, queue);
    }
    @AfterEach void restore() {
        pulls.close(); client.close(); bukkit.close(); Cache.iaContentsPath = oldContents; Cache.iaReloadDelaySeconds = oldDelay;
    }
    private static ApprovedSubmission item(String id) { return submission(id, id, "handheld", List.of()); }
    private static ApprovedSubmission submission(String id, String slug, String kind, List<String> tiers) {
        return new ApprovedSubmission(id, null, slug, kind, slug, null, null, tiers, List.of(), Map.of(),
            false, List.of(), List.of(), List.of(), false, null, null, null, "tfmc_submissions");
    }
    private void approved(ApprovedSubmission... subs) { client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.success(Arrays.asList(subs))); }
    private void start(boolean force, boolean refresh) { service.requestFlush(force, refresh); workers.removeFirst().run(); main.removeFirst().run(); }
    private void compressed() { service.onPackCompressed(mock(ItemsAdderPackCompressedEvent.class)); }
    private void writeConfig(String slug) throws Exception {
        Path config = Path.of(Cache.iaContentsPath).resolve("tfmc_submissions/configs/" + slug + ".yml");
        Files.createDirectories(config.getParent()); Files.writeString(config, "info:\n  namespace: tfmc_submissions\n");
    }

    @Test void pendingQueuePersistsNormalizesDeduplicatesAndReloads() throws Exception {
        queue.load(); assertTrue(queue.isEmpty()); queue.enqueue(null); queue.enqueue(List.of());
        queue.enqueue(Arrays.asList(null, " ", " a ", "b", "a")); assertEquals(List.of("a", "b"), queue.snapshot());
        queue.enqueue(List.of("a")); var copy = queue.snapshot(); copy.clear(); assertEquals(2, queue.size());
        var loaded = new PendingReloadQueue(plugin); loaded.load(); assertEquals(List.of("a", "b"), loaded.snapshot());
        queue.clear(null); queue.clear(List.of()); queue.clear(Arrays.asList(null, "unknown")); assertEquals(2, queue.size());
        queue.clear(List.of(" a ")); assertEquals(List.of("b"), queue.snapshot());
        queue.enqueue(List.of("c")); assertEquals(2, queue.retainAll(Arrays.asList(null, " ", " b ", "never_written")));
        assertEquals(List.of("b"), queue.snapshot()); assertEquals(1, queue.retainAll(List.of("b")));
        assertEquals(1, queue.retainAll(null)); assertTrue(queue.isEmpty());
        Path file = temp.resolve("plugin/pending-reload.yml");
        Files.writeString(file, "submission-ids:\n  - ' one '\n  - ''\n  - ' '\n  - one\n  - two\n");
        queue.load(); assertEquals(List.of("one", "two"), queue.snapshot());
        Files.delete(file); queue.load(); assertTrue(queue.isEmpty());
    }

    @Test void queueSaveFailuresAreLoggedWithoutLosingInMemoryIds() throws Exception {
        Files.createDirectories(temp.resolve("plugin/pending-reload.yml"));
        queue.enqueue(List.of("a")); assertEquals(List.of("a"), queue.snapshot());
        verify(log).warning(contains("failed to save pending-reload.yml"));
    }

    @Test void pruningDropsUnapprovedIdsWithoutImportingUnwrittenSubmissions() {
        queue.enqueue(List.of("a", "revoked")); approved(null, item(null), item(" "), item(" a "), item("never_written"));
        var result = service.pruneQueueToApproved(log); assertTrue(result.ok); assertEquals(2, result.before); assertEquals(1, result.after); assertNull(result.error);
        assertEquals(List.of("a"), queue.snapshot()); assertSame(queue, service.queue());
        assertTrue(service.pruneQueueToApproved(null).ok);
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.fail(null));
        result = service.pruneQueueToApproved(log); assertFalse(result.ok); assertEquals("unknown error", result.error); assertEquals(List.of("a"), queue.snapshot());
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.fail("offline"));
        assertEquals("offline", service.pruneQueueToApproved(null).error);
    }

    @Test void flushPrunesThenWaitsForEmptyServerUnlessForced() {
        queue.enqueue(List.of("a")); var player = mock(Player.class); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        service.requestFlush(); workers.removeFirst().run(); main.removeFirst().run(); assertTrue(delayed.isEmpty());
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "iareload"), never());
        service.requestFlush(true); workers.removeFirst().run(); main.removeFirst().run();
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "iareload"));
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(40L));
        assertEquals(1, delayed.size()); service.requestFlush(); assertTrue(workers.isEmpty());
    }

    @Test void simultaneousRequestsDoNotStartTwoReloadsAndEarlyZipEventsAreIgnored() {
        queue.enqueue(List.of("a")); compressed(); assertTrue(workers.isEmpty());
        service.requestFlush(); service.requestFlush(); assertEquals(2, workers.size());
        workers.removeFirst().run(); workers.removeFirst().run(); main.removeFirst().run(); main.removeFirst().run();
        assertEquals(1, delayed.size()); compressed(); assertTrue(workers.isEmpty());
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "iareload"), times(1));
        delayed.removeFirst().run(); bukkit.verify(() -> Bukkit.dispatchCommand(console, "iazip"));
        compressed(); assertEquals(1, workers.size()); compressed(); assertEquals(1, workers.size());
    }

    @Test void emptyQueuesSkipRefreshUnlessExplicitlyRequested() {
        start(false, false); assertTrue(delayed.isEmpty());
        queue.enqueue(List.of("revoked")); start(false, false); assertTrue(queue.isEmpty()); assertTrue(delayed.isEmpty());
        verify(log).info("[ia-reload] queue empty after prune — skipping IA refresh");
        Cache.iaReloadDelaySeconds = -5; start(false, true);
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(0L));
        delayed.removeFirst().run(); compressed(); assertTrue(workers.isEmpty());
        bukkit.verify(() -> Bukkit.dispatchCommand(console, "iazip"));
    }

    @Test void commandDispatchFailuresKeepQueueAndAllowRetry() {
        queue.enqueue(List.of("a"));
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iareload")).thenReturn(false);
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iazip")).thenReturn(false);
        start(false, false); delayed.removeFirst().run(); compressed(); assertTrue(workers.isEmpty());
        assertEquals(List.of("a"), queue.snapshot()); verify(log).warning(contains("failed to dispatch iareload"));
        verify(log).severe(contains("failed to dispatch iazip"));
        service.requestFlush(); assertEquals(1, workers.size());
    }

    private void finishRetriedFlush() {
        assertEquals(List.of("a"), queue.snapshot());
        service.requestFlush(); assertEquals(1, workers.size(), "failed refresh must release the in-flight guard");
        workers.removeFirst().run(); main.removeFirst().run(); delayed.removeFirst().run();
        compressed(); workers.removeFirst().run(); main.removeFirst().run(); assertTrue(queue.isEmpty());
    }

    @Test void delayedSchedulingExceptionKeepsPendingIdsAndPermitsRetry() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a");
        doThrow(new IllegalStateException("scheduler rejected")).when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
        service.requestFlush(); workers.removeFirst().run(); assertThrows(IllegalStateException.class, () -> main.removeFirst().run());
        compressed(); assertTrue(workers.isEmpty());
        doAnswer(i -> { delayed.add(i.getArgument(1)); return mock(BukkitTask.class); }).when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
        finishRetriedFlush();
    }

    @Test void reloadDispatchExceptionKeepsPendingIdsAndPermitsRetry() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a");
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iareload")).thenThrow(new IllegalStateException("reload handler failed"));
        service.requestFlush(); workers.removeFirst().run(); assertThrows(IllegalStateException.class, () -> main.removeFirst().run());
        assertTrue(delayed.isEmpty()); compressed(); assertTrue(workers.isEmpty());
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iareload")).thenReturn(true); finishRetriedFlush();
    }

    @Test void zipDispatchExceptionKeepsPendingIdsAndPermitsRetry() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a"); start(false, false);
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iazip")).thenThrow(new IllegalStateException("zip handler failed"));
        assertThrows(IllegalStateException.class, () -> delayed.removeFirst().run());
        compressed(); assertTrue(workers.isEmpty(), "a failed zip must not schedule an acknowledgement");
        bukkit.when(() -> Bukkit.dispatchCommand(console, "iazip")).thenReturn(true); finishRetriedFlush();
    }

    @Test void pruningCannotDiscardNewOrRewrittenEntriesAddedWhileApiFetches() {
        queue.enqueue(List.of("a"));
        client.when(ProvinceSystemClient::listApproved).thenAnswer(i -> {
            queue.enqueue(List.of("a", "late")); return ListResult.success(List.of());
        });
        var result = service.pruneQueueToApproved(log);
        assertEquals(List.of("a", "late"), queue.snapshot()); assertEquals(1, result.before); assertEquals(2, result.after);
    }

    @Test void completedZipDoesNotAckSameIdRewrittenAfterReloadStarted() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a"); start(false, false); delayed.removeFirst().run();
        queue.enqueue(List.of("a")); compressed(); workers.removeFirst().run(); main.removeFirst().run();
        client.verify(() -> ProvinceSystemClient.markApplied(anyList()), never()); assertEquals(List.of("a"), queue.snapshot());
    }

    @Test void acknowledgementRechecksGenerationAfterFetchingRemoteApprovals() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a"); start(false, false); delayed.removeFirst().run(); compressed();
        client.when(ProvinceSystemClient::listApproved).thenAnswer(i -> {
            queue.enqueue(List.of("a")); return ListResult.success(List.of(item("a")));
        });
        workers.removeFirst().run(); main.removeFirst().run();
        client.verify(() -> ProvinceSystemClient.markApplied(anyList()), never()); assertEquals(List.of("a"), queue.snapshot());
    }

    @Test void acknowledgementCompletionPreservesEntriesRewrittenWhileHttpWasInFlight() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a"); start(false, false); delayed.removeFirst().run(); compressed();
        workers.removeFirst().run(); queue.enqueue(List.of("a", "late")); main.removeFirst().run();
        assertEquals(List.of("a", "late"), queue.snapshot()); client.verify(() -> ProvinceSystemClient.markApplied(List.of("a")));
    }

    @Test void completedZipAcknowledgesOnlyConfirmedWrittenConfigsAndHandlesPartialResponses() throws Exception {
        queue.enqueue(List.of("a", "b", "missing", "revoked")); approved(item("a"), item("b"), item("missing"), item("revoked"));
        writeConfig("a"); writeConfig("b"); start(false, false); delayed.removeFirst().run();
        approved(item("a"), item("b"), item("missing"));
        client.when(() -> ProvinceSystemClient.markApplied(List.of("a", "b"))).thenReturn(AppliedResult.success(List.of("a")));
        compressed(); workers.removeFirst().run(); assertEquals(4, queue.size()); main.removeFirst().run();
        assertEquals(List.of("b"), queue.snapshot());
        client.verify(() -> ProvinceSystemClient.markApplied(List.of("a", "b")));
        verify(log).info(contains("dropped 1 queued id(s)")); verify(log).warning(contains("pack config missing"));
        verify(log).warning(contains("some written ids were not marked applied"));
    }

    @Test void failedAcksPreserveWrittenIdsAndEmptyPlansDoNotCallApi() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a");
        client.when(() -> ProvinceSystemClient.markApplied(anyList())).thenReturn(AppliedResult.fail("offline"));
        start(false, false); delayed.removeFirst().run(); compressed(); workers.removeFirst().run(); main.removeFirst().run();
        assertEquals(List.of("a"), queue.snapshot()); verify(log).warning(contains("applied ack failed: offline"));
        client.clearInvocations(); Files.delete(Path.of(Cache.iaContentsPath).resolve("tfmc_submissions/configs/a.yml"));
        start(false, false); delayed.removeFirst().run(); compressed(); workers.removeFirst().run(); main.removeFirst().run();
        assertTrue(queue.isEmpty()); client.verify(() -> ProvinceSystemClient.markApplied(anyList()), never());
    }

    @Test void ackDefersWhenContentsOrApprovalsCannotBeConfirmed() {
        queue.enqueue(List.of("a")); start(false, false); delayed.removeFirst().run();
        Cache.iaContentsPath = null; compressed(); workers.removeFirst().run(); assertTrue(main.isEmpty()); assertEquals(List.of("a"), queue.snapshot());
        Cache.iaContentsPath = " "; start(false, false); delayed.removeFirst().run(); compressed(); workers.removeFirst().run();
        Cache.iaContentsPath = temp.toString(); start(false, false); delayed.removeFirst().run();
        client.when(ProvinceSystemClient::listApproved).thenReturn(ListResult.fail(null)); compressed(); workers.removeFirst().run();
        verify(log).warning(contains("could not confirm approvals (unknown error)")); assertEquals(List.of("a"), queue.snapshot());
    }

    @Test void quitTriggersPullOnlyAfterLastPlayerHasLeft() {
        service.onPlayerQuit(mock(PlayerQuitEvent.class)); var player = mock(Player.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player)); main.removeFirst().run(); pulls.verifyNoInteractions();
        service.onPlayerQuit(mock(PlayerQuitEvent.class)); bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        main.removeFirst().run(); pulls.verify(() -> PackPullRunner.run(false, null));
    }

    @Test void aZipMustNotAcknowledgeFilesEnqueuedAfterReloadStarted() throws Exception {
        queue.enqueue(List.of("a")); writeConfig("a"); start(false, false); delayed.removeFirst().run();
        queue.enqueue(List.of("late")); writeConfig("late"); approved(item("a"), item("late"));
        compressed(); workers.removeFirst().run(); main.removeFirst().run();
        assertEquals(List.of("late"), queue.snapshot());
        client.verify(() -> ProvinceSystemClient.markApplied(List.of("a")));
    }

    @Test void ackPlannerHandlesMalformedRowsAndMissingMetadata() throws Exception {
        assertTrue(ApplyAckPlanner.plan(null, null, temp).ack().isEmpty());
        var plan = ApplyAckPlanner.plan(Arrays.asList(null, " ", " a "), Arrays.asList(null, item(null), item(" ")), temp);
        assertEquals(List.of("a"), plan.notApproved());
        assertFalse(ApplyAckPlanner.hasPackConfig(null, item("a"))); assertFalse(ApplyAckPlanner.hasPackConfig(temp, null));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission("a", null, null, List.of())));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission("a", " ", "handheld", List.of())));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission("a", "skin", "armor_set", List.of())));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission(null, "skin", "armor_set", List.of("iron"))));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission(" ", "skin", "armor_set", List.of("iron"))));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission("a", "skin", "armor_set", Arrays.asList((String) null))));
        assertFalse(ApplyAckPlanner.hasPackConfig(temp, submission("a", "skin", "armor_set", List.of(" "))));
        assertThrows(UnsupportedOperationException.class, () -> plan.notApproved().clear());
    }

    @Test void armorAckChecksSlugConfigButAcknowledgesSubmissionId() throws Exception {
        var armor = submission("7d294886-978e-42b1-903a-6207e87d9548", "skin", "armor_set", List.of("iron"));
        writeConfig("skin_iron");
        var plan = ApplyAckPlanner.plan(List.of(armor.id), List.of(armor), Path.of(Cache.iaContentsPath));
        assertEquals(List.of(armor.id), plan.ack()); assertTrue(plan.missingFiles().isEmpty());
    }
}

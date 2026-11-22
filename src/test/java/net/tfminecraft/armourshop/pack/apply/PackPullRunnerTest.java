package net.tfminecraft.armourshop.pack.apply;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.Cache;
import net.tfminecraft.armourshop.api.ProvinceSystemClient.ApprovedSubmission;
import net.tfminecraft.armourshop.pack.reload.*;
import net.tfminecraft.armourshop.pack.shop.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class PackPullRunnerTest {
    @TempDir Path temp;
    private ArmourShop plugin;
    private Logger log;
    private BukkitScheduler scheduler;
    private Deque<Runnable> workers, main;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<JavaPlugin> plugins;
    private MockedStatic<PackApplyService> apply;
    private MockedStatic<ShopSubmissionWriter> shop;
    private MockedStatic<LuckPermsGrant> grants;
    private AtomicBoolean running;
    private String oldForceTime;
    private static final UUID OWNER = UUID.fromString("ef76d943-1be4-445f-b4a6-194f8618c975");

    @BeforeEach void setup() throws Exception {
        var field = PackPullRunner.class.getDeclaredField("RUNNING"); field.setAccessible(true); running = (AtomicBoolean) field.get(null);
        assertFalse(running.get(), "Previous pull must be complete");
        oldForceTime = Cache.forceReloadTime;
        plugin = mock(ArmourShop.class); log = mock(Logger.class); scheduler = mock(BukkitScheduler.class);
        when(plugin.getLogger()).thenReturn(log); when(plugin.getDataFolder()).thenReturn(temp.toFile());
        workers = new ArrayDeque<>(); main = new ArrayDeque<>();
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        plugins = mockStatic(JavaPlugin.class); plugins.when(() -> JavaPlugin.getPlugin(ArmourShop.class)).thenReturn(plugin);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(i -> { workers.add(i.getArgument(1)); return mock(BukkitTask.class); });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(i -> { main.add(i.getArgument(1)); return mock(BukkitTask.class); });
        apply = mockStatic(PackApplyService.class); shop = mockStatic(ShopSubmissionWriter.class); grants = mockStatic(LuckPermsGrant.class);
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenReturn(new PackApplyService.ApplySummary(0, 0, 0, List.of(), List.of()));
    }
    @AfterEach void restore() {
        grants.close(); shop.close(); apply.close(); plugins.close(); bukkit.close(); running.set(false); Cache.forceReloadTime = oldForceTime;
    }
    private static ApprovedSubmission sub(String id, String uuid, boolean staff) {
        return new ApprovedSubmission(id, uuid, id, "handheld", id, null, null, null, null, null,
            false, null, null, null, staff, null, null, null, null);
    }
    private void finish() { workers.removeFirst().run(); main.removeFirst().run(); }

    @Test void onlyOnePullRunsAndBusyCallbacksReturnOnMainThread() {
        List<PackPullRunner.PullResult> completed = new ArrayList<>();
        assertTrue(PackPullRunner.run(false, completed::add)); assertTrue(PackPullRunner.isRunning());
        assertFalse(PackPullRunner.run(true, completed::add)); assertFalse(PackPullRunner.run(false, null));
        assertEquals(1, workers.size()); assertTrue(completed.isEmpty());
        main.removeFirst().run(); assertTrue(completed.getFirst().busy);
        assertEquals("Pack pull already running.", completed.getFirst().summaryLine());
        finish(); assertFalse(PackPullRunner.isRunning()); assertEquals(2, completed.size());
        assertEquals("Pack pull done: wrote=0 skipped=0 failed=0 shop=0/0 lp=0/0 queued=0", completed.getLast().summaryLine());
    }

    @Test void writesAndGrantsMustBothSucceedBeforeQueuing() throws Exception {
        var player = sub(" good ", " " + OWNER + " ", false);
        var staff = sub("staff", null, true);
        var shopFailed = sub("shop_failed", OWNER.toString(), false);
        var invalidUuid = sub("invalid", "bad", false);
        var missingUuid = sub("missing", null, false);
        var blankUuid = sub("blank", " ", false);
        var denied = sub("denied", OWNER.toString(), false);
        var missingId = sub(null, null, true);
        var blankId = sub(" ", null, true);
        var submissions = List.of(player, staff, shopFailed, invalidUuid, missingUuid, blankUuid, denied, missingId, blankId);
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenReturn(new PackApplyService.ApplySummary(9, 2, 1, List.of("pack detail"), submissions));
        shop.when(() -> ShopSubmissionWriter.write(shopFailed, log)).thenThrow(new IOException("disk full"));
        grants.when(() -> LuckPermsGrant.grantSubmission(eq(OWNER), anyString(), eq(log))).thenAnswer(i -> !"denied".equals(i.getArgument(1)));
        var reload = mock(DeferredIaReloadService.class); var queue = new PendingReloadQueue(plugin);
        when(reload.queue()).thenReturn(queue); when(plugin.getDeferredIaReloadService()).thenReturn(reload);
        List<PackPullRunner.PullResult> completed = new ArrayList<>();
        assertTrue(PackPullRunner.run(true, completed::add)); workers.removeFirst().run();
        assertTrue(queue.isEmpty()); verify(plugin, never()).reload(); assertTrue(PackPullRunner.isRunning());
        main.removeFirst().run();
        assertEquals(List.of("good", "staff"), queue.snapshot()); verify(reload).requestFlush(true); verify(plugin).reload();
        var result = completed.getFirst(); assertFalse(result.failed); assertNull(result.error);
        assertEquals(9, result.written); assertEquals(2, result.skipped); assertEquals(1, result.failedCount);
        assertEquals(8, result.shopOk); assertEquals(1, result.shopFail); assertEquals(2, result.lpOk); assertEquals(4, result.lpFail); assertEquals(2, result.queued);
        assertTrue(result.messages.contains("shop fail shop_failed: disk full")); assertTrue(result.messages.contains("lp fail invalid: invalid uuid"));
        assertTrue(result.messages.contains("lp fail denied")); assertEquals("pack detail", result.messages.getFirst());
        assertFalse(PackPullRunner.isRunning());
    }

    @Test void emptyPullFlushesExistingQueueButDoesNotReloadShop() {
        var reload = mock(DeferredIaReloadService.class); var queue = new PendingReloadQueue(plugin);
        queue.enqueue(List.of("pending")); when(reload.queue()).thenReturn(queue); when(plugin.getDeferredIaReloadService()).thenReturn(reload);
        assertTrue(PackPullRunner.run(false, null)); finish(); verify(reload).requestFlush(false); verify(plugin, never()).reload();
        queue.clear(List.of("pending")); clearInvocations(reload);
        assertTrue(PackPullRunner.run(false, null)); finish(); verify(reload, never()).requestFlush(anyBoolean());
    }

    @Test void workerFailuresReturnDiagnosticResultAndReleaseLock() {
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenThrow(new IllegalStateException("broken"));
        List<PackPullRunner.PullResult> completed = new ArrayList<>();
        assertTrue(PackPullRunner.run(false, completed::add)); finish();
        var result = completed.getFirst(); assertTrue(result.failed); assertFalse(result.busy); assertEquals("broken", result.error);
        assertEquals("Pack pull failed: broken", result.summaryLine()); assertTrue(result.messages.isEmpty()); assertFalse(PackPullRunner.isRunning());
        assertEquals("Pack pull failed: unknown", PackPullRunner.PullResult.failed(null).summaryLine());
        assertTrue(PackPullRunner.run(false, null)); finish(); assertFalse(PackPullRunner.isRunning());
    }

    @Test void linkageFailurePropagatesAndAllowsTheNextPull() {
        LinkageError failure = new NoClassDefFoundError("optional/plugin/Api");
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenThrow(failure);
        assertTrue(PackPullRunner.run(false, null));
        assertSame(failure, assertThrows(LinkageError.class, () -> workers.removeFirst().run()));
        assertFalse(PackPullRunner.isRunning(), "A failed worker must not permanently block pulls");
        assertTrue(main.isEmpty(), "A fatal failure must not report a successful pull");
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenReturn(
            new PackApplyService.ApplySummary(0, 0, 0, List.of(), List.of()));
        assertTrue(PackPullRunner.run(false, null));
        finish();
        assertFalse(PackPullRunner.isRunning());
    }

    @Test void callbackExceptionsStillReleasePullLock() {
        assertTrue(PackPullRunner.run(false, result -> { throw new IllegalArgumentException("callback"); }));
        workers.removeFirst().run(); assertThrows(IllegalArgumentException.class, () -> main.removeFirst().run()); assertFalse(PackPullRunner.isRunning());
        apply.when(() -> PackApplyService.pullAndWrite(log)).thenThrow(new IllegalStateException("worker"));
        assertTrue(PackPullRunner.run(false, result -> { throw new IllegalArgumentException("callback"); }));
        workers.removeFirst().run(); assertThrows(IllegalArgumentException.class, () -> main.removeFirst().run()); assertFalse(PackPullRunner.isRunning());
    }

    @Test void schedulerRejectionDoesNotPermanentlyLockPackPulls() {
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenThrow(new IllegalStateException("plugin disabled"));
        assertThrows(IllegalStateException.class, () -> PackPullRunner.run(false, null));
        assertFalse(PackPullRunner.isRunning());
    }

    @Test void failureCallbackSchedulingRejectionReleasesLock() {
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenThrow(new IllegalStateException("plugin disabled"));
        assertTrue(PackPullRunner.run(false, null));
        assertThrows(IllegalStateException.class, () -> workers.removeFirst().run());
        assertFalse(PackPullRunner.isRunning());
    }

    @Test void schedulerStartsStopsAndRunsOnlyDuringConfiguredMinuteOncePerDate() {
        var daily = new PackPullScheduler(plugin); List<Runnable> ticks = new ArrayList<>(); var task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(600L), eq(1200L))).thenAnswer(i -> { ticks.add(i.getArgument(1)); return task; });
        Cache.forceReloadTime = null; daily.start(); ticks.getLast().run();
        Cache.forceReloadTime = " "; daily.start(); ticks.getLast().run(); verify(task).cancel();
        Cache.forceReloadTime = "garbage"; ticks.getLast().run(); verify(log).warning(contains("invalid force-reload-time"));
        Cache.forceReloadTime = " 06:00 "; daily.start();
        LocalDate today = LocalDate.of(2026, 10, 2), tomorrow = today.plusDays(1);
        LocalTime beforeHour = LocalTime.of(5, 0), beforeMinute = LocalTime.of(6, 1), target = LocalTime.of(6, 0, 15);
        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS); var times = mockStatic(LocalTime.class, CALLS_REAL_METHODS); var pulls = mockStatic(PackPullRunner.class)) {
            dates.when(LocalDate::now).thenReturn(today);
            times.when(LocalTime::now).thenReturn(beforeHour, beforeMinute, target, target, target);
            pulls.when(() -> PackPullRunner.run(true, null)).thenReturn(true);
            ticks.getLast().run(); ticks.getLast().run(); pulls.verifyNoInteractions();
            ticks.getLast().run(); ticks.getLast().run(); pulls.verify(() -> PackPullRunner.run(true, null), times(1));
            dates.when(LocalDate::now).thenReturn(tomorrow); ticks.getLast().run(); pulls.verify(() -> PackPullRunner.run(true, null), times(2));
        }
        daily.stop(); daily.stop(); verify(task, times(3)).cancel();
    }
}

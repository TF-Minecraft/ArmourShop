package net.tfminecraft.armourshop.pack.reload;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingReloadQueueGenerationTest {
    @TempDir Path directory;
    JavaPlugin plugin;
    Logger logger;
    PendingReloadQueue queue;

    @BeforeEach void setup() {
        plugin = mock(JavaPlugin.class);
        logger = mock(Logger.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        queue = new PendingReloadQueue(plugin);
    }

    @Test void reenqueueOfTheSameIdSurvivesAnOlderAcknowledgement() {
        queue.enqueue(List.of("one", "two"));
        Map<String, Long> reloading = queue.snapshotVersions();
        queue.enqueue(List.of(" one "));
        Map<String, Long> current = queue.snapshotVersions();
        assertTrue(current.get("one") > reloading.get("one"));
        assertEquals(reloading.get("two"), current.get("two"));
        assertEquals(List.of("two"), queue.matchingIds(reloading));
        queue.clearIfUnchanged(List.of("one", "two"), reloading);
        assertEquals(List.of("one"), queue.snapshot());
        assertEquals(List.of("one"), savedIds());
        queue.clearIfUnchanged(List.of("one"), current);
        assertTrue(queue.isEmpty());
        assertEquals(List.of(), savedIds());
    }

    @Test void stalePruneCannotRemoveRewrittenOrNewlyQueuedWork() {
        queue.enqueue(List.of("rewritten", "revoked"));
        Map<String, Long> beforeRemoteApprovalLookup = queue.snapshotVersions();
        queue.enqueue(List.of("rewritten", "new"));
        queue.clearIfUnchanged(List.of("rewritten", "revoked", "new"), beforeRemoteApprovalLookup);
        assertEquals(List.of("rewritten", "new"), queue.snapshot());
        assertEquals(List.of(), queue.matchingIds(beforeRemoteApprovalLookup));
        assertEquals(List.of("rewritten", "new"), savedIds());
    }

    @Test void capturesAreDetachedAndMatchingPreservesCapturedOrder() {
        queue.enqueue(List.of("a", "b", "c"));
        Map<String, Long> capture = queue.snapshotVersions();
        Map<String, Long> reversed = new LinkedHashMap<>();
        reversed.put("c", capture.get("c")); reversed.put("a", capture.get("a"));
        reversed.put("absent", 99L); reversed.put("null-value", null);
        assertEquals(List.of("c", "a"), queue.matchingIds(reversed));
        List<String> matches = queue.matchingIds(capture); matches.clear();
        capture.clear(); List<String> ids = queue.snapshot(); ids.clear();
        assertEquals(List.of("a", "b", "c"), queue.snapshot());
        assertEquals(3, queue.snapshotVersions().size());
        assertEquals(3, queue.size());
    }

    @Test void loadKeepsTheLegacyYamlFormatAndInvalidatesOlderRuntimeCaptures() throws Exception {
        queue.load(); assertTrue(queue.isEmpty());
        Files.writeString(file(), "submission-ids:\n  - ' a '\n  - ''\n  - ' '\n  - a\n  - b\n");
        queue.load(); assertEquals(List.of("a", "b"), queue.snapshot());
        Map<String, Long> beforeReload = queue.snapshotVersions();
        queue.load(); Map<String, Long> afterReload = queue.snapshotVersions();
        assertTrue(afterReload.get("a") > beforeReload.get("a"));
        assertTrue(afterReload.get("b") > beforeReload.get("b"));
        queue.clearIfUnchanged(List.of("a", "b"), beforeReload);
        assertEquals(List.of("a", "b"), queue.snapshot());
        queue.enqueue(List.of("c"));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(file().toFile());
        assertEquals(Set.of("submission-ids"), saved.getKeys(false));
        assertEquals(List.of("a", "b", "c"), saved.getStringList("submission-ids"));
        PendingReloadQueue restarted = new PendingReloadQueue(plugin); restarted.load();
        assertEquals(List.of("a", "b", "c"), restarted.snapshot());
        Files.delete(file()); queue.load(); assertTrue(queue.isEmpty());
        queue.enqueue(List.of("a"));
        assertTrue(queue.snapshotVersions().get("a") > afterReload.get("a"));
    }

    @Test void invalidInputsNeverClearLiveGenerationsAndAckIdsAreTrimmed() {
        queue.enqueue(null); queue.enqueue(List.of()); queue.enqueue(Arrays.asList(null, " "));
        assertTrue(queue.isEmpty());
        queue.enqueue(List.of("a", "b")); Map<String, Long> capture = queue.snapshotVersions();
        assertEquals(List.of(), queue.matchingIds(null)); assertEquals(List.of(), queue.matchingIds(Map.of()));
        queue.clearIfUnchanged(null, capture); queue.clearIfUnchanged(List.of(), capture);
        queue.clearIfUnchanged(List.of("a"), null); queue.clearIfUnchanged(List.of("a"), Map.of());
        queue.clearIfUnchanged(Arrays.asList(null, " ", "missing"), capture);
        Map<String, Long> nullVersion = new HashMap<>(); nullVersion.put("a", null);
        queue.clearIfUnchanged(List.of("a"), nullVersion);
        assertEquals(List.of("a", "b"), queue.snapshot());
        queue.clearIfUnchanged(Arrays.asList(null, " a ", "a"), capture);
        assertEquals(List.of("b"), queue.snapshot()); assertEquals(List.of("b"), savedIds());
    }

    @Test void clearAndRetainPreserveTheirExistingPublicBehavior() {
        queue.enqueue(Arrays.asList(null, " ", " a ", "b", "a"));
        Map<String, Long> original = queue.snapshotVersions();
        queue.enqueue(List.of("a")); assertTrue(queue.snapshotVersions().get("a") > original.get("a"));
        queue.clear(null); queue.clear(List.of()); queue.clear(Arrays.asList(null, "missing"));
        assertEquals(2, queue.size());
        assertEquals(2, queue.retainAll(Arrays.asList(null, " ", " a ", "never-written")));
        assertEquals(List.of("a"), queue.snapshot());
        Long keptGeneration = queue.snapshotVersions().get("a");
        assertEquals(1, queue.retainAll(List.of("a")));
        assertEquals(keptGeneration, queue.snapshotVersions().get("a"));
        queue.clear(List.of(" a ")); assertTrue(queue.isEmpty());
        queue.enqueue(List.of("a"));
        assertTrue(queue.snapshotVersions().get("a") > keptGeneration);
        queue.clearIfUnchanged(List.of("a"), original); assertEquals(List.of("a"), queue.snapshot());
        assertEquals(1, queue.retainAll(null)); assertTrue(queue.isEmpty()); assertEquals(List.of(), savedIds());
    }

    @Test void failedPersistenceRetainsGenerationChecksAndLogsTheFailure() throws Exception {
        Files.createDirectory(file());
        queue.enqueue(List.of("a")); Map<String, Long> old = queue.snapshotVersions();
        queue.enqueue(List.of("a"));
        queue.clearIfUnchanged(List.of("a"), old); assertEquals(List.of("a"), queue.snapshot());
        queue.clearIfUnchanged(List.of("a"), queue.snapshotVersions()); assertTrue(queue.isEmpty());
        verify(logger, times(2)).warning(contains("failed to save pending-reload.yml"));
    }

    Path file() { return directory.resolve("pending-reload.yml"); }
    List<String> savedIds() { return YamlConfiguration.loadConfiguration(file().toFile()).getStringList("submission-ids"); }
}

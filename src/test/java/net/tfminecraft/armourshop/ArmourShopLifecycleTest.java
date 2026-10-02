package net.tfminecraft.armourshop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.armourshop.loaders.*;
import net.tfminecraft.armourshop.pack.apply.PackPullScheduler;
import net.tfminecraft.armourshop.pack.catalog.CatalogSyncService;
import net.tfminecraft.armourshop.pack.delete.DeletableSubmissionCache;
import net.tfminecraft.armourshop.pack.reload.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class ArmourShopLifecycleTest {
    ArmourShop previous;
    @BeforeEach void setup(){previous=ArmourShop.plugin;MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();ArmourShop.plugin=previous;}
    @Test void enableReloadAndDisableKeepPendingWorkAndConfigureCommands()throws Exception{
        try(var config=mockConstruction(ConfigLoader.class);var categories=mockConstruction(CategoryLoader.class);var bases=mockConstruction(BaseSetLoader.class);var permissions=mockConstruction(PermissionGroupsLoader.class);var skins=mockConstruction(SkinSetLoader.class);
            var queues=mockConstruction(PendingReloadQueue.class,(q,c)->{when(q.isEmpty()).thenReturn(false);when(q.size()).thenReturn(2);});var reloads=mockConstruction(DeferredIaReloadService.class);var schedulers=mockConstruction(PackPullScheduler.class);var deletable=mockStatic(DeletableSubmissionCache.class);var catalog=mockStatic(CatalogSyncService.class)){
            ArmourShop plugin=MockBukkit.load(ArmourShop.class);assertSame(plugin,ArmourShop.plugin);
            assertSame(queues.constructed().getFirst(),plugin.getPendingReloadQueue());assertSame(reloads.constructed().getFirst(),plugin.getDeferredIaReloadService());
            verify(plugin.getPendingReloadQueue()).load();verify(plugin.getDeferredIaReloadService()).requestFlush(true);verify(schedulers.constructed().getFirst()).start();
            assertNotNull(plugin.getCommand("armourshop").getExecutor());assertNotNull(plugin.getCommand("armourshop").getTabCompleter());
            for(String file:List.of("config.yml","categories.yml","base-sets.yml","permission-groups.yml"))assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve(file)));
            Path categoryDir=plugin.getDataFolder().toPath().resolve("Categories");Files.createDirectory(categoryDir.resolve("nested"));Path skin=Files.writeString(categoryDir.resolve("staff.yml"),"test: true\n");
            plugin.createFolders();plugin.createConfigs();plugin.reload();verify(skins.constructed().getFirst()).load(skin.toFile());
            Player player=mock(Player.class);plugin.reloadMessage(player);verify(player,times(2)).sendMessage(anyString());
            verify(plugin.getPendingReloadQueue(),times(1)).load();
            plugin.onDisable();verify(schedulers.constructed().getFirst()).stop();
            try(var paths=Files.walk(plugin.getDataFolder().toPath())){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
            plugin.onEnable();assertTrue(Files.isDirectory(categoryDir));
            MockBukkit.getMock().getPluginManager().disablePlugin(plugin);
        }
    }
    @Test void disableBeforeEnableIsSafe(){ArmourShop uninitialized=mock(ArmourShop.class);doCallRealMethod().when(uninitialized).onDisable();uninitialized.onDisable();}
}

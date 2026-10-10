package net.tfminecraft.armourshop.managers;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.scheduler.*;
import org.bukkit.persistence.*;
import net.tfminecraft.armourshop.ArmourShop;
import dev.lone.itemsadder.api.CustomStack;
import org.mockito.MockedStatic;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookSignSkinListenerTest {
    // MockBukkit has no Adventure book-page implementation. Extend that boundary only;
    // real inventories, item cloning, ordinary metadata, PDC and the listener still execute.
    private static final class RichBookMeta extends org.mockbukkit.mockbukkit.inventory.meta.BookMetaMock {
        private List<net.kyori.adventure.text.Component> richPages;
        RichBookMeta() {}
        private RichBookMeta(RichBookMeta source) {super(source); richPages = source.richPages;}
        @Override public RichBookMeta clone() {return new RichBookMeta(this);}
        @Override public List<net.kyori.adventure.text.Component> pages() {
            return richPages != null ? richPages : getPages().stream().map(net.kyori.adventure.text.Component::text).map(value -> (net.kyori.adventure.text.Component) value).toList();
        }
        @Override public RichBookMeta pages(List<net.kyori.adventure.text.Component> pages) {
            richPages = List.copyOf(pages);
            super.setPages(pages.stream().map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList());
            return this;
        }
        @Override public void setPages(List<String> pages) {richPages = null; super.setPages(pages);}
        @Override public void setPages(String... pages) {richPages = null; super.setPages(pages);}
        @Override public boolean equals(Object other) {return super.equals(other) && other instanceof RichBookMeta rich && pages().equals(rich.pages());}
        @Override public int hashCode() {return Objects.hash(super.hashCode(), pages());}
    }
    @Test void existingListenerInvokesPreserverAndKeepsRegistration() throws Exception {
        PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
        try (var helpers = mockConstruction(BookEditSkinPreserver.class)) {
            new BookSignSkinListener().onSignBook(event);
            assertEquals(1, helpers.constructed().size());
            verify(helpers.constructed().get(0)).onEditBook(event);
        }
        EventHandler handler = BookSignSkinListener.class.getMethod("onSignBook", PlayerEditBookEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    @Test void signingInOffHandReadsOffHandSlot() {
        PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(event.isSigning()).thenReturn(true);
        when(event.getSlot()).thenReturn(-1);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        new BookSignSkinListener().onSignBook(event);
        verify(inventory).getItem(40);
        verify(inventory, never()).getItem(-1);
    }

    @Nested class RuntimePaths {
        ServerMock server;
        PlayerMock player;
        ArmourShop plugin, previousPlugin;
        BukkitScheduler scheduler;
        CustomStack custom, target;
        Deque<Runnable> tasks;
        MockedStatic<Bukkit> bukkit;
        MockedStatic<CustomStack> stacks;
        BookSignSkinListener listener;
        @BeforeEach void setupRuntime() {
            server = MockBukkit.mock(); player = server.addPlayer(); previousPlugin = ArmourShop.plugin;
            plugin = mock(ArmourShop.class); when(plugin.getName()).thenReturn("ArmourShop"); when(plugin.namespace()).thenReturn("armourshop");
            when(plugin.getServer()).thenReturn(server); when(plugin.getLogger()).thenReturn(mock(Logger.class)); when(plugin.isEnabled()).thenReturn(true); ArmourShop.plugin = plugin;
            scheduler = mock(BukkitScheduler.class); tasks = new ArrayDeque<>();
            bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var itemFactory = spy(server.getItemFactory());
            doAnswer(inv -> ((RichBookMeta) inv.getArgument(0)).clone()).when(itemFactory).asMetaFor(isA(RichBookMeta.class), any(ItemStack.class));
            doAnswer(inv -> ((RichBookMeta) inv.getArgument(0)).clone()).when(itemFactory).asMetaFor(isA(RichBookMeta.class), any(Material.class));
            bukkit.when(Bukkit::getItemFactory).thenReturn(itemFactory);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(1L))).thenAnswer(inv -> {tasks.add(inv.getArgument(1)); return mock(BukkitTask.class);});
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(inv -> {tasks.add(inv.getArgument(1)); return mock(BukkitTask.class);});
            custom = mock(CustomStack.class); target = mock(CustomStack.class); when(custom.getNamespace()).thenReturn("skins"); when(custom.getId()).thenReturn("book");
            stacks = mockStatic(CustomStack.class); stacks.when(() -> CustomStack.byItemStack(any(ItemStack.class))).thenReturn(custom);
            stacks.when(() -> CustomStack.getInstance("skins:book_signed")).thenReturn(target);
            var targetItem = new ItemStack(Material.WRITTEN_BOOK); var appearance = new RichBookMeta(); appearance.setCustomModelData(99); targetItem.setItemMeta(appearance); when(target.getItemStack()).thenReturn(targetItem);
            listener = new BookSignSkinListener();
        }
        @AfterEach void restoreRuntime() {stacks.close(); bukkit.close(); MockBukkit.unmock(); ArmourShop.plugin = previousPlugin;}
        ItemStack book(Material material, String text) {
            var item = new ItemStack(material); var meta = new RichBookMeta(); meta.setPages(text);
            if (material == Material.WRITTEN_BOOK) {meta.setTitle("Title"); meta.setAuthor("Writer");}
            item.setItemMeta(meta); return item;
        }
        PlayerEditBookEvent signing() {
            var original = book(Material.WRITABLE_BOOK, "Old page"); var previous = (BookMeta) original.getItemMeta();
            previous.setDisplayName("Personal name"); previous.setLore(List.of("Lore")); previous.setCustomModelData(7);
            previous.getPersistentDataContainer().set(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING, "owner"); original.setItemMeta(previous);
            player.getInventory().setItem(2, original);
            var content = (BookMeta) book(Material.WRITTEN_BOOK, "Edited page").getItemMeta(); content.setGeneration(BookMeta.Generation.COPY_OF_ORIGINAL);
            return new PlayerEditBookEvent(player, 2, previous, content, true);
        }
        ItemStack save(PlayerEditBookEvent event, int amount) {
            var saved = new ItemStack(Material.WRITTEN_BOOK, amount); saved.setItemMeta(event.getNewBookMeta()); player.getInventory().setItem(2, saved); return saved;
        }
        @Test void validSigningPreservesContentNamesLoreTagsAndTargetAppearance() {
            var event = signing(); var template = target.getItemStack(); listener.onSignBook(event); save(event, 2);
            tasks.remove().run(); var result = player.getInventory().getItem(2); var meta = (BookMeta) result.getItemMeta();
            assertEquals(Material.WRITTEN_BOOK, result.getType()); assertEquals(2, result.getAmount()); assertEquals(1, template.getAmount());
            assertEquals(List.of("Edited page"), meta.getPages()); assertEquals("Title", meta.getTitle()); assertEquals("Writer", meta.getAuthor());
            assertEquals(BookMeta.Generation.COPY_OF_ORIGINAL, meta.getGeneration()); assertEquals("Personal name", meta.getDisplayName()); assertEquals(List.of("Lore"), meta.getLore());
            assertEquals(99, meta.getCustomModelData()); assertEquals("owner", meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING));
            assertFalse(((BookMeta) template.getItemMeta()).hasTitle()); assertTrue(((BookMeta) template.getItemMeta()).getPages().isEmpty());
            verify(plugin.getLogger()).info(contains("skins:book -> book_signed"));
        }
        @Test void signingAnOriginalNeverKeepsTheTemplatesTatteredGeneration() {
            // ItemsAdder written-book templates default to Tattered; Paper reports Original as no generation.
            var template = target.getItemStack(); var appearance = (BookMeta) template.getItemMeta();
            appearance.setGeneration(BookMeta.Generation.TATTERED); template.setItemMeta(appearance);
            var event = signing(); var content = event.getNewBookMeta(); content.setGeneration(null); event.setNewBookMeta(content);
            listener.onSignBook(event); save(event, 1); tasks.remove().run();
            assertNotEquals(BookMeta.Generation.TATTERED, ((BookMeta) player.getInventory().getItem(2).getItemMeta()).getGeneration());
        }
        @Test void emptyVanillaAlreadySignedAndMissingIaDefinitionsDoNotScheduleReplacement() {
            var event = signing(); player.getInventory().setItem(2, null); listener.onSignBook(event);
            player.getInventory().setItem(2, new ItemStack(Material.AIR)); listener.onSignBook(event);
            event = signing(); stacks.when(() -> CustomStack.byItemStack(any(ItemStack.class))).thenReturn(null); listener.onSignBook(event);
            stacks.when(() -> CustomStack.byItemStack(any(ItemStack.class))).thenReturn(custom);
            for (String invalid : Arrays.asList(null, " ")) {
                when(custom.getNamespace()).thenReturn(invalid); listener.onSignBook(event);
                when(custom.getNamespace()).thenReturn("skins"); when(custom.getId()).thenReturn(invalid); listener.onSignBook(event); when(custom.getId()).thenReturn("book");
            }
            when(custom.getId()).thenReturn("book_signed"); listener.onSignBook(event); when(custom.getId()).thenReturn("book");
            stacks.when(() -> CustomStack.getInstance("skins:book_signed")).thenReturn(null); listener.onSignBook(event); assertTrue(tasks.isEmpty());
        }
        @Test void disconnectedPlayersKeepTheSavedBookWithoutDeferredReplacement() {
            var event = signing(); listener.onSignBook(event); var saved = save(event, 1); player.disconnect(); tasks.remove().run();
            assertEquals(saved, player.getInventory().getItem(2)); verify(target, never()).getItemStack();
        }
        @Test void invalidTargetStacksAndTargetMetadataLeaveSavedBookUntouched() {
            var wrongMeta = mock(ItemStack.class); when(wrongMeta.getType()).thenReturn(Material.WRITTEN_BOOK); when(wrongMeta.clone()).thenReturn(wrongMeta); when(wrongMeta.getItemMeta()).thenReturn(mock(ItemMeta.class));
            for (ItemStack invalid : Arrays.asList(null, new ItemStack(Material.AIR), wrongMeta)) {
                var event = signing(); when(target.getItemStack()).thenReturn(invalid); listener.onSignBook(event); var saved = save(event, 1);
                tasks.remove().run(); assertEquals(saved, player.getInventory().getItem(2));
            }
        }
        @Test void emptyAndDifferentWrittenBooksAreNeverMistakenForTheSavedResult() {
            for (String difference : List.of("empty", "pages", "title", "author")) {
                var event = signing(); listener.onSignBook(event); ItemStack replacement = null;
                if (!difference.equals("empty")) {
                    replacement = book(Material.WRITTEN_BOOK, "Edited page"); var meta = (BookMeta) replacement.getItemMeta();
                    switch (difference) {case "pages" -> meta.setPages("Other page"); case "title" -> meta.setTitle("Other title"); case "author" -> meta.setAuthor("Someone else");}
                    replacement.setItemMeta(meta);
                }
                player.getInventory().setItem(2, replacement); tasks.remove().run(); assertEquals(replacement, player.getInventory().getItem(2));
            }
        }
        @Test void legacyPdcCopyFailuresStillRetainWritingAndVisibleCustomMetadata() {
            for (Throwable failure : List.of(new UnsupportedOperationException("old API"), new NoSuchMethodError("copyTo"))) {
                var event = signing(); var previous = mock(ItemMeta.class); when(previous.clone()).thenReturn(previous);
                when(previous.hasDisplayName()).thenReturn(true); when(previous.getDisplayName()).thenReturn("Legacy name");
                var data = mock(PersistentDataContainer.class); when(previous.getPersistentDataContainer()).thenReturn(data); doThrow(failure).when(data).copyTo(any(), eq(true));
                var original = mock(ItemStack.class); when(original.clone()).thenReturn(original); when(original.getAmount()).thenReturn(1); when(original.getType()).thenReturn(Material.WRITABLE_BOOK); when(original.getItemMeta()).thenReturn(previous); player.getInventory().setItem(2, original);
                listener.onSignBook(event); save(event, 1); tasks.remove().run(); var meta = (BookMeta) player.getInventory().getItem(2).getItemMeta();
                assertEquals(List.of("Edited page"), meta.getPages()); assertEquals("Legacy name", meta.getDisplayName());
            }
        }
        @Test void unsignedEventUsesTheRealStaticPreserverSchedulerAdapter() {
            // MockBukkit does not implement BookMeta.spigot(); mock only that legacy boundary.
            var event = mock(PlayerEditBookEvent.class); var previous = mock(BookMeta.class); var preserved = mock(BookMeta.class); var edited = mock(BookMeta.class);
            var editedPages = mock(BookMeta.Spigot.class); var preservedPages = mock(BookMeta.Spigot.class);
            var pages = List.<net.md_5.bungee.api.chat.BaseComponent[]>of(new net.md_5.bungee.api.chat.BaseComponent[]{new net.md_5.bungee.api.chat.TextComponent("Rich writing")});
            when(event.getPlayer()).thenReturn(player); when(event.getSlot()).thenReturn(2); when(event.getPreviousBookMeta()).thenReturn(previous); when(event.getNewBookMeta()).thenReturn(edited);
            when(previous.clone()).thenReturn(preserved); when(preserved.spigot()).thenReturn(preservedPages); when(edited.spigot()).thenReturn(editedPages); when(editedPages.getPages()).thenReturn(pages);
            var original = book(Material.WRITABLE_BOOK, "Old"); var meta = original.getItemMeta(); meta.setCustomModelData(7); original.setItemMeta(meta); player.getInventory().setItem(2, original);
            listener.onSignBook(event); verify(preservedPages).setPages(pages); verify(event).setNewBookMeta(preserved); assertEquals(1, tasks.size());
            tasks.remove().run(); assertEquals(original, player.getInventory().getItem(2)); verify(scheduler).runTask(eq(plugin), any(Runnable.class));
        }
        @Test void signingNeverOverwritesAnItemPlacedInTheOriginalSlotNextTick() {
            var event = signing(); listener.onSignBook(event); var replacement = new ItemStack(Material.DIAMOND, 3);
            player.getInventory().setItem(2, replacement); tasks.remove().run(); assertEquals(replacement, player.getInventory().getItem(2));
        }
        @Test void matchingWritingOnAnotherSkinNeverReplacesTheDifferentBook() {
            for (String difference : List.of("pdc", "model")) {
                var event = signing(); listener.onSignBook(event); var replacement = save(event, 1);
                var meta = replacement.getItemMeta();
                if (difference.equals("pdc")) meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "other"), PersistentDataType.STRING, "different owner");
                else meta.setCustomModelData(1234);
                replacement.setItemMeta(meta); player.getInventory().setItem(2, replacement);
                tasks.remove().run(); assertEquals(replacement, player.getInventory().getItem(2), difference + " identifies a different book");
            }
        }
        @Test void signingPreservesRichPageClickAndHoverEvents() {
            var event = signing(); var content = event.getNewBookMeta();
            var rich = net.kyori.adventure.text.Component.text("Interactive writing")
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://example.invalid/book"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Hover details")));
            content.pages(List.of(rich)); event.setNewBookMeta(content); listener.onSignBook(event); save(event, 1);
            tasks.remove().run(); assertEquals(List.of(rich), ((BookMeta) player.getInventory().getItem(2).getItemMeta()).pages());
        }
        @Test void signingRejectsNonBookTargetsWithoutLosingWrittenContent() {
            var event = signing(); when(target.getItemStack()).thenReturn(new ItemStack(Material.PAPER)); listener.onSignBook(event); var saved = save(event, 1);
            tasks.remove().run(); assertEquals(saved, player.getInventory().getItem(2));
        }
        @Test void signingUsesFinalHandlerContentAndSavedStackAmount() {
            var event = signing(); listener.onSignBook(event);
            var finalContent = event.getNewBookMeta(); finalContent.setPages("Final formatted page"); event.setNewBookMeta(finalContent); save(event, 2);
            tasks.remove().run(); var result = player.getInventory().getItem(2); var meta = (BookMeta) result.getItemMeta();
            assertEquals(List.of("Final formatted page"), meta.getPages()); assertEquals(2, result.getAmount());
        }
    }
}

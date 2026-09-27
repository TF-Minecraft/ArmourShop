package net.tfminecraft.armourshop.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class BookEditSkinPreserverTest {
    private final PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final ItemStack item = mock(ItemStack.class);
    private final List<Runnable> tasks = new ArrayList<>();

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    private void slot(int slot) {
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(41);
        when(event.getSlot()).thenReturn(slot);
        when(inventory.getItem(slot)).thenReturn(item);
        when(item.getType()).thenReturn(Material.WRITABLE_BOOK);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @Test void preservesOriginalMetadataAndNewRichPagesInEitherHand() {
        for (int slot : new int[] {0, 40}) {
            slot(slot);
            BookMeta previous = mock(BookMeta.class);
            BookMeta preserved = mock(BookMeta.class);
            BookMeta edited = mock(BookMeta.class);
            BookMeta.Spigot originalPages = mock(BookMeta.Spigot.class);
            BookMeta.Spigot editedPages = mock(BookMeta.Spigot.class);
            List<BaseComponent[]> pages = List.<BaseComponent[]>of(new BaseComponent[] {new TextComponent("New page")});
            when(event.getPreviousBookMeta()).thenReturn(previous);
            when(previous.clone()).thenReturn(preserved);
            when(event.getNewBookMeta()).thenReturn(edited);
            when(preserved.spigot()).thenReturn(originalPages);
            when(edited.spigot()).thenReturn(editedPages);
            when(editedPages.getPages()).thenReturn(pages);
            new BookEditSkinPreserver(stack -> stack == item, tasks::add).onEditBook(event);
            verify(originalPages).setPages(pages);
            verify(event).setNewBookMeta(preserved);
            // The book is only checked after every handler has run, never during the event.
            verify(inventory, never()).setItem(anyInt(), any());
            assertEquals(1, tasks.size());
            tasks.clear();
            verify(preserved).spigot();
            verifyNoMoreInteractions(preserved);
        }
    }

    @Test void skipsSigningAndCancelledEdits() {
        when(event.isSigning()).thenReturn(true);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        verify(event, never()).getPlayer();
        when(event.isSigning()).thenReturn(false);
        when(event.isCancelled()).thenReturn(true);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        verify(event, never()).getPlayer();
    }

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    @Test void skipsVanillaBooksAndInvalidSlots() {
        slot(0);
        new BookEditSkinPreserver(stack -> false, tasks::add).onEditBook(event);
        verify(event, never()).setNewBookMeta(any());
        when(event.getSlot()).thenReturn(-2);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        when(event.getSlot()).thenReturn(41);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        verify(event, never()).getPreviousBookMeta();
    }

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    @Test void readsOffHandBookWhenPaperReportsMinusOne() {
        slot(40);
        when(event.getSlot()).thenReturn(-1);
        new BookEditSkinPreserver(stack -> false, tasks::add).onEditBook(event);
        verify(inventory).getItem(40);
        verify(inventory, never()).getItem(-1);
    }

    // Legacy custom model data is how existing skinned books are identified.
    @SuppressWarnings("deprecation")
    @Test void treatsModelledBooksAsCustom() {
        ItemMeta withModelData = mock(ItemMeta.class);
        when(withModelData.hasCustomModelData()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(withModelData);
        assertTrue(BookEditSkinPreserver.isCustomBook(item));

        ItemMeta withItemModel = mock(ItemMeta.class);
        when(withItemModel.hasItemModel()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(withItemModel);
        assertTrue(BookEditSkinPreserver.isCustomBook(item));
    }

    @Test void treatsPlainMmoItemsBooksAsCustom() {
        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
        NBTItem mmoBook = mock(NBTItem.class);
        when(mmoBook.hasType()).thenReturn(true);
        // Checked before ItemsAdder, whose API cannot load without the server.
        try (var nbt = mockStatic(NBTItem.class)) {
            nbt.when(() -> NBTItem.get(item)).thenReturn(mmoBook);
            assertTrue(BookEditSkinPreserver.isCustomBook(item));
        }
    }

    @Test void skipsEmptySlotsAndNonBooks() {
        slot(0);
        when(inventory.getItem(0)).thenReturn(null);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        when(inventory.getItem(0)).thenReturn(item);
        when(item.getType()).thenReturn(Material.STONE);
        new BookEditSkinPreserver(stack -> true, tasks::add).onEditBook(event);
        verify(event, never()).getPreviousBookMeta();
    }

    // A stripped book: plain book and quill that only holds the saved pages.
    private ItemStack strippedBook(int pageCount, List<BaseComponent[]> pages) {
        ItemStack current = mock(ItemStack.class);
        BookMeta meta = mock(BookMeta.class);
        BookMeta.Spigot spigot = mock(BookMeta.Spigot.class);
        when(current.getType()).thenReturn(Material.WRITABLE_BOOK);
        when(current.getItemMeta()).thenReturn(meta);
        when(current.getAmount()).thenReturn(1);
        when(meta.getPageCount()).thenReturn(pageCount);
        when(meta.spigot()).thenReturn(spigot);
        when(spigot.getPages()).thenReturn(pages);
        return current;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    @Test void restoresBookStrippedByLaterHandlerWithSavedPages() {
        slot(0);
        BookMeta edited = mock(BookMeta.class);
        BookMeta preserved = mock(BookMeta.class);
        when(event.getPreviousBookMeta()).thenReturn(preserved);
        when(preserved.clone()).thenReturn(preserved);
        when(preserved.spigot()).thenReturn(mock(BookMeta.Spigot.class));
        when(event.getNewBookMeta()).thenReturn(edited);
        when(edited.spigot()).thenReturn(mock(BookMeta.Spigot.class));
        when(edited.getPageCount()).thenReturn(1);
        ItemStack original = mock(ItemStack.class);
        ItemStack restored = mock(ItemStack.class);
        BookMeta restoredMeta = mock(BookMeta.class);
        BookMeta.Spigot restoredPages = mock(BookMeta.Spigot.class);
        when(item.clone()).thenReturn(original);
        when(original.clone()).thenReturn(restored);
        when(restored.getItemMeta()).thenReturn(restoredMeta);
        when(restoredMeta.spigot()).thenReturn(restoredPages);
        when(player.isOnline()).thenReturn(true);

        Predicate<ItemStack> custom = stack -> stack == item;
        new BookEditSkinPreserver(custom, tasks::add).onEditBook(event);
        // ItemsAdder rebuilds the meta after ArmourShop; the slot now holds a plain book.
        List<BaseComponent[]> savedPages = List.<BaseComponent[]>of(new BaseComponent[] {new TextComponent("Formatted")});
        ItemStack current = strippedBook(1, savedPages);
        when(inventory.getItem(0)).thenReturn(current);
        tasks.forEach(Runnable::run);

        verify(restoredPages).setPages(savedPages);
        verify(restored).setItemMeta(restoredMeta);
        verify(restored).setAmount(1);
        verify(inventory).setItem(0, restored);
    }

    @Test void leavesBookAloneWhenStillCustomMovedOrPlayerOffline() {
        slot(0);
        ItemStack original = mock(ItemStack.class);
        List<BaseComponent[]> pages = List.<BaseComponent[]>of(new BaseComponent[] {new TextComponent("Page")});
        when(player.isOnline()).thenReturn(true);

        // Still custom: nothing stripped it.
        new BookEditSkinPreserver(stack -> true, tasks::add).restoreIfStripped(player, 0, original, 1);
        // Moved away or replaced with something else.
        when(inventory.getItem(0)).thenReturn(null);
        new BookEditSkinPreserver(stack -> false, tasks::add).restoreIfStripped(player, 0, original, 1);
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(inventory.getItem(0)).thenReturn(stone);
        new BookEditSkinPreserver(stack -> false, tasks::add).restoreIfStripped(player, 0, original, 1);
        // A different plain book with another page count.
        ItemStack other = strippedBook(3, pages);
        when(inventory.getItem(0)).thenReturn(other);
        new BookEditSkinPreserver(stack -> false, tasks::add).restoreIfStripped(player, 0, original, 1);
        // Player left before the next tick.
        ItemStack stripped = strippedBook(1, pages);
        when(inventory.getItem(0)).thenReturn(stripped);
        when(player.isOnline()).thenReturn(false);
        new BookEditSkinPreserver(stack -> false, tasks::add).restoreIfStripped(player, 0, original, 1);

        verify(original, never()).clone();
        verify(inventory, never()).setItem(anyInt(), any());
    }
}

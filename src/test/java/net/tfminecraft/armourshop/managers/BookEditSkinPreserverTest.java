package net.tfminecraft.armourshop.managers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
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
import org.junit.jupiter.api.Test;

class BookEditSkinPreserverTest {
    private final PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final ItemStack item = mock(ItemStack.class);

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
            new BookEditSkinPreserver(stack -> stack == item).onEditBook(event);
            verify(originalPages).setPages(pages);
            verify(event).setNewBookMeta(preserved);
            // No inventory replacement or delayed task that could overwrite a moved book.
            verify(inventory, never()).setItem(anyInt(), any());
            verify(preserved).spigot();
            verifyNoMoreInteractions(preserved);
        }
    }

    @Test void skipsSigningAndCancelledEdits() {
        when(event.isSigning()).thenReturn(true);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        verify(event, never()).getPlayer();
        when(event.isSigning()).thenReturn(false);
        when(event.isCancelled()).thenReturn(true);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        verify(event, never()).getPlayer();
    }

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    @Test void skipsVanillaBooksAndInvalidSlots() {
        slot(0);
        new BookEditSkinPreserver(stack -> false).onEditBook(event);
        verify(event, never()).setNewBookMeta(any());
        when(event.getSlot()).thenReturn(-1);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        when(event.getSlot()).thenReturn(41);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        verify(event, never()).getPreviousBookMeta();
    }

    @Test void skipsEmptySlotsAndNonBooks() {
        slot(0);
        when(inventory.getItem(0)).thenReturn(null);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        when(inventory.getItem(0)).thenReturn(item);
        when(item.getType()).thenReturn(Material.STONE);
        new BookEditSkinPreserver(stack -> true).onEditBook(event);
        verify(event, never()).getPreviousBookMeta();
    }
}

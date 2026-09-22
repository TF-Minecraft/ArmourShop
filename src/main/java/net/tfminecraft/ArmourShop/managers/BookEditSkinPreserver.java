package net.tfminecraft.ArmourShop.managers;

import dev.lone.itemsadder.api.CustomStack;

import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

/** Keep the original custom item intact while accepting the final edited pages. */
public final class BookEditSkinPreserver {
    private final Predicate<ItemStack> isCustomBook;

    public BookEditSkinPreserver(Predicate<ItemStack> isCustomBook) {
        this.isCustomBook = isCustomBook;
    }

    // Called by ArmourShop's existing MONITOR listener, registered after ItemsAdder.
    public static void preserve(PlayerEditBookEvent event) {
        new BookEditSkinPreserver(item -> CustomStack.byItemStack(item) != null).onEditBook(event);
    }

    public void onEditBook(PlayerEditBookEvent event) {
        // ArmourShop owns the unsigned -> signed item conversion.
        if (event.isCancelled() || event.isSigning()) return;
        PlayerInventory inventory = event.getPlayer().getInventory();
        int slot = event.getSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() != Material.WRITABLE_BOOK || !isCustomBook.test(item)) return;

        // Earlier formatting handlers can replace BookMeta with a content-only
        // instance. Clone the pre-edit metadata to retain the model, IA identity,
        // name, lore and all other item components, then copy only edited pages.
        BookMeta preserved = event.getPreviousBookMeta().clone();
        preserved.spigot().setPages(event.getNewBookMeta().spigot().getPages());
        event.setNewBookMeta(preserved);
    }
}

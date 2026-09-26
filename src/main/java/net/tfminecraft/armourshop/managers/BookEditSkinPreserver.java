package net.tfminecraft.armourshop.managers;

import dev.lone.itemsadder.api.CustomStack;

import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

/** Keep the original custom item intact while accepting the final edited pages. */
public final class BookEditSkinPreserver {
    private static final int OFF_HAND_SLOT = 40;

    private final Predicate<ItemStack> isCustomBook;

    public BookEditSkinPreserver(Predicate<ItemStack> isCustomBook) {
        this.isCustomBook = isCustomBook;
    }

    // Called by ArmourShop's existing MONITOR listener, registered after ItemsAdder.
    public static void preserve(PlayerEditBookEvent event) {
        new BookEditSkinPreserver(BookEditSkinPreserver::isCustomBook).onEditBook(event);
    }

    // Letters and other skinned books carry a model without always being an IA item.
    // Keep the legacy custom model data check that skinned books were written with.
    @SuppressWarnings("deprecation")
    static boolean isCustomBook(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && (meta.hasCustomModelData() || meta.hasItemModel())
            || CustomStack.byItemStack(item) != null;
    }

    // Paper reports off-hand edits as -1; map them to the off-hand inventory slot.
    @SuppressWarnings({"deprecation", "removal"})
    static int inventorySlot(PlayerEditBookEvent event) {
        int slot = event.getSlot();
        return slot == -1 ? OFF_HAND_SLOT : slot;
    }

    // Retain the originating book slot for deferred restoration; this API exposes no replacement.
    @SuppressWarnings({"deprecation", "removal"})
    public void onEditBook(PlayerEditBookEvent event) {
        // ArmourShop owns the unsigned -> signed item conversion.
        if (event.isCancelled() || event.isSigning()) return;
        PlayerInventory inventory = event.getPlayer().getInventory();
        int slot = inventorySlot(event);
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

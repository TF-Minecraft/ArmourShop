package net.tfminecraft.armourshop.managers;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;

import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import net.tfminecraft.armourshop.ArmourShop;

/** Keep the original custom item intact while accepting the final edited pages. */
public final class BookEditSkinPreserver {
    private static final int OFF_HAND_SLOT = 40;

    private final Predicate<ItemStack> isCustomBook;
    private final Consumer<Runnable> nextTick;

    public BookEditSkinPreserver(Predicate<ItemStack> isCustomBook, Consumer<Runnable> nextTick) {
        this.isCustomBook = isCustomBook;
        this.nextTick = nextTick;
    }

    // Called by ArmourShop's existing MONITOR listener.
    public static void preserve(PlayerEditBookEvent event) {
        new BookEditSkinPreserver(
            BookEditSkinPreserver::isCustomBook,
            task -> Bukkit.getScheduler().runTask(ArmourShop.plugin, task)
        ).onEditBook(event);
    }

    // Letters and other skinned books carry a model without always being an IA item.
    // Keep the legacy custom model data check that skinned books were written with.
    // Plain MMOItems books must keep their id too, or they can no longer be skinned.
    @SuppressWarnings("deprecation")
    static boolean isCustomBook(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && (meta.hasCustomModelData() || meta.hasItemModel())
            || CustomStack.byItemStack(item) != null
            || NBTItem.get(item).hasType();
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
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        int slot = inventorySlot(event);
        if (slot < 0 || slot >= inventory.getSize()) return;
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() != Material.WRITABLE_BOOK || !isCustomBook.test(item)) return;

        // Earlier formatting handlers can replace BookMeta with a content-only
        // instance. Clone the pre-edit metadata to retain the model, IA identity,
        // name, lore and all other item components, then copy only edited pages.
        BookMeta edited = event.getNewBookMeta();
        BookMeta preserved = event.getPreviousBookMeta().clone();
        preserved.spigot().setPages(edited.spigot().getPages());
        event.setNewBookMeta(preserved);

        // ItemsAdder's book formatter also listens at MONITOR but registers after
        // ArmourShop, so it rebuilds the meta from pages only after this handler.
        // Check the saved book once every handler has run.
        ItemStack original = item.clone();
        int pageCount = edited.getPageCount();
        nextTick.accept(() -> restoreIfStripped(player, slot, original, pageCount));
    }

    // Retain the original item's components; only the pages come from the saved book.
    @SuppressWarnings("deprecation")
    void restoreIfStripped(Player player, int slot, ItemStack original, int pageCount) {
        if (!player.isOnline()) return;
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        // Only a book that lost its custom data and still holds the edit is ours to fix;
        // anything else means the book was moved or replaced in the meantime.
        if (current == null || current.getType() != Material.WRITABLE_BOOK || isCustomBook.test(current)) return;
        if (!(current.getItemMeta() instanceof BookMeta stripped) || stripped.getPageCount() != pageCount) return;

        ItemStack restored = original.clone();
        if (!(restored.getItemMeta() instanceof BookMeta meta)) return;
        // Take the pages from the saved book so ItemsAdder's text formatting is kept.
        meta.spigot().setPages(stripped.spigot().getPages());
        restored.setItemMeta(meta);
        restored.setAmount(current.getAmount());
        inventory.setItem(slot, restored);
    }
}

package net.tfminecraft.armourshop.managers;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import dev.lone.itemsadder.api.CustomStack;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;

/** Let Essentials authorize /book, then retain the custom item during its conversion. */
public final class BookCommandSkinListener implements Listener {
    private final Function<String, PluginCommand> commands;
    private final Predicate<ItemStack> customBooks;
    private final BiFunction<ItemStack, ItemStack, ItemStack> restore;

    public BookCommandSkinListener() {
        this(Bukkit::getPluginCommand, BookEditSkinPreserver::isCustomBook,
            BookCommandSkinListener::restoreConversion);
    }

    BookCommandSkinListener(Function<String, PluginCommand> commands,
            Predicate<ItemStack> customBooks,
            BiFunction<ItemStack, ItemStack, ItemStack> restore) {
        this.commands = commands;
        this.customBooks = customBooks;
        this.restore = restore;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String[] words = event.getMessage().substring(1).trim().split("\\s+");
        PluginCommand command = commands.apply(words[0]);
        // Resolve aliases and namespaced commands, without intercepting other plugins' /book.
        if (command == null || !command.getName().equalsIgnoreCase("book")
                || !command.getPlugin().getName().equalsIgnoreCase("Essentials")) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!isBook(held) || !customBooks.test(held)) return;

        ItemStack original = held.clone();
        event.setCancelled(true);
        // Execute the original command synchronously: permissions, ownership, formatting and
        // denial messages stay with Essentials. No deferred slot overwrite can move/duplicate books.
        command.getPlugin().getLogger().info(event.getPlayer().getName()
            + " issued server command: " + event.getMessage());
        try {
            command.execute(event.getPlayer(), words[0], Arrays.copyOfRange(words, 1, words.length));
        } catch (CommandException failure) {
            command.getPlugin().getLogger().log(Level.SEVERE, "Failed custom book command", failure);
            event.getPlayer().sendMessage("An error occurred while editing your book. Please contact staff.");
        }
        ItemStack converted = event.getPlayer().getInventory().getItemInMainHand();
        if (!isBook(converted) || converted.getType() == original.getType()) return;
        event.getPlayer().getInventory().setItemInMainHand(restore.apply(original, converted));
    }

    private static boolean isBook(ItemStack item) {
        return item != null && (item.getType() == Material.WRITABLE_BOOK
            || item.getType() == Material.WRITTEN_BOOK);
    }

    @SuppressWarnings("deprecation")
    static ItemStack restoreConversion(ItemStack original, ItemStack converted) {
        BookMeta previous = (BookMeta) original.getItemMeta();
        BookMeta content = (BookMeta) converted.getItemMeta();
        ItemStack restored = original.clone();
        restored.setType(converted.getType());

        CustomStack custom = CustomStack.byItemStack(original);
        if (custom != null) {
            String id = custom.getId();
            String targetId = converted.getType() == Material.WRITTEN_BOOK
                ? (id.endsWith("_signed") ? id : id + "_signed")
                : (id.endsWith("_signed") ? id.substring(0, id.length() - 7) : id);
            CustomStack target = CustomStack.getInstance(custom.getNamespace() + ":" + targetId);
            if (target != null && target.getItemStack().getType() == converted.getType()) {
                ItemMeta appearance = target.getItemStack().getItemMeta();
                ItemMeta meta = restored.getItemMeta();
                meta.setCustomModelDataComponent(appearance.getCustomModelDataComponent());
                meta.setItemModel(appearance.getItemModel());
                restored.setItemMeta(meta);
                restored = ItemSkinPreserver.writeIaTag(restored, custom.getNamespace(), targetId);
                ItemSkinPreserver.writeItemsAdderCompound(restored, custom.getNamespace(), targetId);
                if (appearance.hasCustomModelData()) {
                    restored = ItemSkinPreserver.writeAmodel(restored, appearance.getCustomModelData());
                }
            }
        }

        // setType clears book content. Use the original pages, avoiding Essentials' legacy
        // plain-string round trip, while retaining every other original item component.
        BookMeta meta = (BookMeta) restored.getItemMeta();
        meta.spigot().setPages(previous.spigot().getPages());
        if (converted.getType() == Material.WRITTEN_BOOK) {
            meta.setTitle(content.hasTitle() ? content.getTitle() : "Book");
            meta.setAuthor(content.getAuthor());
            if (content.hasGeneration()) meta.setGeneration(content.getGeneration());
        }
        restored.setItemMeta(meta);
        restored.setAmount(converted.getAmount());
        return restored;
    }
}

package net.tfminecraft.armourshop.managers;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import net.tfminecraft.tlibs.objects.api.subapi.ItemSkinPreserver;
import dev.lone.itemsadder.api.CustomStack;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class BookCommandSkinListenerTest {
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final PluginCommand command = mock(PluginCommand.class);
    private final Plugin essentials = mock(Plugin.class);
    private final ItemStack held = mock(ItemStack.class);
    private final ItemStack original = mock(ItemStack.class);
    private final ItemStack converted = mock(ItemStack.class);
    private final ItemStack restored = mock(ItemStack.class);

    @BeforeEach void setup() {
        when(player.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(held, converted);
        when(held.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(held.clone()).thenReturn(original);
        when(original.getType()).thenReturn(Material.WRITTEN_BOOK);
        when(converted.getType()).thenReturn(Material.WRITABLE_BOOK);
        when(command.getName()).thenReturn("book");
        when(command.getPlugin()).thenReturn(essentials);
        when(essentials.getName()).thenReturn("Essentials");
        when(essentials.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
    }

    @Test void delegatesNamespacedCommandAndRepairsImmediately() {
        var event = new PlayerCommandPreprocessEvent(player, "/essentials:book unsign");
        var listener = new BookCommandSkinListener(label -> command, item -> true,
            (before, after) -> {
                org.junit.jupiter.api.Assertions.assertSame(original, before);
                org.junit.jupiter.api.Assertions.assertSame(converted, after);
                return restored;
            });
        listener.onBookCommand(event);
        org.junit.jupiter.api.Assertions.assertTrue(event.isCancelled());
        var order = inOrder(command, inventory);
        order.verify(command).execute(eq(player), eq("essentials:book"), aryEq("unsign"));
        order.verify(inventory).setItemInMainHand(restored);
    }

    @Test void deniedConversionNeverReplacesBook() {
        when(converted.getType()).thenReturn(Material.WRITTEN_BOOK);
        new BookCommandSkinListener(label -> command, item -> true, (a, b) -> {
            throw new AssertionError("denied commands must not restore");
        }).onBookCommand(new PlayerCommandPreprocessEvent(player, "/book unsign"));
        verify(command).execute(eq(player), eq("book"), aryEq("unsign"));
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test void titleAndAuthorEditsStayWithEssentials() {
        when(converted.getType()).thenReturn(Material.WRITTEN_BOOK);
        new BookCommandSkinListener(label -> command, item -> true, (a, b) -> restored)
            .onBookCommand(new PlayerCommandPreprocessEvent(player, "/book title My Notebook"));
        verify(command).execute(eq(player), eq("book"), aryEq("title", "My", "Notebook"));
        verify(inventory, never()).setItemInMainHand(any());
    }

    @Test void vanillaBooksAndOtherPluginsAreNotIntercepted() {
        var event = new PlayerCommandPreprocessEvent(player, "/book");
        new BookCommandSkinListener(label -> command, item -> false, (a, b) -> restored)
            .onBookCommand(event);
        org.junit.jupiter.api.Assertions.assertFalse(event.isCancelled());
        when(essentials.getName()).thenReturn("AnotherPlugin");
        new BookCommandSkinListener(label -> command, item -> true, (a, b) -> restored)
            .onBookCommand(event);
        verify(command, never()).execute(any(), any(), any());
    }

    @Test void cancelledCommandsAreNotExecuted() {
        var event = new PlayerCommandPreprocessEvent(player, "/book");
        event.setCancelled(true);
        new BookCommandSkinListener(label -> command, item -> true, (a, b) -> restored)
            .onBookCommand(event);
        verifyNoInteractions(command);
    }

    @Test void executionFailureReportsErrorAndPreservesPartialConversion() {
        when(command.execute(any(), any(), any()))
            .thenThrow(new org.bukkit.command.CommandException("test failure"));
        new BookCommandSkinListener(label -> command, item -> true, (a, b) -> restored)
            .onBookCommand(new PlayerCommandPreprocessEvent(player, "/book unsign"));
        verify(player).sendMessage(contains("error occurred"));
        verify(inventory).setItemInMainHand(restored);
        verify(essentials.getLogger()).log(eq(java.util.logging.Level.SEVERE),
            eq("Failed custom book command"), any(Throwable.class));
    }

    @Test void absentItemsAdderTargetStackPreservesBookContentAndOriginalAppearance() {
        var previous = mock(BookMeta.class); var content = mock(BookMeta.class); var output = mock(BookMeta.class);
        var oldPages = mock(BookMeta.Spigot.class); var newPages = mock(BookMeta.Spigot.class);
        var pages = List.<net.md_5.bungee.api.chat.BaseComponent[]>of(new net.md_5.bungee.api.chat.BaseComponent[]{new net.md_5.bungee.api.chat.TextComponent("Original writing")});
        when(previous.spigot()).thenReturn(oldPages); when(oldPages.getPages()).thenReturn(pages); when(output.spigot()).thenReturn(newPages);
        when(original.getItemMeta()).thenReturn(previous); when(converted.getItemMeta()).thenReturn(content); when(original.clone()).thenReturn(restored); when(restored.getItemMeta()).thenReturn(output);
        when(converted.getAmount()).thenReturn(2);
        try (var stacks = mockStatic(CustomStack.class)) {
            var custom = mock(CustomStack.class); var target = mock(CustomStack.class); when(custom.getId()).thenReturn("book_signed"); when(custom.getNamespace()).thenReturn("skins");
            stacks.when(() -> CustomStack.byItemStack(original)).thenReturn(custom); stacks.when(() -> CustomStack.getInstance("skins:book")).thenReturn(target);
            assertSame(restored, assertDoesNotThrow(() -> BookCommandSkinListener.restoreConversion(original, converted)));
            verify(newPages).setPages(pages); verify(restored).setAmount(2); verify(output, never()).setCustomModelDataComponent(any());
        }
    }

    private record Conversion(BookMeta previous, BookMeta content, BookMeta output, BookMeta.Spigot outputPages,
        List<net.md_5.bungee.api.chat.BaseComponent[]> pages) {}
    private Conversion conversion(Material targetType) {
        // ItemStack and legacy Spigot boundaries are mocked; restoreConversion itself executes.
        var previous = mock(BookMeta.class); var content = mock(BookMeta.class); var output = mock(BookMeta.class);
        var sourcePages = mock(BookMeta.Spigot.class); var outputPages = mock(BookMeta.Spigot.class);
        var rich = new net.md_5.bungee.api.chat.TextComponent("Rich original writing"); rich.setBold(true);
        rich.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, "https://example.invalid/book"));
        var pages = List.<net.md_5.bungee.api.chat.BaseComponent[]>of(new net.md_5.bungee.api.chat.BaseComponent[]{rich});
        when(previous.spigot()).thenReturn(sourcePages); when(sourcePages.getPages()).thenReturn(pages); when(output.spigot()).thenReturn(outputPages);
        when(original.getItemMeta()).thenReturn(previous); when(original.clone()).thenReturn(restored); when(converted.getItemMeta()).thenReturn(content); when(converted.getType()).thenReturn(targetType); when(converted.getAmount()).thenReturn(2); when(restored.getItemMeta()).thenReturn(output);
        return new Conversion(previous, content, output, outputPages, pages);
    }

    @Test void conversionsCopySignedAndUnsignedIaAppearanceWithoutFlatteningRichPages() {
        for (Material type : List.of(Material.WRITTEN_BOOK, Material.WRITABLE_BOOK)) {
            for (String id : List.of("book", "book_signed")) {
                var fixture = conversion(type); String targetId = type == Material.WRITTEN_BOOK ? "book_signed" : "book";
                when(fixture.content.hasTitle()).thenReturn(true); when(fixture.content.getTitle()).thenReturn("Signed title"); when(fixture.content.getAuthor()).thenReturn("Writer"); when(fixture.content.hasGeneration()).thenReturn(true); when(fixture.content.getGeneration()).thenReturn(BookMeta.Generation.COPY_OF_COPY);
                try (var stacks = mockStatic(CustomStack.class); var tags = mockStatic(ItemSkinPreserver.class)) {
                    var custom = mock(CustomStack.class); var target = mock(CustomStack.class); var appearance = mock(BookMeta.class); var targetItem = mock(ItemStack.class); var model = mock(CustomModelDataComponent.class);
                    when(custom.getId()).thenReturn(id); when(custom.getNamespace()).thenReturn("skins"); when(target.getItemStack()).thenReturn(targetItem); when(targetItem.getType()).thenReturn(type); when(targetItem.getItemMeta()).thenReturn(appearance);
                    when(appearance.getCustomModelDataComponent()).thenReturn(model); var itemModel = NamespacedKey.fromString("skins:appearance"); when(appearance.getItemModel()).thenReturn(itemModel);
                    when(appearance.hasCustomModelData()).thenReturn(id.endsWith("_signed")); when(appearance.getCustomModelData()).thenReturn(42);
                    stacks.when(() -> CustomStack.byItemStack(original)).thenReturn(custom); stacks.when(() -> CustomStack.getInstance("skins:" + targetId)).thenReturn(target);
                    tags.when(() -> ItemSkinPreserver.writeIaTag(restored, "skins", targetId)).thenReturn(restored);
                    tags.when(() -> ItemSkinPreserver.writeAmodel(restored, 42)).thenReturn(restored);
                    assertSame(restored, BookCommandSkinListener.restoreConversion(original, converted));
                    verify(fixture.output).setCustomModelDataComponent(model); verify(fixture.output).setItemModel(itemModel); verify(fixture.outputPages).setPages(fixture.pages);
                    verify(target, times(1)).getItemStack(); tags.verify(() -> ItemSkinPreserver.writeItemsAdderCompound(restored, "skins", targetId));
                    tags.verify(() -> ItemSkinPreserver.writeAmodel(restored, 42), times(id.endsWith("_signed") ? 1 : 0));
                    if (type == Material.WRITTEN_BOOK) {verify(fixture.output).setTitle("Signed title"); verify(fixture.output).setAuthor("Writer"); verify(fixture.output).setGeneration(BookMeta.Generation.COPY_OF_COPY);}
                    else {verify(fixture.output, never()).setTitle(any()); verify(fixture.output, never()).setAuthor(any());}
                    verify(fixture.output, never()).setDisplayName(anyString()); verify(fixture.output, never()).setLore(any());
                }
                clearInvocations(restored);
            }
        }
    }

    @Test void missingWrongTypeAndNonBookTargetMetadataKeepOriginalAppearance() {
        for (String invalid : List.of("missing", "null-stack", "wrong-type", "wrong-meta")) {
            var fixture = conversion(Material.WRITABLE_BOOK);
            try (var stacks = mockStatic(CustomStack.class); var tags = mockStatic(ItemSkinPreserver.class)) {
                var custom = mock(CustomStack.class); when(custom.getId()).thenReturn("book_signed"); when(custom.getNamespace()).thenReturn("skins");
                stacks.when(() -> CustomStack.byItemStack(original)).thenReturn(custom);
                if (!invalid.equals("missing")) {
                    var target = mock(CustomStack.class); stacks.when(() -> CustomStack.getInstance("skins:book")).thenReturn(target);
                    if (!invalid.equals("null-stack")) {var stack = mock(ItemStack.class); when(target.getItemStack()).thenReturn(stack); when(stack.getType()).thenReturn(invalid.equals("wrong-type") ? Material.PAPER : Material.WRITABLE_BOOK); when(stack.getItemMeta()).thenReturn(mock(ItemMeta.class));}
                }
                assertSame(restored, BookCommandSkinListener.restoreConversion(original, converted)); verify(fixture.outputPages).setPages(fixture.pages);
                verify(fixture.output, never()).setCustomModelDataComponent(any()); tags.verifyNoInteractions();
            }
        }
    }

    @Test void nonIaSigningUsesDefaultTitleAndConvertedAuthorWhileKeepingOriginalRichPages() {
        var fixture = conversion(Material.WRITTEN_BOOK); when(fixture.content.getAuthor()).thenReturn("Writer");
        try (var stacks = mockStatic(CustomStack.class)) {
            assertSame(restored, BookCommandSkinListener.restoreConversion(original, converted));
            verify(fixture.output).setTitle("Book"); verify(fixture.output).setAuthor("Writer"); verify(fixture.output, never()).setGeneration(any());
            verify(fixture.outputPages).setPages(fixture.pages); verify(restored).setType(Material.WRITTEN_BOOK); verify(restored).setAmount(2);
        }
    }

    @Test void defaultListenerRunsRealCustomBookDetectionAndRestoration() {
        var fixture = conversion(Material.WRITABLE_BOOK); when(held.getItemMeta()).thenReturn(fixture.previous); when(fixture.previous.hasCustomModelData()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var stacks = mockStatic(CustomStack.class)) {
            bukkit.when(() -> Bukkit.getPluginCommand("book")).thenReturn(command);
            var event = new PlayerCommandPreprocessEvent(player, "/book unsign"); new BookCommandSkinListener().onBookCommand(event);
            assertTrue(event.isCancelled()); verify(inventory).setItemInMainHand(restored); verify(fixture.outputPages).setPages(fixture.pages);
        }
    }

    private static String[] aryEq(String... values) {
        return org.mockito.AdditionalMatchers.aryEq(values);
    }
}

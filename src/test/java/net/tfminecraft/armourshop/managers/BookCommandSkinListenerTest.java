package net.tfminecraft.armourshop.managers;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
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

    private static String[] aryEq(String... values) {
        return org.mockito.AdditionalMatchers.aryEq(values);
    }
}

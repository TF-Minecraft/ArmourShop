package net.tfminecraft.armourshop.managers;

import org.junit.jupiter.api.Test;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookSignSkinListenerTest {
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
}

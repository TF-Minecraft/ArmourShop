package net.tfminecraft.armourshop.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import dev.lone.itemsadder.api.CustomStack;
import net.tfminecraft.armourshop.ArmourShop;

/**
 * Preserve custom item metadata when saving unsigned pages.
 * When a player signs an ItemsAdder book skin ({@code slug}), swap the stack to
 * {@code slug_signed} while keeping pages, title, author, display name, lore, and PDC.
 */
public final class BookSignSkinListener implements Listener {

	// Retain the originating book slot for deferred restoration; this API exposes no replacement.
	@SuppressWarnings({"deprecation", "removal"})
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSignBook(PlayerEditBookEvent event) {
		BookEditSkinPreserver.preserve(event);
		if (!event.isSigning()) {
			return;
		}

		Player player = event.getPlayer();
		int slot = BookEditSkinPreserver.inventorySlot(event);
		ItemStack current = player.getInventory().getItem(slot);
		if (current == null || current.getType().isAir()) {
			return;
		}

		CustomStack custom = CustomStack.byItemStack(current);
		if (custom == null) {
			return;
		}

		String namespace = custom.getNamespace();
		String id = custom.getId();
		if (namespace == null || namespace.isBlank() || id == null || id.isBlank()) {
			return;
		}
		if (id.endsWith("_signed")) {
			return;
		}

		String targetId = id + "_signed";
		CustomStack target = CustomStack.getInstance(namespace + ":" + targetId);
		if (target == null) {
			return;
		}

		ItemMeta prevMeta = current.getItemMeta();
		ItemMeta prevMetaClone = prevMeta != null ? prevMeta.clone() : null;
		String displayName = prevMeta != null && prevMeta.hasDisplayName()
			? prevMeta.getDisplayName()
			: null;
		List<String> lore = prevMeta != null && prevMeta.hasLore() && prevMeta.getLore() != null
			? new ArrayList<>(prevMeta.getLore())
			: null;
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!player.isOnline()) {
					return;
				}
				// Later book handlers may format the content. Only replace the saved result
				// of this event, never another item moved into its slot before the next tick.
				BookMeta content = event.getNewBookMeta();
				ItemStack saved = player.getInventory().getItem(slot);
				if (saved == null || saved.getType() != Material.WRITTEN_BOOK
					|| !(saved.getItemMeta() instanceof BookMeta savedMeta)
					|| content == null
					|| !savedMeta.equals(content)) {
					return;
				}
				ItemStack restored = target.getItemStack();
				if (restored == null || restored.getType() != Material.WRITTEN_BOOK) {
					return;
				}
				restored = restored.clone();
				restored.setAmount(saved.getAmount());

				ItemMeta meta = restored.getItemMeta();
				if (!(meta instanceof BookMeta bookMeta)) {
					return;
				}

				bookMeta.pages(content.pages());
				if (content.hasTitle()) {
					bookMeta.setTitle(content.getTitle());
				}
				if (content.hasAuthor()) {
					bookMeta.setAuthor(content.getAuthor());
				}
				// Paper reports Original as "no generation", which would keep the
				// ItemsAdder template's default of Tattered.
				bookMeta.setGeneration(content.getGeneration());
				if (displayName != null) {
					bookMeta.setDisplayName(displayName);
				}
				if (lore != null) {
					bookMeta.setLore(lore);
				}
				if (prevMetaClone != null) {
					copyPdc(prevMetaClone, bookMeta);
				}

				restored.setItemMeta(bookMeta);
				player.getInventory().setItem(slot, restored);
				ArmourShop.plugin.getLogger().info(
					"[book-sign] " + player.getName() + " "
						+ namespace + ":" + id + " -> " + targetId
				);
			}
		}.runTaskLater(ArmourShop.plugin, 1L);
	}

	private static void copyPdc(ItemMeta from, ItemMeta to) {
		try {
			from.getPersistentDataContainer().copyTo(to.getPersistentDataContainer(), true);
		} catch (NoSuchMethodError | UnsupportedOperationException ignored) {
			// Older API without copyTo — display/lore already copied above.
		}
	}
}

package net.tfminecraft.ArmourShop.managers;

import java.util.ArrayList;
import java.util.List;

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
import net.tfminecraft.ArmourShop.ArmourShop;

/**
 * Preserve custom item metadata when saving unsigned pages.
 * When a player signs an ItemsAdder book skin ({@code slug}), swap the stack to
 * {@code slug_signed} while keeping pages, title, author, display name, lore, and PDC.
 */
public final class BookSignSkinListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSignBook(PlayerEditBookEvent event) {
		BookEditSkinPreserver.preserve(event);
		if (!event.isSigning()) {
			return;
		}

		Player player = event.getPlayer();
		int slot = event.getSlot();
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

		boolean signing = event.isSigning();
		String targetId = signing ? id + "_signed" : id;
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
		BookMeta content = event.getNewBookMeta();
		int amount = Math.max(1, current.getAmount());

		new BukkitRunnable() {
			@Override
			public void run() {
				if (!player.isOnline()) {
					return;
				}
				ItemStack restored = target.getItemStack();
				if (restored == null || restored.getType().isAir()) {
					return;
				}
				restored = restored.clone();
				restored.setAmount(amount);

				ItemMeta meta = restored.getItemMeta();
				if (!(meta instanceof BookMeta bookMeta)) {
					player.getInventory().setItem(slot, restored);
					return;
				}

				if (content != null) {
					bookMeta.setPages(content.getPages());
					if (signing) {
						if (content.hasTitle()) {
							bookMeta.setTitle(content.getTitle());
						}
						if (content.hasAuthor()) {
							bookMeta.setAuthor(content.getAuthor());
						}
						if (content.hasGeneration()) {
							bookMeta.setGeneration(content.getGeneration());
						}
					}
				}
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
				String action = signing ? "book-sign" : "book-edit";
				ArmourShop.plugin.getLogger().info(
					"[" + action + "] " + player.getName() + " "
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

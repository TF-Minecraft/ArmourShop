package net.tfminecraft.armourshop.managers;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ArmorMerger;
import net.tfminecraft.armourshop.ArmourShop;
import net.tfminecraft.armourshop.enums.ArmorType;
import net.tfminecraft.armourshop.holder.ASInventoryHolder;
import net.tfminecraft.armourshop.loaders.CategoryLoader;
import net.tfminecraft.armourshop.objects.SkinCategory;
import net.tfminecraft.armourshop.objects.SkinSet;

public class SkinManager implements Listener{
	InventoryManager inv = new InventoryManager();
	private boolean isCategoryInventory(String name) {
		for(SkinCategory c : CategoryLoader.get()) {
			if(c.getName().equalsIgnoreCase(name)) return true;
		}
		return false;
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@EventHandler
	public void invenClick(InventoryClickEvent e) {
		if(e.getClickedInventory() == null) return;
		if(e.getCurrentItem() == null) return;
		Player p = (Player) e.getWhoClicked();
		if(!(e.getView().getTopInventory().getHolder() instanceof ASInventoryHolder)) return;
		ASInventoryHolder holder = (ASInventoryHolder) e.getView().getTopInventory().getHolder();
		if(e.getView().getTitle().equalsIgnoreCase("\u00A77Armourshop Categories")) {
			e.setCancelled(true);
			if(e.getSlot() == 53) {
				inv.typeView(p);
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
				return;
			}
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			SkinCategory c = CategoryLoader.getByName(item.getItemMeta().getDisplayName());
			if(c == null) return;
			
			inv.skinView(p, c, 0, holder.isItem());
			p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		} else if(e.getView().getTitle().equalsIgnoreCase("\u00A77Armourshop Type")) {
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			boolean isItem = false;
			if(e.getSlot() == 1) isItem = true;
			inv.categoryView(p, isItem);
			p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
		} else if(isCategoryInventory(e.getView().getTitle())) {
			SkinCategory c = CategoryLoader.getByName(e.getView().getTitle());
			e.setCancelled(true);
			ItemStack item = e.getCurrentItem();
			if(item == null) return;
			ItemMeta m = item.getItemMeta();
			if(item.getType().equals(Material.GRAY_STAINED_GLASS_PANE)) return;
			if(e.getSlot() == 3) {			
				NamespacedKey key = new NamespacedKey(ArmourShop.plugin, "page");
				int page = m.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
				inv.skinView(p, c, page-1, holder.isItem());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 5) {
				NamespacedKey key = new NamespacedKey(ArmourShop.plugin, "page");
				int page = m.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
				inv.skinView(p, c, page+1, holder.isItem());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else if(e.getSlot() == 4) {
				inv.categoryView(p, holder.isItem());
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
			} else {
				applySkin(p, item);
			}
		}
	}
	
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void applySkin(Player p, ItemStack i) {
		ItemMeta m = i.getItemMeta();
		NamespacedKey key = new NamespacedKey(ArmourShop.plugin, "set");
		if(m.getPersistentDataContainer().get(key, PersistentDataType.STRING) == null) {
			return;
		}
		String info = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
		SkinSet set = CategoryLoader.getByContainsSet(info.split("\\.")[0]);
		if(set == null) return;
		ItemStack scroll = null;
		if(set.hasScroll()) {
			scroll = findScroll(p, set.getScroll());
			if(scroll == null) {
				p.sendMessage("\u00A7cLacking Scroll");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
		}
		if(ArmorType.valueOf(info.split("\\.")[1].toUpperCase()) == null) return;
		ArmorType type = ArmorType.valueOf(info.split("\\.")[1].toUpperCase());
		for(int y = 0; y<p.getInventory().getContents().length;y++) {
			ItemStack item = p.getInventory().getContents()[y];
			if(set.getSet().contains(item, type)) {
				String s = null;
				if(type.equals(ArmorType.HELMET)) {
					s = set.getHelmet();
				} else if(type.equals(ArmorType.CHESTPLATE)) {
					s = set.getChestplate();
				} else if(type.equals(ArmorType.LEGGINGS)) {
					s = set.getLeggings();
				} else if(type.equals(ArmorType.BOOTS)) {
					s = set.getBoots();
				} else if(type.equals(ArmorType.ITEM)){
					s = set.getItem();
				}
				Optional<String> name = Optional.empty();
				if(set.addName()) {
					name = Optional.of(i.getItemMeta().getDisplayName());
				}
				ArmorMerger merger = TLibs.getItemAPI().getArmorMerger();
				p.getInventory().setItem(y, merger.merge(item, name, s));
				if(set.hasScroll()) scroll.setAmount(scroll.getAmount()-1);
				p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				return;
			}
		}
		p.sendMessage("\u00A7cNo item to apply skin on in your inventory");
		p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
	}
	
	public ItemStack findScroll(Player p, String scroll) {
		ItemAPI api = TLibs.getItemAPI();
		for(ItemStack i : p.getInventory().getContents()) {
			if(api.getChecker().checkItemWithPath(i, scroll)) {
				return i;
			}
		}
		return null;
	}
	@EventHandler
	public void fixItem(InventoryClickEvent e) {
		ItemStack i = e.getCurrentItem();
		if(i == null) return;
		if(!NBTItem.get(i).hasType()) return;
		if(CustomStack.byItemStack(i) == null) return;
		NBTItem nbt = NBTItem.get(i);
		if(nbt.hasTag("ia")) return;
		CustomStack stack = CustomStack.byItemStack(i);
		nbt.addTag(new ItemTag("ia", stack.getNamespace()+"."+stack.getId()));
		e.setCurrentItem(nbt.toItem());
	}
}

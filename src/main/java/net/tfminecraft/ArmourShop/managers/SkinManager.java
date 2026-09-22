package net.tfminecraft.ArmourShop.managers;

import java.util.HashMap;
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
import org.bukkit.scheduler.BukkitRunnable;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.ArmorMerger;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.event.item.ApplyGemStoneEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import net.tfminecraft.ArmourShop.ArmourShop;
import net.tfminecraft.ArmourShop.enums.ArmorType;
import net.tfminecraft.ArmourShop.holder.ASInventoryHolder;
import net.tfminecraft.ArmourShop.loaders.CategoryLoader;
import net.tfminecraft.ArmourShop.objects.SkinCategory;
import net.tfminecraft.ArmourShop.objects.SkinSet;

public class SkinManager implements Listener{
	InventoryManager inv = new InventoryManager();
	private HashMap<Player, Boolean> usedGem = new HashMap<>();
	private boolean isCategoryInventory(String name) {
		for(SkinCategory c : CategoryLoader.get()) {
			if(c.getName().equalsIgnoreCase(name)) return true;
		}
		return false;
	}
	/*
	@EventHandler
	public void applyGem(ApplyGemStoneEvent e) {
		Player p = e.getPlayer();
		MMOItem mmoitem = e.getTargetItem();
		DoubleData oldModel = null;
		if(mmoitem.hasData(ItemStats.CUSTOM_MODEL_DATA)) {
			oldModel = (DoubleData) mmoitem.getData(ItemStats.CUSTOM_MODEL_DATA);
		}
		ItemStack i = mmoitem.newBuilder().build();
		NBTItem mnbt = NBTItem.get(i);
		p.sendMessage(mnbt.getTags().toString());
		final DoubleData fixed = oldModel;
		if(mnbt.hasTag("amodel")) {
			p.sendMessage("found");
			String tag = mnbt.getString("amodel");
			p.sendMessage("model is "+tag);
			int model = Integer.parseInt(tag);
			mmoitem.setData(ItemStats.CUSTOM_MODEL_DATA, new DoubleData(model));
			if(fixed != null) {
				new BukkitRunnable() {
					@Override
					public void run() {
						mmoitem.setData(ItemStats.CUSTOM_MODEL_DATA, fixed);
					}
				}.runTaskLater(ArmourShop.plugin, 5L);
			}
		}
	}
	*/
	
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
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
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
	/*
	@EventHandler
	public void gemEvent(InventoryClickEvent e) {
		if(!(e.getWhoClicked() instanceof Player)) return;
		Player p = (Player) e.getWhoClicked();
		if(!usedGem.containsKey(p)) return;
		ItemStack i = e.getCurrentItem();
		if(i == null) return;
		if(!NBTItem.get(i).hasType()) return;
		NBTItem mnbt = NBTItem.get(i);
		if(mnbt.getString("ia") == null) return;
		String info = mnbt.getString("ia");
		NBT.modify(i, nbt ->{
			nbt.getOrCreateCompound("itemsadder");
			nbt.getCompound("itemsadder").setString("namespace", info.split("\\.")[0]);
			nbt.getCompound("itemsadder").setString("id", info.split("\\.")[1]);
		});
	}
	@EventHandler
	public void gemstoneEvent(ApplyGemStoneEvent e) {
		usedGem.put(e.getPlayer(), true);
		new BukkitRunnable()
		{
			public void run()
			{
				usedGem.remove(e.getPlayer());
			}
		}.runTaskLater(ArmourShop.plugin, 5L);
	}
	*/
}

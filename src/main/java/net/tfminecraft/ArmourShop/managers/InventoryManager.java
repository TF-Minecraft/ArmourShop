package net.tfminecraft.ArmourShop.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Enums.APIType;
import me.Plugins.TLibs.Objects.API.ItemAPI;
import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.plugins.tlibs.shaded.lang3.text.WordUtils;
import net.tfminecraft.ArmourShop.ArmourShop;
import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.enums.ArmorType;
import net.tfminecraft.ArmourShop.holder.ASInventoryHolder;
import net.tfminecraft.ArmourShop.loaders.CategoryLoader;
import net.tfminecraft.ArmourShop.objects.SkinCategory;
import net.tfminecraft.ArmourShop.objects.SkinSet;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinData;
import net.tfminecraft.gunsandgadgets.guns.skins.SkinState;
import net.tfminecraft.gunsandgadgets.loader.SkinLoader;

public class InventoryManager {

	public void typeView(Player player) {
		Inventory i = ArmourShop.plugin.getServer().createInventory(new ASInventoryHolder(false), 9, "\u00A77Armourshop Type");
		i.setItem(0, createArmourItem());
		i.setItem(1, createItemItem());
		player.openInventory(i);
	}
	public void categoryView(Player player, boolean item) {
		Inventory i = ArmourShop.plugin.getServer().createInventory(new ASInventoryHolder(item), 54, "\u00A77Armourshop Categories");
		int c = 0;
		for(int y = 0; y<CategoryLoader.get().size();y++) {
			if(c > 53) break;
			SkinCategory cat = CategoryLoader.get().get(y);
			if(!(cat.isItem() == item)) continue;
			if(cat.hasPermission()) {
				while(cat.hasPermission() && !player.hasPermission(cat.getPermission())) {
					y++;
					if(y >= CategoryLoader.get().size()) break;
					SkinCategory next = CategoryLoader.get().get(y);
					if(!(next.isItem() == item)) continue;
					cat = next;
				}
			}
			if(!cat.hasPermission()) {
				i.setItem(c, createCategoryItem(cat));
			} else if(player.hasPermission(cat.getPermission())) {
				i.setItem(c, createCategoryItem(cat));
			}
			c++;
		}
		i.setItem(53, createBackButton());
		player.openInventory(i);
	}
	public void skinView(Player player, SkinCategory cat, int page, boolean item) {
		Inventory inv = ArmourShop.plugin.getServer().createInventory(new ASInventoryHolder(item), 54, cat.getName());
		int slot = 0;
		while (slot < 9) {
			ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
			ItemMeta fm = fill.getItemMeta();
			fm.setDisplayName("\u00A78 ");
			fill.setItemMeta(fm);
			inv.setItem(slot, fill);
			slot++;
		}

		List<Integer> points = item ? Cache.itemPoints : Cache.points;
		// Pack only sets this player can see (no holes from skipped permission / bad base set).
		List<SkinSet> visible = visibleSets(player, cat);
		int pageSize = points.size();
		if (page < 0) {
			page = 0;
		}
		int start = page * pageSize;
		int pointIdx = 0;
		for (int i = start; i < visible.size() && pointIdx < pageSize; i++) {
			SkinSet set = visible.get(i);
			int x = points.get(pointIdx++);
			if (set.hasItem()) {
				ItemStack stack = createSkinItem(set, set.getItem(), ArmorType.ITEM);
				if (stack != null) {
					inv.setItem(x, stack);
				}
				continue;
			}
			if (set.hasHelmet()) {
				ItemStack stack = createSkinItem(set, set.getHelmet(), ArmorType.HELMET);
				if (stack != null) {
					inv.setItem(x, stack);
				}
			}
			if (set.hasChestplate()) {
				ItemStack stack = createSkinItem(set, set.getChestplate(), ArmorType.CHESTPLATE);
				if (stack != null) {
					inv.setItem(x + 1, stack);
				}
			}
			if (set.hasLeggings()) {
				ItemStack stack = createSkinItem(set, set.getLeggings(), ArmorType.LEGGINGS);
				if (stack != null) {
					inv.setItem(x + 2, stack);
				}
			}
			if (set.hasBoots()) {
				ItemStack stack = createSkinItem(set, set.getBoots(), ArmorType.BOOTS);
				if (stack != null) {
					inv.setItem(x + 3, stack);
				}
			}
		}

		inv.setItem(4, createBackButton());
		if (page > 0) {
			inv.setItem(3, getPageItem("mcicons:icon_back_orange", page));
		}
		if (visible.size() - start > pageSize) {
			inv.setItem(5, getPageItem("mcicons:icon_next_orange", page));
		}
		player.openInventory(inv);
	}

	/**
	 * Sets the player may see in this category: permission OK and base set resolved.
	 * Unresolved sets are logged once per view and omitted so the grid has no holes.
	 */
	private List<SkinSet> visibleSets(Player player, SkinCategory cat) {
		List<SkinSet> visible = new ArrayList<>();
		if (cat == null || cat.getSets() == null) {
			return visible;
		}
		for (SkinSet set : cat.getSets()) {
			if (set == null) {
				continue;
			}
			if (set.hasPermission() && !player.hasPermission(set.getPermission())) {
				continue;
			}
			if (set.getSet() == null) {
				ArmourShop.plugin.getLogger().warning(
						"[shop] skipping skin '" + set.getId()
								+ "' in category '" + cat.getName()
								+ "': base set unresolved (missing/invalid 'set' in YAML "
								+ "or not present in live base-sets.yml)"
				);
				continue;
			}
			visible.add(set);
		}
		return visible;
	}

	public ItemStack createArmourItem(){
		ItemStack i = new ItemStack(Material.IRON_CHESTPLATE, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#52de81\u00A7lArmour"));
		m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		List<String> lore = new ArrayList<>();
		lore.add("\u00A77Armour skins");
		lore.add("");
		lore.add(StringFormatter.formatHex("#21de21Click to View"));
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}

	public ItemStack createItemItem(){
		ItemStack i = new ItemStack(Material.IRON_SWORD, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName(StringFormatter.formatHex("#52de81\u00A7lItems"));
		m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		List<String> lore = new ArrayList<>();
		lore.add("\u00A77Item skins");
		lore.add("");
		lore.add(StringFormatter.formatHex("#21de21Click to View"));
		m.setLore(lore);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createCategoryItem(SkinCategory c) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack i = api.getCreator().getItemFromPath(c.getItem());
		if(i == null) i = new ItemStack(Material.DIRT, 1);
		ItemMeta meta = i.getItemMeta();
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		meta.setDisplayName(c.getName());
		List<String> lore = new ArrayList<String>();
		if(c.isItem()) lore.add("\u00A7a"+c.getSets().size()+" \u00A7eItems");
		else lore.add("\u00A7a"+c.getSets().size()+" \u00A7eArmor Sets");
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	
	@SuppressWarnings("deprecation")
	public ItemStack createSkinItem(SkinSet set, String id, ArmorType type) {
		if (set == null || id == null || id.isBlank()) {
			return null;
		}
		if (set.getSet() == null) {
			ArmourShop.plugin.getLogger().warning(
					"[shop] createSkinItem skipped '" + set.getId()
							+ "': base set is null"
			);
			return null;
		}
		ItemStack i = null;
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		if(id.split("\\(")[0].equalsIgnoreCase("localmodel")){
			String info = id.split("\\(")[1].replace(")", "");
			try {
				i = new ItemStack(Material.valueOf(info.split("\\.")[0].toUpperCase()), 1);
				ItemMeta m = i.getItemMeta();
				m.setCustomModelData(Integer.parseInt(info.split("\\.")[1]));
				i.setItemMeta(m);
			} catch (Exception e) {
				e.printStackTrace();
				i = new ItemStack(Material.DIRT, 1);
			}
		} else if(id.split("\\(")[0].equalsIgnoreCase("gunskin")) {
			String value = id.split("\\(")[1].replace(")", "");
			SkinData gunskin = SkinLoader.getByString(value);
			if(gunskin == null) {
				ArmourShop.plugin.getLogger().warning("No gunskin called " + value);
				i = new ItemStack(Material.DIRT, 1);
			} else {
				i = gunskin.parseModel(SkinState.CARRY);
			}
		} else{
			i = api.getCreator().getItemFromPath(id);
		}
		if (i == null || i.getType().isAir()) {
			ArmourShop.plugin.getLogger().warning(
					"[shop] createSkinItem skipped '" + set.getId()
							+ "': could not resolve path '" + id + "'"
			);
			return null;
		}

		ItemMeta meta = i.getItemMeta();
		if (meta == null) {
			ArmourShop.plugin.getLogger().warning(
					"[shop] createSkinItem skipped '" + set.getId() + "': null ItemMeta"
			);
			return null;
		}
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		meta.setDisplayName(set.getFormattedPieceName(type));
		List<String> lore = new ArrayList<String>();
		lore.add("\u00A7eTier: \u00A7f"+WordUtils.capitalize(set.getSet().getId()));
		if(set.hasScroll()) {
			lore.add(" ");
			ItemStack scrollStack = api.getCreator().getItemFromPath(set.getScroll());
			String scrollName = set.getScroll();
			if (scrollStack != null && scrollStack.getItemMeta() != null
					&& scrollStack.getItemMeta().hasDisplayName()) {
				scrollName = scrollStack.getItemMeta().getDisplayName();
			}
			lore.add("\u00A77Scroll: " + scrollName);
		}
		NamespacedKey key = new NamespacedKey(ArmourShop.plugin, "set");
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, set.getId()+"."+type.toString().toLowerCase());
		meta.setLore(lore);
		i.setItemMeta(meta);
		return i;
	}
	
	public ItemStack getPageItem(String s, int page) {
		ItemAPI api = (ItemAPI) TLibs.getApiInstance(APIType.ITEM_API);
		ItemStack i = api.getCreator().getItemsAdderItem(s);
		ItemMeta m = i.getItemMeta();
		NamespacedKey key = new NamespacedKey(ArmourShop.plugin, "page");
		m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, page);
		i.setItemMeta(m);
		return i;
	}
	
	public ItemStack createBackButton() {
		ItemStack i = new ItemStack(Material.BARRIER, 1);
		ItemMeta m = i.getItemMeta();
		m.setDisplayName("\u00A7cBACK");
		i.setItemMeta(m);
		return i;
	}
}

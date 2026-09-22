package net.tfminecraft.armourshop.objects;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.tfminecraft.armourshop.enums.ArmorType;

public class ArmorPiece {
	private NBTItem item;
	private ArmorType type;
	
	public ArmorPiece(String key, ItemStack item, ArmorType type){
		NBTItem nbt = NBTItem.get(item);
		if(!nbt.hasType()) Bukkit.getLogger().info("Item is not an MMOItem for id "+key+" and type "+type.toString());
		this.item = nbt;
		this.type = type;
	}

	public NBTItem getItem() {
		return item;
	}

	public ArmorType getType() {
		return type;
	}
	
	public boolean is(ItemStack target) {
		NBTItem nbt = NBTItem.get(target);
		if(!nbt.hasType()) return false;
		if(nbt.getType().equalsIgnoreCase(this.item.getType()) && nbt.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(this.item.getString("MMOITEMS_ITEM_ID"))) return true;
		return false;
	}
}

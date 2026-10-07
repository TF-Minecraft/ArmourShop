package net.tfminecraft.armourshop.objects;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.armourshop.enums.ArmorType;

/**
 * One base-set entry. {@code type.id} matches that MMOItems item; a bare {@code type}
 * matches every item of that MMOItems type, so templates other plugins craft from
 * (AdvancedCrafting, Magic) count without being listed one by one.
 */
public class ArmorPiece {
	private String path;
	private ArmorType type;

	public ArmorPiece(String key, String entry, ArmorType type){
		this.path = "m." + entry.trim();
		this.type = type;
		if(!isTypeOnly() && TLibs.getItemAPI().getCreator().getItemFromPath(path) == null) {
			Bukkit.getLogger().warning("[ArmourShop] Base set " + key + " lists " + entry + " (" + type + "), which is not an MMOItem");
		}
	}

	public String getPath() {
		return path;
	}

	public ArmorType getType() {
		return type;
	}

	public boolean isTypeOnly() {
		return path.split("\\.").length < 3;
	}

	public boolean is(ItemStack target) {
		return TLibs.getItemAPI().getChecker().checkItemWithPath(target, path);
	}
}

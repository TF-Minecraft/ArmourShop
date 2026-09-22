package net.tfminecraft.armourshop.objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.tlibs.objects.api.subapi.ItemCreator;
import net.tfminecraft.armourshop.enums.ArmorType;

public class BaseSet {
	private String id;
	private List<ArmorPiece> helmets = new ArrayList<>();
	private List<ArmorPiece> chestplates = new ArrayList<>();
	private List<ArmorPiece> leggings = new ArrayList<>();
	private List<ArmorPiece> boots = new ArrayList<>();
	private List<ArmorPiece> items = new ArrayList<>();
	
	public BaseSet(String key, ConfigurationSection config) {
		this.id = key;
		ItemAPI api = TLibs.getItemAPI();
		ItemCreator c = api.getCreator();
		if(config.contains("helmet")) {
			for(String s : config.getStringList("helmet")) {
				this.helmets.add(new ArmorPiece(key, c.getItemFromPath("m."+s), ArmorType.HELMET));
			}
		}
		if(config.contains("chestplate")) {
			for(String s : config.getStringList("chestplate")) {
				this.chestplates.add(new ArmorPiece(key, c.getItemFromPath("m."+s), ArmorType.CHESTPLATE));
			}
		}
		if(config.contains("leggings")) {
			for(String s : config.getStringList("leggings")) {
				this.leggings.add(new ArmorPiece(key, c.getItemFromPath("m."+s), ArmorType.LEGGINGS));
			}
		}
		if(config.contains("boots")) {
			for(String s : config.getStringList("boots")) {
				this.boots.add(new ArmorPiece(key, c.getItemFromPath("m."+s), ArmorType.BOOTS));
			}
		}
		if(config.contains("item")) {
			for(String s : config.getStringList("item")) {
				this.items.add(new ArmorPiece(key, c.getItemFromPath("m."+s), ArmorType.ITEM));
			}
		}
	}

	public String getId() {
		return id;
	}

	public List<ArmorPiece> getHelmets() {
		return helmets;
	}

	public List<ArmorPiece> getChestplates() {
		return chestplates;
	}

	public List<ArmorPiece> getLeggings() {
		return leggings;
	}

	public List<ArmorPiece> getBoots() {
		return boots;
	}
	
	public boolean contains(ItemStack i, ArmorType type) {
		if(type.equals(ArmorType.HELMET)) {
			for(ArmorPiece p : helmets) {
				if(p.is(i)) return true;
			}
		} else if(type.equals(ArmorType.CHESTPLATE)) {
			for(ArmorPiece p : chestplates) {
				if(p.is(i)) return true;
			}
		} else if(type.equals(ArmorType.LEGGINGS)) {
			for(ArmorPiece p : leggings) {
				if(p.is(i)) return true;
			}
		} else if(type.equals(ArmorType.BOOTS)) {
			for(ArmorPiece p : boots) {
				if(p.is(i)) return true;
			}
		} else if(type.equals(ArmorType.ITEM)) {
			for(ArmorPiece p : items) {
				if(p.is(i)) return true;
			}
		}
		return false;
	}
}

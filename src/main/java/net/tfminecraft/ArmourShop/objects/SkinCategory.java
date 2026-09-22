package net.tfminecraft.ArmourShop.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.ArmourShop.utils.NameDisplay;

public class SkinCategory {
	private String id;
	private String name;
	private String item;
	private boolean isItem;
	private List<SkinSet> sets = new ArrayList<>();
	private Optional<String> permission = Optional.empty();
	
	public SkinCategory(String key, ConfigurationSection config) {
		this.id = key;
		this.name = NameDisplay.formatFromConfig(config, "name");
		this.item = config.getString("item");
		if(config.contains("permission")) {
			this.permission = Optional.of(config.getString("permission"));
		}
		isItem = config.getBoolean("is-item", false);
	}

	public String getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}

	public String getItem() {
		return item;
	}
	public boolean isItem() {
		return isItem;
	}

	public String getPermission() {
		return permission.get();
	}
	
	public boolean hasPermission() {
		return permission.isPresent();
	}
	
	public List<SkinSet> getSets(){
		return sets;
	}
	
	public void addSet(SkinSet s) {
		this.sets.add(s);
	}
	
	public SkinCategory(SkinCategory another) {
		this.id = another.getId();
		this.name = another.getName();
		this.item = another.getItem();
		this.permission = Optional.of(another.getPermission());
		this.isItem = another.isItem();
	}
}

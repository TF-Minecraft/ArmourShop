package net.tfminecraft.ArmourShop.objects;

import java.util.List;
import java.util.Optional;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import me.plugins.tlibs.shaded.lang3.text.WordUtils;
import net.tfminecraft.ArmourShop.enums.ArmorType;
import net.tfminecraft.ArmourShop.loaders.BaseSetLoader;
import net.tfminecraft.ArmourShop.utils.NameDisplay;

public class SkinSet {
	private String id;
	private String plainName;
	private List<String> colours;
	private List<String> styles;
	private String name;
	private BaseSet set;
	private Optional<String> scroll = Optional.empty();
	private Optional<String> helmet = Optional.empty();
	private Optional<String> chestplate = Optional.empty();
	private Optional<String> leggings = Optional.empty();
	private Optional<String> boots = Optional.empty();
	private Optional<String> item = Optional.empty();
	private boolean addName;
	private String permission;
	
	public SkinSet(String key, ConfigurationSection config) {
		this.id = key;
		NameDisplay.ParsedName parsed = NameDisplay.parseFromConfig(config, "name");
		this.plainName = parsed.plain;
		this.colours = parsed.colours;
		this.styles = parsed.styles;
		this.name = StringFormatter.formatDisplayName(plainName, colours, styles);
		this.set = BaseSetLoader.getByString(config.getString("set"));
		if(config.contains("scroll")) {
			scroll = Optional.of(config.getString("scroll"));
		}
		if(config.contains("helmet")) {
			helmet = Optional.of(config.getString("helmet"));
		}
		if(config.contains("chestplate")) {
			chestplate = Optional.of(config.getString("chestplate"));
		}
		if(config.contains("leggings")) {
			leggings = Optional.of(config.getString("leggings"));
		}
		if(config.contains("boots")) {
			boots = Optional.of(config.getString("boots"));
		}
		if(config.contains("item")){
			item = Optional.of(config.getString("item"));
		}
		if(config.contains("add-name")) {
			addName = config.getBoolean("add-name");
		} else {
			addName = false;
		}
		permission = config.getString("permission", "none");
	}

	public boolean hasPermission() {
		return !permission.equalsIgnoreCase("none");
	}

	public String getPermission() {
		return permission;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	/**
	 * Display name with colour/gradient applied across the full piece string
	 * (e.g. "Blue Knight Chestplate"), not only the set prefix.
	 */
	public String getFormattedPieceName(ArmorType type) {
		String plain = plainName;
		if (type != null && !type.equals(ArmorType.ITEM)) {
			plain = plain + " " + WordUtils.capitalize(type.toString().toLowerCase());
		}
		return StringFormatter.formatDisplayName(plain, colours, styles);
	}

	public BaseSet getSet() {
		return set;
	}

	public String getScroll() {
		return scroll.get();
	}

	public String getHelmet() {
		return helmet.get();
	}

	public String getChestplate() {
		return chestplate.get();
	}

	public String getLeggings() {
		return leggings.get();
	}

	public String getBoots() {
		return boots.get();
	}

	public String getItem() {
		return item.get();
	}
	
	public boolean hasScroll() {
		return scroll.isPresent();
	}
	public boolean hasHelmet() {
		return helmet.isPresent();
	}
	public boolean hasChestplate() {
		return chestplate.isPresent();
	}
	public boolean hasLeggings() {
		return leggings.isPresent();
	}
	public boolean hasBoots() {
		return boots.isPresent();
	}
	public boolean hasItem() {
		return item.isPresent();
	}

	public boolean addName() {
		return addName;
	}
}

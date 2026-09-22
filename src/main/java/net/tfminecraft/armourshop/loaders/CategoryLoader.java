package net.tfminecraft.armourshop.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.armourshop.objects.SkinCategory;
import net.tfminecraft.armourshop.objects.SkinSet;

public class CategoryLoader implements LoaderInterface{
	static List<SkinCategory> oList = new ArrayList<SkinCategory>();
	public static void clear() {
		oList.clear();
	}
	public static List<SkinCategory> get() {
		return oList;
	}
	public static SkinCategory getByString(String id) {
		for(SkinCategory o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return o;
		}
		return null;
	}
	public static SkinCategory getByName(String id) {
		for(SkinCategory o : oList) {
			if(o.getName().equalsIgnoreCase(id)) return o;
		}
		return null;
	}
	public static SkinSet getByContainsSet(String id) {
		for(SkinCategory o : oList) {
			for(SkinSet s : o.getSets()) {
				if(s.getId().equalsIgnoreCase(id)) return s;
			}
		}
		return null;
	}
	public void load(File configFile) {
		clear();
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			SkinCategory o = new SkinCategory(key, config.getConfigurationSection(key));
			oList.add(o);
		}
	}
}

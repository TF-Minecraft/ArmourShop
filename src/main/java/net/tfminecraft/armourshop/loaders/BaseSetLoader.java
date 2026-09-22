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
import net.tfminecraft.armourshop.objects.BaseSet;

public class BaseSetLoader implements LoaderInterface{
	static List<BaseSet> oList = new ArrayList<BaseSet>();
	public static void clear() {
		oList.clear();
	}
	public static List<BaseSet> get() {
		return oList;
	}
	public static BaseSet getByString(String id) {
		for(BaseSet o : oList) {
			if(o.getId().equalsIgnoreCase(id)) return o;
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
			BaseSet o = new BaseSet(key, config.getConfigurationSection(key));
			oList.add(o);
		}
	}
}

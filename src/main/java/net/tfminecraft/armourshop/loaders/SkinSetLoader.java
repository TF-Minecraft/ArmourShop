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

public class SkinSetLoader implements LoaderInterface{

	public void load(File configFile) {
		String category = new String(configFile.getName());
		category = category.replace(".yml", "");
		SkinCategory c =  CategoryLoader.getByString(category);
		if(c == null) return;
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Set<String> set = config.getKeys(false);

		List<String> list = new ArrayList<String>(set);
		
		for(String key : list) {
			SkinSet o = new SkinSet(key, config.getConfigurationSection(key));
			c.addSet(o);
		}
	}

}

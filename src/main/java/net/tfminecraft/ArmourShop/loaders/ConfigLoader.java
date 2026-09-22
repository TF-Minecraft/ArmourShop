package net.tfminecraft.ArmourShop.loaders;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.ArmourShop.Cache;
import net.tfminecraft.ArmourShop.objects.ScrollOption;

public class ConfigLoader implements LoaderInterface{
	public void load(File configFile) {
		FileConfiguration config = new YamlConfiguration();
        try {
        	config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        Cache.points = config.getIntegerList("start-points");
        Cache.itemPoints = config.getIntegerList("item-start-points");

        String iaPath = config.getString("pack-apply.ia-contents-path", "");
        Cache.iaContentsPath = iaPath == null ? "" : iaPath.trim();

        String catPath = config.getString("pack-apply.categories-path", "");
        Cache.categoriesPath = catPath == null ? "" : catPath.trim();

        String gunsSkins = config.getString("pack-apply.guns-skins-yml", "");
        Cache.gunsSkinsYmlPath = gunsSkins == null ? "" : gunsSkins.trim();

        String forceTime = config.getString("pack-apply.force-reload-time", "06:00");
        Cache.forceReloadTime = forceTime == null ? "" : forceTime.trim();

        Cache.iaReloadDelaySeconds = Math.max(0, config.getInt("pack-apply.ia-reload-delay-seconds", 5));

        List<ScrollOption> scrolls = new ArrayList<>();
        List<Map<?, ?>> rawScrolls = config.getMapList("scrolls");
        if (rawScrolls != null) {
            for (Map<?, ?> row : rawScrolls) {
                if (row == null) {
                    continue;
                }
                Object idObj = row.get("id");
                if (idObj == null) {
                    continue;
                }
                String id = String.valueOf(idObj).trim();
                if (id.isEmpty()) {
                    continue;
                }
                Object labelObj = row.get("label");
                String label = labelObj == null ? id : String.valueOf(labelObj).trim();
                scrolls.add(new ScrollOption(id, label));
            }
        }
        Cache.scrolls = scrolls;
	}
}

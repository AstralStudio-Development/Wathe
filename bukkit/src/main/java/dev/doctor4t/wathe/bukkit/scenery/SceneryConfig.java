package dev.doctor4t.wathe.bukkit.scenery;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 山景配置
 */
public class SceneryConfig {

    private final WatheBukkit plugin;
    
    private boolean enabled;
    private double speed;
    private char moveAxis;
    private int segmentCount;
    
    private String templateWorld;
    private Location templatePos1;
    private Location templatePos2;
    private Location displayPosition;

    public SceneryConfig(WatheBukkit plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("scenery");
        if (section == null) {
            enabled = false;
            return;
        }
        
        enabled = section.getBoolean("enabled", false);
        speed = section.getDouble("speed", 0.5);
        String axisStr = section.getString("move-axis", "X");
        moveAxis = axisStr.isEmpty() ? 'X' : axisStr.charAt(0);
        segmentCount = section.getInt("segment-count", 2);
        
        // 加载模板区域
        ConfigurationSection templateSection = section.getConfigurationSection("template-region");
        if (templateSection != null) {
            templateWorld = templateSection.getString("world", "world");
            World world = Bukkit.getWorld(templateWorld);
            
            ConfigurationSection pos1Section = templateSection.getConfigurationSection("pos1");
            ConfigurationSection pos2Section = templateSection.getConfigurationSection("pos2");
            
            if (world != null && pos1Section != null && pos2Section != null) {
                templatePos1 = new Location(world,
                    pos1Section.getDouble("x"),
                    pos1Section.getDouble("y"),
                    pos1Section.getDouble("z")
                );
                templatePos2 = new Location(world,
                    pos2Section.getDouble("x"),
                    pos2Section.getDouble("y"),
                    pos2Section.getDouble("z")
                );
            }
        }
        
        // 加载显示位置
        ConfigurationSection displaySection = section.getConfigurationSection("display-position");
        if (displaySection != null) {
            String displayWorldName = displaySection.getString("world", "world");
            World displayWorld = Bukkit.getWorld(displayWorldName);
            
            if (displayWorld != null) {
                displayPosition = new Location(displayWorld,
                    displaySection.getDouble("x"),
                    displaySection.getDouble("y"),
                    displaySection.getDouble("z")
                );
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getSpeed() {
        return speed;
    }

    public char getMoveAxis() {
        return moveAxis;
    }

    public int getSegmentCount() {
        return segmentCount;
    }

    public String getTemplateWorld() {
        return templateWorld;
    }

    public Location getTemplatePos1() {
        return templatePos1;
    }

    public Location getTemplatePos2() {
        return templatePos2;
    }

    public Location getDisplayPosition() {
        return displayPosition;
    }

    public boolean isValid() {
        return enabled && templatePos1 != null && templatePos2 != null && displayPosition != null;
    }
}

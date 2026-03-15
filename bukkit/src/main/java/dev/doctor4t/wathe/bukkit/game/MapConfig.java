package dev.doctor4t.wathe.bukkit.game;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;

public class MapConfig {

    private final WatheBukkit plugin;
    private File mapFile;
    private FileConfiguration mapConfig;

    // 地图变量
    private Location spawnLocation;
    private Location spectatorSpawnLocation;
    private BoundingBox readyArea;
    private BoundingBox playArea;
    private BoundingBox insideArea;
    private Vector playAreaOffset;
    private BoundingBox resetTemplateArea;
    private Vector resetPasteOffset;

    public MapConfig(WatheBukkit plugin) {
        this.plugin = plugin;
        loadMapConfig();
    }

    private void loadMapConfig() {
        mapFile = new File(plugin.getDataFolder(), "map.yml");
        if (!mapFile.exists()) {
            createDefaultMapConfig();
        }
        mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        loadValues();
    }

    private void createDefaultMapConfig() {
        try {
            mapFile.getParentFile().mkdirs();
            mapFile.createNewFile();

            FileConfiguration config = YamlConfiguration.loadConfiguration(mapFile);

            config.set("world", "world");

            config.set("spawn.x", 0.5);
            config.set("spawn.y", 64);
            config.set("spawn.z", 0.5);
            config.set("spawn.yaw", 0);
            config.set("spawn.pitch", 0);

            config.set("spectator-spawn.x", 0.5);
            config.set("spectator-spawn.y", 100);
            config.set("spectator-spawn.z", 0.5);
            config.set("spectator-spawn.yaw", 0);
            config.set("spectator-spawn.pitch", 45);

            config.set("ready-area.pos1.x", -10);
            config.set("ready-area.pos1.y", 60);
            config.set("ready-area.pos1.z", -10);
            config.set("ready-area.pos2.x", 10);
            config.set("ready-area.pos2.y", 70);
            config.set("ready-area.pos2.z", 10);

            config.set("play-area.pos1.x", -100);
            config.set("play-area.pos1.y", 0);
            config.set("play-area.pos1.z", -100);
            config.set("play-area.pos2.x", 100);
            config.set("play-area.pos2.y", 256);
            config.set("play-area.pos2.z", 100);

            config.set("inside-area.pos1.x", -20);
            config.set("inside-area.pos1.y", 0);
            config.set("inside-area.pos1.z", -20);
            config.set("inside-area.pos2.x", 20);
            config.set("inside-area.pos2.y", 256);
            config.set("inside-area.pos2.z", 20);

            config.set("play-area-offset.x", 0);
            config.set("play-area-offset.y", 0);
            config.set("play-area-offset.z", 0);

            config.set("reset-template-area.pos1.x", 0);
            config.set("reset-template-area.pos1.y", 0);
            config.set("reset-template-area.pos1.z", 0);
            config.set("reset-template-area.pos2.x", 0);
            config.set("reset-template-area.pos2.y", 0);
            config.set("reset-template-area.pos2.z", 0);

            config.set("reset-paste-offset.x", 0);
            config.set("reset-paste-offset.y", 0);
            config.set("reset-paste-offset.z", 0);

            config.save(mapFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create map.yml: " + e.getMessage());
        }
    }

    private void loadValues() {
        String worldName = mapConfig.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }

        spawnLocation = new Location(
                world,
                mapConfig.getDouble("spawn.x", 0.5),
                mapConfig.getDouble("spawn.y", 64),
                mapConfig.getDouble("spawn.z", 0.5),
                (float) mapConfig.getDouble("spawn.yaw", 0),
                (float) mapConfig.getDouble("spawn.pitch", 0)
        );

        spectatorSpawnLocation = new Location(
                world,
                mapConfig.getDouble("spectator-spawn.x", 0.5),
                mapConfig.getDouble("spectator-spawn.y", 100),
                mapConfig.getDouble("spectator-spawn.z", 0.5),
                (float) mapConfig.getDouble("spectator-spawn.yaw", 0),
                (float) mapConfig.getDouble("spectator-spawn.pitch", 45)
        );

        readyArea = new BoundingBox(
                mapConfig.getDouble("ready-area.pos1.x", -10),
                mapConfig.getDouble("ready-area.pos1.y", 60),
                mapConfig.getDouble("ready-area.pos1.z", -10),
                mapConfig.getDouble("ready-area.pos2.x", 10),
                mapConfig.getDouble("ready-area.pos2.y", 70),
                mapConfig.getDouble("ready-area.pos2.z", 10)
        );

        playArea = new BoundingBox(
                mapConfig.getDouble("play-area.pos1.x", -100),
                mapConfig.getDouble("play-area.pos1.y", 0),
                mapConfig.getDouble("play-area.pos1.z", -100),
                mapConfig.getDouble("play-area.pos2.x", 100),
                mapConfig.getDouble("play-area.pos2.y", 256),
                mapConfig.getDouble("play-area.pos2.z", 100)
        );

        insideArea = new BoundingBox(
                mapConfig.getDouble("inside-area.pos1.x", -20),
                mapConfig.getDouble("inside-area.pos1.y", 0),
                mapConfig.getDouble("inside-area.pos1.z", -20),
                mapConfig.getDouble("inside-area.pos2.x", 20),
                mapConfig.getDouble("inside-area.pos2.y", 256),
                mapConfig.getDouble("inside-area.pos2.z", 20)
        );

        playAreaOffset = new Vector(
                mapConfig.getDouble("play-area-offset.x", 0),
                mapConfig.getDouble("play-area-offset.y", 0),
                mapConfig.getDouble("play-area-offset.z", 0)
        );

        resetTemplateArea = new BoundingBox(
                mapConfig.getDouble("reset-template-area.pos1.x", 0),
                mapConfig.getDouble("reset-template-area.pos1.y", 0),
                mapConfig.getDouble("reset-template-area.pos1.z", 0),
                mapConfig.getDouble("reset-template-area.pos2.x", 0),
                mapConfig.getDouble("reset-template-area.pos2.y", 0),
                mapConfig.getDouble("reset-template-area.pos2.z", 0)
        );

        resetPasteOffset = new Vector(
                mapConfig.getDouble("reset-paste-offset.x", 0),
                mapConfig.getDouble("reset-paste-offset.y", 0),
                mapConfig.getDouble("reset-paste-offset.z", 0)
        );
    }

    public void save() {
        try {
            String worldName = spawnLocation.getWorld() != null ? spawnLocation.getWorld().getName() : "world";
            mapConfig.set("world", worldName);

            mapConfig.set("spawn.x", spawnLocation.getX());
            mapConfig.set("spawn.y", spawnLocation.getY());
            mapConfig.set("spawn.z", spawnLocation.getZ());
            mapConfig.set("spawn.yaw", spawnLocation.getYaw());
            mapConfig.set("spawn.pitch", spawnLocation.getPitch());

            mapConfig.set("spectator-spawn.x", spectatorSpawnLocation.getX());
            mapConfig.set("spectator-spawn.y", spectatorSpawnLocation.getY());
            mapConfig.set("spectator-spawn.z", spectatorSpawnLocation.getZ());
            mapConfig.set("spectator-spawn.yaw", spectatorSpawnLocation.getYaw());
            mapConfig.set("spectator-spawn.pitch", spectatorSpawnLocation.getPitch());

            mapConfig.set("ready-area.pos1.x", readyArea.getMinX());
            mapConfig.set("ready-area.pos1.y", readyArea.getMinY());
            mapConfig.set("ready-area.pos1.z", readyArea.getMinZ());
            mapConfig.set("ready-area.pos2.x", readyArea.getMaxX());
            mapConfig.set("ready-area.pos2.y", readyArea.getMaxY());
            mapConfig.set("ready-area.pos2.z", readyArea.getMaxZ());

            mapConfig.set("play-area.pos1.x", playArea.getMinX());
            mapConfig.set("play-area.pos1.y", playArea.getMinY());
            mapConfig.set("play-area.pos1.z", playArea.getMinZ());
            mapConfig.set("play-area.pos2.x", playArea.getMaxX());
            mapConfig.set("play-area.pos2.y", playArea.getMaxY());
            mapConfig.set("play-area.pos2.z", playArea.getMaxZ());

            mapConfig.set("inside-area.pos1.x", insideArea.getMinX());
            mapConfig.set("inside-area.pos1.y", insideArea.getMinY());
            mapConfig.set("inside-area.pos1.z", insideArea.getMinZ());
            mapConfig.set("inside-area.pos2.x", insideArea.getMaxX());
            mapConfig.set("inside-area.pos2.y", insideArea.getMaxY());
            mapConfig.set("inside-area.pos2.z", insideArea.getMaxZ());

            mapConfig.set("play-area-offset.x", playAreaOffset.getX());
            mapConfig.set("play-area-offset.y", playAreaOffset.getY());
            mapConfig.set("play-area-offset.z", playAreaOffset.getZ());

            mapConfig.set("reset-template-area.pos1.x", resetTemplateArea.getMinX());
            mapConfig.set("reset-template-area.pos1.y", resetTemplateArea.getMinY());
            mapConfig.set("reset-template-area.pos1.z", resetTemplateArea.getMinZ());
            mapConfig.set("reset-template-area.pos2.x", resetTemplateArea.getMaxX());
            mapConfig.set("reset-template-area.pos2.y", resetTemplateArea.getMaxY());
            mapConfig.set("reset-template-area.pos2.z", resetTemplateArea.getMaxZ());

            mapConfig.set("reset-paste-offset.x", resetPasteOffset.getX());
            mapConfig.set("reset-paste-offset.y", resetPasteOffset.getY());
            mapConfig.set("reset-paste-offset.z", resetPasteOffset.getZ());

            mapConfig.save(mapFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save map.yml: " + e.getMessage());
        }
    }

    public void reload() {
        mapConfig = YamlConfiguration.loadConfiguration(mapFile);
        loadValues();
    }

    // Getters and Setters
    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location.clone();
    }

    public Location getSpectatorSpawnLocation() {
        return spectatorSpawnLocation.clone();
    }

    public void setSpectatorSpawnLocation(Location location) {
        this.spectatorSpawnLocation = location.clone();
    }

    public BoundingBox getReadyArea() {
        return readyArea.clone();
    }

    public void setReadyArea(BoundingBox box) {
        this.readyArea = box.clone();
    }

    public BoundingBox getPlayArea() {
        return playArea.clone();
    }

    public void setPlayArea(BoundingBox box) {
        this.playArea = box.clone();
    }

    public Vector getPlayAreaOffset() {
        return playAreaOffset.clone();
    }

    public void setPlayAreaOffset(Vector offset) {
        this.playAreaOffset = offset.clone();
    }

    public BoundingBox getInsideArea() {
        return insideArea.clone();
    }

    public void setInsideArea(BoundingBox box) {
        this.insideArea = box.clone();
    }

    public BoundingBox getResetTemplateArea() {
        return resetTemplateArea.clone();
    }

    public void setResetTemplateArea(BoundingBox box) {
        this.resetTemplateArea = box.clone();
    }

    public Vector getResetPasteOffset() {
        return resetPasteOffset.clone();
    }

    public void setResetPasteOffset(Vector offset) {
        this.resetPasteOffset = offset.clone();
    }

    public boolean isInPlayArea(Location location) {
        return playArea.contains(location.toVector());
    }

    public boolean isInReadyArea(Location location) {
        return readyArea.contains(location.toVector());
    }

    public boolean isInInsideArea(Location location) {
        return insideArea.contains(location.toVector());
    }
}

package dev.doctor4t.wathe.bukkit.scenery;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 山景片段，存储一组 Block Display Entity
 */
public class ScenerySegment {

    private final List<BlockDisplay> displays = new ArrayList<>();
    private final World world;
    private final Location baseLocation;
    private double currentOffset = 0;

    public ScenerySegment(World world, Location baseLocation) {
        this.world = world;
        this.baseLocation = baseLocation.clone();
    }

    /**
     * 添加一个方块到片段
     */
    public void addBlock(BlockData blockData, double relativeX, double relativeY, double relativeZ) {
        Location spawnLoc = baseLocation.clone().add(relativeX, relativeY, relativeZ);
        
        BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, entity -> {
            entity.setBlock(blockData);
            entity.setPersistent(false);
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(1);
        });
        
        displays.add(display);
    }

    /**
     * 沿指定轴移动片段
     * @param axis 移动轴 (X 或 Z)
     * @param offset 偏移量
     */
    public void move(char axis, double offset) {
        this.currentOffset += offset;
        
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                Location loc = display.getLocation();
                if (axis == 'X' || axis == 'x') {
                    loc.setX(loc.getX() + offset);
                } else {
                    loc.setZ(loc.getZ() + offset);
                }
                
                display.setInterpolationDelay(0);
                display.teleport(loc);
            }
        }
    }

    /**
     * 传送整个片段到新位置（用于循环）
     */
    public void teleportTo(char axis, double position) {
        double delta = position - currentOffset;
        this.currentOffset = position;
        
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                Location loc = display.getLocation();
                if (axis == 'X' || axis == 'x') {
                    loc.setX(loc.getX() + delta);
                } else {
                    loc.setZ(loc.getZ() + delta);
                }
                
                display.setInterpolationDelay(-1);
                display.teleport(loc);
            }
        }
    }

    /**
     * 获取当前偏移量
     */
    public double getCurrentOffset() {
        return currentOffset;
    }

    /**
     * 清理所有 Display Entity
     */
    public void cleanup() {
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    /**
     * 获取片段中的 Display 数量
     */
    public int size() {
        return displays.size();
    }

    /**
     * 获取基础位置
     */
    public Location getBaseLocation() {
        return baseLocation.clone();
    }
}

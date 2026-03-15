package dev.doctor4t.wathe.bukkit.scenery;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * 山景管理器，负责加载、移动和清理山景
 */
public class SceneryManager {

    private final WatheBukkit plugin;
    private final List<ScenerySegment> segments = new ArrayList<>();
    
    private ScheduledTask moveTask;
    private boolean isMoving = false;
    
    // 配置
    private double speed = 0.5; // 格/tick
    private char moveAxis = 'X';
    private double loopDistance = 200; // 循环距离
    private double resetPosition = -200; // 重置位置

    public SceneryManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * 从世界区域加载山景
     * @param world 世界
     * @param pos1 区域角1
     * @param pos2 区域角2
     * @param displayPos Display Entity 生成的基础位置
     * @param segmentCount 循环片段数量
     */
    public void loadFromRegion(World world, Location pos1, Location pos2, Location displayPos, int segmentCount) {
        cleanup();
        
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        double segmentLength = (moveAxis == 'X' || moveAxis == 'x') ? sizeX : sizeZ;
        
        // 创建多个片段用于循环
        for (int s = 0; s < segmentCount; s++) {
            double offsetAlongAxis = s * segmentLength;
            Location segmentBase = displayPos.clone();
            
            if (moveAxis == 'X' || moveAxis == 'x') {
                segmentBase.setX(segmentBase.getX() + offsetAlongAxis);
            } else {
                segmentBase.setZ(segmentBase.getZ() + offsetAlongAxis);
            }
            
            ScenerySegment segment = new ScenerySegment(displayPos.getWorld(), segmentBase);
            
            // 扫描区域中的方块
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() != Material.AIR) {
                            BlockData blockData = block.getBlockData().clone();
                            double relX = x - minX;
                            double relY = y - minY;
                            double relZ = z - minZ;
                            segment.addBlock(blockData, relX, relY, relZ);
                        }
                    }
                }
            }
            
            segments.add(segment);
            plugin.getComponentLogger().info("Loaded scenery segment {} with {} blocks", s, segment.size());
        }
        
        // 更新循环距离
        this.loopDistance = segmentLength * segmentCount;
        this.resetPosition = -segmentLength;
        
        plugin.getComponentLogger().info("Loaded {} scenery segments, loop distance: {}", segments.size(), loopDistance);
    }

    /**
     * 开始移动山景
     */
    public void startMoving() {
        if (isMoving || segments.isEmpty()) {
            return;
        }
        
        isMoving = true;
        
        moveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (ScenerySegment segment : segments) {
                segment.move(moveAxis, -speed); // 负方向移动，模拟列车前进
                
                // 检查是否需要循环
                if (segment.getCurrentOffset() < resetPosition) {
                    segment.teleportTo(moveAxis, segment.getCurrentOffset() + loopDistance);
                }
            }
        }, 1L, 1L);
    }

    /**
     * 停止移动山景
     */
    public void stopMoving() {
        isMoving = false;
        
        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }
    }

    /**
     * 清理所有山景
     */
    public void cleanup() {
        stopMoving();
        
        for (ScenerySegment segment : segments) {
            segment.cleanup();
        }
        segments.clear();
    }

    /**
     * 设置移动速度
     */
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /**
     * 获取移动速度
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * 设置移动轴
     */
    public void setMoveAxis(char axis) {
        this.moveAxis = axis;
    }

    /**
     * 获取移动轴
     */
    public char getMoveAxis() {
        return moveAxis;
    }

    /**
     * 是否正在移动
     */
    public boolean isMoving() {
        return isMoving;
    }

    /**
     * 获取片段数量
     */
    public int getSegmentCount() {
        return segments.size();
    }
}

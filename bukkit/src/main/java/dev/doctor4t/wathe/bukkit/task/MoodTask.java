package dev.doctor4t.wathe.bukkit.task;

import org.bukkit.entity.Player;

/**
 * 心情任务接口
 */
public interface MoodTask {
    
    /**
     * 获取任务类型
     */
    TaskType getType();
    
    /**
     * 每tick调用
     */
    default void tick(Player player) {}
    
    /**
     * 检查任务是否完成
     */
    boolean isFulfilled(Player player);
    
    /**
     * 获取任务名称（用于显示）
     */
    default String getName() {
        return getType().name().toLowerCase();
    }
}

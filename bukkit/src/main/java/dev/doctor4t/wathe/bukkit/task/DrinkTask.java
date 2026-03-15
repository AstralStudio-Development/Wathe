package dev.doctor4t.wathe.bukkit.task;

import org.bukkit.entity.Player;

/**
 * 喝酒任务 - 玩家需要喝鸡尾酒/饮料
 */
public class DrinkTask implements MoodTask {
    
    private boolean fulfilled = false;
    
    @Override
    public TaskType getType() {
        return TaskType.DRINK;
    }
    
    @Override
    public boolean isFulfilled(Player player) {
        return fulfilled;
    }
    
    /**
     * 标记任务完成（当玩家喝饮料时调用）
     */
    public void complete() {
        this.fulfilled = true;
    }
    
    public boolean isCompleted() {
        return fulfilled;
    }
}

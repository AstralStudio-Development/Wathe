package dev.doctor4t.wathe.bukkit.task;

import org.bukkit.entity.Player;

/**
 * 吃饭任务 - 玩家需要吃东西
 */
public class EatTask implements MoodTask {
    
    private boolean fulfilled = false;
    
    @Override
    public TaskType getType() {
        return TaskType.EAT;
    }
    
    @Override
    public boolean isFulfilled(Player player) {
        return fulfilled;
    }
    
    /**
     * 标记任务完成（当玩家吃东西时调用）
     */
    public void complete() {
        this.fulfilled = true;
    }
    
    public boolean isCompleted() {
        return fulfilled;
    }
}

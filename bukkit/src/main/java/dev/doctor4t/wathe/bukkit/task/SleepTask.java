package dev.doctor4t.wathe.bukkit.task;

import org.bukkit.entity.Player;

/**
 * 睡觉任务 - 玩家需要在床上睡一段时间
 */
public class SleepTask implements MoodTask {
    
    private int remainingTicks;
    
    public SleepTask(int durationTicks) {
        this.remainingTicks = durationTicks;
    }
    
    @Override
    public TaskType getType() {
        return TaskType.SLEEP;
    }
    
    @Override
    public void tick(Player player) {
        if (player.isSleeping() && remainingTicks > 0) {
            remainingTicks--;
        }
    }
    
    @Override
    public boolean isFulfilled(Player player) {
        return remainingTicks <= 0;
    }
    
    public int getRemainingTicks() {
        return remainingTicks;
    }
}

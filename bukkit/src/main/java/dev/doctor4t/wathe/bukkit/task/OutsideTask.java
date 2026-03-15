package dev.doctor4t.wathe.bukkit.task;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import org.bukkit.entity.Player;

/**
 * 外出任务：玩家需要在 inside-area 之外停留一段时间。
 */
public class OutsideTask implements MoodTask {

    private int remainingTicks;

    public OutsideTask(int durationTicks) {
        this.remainingTicks = durationTicks;
    }

    @Override
    public TaskType getType() {
        return TaskType.OUTSIDE;
    }

    @Override
    public void tick(Player player) {
        if (isOutsideInsideArea(player) && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    @Override
    public boolean isFulfilled(Player player) {
        return remainingTicks <= 0;
    }

    private boolean isOutsideInsideArea(Player player) {
        WatheBukkit plugin = WatheBukkit.getInstance();
        return plugin != null && !plugin.getMapConfig().isInInsideArea(player.getLocation());
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }
}

package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.api.Role;
import dev.doctor4t.wathe.bukkit.task.MoodTask;
import dev.doctor4t.wathe.bukkit.task.TaskType;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class GamePlayer {

    private final UUID uuid;
    private final String name;
    private Role role;
    private boolean alive;
    private int money;
    private float mood;
    private long lastTaskTime;
    private float sprintTicks;
    
    // 任务系统
    private final Map<TaskType, MoodTask> activeTasks = new EnumMap<>(TaskType.class);
    private final Map<TaskType, Integer> taskTimesGotten = new EnumMap<>(TaskType.class);
    private int nextTaskTimer;

    public GamePlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.alive = true;
        this.money = 100;
        this.mood = 1.0f;
        this.lastTaskTime = 0;
        this.sprintTicks = 0;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public Role role() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
        // 体力系统：分配角色时按角色冲刺上限充满体力；-1 表示无限体力（杀手）
        if (role != null) {
            this.sprintTicks = role.maxSprintTicks() >= 0 ? role.maxSprintTicks() : 0;
        }
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int money() {
        return money;
    }

    public void setMoney(int money) {
        this.money = money;
    }

    public void addMoney(int amount) {
        this.money += amount;
    }

    public boolean removeMoney(int amount) {
        if (this.money >= amount) {
            this.money -= amount;
            return true;
        }
        return false;
    }

    public float mood() {
        return mood;
    }

    public void setMood(float mood) {
        this.mood = Math.clamp(mood, 0f, 1f);
    }

    public void addMood(float amount) {
        setMood(this.mood + amount);
    }

    public void drainMood(float amount) {
        setMood(this.mood - amount);
    }

    public long lastTaskTime() {
        return lastTaskTime;
    }

    public void setLastTaskTime(long lastTaskTime) {
        this.lastTaskTime = lastTaskTime;
    }

    public float sprintTicks() {
        return sprintTicks;
    }

    public void setSprintTicks(float sprintTicks) {
        this.sprintTicks = sprintTicks;
    }

    public void incrementSprintTicks() {
        this.sprintTicks++;
    }

    public void resetSprintTicks() {
        this.sprintTicks = 0;
    }

    public boolean isKiller() {
        return role != null && role.isKiller();
    }

    public boolean canUseKillerItems() {
        return role != null && role.canUseKillerItems();
    }
    
    // 任务系统方法
    public Map<TaskType, MoodTask> getActiveTasks() {
        return activeTasks;
    }
    
    public boolean hasTask(TaskType type) {
        return activeTasks.containsKey(type);
    }
    
    public MoodTask getTask(TaskType type) {
        return activeTasks.get(type);
    }
    
    public void addTask(MoodTask task) {
        activeTasks.put(task.getType(), task);
        taskTimesGotten.merge(task.getType(), 1, Integer::sum);
    }
    
    public void removeTask(TaskType type) {
        activeTasks.remove(type);
    }
    
    public void clearTasks() {
        activeTasks.clear();
    }
    
    public int getTaskTimesGotten(TaskType type) {
        return taskTimesGotten.getOrDefault(type, 0);
    }
    
    public int getNextTaskTimer() {
        return nextTaskTimer;
    }
    
    public void setNextTaskTimer(int timer) {
        this.nextTaskTimer = timer;
    }
    
    public void decrementNextTaskTimer() {
        if (nextTaskTimer > 0) {
            nextTaskTimer--;
        }
    }
    
    public void resetTaskData() {
        activeTasks.clear();
        taskTimesGotten.clear();
        nextTaskTimer = 0;
    }
    
    /**
     * 获取当前任务类型（用于PAPI）
     */
    public TaskType currentTask() {
        if (activeTasks.isEmpty()) {
            return null;
        }
        return activeTasks.keySet().iterator().next();
    }
}

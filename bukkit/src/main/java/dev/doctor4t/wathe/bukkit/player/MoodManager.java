package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.api.Role;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.task.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MoodManager {

    private final WatheBukkit plugin;

    public MoodManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (GamePlayer gp : plugin.getPlayerManager().getAllPlayers()) {
            if (!gp.isAlive()) continue;

            Role role = gp.role();
            if (role == null || role.moodType() == Role.MoodType.NONE) continue;

            // 只有REAL心情类型才会下降和生成任务
            if (role.moodType() == Role.MoodType.REAL) {
                // 只有有任务时才会掉心情（与原版一致）
                int taskCount = gp.getActiveTasks().size();
                if (taskCount > 0) {
                    gp.drainMood(GameConstants.getMoodDrainPerTick() * taskCount);
                }
                
                // 任务生成逻辑
                tickTaskGeneration(gp);
                
                // 任务tick和完成检测
                tickTasks(gp);
            }
            
            // FAKE心情类型（杀手）生成假任务
            if (role.moodType() == Role.MoodType.FAKE) {
                tickFakeTaskGeneration(gp);
            }
        }
    }
    
    private void tickTaskGeneration(GamePlayer gp) {
        gp.decrementNextTaskTimer();
        
        // 只有当timer到0且没有任务时才生成新任务
        if (gp.getNextTaskTimer() <= 0) {
            if (gp.getActiveTasks().isEmpty()) {
                MoodTask task = generateTask(gp);
                if (task != null) {
                    gp.addTask(task);
                }
            }
            
            // 无论是否生成任务，都重置timer
            int min = GameConstants.getMinTaskCooldown();
            int max = GameConstants.getMaxTaskCooldown();
            gp.setNextTaskTimer(ThreadLocalRandom.current().nextInt(min, max + 1));
        }
    }
    
    private MoodTask generateTask(GamePlayer gp) {
        // 如果已有任务则不生成新任务
        if (!gp.getActiveTasks().isEmpty()) {
            return null;
        }
        
        // 权重随机选择任务类型
        Map<TaskType, Float> weights = new EnumMap<>(TaskType.class);
        float totalWeight = 0f;
        
        for (TaskType type : TaskType.values()) {
            if (gp.hasTask(type)) continue;
            
            // 权重与获得次数成反比
            float weight = 1f / (gp.getTaskTimesGotten(type) + 1);
            weights.put(type, weight);
            totalWeight += weight;
        }
        
        if (totalWeight <= 0) return null;
        
        float random = ThreadLocalRandom.current().nextFloat() * totalWeight;
        for (Map.Entry<TaskType, Float> entry : weights.entrySet()) {
            random -= entry.getValue();
            if (random <= 0) {
                return createTask(entry.getKey());
            }
        }
        
        return null;
    }
    
    private MoodTask createTask(TaskType type) {
        return switch (type) {
            case SLEEP -> new SleepTask(GameConstants.getSleepTaskDuration());
            case OUTSIDE -> new OutsideTask(GameConstants.getOutsideTaskDuration());
            case EAT -> new EatTask();
            case DRINK -> new DrinkTask();
        };
    }
    
    private void tickTasks(GamePlayer gp) {
        Player player = Bukkit.getPlayer(gp.uuid());
        if (player == null) return;
        
        List<TaskType> completedTasks = new ArrayList<>();
        
        for (MoodTask task : gp.getActiveTasks().values()) {
            task.tick(player);
            
            if (task.isFulfilled(player)) {
                completedTasks.add(task.getType());
            }
        }
        
        // 处理完成的任务
        for (TaskType type : completedTasks) {
            gp.removeTask(type);
            gp.addMood(GameConstants.getMoodGain());
        }
    }
    
    /**
     * 杀手假任务生成逻辑
     */
    private void tickFakeTaskGeneration(GamePlayer gp) {
        gp.decrementNextTaskTimer();
        
        if (gp.getNextTaskTimer() <= 0) {
            // 随机生成一个假任务类型
            TaskType[] types = TaskType.values();
            TaskType fakeTask = types[ThreadLocalRandom.current().nextInt(types.length)];
            
            // 清除旧任务，设置新的假任务
            gp.clearTasks();
            gp.addTask(createTask(fakeTask));
            
            // 设置下一个假任务的冷却时间
            int min = GameConstants.getMinTaskCooldown();
            int max = GameConstants.getMaxTaskCooldown();
            gp.setNextTaskTimer(ThreadLocalRandom.current().nextInt(min, max + 1));
        }
    }
    
    /**
     * 玩家吃东西时调用
     */
    public void onPlayerEat(GamePlayer gp) {
        MoodTask task = gp.getTask(TaskType.EAT);
        if (task instanceof EatTask eatTask) {
            eatTask.complete();
        }
    }
    
    /**
     * 玩家喝饮料时调用
     */
    public void onPlayerDrink(GamePlayer gp) {
        MoodTask task = gp.getTask(TaskType.DRINK);
        if (task instanceof DrinkTask drinkTask) {
            drinkTask.complete();
        }
    }
    
    /**
     * 初始化玩家的任务系统
     */
    public void initPlayer(GamePlayer gp) {
        gp.resetTaskData();
        gp.setNextTaskTimer(GameConstants.getTimeToFirstTask());
        gp.setMood(1.0f);
    }
    
    /**
     * 重置玩家的任务系统
     */
    public void resetPlayer(GamePlayer gp) {
        gp.resetTaskData();
    }
}

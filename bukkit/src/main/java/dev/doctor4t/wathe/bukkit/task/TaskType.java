package dev.doctor4t.wathe.bukkit.task;

/**
 * 任务类型枚举
 */
public enum TaskType {
    SLEEP("睡觉", "sleep"),
    OUTSIDE("外出", "outside"),
    EAT("吃饭", "eat"),
    DRINK("喝酒", "drink");
    
    private final String displayName;
    private final String id;
    
    TaskType(String displayName, String id) {
        this.displayName = displayName;
        this.id = id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getId() {
        return id;
    }
}

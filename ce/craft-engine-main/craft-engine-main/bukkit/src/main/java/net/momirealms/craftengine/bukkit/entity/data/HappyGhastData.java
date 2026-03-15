package net.momirealms.craftengine.bukkit.entity.data;

public class HappyGhastData<T> extends AnimalData<T> {
    public static final HappyGhastData<Boolean> IsLeashHolder = new HappyGhastData<>(HappyGhastData.class, EntityDataValue.Serializers$BOOLEAN, false);
    public static final BaseEntityData<Boolean> StaysStill = new HappyGhastData<>(HappyGhastData.class, EntityDataValue.Serializers$BOOLEAN, false);

    public HappyGhastData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}

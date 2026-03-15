package net.momirealms.craftengine.bukkit.entity.data;

public class AgeableMobData<T> extends PathfinderMobData<T> {
    public static final MobData<Boolean> Baby = new AgeableMobData<>(AgeableMobData.class, EntityDataValue.Serializers$BOOLEAN, false);

    public AgeableMobData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}

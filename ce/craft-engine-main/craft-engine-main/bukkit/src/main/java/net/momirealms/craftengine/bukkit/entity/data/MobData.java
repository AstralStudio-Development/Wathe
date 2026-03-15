package net.momirealms.craftengine.bukkit.entity.data;

public class MobData<T> extends LivingEntityData<T> {
    public static final MobData<Byte> MobFlags = new MobData<>(MobData.class, EntityDataValue.Serializers$BYTE, (byte) 0);

    public MobData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}
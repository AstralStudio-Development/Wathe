package net.momirealms.craftengine.bukkit.entity.data;

import java.util.List;
import java.util.Optional;

public class LivingEntityData<T> extends BaseEntityData<T> {
    public static final LivingEntityData<Byte> LivingEntityFlags = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$BYTE, (byte) 0);
    public static final LivingEntityData<Float> Health = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$FLOAT, 1.0f);
    public static final LivingEntityData<List<Object>> EffectParticles = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$PARTICLES, List.of());
    public static final LivingEntityData<Boolean> EffectAmbience = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$BOOLEAN, false);
    public static final LivingEntityData<Integer> ArrowCount = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$INT, 0);
    public static final LivingEntityData<Integer> StingerCount = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$INT, 0);
    public static final LivingEntityData<Optional<Object>> SleepingPos = new LivingEntityData<>(LivingEntityData.class, EntityDataValue.Serializers$OPTIONAL_BLOCK_POS, Optional.empty());

    public LivingEntityData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}
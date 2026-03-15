package net.momirealms.craftengine.bukkit.entity.data;

public class InteractionEntityData<T> extends BaseEntityData<T> {
    public static final InteractionEntityData<Float> Width = new InteractionEntityData<>(InteractionEntityData.class, EntityDataValue.Serializers$FLOAT, 1F);
    public static final InteractionEntityData<Float> Height = new InteractionEntityData<>(InteractionEntityData.class, EntityDataValue.Serializers$FLOAT, 1F);
    public static final InteractionEntityData<Boolean> Responsive = new InteractionEntityData<>(InteractionEntityData.class, EntityDataValue.Serializers$BOOLEAN, false);

    public InteractionEntityData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}

package net.momirealms.craftengine.bukkit.entity.data;

public class AnimalData<T> extends AgeableMobData<T> {

    public AnimalData(Class<?> clazz, Object serializer, T defaultValue) {
        super(clazz, serializer, defaultValue);
    }
}

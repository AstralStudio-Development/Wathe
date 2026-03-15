package net.momirealms.craftengine.core.item.setting;

public class FoodData {
    private final int nutrition;
    private final float saturation;

    public FoodData(int nutrition, float saturation) {
        this.nutrition = nutrition;
        this.saturation = saturation;
    }

    public int nutrition() {
        return nutrition;
    }

    public float saturation() {
        return saturation;
    }
}

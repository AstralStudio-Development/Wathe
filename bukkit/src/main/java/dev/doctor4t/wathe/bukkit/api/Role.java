package dev.doctor4t.wathe.bukkit.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public record Role(
        String id,
        String displayName,
        TextColor color,
        boolean innocent,
        boolean canUseKillerItems,
        MoodType moodType,
        int maxSprintTicks,
        boolean canSeeTimer
) {
    public enum MoodType {
        NONE, REAL, FAKE
    }

    public Component coloredName() {
        return Component.text(displayName, color);
    }

    public String coloredNameLegacy() {
        return color.asHexString() + displayName;
    }

    public String name() {
        return displayName;
    }

    public boolean isKiller() {
        return !innocent;
    }
}

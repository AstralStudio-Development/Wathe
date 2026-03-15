package dev.doctor4t.wathe.bukkit.api;

import net.kyori.adventure.text.format.TextColor;

import java.util.HashMap;
import java.util.Map;

public final class WatheRoles {

    private static final Map<String, Role> ROLES = new HashMap<>();

    public static final Role CIVILIAN = register(new Role(
            "civilian",
            "平民",
            TextColor.color(0x36E51B),
            true,
            false,
            Role.MoodType.REAL,
            200,
            false
    ));

    public static final Role VIGILANTE = register(new Role(
            "vigilante",
            "义警",
            TextColor.color(0x1B8AE5),
            true,
            false,
            Role.MoodType.REAL,
            200,
            false
    ));

    public static final Role KILLER = register(new Role(
            "killer",
            "杀手",
            TextColor.color(0xC13838),
            false,
            true,
            Role.MoodType.FAKE,
            -1,
            false
    ));

    public static final Role LOOSE_END = register(new Role(
            "loose_end",
            "残局者",
            TextColor.color(0x9F0000),
            false,
            false,
            Role.MoodType.NONE,
            -1,
            false
    ));

    public static final Role DISCOVERY_CIVILIAN = register(new Role(
            "discovery_civilian",
            "探索者",
            TextColor.color(0x36E51B),
            true,
            false,
            Role.MoodType.NONE,
            -1,
            true
    ));

    private WatheRoles() {}

    private static Role register(Role role) {
        ROLES.put(role.id(), role);
        return role;
    }

    public static Role getById(String id) {
        return ROLES.get(id);
    }

    public static Map<String, Role> getAllRoles() {
        return Map.copyOf(ROLES);
    }
}

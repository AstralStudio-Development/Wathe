package net.momirealms.craftengine.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum PressurePlateSensitivity {
    EVERYTHING("everything", "all"),
    MOBS("mobs", "mob");

    private final String[] names;

    PressurePlateSensitivity(String... names) {
        this.names = names;
    }

    public String[] names() {
        return names;
    }

    private static final Map<String, PressurePlateSensitivity> BY_NAME = new HashMap<>();

    static {
        for (PressurePlateSensitivity trigger : PressurePlateSensitivity.values()) {
            for (String name : trigger.names()) {
                BY_NAME.put(name, trigger);
            }
        }
    }

    public static PressurePlateSensitivity byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name)).orElseThrow(() -> new IllegalArgumentException("PressurePlateSensitivity not found: " + name));
    }
}

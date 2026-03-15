package dev.doctor4t.wathe.bukkit.api;

import dev.doctor4t.wathe.bukkit.game.GameManager;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class GameMode {

    protected final String id;
    protected final String displayName;
    protected final int defaultTimerMinutes;
    protected final int minPlayers;

    protected GameMode(String id, String displayName, int defaultTimerMinutes, int minPlayers) {
        this.id = id;
        this.displayName = displayName;
        this.defaultTimerMinutes = defaultTimerMinutes;
        this.minPlayers = minPlayers;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultTimerMinutes() {
        return defaultTimerMinutes;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public abstract void initialize(GameManager gameManager, List<Player> players);

    public abstract void tick(GameManager gameManager);

    public abstract void onPlayerDeath(GameManager gameManager, Player victim, Player killer);

    public abstract void checkWinCondition(GameManager gameManager);

    public void onFinalize(GameManager gameManager) {
    }
}

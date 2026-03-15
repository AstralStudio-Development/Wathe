package dev.doctor4t.wathe.bukkit.game;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AutoStartManager {

    private final WatheBukkit plugin;
    private ScheduledTask checkTask;
    private int countdownTicks = -1;

    public AutoStartManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (checkTask != null) {
            return;
        }
        checkTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick(), 1L, 1L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        countdownTicks = -1;
    }

    private void tick() {
        // 游戏已经在运行，不需要自动开始
        if (plugin.getGameManager().isGameRunning()) {
            countdownTicks = -1;
            return;
        }

        int readyPlayers = getReadyPlayersCount();
        int minPlayers = GameConstants.getMinPlayersMurder();

        if (readyPlayers >= minPlayers) {
            // 人数足够，开始或继续倒计时
            if (countdownTicks < 0) {
                countdownTicks = GameConstants.getAutoStartCountdown();
            } else {
                countdownTicks--;
                if (countdownTicks <= 0) {
                    // 倒计时结束，开始游戏
                    startGame();
                    countdownTicks = -1;
                }
            }
        } else {
            // 人数不足，取消倒计时
            countdownTicks = -1;
        }
    }

    private void startGame() {
        plugin.getGameManager().startGame(new MurderGameMode());
    }

    private int getReadyPlayersCount() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.getMapConfig().isInReadyArea(p.getLocation())) {
                count++;
            }
        }
        return count;
    }

    public int getCountdownTicks() {
        return countdownTicks;
    }

    public int getCountdownSeconds() {
        if (countdownTicks < 0) {
            return -1;
        }
        return countdownTicks / 20;
    }

    public boolean isCountingDown() {
        return countdownTicks > 0;
    }
}

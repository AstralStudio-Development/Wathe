package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AmbientSoundManager {

    private static final String INSIDE_SOUND = "wathe:ambient.train.inside";
    private static final String OUTSIDE_SOUND = "wathe:ambient.train.outside";

    private final WatheBukkit plugin;
    private final Map<UUID, Boolean> lastInsideState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextLoopTick = new ConcurrentHashMap<>();
    private ScheduledTask tickTask;
    private long tickCounter = 0L;

    public AmbientSoundManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick(), 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopAllAmbient();
        lastInsideState.clear();
        nextLoopTick.clear();
    }

    public void stopAllAmbient() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound(INSIDE_SOUND, SoundCategory.AMBIENT);
            player.stopSound(OUTSIDE_SOUND, SoundCategory.AMBIENT);
        }
    }

    private void tick() {
        tickCounter++;

        if (!plugin.getGameManager().isGameRunning()) {
            stopAllAmbient();
            lastInsideState.clear();
            nextLoopTick.clear();
            return;
        }

        Set<UUID> seen = new HashSet<>();
        for (GamePlayer gp : plugin.getPlayerManager().getAllPlayers()) {
            seen.add(gp.uuid());
            if (!gp.isAlive()) {
                clearPlayerAmbient(gp.uuid());
                continue;
            }
            Player player = Bukkit.getPlayer(gp.uuid());
            if (player == null || !player.isOnline()) {
                clearPlayerAmbient(gp.uuid());
                continue;
            }

            boolean inside = plugin.getMapConfig().isInInsideArea(player.getLocation());
            UUID uuid = player.getUniqueId();
            Boolean last = lastInsideState.put(uuid, inside);

            if (last == null || last.booleanValue() != inside) {
                player.stopSound(INSIDE_SOUND, SoundCategory.AMBIENT);
                player.stopSound(OUTSIDE_SOUND, SoundCategory.AMBIENT);
                playTrack(player, inside);
                nextLoopTick.put(uuid, tickCounter + getLoopDuration(inside));
                continue;
            }

            long next = nextLoopTick.getOrDefault(uuid, 0L);
            if (tickCounter >= next) {
                playTrack(player, inside);
                nextLoopTick.put(uuid, tickCounter + getLoopDuration(inside));
            }
        }

        // 清理已离线/不在局内玩家的状态与残留声音
        for (UUID uuid : new HashSet<>(lastInsideState.keySet())) {
            if (!seen.contains(uuid)) {
                clearPlayerAmbient(uuid);
            }
        }
    }

    private void playTrack(Player player, boolean inside) {
        // 防止同一轨道重复叠加：重播前先清理已存在实例
        player.stopSound(INSIDE_SOUND, SoundCategory.AMBIENT);
        player.stopSound(OUTSIDE_SOUND, SoundCategory.AMBIENT);
        player.playSound(player, inside ? INSIDE_SOUND : OUTSIDE_SOUND, SoundCategory.AMBIENT, 1.0f, 1.0f);
    }

    private long getLoopDuration(boolean inside) {
        int ticks = inside ? GameConstants.getAmbientInsideLoopTicks() : GameConstants.getAmbientOutsideLoopTicks();
        return Math.max(1, ticks);
    }

    private void clearPlayerAmbient(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.stopSound(INSIDE_SOUND, SoundCategory.AMBIENT);
            player.stopSound(OUTSIDE_SOUND, SoundCategory.AMBIENT);
        }
        lastInsideState.remove(uuid);
        nextLoopTick.remove(uuid);
    }
}

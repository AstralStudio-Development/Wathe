package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViewShakeManager {

    private static final long SHAKE_INTERVAL_TICKS = 1L;
    private static final float LOOK_MOVE_THRESHOLD = 0.55f;
    private static final int STILL_REQUIRED_TICKS = 6;
    private static final float MAX_PITCH_DELTA_PER_TICK = 0.32f;

    private final WatheBukkit plugin;
    private final Map<UUID, Float> lastWavePitch = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastInputYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Float> lastInputPitch = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> stillTicks = new ConcurrentHashMap<>();
    private ScheduledTask task;

    public ViewShakeManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> tick(), 1L, SHAKE_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    private void tick() {
        if (!plugin.getGameManager().isGameRunning()) {
            clearAll();
            return;
        }

        for (GamePlayer gp : plugin.getPlayerManager().getAllPlayers()) {
            if (!gp.isAlive()) {
                clearTracking(gp.uuid());
                continue;
            }

            Player player = Bukkit.getPlayer(gp.uuid());
            if (player == null || !player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
                clearTracking(gp.uuid());
                continue;
            }

            UUID uuid = player.getUniqueId();
            float currentYaw = player.getYaw();
            float currentPitch = player.getPitch();
            boolean inside = plugin.getMapConfig().isInInsideArea(player.getLocation());
            float prevInputYaw = lastInputYaw.getOrDefault(uuid, currentYaw);
            float prevInputPitch = lastInputPitch.getOrDefault(uuid, currentPitch);
            lastInputYaw.put(uuid, currentYaw);
            lastInputPitch.put(uuid, currentPitch);

            boolean lookMoved = Math.abs(wrapDegrees(currentYaw - prevInputYaw)) > LOOK_MOVE_THRESHOLD
                    || Math.abs(currentPitch - prevInputPitch) > LOOK_MOVE_THRESHOLD;
            float targetPitch = computePitchOffset(player, gp, uuid, player.getTicksLived());
            // 仅车内在玩家移动鼠标时暂停晃动；车外始终晃动。
            if (inside) {
                if (lookMoved) {
                    stillTicks.put(uuid, 0);
                    // Re-anchor wave to avoid jump when shake resumes.
                    lastWavePitch.put(uuid, targetPitch);
                    continue;
                }

                int still = stillTicks.getOrDefault(uuid, 0) + 1;
                stillTicks.put(uuid, still);
                if (still < STILL_REQUIRED_TICKS) {
                    lastWavePitch.put(uuid, targetPitch);
                    continue;
                }
            } else {
                stillTicks.remove(uuid);
            }

            float prevWavePitch = lastWavePitch.getOrDefault(uuid, targetPitch);
            float deltaPitch = targetPitch - prevWavePitch;
            if (deltaPitch > MAX_PITCH_DELTA_PER_TICK) {
                deltaPitch = MAX_PITCH_DELTA_PER_TICK;
            } else if (deltaPitch < -MAX_PITCH_DELTA_PER_TICK) {
                deltaPitch = -MAX_PITCH_DELTA_PER_TICK;
            }

            if (Math.abs(deltaPitch) > 0.0001f) {
                float finalPitch = clampPitch(currentPitch + deltaPitch);
                player.setRotation(currentYaw, finalPitch);
            }

            lastWavePitch.put(uuid, targetPitch);
        }
    }

    private float computePitchOffset(Player player, GamePlayer gp, UUID uuid, int age) {
        float mood = Math.max(0.0f, Math.min(1.0f, gp.mood()));
        float v = (1.0f + (1.0f - mood)) * 2.5f;

        boolean inside = plugin.getMapConfig().isInInsideArea(player.getLocation());
        float amplitude = (inside ? 0.020f : 0.125f) * v;
        float strength = inside ? 0.50f : 1.65f;
        float time = age;

        float pitch = (float) Math.cos(time * strength) * amplitude;

        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        if (!inside) {
            float pitchNoise = smoothNoise(time, seed * 0.00000037D, seed * 0.00000053D);
            pitch += pitchNoise * (amplitude * 0.35f);

            // Outside has extra high-frequency jitter to mimic stronger wind exposure.
            pitch += (float) Math.sin(time * 3.6f) * (amplitude * 0.18f);
        }

        return pitch;
    }

    private void clearTracking(UUID uuid) {
        lastWavePitch.remove(uuid);
        lastInputYaw.remove(uuid);
        lastInputPitch.remove(uuid);
        stillTicks.remove(uuid);
    }

    private void clearAll() {
        lastWavePitch.clear();
        lastInputYaw.clear();
        lastInputPitch.clear();
        stillTicks.clear();
    }

    private float smoothNoise(float time, double phaseA, double phaseB) {
        double x = Math.sin(time * 0.13D + phaseA) * 0.65D;
        double y = Math.sin(time * 0.07D + phaseB) * 0.35D;
        return (float) (x + y);
    }

    private float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    private float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}

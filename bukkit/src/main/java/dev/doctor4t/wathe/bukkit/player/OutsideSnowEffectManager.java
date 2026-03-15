package dev.doctor4t.wathe.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class OutsideSnowEffectManager {

    private static final long TICK_INTERVAL = 1L;
    private static final int OUTSIDE_FULL_TICKS = 60; // 3 seconds
    private static final int INSIDE_FADE_STEP_TICKS = 1; // smooth fade out
    private static final int ENTITY_FROZEN_TICKS_INDEX = 7;
    private static final double DEFAULT_VISUAL_SCALE = 0.60D;
    private static final double KILLER_FACTION_VISUAL_SCALE = 0.20D;

    private final WatheBukkit plugin;
    private final Set<UUID> affectedPlayers = new HashSet<>();
    private final Map<UUID, Integer> exposureTicks = new HashMap<>();
    private final Map<UUID, Integer> visualFreezeTicks = new HashMap<>();
    private ScheduledTask task;

    public OutsideSnowEffectManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> tick(), 1L, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAllEffects();
    }

    private void tick() {
        if (!plugin.getGameManager().isGameRunning()) {
            clearAllEffects();
            return;
        }

        Set<UUID> seen = new HashSet<>();
        for (GamePlayer gp : plugin.getPlayerManager().getAllPlayers()) {
            UUID uuid = gp.uuid();
            seen.add(uuid);

            if (!gp.isAlive()) {
                clearPlayerEffect(uuid);
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
                clearPlayerEffect(uuid);
                continue;
            }

            boolean inside = plugin.getMapConfig().isInInsideArea(player.getLocation());
            int exposure = exposureTicks.getOrDefault(uuid, 0);
            if (inside) {
                exposure = Math.max(0, exposure - INSIDE_FADE_STEP_TICKS);
            } else {
                exposure = Math.min(OUTSIDE_FULL_TICKS, exposure + 1);
            }
            exposureTicks.put(uuid, exposure);

            int maxVisualTicks = Math.max(0, player.getMaxFreezeTicks() - 1);
            double progress = exposure / (double) OUTSIDE_FULL_TICKS;
            double visualScale = gp.isKiller() ? KILLER_FACTION_VISUAL_SCALE : DEFAULT_VISUAL_SCALE;
            int next = (int) Math.round(maxVisualTicks * progress * visualScale);
            int previousVisual = visualFreezeTicks.getOrDefault(uuid, -1);
            if (next != previousVisual) {
                sendVisualFreezeTicks(player, next);
                visualFreezeTicks.put(uuid, next);
            }

            // Keep server-side freeze mechanics disabled (no slowdown / no freezing behavior).
            if (player.getFreezeTicks() != 0) {
                player.setFreezeTicks(0);
            }

            if (exposure > 0) {
                affectedPlayers.add(uuid);
            } else {
                affectedPlayers.remove(uuid);
                exposureTicks.remove(uuid);
                visualFreezeTicks.remove(uuid);
            }
        }

        for (UUID uuid : new HashSet<>(affectedPlayers)) {
            if (!seen.contains(uuid)) {
                clearPlayerEffect(uuid);
            }
        }
    }

    private void clearPlayerEffect(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (player.getFreezeTicks() > 0) {
                player.setFreezeTicks(0);
            }
            sendVisualFreezeTicks(player, 0);
        }
        affectedPlayers.remove(uuid);
        exposureTicks.remove(uuid);
        visualFreezeTicks.remove(uuid);
    }

    public void clearAllEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getFreezeTicks() > 0) {
                player.setFreezeTicks(0);
            }
            sendVisualFreezeTicks(player, 0);
        }
        affectedPlayers.clear();
        exposureTicks.clear();
        visualFreezeTicks.clear();
    }

    private void sendVisualFreezeTicks(Player player, int ticks) {
        try {
            List<EntityData> metadata = new ArrayList<>(1);
            metadata.add(new EntityData(ENTITY_FROZEN_TICKS_INDEX, EntityDataTypes.INT, Math.max(0, ticks)));
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(player.getEntityId(), metadata);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable ignored) {
            // If packet api changes, skip visual update but keep gameplay safe.
        }
    }

}

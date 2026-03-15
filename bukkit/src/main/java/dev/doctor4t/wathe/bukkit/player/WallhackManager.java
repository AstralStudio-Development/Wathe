package dev.doctor4t.wathe.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.api.WatheRoles;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WallhackManager {

    private final WatheBukkit plugin;
    private final Set<UUID> activeWallhacks = new HashSet<>();
    private final Map<UUID, Set<Integer>> glowingEntities = new HashMap<>();
    private final Map<UUID, Integer> activeTicks = new HashMap<>();
    private ScheduledTask refreshTask;
    private static final int PULSE_SOUND_INTERVAL_TICKS = 200;
    private static final double PULSE_SOUND_RADIUS = 16.0;
    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte GLOWING_FLAG = 0x40;

    public WallhackManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void onFKeyPress(Player player) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null || !gp.isAlive() || gp.role() != WatheRoles.KILLER) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (activeWallhacks.contains(uuid)) {
            deactivateWallhack(uuid);
            return;
        }

        activeWallhacks.add(uuid);
        glowingEntities.put(uuid, new HashSet<>());
        activeTicks.put(uuid, 0);
        applyGlowingForKiller(player);

        if (refreshTask == null) {
            startRefreshTask();
        }
    }

    private void startRefreshTask() {
        refreshTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (activeWallhacks.isEmpty()) {
                task.cancel();
                refreshTask = null;
                return;
            }

            for (UUID killerUUID : new HashSet<>(activeWallhacks)) {
                Player killer = Bukkit.getPlayer(killerUUID);
                if (killer == null || !killer.isOnline()) {
                    deactivateWallhack(killerUUID);
                    continue;
                }
                updateGlowingTargets(killer);
                tickPulseSound(killer);
            }
        }, 1L, 1L);
    }

    private void tickPulseSound(Player killer) {
        UUID killerUUID = killer.getUniqueId();
        int ticks = activeTicks.getOrDefault(killerUUID, 0) + 1;
        activeTicks.put(killerUUID, ticks);
        sendPulseCountdownActionBar(killer, ticks);

        if (ticks % PULSE_SOUND_INTERVAL_TICKS != 0) {
            return;
        }

        double maxDistanceSq = PULSE_SOUND_RADIUS * PULSE_SOUND_RADIUS;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(killerUUID)) {
                continue;
            }
            if (!target.getWorld().equals(killer.getWorld())) {
                continue;
            }
            if (target.getLocation().distanceSquared(killer.getLocation()) > maxDistanceSq) {
                continue;
            }
            target.playSound(killer.getLocation(), Sound.ENTITY_BAT_HURT, 1.0f, 1.0f);
        }
    }

    private void sendPulseCountdownActionBar(Player killer, int ticks) {
        int mod = ticks % PULSE_SOUND_INTERVAL_TICKS;
        int remainingTicks = (mod == 0) ? PULSE_SOUND_INTERVAL_TICKS : (PULSE_SOUND_INTERVAL_TICKS - mod);
        int remainingSeconds = (remainingTicks + 19) / 20;
        killer.sendActionBar(Component.text("距离发出声音还剩 " + remainingSeconds + " 秒", NamedTextColor.RED));
    }

    private void deactivateWallhack(UUID killerUUID) {
        activeWallhacks.remove(killerUUID);
        activeTicks.remove(killerUUID);

        Player killer = Bukkit.getPlayer(killerUUID);
        if (killer != null && killer.isOnline()) {
            removeGlowingForKiller(killer);
            plugin.getGameManager().refreshHiddenNametagsForOnlinePlayers();
            killer.sendActionBar(Component.empty());
        }

        glowingEntities.remove(killerUUID);
        if (activeWallhacks.isEmpty() && refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void applyGlowingForKiller(Player killer) {
        UUID killerUUID = killer.getUniqueId();
        Set<Integer> tracked = glowingEntities.computeIfAbsent(killerUUID, key -> new HashSet<>());

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(killerUUID)) {
                continue;
            }
            if (!target.getWorld().equals(killer.getWorld())) {
                continue;
            }

            GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
            if (targetGP != null && targetGP.isAlive()) {
                sendGlowingPacket(killer, target, true);
                tracked.add(target.getEntityId());
            }
        }
    }

    private void updateGlowingTargets(Player killer) {
        UUID killerUUID = killer.getUniqueId();
        Set<Integer> tracked = glowingEntities.get(killerUUID);
        if (tracked == null) {
            return;
        }

        Set<Integer> currentAlive = new HashSet<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getUniqueId().equals(killerUUID)) {
                continue;
            }
            if (!target.getWorld().equals(killer.getWorld())) {
                continue;
            }

            GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
            if (targetGP != null && targetGP.isAlive()) {
                currentAlive.add(target.getEntityId());
                if (!tracked.contains(target.getEntityId())) {
                    sendGlowingPacket(killer, target, true);
                    tracked.add(target.getEntityId());
                }
            }
        }

        for (Integer entityId : new HashSet<>(tracked)) {
            if (!currentAlive.contains(entityId)) {
                Player target = getPlayerByEntityId(entityId);
                if (target != null && target.getWorld().equals(killer.getWorld())) {
                    sendGlowingPacket(killer, target, false);
                }
                tracked.remove(entityId);
            }
        }
    }

    private Player getPlayerByEntityId(int entityId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getEntityId() == entityId) {
                return player;
            }
        }
        return null;
    }

    private void sendGlowingPacket(Player viewer, Player target, boolean glowing) {
        byte currentFlags = 0;

        if (target.getFireTicks() > 0) {
            currentFlags |= 0x01;
        }
        if (target.isSneaking()) {
            currentFlags |= 0x02;
        }
        if (target.isSprinting()) {
            currentFlags |= 0x08;
        }
        if (target.isSwimming()) {
            currentFlags |= 0x10;
        }
        if (target.isInvisible()) {
            currentFlags |= 0x20;
        }
        if (target.isGlowing()) {
            currentFlags |= GLOWING_FLAG;
        }

        if (glowing) {
            currentFlags |= GLOWING_FLAG;
        } else {
            currentFlags &= ~GLOWING_FLAG;
        }

        List<EntityData> metadata = new ArrayList<>();
        metadata.add(new EntityData(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, currentFlags));
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(target.getEntityId(), metadata);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private void removeGlowingForKiller(Player killer) {
        if (!killer.isOnline()) {
            return;
        }

        UUID killerUUID = killer.getUniqueId();
        Set<Integer> tracked = glowingEntities.get(killerUUID);
        if (tracked == null) {
            return;
        }

        for (Integer entityId : tracked) {
            Player target = getPlayerByEntityId(entityId);
            if (target != null && target.getWorld().equals(killer.getWorld())) {
                sendGlowingPacket(killer, target, false);
            }
        }
    }

    public void stopAllWallhacks() {
        for (UUID uuid : new HashSet<>(activeWallhacks)) {
            Player killer = Bukkit.getPlayer(uuid);
            if (killer != null && killer.isOnline()) {
                removeGlowingForKiller(killer);
            }
        }

        activeWallhacks.clear();
        activeTicks.clear();
        glowingEntities.clear();

        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public boolean isUsingWallhack(Player player) {
        return activeWallhacks.contains(player.getUniqueId());
    }
}

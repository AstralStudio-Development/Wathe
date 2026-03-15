package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PoisonManager {

    private static final int MIN_POISON_TICKS = 800;
    private static final int MAX_POISON_TICKS = 1400;
    private static final int MIN_REDUCE_TICKS = 100;
    private static final int MAX_REDUCE_TICKS = 300;

    private final WatheBukkit plugin;
    private final Map<UUID, PoisonData> poisonedPlayers = new ConcurrentHashMap<>();
    private final ScheduledTask poisonTickTask;

    public record PoisonData(UUID poisoner, int ticksRemaining) {}

    public PoisonManager(WatheBukkit plugin) {
        this.plugin = plugin;
        this.poisonTickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> tickPoisons(),
                1L,
                1L
        );
    }

    public void poisonPlayer(Player target, Player poisoner) {
        poisonPlayer(target, poisoner == null ? null : poisoner.getUniqueId());
    }

    public void poisonPlayer(Player target, UUID poisoner) {
        if (target == null || !target.isOnline()) {
            return;
        }

        var gp = plugin.getPlayerManager().getPlayer(target);
        if (gp == null || !gp.isAlive()) {
            return;
        }

        UUID targetId = target.getUniqueId();
        PoisonData current = poisonedPlayers.get(targetId);

        if (current == null) {
            int ticks = ThreadLocalRandom.current().nextInt(MIN_POISON_TICKS, MAX_POISON_TICKS + 1);
            poisonedPlayers.put(targetId, new PoisonData(poisoner, ticks));
            return;
        }

        int reduce = ThreadLocalRandom.current().nextInt(MIN_REDUCE_TICKS, MAX_REDUCE_TICKS + 1);
        int nextTicks = Math.max(0, Math.min(MAX_POISON_TICKS, current.ticksRemaining() - reduce));

        if (nextTicks <= 0) {
            killPoisonedPlayer(target, poisoner == null ? current.poisoner() : poisoner);
            poisonedPlayers.remove(targetId);
            return;
        }

        poisonedPlayers.put(
                targetId,
                new PoisonData(poisoner == null ? current.poisoner() : poisoner, nextTicks)
        );
    }

    private void tickPoisons() {
        if (poisonedPlayers.isEmpty()) {
            return;
        }

        List<UUID> toRemove = new ArrayList<>();
        Map<UUID, PoisonData> toUpdate = new HashMap<>();

        for (Map.Entry<UUID, PoisonData> entry : poisonedPlayers.entrySet()) {
            UUID targetId = entry.getKey();
            PoisonData data = entry.getValue();

            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                toRemove.add(targetId);
                continue;
            }

            var gp = plugin.getPlayerManager().getPlayer(target);
            if (gp == null || !gp.isAlive()) {
                toRemove.add(targetId);
                continue;
            }

            int remain = data.ticksRemaining() - 1;
            if (remain <= 0) {
                killPoisonedPlayer(target, data.poisoner());
                toRemove.add(targetId);
                continue;
            }

            toUpdate.put(targetId, new PoisonData(data.poisoner(), remain));
        }

        for (Map.Entry<UUID, PoisonData> entry : toUpdate.entrySet()) {
            poisonedPlayers.put(entry.getKey(), entry.getValue());
        }
        for (UUID uuid : toRemove) {
            poisonedPlayers.remove(uuid);
        }
    }

    private void killPoisonedPlayer(Player target, UUID poisonerId) {
        Player killer = poisonerId == null ? null : Bukkit.getPlayer(poisonerId);
        plugin.getGameManager().onPlayerDeath(target, killer);
    }

    public boolean isPoisoned(Player player) {
        return player != null && poisonedPlayers.containsKey(player.getUniqueId());
    }

    public int getPoisonTicksRemaining(Player player) {
        if (player == null) {
            return -1;
        }
        PoisonData data = poisonedPlayers.get(player.getUniqueId());
        return data == null ? -1 : data.ticksRemaining();
    }

    public void clearPoison(Player player) {
        if (player == null) {
            return;
        }
        poisonedPlayers.remove(player.getUniqueId());
    }

    public void clearAllPoisons() {
        poisonedPlayers.clear();
    }

    public void shutdown() {
        clearAllPoisons();
        if (poisonTickTask != null) {
            poisonTickTask.cancel();
        }
    }
}

package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final WatheBukkit plugin;
    private final Map<UUID, GamePlayer> players = new ConcurrentHashMap<>();

    public PlayerManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public GamePlayer addPlayer(Player player) {
        var gamePlayer = new GamePlayer(player);
        players.put(player.getUniqueId(), gamePlayer);
        return gamePlayer;
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void removePlayer(Player player) {
        removePlayer(player.getUniqueId());
    }

    public GamePlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public GamePlayer getPlayer(Player player) {
        return getPlayer(player.getUniqueId());
    }

    public boolean hasPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    public boolean hasPlayer(Player player) {
        return hasPlayer(player.getUniqueId());
    }

    public Collection<GamePlayer> getAllPlayers() {
        return players.values();
    }

    public void clearAllPlayers() {
        players.clear();
    }

    public long getAliveCount() {
        return players.values().stream().filter(GamePlayer::isAlive).count();
    }

    public long getTotalCount() {
        return players.size();
    }

    public long getAliveInnocentCount() {
        return players.values().stream()
                .filter(GamePlayer::isAlive)
                .filter(p -> p.role() != null && p.role().innocent())
                .count();
    }

    public long getAliveKillerCount() {
        return players.values().stream()
                .filter(GamePlayer::isAlive)
                .filter(GamePlayer::isKiller)
                .count();
    }

    public long getKillerCount() {
        return players.values().stream()
                .filter(GamePlayer::isKiller)
                .count();
    }
}

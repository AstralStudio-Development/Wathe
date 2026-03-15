package dev.doctor4t.wathe.bukkit.game;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 黑屏过渡动画管理器
 * 使用Title和CE的BitmapImage实现全屏黑色渐变效果
 */
public class BlackoutTransitionManager {

    private final WatheBukkit plugin;
    private final Map<UUID, Scoreboard> savedScoreboards = new WeakHashMap<>();
    
    // 默认过渡时间（ticks）
    private static final int DEFAULT_FADEIN = 20;  // 1秒
    private static final int DEFAULT_STAY = 40;    // 2秒
    private static final int DEFAULT_FADEOUT = 20; // 1秒
    
    // CE全屏黑色图片ID
    private static final String FULLSCREEN_BLACK_IMAGE_ID = "wathe:fullscreen_black";

    public BlackoutTransitionManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * 为所有玩家开始黑屏过渡动画
     */
    public void startTransitionForAll(List<Player> players) {
        startTransitionForAll(players, DEFAULT_FADEIN, DEFAULT_STAY, DEFAULT_FADEOUT);
    }

    /**
     * 为所有玩家开始黑屏过渡动画（自定义时间）
     */
    public void startTransitionForAll(List<Player> players, int fadein, int stay, int fadeout) {
        for (Player player : players) {
            startTransition(player, fadein, stay, fadeout);
        }
    }

    /**
     * 为单个玩家开始黑屏过渡动画
     */
    public void startTransition(Player player) {
        startTransition(player, DEFAULT_FADEIN, DEFAULT_STAY, DEFAULT_FADEOUT);
    }

    /**
     * 为单个玩家开始黑屏过渡动画（自定义时间）
     */
    public void startTransition(Player player, int fadein, int stay, int fadeout) {
        // 获取全屏黑色图片的Component
        Component blackScreen = getFullscreenBlackComponent();
        if (blackScreen == null) {
            plugin.getLogger().warning("无法获取全屏黑色图片，跳过黑屏过渡");
            return;
        }

        // 隐藏HUD
        hideHUD(player);

        // 发送Title
        // 根据版本，title和subtitle的渲染顺序可能不同
        // 在大多数版本中，subtitle在title下方，所以我们把图片放在subtitle
        player.sendTitlePart(net.kyori.adventure.title.TitlePart.TIMES, 
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(fadein * 50L),
                java.time.Duration.ofMillis(stay * 50L),
                java.time.Duration.ofMillis(fadeout * 50L)
            ));
        player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, Component.empty());
        player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, blackScreen);

        // 在过渡结束后恢复HUD
        int totalDuration = fadein + stay + fadeout;
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                showHUD(player);
            }
        }, totalDuration);
    }

    /**
     * 获取全屏黑色图片的Component
     */
    private Component getFullscreenBlackComponent() {
        try {
            var fontManager = CraftEngine.instance().fontManager();
            var imageOpt = fontManager.imageById(Key.of(FULLSCREEN_BLACK_IMAGE_ID));
            
            if (imageOpt.isPresent()) {
                // 使用miniMessageAt获取MiniMessage格式字符串，然后用Bukkit的MiniMessage解析
                String miniMessageStr = imageOpt.get().miniMessageAt(0, 0);
                return MiniMessage.miniMessage().deserialize(miniMessageStr).color(NamedTextColor.BLACK);
            } else {
                plugin.getLogger().warning("找不到图片: " + FULLSCREEN_BLACK_IMAGE_ID);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取全屏黑色图片失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 隐藏玩家HUD
     * 通过发送假的旁观者模式数据包实现
     */
    public void hideHUD(Player player) {
        // 保存当前计分板
        savedScoreboards.put(player.getUniqueId(), player.getScoreboard());
        // 设置空计分板隐藏侧边栏
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        // 如果玩家已经是旁观者模式，不需要发送假数据包
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        try {
            // 发送假的旁观者模式数据包来隐藏HUD
            WrapperPlayServerChangeGameState packet = new WrapperPlayServerChangeGameState(
                WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE,
                3.0f // 3 = 旁观者模式
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
            
            // 刷新玩家能力，确保不会真的变成旁观者
            refreshAbilities(player);
        } catch (Exception e) {
            plugin.getLogger().warning("隐藏HUD失败: " + e.getMessage());
        }
    }

    /**
     * 恢复玩家HUD
     */
    public void showHUD(Player player) {
        // 恢复计分板
        Scoreboard scoreboard = savedScoreboards.get(player.getUniqueId());
        if (scoreboard != null) {
            player.setScoreboard(scoreboard);
        }

        // 如果玩家是旁观者模式，不需要恢复
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        try {
            // 发送真实的游戏模式数据包
            float gamemodeId = gamemodeToId(player.getGameMode());
            WrapperPlayServerChangeGameState packet = new WrapperPlayServerChangeGameState(
                WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE,
                gamemodeId
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
            
            // 延迟刷新能力
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    refreshAbilities(player);
                }
            }, 5L);
        } catch (Exception e) {
            plugin.getLogger().warning("恢复HUD失败: " + e.getMessage());
        }
    }

    /**
     * 刷新玩家能力
     */
    private void refreshAbilities(Player player) {
        // 通过设置飞行权限来触发能力更新
        player.setAllowFlight(player.getAllowFlight());
    }

    /**
     * 游戏模式转换为ID
     */
    private float gamemodeToId(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> 0f;
            case CREATIVE -> 1f;
            case ADVENTURE -> 2f;
            case SPECTATOR -> 3f;
        };
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        savedScoreboards.clear();
    }
}

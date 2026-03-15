package dev.doctor4t.wathe.bukkit.game;

import dev.doctor4t.wathe.bukkit.api.GameMode;
import dev.doctor4t.wathe.bukkit.api.Role;
import dev.doctor4t.wathe.bukkit.api.WatheRoles;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MurderGameMode extends GameMode {
    private static final long START_BLACKOUT_TOTAL_TICKS = 80L;

    public MurderGameMode() {
        super("murder", "谋杀模式",
                GameConstants.getDefaultGameTimeMinutes(),
                GameConstants.getMinPlayersMurder());
    }

    @Override
    public void initialize(GameManager gameManager, List<Player> players) {
        // 开始黑屏过渡动画
        gameManager.plugin().getBlackoutTransitionManager().startTransitionForAll(players);
        
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int killerCount = players.size() >= GameConstants.getKillerThreshold() ? 2 : 1;
        int vigilanteCount = players.size() >= GameConstants.getVigilanteThreshold() ? 2 : 1;

        int index = 0;

        for (int i = 0; i < killerCount && index < shuffled.size(); i++, index++) {
            Player player = shuffled.get(index);
            GamePlayer gp = gameManager.playerManager().getPlayer(player);
            gp.setRole(WatheRoles.KILLER);
            // 延迟到黑屏完全结束后再公告角色（80 ticks后）
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    gameManager.announceRole(player, WatheRoles.KILLER);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    giveKillerItems(player, gameManager);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            resetPlayerStatus(player, gameManager);
            // 杀手也需要初始化假任务系统
            gameManager.plugin().getMoodManager().initPlayer(gp);
        }

        for (int i = 0; i < vigilanteCount && index < shuffled.size(); i++, index++) {
            Player player = shuffled.get(index);
            GamePlayer gp = gameManager.playerManager().getPlayer(player);
            gp.setRole(WatheRoles.VIGILANTE);
            // 延迟到黑屏完全结束后再公告角色（80 ticks后）
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    gameManager.announceRole(player, WatheRoles.VIGILANTE);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    giveVigilanteItems(player);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            resetPlayerStatus(player, gameManager);
            // 初始化心情任务系统
            gameManager.plugin().getMoodManager().initPlayer(gp);
        }

        while (index < shuffled.size()) {
            Player player = shuffled.get(index);
            GamePlayer gp = gameManager.playerManager().getPlayer(player);
            gp.setRole(WatheRoles.CIVILIAN);
            // 延迟到黑屏完全结束后再公告角色（80 ticks后）
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    gameManager.announceRole(player, WatheRoles.CIVILIAN);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                if (player.isOnline()) {
                    giveCivilianItems(player);
                }
            }, START_BLACKOUT_TOTAL_TICKS);
            resetPlayerStatus(player, gameManager);
            // 初始化心情任务系统
            gameManager.plugin().getMoodManager().initPlayer(gp);
            index++;
        }
    }

    private void resetPlayerStatus(Player player, GameManager gameManager) {
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(10.0f);
        
        // 恢复原版默认攻击速度（4.0），避免近战攻速过快
        var attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(4.0);
        }
        
        // 禁止跳跃
        var jumpStrengthAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpStrengthAttr != null) {
            jumpStrengthAttr.setBaseValue(0.0);
        }
    }

    private void giveKillerItems(Player player, GameManager gameManager) {
        player.getInventory().clear();
        // 杀手背包中放置商店物品
        gameManager.plugin().getShopManager().fillInventoryWithShopItems(player);
    }

    private void giveVigilanteItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, GameItems.createGun());
    }

    private void giveCivilianItems(Player player) {
        player.getInventory().clear();
    }

    @Override
    public void tick(GameManager gameManager) {
        tickSprintStamina(gameManager);
        // 心情系统tick
        gameManager.plugin().getMoodManager().tick();
        // 幻觉系统tick
        gameManager.plugin().getPsychosisManager().tick();
        // 狂暴模式tick
        gameManager.plugin().getPsychoModeManager().tick();
    }

    private void tickSprintStamina(GameManager gameManager) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GamePlayer gp = gameManager.playerManager().getPlayer(player);
            if (gp == null || !gp.isAlive() || gp.role() == null) {
                continue;
            }

            int maxSprintTicks = gp.role().maxSprintTicks();
            // 负值表示无限体力（杀手）
            if (maxSprintTicks < 0) {
                continue;
            }

            float sprintTicks = gp.sprintTicks();
            if (player.isSprinting()) {
                sprintTicks = Math.max(sprintTicks - 1.0f, 0.0f);
            } else {
                sprintTicks = Math.min(sprintTicks + 0.25f, maxSprintTicks);
            }

            gp.setSprintTicks(sprintTicks);
            if (sprintTicks <= 0.0f) {
                player.setSprinting(false);
            }
        }
    }

    @Override
    public void onPlayerDeath(GameManager gameManager, Player victim, Player killer) {
        GamePlayer victimGP = gameManager.playerManager().getPlayer(victim);
        if (victimGP == null) return;

        Role victimRole = victimGP.role();
        if (victimRole == null) return;

        if (killer != null) {
            GamePlayer killerGP = gameManager.playerManager().getPlayer(killer);
            if (killerGP != null && killerGP.role() != WatheRoles.KILLER && victimRole.innocent()) {
                // 非杀手击杀无辜平民，击杀者也死亡
                // 延迟执行击杀者死亡，避免递归问题
                org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(gameManager.plugin(), task -> {
                    if (killerGP.isAlive()) {
                        gameManager.onPlayerDeath(killer, null);
                    }
                }, 1L);
            }
        }
    }

    @Override
    public void checkWinCondition(GameManager gameManager) {
        long aliveInnocent = gameManager.playerManager().getAliveInnocentCount();
        long aliveKiller = gameManager.playerManager().getAliveKillerCount();

        if (aliveKiller == 0) {
            gameManager.endGameWithWinner("innocent");
            return;
        }

        if (aliveInnocent == 0) {
            gameManager.endGameWithWinner("killer");
        }
    }
}

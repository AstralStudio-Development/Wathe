package dev.doctor4t.wathe.bukkit.listener;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.integration.BetterHudIntegration;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final WatheBukkit plugin;

    public PlayerListener(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getGameManager().isGameRunning()) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setCollidable(false);
            plugin.getGameManager().applyHiddenNametagForPlayer(player);
            player.sendMessage(Component.text("游戏正在进行中，你将以旁观者模式观战", NamedTextColor.YELLOW));

            // 延迟为新玩家显示已存在尸体
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    plugin.getCorpseManager().spawnAllCorpsesForPlayer(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 取消蓄力
        plugin.getItemHandler().cancelCharge(player);

        if (plugin.getPlayerManager().hasPlayer(player)) {
            GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
            if (gp != null && gp.isAlive()) {
                plugin.getGameManager().onPlayerDeath(player, null);
            }
            plugin.getPlayerManager().removePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 取消蓄力
        plugin.getItemHandler().cancelCharge(player);

        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        event.setCancelled(true);
        event.deathMessage(null);

        Player killer = player.getKiller();
        plugin.getGameManager().onPlayerDeath(player, killer);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }

        Player player = event.getPlayer();

        // 餐盘/饮品盘交互（取物、下毒）
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (plugin.getTrayManager().handleInteract(player, event.getClickedBlock(), event.getItem())) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setUseItemInHand(Event.Result.DENY);
                event.setCancelled(true);
                return;
            }
        }

        var item = event.getItem();
        if (item == null) {
            return;
        }

        String itemId = GameItems.getGameItemId(item);
        if (itemId == null) {
            return;
        }

        // 结算回放信件：右键开关结算界面
        if ("letter".equals(itemId)
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (plugin.getGameManager().isRoundEndVisible()) {
                BetterHudIntegration.toggleRoundEndPopup(player);
            }
            return;
        }

        // 右键使用游戏道具
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // 匕首不取消事件，保留原版蓄力动画，同时触发插件蓄力逻辑
            if ("knife".equals(itemId)) {
                plugin.getItemHandler().useItem(player, item);
                return;
            }

            event.setCancelled(true);
            plugin.getItemHandler().useItem(player, item);
        }
    }

    // 切换手持物品时取消蓄力
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getItemHandler().isCharging(player)) {
            plugin.getItemHandler().cancelCharge(player);
        }
    }

    // 切换副手时取消蓄力
    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.getItemHandler().isCharging(player)) {
            plugin.getItemHandler().cancelCharge(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 打开背包时取消蓄力
        if (plugin.getItemHandler().isCharging(player)) {
            plugin.getItemHandler().cancelCharge(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 检查是否有待激活的狂暴模式
        plugin.getPsychoModeManager().onInventoryClose(player);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 对局中禁用聊天
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        Player player = event.getPlayer();
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null || !gp.isAlive()) {
            return;
        }

        ItemStack item = event.getItem();
        Material type = item.getType();

        // 食物视为进食任务
        if (type.isEdible()) {
            plugin.getMoodManager().onPlayerEat(gp);
        }

        // 药水/蜂蜜/牛奶视为饮料任务
        if (type == Material.POTION || type == Material.HONEY_BOTTLE || type == Material.MILK_BUCKET) {
            plugin.getMoodManager().onPlayerDrink(gp);
        }

        // 托盘毒药：吃到带标记食物/饮品后进入中毒倒计时
        var poisoner = plugin.getTrayManager().getPoisonedBy(item);
        if (poisoner != null) {
            plugin.getPoisonManager().poisonPlayer(player, poisoner);
        }
    }
}

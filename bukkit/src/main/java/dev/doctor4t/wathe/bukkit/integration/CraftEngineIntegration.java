package dev.doctor4t.wathe.bukkit.integration;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CraftEngineIntegration {

    private static boolean enabled = false;

    public static void init() {
        enabled = Bukkit.getPluginManager().getPlugin("CraftEngine") != null;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取 CraftEngine 物品
     * @param id 物品ID，格式为 "namespace:item_id"
     * @param amount 数量
     * @return ItemStack，如果不存在则返回 null
     */
    public static ItemStack getItem(String id, int amount) {
        if (!enabled) {
            return null;
        }
        
        try {
            Key key = Key.of(id);
            var customItem = CraftEngineItems.byId(key);
            if (customItem != null) {
                ItemStack item = customItem.buildItemStack();
                item.setAmount(amount);
                return item;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取 CraftEngine 物品（数量为1）
     */
    public static ItemStack getItem(String id) {
        return getItem(id, 1);
    }

    /**
     * 给予玩家 CraftEngine 物品
     * @param player 玩家
     * @param id 物品ID
     * @param amount 数量
     * @return 是否成功
     */
    public static boolean giveItem(Player player, String id, int amount) {
        ItemStack item = getItem(id, amount);
        if (item != null) {
            player.getInventory().addItem(item);
            return true;
        }
        return false;
    }

    /**
     * 给予玩家 CraftEngine 物品到指定槽位
     */
    public static boolean giveItem(Player player, String id, int amount, int slot) {
        ItemStack item = getItem(id, amount);
        if (item != null) {
            player.getInventory().setItem(slot, item);
            return true;
        }
        return false;
    }
}

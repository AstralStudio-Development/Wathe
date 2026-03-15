package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ShopManager {

    private final WatheBukkit plugin;
    private static final Component SHOP_TITLE = Component.text("商店", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);

    public record ShopItem(String id, int price, boolean killerOnly) {
        public ItemStack createDisplay() {
            return switch (id) {
                case "knife" -> GameItems.createKnife();
                case "gun" -> GameItems.createGun();
                case "grenade" -> GameItems.createGrenade();
                case "poison" -> GameItems.createPoison();
                case "lockpick" -> GameItems.createLockpick();
                case "crowbar" -> GameItems.createCrowbar();
                case "body_bag" -> GameItems.createBodyBag();
                case "firecracker" -> GameItems.createFirecracker();
                case "note" -> GameItems.createNote();
                case "blackout" -> GameItems.createBlackout();
                case "psycho_mode" -> GameItems.createPsychoMode();
                default -> null;
            };
        }
    }

    private List<ShopItem> getShopItems() {
        return List.of(
                new ShopItem("knife", GameConstants.getPriceKnife(), true),
                new ShopItem("gun", GameConstants.getPriceGun(), false),
                new ShopItem("grenade", GameConstants.getPriceGrenade(), true),
                new ShopItem("poison", GameConstants.getPricePoison(), true),
                new ShopItem("lockpick", GameConstants.getPriceLockpick(), false),
                new ShopItem("body_bag", GameConstants.getPriceBodyBag(), true),
                new ShopItem("blackout", GameConstants.getPriceBlackout(), true),
                new ShopItem("psycho_mode", GameConstants.PRICE_PSYCHO_MODE, true),
                new ShopItem("firecracker", GameConstants.getPriceFirecracker(), false)
        );
    }

    public ShopManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public void openShop(Player player) {
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null || !gp.isAlive()) {
            player.sendMessage(Component.text("你无法使用商店", NamedTextColor.RED));
            return;
        }
        if (!gp.canUseKillerItems()) {
            player.sendMessage(Component.text("当前角色无法使用背包商店！", NamedTextColor.RED));
            return;
        }
        refreshInventoryShopItems(player);
    }

    public boolean handleClick(Player player, int slot, ItemStack clicked) {
        return false;
    }

    public boolean isShopInventory(Component title) {
        return false;
    }

    /**
     * 处理玩家在背包中点击商店物品的购买
     *
     * @return 是否购买成功
     */
    public boolean handleInventoryShopClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.isEmpty()) {
            return false;
        }

        String itemId = GameItems.getGameItemId(clicked);
        if (itemId == null) {
            return false;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null) {
            return false;
        }

        ShopItem shopItem = getShopItems().stream()
                .filter(si -> si.id().equals(itemId))
                .findFirst()
                .orElse(null);

        if (shopItem == null) {
            return false;
        }

        if ("knife".equals(itemId) && hasGameItem(player, "knife")) {
            playBuyFailSound(player);
            return false;
        }

        if (gp.money() < shopItem.price()) {
            playBuyFailSound(player);
            return false;
        }

        // 狂暴模式特殊处理：购买后立即生效，不给物品
        if ("psycho_mode".equals(itemId)) {
            if (plugin.getPsychoModeManager().isInPsychoMode(player.getUniqueId())) {
                playBuyFailSound(player);
                return false;
            }
            gp.removeMoney(shopItem.price());
            plugin.getPsychoModeManager().setPendingActivation(player.getUniqueId());
            player.closeInventory();
            playBuySuccessSound(player);
            return true;
        }

        gp.removeMoney(shopItem.price());
        ItemStack item = shopItem.createDisplay();
        if (item != null) {
            player.getInventory().addItem(item);
        }
        playBuySuccessSound(player);
        return true;
    }

    private boolean hasGameItem(Player player, String itemId) {
        // 仅检查可携带区：快捷栏(0-8)与副手，忽略商店展示区(9-35)
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (itemId.equals(GameItems.getGameItemId(stack))) {
                return true;
            }
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && !offhand.isEmpty() && itemId.equals(GameItems.getGameItemId(offhand));
    }

    private void playBuySuccessSound(Player player) {
        player.playSound(player.getLocation(), "wathe:ui.shop.buy", 1.0f, 1.0f);
    }

    private void playBuyFailSound(Player player) {
        player.playSound(player.getLocation(), "wathe:ui.shop.buy_fail", 1.0f, 1.0f);
    }

    /**
     * 获取杀手可用的商店物品列表
     */
    public List<ShopItem> getKillerShopItems() {
        return getShopItems();
    }

    /**
     * 将商店物品填充到玩家背包中（用于杀手）
     * 物品水平居中排列在背包第一行
     */
    public void fillInventoryWithShopItems(Player player) {
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null) {
            return;
        }

        List<ShopItem> items = getShopItems();
        int itemCount = items.size();
        int rowStart = 9;
        int rowSize = 9;
        int startSlot = rowStart + (rowSize - itemCount) / 2;

        int slot = startSlot;
        for (ShopItem shopItem : items) {
            ItemStack display = shopItem.createDisplay();
            if (display == null) {
                continue;
            }

            final ShopItem current = shopItem;
            display.editMeta(meta -> {
                var lore = meta.lore();
                if (lore == null) {
                    lore = new java.util.ArrayList<>();
                }
                lore.add(Component.empty());
                lore.add(Component.text("价格 " + current.price() + " 金币", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                if ("knife".equals(current.id()) && hasGameItem(player, "knife")) {
                    lore.add(Component.text("已拥有1把匕首", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                } else if (gp.money() >= current.price()) {
                    lore.add(Component.text("点击购买", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("金币不足", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
            });
            player.getInventory().setItem(slot, display);
            slot++;
        }
    }

    /**
     * 刷新玩家背包中的商店物品（更新价格显示）
     */
    public void refreshInventoryShopItems(Player player) {
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null) {
            return;
        }

        List<ShopItem> items = getShopItems();
        int itemCount = items.size();
        int rowStart = 9;
        int rowSize = 9;
        int startSlot = rowStart + (rowSize - itemCount) / 2;

        int slot = startSlot;
        for (ShopItem shopItem : items) {
            ItemStack display = shopItem.createDisplay();
            if (display == null) {
                continue;
            }

            final ShopItem current = shopItem;
            display.editMeta(meta -> {
                var lore = meta.lore();
                if (lore == null) {
                    lore = new java.util.ArrayList<>();
                }
                lore.add(Component.empty());
                lore.add(Component.text("价格 " + current.price() + " 金币", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
                if ("knife".equals(current.id()) && hasGameItem(player, "knife")) {
                    lore.add(Component.text("已拥有1把匕首", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                } else if (gp.money() >= current.price()) {
                    lore.add(Component.text("点击购买", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("金币不足", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
            });
            player.getInventory().setItem(slot, display);
            slot++;
        }
    }
}

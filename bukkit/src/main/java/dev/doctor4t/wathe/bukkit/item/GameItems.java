package dev.doctor4t.wathe.bukkit.item;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.integration.CraftEngineIntegration;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class GameItems {

    private GameItems() {}

    private static NamespacedKey gameItemKey;

    public static final Material KNIFE_MATERIAL = Material.IRON_SWORD;
    public static final Material GUN_MATERIAL = Material.CROSSBOW;
    public static final Material GRENADE_MATERIAL = Material.FIRE_CHARGE;
    public static final Material LOCKPICK_MATERIAL = Material.TRIPWIRE_HOOK;
    public static final Material CROWBAR_MATERIAL = Material.IRON_HOE;
    public static final Material BODY_BAG_MATERIAL = Material.BLACK_WOOL;
    public static final Material POISON_MATERIAL = Material.POTION;
    public static final Material FIRECRACKER_MATERIAL = Material.FIREWORK_ROCKET;
    public static final Material NOTE_MATERIAL = Material.PAPER;
    public static final Material BLACKOUT_MATERIAL = Material.COAL;
    public static final Material BAT_MATERIAL = Material.WOODEN_HOE;

    public static NamespacedKey getGameItemKey() {
        if (gameItemKey == null) {
            gameItemKey = new NamespacedKey(WatheBukkit.getInstance(), "game_item");
        }
        return gameItemKey;
    }

    private static Component whiteItemName(String name) {
        return Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private static void setUnstackable(ItemStack item) {
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
    }

    public static ItemStack createKnife() {
        // 优先使用 CraftEngine 的自定义刀
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:knife");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("匕首"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("右键蓄力一秒，靠近目标即可发动暗杀", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("击杀后冷却 1 分钟", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("攻击可击退玩家（无冷却）", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.itemAttributes().showInTooltip(false));
                ceItem.setData(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP);
                // 移除耐久度
                ceItem.unsetData(DataComponentTypes.MAX_DAMAGE);
                ceItem.unsetData(DataComponentTypes.DAMAGE);
                ceItem.editMeta(meta -> {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
                    meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "knife");
                });
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createGun() {
        // 优先使用 CraftEngine 的自定义枪
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:revolver");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("左轮手枪"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("瞄准、右键点击并射击", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("若误杀无辜者则会掉落", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> {
                    meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "gun");
                });
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createGrenade() {
        // 优先使用 CraftEngine 的自定义手榴弹
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:grenade");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("手榴弹"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("右键投掷，撞击即爆", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("适合清理人群，但要小心爆炸范围！", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("一次性物品，冷却 5 分钟", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "grenade"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createLockpick() {
        // 优先使用 CraftEngine 的自定义撬锁器
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:lockpick");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("撬锁器"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("用于任意上锁的门将其打开（无冷却）", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("潜行使用可将门堵死 1 分钟（冷却 3 分钟）", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "lockpick"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createCrowbar() {
        // 优先使用 CraftEngine 的自定义撬棍
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:crowbar");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("撬棍"));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "crowbar"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createBodyBag() {
        // 优先使用 CraftEngine 的自定义尸袋
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:body_bag");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("裹尸袋"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("用于尸体，可将其打包移除", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("一次性物品，冷却 5 分钟", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "body_bag"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createPoison() {
        // 优先使用 CraftEngine 的自定义毒药瓶
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:poison_vial");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("毒药"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("偷偷混入食物或饮品中，使下一个取餐者中毒而亡", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "poison"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createFirecracker() {
        // 优先使用 CraftEngine 的自定义鞭炮
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:firecracker");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("爆竹"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("放置 15 秒后爆炸", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("可模拟枪声吸引他人", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "firecracker"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createNote() {
        // 优先使用 CraftEngine 的自定义笔记
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:note");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("便条"));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "note"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createLetter() {
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:letter");
            if (ceItem != null) {
                setUnstackable(ceItem);
                Component name = Component.text("战局回放 ", NamedTextColor.WHITE)
                        .append(Component.text("右键使用", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false);
                ceItem.setData(DataComponentTypes.ITEM_NAME, name);
                ceItem.editMeta(meta ->
                        meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "letter"));
                return ceItem;
            }
        }

        return null;
    }

    public static ItemStack createBlackout() {
        // 优先使用 CraftEngine 的自定义停电装置
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:blackout");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("停电装置"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("关闭全车所有灯光 15 至 20 秒", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("使用直觉（F键）可在黑暗中看见目标", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("购买后立即生效，冷却 5 分钟", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "blackout"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createPsychoMode() {
        // 优先使用 CraftEngine 的自定义狂暴模式
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:psycho_mode");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("狂暴模式"));
                ceItem.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                        Component.text("\"你喜欢伤害别人吗？\"", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("隐藏你的身份，让你在 30 秒内举着球棒肆意妄为", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("球棒全力挥击可直接击杀，持续期间无法切换其他物品", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("购买后立即启用，冷却时间 5 分钟", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                )));
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "psycho_mode"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static ItemStack createBat() {
        // 优先使用 CraftEngine 的自定义球棒
        if (CraftEngineIntegration.isEnabled()) {
            var ceItem = CraftEngineIntegration.getItem("wathe:bat");
            if (ceItem != null) {
                setUnstackable(ceItem);
                ceItem.setData(DataComponentTypes.ITEM_NAME, whiteItemName("球棒"));
                ceItem.unsetData(DataComponentTypes.MAX_DAMAGE);
                ceItem.unsetData(DataComponentTypes.DAMAGE);
                ceItem.editMeta(meta -> meta.getPersistentDataContainer().set(getGameItemKey(), PersistentDataType.STRING, "bat"));
                return ceItem;
            }
        }
        
        // 回退到原版物品
        return null;
    }

    public static String getGameItemId(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        var pdc = meta.getPersistentDataContainer();
        if (!pdc.has(getGameItemKey(), PersistentDataType.STRING)) {
            return null;
        }
        return pdc.get(getGameItemKey(), PersistentDataType.STRING);
    }

    public static boolean isGameItem(ItemStack item) {
        return getGameItemId(item) != null;
    }
}

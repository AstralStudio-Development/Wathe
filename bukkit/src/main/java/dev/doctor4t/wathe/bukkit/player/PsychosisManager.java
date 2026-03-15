package dev.doctor4t.wathe.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 精神病幻觉系统
 * 当玩家心情低于中等阈值时：
 * - 会看到其他玩家手持杀手商店的物品（刀、手榴弹、毒药、尸袋）
 * - 1秒后变回空手，让玩家以为对方是杀手
 * - 会看到其他玩家的皮肤随机变化
 */
public class PsychosisManager {

    private final WatheBukkit plugin;
    
    // 幻觉物品池 - 只包含杀手能购买的物品ID
    private static final String[] PSYCHOSIS_ITEM_IDS = {
        "knife",
        "grenade", 
        "poison",
        "body_bag"
    };
    
    // 每个观察者看到的每个玩家的幻觉状态
    // Map<观察者UUID, Map<目标玩家UUID, 剩余显示ticks>>
    private final Map<UUID, Map<UUID, Integer>> psychosisTimers = new ConcurrentHashMap<>();
    
    // 每个观察者看到的每个玩家的幻觉皮肤索引
    private final Map<UUID, Map<UUID, Integer>> psychosisSkins = new ConcurrentHashMap<>();
    
    // 缓存在线玩家的真实皮肤数据
    private final Map<UUID, TextureProperty> realSkins = new ConcurrentHashMap<>();
    
    // 幻觉物品显示时间（ticks）- 1秒
    private static final int ITEM_DISPLAY_DURATION = 20;
    
    // 幻觉触发间隔（ticks）- 随机触发
    private static final int ITEM_TRIGGER_INTERVAL = 100; // 5秒检查一次
    
    // 皮肤变化间隔
    private static final int SKIN_REROLL_INTERVAL = 100;
    
    // 幻觉出现的概率
    private static final float PSYCHOSIS_CHANCE = 0.5f;

    public PsychosisManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * 每tick调用，更新幻觉效果
     */
    public void tick() {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        
        for (GamePlayer observer : plugin.getPlayerManager().getAllPlayers()) {
            if (!observer.isAlive()) continue;
            
            Player observerPlayer = Bukkit.getPlayer(observer.uuid());
            if (observerPlayer == null) continue;
            
            // 只有心情低于中等阈值的玩家才会产生幻觉
            if (observer.mood() >= GameConstants.getMidMoodThreshold()) {
                // 心情恢复后清除幻觉
                clearPsychosisFor(observer.uuid());
                continue;
            }
            
            // 为这个观察者更新幻觉
            updatePsychosisFor(observerPlayer, observer);
        }
    }
    
    private void updatePsychosisFor(Player observer, GamePlayer observerGP) {
        Map<UUID, Integer> observerTimers = psychosisTimers.computeIfAbsent(
            observer.getUniqueId(), k -> new ConcurrentHashMap<>()
        );
        Map<UUID, Integer> observerSkinPsychosis = psychosisSkins.computeIfAbsent(
            observer.getUniqueId(), k -> new ConcurrentHashMap<>()
        );
        
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(observer)) continue;
            
            GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
            if (targetGP == null || !targetGP.isAlive()) continue;
            
            UUID targetUUID = target.getUniqueId();
            
            // 处理物品幻觉计时器
            Integer timer = observerTimers.get(targetUUID);
            if (timer != null && timer > 0) {
                // 正在显示幻觉物品，倒计时
                observerTimers.put(targetUUID, timer - 1);
                if (timer - 1 <= 0) {
                    // 时间到，变回空手
                    sendEmptyHand(observer, target);
                    observerTimers.remove(targetUUID);
                }
            } else {
                // 随机触发新的幻觉
                if (ThreadLocalRandom.current().nextInt(ITEM_TRIGGER_INTERVAL) == 0) {
                    if (ThreadLocalRandom.current().nextFloat() < PSYCHOSIS_CHANCE) {
                        // 显示杀手物品
                        String itemId = PSYCHOSIS_ITEM_IDS[ThreadLocalRandom.current().nextInt(PSYCHOSIS_ITEM_IDS.length)];
                        ItemStack psychosisItem = createPsychosisItem(itemId);
                        if (psychosisItem != null) {
                            sendFakeEquipment(observer, target, psychosisItem);
                            observerTimers.put(targetUUID, ITEM_DISPLAY_DURATION);
                        }
                    }
                }
            }
            
            // 皮肤幻觉 - 随机切换到其他在线玩家的皮肤
            if (ThreadLocalRandom.current().nextInt(SKIN_REROLL_INTERVAL) == 0) {
                List<Player> otherPlayers = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(target) && !p.equals(observer)) {
                        otherPlayers.add(p);
                    }
                }
                
                if (!otherPlayers.isEmpty()) {
                    Player skinSource = otherPlayers.get(ThreadLocalRandom.current().nextInt(otherPlayers.size()));
                    sendFakeSkin(observer, target, skinSource);
                    observerSkinPsychosis.put(targetUUID, 1);
                }
            }
        }
    }
    
    /**
     * 根据物品ID创建幻觉物品
     */
    private ItemStack createPsychosisItem(String itemId) {
        return switch (itemId) {
            case "knife" -> GameItems.createKnife();
            case "grenade" -> GameItems.createGrenade();
            case "poison" -> GameItems.createPoison();
            case "body_bag" -> GameItems.createBodyBag();
            default -> null;
        };
    }
    
    private void sendFakeEquipment(Player observer, Player target, ItemStack fakeItem) {
        try {
            List<Equipment> equipmentList = new ArrayList<>();
            equipmentList.add(new Equipment(
                EquipmentSlot.MAIN_HAND,
                SpigotConversionUtil.fromBukkitItemStack(fakeItem)
            ));
            
            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(
                target.getEntityId(),
                equipmentList
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, packet);
        } catch (Exception e) {
            // PacketEvents可能未加载
        }
    }
    
    /**
     * 发送空手显示
     */
    private void sendEmptyHand(Player observer, Player target) {
        try {
            List<Equipment> equipmentList = new ArrayList<>();
            equipmentList.add(new Equipment(
                EquipmentSlot.MAIN_HAND,
                SpigotConversionUtil.fromBukkitItemStack(target.getInventory().getItemInMainHand())
            ));
            
            WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(
                target.getEntityId(),
                equipmentList
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, packet);
        } catch (Exception e) {
            // PacketEvents可能未加载
        }
    }
    
    /**
     * 发送假皮肤包，让观察者看到目标玩家使用另一个玩家的皮肤
     */
    private void sendFakeSkin(Player observer, Player target, Player skinSource) {
        try {
            var profile = skinSource.getPlayerProfile();
            var textures = profile.getProperties();
            
            TextureProperty skinTexture = null;
            for (var prop : textures) {
                if ("textures".equals(prop.getName())) {
                    skinTexture = new TextureProperty(prop.getName(), prop.getValue(), prop.getSignature());
                    break;
                }
            }
            
            if (skinTexture == null) return;
            
            UserProfile fakeProfile = new UserProfile(
                target.getUniqueId(),
                target.getName(),
                Collections.singletonList(skinTexture)
            );
            
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                fakeProfile,
                true,
                target.getPing(),
                GameMode.SURVIVAL,
                Component.text(target.getName()),
                null
            );
            
            WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(
                Collections.singletonList(target.getUniqueId())
            );
            
            WrapperPlayServerPlayerInfoUpdate addPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER),
                playerInfo
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removePacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addPacket);
            
        } catch (Exception e) {
            // PacketEvents可能未加载或API变化
        }
    }
    
    /**
     * 恢复目标玩家的真实皮肤给观察者
     */
    private void restoreRealSkin(Player observer, Player target) {
        try {
            var profile = target.getPlayerProfile();
            var textures = profile.getProperties();
            
            TextureProperty skinTexture = null;
            for (var prop : textures) {
                if ("textures".equals(prop.getName())) {
                    skinTexture = new TextureProperty(prop.getName(), prop.getValue(), prop.getSignature());
                    break;
                }
            }
            
            UserProfile realProfile = new UserProfile(
                target.getUniqueId(),
                target.getName(),
                skinTexture != null ? Collections.singletonList(skinTexture) : Collections.emptyList()
            );
            
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                realProfile,
                true,
                target.getPing(),
                GameMode.SURVIVAL,
                Component.text(target.getName()),
                null
            );
            
            WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(
                Collections.singletonList(target.getUniqueId())
            );
            
            WrapperPlayServerPlayerInfoUpdate addPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER),
                playerInfo
            );
            
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removePacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addPacket);
            
        } catch (Exception e) {
            // 忽略
        }
    }
    
    /**
     * 清除指定观察者的所有幻觉
     */
    public void clearPsychosisFor(UUID observerUUID) {
        Map<UUID, Integer> removedTimers = psychosisTimers.remove(observerUUID);
        Map<UUID, Integer> removedSkins = psychosisSkins.remove(observerUUID);
        
        Player observer = Bukkit.getPlayer(observerUUID);
        if (observer == null) return;
        
        // 恢复真实装备显示
        if (removedTimers != null) {
            for (UUID targetUUID : removedTimers.keySet()) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null) {
                    sendEmptyHand(observer, target);
                }
            }
        }
        
        // 恢复真实皮肤
        if (removedSkins != null) {
            for (UUID targetUUID : removedSkins.keySet()) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null) {
                    restoreRealSkin(observer, target);
                }
            }
        }
    }
    
    /**
     * 清除所有幻觉
     */
    public void clearAll() {
        for (UUID observerUUID : new HashSet<>(psychosisTimers.keySet())) {
            clearPsychosisFor(observerUUID);
        }
        psychosisTimers.clear();
        psychosisSkins.clear();
        realSkins.clear();
    }
    
    /**
     * 当玩家加入时，为低心情玩家发送幻觉
     */
    public void onPlayerJoin(Player player) {
        // 新玩家加入时不立即触发幻觉，等待正常tick处理
    }
    
    /**
     * 当玩家离开时，清理相关数据
     */
    public void onPlayerQuit(Player player) {
        psychosisTimers.remove(player.getUniqueId());
        psychosisSkins.remove(player.getUniqueId());
        realSkins.remove(player.getUniqueId());
        
        for (Map<UUID, Integer> observerTimers : psychosisTimers.values()) {
            observerTimers.remove(player.getUniqueId());
        }
        for (Map<UUID, Integer> observerSkins : psychosisSkins.values()) {
            observerSkins.remove(player.getUniqueId());
        }
    }
}

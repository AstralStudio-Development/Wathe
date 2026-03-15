package dev.doctor4t.wathe.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

/**
 * 狂暴模式管理器
 * 管理玩家的狂暴状态、球棒武器、护甲值和皮肤切换
 */
public class PsychoModeManager {
    private static final String DEFAULT_STEVE_TEXTURE_URL =
            "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";
    private static final TextureProperty DEFAULT_STEVE_TEXTURE = createUnsignedTexture(DEFAULT_STEVE_TEXTURE_URL);

    private final WatheBukkit plugin;
    
    // 玩家狂暴模式剩余时间（ticks）
    private final Map<UUID, Integer> psychoTicks = new ConcurrentHashMap<>();
    
    // 玩家护甲值
    private final Map<UUID, Integer> armourValues = new ConcurrentHashMap<>();
    
    // 玩家球棒所在槽位
    private final Map<UUID, Integer> batSlots = new ConcurrentHashMap<>();
    
    // 玩家原始皮肤数据（用于恢复）
    private final Map<UUID, TextureProperty> originalSkins = new ConcurrentHashMap<>();
    
    // 待激活狂暴模式的玩家（购买后关闭背包时激活）
    private final Set<UUID> pendingActivation = new HashSet<>();
    
    // Psycho 皮肤纹理数据（需要在配置中设置）
    private String psychoSkinValue = "";
    private String psychoSkinSignature = "";
    private String psychoThinSkinValue = "";
    private String psychoThinSkinSignature = "";

    public PsychoModeManager(WatheBukkit plugin) {
        this.plugin = plugin;
        loadSkinConfig();
    }
    
    /**
     * 从配置文件加载皮肤数据
     */
    private void loadSkinConfig() {
        var config = plugin.getConfig();
        psychoSkinValue = config.getString("psycho-skin.normal.value", "");
        psychoSkinSignature = config.getString("psycho-skin.normal.signature", "");
        psychoThinSkinValue = config.getString("psycho-skin.thin.value", "");
        psychoThinSkinSignature = config.getString("psycho-skin.thin.signature", "");
        
        // 调试日志
        if (psychoSkinValue.isEmpty()) {
            plugin.getComponentLogger().warn("Psycho skin not configured! Please set psycho-skin values in config.yml");
        } else {
            plugin.getComponentLogger().info("Psycho skin loaded successfully (value length: " + psychoSkinValue.length() + ")");
        }
    }
    
    /**
     * 设置玩家待激活狂暴模式
     */
    public void setPendingActivation(UUID uuid) {
        pendingActivation.add(uuid);
    }
    
    /**
     * 当玩家关闭背包时调用，检查是否需要激活狂暴模式
     */
    public void onInventoryClose(Player player) {
        if (pendingActivation.remove(player.getUniqueId())) {
            startPsychoMode(player);
        }
    }

    /**
     * 每 Tick 调用，更新狂暴模式状态
     */
    public void tick() {
        for (var entry : psychoTicks.entrySet()) {
            UUID uuid = entry.getKey();
            int ticks = entry.getValue();
            
            if (ticks <= 0) {
                stopPsychoMode(uuid);
                continue;
            }
            
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                stopPsychoMode(uuid);
                continue;
            }
            
            // 强制切换到球棒槽位
            Integer batSlot = batSlots.get(uuid);
            if (batSlot != null && player.getInventory().getHeldItemSlot() != batSlot) {
                player.getInventory().setHeldItemSlot(batSlot);
            }
            
            psychoTicks.put(uuid, ticks - 1);
        }
    }

    /**
     * 激活狂暴模式
     */
    public boolean startPsychoMode(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (isInPsychoMode(uuid)) {
            return false;
        }
        
        // 找一个空槽位放球棒
        int slot = findEmptySlot(player);
        if (slot == -1) {
            return false;
        }
        
        // 保存原始皮肤
        saveOriginalSkin(player);
        
        // 给予球棒
        ItemStack bat = GameItems.createBat();
        player.getInventory().setItem(slot, bat);
        
        // 切换到球棒槽位
        player.getInventory().setHeldItemSlot(slot);
        
        // 设置状态
        psychoTicks.put(uuid, GameConstants.PSYCHO_MODE_DURATION);
        armourValues.put(uuid, GameConstants.PSYCHO_MODE_ARMOUR);
        batSlots.put(uuid, slot);
        
        // 切换到 Psycho 皮肤
        applyPsychoSkin(player);
        
        // 播放全场可听见的狂暴音效
        playPsychoDroneForAll(player);
    
        
        return true;
    }
    
    /**
     * 播放全场可听见的狂暴音效
     */
    private void playPsychoDroneForAll(Player psychoPlayer) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(psychoPlayer.getLocation(), "wathe:ambient.psycho_drone", 10.0f, 1.0f);
        }
    }

    /**
     * 停止狂暴模式
     */
    public void stopPsychoMode(UUID uuid) {
        if (!psychoTicks.containsKey(uuid) && !originalSkins.containsKey(uuid)) {
            return;
        }
        
        psychoTicks.remove(uuid);
        armourValues.remove(uuid);
        Integer batSlot = batSlots.remove(uuid);
        
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            // 移除球棒
            removeBatFromInventory(player);
            // 恢复原始皮肤（异步，内部会延迟执行）
            restoreOriginalSkin(player);
            // 停止音效
            stopPsychoDroneForAll(player);
        } else {
            // 玩家不在线，直接清理
            originalSkins.remove(uuid);
        }
    }
    
    /**
     * 停止全场的狂暴音效
     */
    private void stopPsychoDroneForAll(Player psychoPlayer) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.stopSound("wathe:ambient.psycho_drone");
        }
    }
    
    /**
     * 保存玩家原始皮肤
     */
    private void saveOriginalSkin(Player player) {
        try {
            TextureProperty texture = resolveCurrentTexture(player);
            if (texture != null) {
                originalSkins.put(player.getUniqueId(), texture);
                plugin.getComponentLogger().info("Saved original skin for " + player.getName() + " (value length: " + texture.getValue().length() + ")");
                return;
            }
            plugin.getComponentLogger().warn("No textures property found for " + player.getName());
        } catch (Exception e) {
            plugin.getComponentLogger().warn("Failed to save original skin for " + player.getName() + ": " + e.getMessage());
        }
    }
    /**
     * 应用 Psycho 皮肤给所有玩家看（包括自己）
     */
    private void applyPsychoSkin(Player player) {
        // 检查是否配置了皮肤数据
        if (psychoSkinValue.isEmpty()) {
            plugin.getComponentLogger().warn("Cannot apply psycho skin: skin value is empty");
            return;
        }
        
        try {
            // 判断玩家是否使用 slim 模型
            boolean isSlim = isSlimModel(player);
            String skinValue = isSlim ? psychoThinSkinValue : psychoSkinValue;
            String skinSignature = isSlim ? psychoThinSkinSignature : psychoSkinSignature;
            
            if (skinValue.isEmpty()) {
                skinValue = psychoSkinValue;
                skinSignature = psychoSkinSignature;
            }
            
            plugin.getComponentLogger().info("Applying psycho skin to " + player.getName() + " (slim: " + isSlim + ")");
            
            final String finalSkinValue = skinValue;
            final String finalSkinSignature = skinSignature;
            
            // 第一步：销毁实体并移除 PlayerInfo（对所有观察者，包括自己）
            for (Player observer : Bukkit.getOnlinePlayers()) {
                if (observer.equals(player)) {
                    // 对自己：只移除 PlayerInfo，不销毁实体
                    WrapperPlayServerPlayerInfoRemove removeInfoPacket = new WrapperPlayServerPlayerInfoRemove(player.getUniqueId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removeInfoPacket);
                } else {
                    // 对其他玩家：销毁实体并移除 PlayerInfo
                    WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(player.getEntityId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, destroyPacket);
                    
                    WrapperPlayServerPlayerInfoRemove removeInfoPacket = new WrapperPlayServerPlayerInfoRemove(player.getUniqueId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removeInfoPacket);
                }
            }
            
            // 第二步：延迟 2 tick 后添加新皮肤的 PlayerInfo 并重生实体
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (!player.isOnline()) return;
                
                TextureProperty psychoTexture = new TextureProperty("textures", finalSkinValue, finalSkinSignature);
                
                UserProfile fakeProfile = new UserProfile(
                    player.getUniqueId(),
                    player.getName(),
                    Collections.singletonList(psychoTexture)
                );
                
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    fakeProfile,
                    true,
                    player.getPing(),
                    GameMode.SURVIVAL,
                    net.kyori.adventure.text.Component.text(player.getName()),
                    null
                );
                
                Location loc = player.getLocation();
                
                // 准备装备数据
                java.util.List<Equipment> equipmentList = new java.util.ArrayList<>();
                equipmentList.add(new Equipment(
                    EquipmentSlot.MAIN_HAND,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getItemInMainHand())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.OFF_HAND,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getItemInOffHand())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.HELMET,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getHelmet())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.CHEST_PLATE,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getChestplate())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.LEGGINGS,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getLeggings())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.BOOTS,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getBoots())
                ));
                
                for (Player observer : Bukkit.getOnlinePlayers()) {
                    if (!observer.isOnline()) continue;
                    
                    if (observer.equals(player)) {
                        // 对自己：只发送 PlayerInfo 更新
                        WrapperPlayServerPlayerInfoUpdate addInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                      WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            playerInfo
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addInfoPacket);
                    } else {
                        // 对其他玩家：使用 Bundle 发送完整数据
                        WrapperPlayServerBundle bundleStart = new WrapperPlayServerBundle();
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, bundleStart);
                        
                        WrapperPlayServerPlayerInfoUpdate addInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                      WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            playerInfo
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addInfoPacket);
                        
                        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            player.getEntityId(),
                            java.util.Optional.of(player.getUniqueId()),
                            EntityTypes.PLAYER,
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            loc.getPitch(),
                            loc.getYaw(),
                            loc.getYaw(),
                            0,
                            java.util.Optional.empty()
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
                        
                        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                            player.getEntityId(),
                            loc.getYaw()
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, headLookPacket);
                        
                        byte skinParts = 0x7F;
                        java.util.List<EntityData> metadata = new java.util.ArrayList<>();
                        metadata.add(new EntityData(17, EntityDataTypes.BYTE, skinParts));
                        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                            player.getEntityId(),
                            metadata
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, metadataPacket);
                        
                        // 发送装备数据
                        WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(
                            player.getEntityId(),
                            equipmentList
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, equipmentPacket);
                        
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, bundleStart);
                    }
                }
            }, 2L);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 恢复玩家原始皮肤（包括自己）
     */
    private void restoreOriginalSkin(Player player) {
        // 先获取并移除原始皮肤数据，避免被其他流程清理
        TextureProperty originalTexture = originalSkins.remove(player.getUniqueId());
        if (originalTexture == null) {
            originalTexture = resolveCurrentTexture(player);
            if (originalTexture == null) {
                plugin.getComponentLogger().warn("Cannot restore skin: original texture not found for " + player.getName());
                return;
            }
        }
        
        plugin.getComponentLogger().info("Restoring original skin for " + player.getName());
        
        try {
            // 第一步：销毁实体并移除 PlayerInfo（对所有观察者，包括自己）
            for (Player observer : Bukkit.getOnlinePlayers()) {
                if (observer.equals(player)) {
                    // 对自己：只移除 PlayerInfo
                    WrapperPlayServerPlayerInfoRemove removeInfoPacket = new WrapperPlayServerPlayerInfoRemove(player.getUniqueId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removeInfoPacket);
                } else {
                    // 对其他玩家：销毁实体并移除 PlayerInfo
                    WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(player.getEntityId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, destroyPacket);
                    
                    WrapperPlayServerPlayerInfoRemove removeInfoPacket = new WrapperPlayServerPlayerInfoRemove(player.getUniqueId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, removeInfoPacket);
                }
            }
            
            // 第二步：延迟 2 tick 后发送原始皮肤的 PlayerInfo 并重生实体
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (!player.isOnline()) return;
                
                TextureProperty restoredTexture = resolveCurrentTexture(player);
                if (restoredTexture == null) {
                    return;
                }
                UserProfile realProfile = new UserProfile(
                    player.getUniqueId(),
                    player.getName(),
                    Collections.singletonList(restoredTexture)
                );
                
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    realProfile,
                    true,
                    player.getPing(),
                    GameMode.SURVIVAL,
                    net.kyori.adventure.text.Component.text(player.getName()),
                    null
                );
                
                Location loc = player.getLocation();
                
                // 准备装备数据
                java.util.List<Equipment> equipmentList = new java.util.ArrayList<>();
                equipmentList.add(new Equipment(
                    EquipmentSlot.MAIN_HAND,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getItemInMainHand())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.OFF_HAND,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getItemInOffHand())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.HELMET,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getHelmet())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.CHEST_PLATE,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getChestplate())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.LEGGINGS,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getLeggings())
                ));
                equipmentList.add(new Equipment(
                    EquipmentSlot.BOOTS,
                    SpigotConversionUtil.fromBukkitItemStack(player.getInventory().getBoots())
                ));
                
                for (Player observer : Bukkit.getOnlinePlayers()) {
                    if (!observer.isOnline()) continue;
                    
                    if (observer.equals(player)) {
                        // 对自己：只发送 PlayerInfo 更新
                        WrapperPlayServerPlayerInfoUpdate addInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                      WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            playerInfo
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addInfoPacket);
                    } else {
                        // 对其他玩家：使用 Bundle 发送完整数据
                        WrapperPlayServerBundle bundleStart = new WrapperPlayServerBundle();
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, bundleStart);
                        
                        WrapperPlayServerPlayerInfoUpdate addInfoPacket = new WrapperPlayServerPlayerInfoUpdate(
                            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                                      WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                            playerInfo
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, addInfoPacket);
                        
                        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            player.getEntityId(),
                            java.util.Optional.of(player.getUniqueId()),
                            EntityTypes.PLAYER,
                            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
                            loc.getPitch(),
                            loc.getYaw(),
                            loc.getYaw(),
                            0,
                            java.util.Optional.empty()
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, spawnPacket);
                        
                        WrapperPlayServerEntityHeadLook headLookPacket = new WrapperPlayServerEntityHeadLook(
                            player.getEntityId(),
                            loc.getYaw()
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, headLookPacket);
                        
                        byte skinParts = 0x7F;
                        java.util.List<EntityData> metadata = new java.util.ArrayList<>();
                        metadata.add(new EntityData(17, EntityDataTypes.BYTE, skinParts));
                        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                            player.getEntityId(),
                            metadata
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, metadataPacket);
                        
                        // 发送装备数据
                        WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(
                            player.getEntityId(),
                            equipmentList
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, equipmentPacket);
                        
                        PacketEvents.getAPI().getPlayerManager().sendPacket(observer, bundleStart);
                    }
                }
            }, 2L);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 判断玩家是否使用 slim 模型
     */
    private boolean isSlimModel(Player player) {
        try {
            var profile = player.getPlayerProfile();
            for (var prop : profile.getProperties()) {
                if ("textures".equals(prop.getName())) {
                    String value = prop.getValue();
                    String decoded = new String(java.util.Base64.getDecoder().decode(value));
                    return decoded.contains("\"model\":\"slim\"") || decoded.contains("\"model\": \"slim\"");
                }
            }
        } catch (Exception e) {
            // 忽略
        }
        return false;
    }

    /**
     * 从玩家背包移除球棒
     */
    private TextureProperty resolveCurrentTexture(Player player) {
        try {
            var profile = player.getPlayerProfile();
            if (!profile.hasTextures()) {
                profile.complete(true);
            }
            for (var prop : profile.getProperties()) {
                if ("textures".equals(prop.getName())) {
                    if (prop.getValue() != null && !prop.getValue().isBlank()) {
                        return new TextureProperty(prop.getName(), prop.getValue(), prop.getSignature());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_STEVE_TEXTURE;
    }

    private static TextureProperty createUnsignedTexture(String textureUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        String value = java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return new TextureProperty("textures", value, "");
    }
    private void removeBatFromInventory(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && "bat".equals(GameItems.getGameItemId(item))) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * 找一个空槽位（优先快捷栏）
     */
    private int findEmptySlot(Player player) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 检查玩家是否处于狂暴模式
     */
    public boolean isInPsychoMode(UUID uuid) {
        return psychoTicks.containsKey(uuid) && psychoTicks.get(uuid) > 0;
    }

    /**
     * 获取玩家护甲值
     */
    public int getArmour(UUID uuid) {
        return armourValues.getOrDefault(uuid, 0);
    }

    /**
     * 减少护甲值
     * @return 是否还有护甲（true 表示伤害被护甲吸收）
     */
    public boolean damageArmour(UUID uuid) {
        int armour = armourValues.getOrDefault(uuid, 0);
        if (armour > 0) {
            armourValues.put(uuid, armour - 1);
            return true;
        }
        return false;
    }

    /**
     * 获取剩余时间（秒）
     */
    public int getRemainingSeconds(UUID uuid) {
        return psychoTicks.getOrDefault(uuid, 0) / 20;
    }

    /**
     * 清除所有狂暴状态
     */
    public void clearAll() {
        Set<UUID> toRestore = new HashSet<>(psychoTicks.keySet());
        toRestore.addAll(originalSkins.keySet());
        for (UUID uuid : toRestore) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                removeBatFromInventory(player);
                restoreOriginalSkin(player);
                stopPsychoDroneForAll(player);
            }
        }
        psychoTicks.clear();
        armourValues.clear();
        batSlots.clear();
        originalSkins.clear();
        pendingActivation.clear();
    }

    /**
     * 玩家离开时清理
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (isInPsychoMode(uuid)) {
            stopPsychoDroneForAll(player);
        }
        psychoTicks.remove(uuid);
        armourValues.remove(uuid);
        batSlots.remove(uuid);
        originalSkins.remove(uuid);
        pendingActivation.remove(uuid);
    }
}

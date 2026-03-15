package dev.doctor4t.wathe.bukkit.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CorpseManager {

    private final WatheBukkit plugin;
    private final Map<UUID, CorpseData> corpses = new ConcurrentHashMap<>();
    private final Map<UUID, ArmorStand> fallbackCorpses = new ConcurrentHashMap<>();
    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(-1000);
    private boolean packetEventsAvailable = false;

    public record CorpseData(
        int entityId,
        UUID corpseUuid,
        UUID victimUuid,
        String victimName,
        Location location,
        boolean hidden,
        TextureProperty texture
    ) {}

    public CorpseManager(WatheBukkit plugin) {
        this.plugin = plugin;
        checkPacketEventsAvailable();
    }
    
    private void checkPacketEventsAvailable() {
        try {
            if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
                PacketEvents.getAPI();
                packetEventsAvailable = true;
                plugin.getLogger().info("PacketEvents已加载，使用虚拟玩家尸体");
            } else {
                plugin.getLogger().info("PacketEvents未安装，使用ArmorStand尸体");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("PacketEvents检测失败: " + e.getMessage());
            packetEventsAvailable = false;
        }
    }

    public void createCorpse(Player victim) {
        Location loc = victim.getLocation().clone();
        
        if (packetEventsAvailable) {
            createPacketCorpse(victim, loc);
        } else {
            createArmorStandCorpse(victim, loc);
        }
        
        // 血迹粒子
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 50, 0.5, 0.1, 0.5, Material.REDSTONE_BLOCK.createBlockData());
    }
    
    private void createArmorStandCorpse(Player victim, Location loc) {
        ArmorStand corpse = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        corpse.setInvisible(false);
        corpse.setGravity(false);
        corpse.setInvulnerable(true);
        corpse.setSmall(true);
        corpse.setCustomNameVisible(false);
        corpse.setBasePlate(false);
        corpse.setRotation(loc.getYaw(), 0);
        corpse.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
        
        fallbackCorpses.put(victim.getUniqueId(), corpse);
    }
    
    private void createPacketCorpse(Player victim, Location loc) {
        try {
            // 获取玩家皮肤数据
            TextureProperty texture = getPlayerTexture(victim);
            
            // 生成唯一的实体ID和UUID
            int entityId = ENTITY_ID_COUNTER.getAndDecrement();
            UUID corpseUuid = UUID.randomUUID();
            
            // 创建尸体数据
            CorpseData corpseData = new CorpseData(
                entityId,
                corpseUuid,
                victim.getUniqueId(),
                victim.getName(),
                loc,
                false,
                texture
            );
            
            corpses.put(victim.getUniqueId(), corpseData);
            
            // 向所有在线玩家发送尸体数据包
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                spawnCorpseForPlayer(viewer, corpseData);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("创建虚拟尸体失败，回退到ArmorStand: " + e.getMessage());
            createArmorStandCorpse(victim, loc);
        }
    }
    
    private TextureProperty getPlayerTexture(Player player) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
            Collection<com.destroystokyo.paper.profile.ProfileProperty> properties = profile.getProperties();
            
            for (com.destroystokyo.paper.profile.ProfileProperty property : properties) {
                if (property.getName().equals("textures")) {
                    return new TextureProperty("textures", property.getValue(), property.getSignature());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法获取玩家皮肤: " + e.getMessage());
        }
        return null;
    }
    
    private static final String CORPSE_TEAM_NAME = "wathe_corpse";

    private boolean isCorpseStillActive(CorpseData data) {
        CorpseData current = corpses.get(data.victimUuid());
        return current != null
                && current.corpseUuid().equals(data.corpseUuid())
                && !current.hidden();
    }
    
    private void spawnCorpseForPlayer(Player viewer, CorpseData data) {
        try {
            // 生成唯一的尸体名字用于Team
            String corpseName = "c" + Integer.toHexString(data.entityId);
            
            // 1. 创建UserProfile
            UserProfile profile = new UserProfile(data.corpseUuid, corpseName);
            if (data.texture != null) {
                profile.setTextureProperties(List.of(data.texture));
            }
            
            // 2. 发送PlayerInfo包（添加到Tab列表，但不显示在列表中）
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile,
                false, // listed = false，不显示在Tab列表
                0,
                GameMode.SURVIVAL,
                null,
                null
            );
            
            WrapperPlayServerPlayerInfoUpdate infoPacket = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER),
                playerInfo
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, infoPacket);
            
            // 3. 发送Team数据包隐藏名字标签
            WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                null,
                WrapperPlayServerTeams.OptionData.NONE
            );
            
            WrapperPlayServerTeams teamPacket = new WrapperPlayServerTeams(
                CORPSE_TEAM_NAME + data.entityId,
                WrapperPlayServerTeams.TeamMode.CREATE,
                Optional.of(teamInfo),
                corpseName
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, teamPacket);
            
            // 4. 延迟发送生成实体包
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (!viewer.isOnline()) return;
                if (!isCorpseStillActive(data)) return;
                
                try {
                    Location spawnLoc = data.location.clone();
                    
                    // 准备元数据
                    List<EntityData> metadata = new ArrayList<>();
                    // 姿势：SLEEPING（睡觉）
                    metadata.add(new EntityData(6, EntityDataTypes.ENTITY_POSE, EntityPose.SLEEPING));
                    // 皮肤显示层：index 17，0x7F = 显示所有皮肤层
                    metadata.add(new EntityData(17, EntityDataTypes.BYTE, (byte) 0x7F));
                    
                    // 准备SpawnPlayer包
                    WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                        data.entityId,
                        data.corpseUuid,
                        com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.PLAYER,
                        new com.github.retrooper.packetevents.protocol.world.Location(
                            spawnLoc.getX(),
                            spawnLoc.getY(),
                            spawnLoc.getZ(),
                            spawnLoc.getYaw(),
                            0
                        ),
                        spawnLoc.getYaw(),
                        0,
                        null
                    );
                    
                    // 准备元数据包
                    WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                        data.entityId,
                        metadata
                    );
                    
                    // 使用 Bundle 包将生成和元数据打包一起发送，避免过渡动画
                    WrapperPlayServerBundle bundleStart = new WrapperPlayServerBundle();
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, bundleStart);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, bundleStart); // Bundle 结束
                    
                    // 6. 延迟移除Tab列表中的虚拟玩家
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task2 -> {
                        if (!viewer.isOnline()) return;
                        if (!isCorpseStillActive(data)) return;
                        
                        try {
                            WrapperPlayServerPlayerInfoRemove removePacket = new WrapperPlayServerPlayerInfoRemove(
                                List.of(data.corpseUuid)
                            );
                            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, removePacket);
                        } catch (Exception e) {
                            plugin.getLogger().warning("移除Tab列表失败: " + e.getMessage());
                        }
                    }, 20L);
                } catch (Exception e) {
                    plugin.getLogger().warning("生成尸体实体失败: " + e.getMessage());
                }
            }, 2L);
        } catch (Exception e) {
            plugin.getLogger().warning("发送尸体数据包失败: " + e.getMessage());
        }
    }
    
    public void spawnAllCorpsesForPlayer(Player viewer) {
        for (CorpseData data : corpses.values()) {
            if (!data.hidden) {
                spawnCorpseForPlayer(viewer, data);
            }
        }
    }

    public boolean hideCorpse(Player player, UUID victimUuid) {
        // 检查PacketEvents尸体
        CorpseData data = corpses.get(victimUuid);
        if (data != null && !data.hidden) {
            if (packetEventsAvailable) {
                try {
                    WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(data.entityId);
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("隐藏尸体失败: " + e.getMessage());
                }
            }
            
            corpses.put(victimUuid, new CorpseData(
                data.entityId,
                data.corpseUuid,
                data.victimUuid,
                data.victimName,
                data.location,
                true,
                data.texture
            ));

            player.sendMessage(Component.text("你用尸袋隐藏了 " + data.victimName + " 的尸体!", NamedTextColor.DARK_GRAY));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            return true;
        }
        
        // 检查ArmorStand尸体
        ArmorStand armorStand = fallbackCorpses.remove(victimUuid);
        if (armorStand != null) {
            String name = armorStand.getCustomName();
            armorStand.remove();
            
            player.sendMessage(Component.text("你用尸袋隐藏了尸体!", NamedTextColor.DARK_GRAY));
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            return true;
        }
        
        return false;
    }

    public CorpseData getNearbyCorpse(Player player, double radius) {
        Location playerLoc = player.getLocation();

        // 检查PacketEvents尸体
        for (CorpseData data : corpses.values()) {
            if (data.hidden) continue;

            if (data.location.getWorld().equals(playerLoc.getWorld()) &&
                data.location.distance(playerLoc) <= radius) {
                return data;
            }
        }
        return null;
    }
    
    public UUID getNearbyCorpseUuid(Player player, double radius) {
        Location playerLoc = player.getLocation();
        
        // 检查PacketEvents尸体
        for (CorpseData data : corpses.values()) {
            if (data.hidden) continue;
            if (data.location.getWorld().equals(playerLoc.getWorld()) &&
                data.location.distance(playerLoc) <= radius) {
                return data.victimUuid;
            }
        }
        
        // 检查ArmorStand尸体
        for (Map.Entry<UUID, ArmorStand> entry : fallbackCorpses.entrySet()) {
            ArmorStand stand = entry.getValue();
            if (stand.getLocation().getWorld().equals(playerLoc.getWorld()) &&
                stand.getLocation().distance(playerLoc) <= radius) {
                return entry.getKey();
            }
        }
        
        return null;
    }

    public void removeCorpse(UUID victimUuid) {
        // 移除PacketEvents尸体
        CorpseData data = corpses.remove(victimUuid);
        if (data != null && packetEventsAvailable) {
            try {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(data.entityId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("移除尸体失败: " + e.getMessage());
            }
        }
        
        // 移除ArmorStand尸体
        ArmorStand armorStand = fallbackCorpses.remove(victimUuid);
        if (armorStand != null) {
            armorStand.remove();
        }
    }

    public void clearAllCorpses() {
        // 清除PacketEvents尸体
        if (packetEventsAvailable && !corpses.isEmpty()) {
            try {
                int[] entityIds = corpses.values().stream()
                    .mapToInt(CorpseData::entityId)
                    .toArray();
                
                if (entityIds.length > 0) {
                    WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("清除尸体失败: " + e.getMessage());
            }
        }
        corpses.clear();
        
        // 清除ArmorStand尸体
        for (ArmorStand armorStand : fallbackCorpses.values()) {
            armorStand.remove();
        }
        fallbackCorpses.clear();
    }

    public Map<UUID, CorpseData> getAllCorpses() {
        return Map.copyOf(corpses);
    }
}

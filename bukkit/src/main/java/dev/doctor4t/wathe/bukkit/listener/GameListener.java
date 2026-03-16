package dev.doctor4t.wathe.bukkit.listener;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.api.WatheRoles;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CustomBlockInteractEvent;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameListener implements Listener {

    private final WatheBukkit plugin;
    
    // 正在蓄力匕首的玩家
    private final Set<UUID> chargingKnifePlayers = new HashSet<>();
    private final Set<String> recentlyBrokenPanels = ConcurrentHashMap.newKeySet();

    public GameListener(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.getTrayManager() == null || plugin.getMapConfig() == null || plugin.getMapConfig().getSpawnLocation() == null) {
            return;
        }
        var mapWorld = plugin.getMapConfig().getSpawnLocation().getWorld();
        if (mapWorld == null || !mapWorld.equals(event.getWorld())) {
            return;
        }

        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getDoorAnimationManager().clearDoorInteractionEntitiesInChunk(event.getChunk());
            plugin.getTrayManager().preloadTraysInChunk(event.getChunk());
            plugin.getTrayManager().restorePersistedDisplaysInChunk(event.getChunk());
        }, 10L);
    }

    // ========== wathe 门交互动==========
    
    @EventHandler
    public void onCustomBlockInteract(CustomBlockInteractEvent event) {
        String blockId = event.customBlock().id().value();
        if (!event.customBlock().id().namespace().equals("wathe")) {
            return;
        }
        if (isWatheLightBlock(blockId)) {
            playWatheLightToggleSound(event.bukkitBlock(), event.player(), event.customBlock().toString());
            return;
        }
        if (!blockId.contains("door")) {
            return;
        }
        event.setCancelled(true);

        var block = event.bukkitBlock();
        var blockState = CraftEngineBlocks.getCustomBlockState(block);
        if (blockState != null) {
            plugin.getDoorAnimationManager().tryOpenDoor(event.player(), block, blockState);
        }
    }

    private boolean isWatheLightBlock(String blockId) {
        return blockId.contains("neon") || blockId.contains("lamp") || blockId.contains("lantern");
    }

    private void playWatheLightToggleSound(Block block, Player player, String stateString) {
        boolean lit = stateString.contains("lit=true");
        boolean active = !stateString.contains("active=false");
        block.getWorld().playSound(
            block.getLocation().add(0.5, 0.5, 0.5),
            "wathe:block.light.toggle",
            SoundCategory.BLOCKS,
            0.5f,
            lit ? 1.0f : 1.2f
        );
        if (!active) {
            player.playSound(
                block.getLocation().add(0.5, 0.5, 0.5),
                "minecraft:block.stone_button.click_off",
                SoundCategory.BLOCKS,
                0.1f,
                1.0f
            );
        }
    }
    
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 检查是否点击了门的 Interaction 实体
        if (event.getRightClicked() instanceof Interaction interaction) {
            if (plugin.getDoorAnimationManager().tryCloseDoorByInteraction(interaction)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // 只处理从无信号变为有信号的情况
        if (event.getOldCurrent() > 0 || event.getNewCurrent() == 0) {
            return;
        }
        
        var block = event.getBlock();
        var blockState = CraftEngineBlocks.getCustomBlockState(block);
        
        if (blockState == null) {
            return;
        }
        
        // 检查是否是 wathe 门
        String stateStr = blockState.toString();
        if (!stateStr.contains("wathe:") || !stateStr.contains("door")) {
            return;
        }
        
        // 触发开门动画
        plugin.getDoorAnimationManager().tryOpenDoor(null, block, blockState);
    }
    
    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        var block = event.getBlock();
        var blockState = CraftEngineBlocks.getCustomBlockState(block);
        
        if (blockState == null) {
            return;
        }

        if (isWathePanel(blockState)) {
            // 面板方块不做附着物理校验，避免支撑面消失后自动掉落
            event.setCancelled(true);
            keepPanelStable(block, blockState);
            return;
        }
        
        // 检查是否是 wathe 门
        String stateStr = blockState.toString();
        if (!stateStr.contains("wathe:") || !stateStr.contains("door")) {
            return;
        }
        
        // 检查方块是否被红石激活
        if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
            // 触发开门动画
            plugin.getDoorAnimationManager().tryOpenDoor(null, block, blockState);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        plugin.getTrayManager().handleBlockBreak(broken);
        var brokenState = CraftEngineBlocks.getCustomBlockState(broken);
        if (brokenState != null && isWathePanel(brokenState)) {
            String key = blockKey(broken);
            recentlyBrokenPanels.add(key);
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> recentlyBrokenPanels.remove(key), 3L);
        }
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            plugin.getDoorAnimationManager().handlePotentialDoorBlockChange(broken);
        }, 1L);
    }

    private void keepPanelStable(Block block, ImmutableBlockState snapshot) {
        String key = blockKey(block);
        var location = block.getLocation();
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (recentlyBrokenPanels.contains(key)) {
                return;
            }
            Block current = location.getBlock();
            if (!current.getType().isAir()) {
                return;
            }
            if (CraftEngineBlocks.getCustomBlockState(current) != null) {
                return;
            }
            CraftEngineBlocks.place(location, snapshot, false);
        }, 1L);
    }

    private boolean isWathePanel(ImmutableBlockState state) {
        var id = state.owner().value().id();
        if (!"wathe".equals(id.namespace())) {
            return false;
        }
        String value = id.value();
        return value.endsWith("_panel") || "panel_stripes".equals(value);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        // 禁用所有非玩家造成的伤伤害
        event.setDamage(0);

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event instanceof EntityDamageByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        event.setDamage(0);

        if (!(event.getDamager() instanceof Player attacker)) {
            event.setCancelled(true);
            return;
        }
        silenceAttackerSwingSounds(attacker);

        // 检查攻击者是否使用匕首或球棒
        var item = attacker.getInventory().getItemInMainHand();
        String itemId = GameItems.getGameItemId(item);
        
        if ("knife".equals(itemId)) {
            // 匕首左键：取消伤害但保留击退
            event.setDamage(0);
            
            // 手动应用击退
            Vector knockback = attacker.getLocation().getDirection().setY(0).normalize().multiply(0.18);
            Vector newVelocity = victim.getVelocity();
            // 覆盖水平速度，避免快速点击叠加导致超远击退
            newVelocity.setX(knockback.getX());
            newVelocity.setZ(knockback.getZ());
            newVelocity.setY(Math.max(newVelocity.getY(), 0.08));
            victim.setVelocity(newVelocity);
        } else if ("bat".equals(itemId)) {
            // 球棒攻击：检查攻击冷却
            if (attacker.getAttackCooldown() < 1.0f) {
                event.setCancelled(true);
                return;
            }
            
            // 检查受害者是否有护甲（狂暴模式护甲）
            GamePlayer victimGP = plugin.getPlayerManager().getPlayer(victim);
            if (victimGP != null && victimGP.isAlive()) {
                if (plugin.getPsychoModeManager().damageArmour(victim.getUniqueId())) {
                    // 护甲吸收了伤害
                    event.setCancelled(true);
                    victim.getWorld().playSound(victim.getLocation(), "wathe:item.bat.blocked", 1.0f, 1.0f);
                } else {
                    // 没有护甲，直接击杀
                    event.setCancelled(true);
                    victim.getWorld().playSound(victim.getLocation(), "wathe:item.bat.hit", 1.0f, 1.0f);
                    plugin.getGameManager().onPlayerDeath(victim, attacker);
                }
            }
        } else {
            // 其他情况禁用伤害
            event.setCancelled(true);
        }
    }

    private void silenceAttackerSwingSounds(Player attacker) {
        attacker.stopSound(Sound.ENTITY_PLAYER_ATTACK_WEAK);
        attacker.stopSound(Sound.ENTITY_PLAYER_ATTACK_STRONG);
        attacker.stopSound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK);
        attacker.stopSound(Sound.ENTITY_PLAYER_ATTACK_SWEEP);
        attacker.stopSound(Sound.ENTITY_PLAYER_ATTACK_NODAMAGE);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 游戏中禁用饥饿
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 允许随时睡觉，强制允许进入床
        event.setUseBed(org.bukkit.event.Event.Result.ALLOW);
        
        Player player = event.getPlayer();
        
        // 记录当前重生点，稍后恢复（防止床设置重生点）
        var currentRespawn = player.getRespawnLocation();
        
        // 睡觉时添加黑暗效果
        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && player.isSleeping()) {
                // 恢复原来的重生点，不发送消息
                player.setRespawnLocation(currentRespawn, true);
                // 添加黑暗和失明效果
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // 起床时移除黑暗和失明效果
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 禁止丢弃游戏物品
        var droppedItem = event.getItemDrop().getItemStack();
        if (GameItems.isGameItem(droppedItem)) {
            event.setCancelled(true);
            return;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(event.getPlayer());
        if (gp == null || !gp.isAlive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(event.getPlayer());
        if (gp == null || !gp.isAlive()) {
            return;
        }

        // 取消挥臂动画，抑制对局内疯狂左键造成的挥击噪音
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        // 禁用副手切换，但用于杀手透视
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        plugin.getWallhackManager().onFKeyPress(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        Player player = event.getPlayer();
        if (!isSolidCollisionParticipant(player)) {
            return;
        }

        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            return;
        }

        BoundingBox baseBox = player.getBoundingBox();
        if (!collidesWithOtherPlayer(player, baseBox.shift(dx, dy, dz))) {
            return;
        }

        // 平滑阻挡：优先尝试沿单轴滑动，避免直接回退导致视角抽搐
        double finalDx = 0.0;
        double finalDy = 0.0;
        double finalDz = 0.0;

        if (!collidesWithOtherPlayer(player, baseBox.shift(dx, 0.0, 0.0))) {
            finalDx = dx;
        }
        if (!collidesWithOtherPlayer(player, baseBox.shift(finalDx, 0.0, dz))) {
            finalDz = dz;
        }
        if (!collidesWithOtherPlayer(player, baseBox.shift(finalDx, dy, finalDz))) {
            finalDy = dy;
        }

        var adjusted = from.clone().add(finalDx, finalDy, finalDz);
        // 保留玩家视角，防止位置回退时镜头抖动
        adjusted.setYaw(to.getYaw());
        adjusted.setPitch(to.getPitch());
        event.setTo(adjusted);
    }

    private boolean isSolidCollisionParticipant(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return false;
        }
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        return gp != null && gp.isAlive();
    }

    private boolean collidesWithOtherPlayer(Player self, BoundingBox box) {
        for (var entity : self.getWorld().getNearbyEntities(box, e -> e instanceof Player other
                && other != self
                && isSolidCollisionParticipant(other))) {
            if (box.overlaps(entity.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getSlot();
        int rawSlot = event.getRawSlot();
        
        // 检查是否是玩家自己的背包
        if (event.getClickedInventory() != null && 
            event.getClickedInventory().getType() == InventoryType.PLAYER &&
            event.getClickedInventory() == player.getInventory()) {
            
            // 快捷栏是 0-8，背包是 9-35，盔甲是 36-39，副手是 40
            // 只允许在快捷栏（0-8）内操作
            if (slot >= 0 && slot <= 8) {
                // 快捷栏内的操作
                // 检查是否是shift点击（会移动到背包）
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    return;
                }
                
                // 检查是否有光标物品要放到快捷栏操作
                // 允许快捷栏内的正常点击和交换
            } else if (slot >= 9 && slot < 36) {
                // 背包格子 9-35
                event.setCancelled(true);
                
                // 杀手点击商店物品时尝试购买
                GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
                if (gp != null && gp.isAlive() && gp.role() == WatheRoles.KILLER) {
                    var clicked = event.getCurrentItem();
                    if (clicked != null && !clicked.isEmpty()) {
                        // 尝试购买
                        if (plugin.getShopManager().handleInventoryShopClick(player, clicked)) {
                            // 购买成功后刷新背包中的商店物品显示
                            plugin.getShopManager().refreshInventoryShopItems(player);
                        }
                    }
                }
            } else if (slot >= 36 && slot <= 40) {
                // 盔甲格子36-39）和副手40
                event.setCancelled(true);
            }
        } else {
            // 点击的是其他容器（如箱子、商店GUI等）
            // 阻止shift点击将物品移动到玩家背包的非快捷栏区域
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
        
        // 阻止数字键将物品移动到背包
        if (event.getClick().name().contains("NUMBER_KEY")) {
            // 数字键对应的是快捷栏槽位，允许
            // 但如果当前点击的是背包格子，则阻止
            if (slot >= 9) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        var item = event.getItem().getItemStack();
        String itemId = GameItems.getGameItemId(item);
        
        // 如果是枪，检查拾取者是否是杀手阵营
        if ("gun".equals(itemId)) {
            GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
            if (gp != null && gp.role() == WatheRoles.KILLER) {
                // 杀手不能拾取枪
                event.setCancelled(true);
            }
        }
    }

    // ========== 匕首蓄力移动==========
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        
        Player player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();
        String itemId = GameItems.getGameItemId(item);
        
        // 检测匕首右键蓄力
        if ("knife".equals(itemId)) {
            if (event.getAction().name().contains("RIGHT")) {
                // 在物品冷却中则不处理
                if (player.hasCooldown(item.getType())) {
                    return;
                }
                
                // 开始蓄力，添加移速
                if (!chargingKnifePlayers.contains(player.getUniqueId())) {
                    addSpeedBoost(player);
                    chargingKnifePlayers.add(player.getUniqueId());
                    
                    // 播放准备音效（范围内可听见）
                    player.getWorld().playSound(player.getLocation(), "wathe:item.knife.prepare", 1.0f, 1.0f);
                    
                    // 启动检测任务，检测玩家是否停止蓄力
                    startChargeCheckTask(player);
                }
            }
        }
    }
    
    // 监听三叉戟投掷事件 匕首蓄力完成松开右键时触发
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }
        
        if (!(trident.getShooter() instanceof Player player)) {
            return;
        }
        
        // 检查是否是匕首（游戏物品）
        var item = player.getInventory().getItemInMainHand();
        String itemId = GameItems.getGameItemId(item);
        
        if ("knife".equals(itemId)) {
            // 取消三叉戟投掷
            event.setCancelled(true);
            
            // 先移除蓄力状态
            chargingKnifePlayers.remove(player.getUniqueId());
            
            // 延迟1tick后移除移速并执行攻击，确保客户端同步
            org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    removeSpeedBoost(player);
                    plugin.getItemHandler().executeKnifeAttack(player);
                }
            }, 1L);
        }
    }
    
    private void startChargeCheckTask(Player player) {
        // 每tick检测，确保及时响应松开右键
        org.bukkit.Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            // 检查玩家是否还在蓄力
            if (!chargingKnifePlayers.contains(player.getUniqueId())) {
                task.cancel();
                return;
            }
            
            if (!player.isOnline()) {
                removeSpeedBoost(player);
                chargingKnifePlayers.remove(player.getUniqueId());
                task.cancel();
                return;
            }
            
            // 检查是否还在使用匕首
            var item = player.getInventory().getItemInMainHand();
            String itemId = GameItems.getGameItemId(item);
            
            // 如果不再持有匕首
            if (!"knife".equals(itemId)) {
                removeSpeedBoost(player);
                chargingKnifePlayers.remove(player.getUniqueId());
                task.cancel();
                return;
            }
            
            // 检测是否停止蓄力
            // 如果玩家没有在使用物品，说明松开了右键
            if (!player.isHandRaised()) {
                removeSpeedBoost(player);
                chargingKnifePlayers.remove(player.getUniqueId());
                task.cancel();
            }
        }, 1L, 1L);
    }
    
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!plugin.getGameManager().isGameRunning()) {
            return;
        }
        
        Player player = event.getPlayer();
        // 切换物品时移除移速加成并设置冷却
        if (chargingKnifePlayers.contains(player.getUniqueId())) {
            player.setWalkSpeed(0.2f);
            chargingKnifePlayers.remove(player.getUniqueId());
            
            // 获取之前手持的物品（刀）设置冷却
            var oldItem = player.getInventory().getItem(event.getPreviousSlot());
            if (oldItem != null) {
                String itemId = GameItems.getGameItemId(oldItem);
                if ("knife".equals(itemId)) {
                    player.setCooldown(oldItem.getType(), 20);
                }
            }
        }
    }
    
    private void addSpeedBoost(Player player) {
        // 使用 setWalkSpeed 来增加移速
        // 默认行走速度0.2，最大值是 1.0
        player.setWalkSpeed(1.0f);
    }
    
    private void removeSpeedBoost(Player player) {
        // 恢复默认行走速度
        player.setWalkSpeed(0.2f);
        
        // 设置1秒物品冷却
        var item = player.getInventory().getItemInMainHand();
        String itemId = GameItems.getGameItemId(item);
        if ("knife".equals(itemId)) {
            player.setCooldown(item.getType(), 20);
        }
    }
    
    public void clearAllKnifeSpeedBoosts() {
        for (UUID uuid : chargingKnifePlayers) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setWalkSpeed(0.2f);
            }
        }
        chargingKnifePlayers.clear();
    }
}

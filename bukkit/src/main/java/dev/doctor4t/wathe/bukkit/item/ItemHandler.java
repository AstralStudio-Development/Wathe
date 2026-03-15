package dev.doctor4t.wathe.bukkit.item;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.player.CorpseManager;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class ItemHandler {

    private final WatheBukkit plugin;

    public ItemHandler(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public boolean useItem(Player player, ItemStack item) {
        String itemId = GameItems.getGameItemId(item);
        if (itemId == null) {
            return false;
        }

        if (!plugin.getGameManager().isGameRunning()) {
            return false;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null || !gp.isAlive()) {
            return false;
        }

        // 检查原版冷却
        if (player.hasCooldown(item.getType())) {
            return false;
        }

        boolean success = false;
        int cooldownTicks = 0;

        switch (itemId) {
            case "knife" -> {
                // 匕首蓄力由三叉戟原版机制处理，这里不做任何事
                return true;
            }
            case "gun" -> {
                success = useGun(player, gp, item);
                cooldownTicks = GameConstants.getGunCooldown();
            }
            case "grenade" -> {
                success = useGrenade(player, gp, item);
                cooldownTicks = GameConstants.getGrenadeCooldown();
            }
            case "firecracker" -> {
                success = useFirecracker(player, item);
                cooldownTicks = 20;
            }
            case "poison" -> {
                success = usePoison(player, gp, item);
                cooldownTicks = 20;
            }
            case "body_bag" -> {
                success = useBodyBag(player, gp, item);
                cooldownTicks = GameConstants.getBodyBagCooldown();
            }
            case "blackout" -> {
                success = useBlackout(player, gp, item);
                cooldownTicks = GameConstants.getBlackoutCooldown();
            }
        }

        if (success && cooldownTicks > 0) {
            player.setCooldown(item.getType(), cooldownTicks);
        }

        return success;
    }

    /**
     * 执行匕首攻击（由三叉戟投掷事件触发）
     */
    public void executeKnifeAttack(Player player) {
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (gp == null || !gp.isAlive()) {
            return;
        }
        
        if (!gp.canUseKillerItems()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        
        // 射线检测前方玩家
        var result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                3.0,
                entity -> entity instanceof Player p && p != player
        );

        if (result != null && result.getHitEntity() instanceof Player target) {
            GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
            if (targetGP != null && targetGP.isAlive()) {
                // 攻击成功，播放刺杀音效
                player.getWorld().playSound(player.getLocation(), "wathe:item.knife.stab", 1.0f, 1.0f);
                plugin.getGameManager().onPlayerDeath(target, player);
                player.setCooldown(item.getType(), GameConstants.getKnifeCooldown());
                return;
            }
        }
    }

    public void cancelCharge(Player player) {
        // 不再需要，保留空方法以兼容
    }

    public void cancelAllCharges() {
        // 不再需要，保留空方法以兼容
    }

    public boolean isCharging(Player player) {
        return false;
    }

    private boolean useGun(Player player, GamePlayer gp, ItemStack item) {
        var result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                20.0,
                entity -> entity instanceof Player p && p != player
        );

        player.getWorld().playSound(player.getLocation(), "wathe:item.revolver.shoot", 2.0f, 1.0f);

        Vector direction = player.getLocation().getDirection();
        Location start = player.getEyeLocation();
        // 减少粒子数量，间隔更大
        for (int i = 0; i < 10; i++) {
            Location particleLoc = start.clone().add(direction.clone().multiply(i * 2.0));
            player.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }

        if (result != null && result.getHitEntity() instanceof Player target) {
            GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
            if (targetGP != null && targetGP.isAlive()) {
                plugin.getGameManager().onPlayerDeath(target, player);
            }
        }

        if (gp.canUseKillerItems() && ThreadLocalRandom.current().nextDouble() < 0.5D) {
            item.setAmount(item.getAmount() - 1);
            ItemStack droppedGun = GameItems.createGun();
            if (droppedGun != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), droppedGun);
            }
        }

        return true;
    }

    private boolean useGrenade(Player player, GamePlayer gp, ItemStack item) {
        if (!gp.canUseKillerItems()) {
            player.sendMessage(Component.text("你不能使用这个物品", NamedTextColor.RED));
            return false;
        }

        Location throwLoc = player.getEyeLocation();
        Vector velocity = player.getLocation().getDirection().multiply(1.5);

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            Location explosionLoc = throwLoc.add(velocity.multiply(20));
            player.getWorld().createExplosion(explosionLoc, 0, false, false);
            player.getWorld().playSound(explosionLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

            for (var entity : explosionLoc.getWorld().getNearbyEntities(explosionLoc, 5, 5, 5)) {
                if (entity instanceof Player target && target != player) {
                    GamePlayer targetGP = plugin.getPlayerManager().getPlayer(target);
                    if (targetGP != null && targetGP.isAlive()) {
                        plugin.getGameManager().onPlayerDeath(target, player);
                    }
                }
            }
        }, 60L);

        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private boolean useFirecracker(Player player, ItemStack item) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);

        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private boolean usePoison(Player player, GamePlayer gp, ItemStack item) {
        if (!gp.canUseKillerItems()) {
            player.sendMessage(Component.text("你不能使用这个物品", NamedTextColor.RED));
            return false;
        }
        // 原版玩法：毒药需要混入餐盘/饮品盘，不支持对玩家直接使用
        return false;
    }

    private boolean useBodyBag(Player player, GamePlayer gp, ItemStack item) {
        if (!gp.canUseKillerItems()) {
            player.sendMessage(Component.text("你不能使用这个物品!", NamedTextColor.RED));
            return false;
        }

        CorpseManager.CorpseData corpse = plugin.getCorpseManager().getNearbyCorpse(player, 3.0);
        if (corpse == null) {
            return false;
        }

        if (plugin.getCorpseManager().hideCorpse(player, corpse.victimUuid())) {
            item.setAmount(item.getAmount() - 1);
            return true;
        }

        return false;
    }

    private boolean useBlackout(Player player, GamePlayer gp, ItemStack item) {
        if (!gp.canUseKillerItems()) {
            player.sendMessage(Component.text("你不能使用这个物品!", NamedTextColor.RED));
            return false;
        }

        if (plugin.getBlackoutManager().startBlackout(player)) {
            item.setAmount(item.getAmount() - 1);
            return true;
        }

        return false;
    }

    public void clearAllCooldowns() {
        cancelAllCharges();
    }
}

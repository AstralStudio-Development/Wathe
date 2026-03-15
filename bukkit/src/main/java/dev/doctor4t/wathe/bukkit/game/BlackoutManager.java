package dev.doctor4t.wathe.bukkit.game;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class BlackoutManager {

    private final WatheBukkit plugin;
    private boolean blackoutActive = false;
    private ScheduledTask blackoutTask;

    public BlackoutManager(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    public boolean startBlackout(Player initiator) {
        if (blackoutActive) {
            return false;
        }

        blackoutActive = true;
        setAllWatheLights(false);

        int minDuration = GameConstants.getBlackoutMinDuration();
        int maxDuration = GameConstants.getBlackoutMaxDuration();
        int duration = ThreadLocalRandom.current().nextInt(minDuration, maxDuration + 1);

        // 给所有玩家添加失明效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration + 40, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration + 40, 0, false, false));
            player.playSound(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.0f, 0.5f);
        }

        // 定时结束停电
        blackoutTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            endBlackout();
        }, duration);

        return true;
    }

    public void endBlackout() {
        if (!blackoutActive) {
            return;
        }

        blackoutActive = false;
        setAllWatheLights(true);

        if (blackoutTask != null) {
            blackoutTask.cancel();
            blackoutTask = null;
        }

        // 移除所有玩家的失明效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
        }
    }

    public boolean isBlackoutActive() {
        return blackoutActive;
    }

    public void forceEnd() {
        if (blackoutTask != null) {
            blackoutTask.cancel();
            blackoutTask = null;
        }
        blackoutActive = false;
        setAllWatheLights(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private void setAllWatheLights(boolean enable) {
        for (World world : Bukkit.getWorlds()) {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            for (Chunk chunk : world.getLoadedChunks()) {
                int baseX = chunk.getX() << 4;
                int baseZ = chunk.getZ() << 4;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
                            if (state == null || !isWatheLightState(state)) {
                                continue;
                            }
                            ImmutableBlockState updated = setLightState(state, enable);
                            if (updated != state) {
                                CraftEngineBlocks.place(block.getLocation(), updated, false);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isWatheLightState(ImmutableBlockState state) {
        var owner = state.owner().value();
        if (owner == null) {
            return false;
        }
        var id = owner.id();
        if (!"wathe".equals(id.namespace())) {
            return false;
        }
        String value = id.value();
        return value.contains("neon") || value.contains("lamp") || value.contains("lantern");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ImmutableBlockState setLightState(ImmutableBlockState state, boolean enabled) {
        ImmutableBlockState updated = state;
        for (Property<?> property : state.getProperties()) {
            if (!Boolean.class.isAssignableFrom(property.valueClass())) {
                continue;
            }
            String name = property.name();
            if (!"lit".equals(name) && !"active".equals(name)) {
                continue;
            }
            updated = updated.with((Property) property, enabled);
        }
        return updated;
    }
}

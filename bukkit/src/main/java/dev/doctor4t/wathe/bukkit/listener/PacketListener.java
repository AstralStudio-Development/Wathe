package dev.doctor4t.wathe.bukkit.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PacketListener extends PacketListenerAbstract {

    private final WatheBukkit plugin;
    private static final List<String> ATTACK_SWING_SOUNDS = List.of(
            "entity.player.attack.weak",
            "entity.player.attack.strong",
            "entity.player.attack.knockback",
            "entity.player.attack.sweep",
            "entity.player.attack.nodamage"
    );

    public PacketListener(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (plugin.getGameManager().isGameRunning() && event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            maskDeadPlayersInTabPacket(event);
        }

        if (isAttackSwingSoundPacket(event)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getGameManager().isGameRunning() && event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            try {
                WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
                Component message = packet.getMessage();
                
                if (message != null) {
                    String plainText = PlainTextComponentSerializer.plainText().serialize(message);
                    
                    // 拦截"已设置重生点"消息
                    if (plainText.contains("已设置重生点") || plainText.contains("Respawn point set")) {
                        event.setCancelled(true);
                    }
                    
                    // 拦截睡觉相关的提示消息
                    if (plainText.contains("入睡") || plainText.contains("睡觉") || 
                        plainText.contains("sleep") || plainText.contains("Sleep") ||
                        plainText.contains("跳过") || plainText.contains("skip") ||
                        plainText.contains("夜晚") || plainText.contains("night")) {
                        event.setCancelled(true);
                    }
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
    }

    private void maskDeadPlayersInTabPacket(PacketSendEvent event) {
        try {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = packet.getActions();
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = packet.getEntries();
            if (actions == null || entries == null || entries.isEmpty()) {
                return;
            }

            boolean changed = false;
            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : entries) {
                UUID uuid = info.getProfileId();
                if (uuid == null) {
                    continue;
                }

                var online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline() || online.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }

                var gp = plugin.getPlayerManager().getPlayer(uuid);
                if (gp == null) {
                    continue;
                }

                info.setGameMode(GameMode.SURVIVAL);
                info.setListed(true);

                UserProfile profile = info.getGameProfile();
                String name = profile != null ? profile.getName() : null;
                if (name == null || name.isBlank()) {
                    name = online.getName();
                }
                if (name != null && !name.isBlank()) {
                    info.setDisplayName(Component.text(name, NamedTextColor.WHITE));
                }
                changed = true;
            }

            if (!changed) {
                return;
            }

            actions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE);
            actions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED);
            actions.add(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
            packet.setActions(actions);
            packet.setEntries(entries);
            event.markForReEncode(true);
        } catch (Exception ignored) {
            // Ignore and keep original packet if PacketEvents API changes.
        }
    }

    private boolean isAttackSwingSoundPacket(PacketSendEvent event) {
        String soundId = extractSoundIdFromKnownSoundPackets(event);
        if (soundId.isBlank()) {
            return false;
        }

        for (String sound : ATTACK_SWING_SOUNDS) {
            if (soundId.contains(sound)) {
                return true;
            }
        }
        return false;
    }

    private String extractSoundIdFromKnownSoundPackets(PacketSendEvent event) {
        try {
            if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
                WrapperPlayServerSoundEffect packet = new WrapperPlayServerSoundEffect(event);
                return stringFromSoundObject(packet.getSound()).toLowerCase(Locale.ROOT);
            }
            if (event.getPacketType() == PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
                WrapperPlayServerEntitySoundEffect packet = new WrapperPlayServerEntitySoundEffect(event);
                return stringFromSoundObject(packet.getSound()).toLowerCase(Locale.ROOT);
            }
            // Never touch STOP_SOUND or any non-target sound packet.
            return "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private Object invokeIfExists(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stringFromSoundObject(Object soundObj) {
        if (soundObj == null) {
            return "";
        }

        for (String method : List.of("getSoundId", "getName", "getKey", "asString")) {
            Object value = invokeIfExists(soundObj, method);
            if (value != null) {
                return value.toString();
            }
        }
        return soundObj.toString();
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}

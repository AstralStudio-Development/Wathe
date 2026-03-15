package dev.doctor4t.wathe.bukkit.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * MiniMessage 工具类
 * 
 * 支持的格式示例：
 * - <red>红色文字</red>
 * - <gradient:red:blue>渐变文字</gradient>
 * - <rainbow>彩虹文字</rainbow>
 * - <bold>粗体</bold>
 * - <italic>斜体</italic>
 * - <underlined>下划线</underlined>
 * - <strikethrough>删除线</strikethrough>
 * - <hover:show_text:'提示文字'>悬停文字</hover>
 * - <click:run_command:/command>点击执行命令</click>
 * - <#FF5555>十六进制颜色</#FF5555>
 * 
 * 占位符使用：
 * - <player> - 玩家名称
 * - <value> - 自定义值
 */
public final class MiniMessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessageUtil() {}

    /**
     * 解析 MiniMessage 格式字符串
     */
    public static Component parse(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /**
     * 解析 MiniMessage 格式字符串，带占位符
     */
    public static Component parse(String message, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, resolvers);
    }

    /**
     * 解析 MiniMessage 格式字符串，带玩家占位符
     */
    public static Component parse(String message, Player player) {
        return MINI_MESSAGE.deserialize(message, 
            Placeholder.unparsed("player", player.getName())
        );
    }

    /**
     * 解析 MiniMessage 格式字符串，带自定义占位符
     */
    public static Component parse(String message, String key, String value) {
        return MINI_MESSAGE.deserialize(message,
            Placeholder.unparsed(key, value)
        );
    }

    /**
     * 解析 MiniMessage 格式字符串，带多个自定义占位符
     */
    public static Component parse(String message, String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be in pairs");
        }
        
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i < keyValues.length; i += 2) {
            builder.resolver(Placeholder.unparsed(keyValues[i], keyValues[i + 1]));
        }
        
        return MINI_MESSAGE.deserialize(message, builder.build());
    }

    /**
     * 向玩家发送 MiniMessage 格式消息
     */
    public static void send(Player player, String message) {
        player.sendMessage(parse(message));
    }

    /**
     * 向玩家发送 MiniMessage 格式消息，带占位符
     */
    public static void send(Player player, String message, TagResolver... resolvers) {
        player.sendMessage(parse(message, resolvers));
    }

    /**
     * 向玩家发送 ActionBar
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(parse(message));
    }

    /**
     * 将 Component 序列化为 MiniMessage 格式字符串
     */
    public static String serialize(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * 去除 MiniMessage 标签，返回纯文本
     */
    public static String stripTags(String message) {
        return MINI_MESSAGE.stripTags(message);
    }
}

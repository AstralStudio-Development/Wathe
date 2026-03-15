package dev.doctor4t.wathe.bukkit.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LuckPermsIntegration {

    private static boolean enabled = false;
    private static LuckPerms luckPerms = null;

    public static void init() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = LuckPermsProvider.get();
                enabled = true;
            } catch (Exception e) {
                enabled = false;
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void addPermission(Player player, String permission) {
        if (!enabled || luckPerms == null) {
            return;
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            user.data().add(Node.builder(permission).build());
            luckPerms.getUserManager().saveUser(user);
        }
    }

    public static void removePermission(Player player, String permission) {
        if (!enabled || luckPerms == null) {
            return;
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            user.data().remove(Node.builder(permission).build());
            luckPerms.getUserManager().saveUser(user);
        }
    }
}

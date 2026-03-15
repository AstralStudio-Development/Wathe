package dev.doctor4t.wathe.bukkit.command;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.api.WatheRoles;
import dev.doctor4t.wathe.bukkit.game.MurderGameMode;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WatheCommand implements CommandExecutor, TabCompleter {

    private static final String REQUIRED_PERMISSION = "wathe.*";
    private final WatheBukkit plugin;
    private final Map<UUID, Location> tempPos1 = new HashMap<>();

    public WatheCommand(WatheBukkit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限执行此命令！", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "role" -> handleRole(sender, args);
            case "money" -> handleMoney(sender, args);
            case "timer" -> handleTimer(sender, args);
            case "shop" -> handleShop(sender);
            case "reload" -> handleReload(sender);
            case "map" -> handleMap(sender, args);
            case "give" -> handleGive(sender, args);
            case "kill" -> handleKill(sender, args);
            case "blackout" -> handleBlackout(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Wathe 命令帮助 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/wathe start", NamedTextColor.YELLOW).append(Component.text(" - 开始游戏", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe stop", NamedTextColor.YELLOW).append(Component.text(" - 停止游戏", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe role <玩家> <角色>", NamedTextColor.YELLOW).append(Component.text(" - 设置玩家角色", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe money <玩家> <数量>", NamedTextColor.YELLOW).append(Component.text(" - 设置玩家金币", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe timer <分钟> [秒]", NamedTextColor.YELLOW).append(Component.text(" - 设置游戏时间", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe shop", NamedTextColor.YELLOW).append(Component.text(" - 刷新背包商店显示", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe reload", NamedTextColor.YELLOW).append(Component.text(" - 重载配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map <子命令>", NamedTextColor.YELLOW).append(Component.text(" - 地图配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe give <玩家> <物品>", NamedTextColor.YELLOW).append(Component.text(" - 给予游戏物品", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe kill <玩家>", NamedTextColor.YELLOW).append(Component.text(" - 杀死玩家", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe blackout", NamedTextColor.YELLOW).append(Component.text(" - 触发停电", NamedTextColor.GRAY)));
    }

    private boolean handleStart(CommandSender sender) {
        if (plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("游戏已经在运行中！", NamedTextColor.RED));
            return true;
        }

        var gameMode = new MurderGameMode();
        if (plugin.getGameManager().startGame(gameMode)) {
            sender.sendMessage(Component.text("游戏已开始。", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("无法开始游戏：人数不足（至少 " + gameMode.minPlayers() + " 人）。", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("当前没有游戏在运行。", NamedTextColor.RED));
            return true;
        }
        plugin.getGameManager().stopGame();
        sender.sendMessage(Component.text("游戏已停止。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleRole(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /wathe role <玩家> <角色>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家: " + args[1], NamedTextColor.RED));
            return true;
        }

        var role = WatheRoles.getById(args[2].toLowerCase());
        if (role == null) {
            sender.sendMessage(Component.text("未知角色: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("可用角色: civilian, vigilante, killer, loose_end", NamedTextColor.GRAY));
            return true;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(target);
        if (gp == null) {
            sender.sendMessage(Component.text("该玩家不在游戏中。", NamedTextColor.RED));
            return true;
        }

        gp.setRole(role);
        plugin.getGameManager().announceRole(target, role);
        sender.sendMessage(Component.text("已将 " + target.getName() + " 的角色设置为 ", NamedTextColor.GREEN).append(role.coloredName()));
        return true;
    }

    private boolean handleMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /wathe money <玩家> <数量>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家: " + args[1], NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效数字: " + args[2], NamedTextColor.RED));
            return true;
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(target);
        if (gp == null) {
            sender.sendMessage(Component.text("该玩家不在游戏中。", NamedTextColor.RED));
            return true;
        }

        gp.setMoney(amount);
        sender.sendMessage(Component.text("已将 " + target.getName() + " 的金币设置为 " + amount, NamedTextColor.GREEN));
        return true;
    }

    private boolean handleTimer(CommandSender sender, String[] args) {
        if (!plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("当前没有游戏在运行。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /wathe timer <分钟> [秒]", NamedTextColor.RED));
            return true;
        }

        int minutes;
        int seconds = 0;
        try {
            minutes = Integer.parseInt(args[1]);
            if (args.length >= 3) {
                seconds = Integer.parseInt(args[2]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效时间。", NamedTextColor.RED));
            return true;
        }

        plugin.getGameManager().setTimer(minutes, seconds);
        sender.sendMessage(Component.text("已将游戏时间设置为 " + minutes + ":" + String.format("%02d", seconds), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用该命令。", NamedTextColor.RED));
            return true;
        }
        if (!plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("当前没有游戏在运行。", NamedTextColor.RED));
            return true;
        }
        plugin.getShopManager().openShop(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfiguration();
        plugin.getMapConfig().reload();
        plugin.preloadMapDoors();
        sender.sendMessage(Component.text("配置已重载。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleMap(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMapHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用该命令。", NamedTextColor.RED));
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "setspawn" -> handleMapSetSpawn(player);
            case "setspectatorspawn" -> handleMapSetSpectatorSpawn(player);
            case "setplayarea" -> handleMapSetArea(player, args, "playarea");
            case "setreadyarea" -> handleMapSetArea(player, args, "readyarea");
            case "setinsidearea" -> handleMapSetArea(player, args, "insidearea");
            case "setresettemplate" -> handleMapSetArea(player, args, "resettemplate");
            case "setplayoffset" -> handleMapSetOffset(player, args, "playoffset");
            case "setpasteoffset" -> handleMapSetOffset(player, args, "pasteoffset");
            case "save" -> handleMapSave(sender);
            case "info" -> handleMapInfo(sender);
            default -> {
                sendMapHelp(sender);
                yield true;
            }
        };
    }

    private void sendMapHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== 地图配置命令 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/wathe map setspawn", NamedTextColor.YELLOW).append(Component.text(" - 设置出生点", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setspectatorspawn", NamedTextColor.YELLOW).append(Component.text(" - 设置旁观者出生点", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setplayarea <pos1|pos2>", NamedTextColor.YELLOW).append(Component.text(" - 设置游戏区域", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setreadyarea <pos1|pos2>", NamedTextColor.YELLOW).append(Component.text(" - 设置准备区域", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setinsidearea <pos1|pos2>", NamedTextColor.YELLOW).append(Component.text(" - 设置车内区域", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setresettemplate <pos1|pos2>", NamedTextColor.YELLOW).append(Component.text(" - 设置重置模板区域", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setplayoffset <x> <y> <z>", NamedTextColor.YELLOW).append(Component.text(" - 设置游戏区域偏移", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map setpasteoffset <x> <y> <z>", NamedTextColor.YELLOW).append(Component.text(" - 设置粘贴偏移", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map save", NamedTextColor.YELLOW).append(Component.text(" - 保存地图配置", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wathe map info", NamedTextColor.YELLOW).append(Component.text(" - 查看地图配置", NamedTextColor.GRAY)));
    }

    private boolean handleMapSetSpawn(Player player) {
        plugin.getMapConfig().setSpawnLocation(player.getLocation());
        player.sendMessage(Component.text("出生点已设置。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleMapSetSpectatorSpawn(Player player) {
        plugin.getMapConfig().setSpectatorSpawnLocation(player.getLocation());
        player.sendMessage(Component.text("旁观者出生点已设置。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleMapSetArea(Player player, String[] args, String areaType) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /wathe map set" + areaType + " <pos1|pos2>", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        String posType = args[2].toLowerCase();

        if (posType.equals("pos1")) {
            tempPos1.put(player.getUniqueId(), loc);
            player.sendMessage(Component.text("Pos1 已设置: " + formatLocation(loc), NamedTextColor.GREEN));
            return true;
        }

        if (posType.equals("pos2")) {
            Location pos1 = tempPos1.get(player.getUniqueId());
            if (pos1 == null) {
                player.sendMessage(Component.text("请先设置 pos1。", NamedTextColor.RED));
                return true;
            }

            BoundingBox box = BoundingBox.of(pos1, loc);
            switch (areaType) {
                case "playarea" -> plugin.getMapConfig().setPlayArea(box);
                case "readyarea" -> plugin.getMapConfig().setReadyArea(box);
                case "insidearea" -> plugin.getMapConfig().setInsideArea(box);
                case "resettemplate" -> plugin.getMapConfig().setResetTemplateArea(box);
                default -> {
                    player.sendMessage(Component.text("未知区域类型。", NamedTextColor.RED));
                    return true;
                }
            }

            tempPos1.remove(player.getUniqueId());
            player.sendMessage(Component.text("区域已设置。", NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(Component.text("无效参数，请使用 pos1 或 pos2。", NamedTextColor.RED));
        return true;
    }

    private boolean handleMapSetOffset(Player player, String[] args, String offsetType) {
        if (args.length < 5) {
            player.sendMessage(Component.text("用法: /wathe map set" + offsetType + " <x> <y> <z>", NamedTextColor.RED));
            return true;
        }

        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            Vector offset = new Vector(x, y, z);

            if (offsetType.equals("playoffset")) {
                plugin.getMapConfig().setPlayAreaOffset(offset);
            } else {
                plugin.getMapConfig().setResetPasteOffset(offset);
            }

            player.sendMessage(Component.text("偏移已设置: " + x + ", " + y + ", " + z, NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效坐标。", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleMapSave(CommandSender sender) {
        plugin.getMapConfig().save();
        sender.sendMessage(Component.text("地图配置已保存。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleMapInfo(CommandSender sender) {
        var config = plugin.getMapConfig();
        sender.sendMessage(Component.text("=== 地图配置信息 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("出生点: " + formatLocation(config.getSpawnLocation()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("旁观者出生点: " + formatLocation(config.getSpectatorSpawnLocation()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("游戏区域: " + formatBoundingBox(config.getPlayArea()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("准备区域: " + formatBoundingBox(config.getReadyArea()), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("车内区域: " + formatBoundingBox(config.getInsideArea()), NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /wathe give <玩家> <物品>", NamedTextColor.RED));
            sender.sendMessage(Component.text("可用物品: knife, gun, grenade, poison, lockpick, crowbar, body_bag, firecracker, note, blackout", NamedTextColor.GRAY));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家: " + args[1], NamedTextColor.RED));
            return true;
        }

        var item = switch (args[2].toLowerCase()) {
            case "knife" -> GameItems.createKnife();
            case "gun" -> GameItems.createGun();
            case "grenade" -> GameItems.createGrenade();
            case "poison" -> GameItems.createPoison();
            case "lockpick" -> GameItems.createLockpick();
            case "crowbar" -> GameItems.createCrowbar();
            case "body_bag" -> GameItems.createBodyBag();
            case "firecracker" -> GameItems.createFirecracker();
            case "note" -> GameItems.createNote();
            case "blackout" -> GameItems.createBlackout();
            default -> null;
        };

        if (item == null) {
            sender.sendMessage(Component.text("未知物品: " + args[2], NamedTextColor.RED));
            return true;
        }

        target.getInventory().addItem(item);
        sender.sendMessage(Component.text("已给予 " + target.getName() + " 物品: " + args[2], NamedTextColor.GREEN));
        return true;
    }

    private boolean handleKill(CommandSender sender, String[] args) {
        if (!plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("当前没有游戏在运行。", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /wathe kill <玩家>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("找不到玩家: " + args[1], NamedTextColor.RED));
            return true;
        }

        plugin.getGameManager().onPlayerDeath(target, null);
        sender.sendMessage(Component.text("已杀死 " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleBlackout(CommandSender sender) {
        if (!plugin.getGameManager().isGameRunning()) {
            sender.sendMessage(Component.text("当前没有游戏在运行。", NamedTextColor.RED));
            return true;
        }
        if (sender instanceof Player player) {
            plugin.getBlackoutManager().startBlackout(player);
        }
        return true;
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private String formatBoundingBox(BoundingBox box) {
        return String.format("(%.0f,%.0f,%.0f) -> (%.0f,%.0f,%.0f)",
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(REQUIRED_PERMISSION)) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("start", "stop", "role", "money", "timer", "shop", "reload", "map", "give", "kill", "blackout"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "role", "money", "give", "kill" -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                }
                case "map" -> completions.addAll(List.of(
                        "setspawn", "setspectatorspawn", "setplayarea", "setreadyarea",
                        "setinsidearea", "setresettemplate", "setplayoffset", "setpasteoffset", "save", "info"
                ));
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "role" -> completions.addAll(WatheRoles.getAllRoles().keySet());
                case "give" -> completions.addAll(List.of("knife", "gun", "grenade", "poison", "lockpick", "crowbar", "body_bag", "firecracker", "note", "blackout"));
                case "map" -> {
                    if (args[1].toLowerCase().startsWith("set") && args[1].toLowerCase().contains("area")) {
                        completions.addAll(List.of("pos1", "pos2"));
                    }
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}

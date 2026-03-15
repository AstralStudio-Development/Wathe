package dev.doctor4t.wathe.bukkit.game;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.api.GameMode;
import dev.doctor4t.wathe.bukkit.api.Role;
import dev.doctor4t.wathe.bukkit.api.WatheRoles;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.integration.BetterHudIntegration;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import dev.doctor4t.wathe.bukkit.player.PlayerManager;
import dev.doctor4t.wathe.bukkit.scenery.SceneryConfig;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class GameManager {
    private static final String INGAME_HIDDEN_NAMETAG_TEAM = "wathe_ingame_hide";
    private static final String DEFAULT_STEVE_HEAD_NAME = "Steve";

    private final WatheBukkit plugin;
    private final PlayerManager playerManager;

    private GameMode currentGameMode;
    private GameState gameState = GameState.WAITING;
    private int timerTicks;
    private ScheduledTask gameTask;
    private List<RoundEndEntry> roundEndEntries = List.of();
    private String roundEndWinner = "";
    private boolean roundEndVisible = false;
    
    // 角色介绍第一行是否显示（1=显示，0=隐藏）
    private int showFirstLine = 0;
    // 角色介绍第二行是否显示（1=显示，0=隐藏）
    private int showSecondLine = 0;
    // 角色介绍第三行是否显示（1=显示，0=隐藏）
    private int showThirdLine = 0;

    public enum GameState {
        WAITING, STARTING, RUNNING, ENDING
    }

    public GameManager(WatheBukkit plugin, PlayerManager playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    public boolean startGame(GameMode gameMode) {
        if (gameState != GameState.WAITING) {
            return false;
        }
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> participants = new ArrayList<>();
        List<Player> outOfReadyPlayers = new ArrayList<>();
        for (Player player : onlinePlayers) {
            if (plugin.getMapConfig().isInReadyArea(player.getLocation())) {
                participants.add(player);
            } else {
                outOfReadyPlayers.add(player);
            }
        }

        if (participants.size() < gameMode.minPlayers()) {
            return false;
        }
        clearRoundEndSnapshot();

        this.currentGameMode = gameMode;
        this.gameState = GameState.STARTING;
        this.timerTicks = gameMode.defaultTimerMinutes() * 60 * 20;

        for (Player player : participants) {
            playerManager.addPlayer(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setCollidable(true);
            player.listPlayer(player);
        }
        applyHiddenNametags(participants);

        for (Player spectator : outOfReadyPlayers) {
            spectator.setGameMode(org.bukkit.GameMode.SPECTATOR);
            spectator.setCollidable(false);
            spectator.teleport(plugin.getMapConfig().getSpectatorSpawnLocation());
            for (Player viewer : onlinePlayers) {
                viewer.unlistPlayer(spectator);
            }
        }

        currentGameMode.initialize(this, participants);

        if (!participants.isEmpty()) {
            var world = participants.getFirst().getWorld();
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101); // 禁用睡觉跳过夜晚
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); // 关闭昼夜循环
            world.setTime(18000); // 固定为夜晚时间
        }


        plugin.preloadMapDoors();
        this.gameState = GameState.RUNNING;

        gameTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick(), 1L, 1L);

        // 开始地图外景动画
        startScenery();

        return true;
    }

    private void startScenery() {
        SceneryConfig config = plugin.getSceneryConfig();
        if (config.isValid()) {
            var sceneryManager = plugin.getSceneryManager();
            sceneryManager.setSpeed(config.getSpeed());
            sceneryManager.setMoveAxis(config.getMoveAxis());
            sceneryManager.loadFromRegion(
                config.getTemplatePos1().getWorld(),
                config.getTemplatePos1(),
                config.getTemplatePos2(),
                config.getDisplayPosition(),
                config.getSegmentCount()
            );
            sceneryManager.startMoving();
        }
    }

    public void stopGame() {
        if (gameState == GameState.WAITING) {
            return;
        }

        this.gameState = GameState.ENDING;
        clearHiddenNametags();

        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        if (currentGameMode != null) {
            currentGameMode.onFinalize(this);
        }

        // 清理对局残留状态
        plugin.getCorpseManager().clearAllCorpses();
        plugin.getPoisonManager().clearAllPoisons();
        plugin.getBlackoutManager().forceEnd();
        plugin.getWallhackManager().stopAllWallhacks();
        plugin.getPsychosisManager().clearAll();
        plugin.getPsychoModeManager().clearAll();
        plugin.getSceneryManager().cleanup();
        plugin.getAmbientSoundManager().stopAllAmbient();

        // 清理世界中残留掉落物
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setCollidable(true);
            player.playerListName(null);
            for (Player target : Bukkit.getOnlinePlayers()) {
                player.listPlayer(target);
            }
            player.getInventory().clear();
            // 清空装备栏
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            // 恢复默认攻击速度
            var attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                attackSpeedAttr.setBaseValue(4.0);
            }
            // 恢复默认跳跃力度
            var jumpStrengthAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jumpStrengthAttr != null) {
                jumpStrengthAttr.setBaseValue(0.42);
            }
            // 清理药水效果与冷却
            player.clearActivePotionEffects();
            for (var material : org.bukkit.Material.values()) {
                if (material.isItem() && player.hasCooldown(material)) {
                    player.setCooldown(material, 0);
                }
            }
        }

        playerManager.clearAllPlayers();
        this.currentGameMode = null;
        this.gameState = GameState.WAITING;
    }

    private void tick() {
        if (gameState != GameState.RUNNING) {
            return;
        }

        if (timerTicks > 0) {
            timerTicks--;
        }

        if (timerTicks % GameConstants.PASSIVE_MONEY_INTERVAL == 0) {
            for (GamePlayer gp : playerManager.getAllPlayers()) {
                if (gp.isAlive()) {
                    gp.addMoney(GameConstants.PASSIVE_MONEY_AMOUNT);
                    Player player = Bukkit.getPlayer(gp.uuid());
                    if (player != null
                            && player.isOnline()
                            && gp.role() == WatheRoles.KILLER
                            && player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
                        plugin.getShopManager().refreshInventoryShopItems(player);
                    }
                }
            }
        }

        refreshTabGameModeMask();

        if (currentGameMode != null) {
            currentGameMode.tick(this);
            currentGameMode.checkWinCondition(this);
        }

        if (timerTicks <= 0) {
            onTimeUp();
        }

        updateTimerDisplay();
    }

    private void onTimeUp() {
        endGameWithWinner("innocent");
    }

    public void endGameWithWinner(String winner) {
        gameState = GameState.ENDING;
        captureRoundEndSnapshot(winner);
        clearHiddenNametags();
        plugin.getPsychoModeManager().clearAll();

        // 游戏结束时立即清理尸体，避免黑屏阶段残留
        plugin.getCorpseManager().clearAllCorpses();

        // 清理世界中的掉落物，避免结算界面被干扰
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }

        // 清空 ActionBar 与玩家物品
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.empty());
            // 清空主背包与护甲栏
            player.getInventory().clear();
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
        }

        // 黑屏过渡参数（与 BlackoutTransitionManager 保持一致）
        final int fadeInTicks = 20;
        final int stayTicks = 40;
        final int fadeOutTicks = 20;
        final int transitionTotalTicks = fadeInTicks + stayTicks + fadeOutTicks;

        // 启动全员黑屏过渡
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        plugin.getBlackoutTransitionManager().startTransitionForAll(allPlayers, fadeInTicks, stayTicks, fadeOutTicks);

        // 在彻底黑屏后传送到出生点并切回冒险模式
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            var spawn = plugin.getMapConfig().getSpawnLocation();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleport(spawn);
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }, fadeInTicks);

        // 渐出结束后播放结算音效，再延迟结束对局
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            showRoundEndSnapshot();
            for (Player player : Bukkit.getOnlinePlayers()) {
                GamePlayer gp = playerManager.getPlayer(player);
                if (gp != null && gp.role() != null) {
                    boolean isWinner = (winner.equals("killer") && gp.role() == WatheRoles.KILLER) ||
                                       (winner.equals("innocent") && gp.role() != WatheRoles.KILLER);
                    if (isWinner) {
                        player.playSound(player.getLocation(), "wathe:ui.piano_win", 10f, 1f);
                    } else {
                        player.playSound(player.getLocation(), "wathe:ui.piano_lose", 10f, 1f);
                    }
                }
            }

            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, stopTask -> {
                stopGame();
                giveRoundEndReplayLetters();
            }, 100L);
        }, transitionTotalTicks);
    }

    private void updateTimerDisplay() {
        int seconds = timerTicks / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
    }

    public void onPlayerDeath(Player victim, Player killer) {
        if (gameState != GameState.RUNNING || currentGameMode == null) {
            return;
        }

        GamePlayer victimGP = playerManager.getPlayer(victim);
        if (victimGP == null || !victimGP.isAlive()) {
            return;
        }

        victimGP.setAlive(false);
        victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
        victim.setCollidable(false);
        spoofTabGameModeAsAlive(victim);
        
        // 非杀手死亡时，若本局未结束则掉落其身上的枪
        if (victimGP.role() != WatheRoles.KILLER) {
            // 对局即将结束时不再处理枪掉落
            long aliveInnocent = playerManager.getAliveInnocentCount();
            long aliveKiller = playerManager.getAliveKillerCount();
            boolean gameWillEnd = aliveKiller == 0 || aliveInnocent == 0;
            
            if (!gameWillEnd) {
                for (ItemStack item : victim.getInventory().getContents()) {
                    if (item != null && "gun".equals(GameItems.getGameItemId(item))) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), GameItems.createGun());
                        break;
                    }
                }
            }
        }

        // 生成尸体
        plugin.getCorpseManager().createCorpse(victim);

        // 清理该玩家的毒药状态
        plugin.getPoisonManager().clearPoison(victim);

        if (killer != null) {
            GamePlayer killerGP = playerManager.getPlayer(killer);
            if (killerGP != null) {
                killerGP.addMoney(GameConstants.MONEY_PER_KILL);

                if (victimGP.role() != null && victimGP.role().innocent()) {
                    addTime(GameConstants.TIME_ON_CIVILIAN_KILL);
                }
            }
        }

        currentGameMode.onPlayerDeath(this, victim, killer);
    }

    private void spoofTabGameModeAsAlive(Player victim) {
        try {
            UserProfile profile = new UserProfile(victim.getUniqueId(), victim.getName());
            WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                    profile,
                    true,
                    victim.getPing(),
                    com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL,
                    Component.text(victim.getName(), NamedTextColor.WHITE),
                    null
            );
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(
                    EnumSet.of(
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
                    ),
                    info
            );
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            }
        } catch (Exception ignored) {
            // If PacketEvents is unavailable, keep default behavior.
        }
    }

    private void refreshTabGameModeMask() {
        for (GamePlayer gp : playerManager.getAllPlayers()) {
            if (gp.isAlive()) {
                continue;
            }
            Player deadPlayer = Bukkit.getPlayer(gp.uuid());
            if (deadPlayer == null || !deadPlayer.isOnline()) {
                continue;
            }
            spoofTabGameModeAsAlive(deadPlayer);
        }
    }

    public void announceRole(Player player, Role role) {
        // 角色介绍文案
        String description = switch (role.id()) {
            case "killer" -> "在时间耗尽前消灭所有平民";
            case "vigilante" -> "消灭任何凶手并保护平民";
            case "civilian" -> "保持安全，坚持到旅程结束";
            default -> "";
        };
        player.playSound(player.getLocation(), "wathe:ui.riser", 10f, 1f);
        
        // 20 tick 时显示第一行并播放音效（pitch 1.25）
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), "wathe:ui.piano", 10f, 1.25f);
            }
            showFirstLine = 1;
        }, 20L);
        
        // 80 tick 时显示第二行并播放音效（pitch 1.5）
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), "wathe:ui.piano", 10f, 1.5f);
            }
            showSecondLine = 1;
        }, 80L);
        
        // 140 tick 时显示第三行并播放音效（pitch 1.75）
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), "wathe:ui.piano", 10f, 1.75f);
            }
            showThirdLine = 1;
        }, 140L);
        
        // 199 tick 时播放收尾音效并隐藏三行文本
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), "wathe:ui.piano_stinger", 10f, 1f);
            }
            showFirstLine = 0;
            showSecondLine = 0;
            showThirdLine = 0;
        }, 199L);
    }
    
    /**
     * @return 第一行是否显示（1=显示，0=隐藏）
     */
    public int getShowFirstLine() {
        return showFirstLine;
    }
    
    /**
     * @return 第二行是否显示（1=显示，0=隐藏）
     */
    public int getShowSecondLine() {
        return showSecondLine;
    }
    
    /**
     * @return 第三行是否显示（1=显示，0=隐藏）
     */
    public int getShowThirdLine() {
        return showThirdLine;
    }

    public void addTime(int ticks) {
        this.timerTicks += ticks;
    }

    private void captureRoundEndSnapshot(String winner) {
        List<RoundEndEntry> snapshot = new ArrayList<>();
        for (GamePlayer gp : playerManager.getAllPlayers()) {
            if (gp.role() == null) {
                continue;
            }
            snapshot.add(new RoundEndEntry(
                    gp.uuid(),
                    gp.name(),
                    resolveRoundEndHeadName(gp.uuid(), gp.name()),
                    gp.role().id(),
                    gp.role().displayName(),
                    !gp.isAlive()
            ));
        }
        snapshot.sort(Comparator
                .comparingInt((RoundEndEntry entry) -> roleSortOrder(entry.roleId()))
                .thenComparing(RoundEndEntry::name, String.CASE_INSENSITIVE_ORDER));
        this.roundEndEntries = List.copyOf(snapshot);
        this.roundEndWinner = winner == null ? "" : winner.toLowerCase();
        // 先缓存结算数据，等待黑屏过渡结束后再显示结算界面
        this.roundEndVisible = false;
    }

    private void showRoundEndSnapshot() {
        this.roundEndVisible = !this.roundEndEntries.isEmpty();
        if (this.roundEndVisible) {
            BetterHudIntegration.showRoundEndPopupForAllOnline();
        }
    }

    private int roleSortOrder(String roleId) {
        return switch (roleId) {
            case "civilian" -> 0;
            case "vigilante" -> 1;
            case "killer" -> 2;
            default -> 3;
        };
    }

    private void clearRoundEndSnapshot() {
        this.roundEndEntries = List.of();
        this.roundEndWinner = "";
        this.roundEndVisible = false;
        BetterHudIntegration.clearRoundEndPopupRequests();
        BetterHudIntegration.clearRoundEndTextureOverrides();
        removeRoundEndReplayLetters();
    }

    private void giveRoundEndReplayLetters() {
        if (roundEndEntries.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeRoundEndReplayLetter(player);
            ItemStack letter = GameItems.createLetter();
            if (letter == null) {
                continue;
            }
            var leftovers = player.getInventory().addItem(letter);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    private void removeRoundEndReplayLetters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeRoundEndReplayLetter(player);
        }
    }

    private void removeRoundEndReplayLetter(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!"letter".equals(GameItems.getGameItemId(stack))) {
                continue;
            }
            player.getInventory().setItem(i, null);
        }
    }

    private String resolveRoundEndHeadName(UUID uuid, String playerName) {
        String officialName = playerName == null ? "" : playerName.trim();
        boolean validOfficialName = officialName.matches("^[A-Za-z0-9_]{3,16}$");

        if (validOfficialName) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                String liveTexture = resolveTextureValueNonBlocking(online);
                if (liveTexture != null && !liveTexture.isBlank()) {
                    BetterHudIntegration.setRoundEndTextureOverride(officialName, liveTexture);
                    return officialName;
                }
            }
            // Do not perform synchronous remote checks on the game thread.
            return officialName;
        }

        String skinRestorerTexture = BetterHudIntegration.resolveSkinRestorerTextureValue(uuid);
        if (skinRestorerTexture != null && !skinRestorerTexture.isBlank()) {
            if (validOfficialName) {
                BetterHudIntegration.setRoundEndTextureOverride(officialName, skinRestorerTexture);
                return officialName;
            }
            return BetterHudIntegration.registerRoundEndTextureValue(skinRestorerTexture);
        }

        String skinRestorerName = BetterHudIntegration.resolveSkinRestorerSkinName(uuid);
        if (skinRestorerName != null) {
            String candidate = skinRestorerName.trim();
            if (candidate.matches("^[A-Za-z0-9_]{3,16}$")) {
                return candidate;
            }
        }

        return DEFAULT_STEVE_HEAD_NAME;
    }

    private String resolveTextureValueNonBlocking(Player player) {
        try {
            var profile = player.getPlayerProfile();
            for (var prop : profile.getProperties()) {
                if (!"textures".equals(prop.getName())) {
                    continue;
                }
                if (prop.getValue() != null && !prop.getValue().isBlank()) {
                    return prop.getValue();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean isRoundEndVisible() {
        return roundEndVisible && !roundEndEntries.isEmpty();
    }

    public String getRoundEndWinner() {
        return roundEndWinner;
    }

    public int getRoundEndCount() {
        return roundEndEntries.size();
    }

    public RoundEndEntry getRoundEndEntry(int indexZeroBased) {
        if (indexZeroBased < 0 || indexZeroBased >= roundEndEntries.size()) {
            return null;
        }
        return roundEndEntries.get(indexZeroBased);
    }

    public RoundEndEntry getRoundEndRoleEntry(String roleId, int indexZeroBased) {
        if (roleId == null || roleId.isBlank() || indexZeroBased < 0) {
            return null;
        }
        int current = 0;
        for (RoundEndEntry entry : roundEndEntries) {
            boolean matches = "innocent".equals(roleId)
                    ? !"killer".equals(entry.roleId())
                    : roleId.equals(entry.roleId());
            if (!matches) {
                continue;
            }
            if (current == indexZeroBased) {
                return entry;
            }
            current++;
        }
        return null;
    }

    public int getRoundEndRoleCount(String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return 0;
        }
        int count = 0;
        for (RoundEndEntry entry : roundEndEntries) {
            boolean matches = "innocent".equals(roleId)
                    ? !"killer".equals(entry.roleId())
                    : roleId.equals(entry.roleId());
            if (matches) {
                count++;
            }
        }
        return count;
    }

    public boolean didPlayerWinRoundEnd(UUID playerId) {
        if (playerId == null || roundEndWinner.isEmpty()) {
            return false;
        }
        for (RoundEndEntry entry : roundEndEntries) {
            if (!entry.uuid().equals(playerId)) {
                continue;
            }
            if ("killer".equals(roundEndWinner)) {
                return "killer".equals(entry.roleId());
            }
            if ("innocent".equals(roundEndWinner)) {
                return !"killer".equals(entry.roleId());
            }
            return false;
        }
        return false;
    }

    public void setTimer(int minutes, int seconds) {
        this.timerTicks = (minutes * 60 + seconds) * 20;
    }

    public boolean isGameRunning() {
        return gameState == GameState.RUNNING;
    }

    public GameState gameState() {
        return gameState;
    }

    public GameMode currentGameMode() {
        return currentGameMode;
    }

    public PlayerManager playerManager() {
        return playerManager;
    }

    public int timerTicks() {
        return timerTicks;
    }

    public WatheBukkit plugin() {
        return plugin;
    }

    public void broadcastMessage(Component message) {
        Bukkit.broadcast(message);
    }

    public void applyHiddenNametagForPlayer(Player player) {
        applyHiddenNametags(List.of(player));
    }

    public void refreshHiddenNametagsForOnlinePlayers() {
        if (!isGameRunning() || Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(INGAME_HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(INGAME_HIDDEN_NAMETAG_TEAM);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        team.setColor(ChatColor.GREEN);

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : onlinePlayers) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
        for (Player player : onlinePlayers) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setCollidable(false);
            } else {
                player.setCollidable(true);
            }
            team.addEntry(player.getName());
            player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
        }
    }

    private void applyHiddenNametags(List<Player> players) {
        if (players.isEmpty() || Bukkit.getScoreboardManager() == null) {
            return;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(INGAME_HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(INGAME_HIDDEN_NAMETAG_TEAM);
        }
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
        team.setColor(ChatColor.GREEN);

        for (Player player : players) {
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setCollidable(false);
            } else {
                player.setCollidable(true);
            }
            team.addEntry(player.getName());
            player.playerListName(Component.text(player.getName(), NamedTextColor.WHITE));
        }
    }

    private void clearHiddenNametags() {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }

        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(INGAME_HIDDEN_NAMETAG_TEAM);
        if (team != null) {
            team.unregister();
        }
    }

    public record RoundEndEntry(
            UUID uuid,
            String name,
            String headName,
            String roleId,
            String roleName,
            boolean dead
    ) {
    }
}

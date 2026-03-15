package dev.doctor4t.wathe.bukkit.integration;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import dev.doctor4t.wathe.bukkit.player.GamePlayer;
import dev.doctor4t.wathe.bukkit.task.TaskType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WathePlaceholderExpansion extends PlaceholderExpansion {

    private static final String KILLER_SCROLL_UNIT = "杀光他们! ";
    private static final int KILLER_SCROLL_REPEAT = 4;
    private static final String KILLER_SCROLL_TEXT = KILLER_SCROLL_UNIT.repeat(KILLER_SCROLL_REPEAT);
    private static final String KILLER_SCROLL_COLOR_PREFIX = "<dark_red>";
    private static final String DEFAULT_ROUND_END_HEAD_KEY = "Steve";
    private static final long KILLER_SCROLL_STEP_MS = 120L;
    private static final double ATTACK_AIM_RANGE = 2.0D;
    private static final double ATTACK_AIM_RAY_SIZE = 0.20D;
    private static final double AIM_SMOOTH_RISE_PER_TICK = 0.10D;
    private static final double AIM_SMOOTH_FALL_PER_TICK = 0.10D;
    private static final double MOOD_ARROW_DECAY_PER_TICK = 0.08D;
    private static final double TASK_TEXT_FADE_IN_PER_TICK = 0.18D;
    private static final double TASK_TEXT_FADE_OUT_PER_TICK = 0.10D;

    private final WatheBukkit plugin;
    private final Map<UUID, String> gunInRangeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> aimInAttackRangeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> aimTargetNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> aimInAttackRangeSmoothCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> aimLastTargetNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> taskStateFadeCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> taskAlphaCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> moodLastBandCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> moodArrowDirCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> moodArrowProgressCache = new ConcurrentHashMap<>();
    private ScheduledTask gunInRangeCacheTask;

    public WathePlaceholderExpansion(WatheBukkit plugin) {
        this.plugin = plugin;
        startGunInRangeCacheTask();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wathe";
    }

    @Override
    public @NotNull String getAuthor() {
        return "doctor4t";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        String lowerParams = params.toLowerCase(Locale.ROOT);
        String roundEndDynamic = resolveRoundEndDynamicPlaceholder(lowerParams);
        if (roundEndDynamic != null) {
            return roundEndDynamic;
        }

        /*
         * Wathe 占位符说明（使用格式：%wathe_<id>%）：
         * - role：角色显示名（带颜色标签）。
         * - role_id：角色内部ID（killer/vigilante/civilian/...）。
         * - role_desc：角色说明文本。
         * - money：玩家当前对局金币。
         * - alive：玩家在对局中是否存活（true/false）。
         * - is_dead / dead：玩家在“正在进行的对局”里是否已死亡（死亡=1，存活/未开局=0）。
         * - timer：对局计时（mm:ss）。
         * - game_running：游戏是否正在进行（true/false）。
         * - game_state：游戏状态（1=进行中，3=倒计时，0=未开始）。
         * - players_alive：存活玩家数量。
         * - players_total：已跟踪的玩家总数。
         * - killer_count：杀手阵营人数。
         * - show_first_line / show_second_line / show_third_line：开场UI三行显示开关。
         * - is_killer：是否为杀手阵营（是=1，否=0）。
         * - has_shop：是否可使用杀手商店（是=1，否=0）。
         * - psycho_mode / is_psycho_mode / killer_psycho：杀手阵营是否有人处于狂暴模式（是=1，否=0）。
         * - psycho_mode_self / is_psycho_mode_self：当前玩家本人是否处于狂暴模式（是=1，否=0，仅杀手阵营可能为1）。
         * - killer_scroll / killer_scroll_text：滚动文本（由4个“杀光他们!”组成并循环滚动）。
         * - in_game：是否被 GamePlayer 跟踪（true/false）。
         * - ready_players：当前处于准备区的在线玩家数量。
         * - online_players：当前在线玩家数量。
         * - countdown：自动开局倒计时秒数。
         * - stamina / sprint_stamina：当前体力值（int；杀手等无限体力角色返回-1）。
         * - stamina_max / sprint_stamina_max：满体力值（int；杀手等无限体力角色返回-1）。
         * - gun_in_range：主手为枪且准星射线可命中存活玩家时返回1，否则0。
         * - mood_state：心情状态（0=无效，1=高，2=中，3=低，4=杀手）。
         * - task_state：任务状态编码
         *   平民/义警：1=吃，2=喝，3=睡，4=外出
         *   杀手阵营：5=吃，6=喝，7=睡，8=外出
         */
        return switch (lowerParams) {
            case "role" -> {
                if (gp == null || gp.role() == null) {
                    yield "无";
                }
                yield switch (gp.role().id()) {
                    case "killer" -> "<red>" + gp.role().name();
                    case "vigilante" -> "<blue>" + gp.role().name();
                    case "civilian" -> "<green>" + gp.role().name();
                    default -> gp.role().name();
                };
            }
            case "role_id" -> {
                if (gp == null || gp.role() == null) {
                    yield "";
                }
                yield gp.role().id();
            }
            case "role_desc" -> {
                if (gp == null || gp.role() == null) {
                    yield "";
                }
                yield switch (gp.role().id()) {
                    case "killer" -> "<red>在时间耗尽前消灭列车上的所有乘客";
                    case "vigilante" -> "<blue>消灭任何凶手并保护平民";
                    case "civilian" -> "<green>保持安全，坚持到旅程结束";
                    default -> "";
                };
            }
            case "money" -> {
                if (gp == null || gp.money() <= 0) {
                    yield "";
                }
                yield String.valueOf(gp.money());
            }
            case "alive" -> {
                if (gp == null) {
                    yield "false";
                }
                yield String.valueOf(gp.isAlive());
            }
            case "is_dead", "dead" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null) {
                    yield "0";
                }
                yield gp.isAlive() ? "0" : "1";
            }
            case "timer" -> {
                if (!plugin.getGameManager().isGameRunning()) {
                    yield "00:00";
                }
                int totalSeconds = plugin.getGameManager().timerTicks() / 20;
                int minutes = totalSeconds / 60;
                int seconds = totalSeconds % 60;
                yield String.format("%02d:%02d", minutes, seconds);
            }
            case "game_running" -> String.valueOf(plugin.getGameManager().isGameRunning());
            case "game_state" -> {
                if (plugin.getGameManager().isGameRunning()) {
                    yield "1";
                } else if (plugin.getAutoStartManager().isCountingDown()) {
                    yield "3";
                }
                yield "0";
            }
            case "round_end_active", "settlement_active", "result_active" ->
                    plugin.getGameManager().isRoundEndVisible() ? "1" : "0";
            case "round_end_winner", "settlement_winner", "result_winner" ->
                    plugin.getGameManager().getRoundEndWinner();
            case "round_end_winner_text", "settlement_winner_text", "result_winner_text" -> {
                String winner = plugin.getGameManager().getRoundEndWinner();
                yield switch (winner) {
                    case "killer" -> "<#C13838>杀手胜利";
                    case "innocent" -> "<#36E51B>乘客胜利";
                    default -> "";
                };
            }
            case "round_end_subtitle_text", "settlement_subtitle_text", "result_subtitle_text" -> {
                if (!plugin.getGameManager().isRoundEndVisible()) {
                    yield "";
                }
                String winner = plugin.getGameManager().getRoundEndWinner();
                yield switch (winner) {
                    case "killer" -> "<white>杀手达成击杀数，他们获胜了";
                    case "innocent" -> "<white>所有杀手已被击倒，乘客胜利";
                    default -> "";
                };
            }
            case "round_end_count", "settlement_count", "result_count" ->
                    String.valueOf(plugin.getGameManager().getRoundEndCount());
            case "round_end_civilian_count" -> String.valueOf(plugin.getGameManager().getRoundEndRoleCount("civilian"));
            case "round_end_vigilante_count" -> String.valueOf(plugin.getGameManager().getRoundEndRoleCount("vigilante"));
            case "round_end_innocent_count" -> String.valueOf(plugin.getGameManager().getRoundEndRoleCount("innocent"));
            case "round_end_killer_count" -> String.valueOf(plugin.getGameManager().getRoundEndRoleCount("killer"));
            case "round_end_did_win", "settlement_did_win", "result_did_win" ->
                    plugin.getGameManager().didPlayerWinRoundEnd(player.getUniqueId()) ? "1" : "0";
            case "players_alive" -> String.valueOf(plugin.getPlayerManager().getAliveCount());
            case "players_total" -> String.valueOf(plugin.getPlayerManager().getTotalCount());
            case "killer_count" -> String.valueOf(plugin.getPlayerManager().getKillerCount());
            case "show_first_line" -> String.valueOf(plugin.getGameManager().getShowFirstLine());
            case "show_second_line" -> String.valueOf(plugin.getGameManager().getShowSecondLine());
            case "show_third_line" -> String.valueOf(plugin.getGameManager().getShowThirdLine());
            case "is_killer" -> {
                if (gp == null || gp.role() == null || !gp.role().isKiller()) {
                    yield "0";
                }
                yield "1";
            }
            case "has_shop" -> {
                if (gp == null || gp.role() == null || !gp.role().isKiller()) {
                    yield "0";
                }
                yield "1";
            }
            case "psycho_mode", "is_psycho_mode", "killer_psycho" -> {
                if (!plugin.getGameManager().isGameRunning()
                        || gp == null
                        || gp.role() == null
                        || !gp.role().isKiller()) {
                    yield "0";
                }
                yield plugin.getPsychoModeManager().isInPsychoMode(player.getUniqueId()) ? "1" : "0";
            }
            case "psycho_mode_self", "is_psycho_mode_self" -> {
                if (!plugin.getGameManager().isGameRunning()
                        || gp == null
                        || gp.role() == null
                        || !gp.role().isKiller()) {
                    yield "0";
                }
                yield plugin.getPsychoModeManager().isInPsychoMode(player.getUniqueId()) ? "1" : "0";
            }
            case "killer_scroll", "killer_scroll_text" -> getKillerScrollText();
            case "in_game" -> String.valueOf(gp != null);
            case "ready_players" -> {
                int count = 0;
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (plugin.getMapConfig().isInReadyArea(p.getLocation())) {
                        count++;
                    }
                }
                yield String.valueOf(count);
            }
            case "online_players" -> String.valueOf(org.bukkit.Bukkit.getOnlinePlayers().size());
            case "countdown" -> String.valueOf(plugin.getAutoStartManager().getCountdownSeconds());
            case "stamina", "sprint_stamina", "stamina_current", "current_stamina" ->
                    String.valueOf(getCurrentStaminaInt(gp));
            case "stamina_max", "sprint_stamina_max", "max_stamina" ->
                    String.valueOf(getMaxStaminaInt(gp));
            case "gun_in_range" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    gunInRangeCache.put(player.getUniqueId(), "0");
                    yield "0";
                }
                yield gunInRangeCache.getOrDefault(player.getUniqueId(), "0");
            }
            case "aim_in_attack_range", "target_in_attack_range" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    aimInAttackRangeCache.put(player.getUniqueId(), "0");
                    yield "0";
                }
                yield aimInAttackRangeCache.getOrDefault(player.getUniqueId(), "0");
            }
            case "aim_in_attack_range_smooth", "target_in_attack_range_smooth" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    aimInAttackRangeSmoothCache.put(player.getUniqueId(), 0.0D);
                    yield "0";
                }
                yield String.format(java.util.Locale.ROOT, "%.3f",
                        aimInAttackRangeSmoothCache.getOrDefault(player.getUniqueId(), 0.0D));
            }
            case "aim_target_id", "target_player_id", "aim_target_name", "target_player_name" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    aimTargetNameCache.put(player.getUniqueId(), "");
                    yield "";
                }
                yield aimTargetNameCache.getOrDefault(player.getUniqueId(), "");
            }
            case "mood_state" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || gp.role() == null) {
                    yield "0";
                }
                if (gp.role().isKiller()) {
                    yield "4";
                }
                float mood = gp.mood();
                if (mood < GameConstants.getDepressiveMoodThreshold()) {
                    yield "3";
                } else if (mood < GameConstants.getMidMoodThreshold()) {
                    yield "2";
                }
                yield "1";
            }
            case "mood_arrow_dir" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    moodArrowDirCache.put(player.getUniqueId(), 0);
                    yield "0";
                }
                yield String.valueOf(moodArrowDirCache.getOrDefault(player.getUniqueId(), 0));
            }
            case "mood_arrow_show" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    moodArrowProgressCache.put(player.getUniqueId(), 0.0D);
                    yield "0";
                }
                yield moodArrowProgressCache.getOrDefault(player.getUniqueId(), 0.0D) > 0.01D ? "1" : "0";
            }
            case "mood_arrow_alpha", "mood_arrow_progress" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
                    moodArrowProgressCache.put(player.getUniqueId(), 0.0D);
                    yield "0";
                }
                yield String.format(Locale.ROOT, "%.3f",
                        moodArrowProgressCache.getOrDefault(player.getUniqueId(), 0.0D));
            }
            case "task_state_fade", "task_state_smooth" ->
                    String.valueOf(taskStateFadeCache.getOrDefault(player.getUniqueId(), 0));
            case "task_alpha", "task_text_alpha" ->
                    String.format(Locale.ROOT, "%.3f", taskAlphaCache.getOrDefault(player.getUniqueId(), 0.0D));
            case "task_show", "task_text_show" ->
                    taskAlphaCache.getOrDefault(player.getUniqueId(), 0.0D) > 0.01D ? "1" : "0";
            case "task_text" -> getTaskText(taskStateFadeCache.getOrDefault(player.getUniqueId(), 0));
            case "task_text_1" -> getTaskText(1);
            case "task_text_2" -> getTaskText(2);
            case "task_text_3" -> getTaskText(3);
            case "task_text_4" -> getTaskText(4);
            case "task_text_5" -> getTaskText(5);
            case "task_text_6" -> getTaskText(6);
            case "task_text_7" -> getTaskText(7);
            case "task_text_8" -> getTaskText(8);
            case "task_state" -> {
                if (!plugin.getGameManager().isGameRunning() || gp == null) {
                    yield "0";
                }
                yield String.valueOf(computeTaskState(gp));
            }
            default -> null;
        };
    }

    private @Nullable String resolveRoundEndDynamicPlaceholder(String params) {
        String roleScoped = resolveRoundEndRoleScopedPlaceholder(params);
        if (roleScoped != null) {
            return roleScoped;
        }

        final String prefix = "round_end_player_";
        if (!params.startsWith(prefix)) {
            return null;
        }

        String body = params.substring(prefix.length());
        int split = body.indexOf('_');
        if (split <= 0 || split >= body.length() - 1) {
            return "";
        }

        int slot;
        try {
            slot = Integer.parseInt(body.substring(0, split));
        } catch (NumberFormatException ignored) {
            return "";
        }
        if (slot <= 0) {
            return "";
        }

        String field = body.substring(split + 1);
        var entry = plugin.getGameManager().getRoundEndEntry(slot - 1);
        boolean exists = entry != null;

        return switch (field) {
            case "show", "exists" -> exists ? "1" : "0";
            case "name", "player_name" -> exists ? entry.name() : "";
            case "head", "head_name", "player_head" -> exists ? entry.headName() : DEFAULT_ROUND_END_HEAD_KEY;
            case "uuid", "player_uuid" -> exists ? entry.uuid().toString() : "";
            case "role", "role_name" -> exists ? entry.roleName() : "";
            case "role_title" -> exists ? roundEndRoleTitle(entry.roleId()) : "";
            case "role_colored", "role_name_colored" -> exists ? roundEndRoleColored(entry.roleId()) : "";
            case "role_id" -> exists ? entry.roleId() : "";
            case "dead", "is_dead" -> exists && entry.dead() ? "1" : "0";
            case "alive", "is_alive" -> exists && !entry.dead() ? "1" : "0";
            default -> "";
        };
    }

    private @Nullable String resolveRoundEndRoleScopedPlaceholder(String params) {
        String roleId;
        String prefix;
        if (params.startsWith("round_end_civilian_")) {
            roleId = "civilian";
            prefix = "round_end_civilian_";
        } else if (params.startsWith("round_end_innocent_")) {
            roleId = "innocent";
            prefix = "round_end_innocent_";
        } else if (params.startsWith("round_end_vigilante_")) {
            roleId = "vigilante";
            prefix = "round_end_vigilante_";
        } else if (params.startsWith("round_end_killer_")) {
            roleId = "killer";
            prefix = "round_end_killer_";
        } else {
            return null;
        }

        String body = params.substring(prefix.length());
        int split = body.indexOf('_');
        if (split <= 0 || split >= body.length() - 1) {
            return "";
        }

        int slot;
        try {
            slot = Integer.parseInt(body.substring(0, split));
        } catch (NumberFormatException ignored) {
            return "";
        }
        if (slot <= 0) {
            return "";
        }

        String field = body.substring(split + 1);
        var entry = plugin.getGameManager().getRoundEndRoleEntry(roleId, slot - 1);
        boolean exists = entry != null;

        return switch (field) {
            case "show", "exists" -> exists ? "1" : "0";
            case "name", "player_name" -> exists ? entry.name() : "";
            case "head", "head_name", "player_head" -> exists ? entry.headName() : DEFAULT_ROUND_END_HEAD_KEY;
            case "uuid", "player_uuid" -> exists ? entry.uuid().toString() : "";
            case "role", "role_name" -> exists ? entry.roleName() : "";
            case "role_title" -> exists ? roundEndRoleTitle(entry.roleId()) : "";
            case "role_colored", "role_name_colored" -> exists ? roundEndRoleColored(entry.roleId()) : "";
            case "role_id" -> exists ? entry.roleId() : "";
            case "dead", "is_dead" -> exists && entry.dead() ? "1" : "0";
            case "alive", "is_alive" -> exists && !entry.dead() ? "1" : "0";
            default -> "";
        };
    }

    private String roundEndRoleTitle(String roleId) {
        return switch (roleId) {
            case "civilian" -> "平民";
            case "vigilante" -> "义警";
            case "killer" -> "杀手";
            default -> "";
        };
    }

    private String roundEndRoleColored(String roleId) {
        return switch (roleId) {
            case "civilian" -> "<#36E51B>平民";
            case "vigilante" -> "<#1B8AE5>义警";
            case "killer" -> "<#C13838>杀手";
            default -> "";
        };
    }

    private String computeGunInRange(Player player) {
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        if (!plugin.getGameManager().isGameRunning() || gp == null || !gp.isAlive()) {
            return "0";
        }

        String heldItemId = GameItems.getGameItemId(player.getInventory().getItemInMainHand());
        if (!"gun".equals(heldItemId)) {
            return "0";
        }

        var result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getLocation().getDirection(),
            20.0,
            entity -> entity instanceof Player p
                && p != player
                && plugin.getPlayerManager().getPlayer(p) != null
                && plugin.getPlayerManager().getPlayer(p).isAlive()
        );
        return result != null && result.getHitEntity() instanceof Player ? "1" : "0";
    }

    private Player computeAttackAimTarget(Player player) {
        var result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getLocation().getDirection(),
            ATTACK_AIM_RANGE,
            ATTACK_AIM_RAY_SIZE,
            entity -> entity instanceof Player p
                && p != player
                && plugin.getPlayerManager().getPlayer(p) != null
                && plugin.getPlayerManager().getPlayer(p).isAlive()
        );
        if (result == null || !(result.getHitEntity() instanceof Player hit)) {
            return null;
        }
        return hit;
    }

    private void startGunInRangeCacheTask() {
        if (gunInRangeCacheTask != null) {
            return;
        }

        // Refresh once per tick on main thread; placeholders only read cache.
        gunInRangeCacheTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> refreshGunInRangeCache(), 1L, 1L);
    }

    private void refreshGunInRangeCache() {
        if (!plugin.getGameManager().isGameRunning()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                gunInRangeCache.put(player.getUniqueId(), "0");
                aimInAttackRangeCache.put(player.getUniqueId(), "0");
                aimTargetNameCache.put(player.getUniqueId(), "");
                aimInAttackRangeSmoothCache.put(player.getUniqueId(), 0.0D);
                aimLastTargetNameCache.remove(player.getUniqueId());
                taskStateFadeCache.put(player.getUniqueId(), 0);
                taskAlphaCache.put(player.getUniqueId(), 0.0D);
                moodLastBandCache.remove(player.getUniqueId());
                moodArrowDirCache.remove(player.getUniqueId());
                moodArrowProgressCache.remove(player.getUniqueId());
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
            if (gp == null || !gp.isAlive()) {
                gunInRangeCache.put(playerId, "0");
                aimInAttackRangeCache.put(playerId, "0");
                aimTargetNameCache.put(playerId, "");
                aimInAttackRangeSmoothCache.put(playerId, 0.0D);
                aimLastTargetNameCache.remove(playerId);
                taskStateFadeCache.put(playerId, 0);
                taskAlphaCache.put(playerId, 0.0D);
                moodLastBandCache.remove(playerId);
                moodArrowDirCache.remove(playerId);
                moodArrowProgressCache.remove(playerId);
                continue;
            }
            gunInRangeCache.put(playerId, computeGunInRange(player));

            Player currentTarget = computeAttackAimTarget(player);
            boolean hardAiming = currentTarget != null;
            if (currentTarget != null) {
                aimLastTargetNameCache.put(playerId, currentTarget.getName());
            }

            double smooth = aimInAttackRangeSmoothCache.getOrDefault(playerId, 0.0D);
            if (hardAiming) {
                smooth = Math.min(1.0D, smooth + AIM_SMOOTH_RISE_PER_TICK);
            } else {
                smooth = Math.max(0.0D, smooth - AIM_SMOOTH_FALL_PER_TICK);
            }
            aimInAttackRangeSmoothCache.put(playerId, smooth);

            aimInAttackRangeCache.put(playerId, hardAiming ? "1" : "0");
            if (hardAiming && currentTarget != null) {
                aimTargetNameCache.put(playerId, currentTarget.getName());
            } else if (smooth > 0.01D) {
                aimTargetNameCache.put(playerId, aimLastTargetNameCache.getOrDefault(playerId, ""));
            } else {
                aimTargetNameCache.put(playerId, "");
                aimLastTargetNameCache.remove(playerId);
            }

            refreshTaskTextFade(playerId, gp);
            refreshMoodArrow(playerId, gp);
        }
    }

    private void refreshTaskTextFade(UUID playerId, GamePlayer gp) {
        if (gp == null || !gp.isAlive()) {
            taskStateFadeCache.put(playerId, 0);
            taskAlphaCache.put(playerId, 0.0D);
            return;
        }

        int currentState = computeTaskState(gp);
        int latchedState = taskStateFadeCache.getOrDefault(playerId, 0);
        double alpha = taskAlphaCache.getOrDefault(playerId, 0.0D);

        if (currentState != 0) {
            latchedState = currentState;
            alpha = Math.min(1.0D, alpha + TASK_TEXT_FADE_IN_PER_TICK);
        } else {
            alpha = Math.max(0.0D, alpha - TASK_TEXT_FADE_OUT_PER_TICK);
            if (alpha <= 0.01D) {
                latchedState = 0;
            }
        }

        taskStateFadeCache.put(playerId, latchedState);
        taskAlphaCache.put(playerId, alpha);
    }

    private void refreshMoodArrow(UUID playerId, GamePlayer gp) {
        if (gp == null || gp.role() == null || gp.role().isKiller()) {
            moodLastBandCache.remove(playerId);
            moodArrowDirCache.put(playerId, 0);
            moodArrowProgressCache.put(playerId, 0.0D);
            return;
        }

        int currentBand = getMoodBand(gp.mood());
        int previousBand = moodLastBandCache.getOrDefault(playerId, currentBand);
        int dir = moodArrowDirCache.getOrDefault(playerId, 0);
        double progress = moodArrowProgressCache.getOrDefault(playerId, 0.0D);

        if (currentBand != previousBand) {
            // 1=up (mood improved), -1=down (mood worsened)
            dir = currentBand < previousBand ? 1 : -1;
            progress = 1.0D;
        } else if (progress > 0.0D) {
            progress = Math.max(0.0D, progress - MOOD_ARROW_DECAY_PER_TICK);
            if (progress <= 0.01D) {
                progress = 0.0D;
                dir = 0;
            }
        }

        moodLastBandCache.put(playerId, currentBand);
        moodArrowDirCache.put(playerId, dir);
        moodArrowProgressCache.put(playerId, progress);
    }

    private int getMoodBand(float mood) {
        if (mood < GameConstants.getDepressiveMoodThreshold()) {
            return 3;
        }
        if (mood < GameConstants.getMidMoodThreshold()) {
            return 2;
        }
        return 1;
    }

    private int computeTaskState(GamePlayer gp) {
        if (gp == null) {
            return 0;
        }
        TaskType task = gp.currentTask();
        if (task == null) {
            return 0;
        }
        boolean isKiller = gp.role() != null && gp.role().isKiller();
        int offset = isKiller ? 4 : 0;
        return switch (task) {
            case EAT -> 1 + offset;
            case DRINK -> 2 + offset;
            case SLEEP -> 3 + offset;
            case OUTSIDE -> 4 + offset;
        };
    }

    private int getMaxStaminaInt(@Nullable GamePlayer gp) {
        if (gp == null || gp.role() == null) {
            return 0;
        }
        int max = gp.role().maxSprintTicks();
        return max < 0 ? -1 : max;
    }

    private int getCurrentStaminaInt(@Nullable GamePlayer gp) {
        int max = getMaxStaminaInt(gp);
        if (max < 0) {
            return -1;
        }
        if (gp == null) {
            return 0;
        }
        int current = Math.round(gp.sprintTicks());
        return Math.max(0, Math.min(max, current));
    }

    private String getTaskText(int state) {
        return switch (state) {
            case 1 -> "你感觉想去吃点零食";
            case 2 -> "你感觉想去喝点饮料";
            case 3 -> "你感觉想去休息一会";
            case 4 -> "你感觉想出去呼吸点新鲜空气";
            case 5 -> "你可以假装去吃点零食";
            case 6 -> "你可以假装去喝点饮料";
            case 7 -> "你可以假装去休息一会";
            case 8 -> "你可以假装出去呼吸点新鲜空气";
            default -> "";
        };
    }

    private String getKillerScrollText() {
        int len = KILLER_SCROLL_TEXT.length();
        if (len <= 1) {
            return KILLER_SCROLL_COLOR_PREFIX + KILLER_SCROLL_TEXT;
        }
        long step = System.currentTimeMillis() / KILLER_SCROLL_STEP_MS;
        int shift = (int) (step % len);
        return KILLER_SCROLL_COLOR_PREFIX
                + KILLER_SCROLL_TEXT.substring(shift)
                + KILLER_SCROLL_TEXT.substring(0, shift);
    }
}

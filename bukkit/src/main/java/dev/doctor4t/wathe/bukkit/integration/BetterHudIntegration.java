package dev.doctor4t.wathe.bukkit.integration;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import kr.toxicity.hud.api.BetterHudAPI;
import kr.toxicity.hud.api.popup.Popup;
import kr.toxicity.hud.api.update.UpdateEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class BetterHudIntegration {

    private static final String TARGET_POPUP_ENABLED_PATH = "betterhud.target-popup-enabled";
    private static final String TARGET_POPUP_ID_PATH = "betterhud.target-popup-id";
    private static final String DEFAULT_TARGET_POPUP_ID = "target_listener";
    private static final String TARGET_LAYOUT_NAME = "wathe_target_layout";
    private static final String ROUND_END_POPUP_ENABLED_PATH = "betterhud.round-end-popup-enabled";
    private static final String ROUND_END_POPUP_ID_PATH = "betterhud.round-end-popup-id";
    private static final String DEFAULT_ROUND_END_POPUP_ID = "round_end_popup";
    private static final String ROUND_END_STEVE_FALLBACK_KEY = "__wathe_steve__";
    private static final String ROUND_END_TEXTURE_KEY_PREFIX = "__wathe_round_skin__";
    private static final String DEFAULT_STEVE_TEXTURE_URL =
            "http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";
    private static final String DEFAULT_STEVE_TEXTURE_VALUE = buildUnsignedTextureValue(DEFAULT_STEVE_TEXTURE_URL);
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern MOJANG_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern MOJANG_VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final long POPUP_WARN_GRACE_MS = 30_000L;
    private static final String ROUND_END_LAYOUT_NAME = "wathe_round_end_layout";
    private static final String ROUND_END_HEAD_NAME = "wathe_round_head";
    private static final int ROUND_END_CIVILIAN_SLOTS = 12;
    private static final int ROUND_END_VIGILANTE_SLOTS = 4;
    private static final int ROUND_END_KILLER_SLOTS = 4;
    private static boolean enabled = false;
    private static boolean listenerRegistered = false;
    private static WatheBukkit plugin;
    private static ScheduledTask targetPopupTask;
    private static ScheduledTask roundEndPopupTask;
    private static final Map<UUID, Boolean> targetPopupShown = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> roundEndPopupShown = new ConcurrentHashMap<>();
    private static final Set<UUID> roundEndPopupRequested = ConcurrentHashMap.newKeySet();
    private static final Set<String> missingPopupWarned = ConcurrentHashMap.newKeySet();
    private static final Set<String> missingRoundEndPopupWarned = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> roundEndTextureOverrides = new ConcurrentHashMap<>();
    private static final Map<String, String> playerNameTextureCache = new ConcurrentHashMap<>();
    private static long popupWarnAvailableAtMs = 0L;
    private static boolean roundEndSkinProviderRegistered = false;
    private static int targetPopupSelfHealAttempts = 0;
    private static long lastTargetPopupSelfHealMs = 0L;
    private static long targetPopupReloadGraceUntilMs = 0L;

    public static void init(WatheBukkit pluginInstance) {
        plugin = pluginInstance;
        enabled = Bukkit.getPluginManager().getPlugin("BetterHud") != null;

        if (enabled) {
            plugin.getComponentLogger().info("BetterHud integration enabled!");
            popupWarnAvailableAtMs = System.currentTimeMillis() + POPUP_WARN_GRACE_MS;
            ensureTargetUiTemplates();
            ensureRoundEndUiTemplates();
            registerCustomListener();
            registerRoundEndSkinFallbackProvider();
            startTargetPopupTask();
            startRoundEndPopupTask();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void shutdown() {
        if (targetPopupTask != null) {
            targetPopupTask.cancel();
            targetPopupTask = null;
        }
        if (roundEndPopupTask != null) {
            roundEndPopupTask.cancel();
            roundEndPopupTask = null;
        }
        hideAllTargetPopups();
        hideAllRoundEndPopups();
        targetPopupShown.clear();
        roundEndPopupShown.clear();
        roundEndPopupRequested.clear();
        missingPopupWarned.clear();
        missingRoundEndPopupWarned.clear();
        roundEndTextureOverrides.clear();
        playerNameTextureCache.clear();
        targetPopupSelfHealAttempts = 0;
        lastTargetPopupSelfHealMs = 0L;
        targetPopupReloadGraceUntilMs = 0L;
    }

    private static void registerCustomListener() {
        if (listenerRegistered || plugin == null) {
            return;
        }

        try {
            BetterHudAPI.inst().getListenerManager().addListener("wathe", yaml -> update -> hudPlayer -> {
                if (!(hudPlayer.handle() instanceof Player player)) {
                    return 0D;
                }

                String key = yaml.getAsString("key", "game_running").toLowerCase(Locale.ROOT);
                var gp = plugin.getPlayerManager().getPlayer(player);

                return switch (key) {
                    case "game_running" -> plugin.getGameManager().isGameRunning() ? 1D : 0D;
                    case "in_game" -> gp != null ? 1D : 0D;
                    case "alive" -> gp != null && gp.isAlive() ? 1D : 0D;
                    case "dead" -> gp != null && !gp.isAlive() ? 1D : 0D;
                    case "wathe_aim_in_attack_range", "aim_in_attack_range" ->
                            "1".equals(PlaceholderAPI.setPlaceholders(player, "%wathe_aim_in_attack_range%")) ? 1D : 0D;
                    case "wathe_aim_in_attack_range_smooth", "aim_in_attack_range_smooth" ->
                            parseDoubleOrZero(PlaceholderAPI.setPlaceholders(player, "%wathe_aim_in_attack_range_smooth%"));
                    case "is_killer", "killer" ->
                            gp != null && gp.role() != null && gp.role().isKiller() ? 1D : 0D;
                    case "psycho_mode", "is_psycho_mode" ->
                            gp != null
                                    && gp.role() != null
                                    && gp.role().isKiller()
                                    && plugin.getPsychoModeManager().isInPsychoMode(player.getUniqueId()) ? 1D : 0D;
                    default -> 0D;
                };
            });

            listenerRegistered = true;
            plugin.getComponentLogger().info("BetterHud custom listener 'wathe' registered.");
        } catch (Throwable throwable) {
            plugin.getComponentLogger().warn("Failed to register BetterHud custom listener: " + throwable.getMessage());
        }
    }

    private static double parseDoubleOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static void startTargetPopupTask() {
        if (targetPopupTask != null || plugin == null || !enabled) {
            return;
        }
        targetPopupTask = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> tickTargetPopup(), 40L, 1L);
    }

    private static void startRoundEndPopupTask() {
        if (roundEndPopupTask != null || plugin == null || !enabled) {
            return;
        }
        roundEndPopupTask = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, task -> tickRoundEndPopup(), 40L, 1L);
    }

    private static void tickTargetPopup() {
        if (plugin == null || !enabled) {
            return;
        }
        if (!plugin.getConfig().getBoolean(TARGET_POPUP_ENABLED_PATH, true)) {
            hideAllTargetPopups();
            return;
        }

        var popupManager = BetterHudAPI.inst().getPopupManager();
        Set<String> loadedNames = popupManager.getAllNames();
        if (loadedNames.isEmpty()) {
            return;
        }

        String popupId = getTargetPopupId();
        if (popupId.isEmpty()) {
            hideAllTargetPopups();
            return;
        }

        var popup = resolvePopup(popupId);
        if (popup == null) {
            long now = System.currentTimeMillis();
            if (now < popupWarnAvailableAtMs) {
                return;
            }
            if (now < targetPopupReloadGraceUntilMs) {
                return;
            }
            if (shouldRetryTargetPopupSelfHeal()) {
                rewriteTargetUiTemplates();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "betterhud reload");
                targetPopupSelfHealAttempts++;
                lastTargetPopupSelfHealMs = now;
                targetPopupReloadGraceUntilMs = now + 8_000L;
                plugin.getComponentLogger().warn("BetterHud target popup missing. Rewrote target templates and triggered '/betterhud reload' (attempt "
                        + targetPopupSelfHealAttempts + "/3).");
                return;
            }
            if (now >= popupWarnAvailableAtMs && missingPopupWarned.add(popupId)) {
                plugin.getComponentLogger().warn("BetterHud popup not found: " + popupId
                        + " | loaded=" + loadedNames);
            }
            hideAllTargetPopups();
            return;
        }
        missingPopupWarned.clear();
        targetPopupSelfHealAttempts = 0;
        targetPopupReloadGraceUntilMs = 0L;

        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            onlinePlayers.add(playerId);

            double aimSmooth = parseDoubleOrZero(
                    PlaceholderAPI.setPlaceholders(player, "%wathe_aim_in_attack_range_smooth%"));
            boolean aiming = aimSmooth > 0.01D;
            boolean shown = targetPopupShown.getOrDefault(playerId, false);
            var hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(playerId);
            if (hudPlayer == null) {
                targetPopupShown.remove(playerId);
                continue;
            }

            if (aiming) {
                if (!shown) {
                    var updater = popup.show(UpdateEvent.EMPTY, hudPlayer);
                    targetPopupShown.put(playerId, updater != null);
                }
            } else if (shown) {
                popup.hide(hudPlayer);
                targetPopupShown.put(playerId, false);
            }
        }

        targetPopupShown.keySet().removeIf(id -> !onlinePlayers.contains(id));
    }

    private static void tickRoundEndPopup() {
        if (plugin == null || !enabled) {
            return;
        }
        if (!plugin.getConfig().getBoolean(ROUND_END_POPUP_ENABLED_PATH, true)) {
            hideAllRoundEndPopups();
            return;
        }

        var popupManager = BetterHudAPI.inst().getPopupManager();
        Set<String> loadedNames = popupManager.getAllNames();
        if (loadedNames.isEmpty()) {
            return;
        }

        String popupId = getRoundEndPopupId();
        if (popupId.isEmpty()) {
            hideAllRoundEndPopups();
            return;
        }

        var popup = resolvePopup(popupId);
        if (popup == null) {
            if (System.currentTimeMillis() >= popupWarnAvailableAtMs && missingRoundEndPopupWarned.add(popupId)) {
                plugin.getComponentLogger().warn("BetterHud popup not found: " + popupId
                        + " | loaded=" + loadedNames);
            }
            hideAllRoundEndPopups();
            return;
        }
        missingRoundEndPopupWarned.clear();

        boolean roundEndAvailable = plugin.getGameManager().isRoundEndVisible();
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            onlinePlayers.add(playerId);
            boolean shouldShow = roundEndAvailable && roundEndPopupRequested.contains(playerId);
            boolean shown = roundEndPopupShown.getOrDefault(playerId, false);
            var hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(playerId);
            if (hudPlayer == null) {
                roundEndPopupShown.remove(playerId);
                continue;
            }

            if (shouldShow) {
                if (!shown) {
                    var updater = popup.show(UpdateEvent.EMPTY, hudPlayer);
                    roundEndPopupShown.put(playerId, updater != null);
                }
            } else if (shown) {
                popup.hide(hudPlayer);
                roundEndPopupShown.put(playerId, false);
            }
        }

        roundEndPopupShown.keySet().removeIf(id -> !onlinePlayers.contains(id));
        roundEndPopupRequested.removeIf(id -> !onlinePlayers.contains(id));
    }

    private static void hideAllTargetPopups() {
        if (plugin == null || !enabled) {
            return;
        }
        if (BetterHudAPI.inst().getPopupManager().getAllNames().isEmpty()) {
            return;
        }
        String popupId = getTargetPopupId();
        if (popupId.isEmpty()) {
            return;
        }
        var popup = resolvePopup(popupId);
        if (popup == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(player.getUniqueId());
            if (hudPlayer != null) {
                popup.hide(hudPlayer);
            }
        }
    }

    private static void hideAllRoundEndPopups() {
        if (plugin == null || !enabled) {
            return;
        }
        if (BetterHudAPI.inst().getPopupManager().getAllNames().isEmpty()) {
            return;
        }
        String popupId = getRoundEndPopupId();
        if (popupId.isEmpty()) {
            return;
        }
        var popup = resolvePopup(popupId);
        if (popup == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var hudPlayer = BetterHudAPI.inst().getPlayerManager().getHudPlayer(player.getUniqueId());
            if (hudPlayer != null) {
                popup.hide(hudPlayer);
            }
        }
    }

    public static void clearRoundEndPopupRequests() {
        roundEndPopupRequested.clear();
        roundEndPopupShown.clear();
        hideAllRoundEndPopups();
    }

    public static void showRoundEndPopupForAllOnline() {
        if (!enabled || plugin == null) {
            return;
        }
        roundEndPopupRequested.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            roundEndPopupRequested.add(player.getUniqueId());
        }
    }

    public static boolean toggleRoundEndPopup(Player player) {
        if (player == null || !enabled || plugin == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (roundEndPopupRequested.remove(playerId)) {
            return false;
        }
        roundEndPopupRequested.add(playerId);
        return true;
    }

    private static String getTargetPopupId() {
        if (plugin == null) {
            return "";
        }
        String configured = plugin.getConfig().getString(TARGET_POPUP_ID_PATH, DEFAULT_TARGET_POPUP_ID);
        return configured == null ? "" : configured.trim();
    }

    private static String getRoundEndPopupId() {
        if (plugin == null) {
            return "";
        }
        String configured = plugin.getConfig().getString(ROUND_END_POPUP_ID_PATH, DEFAULT_ROUND_END_POPUP_ID);
        return configured == null ? "" : configured.trim();
    }

    private static Popup resolvePopup(String configuredId) {
        var popupManager = BetterHudAPI.inst().getPopupManager();
        Popup direct = popupManager.getPopup(configuredId);
        if (direct != null) {
            return direct;
        }

        String lowerId = configuredId.toLowerCase(Locale.ROOT);
        for (String name : popupManager.getAllNames()) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (lowerName.equals(lowerId) || lowerName.endsWith(":" + lowerId)) {
                Popup popup = popupManager.getPopup(name);
                if (popup != null) {
                    return popup;
                }
            }
        }
        return null;
    }

    private static void ensureRoundEndUiTemplates() {
        if (plugin == null || !enabled) {
            return;
        }
        try {
            var betterHudPlugin = Bukkit.getPluginManager().getPlugin("BetterHud");
            if (betterHudPlugin == null) {
                return;
            }

            Path betterHudData = betterHudPlugin.getDataFolder().toPath();
            if (!Files.exists(betterHudData)) {
                return;
            }

            String popupId = getRoundEndPopupId();
            if (popupId.isEmpty()) {
                popupId = DEFAULT_ROUND_END_POPUP_ID;
            }

            Path popupFile = betterHudData.resolve("popups").resolve(popupId + ".yml");
            writeAlways(popupFile, buildRoundEndPopupYaml(popupId));

            Path layoutFile = betterHudData.resolve("layouts").resolve(ROUND_END_LAYOUT_NAME + ".yml");
            writeAlways(layoutFile, buildRoundEndLayoutYaml());

            Path headFile = betterHudData.resolve("heads").resolve(ROUND_END_HEAD_NAME + ".yml");
            writeAlways(headFile, buildRoundEndHeadYaml());
        } catch (Throwable throwable) {
            plugin.getComponentLogger().warn(
                    "Failed to create BetterHud round-end templates: " + throwable.getMessage());
        }
    }

    private static void ensureTargetUiTemplates() {
        if (plugin == null || !enabled) {
            return;
        }
        try {
            var betterHudPlugin = Bukkit.getPluginManager().getPlugin("BetterHud");
            if (betterHudPlugin == null) {
                return;
            }

            Path betterHudData = betterHudPlugin.getDataFolder().toPath();
            if (!Files.exists(betterHudData)) {
                return;
            }

            String popupId = getTargetPopupId();
            if (popupId.isEmpty()) {
                popupId = DEFAULT_TARGET_POPUP_ID;
            }

            boolean wroteAny = false;
            Path popupFile = betterHudData.resolve("popups").resolve(popupId + ".yml");
            wroteAny |= writeIfMissing(popupFile, buildTargetPopupYaml(popupId));

            Path layoutFile = betterHudData.resolve("layouts").resolve(TARGET_LAYOUT_NAME + ".yml");
            wroteAny |= writeIfMissing(layoutFile, buildTargetLayoutYaml());
            if (Files.exists(layoutFile)) {
                try {
                    String current = Files.readString(layoutFile, StandardCharsets.UTF_8);
                    if (!current.contains("wathe_aim_in_attack_range_smooth")) {
                        writeAlways(layoutFile, buildTargetLayoutYaml());
                        wroteAny = true;
                        plugin.getComponentLogger().info(
                                "BetterHud target layout upgraded to smooth fade version.");
                    }
                } catch (IOException ignored) {
                    // Keep existing layout if read failed.
                }
            }

            if (wroteAny) {
                plugin.getComponentLogger().info(
                        "BetterHud target templates created. Run '/betterhud reload' to load them.");
            }
        } catch (Throwable throwable) {
            plugin.getComponentLogger().warn(
                    "Failed to create BetterHud target templates: " + throwable.getMessage());
        }
    }

    private static void rewriteTargetUiTemplates() {
        if (plugin == null || !enabled) {
            return;
        }
        try {
            var betterHudPlugin = Bukkit.getPluginManager().getPlugin("BetterHud");
            if (betterHudPlugin == null) {
                return;
            }

            Path betterHudData = betterHudPlugin.getDataFolder().toPath();
            if (!Files.exists(betterHudData)) {
                return;
            }

            String popupId = getTargetPopupId();
            if (popupId.isEmpty()) {
                popupId = DEFAULT_TARGET_POPUP_ID;
            }

            Path popupFile = betterHudData.resolve("popups").resolve(popupId + ".yml");
            writeAlways(popupFile, buildTargetPopupYaml(popupId));

            Path layoutFile = betterHudData.resolve("layouts").resolve(TARGET_LAYOUT_NAME + ".yml");
            writeAlways(layoutFile, buildTargetLayoutYaml());
        } catch (Throwable throwable) {
            plugin.getComponentLogger().warn(
                    "Failed to rewrite BetterHud target templates: " + throwable.getMessage());
        }
    }

    private static boolean shouldRetryTargetPopupSelfHeal() {
        if (targetPopupSelfHealAttempts >= 3) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now - lastTargetPopupSelfHealMs >= 10_000L;
    }

    private static boolean writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return false;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return true;
    }

    private static void writeAlways(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String buildRoundEndHeadYaml() {
        return """
                wathe_round_head:
                  pixel: 2
                """;
    }

    private static void registerRoundEndSkinFallbackProvider() {
        if (plugin == null || roundEndSkinProviderRegistered) {
            return;
        }

        try {
            Class<?> managerClass = Class.forName("kr.toxicity.hud.manager.PlayerHeadManager");
            Object manager = managerClass.getField("INSTANCE").get(null);
            Class<?> providerClass = Class.forName("kr.toxicity.hud.player.head.PlayerSkinProvider");

            Object providerProxy = Proxy.newProxyInstance(
                    providerClass.getClassLoader(),
                    new Class<?>[]{providerClass},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("provide".equals(methodName) && args != null && args.length == 1 && args[0] instanceof String key) {
                            return resolveHeadTextureValue(key);
                        }
                        if ("toString".equals(methodName)) {
                            return "WatheRoundEndSteveFallbackProvider";
                        }
                        if ("hashCode".equals(methodName)) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(methodName) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        return null;
                    }
            );

            managerClass.getMethod("addSkinProvider", providerClass).invoke(manager, providerProxy);
            roundEndSkinProviderRegistered = true;
            plugin.getComponentLogger().info("BetterHud Steve fallback skin provider registered.");
        } catch (Throwable throwable) {
            plugin.getComponentLogger().warn("Failed to register BetterHud Steve fallback skin provider: "
                    + throwable.getMessage());
        }
    }

    private static String buildUnsignedTextureValue(String textureUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String resolveHeadTextureValue(String key) {
        if (key == null || key.isBlank()) {
            return DEFAULT_STEVE_TEXTURE_VALUE;
        }
        if (ROUND_END_STEVE_FALLBACK_KEY.equalsIgnoreCase(key)) {
            return DEFAULT_STEVE_TEXTURE_VALUE;
        }
        String override = roundEndTextureOverrides.get(key);
        if (override != null && !override.isBlank()) {
            return override;
        }
        if (key.startsWith(ROUND_END_TEXTURE_KEY_PREFIX)) {
            return DEFAULT_STEVE_TEXTURE_VALUE;
        }
        if (!PLAYER_NAME_PATTERN.matcher(key).matches()) {
            return DEFAULT_STEVE_TEXTURE_VALUE;
        }

        String normalized = key.toLowerCase(Locale.ROOT);
        String cached = playerNameTextureCache.get(normalized);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        String fetched = fetchTextureByPlayerName(key);
        if (fetched == null || fetched.isBlank()) {
            fetched = DEFAULT_STEVE_TEXTURE_VALUE;
        }
        playerNameTextureCache.put(normalized, fetched);
        return fetched;
    }

    private static String fetchTextureByPlayerName(String playerName) {
        try {
            String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            HttpRequest profileRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> profileResponse = HTTP_CLIENT.send(profileRequest, HttpResponse.BodyHandlers.ofString());
            if (profileResponse.statusCode() != 200) {
                return null;
            }

            var idMatcher = MOJANG_ID_PATTERN.matcher(profileResponse.body());
            if (!idMatcher.find()) {
                return null;
            }
            String uuidNoDash = idMatcher.group(1);

            HttpRequest sessionRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDash + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> sessionResponse = HTTP_CLIENT.send(sessionRequest, HttpResponse.BodyHandlers.ofString());
            if (sessionResponse.statusCode() != 200) {
                return null;
            }

            var valueMatcher = MOJANG_VALUE_PATTERN.matcher(sessionResponse.body());
            if (!valueMatcher.find()) {
                return null;
            }
            return valueMatcher.group(1);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String registerRoundEndTextureValue(String textureValue) {
        if (textureValue == null || textureValue.isBlank()) {
            return ROUND_END_STEVE_FALLBACK_KEY;
        }
        String key = ROUND_END_TEXTURE_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        roundEndTextureOverrides.put(key, textureValue);
        return key;
    }

    public static void clearRoundEndTextureOverrides() {
        roundEndTextureOverrides.clear();
    }

    public static void setRoundEndTextureOverride(String key, String textureValue) {
        if (key == null || key.isBlank() || textureValue == null || textureValue.isBlank()) {
            return;
        }
        roundEndTextureOverrides.put(key, textureValue);
    }

    public static boolean canResolveSkinByPlayerName(String playerName) {
        if (playerName == null) {
            return false;
        }
        String candidate = playerName.trim();
        if (!PLAYER_NAME_PATTERN.matcher(candidate).matches()) {
            return false;
        }

        String normalized = candidate.toLowerCase(Locale.ROOT);
        String cached = playerNameTextureCache.get(normalized);
        if (cached != null) {
            return !DEFAULT_STEVE_TEXTURE_VALUE.equals(cached);
        }

        String fetched = fetchTextureByPlayerName(candidate);
        if (fetched == null || fetched.isBlank()) {
            playerNameTextureCache.put(normalized, DEFAULT_STEVE_TEXTURE_VALUE);
            return false;
        }

        playerNameTextureCache.put(normalized, fetched);
        return true;
    }

    public static String resolveSkinRestorerSkinName(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        var skinPlugin = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
        if (skinPlugin == null || !skinPlugin.isEnabled()) {
            return null;
        }

        try {
            Class<?> providerClass = loadSkinRestorerClass(skinPlugin, "net.skinsrestorer.api.SkinsRestorerProvider");
            Method getMethod = providerClass.getMethod("get");
            Object skinsRestorer = getMethod.invoke(null);
            if (skinsRestorer == null) {
                return null;
            }

            Object playerStorage = tryInvokeNoArgs(skinsRestorer, "getPlayerStorage");
            if (playerStorage == null) {
                return null;
            }

            Object result = tryInvoke(playerStorage, "getSkinIdOfPlayer", UUID.class, playerUuid);
            if (result == null) {
                result = tryInvoke(playerStorage, "getSkinName", UUID.class, playerUuid);
            }
            if (result == null) {
                result = tryInvoke(playerStorage, "getSkinNameForPlayer", UUID.class, playerUuid);
            }

            if (result == null) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    result = tryInvoke(playerStorage, "getSkinIdOfPlayer", Player.class, player);
                    if (result == null) {
                        result = tryInvoke(playerStorage, "getSkinName", Player.class, player);
                    }
                    if (result == null) {
                        result = tryInvoke(playerStorage, "getSkinNameForPlayer", Player.class, player);
                    }
                }
            }

            String extracted = extractSkinName(result);
            if (extracted == null || extracted.isBlank()) {
                return null;
            }
            return extracted.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveSkinRestorerTextureValue(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        var skinPlugin = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
        if (skinPlugin == null || !skinPlugin.isEnabled()) {
            return null;
        }

        try {
            Class<?> providerClass = loadSkinRestorerClass(skinPlugin, "net.skinsrestorer.api.SkinsRestorerProvider");
            Class<?> skinIdentifierClass = loadSkinRestorerClass(skinPlugin, "net.skinsrestorer.api.property.SkinIdentifier");
            Method getMethod = providerClass.getMethod("get");
            Object skinsRestorer = getMethod.invoke(null);
            if (skinsRestorer == null) {
                return null;
            }

            Object playerStorage = tryInvokeNoArgs(skinsRestorer, "getPlayerStorage");
            Object skinStorage = tryInvokeNoArgs(skinsRestorer, "getSkinStorage");
            if (playerStorage == null || skinStorage == null) {
                return null;
            }

            // First try direct player property from SkinRestorer storage.
            Object directPropertyOptional = tryInvoke(playerStorage, "getSkinOfPlayer", UUID.class, playerUuid);
            Object directProperty = unwrapOptional(directPropertyOptional);
            if (directProperty != null) {
                Object valueObj = tryInvokeNoArgs(directProperty, "getValue");
                if (valueObj instanceof String value && !value.isBlank()) {
                    return value;
                }
            }

            Object skinIdOptional = tryInvoke(playerStorage, "getSkinIdOfPlayer", UUID.class, playerUuid);
            Object skinIdentifier = unwrapOptional(skinIdOptional);
            if (skinIdentifier == null) {
                return null;
            }

            Object propertyOptional = tryInvoke(skinStorage, "getSkinDataByIdentifier", skinIdentifierClass, skinIdentifier);
            Object skinProperty = unwrapOptional(propertyOptional);
            if (skinProperty == null) {
                return null;
            }

            Object valueObj = tryInvokeNoArgs(skinProperty, "getValue");
            if (valueObj instanceof String value && !value.isBlank()) {
                return value;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> loadSkinRestorerClass(org.bukkit.plugin.Plugin skinPlugin, String className)
            throws ClassNotFoundException {
        ClassLoader loader = skinPlugin.getClass().getClassLoader();
        return Class.forName(className, false, loader);
    }

    private static Object tryInvokeNoArgs(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryInvoke(Object target, String methodName, Class<?> argType, Object arg) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, argType);
            return method.invoke(target, arg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractSkinName(Object source) {
        if (source == null) {
            return null;
        }
        Object unwrapped = unwrapOptional(source);
        if (unwrapped == null) {
            return null;
        }
        if (unwrapped != source) {
            return extractSkinName(unwrapped);
        }
        if (source instanceof String str) {
            return str;
        }

        Object bySkinName = tryInvokeNoArgs(source, "getSkinName");
        if (bySkinName instanceof String str && !str.isBlank()) {
            return str;
        }

        Object byIdentifier = tryInvokeNoArgs(source, "getIdentifier");
        if (byIdentifier instanceof String str && !str.isBlank()) {
            return str;
        }

        return null;
    }

    private static Object unwrapOptional(Object source) {
        if (source instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return source;
    }

    private static String buildTargetPopupYaml(String popupId) {
        StringBuilder sb = new StringBuilder();
        sb.append(popupId).append(":\n");
        sb.append("  unique: true\n");
        sb.append("  key-mapping: true\n");
        sb.append("  duration: 1200\n");
        sb.append("  layouts:\n");
        sb.append("    1:\n");
        sb.append("      name: ").append(TARGET_LAYOUT_NAME).append('\n');
        sb.append("      gui:\n");
        sb.append("        x: 50\n");
        sb.append("        y: 50\n");
        sb.append("      pixel:\n");
        sb.append("        x: 0\n");
        sb.append("        y: 0\n");
        return sb.toString();
    }

    private static String buildTargetLayoutYaml() {
        StringBuilder sb = new StringBuilder();
        sb.append(TARGET_LAYOUT_NAME).append(":\n");
        sb.append("  texts:\n");
        double[] opacitySteps = new double[]{0.12D, 0.24D, 0.36D, 0.48D, 0.60D, 0.72D, 0.86D, 1.00D};
        double stepSize = 1.0D / 8.0D;
        int idBase = 101;
        for (int i = 0; i < opacitySteps.length; i++) {
            int id = idBase + i;
            double lower = i * stepSize;
            double upper = (i + 1) * stepSize;
            sb.append("    ").append(id).append(":\n");
            sb.append("      name: example_font\n");
            sb.append("      pattern: \"<white>[papi:wathe_aim_target_name]\"\n");
            sb.append("      align: center\n");
            sb.append("      x: 0\n");
            sb.append("      y: 10\n");
            sb.append("      scale: 0.5\n");
            sb.append("      outline: true\n");
            sb.append("      opacity: ")
                    .append(String.format(Locale.ROOT, "%.2f", opacitySteps[i]))
                    .append('\n');
            sb.append("      conditions:\n");
            sb.append("        1:\n");
            sb.append("          first: (number)papi:wathe_aim_in_attack_range_smooth\n");
            if (i == 0) {
                sb.append("          second: ")
                        .append(String.format(Locale.ROOT, "%.3f", upper))
                        .append('\n');
                sb.append("          operation: '<'\n");
            } else if (i == opacitySteps.length - 1) {
                sb.append("          second: ")
                        .append(String.format(Locale.ROOT, "%.3f", lower))
                        .append('\n');
                sb.append("          operation: '>='\n");
            } else {
                sb.append("          second: ")
                        .append(String.format(Locale.ROOT, "%.3f", lower))
                        .append('\n');
                sb.append("          operation: '>='\n");
                sb.append("        2:\n");
                sb.append("          first: (number)papi:wathe_aim_in_attack_range_smooth\n");
                sb.append("          second: ")
                        .append(String.format(Locale.ROOT, "%.3f", upper))
                        .append('\n');
                sb.append("          operation: '<'\n");
            }
        }
        return sb.toString();
    }

    private static String buildRoundEndPopupYaml(String popupId) {
        StringBuilder sb = new StringBuilder();
        sb.append(popupId).append(":\n");
        sb.append("  unique: true\n");
        sb.append("  key-mapping: false\n");
        sb.append("  duration: 12000\n");
        sb.append("  layouts:\n");
        sb.append("    1:\n");
        sb.append("      name: ").append(ROUND_END_LAYOUT_NAME).append('\n');
        sb.append("      gui:\n");
        sb.append("        x: 50\n");
        sb.append("        y: 50\n");
        sb.append("      pixel:\n");
        sb.append("        x: 0\n");
        sb.append("        y: 0\n");
        return sb.toString();
    }

    private static String buildRoundEndLayoutYaml() {
        StringBuilder sb = new StringBuilder();
        StringBuilder heads = new StringBuilder();
        StringBuilder texts = new StringBuilder();
        final int panelOffsetX = 30;

        sb.append(ROUND_END_LAYOUT_NAME).append(":\n");

        texts.append("  texts:\n");
        texts.append("    1:\n");
        texts.append("      name: example_font\n");
        texts.append("      pattern: \"[papi:wathe_round_end_winner_text]\"\n");
        texts.append("      align: center\n");
        texts.append("      x: 0\n");
        texts.append("      y: -108\n");
        texts.append("      scale: 1.60\n");
        texts.append("      outline: true\n");
        texts.append("    4:\n");
        texts.append("      name: example_font\n");
        texts.append("      pattern: \"[papi:wathe_round_end_subtitle_text]\"\n");
        texts.append("      align: center\n");
        texts.append("      x: 0\n");
        texts.append("      y: -76\n");
        texts.append("      scale: 0.78\n");
        texts.append("      outline: true\n");
        texts.append("    2:\n");
        texts.append("      name: example_font\n");
        texts.append("      pattern: \"<#36E51B>\\u5e73\\u6c11\"\n");
        texts.append("      align: center\n");
        texts.append("      x: ").append(-78 + panelOffsetX).append('\n');
        texts.append("      y: -52\n");
        texts.append("      scale: 0.48\n");
        texts.append("      outline: true\n");
        texts.append("    3:\n");
        texts.append("      name: example_font\n");
        texts.append("      pattern: \"<#C13838>\\u6740\\u624b\"\n");
        texts.append("      align: center\n");
        texts.append("      x: ").append(42 + panelOffsetX).append('\n');
        texts.append("      y: -52\n");
        texts.append("      scale: 0.48\n");
        texts.append("      outline: true\n");

        heads.append("  heads:\n");
        int headId = 1000;
        int textId = 2000;

        for (int slot = 1; slot <= ROUND_END_CIVILIAN_SLOTS; slot++) {
            int col = (slot - 1) % 4;
            int row = (slot - 1) / 4;
            int x = panelOffsetX + (-108 + (col * 24));
            int y = -34 + (row * 22);
            appendRoleHeadEntry(heads, headId++, "round_end_innocent", slot, x, y);
            appendRoleDeadMarkTextEntry(texts, textId++, "round_end_innocent", slot, x, y + 4);
            appendRoleLabelTextEntry(texts, textId++, "round_end_innocent", slot, x, y + 19);
        }

        for (int slot = 1; slot <= ROUND_END_KILLER_SLOTS; slot++) {
            int col = (slot - 1) % 2;
            int row = (slot - 1) / 2;
            int x = panelOffsetX + (24 + (col * 24));
            int y = -34 + (row * 22);
            appendRoleHeadEntry(heads, headId++, "round_end_killer", slot, x, y);
            appendRoleDeadMarkTextEntry(texts, textId++, "round_end_killer", slot, x, y + 4);
            appendRoleLabelTextEntry(texts, textId++, "round_end_killer", slot, x + 1, y + 19);
        }

        sb.append(heads);
        sb.append(texts);
        return sb.toString();
    }

    private static void appendRoleHeadEntry(StringBuilder sb, int id, String rolePrefix, int slot, int x, int y) {
        sb.append("    ").append(id).append(":\n");
        sb.append("      name: ").append(ROUND_END_HEAD_NAME).append('\n');
        sb.append("      type: fancy\n");
        sb.append("      align: center\n");
        sb.append("      x: ").append(x).append('\n');
        sb.append("      y: ").append(y).append('\n');
        sb.append("      follow: papi:wathe_").append(rolePrefix).append("_").append(slot).append("_head\n");
        appendRoleShowCondition(sb, rolePrefix, slot, "      ");
    }

    private static void appendRoleDeadMarkTextEntry(StringBuilder sb, int id, String rolePrefix, int slot, int x, int y) {
        sb.append("    ").append(id).append(":\n");
        sb.append("      name: example_font\n");
        sb.append("      pattern: \"<#E10000>x\"\n");
        sb.append("      align: center\n");
        sb.append("      x: ").append(x).append('\n');
        sb.append("      y: ").append(y).append('\n');
        sb.append("      scale: 0.72\n");
        sb.append("      outline: true\n");
        sb.append("      conditions:\n");
        sb.append("        1:\n");
        sb.append("          first: (number)papi:wathe_").append(rolePrefix).append("_").append(slot).append("_show\n");
        sb.append("          second: 1\n");
        sb.append("          operation: '=='\n");
        sb.append("        2:\n");
        sb.append("          first: (number)papi:wathe_").append(rolePrefix).append("_").append(slot).append("_dead\n");
        sb.append("          second: 1\n");
        sb.append("          operation: '=='\n");
    }

    private static void appendRoleLabelTextEntry(StringBuilder sb, int id, String rolePrefix, int slot, int x, int y) {
        sb.append("    ").append(id).append(":\n");
        sb.append("      name: example_font\n");
        sb.append("      pattern: \"[papi:wathe_").append(rolePrefix).append("_").append(slot).append("_role_colored]\"\n");
        sb.append("      align: center\n");
        sb.append("      x: ").append(x).append('\n');
        sb.append("      y: ").append(y).append('\n');
        sb.append("      scale: 0.40\n");
        sb.append("      outline: true\n");
        appendRoleShowCondition(sb, rolePrefix, slot, "      ");
    }

    private static void appendRoleShowCondition(StringBuilder sb, String rolePrefix, int slot, String indent) {
        sb.append(indent).append("conditions:\n");
        sb.append(indent).append("  1:\n");
        sb.append(indent).append("    first: (number)papi:wathe_").append(rolePrefix).append("_").append(slot).append("_show\n");
        sb.append(indent).append("    second: 1\n");
        sb.append(indent).append("    operation: '=='\n");
    }
}


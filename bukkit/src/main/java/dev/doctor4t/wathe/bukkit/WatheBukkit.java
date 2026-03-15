package dev.doctor4t.wathe.bukkit;

import dev.doctor4t.wathe.bukkit.command.WatheCommand;
import dev.doctor4t.wathe.bukkit.door.DoorAnimationManager;
import dev.doctor4t.wathe.bukkit.game.AutoStartManager;
import dev.doctor4t.wathe.bukkit.game.BlackoutManager;
import dev.doctor4t.wathe.bukkit.game.BlackoutTransitionManager;
import dev.doctor4t.wathe.bukkit.game.GameConstants;
import dev.doctor4t.wathe.bukkit.game.GameManager;
import dev.doctor4t.wathe.bukkit.game.MapConfig;
import dev.doctor4t.wathe.bukkit.integration.BetterHudIntegration;
import dev.doctor4t.wathe.bukkit.integration.CraftEngineIntegration;
import dev.doctor4t.wathe.bukkit.integration.LuckPermsIntegration;
import dev.doctor4t.wathe.bukkit.integration.WathePlaceholderExpansion;
import dev.doctor4t.wathe.bukkit.item.ItemHandler;
import dev.doctor4t.wathe.bukkit.listener.GameListener;
import dev.doctor4t.wathe.bukkit.listener.PacketListener;
import dev.doctor4t.wathe.bukkit.listener.PlayerListener;
import dev.doctor4t.wathe.bukkit.player.CorpseManager;

import dev.doctor4t.wathe.bukkit.player.AmbientSoundManager;
import dev.doctor4t.wathe.bukkit.player.MoodManager;
import dev.doctor4t.wathe.bukkit.player.OutsideSnowEffectManager;
import dev.doctor4t.wathe.bukkit.player.PlayerManager;
import dev.doctor4t.wathe.bukkit.player.PoisonManager;
import dev.doctor4t.wathe.bukkit.player.PsychoModeManager;
import dev.doctor4t.wathe.bukkit.player.PsychosisManager;
import dev.doctor4t.wathe.bukkit.player.ShopManager;
import dev.doctor4t.wathe.bukkit.player.TrayManager;
import dev.doctor4t.wathe.bukkit.player.WallhackManager;
import dev.doctor4t.wathe.bukkit.scenery.SceneryConfig;
import dev.doctor4t.wathe.bukkit.scenery.SceneryManager;
import org.bukkit.plugin.java.JavaPlugin;

public class WatheBukkit extends JavaPlugin {

    private static WatheBukkit instance;
    private GameManager gameManager;
    private PlayerManager playerManager;
    private ItemHandler itemHandler;
    private MoodManager moodManager;
    private ShopManager shopManager;
    private PoisonManager poisonManager;
    private CorpseManager corpseManager;
    private BlackoutManager blackoutManager;
    private WallhackManager wallhackManager;
    private PsychosisManager psychosisManager;
    private PsychoModeManager psychoModeManager;
    private SceneryManager sceneryManager;
    private SceneryConfig sceneryConfig;
    private MapConfig mapConfig;
    private AutoStartManager autoStartManager;
    private PacketListener packetListener;
    private DoorAnimationManager doorAnimationManager;
    private BlackoutTransitionManager blackoutTransitionManager;
    private AmbientSoundManager ambientSoundManager;
    private OutsideSnowEffectManager outsideSnowEffectManager;
    private TrayManager trayManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        GameConstants.loadFromConfig(this);
        
        // 初始化集成
        CraftEngineIntegration.init();
        if (!CraftEngineIntegration.isEnabled()) {
            getComponentLogger().error("CraftEngine is required. Disabling WatheBukkit.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BetterHudIntegration.init(this);
        LuckPermsIntegration.init();
        
        // 注册 PlaceholderAPI 扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WathePlaceholderExpansion(this).register();
            getComponentLogger().info("PlaceholderAPI expansion registered!");
        }

        this.mapConfig = new MapConfig(this);
        this.playerManager = new PlayerManager(this);
        this.moodManager = new MoodManager(this);
        this.shopManager = new ShopManager(this);
        this.poisonManager = new PoisonManager(this);
        this.trayManager = new TrayManager(this);
        this.corpseManager = new CorpseManager(this);
        this.blackoutManager = new BlackoutManager(this);
        this.wallhackManager = new WallhackManager(this);
        this.psychosisManager = new PsychosisManager(this);
        this.psychoModeManager = new PsychoModeManager(this);
        this.sceneryConfig = new SceneryConfig(this);
        this.sceneryManager = new SceneryManager(this);
        this.gameManager = new GameManager(this, playerManager);
        this.itemHandler = new ItemHandler(this);
        this.autoStartManager = new AutoStartManager(this);
        this.autoStartManager.start();
        this.packetListener = new PacketListener(this);
        this.packetListener.register();
        this.doorAnimationManager = new DoorAnimationManager(this);
        this.blackoutTransitionManager = new BlackoutTransitionManager(this);
        this.ambientSoundManager = new AmbientSoundManager(this);
        this.ambientSoundManager.start();
        this.outsideSnowEffectManager = new OutsideSnowEffectManager(this);
        this.outsideSnowEffectManager.start();

        org.bukkit.Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> preloadMapDoors(), 20L);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        var watheCommand = new WatheCommand(this);
        getCommand("wathe").setExecutor(watheCommand);
        getCommand("wathe").setTabCompleter(watheCommand);

        getComponentLogger().info("Wathe has been enabled!");
    }

    @Override
    public void onDisable() {
        BetterHudIntegration.shutdown();
        if (packetListener != null) {
            packetListener.unregister();
        }
        if (autoStartManager != null) {
            autoStartManager.stop();
        }
        if (gameManager != null && gameManager.isGameRunning()) {
            gameManager.stopGame();
        }
        if (poisonManager != null) {
            poisonManager.shutdown();
        }
        if (trayManager != null) {
            trayManager.clearAllTrays();
        }
        if (corpseManager != null) {
            corpseManager.clearAllCorpses();
        }
        if (blackoutManager != null) {
            blackoutManager.forceEnd();
        }
        if (wallhackManager != null) {
            wallhackManager.stopAllWallhacks();
        }
        if (psychosisManager != null) {
            psychosisManager.clearAll();
        }
        if (psychoModeManager != null) {
            psychoModeManager.clearAll();
        }
        if (sceneryManager != null) {
            sceneryManager.cleanup();
        }
        if (doorAnimationManager != null) {
            doorAnimationManager.cleanup();
        }
        if (blackoutTransitionManager != null) {
            blackoutTransitionManager.cleanup();
        }
        if (ambientSoundManager != null) {
            ambientSoundManager.stop();
        }
        if (outsideSnowEffectManager != null) {
            outsideSnowEffectManager.stop();
        }
        getComponentLogger().info("Wathe has been disabled!");
    }

    public void reloadConfiguration() {
        reloadConfig();
        GameConstants.loadFromConfig(this);
    }

    public void preloadMapDoors() {
        if (doorAnimationManager == null || trayManager == null || mapConfig == null) {
            return;
        }

        var world = mapConfig.getSpawnLocation().getWorld();
        if (world == null) {
            getComponentLogger().warn("Could not preload doors: map world is null");
            return;
        }

        var playArea = mapConfig.getPlayArea();
        if (playArea != null) {
            doorAnimationManager.preloadDoorsInArea(world, playArea);
            trayManager.preloadTraysInArea(world, playArea);
            return;
        }
        doorAnimationManager.preloadDoorsInWorld(world);
        trayManager.preloadTraysInWorld(world);
    }

    public static WatheBukkit getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ItemHandler getItemHandler() {
        return itemHandler;
    }

    public MoodManager getMoodManager() {
        return moodManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public PoisonManager getPoisonManager() {
        return poisonManager;
    }

    public TrayManager getTrayManager() {
        return trayManager;
    }

    public CorpseManager getCorpseManager() {
        return corpseManager;
    }

    public BlackoutManager getBlackoutManager() {
        return blackoutManager;
    }

    public WallhackManager getWallhackManager() {
        return wallhackManager;
    }

    public PsychosisManager getPsychosisManager() {
        return psychosisManager;
    }

    public PsychoModeManager getPsychoModeManager() {
        return psychoModeManager;
    }

    public SceneryManager getSceneryManager() {
        return sceneryManager;
    }

    public SceneryConfig getSceneryConfig() {
        return sceneryConfig;
    }

    public MapConfig getMapConfig() {
        return mapConfig;
    }

    public AutoStartManager getAutoStartManager() {
        return autoStartManager;
    }

    public DoorAnimationManager getDoorAnimationManager() {
        return doorAnimationManager;
    }

    public BlackoutTransitionManager getBlackoutTransitionManager() {
        return blackoutTransitionManager;
    }

    public AmbientSoundManager getAmbientSoundManager() {
        return ambientSoundManager;
    }

    public OutsideSnowEffectManager getOutsideSnowEffectManager() {
        return outsideSnowEffectManager;
    }
}

package dev.doctor4t.wathe.bukkit.game;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import org.bukkit.configuration.file.FileConfiguration;

public final class GameConstants {

    private GameConstants() {}

    // 浠庨厤缃姞杞界殑鍊?
    private static int defaultGameTimeMinutes = 10;
    private static int minPlayersMurder = 4;
    private static int killerThreshold = 8;
    private static int vigilanteThreshold = 6;

    private static int moneyStart = 100;
    private static int passiveMoneyInterval = 200; // ticks
    private static int passiveMoneyAmount = 5;
    private static int moneyPerKill = 100;

    private static int priceKnife = 100;
    private static int priceGun = 300;
    private static int priceGrenade = 350;
    private static int pricePoison = 100;
    private static int priceLockpick = 50;
    private static int priceCrowbar = 25;
    private static int priceBodyBag = 200;
    private static int priceBlackout = 200;
    private static int priceFirecracker = 10;
    private static int priceNote = 10;

    private static int knifeCooldown = 1200;
    private static int gunCooldown = 200;
    private static int grenadeCooldown = 6000;
    private static int lockpickCooldown = 3600;
    private static int crowbarCooldown = 200;
    private static int bodyBagCooldown = 6000;
    private static int blackoutCooldown = 3600;

    private static float moodGain = 0.5f;
    private static float moodDrainPerTick = 1f / 3600f; // 3鍒嗛挓浠庢弧鎺夊埌0
    private static float midMoodThreshold = 0.55f;
    private static float depressiveMoodThreshold = 0.2f;

    private static int blackoutMinDuration = 300;
    private static int blackoutMaxDuration = 400;
    private static int poisonEffectDelay = 600;
    private static int poisonDuration = 200;

    private static int timeOnCivilianKill = 1200;
    
    // 浠诲姟绯荤粺
    private static int timeToFirstTask = 600; // 30绉?
    private static int minTaskCooldown = 600; // 30绉?
    private static int maxTaskCooldown = 1200; // 60绉?
    private static int sleepTaskDuration = 160; // 8绉?
    private static int outsideTaskDuration = 160; // 8绉?
    
    // 鑷姩寮€濮嬬郴缁?
    private static int autoStartCountdown = 600; // 30绉?(ticks)
    private static int ambientInsideLoopTicks = 2080;
    private static int ambientOutsideLoopTicks = 2320;

    public static void loadFromConfig(WatheBukkit plugin) {
        FileConfiguration config = plugin.getConfig();

        defaultGameTimeMinutes = config.getInt("game.default-time", 10);
        minPlayersMurder = config.getInt("game.min-players", 4);
        killerThreshold = config.getInt("game.killer-threshold", 8);
        vigilanteThreshold = config.getInt("game.vigilante-threshold", 6);

        moneyStart = config.getInt("money.start", 100);
        passiveMoneyInterval = config.getInt("money.passive-interval", 10) * 20;
        passiveMoneyAmount = config.getInt("money.passive-amount", 5);
        moneyPerKill = config.getInt("money.kill-reward", 100);

        priceKnife = config.getInt("shop.knife", 100);
        priceGun = config.getInt("shop.gun", 300);
        priceGrenade = config.getInt("shop.grenade", 350);
        pricePoison = config.getInt("shop.poison", 100);
        priceLockpick = config.getInt("shop.lockpick", 50);
        priceCrowbar = config.getInt("shop.crowbar", 25);
        priceBodyBag = config.getInt("shop.body-bag", 200);
        priceBlackout = config.getInt("shop.blackout", 200);
        priceFirecracker = config.getInt("shop.firecracker", 10);
        priceNote = config.getInt("shop.note", 10);

        knifeCooldown = config.getInt("cooldowns.knife", 60) * 20;
        gunCooldown = config.getInt("cooldowns.gun", 10) * 20;
        grenadeCooldown = config.getInt("cooldowns.grenade", 300) * 20;
        lockpickCooldown = config.getInt("cooldowns.lockpick", 180) * 20;
        crowbarCooldown = config.getInt("cooldowns.crowbar", 10) * 20;
        bodyBagCooldown = config.getInt("cooldowns.body-bag", 300) * 20;
        blackoutCooldown = config.getInt("cooldowns.blackout", 180) * 20;

        moodGain = (float) config.getDouble("mood.gain", 0.5);
        float drainPerSecond = (float) config.getDouble("mood.drain-per-second", 0.00125);
        moodDrainPerTick = drainPerSecond / 20f;
        midMoodThreshold = (float) config.getDouble("mood.mid-threshold", 0.55);
        depressiveMoodThreshold = (float) config.getDouble("mood.depressive-threshold", 0.2);

        blackoutMinDuration = config.getInt("blackout.min-duration", 15) * 20;
        blackoutMaxDuration = config.getInt("blackout.max-duration", 20) * 20;

        poisonEffectDelay = config.getInt("poison.effect-delay", 30) * 20;
        poisonDuration = config.getInt("poison.duration", 10) * 20;

        timeOnCivilianKill = 60 * 20; // 1鍒嗛挓
        
        // 浠诲姟绯荤粺
        timeToFirstTask = config.getInt("task.time-to-first", 30) * 20;
        minTaskCooldown = config.getInt("task.min-cooldown", 30) * 20;
        maxTaskCooldown = config.getInt("task.max-cooldown", 60) * 20;
        sleepTaskDuration = config.getInt("task.sleep-duration", 8) * 20;
        outsideTaskDuration = config.getInt("task.outside-duration", 8) * 20;
        
        // 鑷姩寮€濮嬬郴缁?
        autoStartCountdown = config.getInt("auto-start.countdown", 30) * 20;

        // 环境音衔接时长（完整音频播放完后再循环）
        ambientInsideLoopTicks = config.getInt("ambient.inside-loop-seconds", 104) * 20;
        ambientOutsideLoopTicks = config.getInt("ambient.outside-loop-seconds", 116) * 20;
    }

    // Getters
    public static int getDefaultGameTimeMinutes() { return defaultGameTimeMinutes; }
    public static int getMinPlayersMurder() { return minPlayersMurder; }
    public static int getKillerThreshold() { return killerThreshold; }
    public static int getVigilanteThreshold() { return vigilanteThreshold; }

    public static int getMoneyStart() { return moneyStart; }
    public static int getPassiveMoneyInterval() { return passiveMoneyInterval; }
    public static int getPassiveMoneyAmount() { return passiveMoneyAmount; }
    public static int getMoneyPerKill() { return moneyPerKill; }

    public static int getPriceKnife() { return priceKnife; }
    public static int getPriceGun() { return priceGun; }
    public static int getPriceGrenade() { return priceGrenade; }
    public static int getPricePoison() { return pricePoison; }
    public static int getPriceLockpick() { return priceLockpick; }
    public static int getPriceCrowbar() { return priceCrowbar; }
    public static int getPriceBodyBag() { return priceBodyBag; }
    public static int getPriceBlackout() { return priceBlackout; }
    public static int getPriceFirecracker() { return priceFirecracker; }
    public static int getPriceNote() { return priceNote; }

    public static int getKnifeCooldown() { return knifeCooldown; }
    public static int getGunCooldown() { return gunCooldown; }
    public static int getGrenadeCooldown() { return grenadeCooldown; }
    public static int getLockpickCooldown() { return lockpickCooldown; }
    public static int getCrowbarCooldown() { return crowbarCooldown; }
    public static int getBodyBagCooldown() { return bodyBagCooldown; }
    public static int getBlackoutCooldown() { return blackoutCooldown; }

    public static float getMoodGain() { return moodGain; }
    public static float getMoodDrainPerTick() { return moodDrainPerTick; }
    public static float getMidMoodThreshold() { return midMoodThreshold; }
    public static float getDepressiveMoodThreshold() { return depressiveMoodThreshold; }

    public static int getBlackoutMinDuration() { return blackoutMinDuration; }
    public static int getBlackoutMaxDuration() { return blackoutMaxDuration; }
    public static int getPoisonEffectDelay() { return poisonEffectDelay; }
    public static int getPoisonDuration() { return poisonDuration; }

    public static int getTimeOnCivilianKill() { return timeOnCivilianKill; }
    
    // 浠诲姟绯荤粺
    public static int getTimeToFirstTask() { return timeToFirstTask; }
    public static int getMinTaskCooldown() { return minTaskCooldown; }
    public static int getMaxTaskCooldown() { return maxTaskCooldown; }
    public static int getSleepTaskDuration() { return sleepTaskDuration; }
    public static int getOutsideTaskDuration() { return outsideTaskDuration; }
    
    // 鑷姩寮€濮嬬郴缁?
    public static int getAutoStartCountdown() { return autoStartCountdown; }
    public static int getAmbientInsideLoopTicks() { return ambientInsideLoopTicks; }
    public static int getAmbientOutsideLoopTicks() { return ambientOutsideLoopTicks; }

    // 鍏煎鏃т唬鐮佺殑甯搁噺
    public static final float MOOD_GAIN = 0.5f;
    public static final float MOOD_DRAIN = 1f / 3600f; // 3鍒嗛挓浠庢弧鎺夊埌0
    public static final float MID_MOOD_THRESHOLD = 0.55f;
    public static final float DEPRESSIVE_MOOD_THRESHOLD = 0.2f;
    public static final int PASSIVE_MONEY_INTERVAL = 200;
    public static final int PASSIVE_MONEY_AMOUNT = 5;
    public static final int MONEY_PER_KILL = 100;
    public static final int TIME_ON_CIVILIAN_KILL = 1200;
    public static final int KNIFE_COOLDOWN = 1200;
    public static final int GUN_COOLDOWN = 200;
    public static final int GRENADE_COOLDOWN = 6000;
    public static final int PRICE_KNIFE = 100;
    public static final int PRICE_GUN = 300;
    public static final int PRICE_GRENADE = 350;
    public static final int PRICE_POISON = 100;
    public static final int PRICE_LOCKPICK = 50;
    public static final int PRICE_CROWBAR = 25;
    public static final int PRICE_BODY_BAG = 200;
    public static final int PRICE_BLACKOUT = 200;
    public static final int PRICE_FIRECRACKER = 10;
    public static final int PRICE_NOTE = 10;
    public static final int PRICE_PSYCHO_MODE = 300;
    
    // 鐙傛毚妯″紡
    public static final int PSYCHO_MODE_DURATION = 600; // 30绉?
    public static final int PSYCHO_MODE_ARMOUR = 1; // 鎶ょ敳鍊?
}

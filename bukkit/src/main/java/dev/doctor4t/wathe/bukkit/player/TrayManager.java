package dev.doctor4t.wathe.bukkit.player;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import dev.doctor4t.wathe.bukkit.item.GameItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TrayManager {

    private static final String BLOCK_FOOD_PLATTER = "food_platter";
    private static final String BLOCK_DRINK_TRAY = "drink_tray";
    private static final byte TRAY_DISPLAY_MARKER = 1;
    private static final boolean DEBUG_TRAY = true;
    private static final int MAX_VISIBLE_ITEMS = 16;
    private static final float FOOD_RING_RADIUS = 0.25f;
    private static final float DRINK_RING_RADIUS = 0.25f;
    private static final float FOOD_RING_Y = 0.0375f;
    private static final float DRINK_RING_Y = 0.225f;
    private static final float FOOD_DISPLAY_SCALE = 0.40f;
    private static final float DRINK_DISPLAY_SCALE = 0.40f;
    private static final Set<String> CE_DRINK_IDS = Set.of(
            "old_fashioned",
            "mojito",
            "martini",
            "cosmopolitan",
            "champagne"
    );

    private final WatheBukkit plugin;
    private final NamespacedKey poisonedByKey;
    private final NamespacedKey trayDisplayMarkerKey;
    private final NamespacedKey trayDisplayOwnerKey;
    private final Map<String, TrayData> trays = new ConcurrentHashMap<>();
    private final File trayDataFile;
    private YamlConfiguration trayDataConfig;

    public TrayManager(WatheBukkit plugin) {
        this.plugin = plugin;
        this.poisonedByKey = new NamespacedKey(plugin, "tray_poisoned_by");
        this.trayDisplayMarkerKey = new NamespacedKey(plugin, "tray_display_marker");
        this.trayDisplayOwnerKey = new NamespacedKey(plugin, "tray_display_owner");
        this.trayDataFile = new File(plugin.getDataFolder(), "trays.yml");
        loadTrayData();
    }

    public void clearAllTrays() {
        for (TrayData data : trays.values()) {
            removeKnownDisplayEntities(data);
        }
        trays.clear();
        for (World world : Bukkit.getWorlds()) {
            clearTrayDisplaysInWorld(world);
        }
    }

    public boolean handleInteract(Player player, Block block, ItemStack handItem) {
        TrayType trayType = resolveTrayType(block);
        if (trayType == TrayType.NONE) {
            return false;
        }

        boolean gameRunning = plugin.getGameManager().isGameRunning();
        GamePlayer gp = plugin.getPlayerManager().getPlayer(player);
        boolean aliveInGame = gameRunning && gp != null && gp.isAlive();

        String ownerKey = blockKey(block);
        TrayData tray = trays.computeIfAbsent(ownerKey, key -> createEmptyTray());
        refreshTrayDisplays(block, trayType, tray, ownerKey);

        // 杀手可将毒药混入餐盘/饮品盘
        if (handItem != null && "poison".equals(GameItems.getGameItemId(handItem))) {
            if (!aliveInGame || !gp.canUseKillerItems()) {
                return true;
            }
            if (tray.poisoner == null) {
                tray.poisoner = player.getUniqueId();
                decrementMainHand(player);
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1f);
                persistTray(ownerKey, tray);
            }
            return true;
        }

        // 仅局外允许放入食物/饮品
        if (handItem != null && !handItem.isEmpty()) {
            if (trayType == TrayType.DRINK && CraftEngineItems.isCustomItem(handItem)) {
                debug("Interact drink tray with CE item: " + describeItem(handItem)
                        + ", ceId=" + formatKey(resolveCeItemId(handItem))
                        + ", gameRunning=" + gameRunning);
            }
            boolean allowInsert = !gameRunning;
            if (allowInsert && canInsertItem(trayType, handItem)) {
                ItemStack toStore = handItem.clone();
                toStore.setAmount(1);
                tray.storedItems.add(toStore);
                decrementMainHand(player);
                refreshTrayDisplays(block, trayType, tray, ownerKey);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.7f, 1.2f);
                persistTray(ownerKey, tray);
                if (trayType == TrayType.DRINK) {
                    debug("Insert accepted. trayKey=" + ownerKey + ", size=" + tray.storedItems.size()
                            + ", inserted=" + describeItem(toStore) + ", ceId=" + formatKey(resolveCeItemId(toStore)));
                }
                return true;
            } else if (trayType == TrayType.DRINK) {
                debug("Insert rejected. allowInsert=" + allowInsert
                        + ", canInsert=" + canInsertItem(trayType, handItem)
                        + ", item=" + describeItem(handItem)
                        + ", ceId=" + formatKey(resolveCeItemId(handItem)));
            }
        }

        // 仅空手取物，避免影响正常拿物品右键行为
        if (handItem != null && !handItem.isEmpty()) {
            return false;
        }

        if (tray.storedItems.isEmpty()) {
            return true;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(tray.storedItems.size());
        ItemStack picked = tray.storedItems.get(randomIndex).clone();
        picked.setAmount(1);

        if (tray.poisoner != null) {
            setPoisonedBy(picked, tray.poisoner);
            tray.poisoner = null;
            persistTray(ownerKey, tray);
        }

        // 局外取出会消耗托盘库存；局内无限取用（不消耗）
        if (!gameRunning) {
            tray.storedItems.remove(randomIndex);
            refreshTrayDisplays(block, trayType, tray, ownerKey);
            persistTray(ownerKey, tray);
        }

        player.getInventory().setItemInMainHand(picked);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        return true;
    }

    public void handleBlockBreak(Block block) {
        String key = blockKey(block);
        TrayType trayType = resolveTrayType(block);
        if (trayType == TrayType.NONE && !trays.containsKey(key)) {
            return;
        }
        TrayData tray = trays.remove(key);
        if (tray != null) {
            removeKnownDisplayEntities(tray);
        }
        clearTrayDisplaysByOwner(block.getWorld(), key);
        removePersistedTray(key);
    }

    public int preloadTraysInWorld(World world) {
        int removed = clearTrayDisplaysInWorld(world);
        if (removed > 0) {
            plugin.getComponentLogger().info("Removed " + removed + " stale tray display entities in world");
        }

        int created = 0;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (Chunk chunk : world.getLoadedChunks()) {
            int baseX = chunk.getX() << 4;
            int baseZ = chunk.getZ() << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                        TrayType trayType = resolveTrayType(block);
                        if (trayType == TrayType.NONE) {
                            continue;
                        }
                        String ownerKey = blockKey(block);
                        TrayData tray = trays.computeIfAbsent(ownerKey, k -> createEmptyTray());
                        refreshTrayDisplays(block, trayType, tray, ownerKey);
                        created++;
                    }
                }
            }
        }

        plugin.getComponentLogger().info("Preloaded " + created + " trays in loaded chunks of world " + world.getName());
        return created;
    }

    public int preloadTraysInArea(World world, BoundingBox area) {
        int removed = clearTrayDisplaysInArea(world, area);
        if (removed > 0) {
            plugin.getComponentLogger().info("Removed " + removed + " stale tray display entities in map area");
        }

        int minX = (int) Math.floor(area.getMinX());
        int minY = (int) Math.floor(area.getMinY());
        int minZ = (int) Math.floor(area.getMinZ());
        int maxX = (int) Math.floor(area.getMaxX());
        int maxY = (int) Math.floor(area.getMaxY());
        int maxZ = (int) Math.floor(area.getMaxZ());

        int created = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    TrayType trayType = resolveTrayType(block);
                    if (trayType == TrayType.NONE) {
                        continue;
                    }
                    String ownerKey = blockKey(block);
                    TrayData tray = trays.computeIfAbsent(ownerKey, k -> createEmptyTray());
                    refreshTrayDisplays(block, trayType, tray, ownerKey);
                    created++;
                }
            }
        }

        plugin.getComponentLogger().info("Preloaded " + created + " trays in map area");
        return created;
    }

    private boolean canInsertItem(TrayType trayType, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Material type = item.getType();
        var ceId = resolveCeItemId(item);

        if (trayType == TrayType.FOOD) {
            // 食物托盘只接收食物，避免 CE 饮品错放到食物托盘
            return type.isEdible();
        }

        if (type == Material.POTION || type == Material.HONEY_BOTTLE || type == Material.MILK_BUCKET) {
            return true;
        }
        if (!CraftEngineItems.isCustomItem(item)) {
            debug("Drink insert blocked (not CE custom): " + describeItem(item));
            return false;
        }
        if (ceId == null) {
            // 是 CE 物品但无法解析 id，先允许放入，避免误拦截
            debug("Drink insert allow: CE custom item id unresolved, item=" + describeItem(item));
            return true;
        }
        if (!isWatheNamespace(ceId.namespace())) {
            debug("Drink insert blocked by namespace: " + ceId.namespace() + " for item " + describeItem(item));
            return false;
        }
        boolean isDrink = isCeDrinkValue(ceId.value());
        debug("Drink insert by CE id: " + ceId + ", isDrink=" + isDrink);
        return isDrink;
    }

    private net.momirealms.craftengine.core.util.Key resolveCeItemId(ItemStack item) {
        if (!CraftEngineItems.isCustomItem(item)) {
            return null;
        }
        var id = CraftEngineItems.getCustomItemId(item);
        if (id != null) {
            return id;
        }
        var custom = CraftEngineItems.byItemStack(item);
        return custom == null ? null : custom.id();
    }

    private boolean isWatheNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        return "wathe".equals(namespace)
                || "items.wathe".equals(namespace)
                || namespace.endsWith(".wathe")
                || namespace.startsWith("wathe.");
    }

    private boolean isCeDrinkValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (CE_DRINK_IDS.contains(value)) {
            return true;
        }
        return value.contains("drink")
                || value.contains("cocktail")
                || value.contains("wine")
                || value.contains("champagne")
                || value.contains("martini")
                || value.contains("mojito")
                || value.contains("fashioned")
                || value.contains("cosmopolitan");
    }

    public UUID getPoisonedBy(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        var meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(poisonedByKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setPoisonedBy(ItemStack item, UUID poisoner) {
        var meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(poisonedByKey, PersistentDataType.STRING, poisoner.toString());
        item.setItemMeta(meta);
    }

    private void decrementMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            return;
        }
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
    }

    private TrayType resolveTrayType(Block block) {
        var blockState = CraftEngineBlocks.getCustomBlockState(block);
        if (blockState == null) {
            return TrayType.NONE;
        }
        var id = blockState.owner().value().id();
        if (!isWatheNamespace(id.namespace())) {
            return TrayType.NONE;
        }
        String value = id.value();
        if (BLOCK_FOOD_PLATTER.equals(value)) {
            return TrayType.FOOD;
        }
        if (BLOCK_DRINK_TRAY.equals(value)) {
            return TrayType.DRINK;
        }
        return TrayType.NONE;
    }

    private TrayData createEmptyTray() {
        return new TrayData();
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private void refreshTrayDisplays(Block block, TrayType trayType, TrayData tray, String ownerKey) {
        removeKnownDisplayEntities(tray);
        if (tray.storedItems.isEmpty()) {
            return;
        }

        int count = Math.min(MAX_VISIBLE_ITEMS, tray.storedItems.size());
        float radius = trayType == TrayType.DRINK ? DRINK_RING_RADIUS : FOOD_RING_RADIUS;
        float baseY = trayType == TrayType.DRINK ? DRINK_RING_Y : FOOD_RING_Y;
        float angleStep = (float) (Math.PI * 2.0 / count);
        float startAngle = (float) (-Math.PI / 2.0);
        if (trayType == TrayType.DRINK) {
            debug("Refresh drink tray displays. key=" + ownerKey + ", stored=" + tray.storedItems.size() + ", visible=" + count);
        }

        for (int i = 0; i < count; i++) {
            ItemStack displayItem = toDisplayItem(tray.storedItems.get(i));
            float angle = startAngle + (i * angleStep);
            Vector3f offset = new Vector3f(
                    (float) (Math.cos(angle) * radius),
                    baseY,
                    (float) (Math.sin(angle) * radius)
            );
            UUID displayId = spawnDisplay(block, offset, displayItem, ownerKey, trayType, angle);
            if (displayId != null) {
                tray.displayEntities.add(displayId);
            }
        }
    }

    private UUID spawnDisplay(Block block, Vector3f offset, ItemStack item, String ownerKey, TrayType trayType, float angle) {
        var world = block.getWorld();
        var location = block.getLocation().add(0.5 + offset.x, offset.y, 0.5 + offset.z);
        Key ceId = resolveCeItemId(item);
        boolean ceDrinkCompat = trayType == TrayType.DRINK
                && ceId != null
                && isWatheNamespace(ceId.namespace())
                && isCeDrinkValue(ceId.value());

        if (ceDrinkCompat) {
            Item dropped = spawnCeDrinkItemEntity(world, location, item, ownerKey, ceId);
            return dropped == null ? null : dropped.getUniqueId();
        }

        var entity = world.spawnEntity(location, EntityType.ITEM_DISPLAY);
        if (!(entity instanceof ItemDisplay display)) {
            entity.remove();
            return null;
        }

        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setItemStack(item);
        if (trayType == TrayType.DRINK) {
            debug("Spawn drink display: item=" + describeItem(item)
                    + ", ceId=" + formatKey(ceId)
                    + ", transform=FIXED"
                    + ", offset=" + offset.x + "," + offset.y + "," + offset.z);
        }

        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.FIXED);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);

        float rotationDegrees = (float) Math.toDegrees(angle) + 90f;
        float rotationRadians = (float) Math.toRadians(rotationDegrees);
        Quaternionf leftRotation = new Quaternionf().rotateY(rotationRadians);
        Vector3f scale;
        Vector3f translation = new Vector3f(0, 0, 0);
        if (trayType == TrayType.FOOD) {
            // 对齐 Fabric：先绕Y旋转，再绕X倾斜约75度
            leftRotation.rotateX((float) Math.toRadians(75));
            scale = new Vector3f(FOOD_DISPLAY_SCALE, FOOD_DISPLAY_SCALE, FOOD_DISPLAY_SCALE);
        } else {
            // 饮品对齐 Fabric：仅绕Y旋转
            scale = new Vector3f(DRINK_DISPLAY_SCALE, DRINK_DISPLAY_SCALE, DRINK_DISPLAY_SCALE);
        }

        display.setTransformation(new Transformation(
                translation,
                leftRotation,
                scale,
                new Quaternionf()
        ));
        display.getPersistentDataContainer().set(trayDisplayMarkerKey, PersistentDataType.BYTE, TRAY_DISPLAY_MARKER);
        display.getPersistentDataContainer().set(trayDisplayOwnerKey, PersistentDataType.STRING, ownerKey);
        return display.getUniqueId();
    }

    private Item spawnCeDrinkItemEntity(World world, org.bukkit.Location location, ItemStack item, String ownerKey, Key ceId) {
        var entity = world.spawnEntity(location, EntityType.ITEM);
        if (!(entity instanceof Item dropped)) {
            entity.remove();
            return null;
        }

        ItemStack one = item.clone();
        one.setAmount(1);
        dropped.setItemStack(one);
        dropped.setGravity(false);
        dropped.setInvulnerable(true);
        dropped.setPersistent(false);
        dropped.setSilent(true);
        dropped.setPickupDelay(Integer.MAX_VALUE);
        dropped.setUnlimitedLifetime(true);
        dropped.setVelocity(new Vector(0, 0, 0));
        dropped.getPersistentDataContainer().set(trayDisplayMarkerKey, PersistentDataType.BYTE, TRAY_DISPLAY_MARKER);
        dropped.getPersistentDataContainer().set(trayDisplayOwnerKey, PersistentDataType.STRING, ownerKey);
        debug("Spawn drink CE item-entity display: item=" + describeItem(item)
                + ", ceId=" + formatKey(ceId)
                + ", loc=" + location.getX() + "," + location.getY() + "," + location.getZ());
        return dropped;
    }

    private ItemStack toDisplayItem(ItemStack source) {
        if (source == null || source.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        ItemStack cloned = source.clone();
        cloned.setAmount(1);
        return cloned;
    }

    private void debug(String message) {
        if (!DEBUG_TRAY) {
            return;
        }
        plugin.getComponentLogger().info("[TrayDebug] " + message);
    }

    private String describeItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "AIR";
        }
        return item.getType() + "x" + item.getAmount();
    }

    private String formatKey(Key key) {
        return key == null ? "null" : key.toString();
    }

    private void loadTrayData() {
        trays.clear();
        if (!trayDataFile.exists()) {
            trayDataConfig = new YamlConfiguration();
            return;
        }

        trayDataConfig = YamlConfiguration.loadConfiguration(trayDataFile);
        ConfigurationSection traysSection = trayDataConfig.getConfigurationSection("trays");
        if (traysSection == null) {
            return;
        }

        for (String encodedKey : traysSection.getKeys(false)) {
            ConfigurationSection traySection = traysSection.getConfigurationSection(encodedKey);
            if (traySection == null) {
                continue;
            }
            TrayData tray = new TrayData();

            ConfigurationSection itemsSection = traySection.getConfigurationSection("items");
            if (itemsSection != null) {
                List<String> indices = new ArrayList<>(itemsSection.getKeys(false));
                indices.sort(Comparator.comparingInt(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException ignored) {
                        return Integer.MAX_VALUE;
                    }
                }));

                for (String idx : indices) {
                    String base = "items." + idx;
                    String ceIdRaw = traySection.getString(base + ".ce-id");
                    ItemStack stack = null;
                    if (ceIdRaw != null && !ceIdRaw.isEmpty()) {
                        try {
                            var custom = CraftEngineItems.byId(Key.of(ceIdRaw));
                            if (custom != null) {
                                stack = custom.buildItemStack();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (stack == null) {
                        stack = traySection.getItemStack(base + ".item");
                    }
                    if (stack != null && !stack.isEmpty()) {
                        stack.setAmount(1);
                        tray.storedItems.add(stack);
                    }
                }
            }

            String poisonerRaw = traySection.getString("poisoner");
            if (poisonerRaw != null && !poisonerRaw.isEmpty()) {
                try {
                    tray.poisoner = UUID.fromString(poisonerRaw);
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (!tray.storedItems.isEmpty() || tray.poisoner != null) {
                trays.put(decodeTrayKey(encodedKey), tray);
            }
        }
    }

    private void persistTray(String key, TrayData tray) {
        ensureTrayConfig();
        String encoded = encodeTrayKey(key);
        String sectionPath = "trays." + encoded;

        if (tray.storedItems.isEmpty() && tray.poisoner == null) {
            trayDataConfig.set(sectionPath, null);
            saveTrayData();
            return;
        }

        trayDataConfig.set(sectionPath + ".poisoner", tray.poisoner == null ? null : tray.poisoner.toString());
        trayDataConfig.set(sectionPath + ".items", null);
        for (int i = 0; i < tray.storedItems.size(); i++) {
            ItemStack stack = tray.storedItems.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack one = stack.clone();
            one.setAmount(1);
            String base = sectionPath + ".items." + i;
            Key ceId = resolveCeItemId(one);
            trayDataConfig.set(base + ".ce-id", ceId == null ? null : ceId.toString());
            trayDataConfig.set(base + ".item", one);
        }
        saveTrayData();
    }

    private void removePersistedTray(String key) {
        ensureTrayConfig();
        trayDataConfig.set("trays." + encodeTrayKey(key), null);
        saveTrayData();
    }

    private void ensureTrayConfig() {
        if (trayDataConfig == null) {
            trayDataConfig = new YamlConfiguration();
        }
    }

    private void saveTrayData() {
        try {
            if (trayDataFile.getParentFile() != null && !trayDataFile.getParentFile().exists()) {
                trayDataFile.getParentFile().mkdirs();
            }
            trayDataConfig.save(trayDataFile);
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Failed to save trays.yml: " + e.getMessage());
        }
    }

    private String encodeTrayKey(String key) {
        return key.replace(":", "|");
    }

    private String decodeTrayKey(String encoded) {
        return encoded.replace("|", ":");
    }

    private void removeKnownDisplayEntities(TrayData tray) {
        for (UUID uuid : tray.displayEntities) {
            var entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        tray.displayEntities.clear();
    }

    private int clearTrayDisplaysInWorld(World world) {
        int removed = 0;
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (!isTrayDisplay(display)) {
                continue;
            }
            display.remove();
            removed++;
        }
        for (Item dropped : world.getEntitiesByClass(Item.class)) {
            if (!isTrayDisplay(dropped)) {
                continue;
            }
            dropped.remove();
            removed++;
        }
        return removed;
    }

    private int clearTrayDisplaysInArea(World world, BoundingBox area) {
        int removed = 0;
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (!display.isValid() || !isTrayDisplay(display)) {
                continue;
            }
            var loc = display.getLocation();
            if (!area.contains(loc.getX(), loc.getY(), loc.getZ())) {
                continue;
            }
            display.remove();
            removed++;
        }
        for (Item dropped : world.getEntitiesByClass(Item.class)) {
            if (!dropped.isValid() || !isTrayDisplay(dropped)) {
                continue;
            }
            var loc = dropped.getLocation();
            if (!area.contains(loc.getX(), loc.getY(), loc.getZ())) {
                continue;
            }
            dropped.remove();
            removed++;
        }
        return removed;
    }

    private void clearTrayDisplaysByOwner(World world, String ownerKey) {
        for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
            if (!isTrayDisplay(display)) {
                continue;
            }
            String markerOwner = display.getPersistentDataContainer().get(trayDisplayOwnerKey, PersistentDataType.STRING);
            if (!ownerKey.equals(markerOwner)) {
                continue;
            }
            display.remove();
        }
        for (Item dropped : world.getEntitiesByClass(Item.class)) {
            if (!isTrayDisplay(dropped)) {
                continue;
            }
            String markerOwner = dropped.getPersistentDataContainer().get(trayDisplayOwnerKey, PersistentDataType.STRING);
            if (!ownerKey.equals(markerOwner)) {
                continue;
            }
            dropped.remove();
        }
    }

    private boolean isTrayDisplay(Entity entity) {
        Byte marker = entity.getPersistentDataContainer().get(trayDisplayMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == TRAY_DISPLAY_MARKER;
    }

    private enum TrayType {
        FOOD,
        DRINK,
        NONE
    }

    private static final class TrayData {
        private final List<ItemStack> storedItems = new ArrayList<>();
        private final List<UUID> displayEntities = new ArrayList<>();
        private UUID poisoner;
    }
}

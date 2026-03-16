package dev.doctor4t.wathe.bukkit.door;

import dev.doctor4t.wathe.bukkit.WatheBukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DoorAnimationManager {

    private final WatheBukkit plugin;
    private final Map<Location, AnimatingDoor> animatingDoors = new ConcurrentHashMap<>();
    private final Map<Location, ScheduledTask> autoCloseTasks = new ConcurrentHashMap<>();
    private final Map<Location, Location> pairedDoors = new ConcurrentHashMap<>();
    private final Map<Location, UUID> animationSequenceIds = new ConcurrentHashMap<>();

    // Persistent visual/interaction cache: create once, then reuse for all open/close animations.
    private final Map<Location, DoorVisual> doorVisuals = new ConcurrentHashMap<>();
    private final Map<UUID, Location> interactionToDoor = new ConcurrentHashMap<>();
    private final Map<Location, DoorInfo> knownDoors = new ConcurrentHashMap<>();

    private static final float SLIDE_DISTANCE = 14f / 16f;
    private static final int ANIMATION_HOLD_TICKS = 1;
    private static final int ANIMATION_SEGMENT_TICKS = 3;
    private static final int ANIMATION_SEGMENTS = 4;
    private static final int TOTAL_ANIMATION_TICKS = ANIMATION_HOLD_TICKS + (ANIMATION_SEGMENTS * ANIMATION_SEGMENT_TICKS);
    private static final int OPEN_DURATION_TICKS = 100;
    private static final byte DOOR_ENTITY_MARKER = 1;

    private final NamespacedKey doorInteractionMarkerKey;

    public DoorAnimationManager(WatheBukkit plugin) {
        this.plugin = plugin;
        this.doorInteractionMarkerKey = new NamespacedKey(plugin, "door_interaction");
    }

    public boolean tryOpenDoor(@Nullable Player player, Block block, ImmutableBlockState blockState) {
        DoorInfo mainDoor = parseDoorInfo(block, blockState);
        if (mainDoor == null) {
            mainDoor = tryResolveDoorInfoFromNeighbors(block);
        }
        if (mainDoor == null) {
            plugin.getComponentLogger().warn("Could not parse door state from: " + blockState);
            return false;
        }

        knownDoors.put(mainDoor.lowerPos, mainDoor);
        return tryOpenDoor(mainDoor);
    }

    public int preloadDoorsInWorld(World world) {
        resetDoorRuntimeStateInWorld(world);
        int removedInteractions = clearInteractionEntitiesInWorld(world);
        if (removedInteractions > 0) {
            plugin.getComponentLogger().info("Removed " + removedInteractions + " stale door interaction entities in world");
        }

        int created = 0;
        Set<Location> seenDoors = new HashSet<>();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (Chunk chunk : world.getLoadedChunks()) {
            int baseX = chunk.getX() << 4;
            int baseZ = chunk.getZ() << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = 0; z < 16; z++) {
                        Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
                        if (state == null) {
                            continue;
                        }

                        DoorInfo door = parseDoorInfo(block, state);
                        if (door == null || !seenDoors.add(door.lowerPos)) {
                            continue;
                        }

                        knownDoors.put(door.lowerPos, door);
                        DoorVisual visual = getOrCreateDoorVisual(door);
                        if (visual != null) {
                            created++;
                        }
                    }
                }
            }
        }

        plugin.getComponentLogger().info("Preloaded " + created + " doors in loaded chunks of world " + world.getName());
        return created;
    }

    public int clearDoorInteractionEntitiesInWorld(World world) {
        resetDoorRuntimeStateInWorld(world);
        return clearInteractionEntitiesInWorld(world);
    }

    public int clearDoorInteractionEntitiesInChunk(Chunk chunk) {
        int removed = 0;
        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        for (var entity : chunk.getEntities()) {
            if (!(entity instanceof Interaction interaction)) {
                continue;
            }
            if (!interaction.isValid()) {
                continue;
            }
            Location loc = interaction.getLocation();
            if (loc.getX() < minX || loc.getX() >= maxX || loc.getZ() < minZ || loc.getZ() >= maxZ) {
                continue;
            }
            if (isWatheDoorInteraction(interaction) || isLikelyLegacyDoorInteraction(interaction)) {
                interaction.remove();
                removed++;
            }
        }
        return removed;
    }

    public int preloadDoorsInArea(World world, BoundingBox area) {
        resetDoorRuntimeStateInArea(world, area);
        int removedInteractions = clearInteractionEntitiesInArea(world, area);
        if (removedInteractions > 0) {
            plugin.getComponentLogger().info("Removed " + removedInteractions + " stale interaction entities in map area");
        }

        int minX = (int) Math.floor(area.getMinX());
        int minY = (int) Math.floor(area.getMinY());
        int minZ = (int) Math.floor(area.getMinZ());
        int maxX = (int) Math.floor(area.getMaxX());
        int maxY = (int) Math.floor(area.getMaxY());
        int maxZ = (int) Math.floor(area.getMaxZ());

        int created = 0;
        Set<Location> seenDoors = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
                    if (state == null) {
                        continue;
                    }

                    DoorInfo door = parseDoorInfo(block, state);
                    if (door == null) {
                        continue;
                    }
                    if (!seenDoors.add(door.lowerPos)) {
                        continue;
                    }

                    knownDoors.put(door.lowerPos, door);
                    DoorVisual visual = getOrCreateDoorVisual(door);
                    if (visual != null) {
                        created++;
                    }
                }
            }
        }

        plugin.getComponentLogger().info("Preloaded " + created + " doors in map area");
        return created;
    }

    private int clearInteractionEntitiesInArea(World world, BoundingBox area) {
        int removed = 0;
        for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
            if (!interaction.isValid()) {
                continue;
            }
            Location loc = interaction.getLocation();
            if (!area.contains(loc.getX(), loc.getY(), loc.getZ())) {
                continue;
            }
            if (isWatheDoorInteraction(interaction) || isLikelyLegacyDoorInteraction(interaction)) {
                interaction.remove();
                removed++;
            }
        }
        return removed;
    }

    private int clearInteractionEntitiesInWorld(World world) {
        int removed = 0;
        for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
            if (!interaction.isValid()) {
                continue;
            }
            if (isWatheDoorInteraction(interaction) || isLikelyLegacyDoorInteraction(interaction)) {
                interaction.remove();
                removed++;
            }
        }
        return removed;
    }

    public void handlePotentialDoorBlockChange(Block block) {
        Set<Location> candidates = new HashSet<>();
        Location base = block.getLocation();
        candidates.add(base);
        candidates.add(base.clone().add(0, -1, 0));
        candidates.add(base.clone().add(0, 1, 0));

        for (Location lowerPos : candidates) {
            if (!doorVisuals.containsKey(lowerPos)
                && !knownDoors.containsKey(lowerPos)
                && !animatingDoors.containsKey(lowerPos)) {
                continue;
            }

            if (!isDoorStillPresent(lowerPos, null)) {
                purgeDoorState(lowerPos);
            }
        }
    }

    private boolean tryOpenDoor(DoorInfo mainDoor) {
        if (!isDoorStillPresent(mainDoor.lowerPos, mainDoor.doorId)) {
            purgeDoorState(mainDoor.lowerPos);
            return false;
        }

        clearStaleAnimation(mainDoor.lowerPos);
        if (animatingDoors.containsKey(mainDoor.lowerPos)) {
            return false;
        }

        NeighborDoorMatch neighborMatch = findNeighborDoor(mainDoor);

        if (neighborMatch != null && neighborMatch.sameFacing && neighborMatch.offsetFromMain != null) {
            Vector3f offsetVector = toHorizontalVector(neighborMatch.offsetFromMain);
            Vector3f mainSlide = new Vector3f(-offsetVector.x, 0, -offsetVector.z);
            Vector3f neighborSlide = new Vector3f(offsetVector);

            startOpenAnimation(mainDoor, true, mainSlide);
            startOpenAnimation(neighborMatch.door, false, neighborSlide);
            pairDoors(mainDoor.lowerPos, neighborMatch.door.lowerPos);
            return true;
        }

        startOpenAnimation(mainDoor, true, null);
        if (neighborMatch != null) {
            startOpenAnimation(neighborMatch.door, false, neighborMatch.forcedSlideDirection);
            pairDoors(mainDoor.lowerPos, neighborMatch.door.lowerPos);
        }
        return true;
    }

    private boolean isDoorStillPresent(Location lowerPos, @Nullable String expectedDoorId) {
        DoorInfo current = resolveDoorInfoAtLowerPos(lowerPos);
        if (current == null) {
            return false;
        }
        return expectedDoorId == null || expectedDoorId.equals(current.doorId);
    }

    private @Nullable DoorInfo resolveDoorInfoAtLowerPos(Location lowerPos) {
        Block lowerBlock = lowerPos.getBlock();
        ImmutableBlockState lowerState = CraftEngineBlocks.getCustomBlockState(lowerBlock);
        if (lowerState != null) {
            DoorInfo lowerInfo = parseDoorInfo(lowerBlock, lowerState);
            if (lowerInfo != null && lowerInfo.lowerPos.equals(lowerPos)) {
                return lowerInfo;
            }
        }

        Block upperBlock = lowerPos.clone().add(0, 1, 0).getBlock();
        ImmutableBlockState upperState = CraftEngineBlocks.getCustomBlockState(upperBlock);
        if (upperState != null) {
            DoorInfo upperInfo = parseDoorInfo(upperBlock, upperState);
            if (upperInfo != null && upperInfo.lowerPos.equals(lowerPos)) {
                return upperInfo;
            }
        }

        return null;
    }

    private void purgeDoorState(Location lowerPos) {
        ScheduledTask autoCloseTask = autoCloseTasks.remove(lowerPos);
        if (autoCloseTask != null) {
            autoCloseTask.cancel();
        }
        animatingDoors.remove(lowerPos);
        animationSequenceIds.remove(lowerPos);
        knownDoors.remove(lowerPos);

        Location pairedPos = pairedDoors.remove(lowerPos);
        if (pairedPos != null) {
            pairedDoors.remove(pairedPos);
        }

        removeDoorVisual(lowerPos);
    }

    private @Nullable DoorInfo tryResolveDoorInfoFromNeighbors(Block origin) {
        Block[] candidates = new Block[] {
            origin,
            origin.getRelative(BlockFace.UP),
            origin.getRelative(BlockFace.DOWN)
        };

        for (Block candidate : candidates) {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(candidate);
            if (state == null) {
                continue;
            }

            DoorInfo info = parseDoorInfo(candidate, state);
            if (info != null) {
                knownDoors.put(info.lowerPos, info);
                return info;
            }
        }
        return null;
    }

    private void clearStaleAnimation(Location lowerPos) {
        AnimatingDoor door = animatingDoors.get(lowerPos);
        if (door == null) {
            return;
        }

        boolean interactionAlive = false;
        var interaction = Bukkit.getEntity(door.interactionId);
        if (interaction instanceof Interaction && interaction.isValid()) {
            interactionAlive = true;
        }

        boolean lowerAlive = false;
        var lowerEntity = Bukkit.getEntity(door.lowerDisplayId);
        if (lowerEntity instanceof BlockDisplay && lowerEntity.isValid()) {
            lowerAlive = true;
        }

        boolean upperAlive = false;
        var upperEntity = Bukkit.getEntity(door.upperDisplayId);
        if (upperEntity instanceof BlockDisplay && upperEntity.isValid()) {
            upperAlive = true;
        }

        if (interactionAlive || lowerAlive || upperAlive) {
            return;
        }

        ScheduledTask autoCloseTask = autoCloseTasks.remove(lowerPos);
        if (autoCloseTask != null) {
            autoCloseTask.cancel();
        }
        animatingDoors.remove(lowerPos);
        animationSequenceIds.remove(lowerPos);

        Location pairedPos = pairedDoors.remove(lowerPos);
        if (pairedPos != null) {
            pairedDoors.remove(pairedPos);
        }
    }

    private void pairDoors(Location first, Location second) {
        pairedDoors.put(first, second);
        pairedDoors.put(second, first);
    }

    private @Nullable BlockFace parseFacing(String stateStr) {
        if (stateStr.contains("facing=north")) return BlockFace.NORTH;
        if (stateStr.contains("facing=south")) return BlockFace.SOUTH;
        if (stateStr.contains("facing=east")) return BlockFace.EAST;
        if (stateStr.contains("facing=west")) return BlockFace.WEST;
        return null;
    }

    private void startOpenAnimation(DoorInfo door, boolean playSound, @Nullable Vector3f forcedSlideDirection) {
        DoorVisual visual = getOrCreateDoorVisual(door);
        if (visual == null) {
            return;
        }

        Vector3f slideDirection = forcedSlideDirection == null
            ? getSlideDirection(door.facing, door.hingeRight)
            : new Vector3f(forcedSlideDirection);

        var lowerEntity = Bukkit.getEntity(visual.lowerDisplayId);
        var upperEntity = Bukkit.getEntity(visual.upperDisplayId);
        var interactionEntity = Bukkit.getEntity(visual.interactionId);

        if (!(lowerEntity instanceof BlockDisplay lowerDisplay)
            || !(upperEntity instanceof BlockDisplay upperDisplay)
            || !(interactionEntity instanceof Interaction interaction)) {
            removeDoorVisual(door.lowerPos);
            return;
        }

        Block lowerBlock = door.lowerPos.getBlock();
        Block upperBlock = door.lowerPos.clone().add(0, 1, 0).getBlock();
        boolean hasCollisionBlocks = lowerBlock.getType() != Material.AIR || upperBlock.getType() != Material.AIR;

        // Phase 1: show display at closed pose so there is no empty-frame flash.
        lowerDisplay.setBlock(visual.lowerBlockData);
        upperDisplay.setBlock(visual.upperBlockData);
        lowerDisplay.setInterpolationDuration(0);
        lowerDisplay.setInterpolationDelay(0);
        lowerDisplay.setTransformation(createClosedVisibleTransform());
        upperDisplay.setInterpolationDuration(0);
        upperDisplay.setInterpolationDelay(0);
        upperDisplay.setTransformation(createClosedVisibleTransform());

        Runnable beginSlide = () -> {
            if (!lowerDisplay.isValid() || !upperDisplay.isValid() || !interaction.isValid()) {
                return;
            }

            // Ensure collision blocks are removed only while door is open.
            if (lowerBlock.getType() != Material.AIR) {
                lowerBlock.setType(Material.AIR, false);
            }
            if (upperBlock.getType() != Material.AIR) {
                upperBlock.setType(Material.AIR, false);
            }

            setInteractionPosition(interaction, door.lowerPos, slideDirection);
            interaction.setResponsive(true);
            startDoorAnimation(door.lowerPos, lowerDisplay, upperDisplay, interaction, slideDirection, true);
        };

        if (hasCollisionBlocks) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> beginSlide.run(), 1L);
        } else {
            beginSlide.run();
        }

        if (playSound) {
            door.lowerPos.getWorld().playSound(door.lowerPos, "wathe:block.door.toggle", 1.0f, 1.0f);
        }

        AnimatingDoor animatingDoor = new AnimatingDoor(
            door.lowerPos,
            visual.lowerDisplayId,
            visual.upperDisplayId,
            visual.interactionId,
            slideDirection
        );
        animatingDoors.put(door.lowerPos, animatingDoor);

        ScheduledTask autoCloseTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            autoCloseTasks.remove(door.lowerPos);
            closeDoor(door.lowerPos);
        }, OPEN_DURATION_TICKS);
        autoCloseTasks.put(door.lowerPos, autoCloseTask);
    }

    private @Nullable DoorVisual getOrCreateDoorVisual(DoorInfo door) {
        DoorVisual existing = doorVisuals.get(door.lowerPos);
        if (existing != null && isDoorVisualValid(existing)) {
            return existing;
        }
        if (existing != null) {
            removeDoorVisual(door.lowerPos);
        }

        Block lowerBlock = door.lowerPos.getBlock();
        Block upperBlock = door.lowerPos.clone().add(0, 1, 0).getBlock();
        BlockData lowerData = lowerBlock.getBlockData().clone();
        BlockData upperData = upperBlock.getBlockData().clone();

        if (lowerBlock.getType() == Material.AIR && upperBlock.getType() == Material.AIR) {
            return null;
        }

        BlockDisplay lowerDisplay = spawnClosedDoorDisplay(door.lowerPos, lowerData);
        BlockDisplay upperDisplay = spawnClosedDoorDisplay(door.lowerPos.clone().add(0, 1, 0), upperData);
        Vector3f defaultOpenSlide = getSlideDirection(door.facing, door.hingeRight);
        Interaction interaction = spawnDoorInteraction(door.lowerPos, defaultOpenSlide);
        interaction.setResponsive(false);

        DoorVisual visual = new DoorVisual(
            door.lowerPos,
            lowerDisplay.getUniqueId(),
            upperDisplay.getUniqueId(),
            interaction.getUniqueId(),
            lowerData,
            upperData
        );
        doorVisuals.put(door.lowerPos, visual);
        interactionToDoor.put(interaction.getUniqueId(), door.lowerPos);
        return visual;
    }

    private boolean isDoorVisualValid(DoorVisual visual) {
        var lowerEntity = Bukkit.getEntity(visual.lowerDisplayId);
        var upperEntity = Bukkit.getEntity(visual.upperDisplayId);
        var interactionEntity = Bukkit.getEntity(visual.interactionId);
        return lowerEntity instanceof BlockDisplay && lowerEntity.isValid()
            && upperEntity instanceof BlockDisplay && upperEntity.isValid()
            && interactionEntity instanceof Interaction && interactionEntity.isValid();
    }

    private void removeDoorVisual(Location lowerPos) {
        DoorVisual visual = doorVisuals.remove(lowerPos);
        if (visual == null) {
            return;
        }

        var lowerEntity = Bukkit.getEntity(visual.lowerDisplayId);
        var upperEntity = Bukkit.getEntity(visual.upperDisplayId);
        var interactionEntity = Bukkit.getEntity(visual.interactionId);

        if (lowerEntity != null) {
            lowerEntity.remove();
        }
        if (upperEntity != null) {
            upperEntity.remove();
        }
        if (interactionEntity != null) {
            interactionEntity.remove();
        }

        interactionToDoor.remove(visual.interactionId);
    }

    private @Nullable NeighborDoorMatch findNeighborDoor(DoorInfo mainDoor) {
        BlockFace leftOffset = rotateYCounterclockwise(mainDoor.facing);
        BlockFace rightOffset = rotateYClockwise(mainDoor.facing);
        if (leftOffset == null || rightOffset == null) {
            return null;
        }

        NeighborDoorMatch mirroredMatch = null;
        NeighborDoorMatch oppositeFacingMatch = null;

        for (BlockFace offset : new BlockFace[] {leftOffset, rightOffset}) {
            DoorInfo neighborDoor = resolveNeighborDoorInfo(mainDoor.lowerPos, offset);
            if (neighborDoor == null) {
                continue;
            }

            if (!mainDoor.doorId.equals(neighborDoor.doorId)) {
                continue;
            }

            if (animatingDoors.containsKey(neighborDoor.lowerPos)) {
                continue;
            }

            if (neighborDoor.facing == mainDoor.facing) {
                boolean mirroredHinge = neighborDoor.hingeRight != mainDoor.hingeRight;
                Vector3f mainSlide = getSlideDirection(mainDoor.facing, mainDoor.hingeRight);
                Vector3f mirroredSlide = new Vector3f(-mainSlide.x, 0, -mainSlide.z);
                NeighborDoorMatch match = new NeighborDoorMatch(neighborDoor, mirroredSlide, true, offset);
                if (mirroredHinge) {
                    mirroredMatch = match;
                    break;
                }
                if (mirroredMatch == null) {
                    mirroredMatch = match;
                }
                continue;
            }

            if (neighborDoor.facing == mainDoor.facing.getOppositeFace() && oppositeFacingMatch == null) {
                oppositeFacingMatch = new NeighborDoorMatch(neighborDoor, null, false, offset);
            }
        }

        return mirroredMatch != null ? mirroredMatch : oppositeFacingMatch;
    }

    private @Nullable DoorInfo resolveNeighborDoorInfo(Location baseLowerPos, BlockFace offset) {
        Location neighborLowerPos = baseLowerPos.clone().add(offset.getModX(), 0, offset.getModZ());
        DoorInfo cached = knownDoors.get(neighborLowerPos);
        if (cached != null) {
            return cached;
        }

        Block neighborBlock = neighborLowerPos.getBlock();
        ImmutableBlockState neighborState = CraftEngineBlocks.getCustomBlockState(neighborBlock);
        if (neighborState == null) {
            return null;
        }

        DoorInfo neighborDoor = parseDoorInfo(neighborBlock, neighborState);
        if (neighborDoor != null) {
            knownDoors.put(neighborDoor.lowerPos, neighborDoor);
        }
        return neighborDoor;
    }

    private @Nullable DoorInfo parseDoorInfo(Block block, ImmutableBlockState blockState) {
        String stateStr = blockState.toString();
        if (!isDoorState(stateStr)) {
            return null;
        }

        BlockFace facing = parseFacing(stateStr);
        String doorId = parseDoorId(stateStr);
        if (facing == null || doorId == null) {
            return null;
        }

        boolean isUpper = stateStr.contains("half=upper");
        Location lowerPos = isUpper ? block.getLocation().add(0, -1, 0) : block.getLocation();
        boolean hingeRight = stateStr.contains("hinge=right");

        return new DoorInfo(lowerPos, blockState, doorId, facing, hingeRight);
    }

    private boolean isDoorState(String stateStr) {
        String blockId = parseDoorId(stateStr);
        return blockId != null
            && blockId.startsWith("wathe:")
            && blockId.contains("door");
    }

    private @Nullable String parseDoorId(String stateStr) {
        int start = stateStr.indexOf("wathe:");
        if (start < 0) {
            return null;
        }

        int end = stateStr.length();
        int endBracket = stateStr.indexOf('[', start);
        int endComma = stateStr.indexOf(',', start);
        int endBrace = stateStr.indexOf('}', start);

        if (endBracket >= 0 && endBracket < end) end = endBracket;
        if (endComma >= 0 && endComma < end) end = endComma;
        if (endBrace >= 0 && endBrace < end) end = endBrace;

        if (end <= start) {
            return null;
        }
        return stateStr.substring(start, end).trim();
    }

    private @Nullable BlockFace rotateYCounterclockwise(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> null;
        };
    }

    private @Nullable BlockFace rotateYClockwise(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> null;
        };
    }

    private Vector3f toHorizontalVector(BlockFace face) {
        return switch (face) {
            case NORTH -> new Vector3f(0, 0, -1);
            case SOUTH -> new Vector3f(0, 0, 1);
            case EAST -> new Vector3f(1, 0, 0);
            case WEST -> new Vector3f(-1, 0, 0);
            default -> new Vector3f(0, 0, 0);
        };
    }

    private Interaction spawnDoorInteraction(Location lowerPos, Vector3f slideDirection) {
        Location spawnLoc = getInteractionLocation(lowerPos, slideDirection);
        Interaction interaction = (Interaction) lowerPos.getWorld().spawnEntity(spawnLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(0.2f);
        interaction.setInteractionHeight(2.0f);
        interaction.setResponsive(true);
        interaction.getPersistentDataContainer().set(doorInteractionMarkerKey, PersistentDataType.BYTE, DOOR_ENTITY_MARKER);

        return interaction;
    }

    private void resetDoorRuntimeStateInWorld(World world) {
        Set<Location> targets = new HashSet<>();

        for (Location pos : doorVisuals.keySet()) {
            if (world.equals(pos.getWorld())) {
                targets.add(pos);
            }
        }
        for (Location pos : knownDoors.keySet()) {
            if (world.equals(pos.getWorld())) {
                targets.add(pos);
            }
        }
        for (Location pos : animatingDoors.keySet()) {
            if (world.equals(pos.getWorld())) {
                targets.add(pos);
            }
        }

        for (Location pos : targets) {
            purgeDoorState(pos);
        }

        interactionToDoor.entrySet().removeIf(entry -> {
            Location value = entry.getValue();
            return value != null && world.equals(value.getWorld());
        });
    }

    private void resetDoorRuntimeStateInArea(World world, BoundingBox area) {
        Set<Location> targets = new HashSet<>();

        for (Location pos : doorVisuals.keySet()) {
            if (world.equals(pos.getWorld()) && area.contains(pos.toVector())) {
                targets.add(pos);
            }
        }
        for (Location pos : knownDoors.keySet()) {
            if (world.equals(pos.getWorld()) && area.contains(pos.toVector())) {
                targets.add(pos);
            }
        }
        for (Location pos : animatingDoors.keySet()) {
            if (world.equals(pos.getWorld()) && area.contains(pos.toVector())) {
                targets.add(pos);
            }
        }

        for (Location pos : targets) {
            purgeDoorState(pos);
        }

        interactionToDoor.entrySet().removeIf(entry -> {
            Location value = entry.getValue();
            return value != null
                && world.equals(value.getWorld())
                && area.contains(value.getX(), value.getY(), value.getZ());
        });
    }

    private boolean isWatheDoorInteraction(Interaction interaction) {
        Byte marker = interaction.getPersistentDataContainer().get(doorInteractionMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == DOOR_ENTITY_MARKER;
    }

    private boolean isLikelyLegacyDoorInteraction(Interaction interaction) {
        if (Math.abs(interaction.getInteractionWidth() - 0.2f) > 0.001f) {
            return false;
        }
        if (Math.abs(interaction.getInteractionHeight() - 2.0f) > 0.001f) {
            return false;
        }
        return hasNearbyWatheDoor(interaction.getLocation());
    }

    private boolean hasNearbyWatheDoor(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        Block base = center.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = world.getBlockAt(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
                    if (state == null) {
                        continue;
                    }
                    if (parseDoorInfo(block, state) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private BlockDisplay spawnClosedDoorDisplay(Location pos, BlockData blockData) {
        Location spawnLoc = pos.clone().add(0.5, 0, 0.5);

        BlockDisplay display = (BlockDisplay) pos.getWorld().spawnEntity(spawnLoc, EntityType.BLOCK_DISPLAY);
        display.setBlock(Material.AIR.createBlockData());
        display.setTransformation(createClosedVisibleTransform());
        return display;
    }

    private void startDoorAnimation(
        Location lowerPos,
        BlockDisplay lowerDisplay,
        BlockDisplay upperDisplay,
        @Nullable Interaction interaction,
        Vector3f slideDirection,
        boolean opening
    ) {
        UUID sequenceId = UUID.randomUUID();
        animationSequenceIds.put(lowerPos, sequenceId);

        if (!opening && interaction != null) {
            interaction.setResponsive(false);
        }

        for (int step = 0; step < ANIMATION_SEGMENTS; step++) {
            long delay = ANIMATION_HOLD_TICKS + ((long) step * ANIMATION_SEGMENT_TICKS);
            float progress = opening
                ? easeOutExpo((step + 1f) / ANIMATION_SEGMENTS)
                : 1f - easeOutExpo((step + 1f) / ANIMATION_SEGMENTS);
            if (!opening && step == ANIMATION_SEGMENTS - 1) {
                progress = 0f;
            }

            final float currentProgress = progress;
            final boolean finalStep = step == ANIMATION_SEGMENTS - 1;
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (!sequenceId.equals(animationSequenceIds.get(lowerPos))) {
                    return;
                }
                if (!lowerDisplay.isValid() || !upperDisplay.isValid()) {
                    return;
                }

                applyAnimationStep(lowerDisplay, slideDirection, currentProgress);
                applyAnimationStep(upperDisplay, slideDirection, currentProgress);
                if (interaction != null && interaction.isValid() && !opening && finalStep) {
                    interaction.setResponsive(false);
                }
            }, delay);
        }
    }

    private void applyAnimationStep(BlockDisplay display, Vector3f slideDirection, float progress) {
        display.setInterpolationDuration(ANIMATION_SEGMENT_TICKS);
        display.setInterpolationDelay(0);
        display.setTransformation(createTransform(slideDirection, progress));
    }

    private float easeOutExpo(float progress) {
        if (progress >= 1f) {
            return 1f;
        }
        return 1f - (float) Math.pow(2.0, -10.0 * progress);
    }

    private Transformation createTransform(Vector3f slideDirection, float progress) {
        return new Transformation(
            new Vector3f(
                -0.5f + slideDirection.x * SLIDE_DISTANCE * progress,
                0,
                -0.5f + slideDirection.z * SLIDE_DISTANCE * progress
            ),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(1, 1, 1),
            new AxisAngle4f(0, 0, 0, 1)
        );
    }

    private Transformation createClosedVisibleTransform() {
        return new Transformation(
            new Vector3f(-0.5f, 0, -0.5f),
            new AxisAngle4f(0, 0, 0, 1),
            new Vector3f(1, 1, 1),
            new AxisAngle4f(0, 0, 0, 1)
        );
    }

    private Location getInteractionLocation(Location lowerPos, Vector3f slideDirection) {
        return lowerPos.clone().add(
            0.5 + slideDirection.x * 0.45,
            0,
            0.5 + slideDirection.z * 0.45
        );
    }

    private void setInteractionPosition(Interaction interaction, Location lowerPos, Vector3f slideDirection) {
        interaction.teleport(getInteractionLocation(lowerPos, slideDirection));
    }

    private void closeDoor(Location lowerPos) {
        closeDoor(lowerPos, true);
    }

    private void closeDoor(Location lowerPos, boolean closePairedDoor) {
        if (closePairedDoor) {
            Location pairedPos = pairedDoors.remove(lowerPos);
            if (pairedPos != null) {
                pairedDoors.remove(pairedPos);
                closeDoor(pairedPos, false);
            }
        }

        AnimatingDoor animatingDoor = animatingDoors.get(lowerPos);
        if (animatingDoor == null) {
            return;
        }

        ScheduledTask autoCloseTask = autoCloseTasks.remove(lowerPos);
        if (autoCloseTask != null) {
            autoCloseTask.cancel();
        }

        var lowerEntity = Bukkit.getEntity(animatingDoor.lowerDisplayId);
        var upperEntity = Bukkit.getEntity(animatingDoor.upperDisplayId);

        if (lowerEntity instanceof BlockDisplay lowerDisplay && upperEntity instanceof BlockDisplay upperDisplay) {
            Interaction interaction = null;
            var interactionEntity = Bukkit.getEntity(animatingDoor.interactionId);
            if (interactionEntity instanceof Interaction existingInteraction) {
                interaction = existingInteraction;
            }
            lowerPos.getWorld().playSound(lowerPos, "wathe:block.door.toggle", 1.0f, 1.0f);
            startDoorAnimation(lowerPos, lowerDisplay, upperDisplay, interaction, animatingDoor.slideDirection, false);

            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                finishCloseDoor(lowerPos, animatingDoor);
            }, TOTAL_ANIMATION_TICKS);
        } else {
            animatingDoors.remove(lowerPos);
            animationSequenceIds.remove(lowerPos);
            pairedDoors.remove(lowerPos);
        }
    }

    private void finishCloseDoor(Location lowerPos, AnimatingDoor animatingDoor) {
        var interactionEntity = Bukkit.getEntity(animatingDoor.interactionId);
        if (interactionEntity instanceof Interaction interaction) {
            interaction.setResponsive(false);
        }
        animationSequenceIds.remove(lowerPos);

        DoorVisual visual = doorVisuals.get(lowerPos);
        if (visual != null) {
            Block lowerBlock = lowerPos.getBlock();
            Block upperBlock = lowerPos.clone().add(0, 1, 0).getBlock();
            lowerBlock.setBlockData(visual.lowerBlockData, false);
            upperBlock.setBlockData(visual.upperBlockData, false);
        }

        var lowerEntity = Bukkit.getEntity(animatingDoor.lowerDisplayId);
        var upperEntity = Bukkit.getEntity(animatingDoor.upperDisplayId);
        if (lowerEntity instanceof BlockDisplay lowerDisplay) {
            lowerDisplay.setBlock(Material.AIR.createBlockData());
            lowerDisplay.setInterpolationDuration(0);
            lowerDisplay.setInterpolationDelay(0);
            lowerDisplay.setTransformation(createClosedVisibleTransform());
        }
        if (upperEntity instanceof BlockDisplay upperDisplay) {
            upperDisplay.setBlock(Material.AIR.createBlockData());
            upperDisplay.setInterpolationDuration(0);
            upperDisplay.setInterpolationDelay(0);
            upperDisplay.setTransformation(createClosedVisibleTransform());
        }

        animatingDoors.remove(lowerPos);
        pairedDoors.remove(lowerPos);
    }

    private Vector3f getSlideDirection(BlockFace facing, boolean hingeRight) {
        return switch (facing) {
            case NORTH -> new Vector3f(hingeRight ? -1 : 1, 0, 0);
            case SOUTH -> new Vector3f(hingeRight ? 1 : -1, 0, 0);
            case EAST -> new Vector3f(0, 0, hingeRight ? -1 : 1);
            case WEST -> new Vector3f(0, 0, hingeRight ? 1 : -1);
            default -> new Vector3f(1, 0, 0);
        };
    }

    public void cleanup() {
        for (ScheduledTask task : autoCloseTasks.values()) {
            task.cancel();
        }
        autoCloseTasks.clear();
        animatingDoors.clear();
        pairedDoors.clear();
        animationSequenceIds.clear();

        for (DoorVisual visual : doorVisuals.values()) {
            var lowerEntity = Bukkit.getEntity(visual.lowerDisplayId);
            var upperEntity = Bukkit.getEntity(visual.upperDisplayId);
            var interaction = Bukkit.getEntity(visual.interactionId);

            if (lowerEntity != null) {
                lowerEntity.remove();
            }
            if (upperEntity != null) {
                upperEntity.remove();
            }
            if (interaction != null) {
                interaction.remove();
            }
            Block lowerBlock = visual.position.getBlock();
            Block upperBlock = visual.position.clone().add(0, 1, 0).getBlock();
            lowerBlock.setBlockData(visual.lowerBlockData, false);
            upperBlock.setBlockData(visual.upperBlockData, false);
        }

        doorVisuals.clear();
        interactionToDoor.clear();
        knownDoors.clear();

        for (World world : Bukkit.getWorlds()) {
            clearInteractionEntitiesInWorld(world);
        }
    }

    public boolean isDoorAnimating(Location lowerPos) {
        return animatingDoors.containsKey(lowerPos);
    }

    public boolean tryCloseDoorByInteraction(Interaction interaction) {
        UUID interactionId = interaction.getUniqueId();

        for (var entry : animatingDoors.entrySet()) {
            AnimatingDoor door = entry.getValue();
            if (door.interactionId.equals(interactionId)) {
                closeDoor(entry.getKey());
                return true;
            }
        }

        Location lowerPos = interactionToDoor.get(interactionId);
        if (lowerPos == null) {
            return false;
        }

        DoorInfo doorInfo = resolveDoorInfoAtLowerPos(lowerPos);
        if (doorInfo == null) {
            purgeDoorState(lowerPos);
            return true;
        }

        knownDoors.put(lowerPos, doorInfo);
        return tryOpenDoor(doorInfo);
    }

    private record AnimatingDoor(
        Location position,
        UUID lowerDisplayId,
        UUID upperDisplayId,
        UUID interactionId,
        Vector3f slideDirection
    ) {}

    private record DoorVisual(
        Location position,
        UUID lowerDisplayId,
        UUID upperDisplayId,
        UUID interactionId,
        BlockData lowerBlockData,
        BlockData upperBlockData
    ) {}

    private record DoorInfo(
        Location lowerPos,
        ImmutableBlockState blockState,
        String doorId,
        BlockFace facing,
        boolean hingeRight
    ) {}

    private record NeighborDoorMatch(
        DoorInfo door,
        @Nullable Vector3f forcedSlideDirection,
        boolean sameFacing,
        @Nullable BlockFace offsetFromMain
    ) {}
}

package com.xy2407.nsukaddition.common.industrial;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xy2407.nsukaddition.common.industrial.FlowerBonemealHelper;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialCarriedItemService;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 花棚花卉培育步骤：扫描建筑界限内 Y 偏移层的花朵方块，逐个移动-骨粉催熟-收集掉落物，一轮后回 output 存箱。
 */
@SuppressWarnings("null")
public final class FlowerFertilizeService {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_MIN_Y_OFFSET = 1;
    private static final int DEFAULT_MAX_Y_OFFSET = 2;
    private static final double ARRIVE_DISTANCE_SQ = 4.0D;

    public enum Result {
        PROGRESSED,
        WAITING,
        WAITING_RETRY
    }

    private FlowerFertilizeService() {
    }

    public static Result execute(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data,
                                 PlacedBuildingRecord building, IndustrialDefinition definition,
                                 IndustrialDefinition.StepDefinition step,
                                 CitizenData worker, CitizenEntity entity) {
        if (level == null || data == null || building == null || entity == null) {
            return Result.WAITING_RETRY;
        }
        State state = State.read(data.machineState());
        if (!hasBoneMeal(level, building, definition)) {
            requestMoveToWork(level, entity, building, definition);
            data.setMachineState("");
            manager.persist(data);
            return Result.WAITING_RETRY;
        }
        if (state == null) {
            state = scanFlowers(level, building, step);
            if (state.flowers.isEmpty()) {
                data.setMachineState("");
                manager.persist(data);
                return Result.WAITING_RETRY;
            }
        }
        if (state.index >= state.flowers.size()) {
            data.setMachineState("");
            manager.persist(data);
            return Result.PROGRESSED;
        }

        BlockPos target = state.flowers.get(state.index);
        switch (state.phase) {
            case 0 -> {
                if (entity.position().distanceToSqr(Vec3.atCenterOf(target)) > ARRIVE_DISTANCE_SQ) {
                    CitizenNavigationService.requestMove(level, entity.getUUID(), Vec3.atCenterOf(target), MovementIntent.WORK);
                    return Result.WAITING;
                }
                state.phase = 1;
            }
            case 1 -> {
                if (!bonemealFlower(level, manager, data, building, definition, target, entity, step)) {
                    return Result.WAITING_RETRY;
                }
                state.phase = 2;
            }
            case 2 -> {
                if (!collectDrops(level, manager, data, target)) {
                    data.setMachineState("");
                    manager.persist(data);
                    return Result.PROGRESSED;
                }
                state.index++;
                state.phase = 0;
            }
            default -> state.phase = 0;
        }
        data.setMachineState(state.serialize());
        manager.persist(data);
        return Result.WAITING;
    }

    private static State scanFlowers(ServerLevel level, PlacedBuildingRecord building,
                                     IndustrialDefinition.StepDefinition step) {
        State state = new State();
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY()) + minYOffset(step);
        int maxY = Math.min(building.minPos().getY(), building.maxPos().getY()) + maxYOffset(step);
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX());
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(BlockTags.FLOWERS)) {
                        state.flowers.add(pos.immutable());
                    }
                }
            }
        }
        state.flowers.sort(Comparator.comparingInt((BlockPos p) -> p.getX())
                .thenComparingInt((BlockPos p) -> p.getZ())
                .thenComparingInt((BlockPos p) -> p.getY()));
        return state;
    }

    private static boolean bonemealFlower(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data,
                                          PlacedBuildingRecord building, IndustrialDefinition definition,
                                          BlockPos pos, CitizenEntity entity, IndustrialDefinition.StepDefinition step) {
        if (!level.getBlockState(pos).is(BlockTags.FLOWERS)) {
            return true;
        }
        ItemStack boneMeal = extractBoneMeal(level, building, definition);
        if (boneMeal.isEmpty()) {
            return false;
        }
        FlowerBonemealHelper.bonemealFlowerDrop(level, pos);
        return true;
    }

    private static boolean hasBoneMeal(ServerLevel level, PlacedBuildingRecord building,
                                       IndustrialDefinition definition) {
        if (definition == null || definition.containers() == null) {
            return false;
        }
        IndustrialDefinition.ContainerDefinition input = definition.containers().get("input");
        if (input == null) {
            return false;
        }
        List<BlockPos> containers = IndustrialCoordinateResolver.resolvePositions(building, input.positions());
        for (BlockPos cp : containers) {
            if (cp == null || !level.isLoaded(cp)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(cp);
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack slot = c.getItem(i);
                    if (!slot.isEmpty() && slot.is(Items.BONE_MEAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void requestMoveToWork(ServerLevel level, CitizenEntity entity,
                                          PlacedBuildingRecord building, IndustrialDefinition definition) {
        if (definition == null || definition.points() == null || entity == null) {
            return;
        }
        IndustrialDefinition.PointDefinition work = definition.points().get("work");
        if (work == null || work.positions() == null || work.positions().isEmpty()) {
            return;
        }
        List<BlockPos> points = IndustrialCoordinateResolver.resolvePositions(building, work.positions());
        if (points.isEmpty() || points.getFirst() == null) {
            return;
        }
        BlockPos workPos = points.getFirst();
        if (entity.position().distanceToSqr(Vec3.atBottomCenterOf(workPos)) > ARRIVE_DISTANCE_SQ) {
            CitizenNavigationService.requestMove(level, entity.getUUID(), Vec3.atBottomCenterOf(workPos), MovementIntent.WORK);
        }
    }

    private static ItemStack extractBoneMeal(ServerLevel level, PlacedBuildingRecord building,
                                             IndustrialDefinition definition) {
        if (definition == null || definition.containers() == null) {
            return ItemStack.EMPTY;
        }
        IndustrialDefinition.ContainerDefinition input = definition.containers().get("input");
        if (input == null) {
            return ItemStack.EMPTY;
        }
        List<BlockPos> containers = IndustrialCoordinateResolver.resolvePositions(building, input.positions());
        for (BlockPos cp : containers) {
            if (cp == null || !level.isLoaded(cp)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(cp);
            if (!(be instanceof Container c)) {
                continue;
            }
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack slot = c.getItem(i);
                if (!slot.isEmpty() && slot.is(Items.BONE_MEAL)) {
                    ItemStack taken = slot.split(1);
                    c.setChanged();
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean collectDrops(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data,
                                        BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.5D);
        List<ItemStack> pickup = new ArrayList<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (itemEntity == null || itemEntity.isRemoved()) {
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                pickup.add(stack.copy());
                itemEntity.discard();
            }
        }
        if (!pickup.isEmpty()) {
            return IndustrialCarriedItemService.addItems(level, manager, data, pickup);
        }
        return true;
    }

    private static int minYOffset(IndustrialDefinition.StepDefinition step) {
        return step != null && step.positions() != null && step.positions().size() >= 1
                && step.positions().getFirst() != null ? step.positions().getFirst().getY() : DEFAULT_MIN_Y_OFFSET;
    }

    private static int maxYOffset(IndustrialDefinition.StepDefinition step) {
        return step != null && step.positions() != null && step.positions().size() >= 2
                && step.positions().get(1) != null ? step.positions().get(1).getY() : DEFAULT_MAX_Y_OFFSET;
    }

    private static final class State {
        final List<BlockPos> flowers = new ArrayList<>();
        int index;
        int phase;

        String serialize() {
            JsonObject obj = new JsonObject();
            obj.addProperty("index", index);
            obj.addProperty("phase", phase);
            JsonArray arr = new JsonArray();
            for (BlockPos p : flowers) {
                JsonArray e = new JsonArray();
                e.add(p.getX());
                e.add(p.getY());
                e.add(p.getZ());
                arr.add(e);
            }
            obj.add("flowers", arr);
            return GSON.toJson(obj);
        }

        static State read(String machineState) {
            if (machineState == null || machineState.isBlank()) {
                return null;
            }
            try {
                JsonObject obj = JsonParser.parseString(machineState).getAsJsonObject();
                State s = new State();
                s.index = obj.has("index") ? obj.get("index").getAsInt() : 0;
                s.phase = obj.has("phase") ? obj.get("phase").getAsInt() : 0;
                JsonArray arr = obj.getAsJsonArray("flowers");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        JsonArray e = el.getAsJsonArray();
                        s.flowers.add(new BlockPos(e.get(0).getAsInt(), e.get(1).getAsInt(), e.get(2).getAsInt()));
                    }
                }
                return s;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
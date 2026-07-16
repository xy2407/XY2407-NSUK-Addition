package com.xy2407.nsukaddition.common.mining;

import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.vein.OreVeinDropProcessor;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 挖矿系统核心工作服务，负责每 tick 驱动挖矿逻辑与逐层挖掘。 */
@SuppressWarnings("null")
public final class MiningWorkService {

    private static OreVeinDropProcessor veinDropProcessor;

    private MiningWorkService() {}

    public static void setVeinDropProcessor(OreVeinDropProcessor processor) {
        veinDropProcessor = processor;
    }

    public static void tick(ServerLevel level) {
        if (level == null) return;
        MiningBoxManager manager = MiningBoxManager.get(level);
        for (MiningBoxData data : manager.all()) {
            if (!data.running()) continue;
            tickBox(level, manager, data);
        }
    }

    private static void tickBox(ServerLevel level, MiningBoxManager manager, MiningBoxData data) {

        BlockPos boxPos = data.boxPos();

        CitizenData worker = MiningControlBoxService.findAssignedWorker(level, boxPos);
        if (worker == null) {
            data.setRunning(false);
            data.setStatusKey(MiningConstants.STATUS_NO_WORKER);
            manager.persist(data);
            return;
        }

        if (CitizenHomeRestService.isRestTime(level)) {
            CitizenEntity entity = common.cn.kafei.simukraft.citizen.CitizenTeleportService.findCitizenEntity(level, worker.uuid());
            if (entity != null) entity.setHasActiveVisualTask(false);
            data.setStatusKey(MiningConstants.STATUS_RESTING);
            return;
        }

        if (CitizenSelfFeedingService.isSelfFeeding(level, worker.uuid())) {
            CitizenEntity entity = common.cn.kafei.simukraft.citizen.CitizenTeleportService.findCitizenEntity(level, worker.uuid());
            if (entity != null) entity.setHasActiveVisualTask(false);
            data.setStatusKey(MiningConstants.STATUS_FEEDING);
            return;
        }

        data.setWorkTicks(data.workTicks() + 1);

        if (data.workTicks() < MiningConstants.TICKS_PER_LAYER) {
            return;
        }

        if (!MiningControlBoxService.isBoxBlockValid(level, boxPos)) {
            data.setRunning(false);
            manager.persist(data);
            return;
        }

        if (!MiningControlBoxService.hasUsablePickaxe(level, boxPos)) {
            data.setRunning(false);
            data.setStatusKey(MiningConstants.STATUS_NO_PICKAXE);
            manager.persist(data);
            return;
        }

        if (data.currentYLevel() <= MiningConstants.MIN_DEPTH) {
            data.setRunning(false);
            data.setStatusKey(MiningConstants.STATUS_MAX_DEPTH);
            manager.persist(data);
            return;
        }

        LayerResult result = mineLayer(level, boxPos, data.currentYLevel());

        applyDurability(level, boxPos, result.blocksBroken());

        switch (result.status()) {
            case SUCCESS -> {
                data.setCurrentYLevel(data.currentYLevel() - 1);
                data.setWorkTicks(0);
                data.setStatusKey(MiningConstants.STATUS_RUNNING);
                data.setStatusText("");
                manager.persist(data);
            }
            case NO_CONTAINER -> {
                data.setRunning(false);
                data.setStatusKey(MiningConstants.STATUS_NO_CONTAINER);
                manager.persist(data);
            }
            case CONTAINER_FULL -> {
                data.setWorkTicks(MiningConstants.TICKS_PER_LAYER - 1);
                data.setStatusKey(MiningConstants.STATUS_CONTAINER_FULL);
                manager.persist(data);
            }
        }
        MiningControlBoxService.broadcastViewUpdate(level, boxPos);
    }

    enum LayerStatus { SUCCESS, NO_CONTAINER, CONTAINER_FULL }

    private record LayerResult(LayerStatus status, int blocksBroken) {}

    private static LayerResult mineLayer(ServerLevel level, BlockPos boxPos, int y) {
        BlockPos containerPos = MiningControlBoxService.resolveContainerPos(level, boxPos);
        if (containerPos == null) return new LayerResult(LayerStatus.NO_CONTAINER, 0);

        Container container = containerAt(level, containerPos);
        if (container != null && isContainerFull(container)) return new LayerResult(LayerStatus.CONTAINER_FULL, 0);

        BlockPos mineStart = boxPos.south(1);
        List<ItemStack> drops = new ArrayList<>();
        int size = MiningConstants.MINE_AREA_SIZE;
        int blocksBroken = 0;

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x == 0 || x == size - 1 || z == 0 || z == size - 1) continue;

                BlockPos target = new BlockPos(mineStart.getX() + x, y, mineStart.getZ() + z);
                if (!level.isLoaded(target)) continue;

                BlockState state = level.getBlockState(target);
                if (state.isAir()) continue;
                if (state.getBlock() == Blocks.BEDROCK) continue;
                if (state.getDestroySpeed(level, target) < 0) continue;

                blocksBroken++;
                for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, target, level.getBlockEntity(target))) {
                    if (drop.isEmpty()) continue;
                    ItemStack processed = veinDropProcessor != null
                            ? veinDropProcessor.process(level, target, drop)
                            : drop;
                    drops.add(processed);
                }
                level.destroyBlock(target, false);
            }
        }

        if (!drops.isEmpty()) {
            for (ItemStack stack : drops) {
                insertOrDrop(level, containerPos, stack);
            }
        }
        return new LayerResult(LayerStatus.SUCCESS, blocksBroken);
    }

    private static void applyDurability(ServerLevel level, BlockPos boxPos, int blocksBroken) {
        if (blocksBroken <= 0) return;
        BlockEntity be = level.getBlockEntity(boxPos);
        if (be instanceof MiningControlBoxBlockEntity box) {
            box.applyPickaxeDurability(blocksBroken);
        }
    }

    private static boolean isContainerFull(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) return false;
            if (stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    private static Container containerAt(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    private static void insertOrDrop(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        Container container = containerAt(level, pos);
        if (container != null) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < container.getContainerSize() && !remaining.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) {
                    int place = Math.min(remaining.getCount(), container.getMaxStackSize());
                    container.setItem(i, remaining.split(place));
                    continue;
                }
                if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                    int room = Math.min(container.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount();
                    if (room <= 0) continue;
                    int move = Math.min(room, remaining.getCount());
                    slot.grow(move);
                    remaining.shrink(move);
                }
            }
            if (remaining.isEmpty()) return;
        }

        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy()));
    }
}

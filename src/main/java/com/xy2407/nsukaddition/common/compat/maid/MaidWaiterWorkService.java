package com.xy2407.nsukaddition.common.compat.maid;

import com.xy2407.nsukaddition.common.breeding.BreedingInventoryHelper;
import com.xy2407.nsukaddition.common.cooking.CookingWorkService;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinition;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆服务员工作服务：为 waiter 类型为 maid 的餐厅驱动女仆执行取菜 → 走向桌 → 上菜，
 * 行为与 SimU-Kraft 市民服务员（CookingWorkService.tickWaiter）对等。
 * maid 未安装或女仆实体未加载时静默跳过，不影响其余餐厅。
 */
@SuppressWarnings("null")
public final class MaidWaiterWorkService {

    private static final long TICK_INTERVAL = 20L;
    private static final float MOVE_SPEED = 0.45F;
    private static final double DESK_DIST_SQ = 4.0D;

    private static final Map<BlockPos, BoxRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private MaidWaiterWorkService() {}

    public static void tick(ServerLevel level) {
        if (level == null || !MaidWaiterBridge.isLoaded()) return;
        long gameTime = level.getGameTime();
        if (gameTime % TICK_INTERVAL != 0L) return;

        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        for (RestaurantBoxData data : manager.all()) {
            BlockPos boxPos = data.boxPos().immutable();
            for (RestaurantBoxData.MaidEntry entry : data.maidWaiters()) {
                LivingEntity maid = MaidWaiterBridge.findMaid(level, entry.uuid());
                if (maid == null) continue;
                if (MaidWaiterBridge.isRestaurantWorker(maid) && MaidWaiterBridge.isHomeRestricted(maid)) {
                    continue;
                }
                if (!MaidWaiterBridge.assignRestaurantJob(level, maid, boxPos)
                        && !MaidWaiterBridge.isRestaurantWorker(maid)) {
                    RestaurantControlBoxService.fireMaid(level, boxPos, entry.uuid());
                }
            }
            if (!data.running()) { removeRuntime(boxPos); continue; }
            if (!level.isLoaded(boxPos)) continue;
            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, boxPos);
            RestaurantDefinition definition = RestaurantDefinitionLoader.loadForBuilding(building).definition();
            if (definition == null || !definition.isMaidWaiter() || data.maidWaiters().isEmpty()) {
                removeRuntime(boxPos);
                continue;
            }
            BoxRuntime rt = RUNTIMES.computeIfAbsent(boxPos, k -> new BoxRuntime());
            rt.level = level; rt.boxPos = boxPos; rt.building = building; rt.definition = definition; rt.data = data;
            tickBox(level, rt, gameTime);
        }
        RUNTIMES.entrySet().removeIf(e -> {
            RestaurantBoxData d = manager.get(e.getKey());
            if (d == null || !d.running()) return true;
            RestaurantDefinition def = e.getValue().definition;
            return def == null || !def.isMaidWaiter() || d.maidWaiters().isEmpty();
        });
    }

    public static void clearAll() {
        RUNTIMES.clear();
    }

    private static void removeRuntime(BlockPos boxPos) {
        RUNTIMES.remove(boxPos);
    }

    private static void tickBox(ServerLevel level, BoxRuntime rt, long gameTime) {
        if (common.cn.kafei.simukraft.citizen.CitizenHomeRestService.isRestTime(level)) {
            rt.resetStage();
            return;
        }
        LivingEntity maid = rt.maidId != null ? MaidWaiterBridge.findMaid(level, rt.maidId) : null;
        if (maid == null || !MaidWaiterBridge.isRestaurantWorker(maid)) {
            maid = pickAvailableMaid(level, rt);
            if (maid == null) {
                rt.resetStage();
                return;
            }
            rt.maidId = maid.getUUID();
        }
        switch (rt.stage) {
            case 0 -> stageGrab(level, rt, maid);
            case 1 -> stageWalk(level, rt, maid);
            case 2 -> stageServe(level, rt, maid);
            default -> rt.stage = 0;
        }
    }

    private static LivingEntity pickAvailableMaid(ServerLevel level, BoxRuntime rt) {
        for (RestaurantBoxData.MaidEntry entry : rt.data.maidWaiters()) {
            LivingEntity maid = MaidWaiterBridge.findMaid(level, entry.uuid());
            if (maid != null && MaidWaiterBridge.isRestaurantWorker(maid)) return maid;
        }
        return null;
    }

    private static void stageGrab(ServerLevel level, BoxRuntime rt, LivingEntity maid) {
        RestaurantBoxData.OrderEntry order = null;
        for (var o : rt.data.orders()) {
            if (o.status() == RestaurantBoxData.OrderStatus.SERVING) continue;
            if (o.seatPos().equals(BlockPos.ZERO)) continue;
            order = o;
            break;
        }
        if (order == null) {
            BlockPos stand = resolveWaiterStandOrBox(rt);
            MaidWaiterBridge.updateRestaurantAnchor(maid, stand);
            if (MaidWaiterBridge.distToBlockSqr(maid, stand) > 4.0D) {
                MaidWaiterBridge.moveMaid(level, maid, stand, MOVE_SPEED);
            }
            return;
        }
        BlockPos deskPos = findAdjacentDesk(level, rt, order.seatPos());
        if (deskPos == null) return;
        List<BlockPos> outputs = CookingWorkService.resolvePositions(rt.building, rt.definition, "output", rt.boxPos);
        ItemStack food = CookingWorkService.takeMatchingFood(level, outputs, order.recipeId(), rt.definition, rt.building);
        if (food.isEmpty()) return;
        rt.carry = food;
        rt.desk = deskPos;
        rt.originalMainHand = maid.getMainHandItem().copy();
        maid.setItemInHand(InteractionHand.MAIN_HAND, food);
        rt.stage = 1;
    }

    private static BlockPos resolveWaiterStandOrBox(BoxRuntime rt) {
        BlockPos stand = resolveWaiterStand(rt);
        return stand != null ? stand : rt.boxPos;
    }

    private static BlockPos resolveWaiterStand(BoxRuntime rt) {
        if (rt.building == null || rt.definition == null) return null;
        List<BlockPos> stand = CookingWorkService.resolvePositions(rt.building, rt.definition, "waiter_work", rt.boxPos);
        if (stand.isEmpty()) {
            stand = CookingWorkService.resolvePositions(rt.building, rt.definition, "work", rt.boxPos);
        }
        return stand.isEmpty() ? null : stand.getFirst();
    }

    private static void stageWalk(ServerLevel level, BoxRuntime rt, LivingEntity maid) {
        if (rt.desk == null) { rt.stage = 0; return; }
        if (maid.position().distanceToSqr(Vec3.atCenterOf(rt.desk)) <= DESK_DIST_SQ) {
            rt.stage = 2;
            return;
        }
        MaidWaiterBridge.moveMaid(level, maid, rt.desk, MOVE_SPEED);
    }

    private static void stageServe(ServerLevel level, BoxRuntime rt, LivingEntity maid) {
        if (rt.carry.isEmpty() || rt.desk == null) { restoreHand(maid, rt); rt.stage = 0; return; }
        boolean ok = CookingWorkService.placeFoodOnDesk(level, rt.desk, rt.carry);
        if (ok) {
            for (var o : rt.data.orders()) {
                if (o.status() == RestaurantBoxData.OrderStatus.SERVING) continue;
                if (o.seatPos().equals(BlockPos.ZERO)) continue;
                if (rt.desk.equals(findAdjacentDesk(level, rt, o.seatPos()))) {
                    rt.data.orders().remove(o);
                    rt.data.orders().add(new RestaurantBoxData.OrderEntry(o.customerId(), o.seatPos(), o.recipeId(), RestaurantBoxData.OrderStatus.SERVING));
                    break;
                }
            }
            RestaurantBoxManager.get(level).persist(rt.data);
        } else {
            List<BlockPos> outputs = CookingWorkService.resolvePositions(rt.building, rt.definition, "output", rt.boxPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, rt.carry);
        }
        restoreHand(maid, rt);
        rt.stage = 0;
        boolean moreOrders = false;
        for (var o : rt.data.orders()) {
            if (o.status() == RestaurantBoxData.OrderStatus.SERVING) continue;
            if (o.seatPos().equals(BlockPos.ZERO)) continue;
            moreOrders = true;
            break;
        }
        if (!moreOrders) {
            BlockPos stand = resolveWaiterStandOrBox(rt);
            MaidWaiterBridge.updateRestaurantAnchor(maid, stand);
            if (MaidWaiterBridge.distToBlockSqr(maid, stand) > 4.0D) {
                MaidWaiterBridge.moveMaid(level, maid, stand, MOVE_SPEED);
            }
        }
    }

    private static void restoreHand(LivingEntity maid, BoxRuntime rt) {
        if (maid != null) {
            if (rt.originalMainHand != null && !rt.originalMainHand.isEmpty()) {
                maid.setItemInHand(InteractionHand.MAIN_HAND, rt.originalMainHand);
            } else {
                maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        rt.originalMainHand = ItemStack.EMPTY;
        rt.carry = ItemStack.EMPTY;
        rt.desk = null;
    }

    private static BlockPos findAdjacentDesk(ServerLevel level, BoxRuntime rt, BlockPos seatPos) {
        List<BlockPos> desks = CookingWorkService.resolvePositions(rt.building, rt.definition, "desk", rt.boxPos);
        for (BlockPos desk : desks) {
            if (desk.getY() != seatPos.getY()) continue;
            int dx = Math.abs(desk.getX() - seatPos.getX());
            int dz = Math.abs(desk.getZ() - seatPos.getZ());
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return desk;
            }
        }
        return null;
    }

    private static final class BoxRuntime {
        ServerLevel level;
        BlockPos boxPos;
        PlacedBuildingRecord building;
        RestaurantDefinition definition;
        RestaurantBoxData data;
        UUID maidId;
        int stage;
        ItemStack carry = ItemStack.EMPTY;
        ItemStack originalMainHand = ItemStack.EMPTY;
        BlockPos desk;

        void resetStage() {
            stage = 0;
            carry = ItemStack.EMPTY;
            originalMainHand = ItemStack.EMPTY;
            desk = null;
        }
    }
}
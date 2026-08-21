package com.xy2407.nsukaddition.common.cooking;

import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.decoration.TableBlockEntity;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.TouristNpcHelper;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData.OrderStatus;
import com.xy2407.nsukaddition.common.entity.SitEntity;
import com.xy2407.nsukaddition.common.network.cooking.DiningOrderSyncPacket;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxSqliteStorage;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkplaceMoveService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 餐厅就餐服务，管理 NPC 顾客的就餐状态机。NPC 按点菜顺序检查相邻桌子取食。 */
@SuppressWarnings("null")
public final class RestaurantDiningService {
    private static final long DINE_COOLDOWN_TICKS = 12000L;
    private static final long MOVE_RETRY = 40L;
    private static final long CHECK_INTERVAL = 80L;
    private static final long TICK_INTERVAL = 20L;

    private static final Map<UUID, DiningRuntime> DINING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIDE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PREVIOUS_STATUS = new ConcurrentHashMap<>();

    private RestaurantDiningService() {}

    public static void tick(ServerLevel level) {
        if (level == null) return;
        long gameTime = level.getGameTime();
        if (gameTime % TICK_INTERVAL != 0L) return;

        DINING.values().removeIf(r -> tickDiner(level, r, gameTime));
        RIDE_COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= gameTime);
    }

    public static boolean isDining(UUID citizenId) { return DINING.containsKey(citizenId); }

    public static UUID findOccupantAt(BlockPos seatPos, BlockPos boxPos) {
        for (DiningRuntime rt : DINING.values()) {
            if (rt.seatPos.equals(seatPos) && rt.boxPos.equals(boxPos)) {
                return rt.citizenId;
            }
        }
        return null;
    }

    public static void reorderForMenu(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) return;
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(boxPos);
        if (data == null) return;
        for (DiningRuntime rt : DINING.values()) {
            if (!rt.boxPos.equals(boxPos)) continue;
            if (rt.state != DiningState.WAITING) continue;
            data.removeOrder(rt.citizenId);
            java.util.List<String> pool = data.selectedCookItems().isEmpty() ? rt.cook : new java.util.ArrayList<>(data.selectedCookItems());
            String outputItemId = pool.isEmpty() ? "" : pool.get(level.random.nextInt(pool.size()));
            if (!outputItemId.isEmpty()) {
                data.addOrder(rt.citizenId, rt.seatPos, outputItemId);
                syncOrderToClient(level, rt.citizenId, outputItemId, true, rt.isTourist);
            }
        }
        manager.persist(data);
    }

    public static boolean isOnRideCooldown(UUID citizenId, long gameTime) {
        Long cooldown = RIDE_COOLDOWNS.get(citizenId);
        return cooldown != null && cooldown > gameTime;
    }

    public static boolean startDining(ServerLevel level, CitizenData citizen, PlacedBuildingRecord building,
                                       RestaurantDefinition definition, RestaurantBoxData data) {
        if (citizen == null || building == null || definition == null || data == null) return false;
        List<BlockPos> worldSeats = IndustrialCoordinateResolver.resolvePositions(building, definition.allSeatPositions());
        BlockPos freeSeat = null;
        for (BlockPos worldSeat : worldSeats) {
            if (data.isSeatFree(worldSeat)) { freeSeat = worldSeat; break; }
        }
        if (freeSeat == null) return false;

        data.occupySeat(freeSeat);
        RestaurantBoxSqliteStorage.occupySeat(level, data.boxPos().asLong(), freeSeat.asLong());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity == null) return false;
        boolean isTourist = TouristNpcHelper.isTouristEntity(entity);
        DiningRuntime rt = new DiningRuntime(citizen.uuid(), data.boxPos(), freeSeat, definition.cook(),
                building.cityId(), definition.cookPrices(), definition.isMaidWaiter(), isTourist);
        String current = citizen.statusLabel();
        PREVIOUS_STATUS.put(citizen.uuid(),
                current != null && current.startsWith("gui.xy2407_nsuk_addition.cooking.dining.") ? "" : current);
        rt.lastMoveTick = level.getGameTime();
        CitizenNavigationService.requestMove(level, citizen.uuid(), Vec3.atBottomCenterOf(freeSeat), MovementIntent.SELF_FEEDING);
        setDiningStatus(level, citizen, RestaurantConstants.DINING_GOING);
        DINING.put(citizen.uuid(), rt);
        return true;
    }

    public static void forceFinish(ServerLevel level, UUID citizenId) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (entity != null) {
            entity.stopRiding();
            entity.setNoAi(false);
            entity.getNavigation().stop();
        }
        DiningRuntime rt = DINING.remove(citizenId);
        if (rt != null) {
            syncOrderToClient(level, citizenId, "", false, rt.isTourist);
            freeSeatFor(level, rt.boxPos, rt.seatPos);
            CitizenData citizen = findCitizen(level, citizenId);
            if (citizen != null) { restoreStatus(level, citizen); CitizenNavigationService.stop(level, citizenId); }
        }
    }

    public static void cleanupAllDiners(ServerLevel level) {
        for (DiningRuntime rt : new ArrayList<>(DINING.values())) {
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.citizenId);
            if (entity != null) {
                entity.stopRiding();
                entity.setNoAi(false);
                entity.getNavigation().stop();
            }
            syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
            freeSeatFor(level, rt.boxPos, rt.seatPos);
            CitizenData citizen = findCitizen(level, rt.citizenId);
            if (citizen != null) restoreStatus(level, citizen);
        }
        DINING.clear();
        PREVIOUS_STATUS.clear();
        RIDE_COOLDOWNS.clear();
    }

    public static void cleanupForBox(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) return;
        boolean changed = false;
        for (DiningRuntime rt : new ArrayList<>(DINING.values())) {
            if (!rt.boxPos.equals(boxPos)) continue;
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.citizenId);
            if (entity != null) {
                entity.stopRiding();
                entity.setNoAi(false);
                entity.getNavigation().stop();
            }
            syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
            freeSeatFor(level, rt.boxPos, rt.seatPos);
            CitizenData citizen = findCitizen(level, rt.citizenId);
            if (citizen != null) restoreStatus(level, citizen);
            RestaurantBoxManager mgr = RestaurantBoxManager.get(level);
            RestaurantBoxData data = mgr.get(boxPos);
            if (data != null) {
                data.orders().removeIf(o -> o.customerId().equals(rt.citizenId));
                mgr.persist(data);
            }
            DINING.remove(rt.citizenId);
            PREVIOUS_STATUS.remove(rt.citizenId);
            changed = true;
        }
        if (changed) {
            RIDE_COOLDOWNS.clear();
        }
    }

    private static void chargeForMeal(ServerLevel level, DiningRuntime rt, CitizenData citizen, String dishItemId) {
        if (rt.restaurantCityId == null || dishItemId == null || dishItemId.isBlank()) return;
        CitizenEntity diningEntity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        boolean isTourist = diningEntity != null && TouristNpcHelper.isTouristEntity(diningEntity);
        boolean isCaravan = diningEntity != null && TouristNpcHelper.isCaravanEntity(diningEntity);
        boolean isLocal = !isTourist && !isCaravan && rt.restaurantCityId.equals(citizen.cityId());
        if (isLocal) return;

        double basePrice = rt.cookPrices.getOrDefault(dishItemId, 0.0);
        if (basePrice <= 0) return;
        double multiplier = getCityLevelMultiplier(level, rt.restaurantCityId);
        double finalPrice = basePrice * multiplier;
        if (rt.maidService) {
            finalPrice += 3.0;
        }
        EconomyService.depositCityFunds(level, rt.restaurantCityId, null, finalPrice, "餐厅收入");
    }

    private static double getCityLevelMultiplier(ServerLevel level, UUID cityId) {
        if (cityId == null) return 1.0;
        var opt = CityService.findCity(level, cityId);
        if (opt.isEmpty()) return 1.0;
        CityData city = opt.get();
        return switch (CityLevel.fromLevel(city.cityLevel())) {
            case SETTLEMENT, VILLAGE -> 1.0;
            case TOWN -> 1.5;
            case CITY_STATE -> 2.0;
            case METROPOLIS -> 2.5;
        };
    }

    private static boolean finishDining(ServerLevel level, DiningRuntime rt, CitizenEntity entity, long gameTime) {
        if (entity.isPassenger()) {
            entity.stopRiding();
        }
        entity.setNoAi(false);
        entity.getNavigation().stop();
        CitizenData citizen = findCitizen(level, rt.citizenId);
        if (citizen != null) {
            restoreStatus(level, citizen);
            CitizenWorkplaceMoveService.returnToWorkplace(level, citizen);
        }
        return true;
    }

    private static Vec3 computeLeaveTarget(ServerLevel level, DiningRuntime rt, CitizenEntity entity) {
        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, rt.boxPos);
        if (building != null && building.minPos() != null && building.maxPos() != null) {
            BlockPos center = new BlockPos(
                    (building.minPos().getX() + building.maxPos().getX()) / 2,
                    rt.seatPos.getY(),
                    (building.minPos().getZ() + building.maxPos().getZ()) / 2);
            double angle = level.random.nextDouble() * Math.PI * 2;
            return Vec3.atBottomCenterOf(center).add(Math.cos(angle) * 15, 0, Math.sin(angle) * 15);
        }
        return entity.position().add(entity.getRandom().nextInt(10) - 5, 0, entity.getRandom().nextInt(10) - 5);
    }

    private static boolean tickDiner(ServerLevel level, DiningRuntime rt, long gameTime) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.citizenId);
        if (entity == null) {
            freeSeatFor(level, rt.boxPos, rt.seatPos);
            syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
            RestaurantBoxManager mgr0 = RestaurantBoxManager.get(level);
            RestaurantBoxData d0 = mgr0.get(rt.boxPos);
            if (d0 != null) {
                d0.orders().removeIf(o -> o.customerId().equals(rt.citizenId));
                mgr0.persist(d0);
            }
            CitizenData citizen = findCitizen(level, rt.citizenId);
            if (citizen != null) restoreStatus(level, citizen);
            return true;
        }

        switch (rt.state) {
            case GOING -> {
                if (!rt.isTourist && entity.getHungerValue() >= CitizenEntity.DEFAULT_HUNGER) {
                    freeSeatFor(level, rt.boxPos, rt.seatPos);
                    CitizenData citizen = findCitizen(level, rt.citizenId);
                    if (citizen != null) {
                        restoreStatus(level, citizen);
                    }
                    CitizenNavigationService.stop(level, rt.citizenId);
                    syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
                    return true;
                }
                if (gameTime - rt.lastMoveTick < MOVE_RETRY) return false;
                rt.lastMoveTick = gameTime;
                if (entity.position().distanceToSqr(Vec3.atBottomCenterOf(rt.seatPos)) <= 4.0D) {
                    sitDown(entity, rt.seatPos);
                    rt.state = DiningState.WAITING;
                    rt.lastCheckTick = gameTime;
                    setDiningStatus(level, findCitizen(level, rt.citizenId), RestaurantConstants.DINING_WAITING);
                    RestaurantBoxManager manager = RestaurantBoxManager.get(level);
                    RestaurantBoxData data = manager.get(rt.boxPos);
                    if (data != null) {
                        java.util.List<String> pool = data.selectedCookItems().isEmpty() ? rt.cook : new java.util.ArrayList<>(data.selectedCookItems());
                        String outputItemId = pool.isEmpty() ? "" : pool.get(level.random.nextInt(pool.size()));
                        data.addOrder(rt.citizenId, rt.seatPos, outputItemId);
                        manager.persist(data);
                        syncOrderToClient(level, rt.citizenId, outputItemId, true, rt.isTourist);
                    }
                } else {
                    CitizenNavigationService.requestMove(level, rt.citizenId, Vec3.atBottomCenterOf(rt.seatPos), MovementIntent.SELF_FEEDING);
                }
            }
            case WAITING -> {
                if (!entity.isPassenger()) {
                    freeSeatFor(level, rt.boxPos, rt.seatPos);
                    CitizenData citizen = findCitizen(level, rt.citizenId);
                    if (citizen != null) { restoreStatus(level, citizen); CitizenNavigationService.stop(level, rt.citizenId); }
                    syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
                    RestaurantBoxManager mgrExit = RestaurantBoxManager.get(level);
                    RestaurantBoxData dExit = mgrExit.get(rt.boxPos);
                    if (dExit != null) {
                        dExit.orders().removeIf(o -> o.customerId().equals(rt.citizenId));
                        mgrExit.persist(dExit);
                    }
                    return true;
                }
                if (!rt.isTourist && entity.getHungerValue() >= CitizenEntity.DEFAULT_HUNGER) {
                    RestaurantBoxManager mgr = RestaurantBoxManager.get(level);
                    RestaurantBoxData d = mgr.get(rt.boxPos);
                    if (d != null) {
                        d.orders().removeIf(o -> o.customerId().equals(rt.citizenId));
                        mgr.persist(d);
                    }
                    syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
                    rt.state = DiningState.LEAVING;
                    rt.leavingStartTick = gameTime;
                    return false;
                }
                if (gameTime - rt.lastCheckTick < CHECK_INTERVAL) return false;
                rt.lastCheckTick = gameTime;

                RestaurantBoxManager manager = RestaurantBoxManager.get(level);
                RestaurantBoxData data = manager.get(rt.boxPos);
                if (data == null) { freeSeatFor(level, rt.boxPos, rt.seatPos); return true; }

                ItemStack food = checkAdjacentDesk(level, rt);
                if (food.isEmpty()) return false;

                entity.setHunger(CitizenEntity.DEFAULT_HUNGER);
                entity.swing(InteractionHand.MAIN_HAND);
                level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);

                String dishName = "未知";
                for (var o : data.orders()) {
                    if (o.customerId().equals(rt.citizenId)) {
                        dishName = o.recipeId();
                        break;
                    }
                }

                CitizenData diner = findCitizen(level, rt.citizenId);
                if (diner != null) {
                    chargeForMeal(level, rt, diner, dishName);
                }

                data.orders().removeIf(o -> o.customerId().equals(rt.citizenId));
                manager.persist(data);

                rt.state = DiningState.LEAVING;
                rt.leavingStartTick = gameTime;
            }
            case LEAVING -> {
                if (!rt.leavingInit) {
                    rt.leavingInit = true;
                    entity.stopRiding();
                    entity.setNoAi(false);
                    CitizenNavigationService.stop(level, rt.citizenId);
                    entity.getNavigation().stop();
                    RIDE_COOLDOWNS.put(rt.citizenId, gameTime + DINE_COOLDOWN_TICKS);
                    syncOrderToClient(level, rt.citizenId, "", false, rt.isTourist);
                    freeSeatFor(level, rt.boxPos, rt.seatPos);
                    Vec3 awayTarget = computeLeaveTarget(level, rt, entity);
                    CitizenNavigationService.requestMove(level, rt.citizenId, awayTarget, MovementIntent.WALK);
                    return false;
                }
                if (gameTime - rt.leavingStartTick > 300L || !CitizenNavigationService.isNavigating(level, rt.citizenId)) {
                    return finishDining(level, rt, entity, gameTime);
                }
                return false;
            }
        }
        return false;
    }

    private static ItemStack checkAdjacentDesk(ServerLevel level, DiningRuntime rt) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(rt.boxPos);
        if (data == null) return ItemStack.EMPTY;

        String outputItemId = null;
        for (var o : data.orders()) {
            if (o.customerId().equals(rt.citizenId)) {
                outputItemId = o.recipeId();
                break;
            }
        }
        if (outputItemId == null) return ItemStack.EMPTY;

        ResourceLocation targetId = ResourceLocation.tryParse(outputItemId);
        if (targetId == null) return ItemStack.EMPTY;

        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, rt.boxPos);
        RestaurantDefinitionLoader.LoadResult load = RestaurantDefinitionLoader.loadForBuilding(building);
        if (load.definition() == null) return ItemStack.EMPTY;

        List<BlockPos> desks = CookingWorkService.resolvePositions(building, load.definition(), "desk", rt.boxPos);
        for (BlockPos desk : desks) {
            if (desk.getY() != rt.seatPos.getY()) continue;
            int dx = Math.abs(desk.getX() - rt.seatPos.getX());
            int dz = Math.abs(desk.getZ() - rt.seatPos.getZ());
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                if (!level.isLoaded(desk)) continue;
                BlockEntity be = level.getBlockEntity(desk);
                if (be instanceof TableBlockEntity table) {
                    var handler = table.getItems();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack slot = handler.getStackInSlot(i);
                        if (!slot.isEmpty() && BuiltInRegistries.ITEM.getKey(slot.getItem()).equals(targetId)) {
                            ItemStack taken = handler.extractItem(i, 1, false);
                            table.refresh();
                            return taken;
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void freeSeatFor(ServerLevel level, BlockPos boxPos, BlockPos seatPos) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).get(boxPos);
        if (data != null) data.freeSeat(seatPos);
        RestaurantBoxSqliteStorage.freeSeat(level, boxPos.asLong(), seatPos.asLong());
    }

    private static void setDiningStatus(ServerLevel level, CitizenData citizen, String statusKey) {
        if (citizen == null) return;
        citizen.setStatusLabel(statusKey);
        citizen.setWorkNeedDetail("");
        CitizenService.save(level, citizen.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null) CitizenManager.get(level).syncEntity(entity);
    }

    private static void restoreStatus(ServerLevel level, CitizenData citizen) {
        if (citizen == null) return;
        String prev = PREVIOUS_STATUS.remove(citizen.uuid());
        citizen.setStatusLabel(prev != null ? prev : "");
        citizen.setWorkNeedDetail("");
        CitizenService.save(level, citizen.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizen.uuid());
        if (entity != null) CitizenManager.get(level).syncEntity(entity);
    }

    private static void sitDown(CitizenEntity entity, BlockPos seatPos) {
        if (entity == null || entity.level().isClientSide) return;
        SitEntity sit = new SitEntity(entity.level(), seatPos);
        entity.level().addFreshEntity(sit);
        entity.startRiding(sit, true);
    }

    private static CitizenData findCitizen(ServerLevel level, UUID id) {
        return CitizenTeleportService.findCitizenEntity(level, id) != null
                ? CitizenService.findCitizen(level, id).orElse(null) : null;
    }

    private enum DiningState { GOING, WAITING, LEAVING }

    private static final class DiningRuntime {
        final UUID citizenId;
        final BlockPos boxPos;
        final BlockPos seatPos;
        final List<String> cook;
        final UUID restaurantCityId;
        final Map<String, Double> cookPrices;
        final boolean maidService;
        final boolean isTourist;
        DiningState state = DiningState.GOING;
        long lastMoveTick, lastCheckTick;
        long leavingStartTick;
        boolean leavingInit;
        DiningRuntime(UUID citizenId, BlockPos boxPos, BlockPos seatPos, List<String> cook,
                      UUID restaurantCityId, Map<String, Double> cookPrices, boolean maidService, boolean isTourist) {
            this.citizenId = citizenId; this.boxPos = boxPos.immutable(); this.seatPos = seatPos.immutable();
            this.cook = cook != null ? List.copyOf(cook) : List.of();
            this.restaurantCityId = restaurantCityId;
            this.cookPrices = cookPrices != null ? Map.copyOf(cookPrices) : Map.of();
            this.maidService = maidService;
            this.isTourist = isTourist;
        }
    }

    private static void syncOrderToClient(ServerLevel level, UUID citizenId, String itemId, boolean start, boolean isTourist) {
        var packet = new DiningOrderSyncPacket(citizenId, itemId, start, isTourist);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private static String resolveResultItem(String recipeId) {
        return recipeId != null ? recipeId : "";
    }
}
package com.xy2407.nsukaddition.common.cooking;

import com.xy2407.nsukaddition.common.breeding.BreedingInventoryHelper;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenFoodConsumptionService;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import common.cn.kafei.simukraft.medical.MedicalMealService;
import common.cn.kafei.simukraft.medical.MedicalService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * 医院外卖接轨餐厅逻辑：病人饥饿时远程向同城市餐厅点菜，
 * 厨房制作完成后由医生前往餐厅取餐并返回医院分发给病人。
 * 城市内病人收取菜品原价 1/2 的费用。
 */
public final class HospitalMealOrderService {
    private static final long ORDER_SCAN_INTERVAL = 100L;
    private static final long TICK_INTERVAL = 20L;
    private static final long MOVE_RETRY_TICKS = 40L;
    private static final long STALE_ORDER_TICKS = 2000L;
    private static final double ARRIVAL_DISTANCE_SQR = 16.0D;
    private static final double HUNGER_THRESHOLD = 5.0D;
    private static final double PATIENT_PRICE_MULTIPLIER = 0.5D;

    private static final BlockPos HOSPITAL_SEAT_MARKER = BlockPos.ZERO;

    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private HospitalMealOrderService() {}

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long gameTime = level.getGameTime();
        if (gameTime % TICK_INTERVAL != 0L) return;

        LevelRuntime runtime = runtime(level);
        tickDeliveries(level, runtime, gameTime);
        checkStaleOrders(level, runtime, gameTime);
        checkReadyOrdersAndStartDelivery(level, runtime, gameTime);
        if (gameTime % ORDER_SCAN_INTERVAL == 0L) {
            scanPatientsAndOrder(level, runtime);
        }
    }

    private static void scanPatientsAndOrder(ServerLevel level, LevelRuntime runtime) {
        long currentDay = level.getDayTime() / 24_000L;
        long gameTime = level.getGameTime();
        runtime.orderedToday.entrySet().removeIf(e -> e.getValue() < currentDay);
        if (!runtime.orderedToday.isEmpty()) {
            markPersisted(level);
        }

        List<HospitalContext> hospitals = findOperationalHospitals(level);

        for (HospitalContext hospital : hospitals) {
            CitizenData doctor = CitizenService.findCitizen(level, hospital.doctorId).orElse(null);
            if (doctor == null || doctor.dead()) continue;

            for (UUID patientId : hospital.patientIds) {
                if (runtime.orderedToday.containsKey(patientId)) continue;
                if (runtime.orderTargets.containsKey(patientId)) continue;
                CitizenData patient = CitizenService.findCitizen(level, patientId).orElse(null);
                if (patient == null || patient.dead()) continue;
                if (patient.medical().lastHospitalMealDay() >= currentDay) continue;
                if (!MedicalService.isAdmitted(patient)) continue;

                CitizenEntity patientEntity = CitizenTeleportService.findCitizenEntity(level, patientId);
                if (patientEntity == null || patientEntity.getHungerValue() >= HUNGER_THRESHOLD) continue;

                if (placeOrder(level, runtime, patientId, patient, hospital.boxPos, currentDay, gameTime)) {
                    runtime.orderedToday.put(patientId, currentDay);
                    markPersisted(level);
                }
            }
        }
    }

    private static boolean placeOrder(ServerLevel level, LevelRuntime runtime, UUID patientId,
                                       CitizenData patient, BlockPos hospitalBoxPos, long currentDay, long gameTime) {
        UUID cityId = patient.cityId();
        if (cityId == null) return false;
        RestaurantBoxManager restaurantManager = RestaurantBoxManager.get(level);
        RestaurantBoxData selectedRestaurant = pickRestaurant(level, restaurantManager, cityId);
        if (selectedRestaurant == null) return false;

        PlacedBuildingRecord restaurantBuilding = RestaurantControlBoxService.resolveBuilding(level, selectedRestaurant.boxPos());
        if (restaurantBuilding == null) return false;
        RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(restaurantBuilding);
        if (loadResult.definition() == null || loadResult.definition().cook().isEmpty()) return false;

        List<String> menu = selectedRestaurant.selectedCookItems().isEmpty()
                ? loadResult.definition().cook()
                : new ArrayList<>(selectedRestaurant.selectedCookItems());
        if (menu.isEmpty()) return false;

        String dishItemId = menu.get(level.random.nextInt(menu.size()));
        double dishPrice = loadResult.definition().cookPrices().getOrDefault(dishItemId, 0.0);

        selectedRestaurant.addOrder(patientId, HOSPITAL_SEAT_MARKER, dishItemId);
        restaurantManager.persist(selectedRestaurant);

        runtime.orderTargets.put(patientId, new OrderTarget(
                hospitalBoxPos, selectedRestaurant.boxPos(), dishItemId, dishPrice, cityId, currentDay, gameTime));
        markPersisted(level);
        return true;
    }

    private static RestaurantBoxData pickRestaurant(ServerLevel level, RestaurantBoxManager manager, UUID cityId) {
        List<RestaurantBoxData> candidates = new ArrayList<>();
        for (RestaurantBoxData data : manager.all()) {
            if (!data.running()) continue;
            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
            if (building == null || !cityId.equals(building.cityId())) continue;
            candidates.add(data);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(level.random.nextInt(candidates.size()));
    }

    private static void tickDeliveries(ServerLevel level, LevelRuntime runtime, long gameTime) {
        for (DeliveryRun run : List.copyOf(runtime.active.values())) {
            try {
                tickRun(level, runtime, run, gameTime);
            } catch (Exception e) {
                cancel(level, runtime, run, gameTime);
            }
        }
    }

    private static void tickRun(ServerLevel level, LevelRuntime runtime, DeliveryRun run, long gameTime) {
        CitizenData doctor = CitizenService.findCitizen(level, run.doctorId).orElse(null);
        CitizenEntity doctorEntity = CitizenTeleportService.findCitizenEntity(level, run.doctorId);
        if (doctor == null || doctorEntity == null || doctor.dead() || doctorEntity.isSleeping()) {
            cancel(level, runtime, run, gameTime);
            return;
        }

        Vec3 target = run.phase == DeliveryPhase.TO_RESTAURANT
                ? Vec3.atBottomCenterOf(run.restaurantBoxPos.above())
                : Vec3.atBottomCenterOf(run.hospitalBoxPos.above());

        if (doctorEntity.position().distanceToSqr(target) <= ARRIVAL_DISTANCE_SQR) {
            if (run.phase == DeliveryPhase.TO_RESTAURANT) {
                if (pickupMeal(level, run, doctorEntity)) {
                    run.pickedUp = true;
                    run.phase = DeliveryPhase.TO_HOSPITAL;
                    setDoctorStatus(level, doctor, MedicalMealService.DELIVERING_MEALS_STATUS);
                    requestMove(level, run.doctorId, target);
                } else {
                    if (gameTime - run.createdTick > 6000L) {
                        cancel(level, runtime, run, gameTime);
                    } else if (gameTime >= run.nextMoveTick) {
                        requestMove(level, run.doctorId, target);
                        run.nextMoveTick = gameTime + MOVE_RETRY_TICKS;
                    }
                    return;
                }
            } else {
                distributeMeal(level, run, doctorEntity, runtime);
                finish(level, runtime, run, gameTime);
            }
            return;
        }

        if (gameTime >= run.nextMoveTick) {
            requestMove(level, run.doctorId, target);
            run.nextMoveTick = gameTime + MOVE_RETRY_TICKS;
        }
    }

    private static boolean pickupMeal(ServerLevel level, DeliveryRun run, CitizenEntity doctor) {
        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, run.restaurantBoxPos);
        if (building == null) return false;
        RestaurantDefinitionLoader.LoadResult load = RestaurantDefinitionLoader.loadForBuilding(building);
        if (load.definition() == null) return false;
        List<BlockPos> outputs = CookingWorkService.resolvePositions(building, load.definition(), "output", run.restaurantBoxPos);
        ItemStack food = takeMatchingFood(level, outputs, run.dishItemId);
        if (food.isEmpty()) return false;
        if (!doctor.getCitizenInventory().insertBackpackAll(List.of(food))) {
            ItemEntity drop = new ItemEntity(level, doctor.getX(), doctor.getY(), doctor.getZ(), food);
            level.addFreshEntity(drop);
        }
        return true;
    }

    private static void distributeMeal(ServerLevel level, DeliveryRun run, CitizenEntity doctor, LevelRuntime runtime) {
        CitizenData patient = CitizenService.findCitizen(level, run.patientId).orElse(null);
        if (patient == null || patient.dead() || !MedicalService.isAdmitted(patient)) return;
        CitizenEntity patientEntity = CitizenTeleportService.findCitizenEntity(level, run.patientId);
        if (patientEntity == null) return;

        CitizenFoodConsumptionService.tryEatBackpackFood(level, patientEntity, patient);
        var meal = doctor.getCitizenInventory().extractFirstBackpack(
                stack -> CitizenFoodConsumptionService.isFoodStack(patientEntity, stack));
        if (meal.isEmpty()) return;
        if (!patientEntity.getCitizenInventory().insertBackpackAll(List.of(meal.get()))) {
            doctor.getCitizenInventory().insertBackpackAll(List.of(meal.get()));
            return;
        }
        patient.medical().setLastHospitalMealDay(run.day);
        CitizenService.save(level, run.patientId);
        CitizenFoodConsumptionService.tryEatBackpackFood(level, patientEntity, patient);
        CitizenManager.get(level).syncEntity(patientEntity);

        chargePatient(level, run);
    }

    private static void chargePatient(ServerLevel level, DeliveryRun run) {
        if (run.cityId == null || run.dishPrice <= 0) return;
        double finalPrice = run.dishPrice * PATIENT_PRICE_MULTIPLIER;
        EconomyService.depositCityFunds(level, run.cityId, null, finalPrice, "医院餐厅供餐");
    }

    private static void checkReadyOrdersAndStartDelivery(ServerLevel level, LevelRuntime runtime, long gameTime) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        for (RestaurantBoxData data : manager.all()) {
            for (var order : data.orders()) {
                if (!order.seatPos().equals(HOSPITAL_SEAT_MARKER)) continue;
                if (order.status() != RestaurantBoxData.OrderStatus.COOKED) continue;
                UUID patientId = order.customerId();
                if (runtime.active.containsKey(patientId)) continue;
                if (isQueued(runtime, patientId)) continue;

                OrderTarget target = runtime.orderTargets.get(patientId);
                if (target == null) continue;

                CitizenData doctor = findDoctorForPatient(level, patientId);
                if (doctor == null || doctor.dead()) continue;
                UUID doctorId = doctor.uuid();
                if (CitizenSelfFeedingService.isSelfFeeding(level, doctorId)) continue;
                if (MedicalMealService.isDoctorMealRunActive(level, doctorId)) continue;

                if (runtime.doctorActive.containsKey(doctorId)) {
                    runtime.doctorQueues.computeIfAbsent(doctorId, k -> new ConcurrentLinkedDeque<>()).addLast(patientId);
                    markPersisted(level);
                    continue;
                }
                startDelivery(level, runtime, patientId, doctor, target, gameTime);
            }
        }
    }

    private static void startDelivery(ServerLevel level, LevelRuntime runtime, UUID patientId,
                                       CitizenData doctor, OrderTarget target, long gameTime) {
        DeliveryRun run = new DeliveryRun(
                doctor.uuid(), patientId, target.cityId,
                target.hospitalBoxPos(), target.restaurantBoxPos(),
                target.dishItemId(), target.dishPrice(), target.day(),
                doctor.statusLabel(), doctor.workNeedDetail(), gameTime
        );
        runtime.active.put(patientId, run);
        runtime.doctorActive.put(doctor.uuid(), patientId);
        setDoctorStatus(level, doctor, MedicalMealService.BUYING_MEALS_STATUS);
        requestMove(level, doctor.uuid(), Vec3.atBottomCenterOf(target.restaurantBoxPos().above()));
    }

    private static void startNextQueuedDelivery(ServerLevel level, LevelRuntime runtime, UUID doctorId, long gameTime) {
        Deque<UUID> queue = runtime.doctorQueues.get(doctorId);
        if (queue == null) return;
        while (true) {
            UUID nextPatientId = queue.pollFirst();
            if (nextPatientId == null) return;
            if (runtime.doctorQueues.get(doctorId) != null && runtime.doctorQueues.get(doctorId).isEmpty()) {
                runtime.doctorQueues.remove(doctorId);
                markPersisted(level);
            }
            if (runtime.active.containsKey(nextPatientId)) continue;
            OrderTarget target = runtime.orderTargets.get(nextPatientId);
            if (target == null) continue;
            if (gameTime - target.createdTick() > STALE_ORDER_TICKS) continue;
            CitizenData doctor = CitizenService.findCitizen(level, doctorId).orElse(null);
            if (doctor == null || doctor.dead()) return;
            if (CitizenSelfFeedingService.isSelfFeeding(level, doctorId)) return;
            if (MedicalMealService.isDoctorMealRunActive(level, doctorId)) return;
            startDelivery(level, runtime, nextPatientId, doctor, target, gameTime);
            return;
        }
    }

    private static boolean isQueued(LevelRuntime runtime, UUID patientId) {
        for (Deque<UUID> queue : runtime.doctorQueues.values()) {
            if (queue.contains(patientId)) return true;
        }
        return false;
    }

    private static void checkStaleOrders(ServerLevel level, LevelRuntime runtime, long gameTime) {
        for (UUID patientId : List.copyOf(runtime.orderTargets.keySet())) {
            OrderTarget target = runtime.orderTargets.get(patientId);
            if (target == null) continue;
            if (gameTime - target.createdTick() <= STALE_ORDER_TICKS) continue;

            DeliveryRun run = runtime.active.get(patientId);
            if (run != null && run.pickedUp) continue;

            if (run != null) {
                cancel(level, runtime, run, gameTime);
            }
            removeHospitalOrderByPatient(level, patientId, target.restaurantBoxPos());
            runtime.orderTargets.remove(patientId);
            markPersisted(level);
            removeFromQueues(level, runtime, patientId);

            CitizenData patient = CitizenService.findCitizen(level, patientId).orElse(null);
            if (patient == null || patient.dead() || !MedicalService.isAdmitted(patient)) continue;
            long currentDay = level.getDayTime() / 24_000L;
            placeOrder(level, runtime, patientId, patient, target.hospitalBoxPos(), currentDay, gameTime);
        }
    }

    private static void removeFromQueues(ServerLevel level, LevelRuntime runtime, UUID patientId) {
        boolean changed = false;
        for (Deque<UUID> queue : runtime.doctorQueues.values()) {
            if (queue.remove(patientId)) changed = true;
        }
        runtime.doctorQueues.entrySet().removeIf(e -> e.getValue().isEmpty());
        if (changed) markPersisted(level);
    }

    private static CitizenData findDoctorForPatient(ServerLevel level, UUID patientId) {
        CitizenData patient = CitizenService.findCitizen(level, patientId).orElse(null);
        if (patient == null || patient.medical().medicalBedPoiId() == null) return null;
        UUID bedPoiId = patient.medical().medicalBedPoiId();

        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            BlockPos boxPos = MedicalControlBoxService.resolveControlBoxPos(level, building);
            if (!MedicalControlBoxService.isOperational(level, building, boxPos)) continue;
            Set<UUID> bedIds = collectBedPoiIds(level, building);
            if (bedIds.contains(bedPoiId)) {
                return MedicalControlBoxService.findAssignedDoctor(level, boxPos);
            }
        }
        return null;
    }

    private static List<HospitalContext> findOperationalHospitals(ServerLevel level) {
        List<HospitalContext> hospitals = new ArrayList<>();
        CityPoiManager poiManager = CityPoiManager.get(level);

        for (PlacedBuildingRecord building : PlacedBuildingService.getBuildings(level)) {
            if (building.cityId() == null) continue;
            BlockPos boxPos = MedicalControlBoxService.resolveControlBoxPos(level, building);
            if (!MedicalControlBoxService.isOperational(level, building, boxPos)) continue;

            Set<UUID> bedIds = collectBedPoiIds(level, building);
            if (bedIds.isEmpty()) continue;

            CitizenData doctor = MedicalControlBoxService.findAssignedDoctor(level, boxPos);
            if (doctor == null) continue;

            List<UUID> patientIds = CitizenManager.get(level).allCitizens().stream()
                    .filter(c -> c.medical().medicalBedPoiId() != null && bedIds.contains(c.medical().medicalBedPoiId()))
                    .filter(c -> !c.dead())
                    .map(CitizenData::uuid)
                    .toList();
            if (patientIds.isEmpty()) continue;

            hospitals.add(new HospitalContext(boxPos, doctor.uuid(), patientIds, building.cityId()));
        }
        return hospitals;
    }

    private static Set<UUID> collectBedPoiIds(ServerLevel level, PlacedBuildingRecord building) {
        CityPoiManager poiManager = CityPoiManager.get(level);
        Set<UUID> bedIds = new HashSet<>();
        for (var instance : building.poiInstances()) {
            if (instance.poiType() != CityPoiType.MEDICAL) continue;
            CityPoiData poi = poiManager.getPoiAt(instance.worldPos());
            if (poi != null && poi.active()) bedIds.add(poi.poiId());
        }
        return bedIds;
    }

    private static ItemStack takeMatchingFood(ServerLevel level, List<BlockPos> outputs, String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation targetId = ResourceLocation.tryParse(outputItemId);
        if (targetId == null) return ItemStack.EMPTY;
        for (BlockPos pos : outputs) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(targetId)) {
                    ItemStack taken = s.split(1);
                    c.setChanged();
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void requestMove(ServerLevel level, UUID doctorId, Vec3 target) {
        if (!CitizenNavigationService.requestMove(level, doctorId, target, MovementIntent.SELF_FEEDING)) {
            CitizenTeleportService.teleportCitizen(level, doctorId, target);
        }
    }

    private static void setDoctorStatus(ServerLevel level, CitizenData doctor, String statusLabel) {
        doctor.setStatusLabel(statusLabel);
        doctor.setWorkNeedDetail("");
        CitizenService.save(level, doctor.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, doctor.uuid());
        if (entity != null) CitizenManager.get(level).syncEntity(entity);
    }

    private static void restoreDoctorStatus(ServerLevel level, DeliveryRun run) {
        CitizenData doctor = CitizenService.findCitizen(level, run.doctorId).orElse(null);
        if (doctor == null) return;
        String status = doctor.statusLabel();
        if (!MedicalMealService.BUYING_MEALS_STATUS.equals(status) && !MedicalMealService.DELIVERING_MEALS_STATUS.equals(status)) return;
        doctor.setStatusLabel(run.previousStatusLabel);
        doctor.setWorkNeedDetail(run.previousWorkNeedDetail);
        CitizenService.save(level, doctor.uuid());
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, doctor.uuid());
        if (entity != null) {
            CitizenJobVisualService.clearMainHandOverride(doctor.uuid());
            CitizenManager.get(level).syncEntity(entity);
        }
    }

    private static void finish(ServerLevel level, LevelRuntime runtime, DeliveryRun run, long gameTime) {
        runtime.active.remove(run.patientId, run);
        runtime.orderTargets.remove(run.patientId);
        markPersisted(level);
        runtime.doctorActive.remove(run.doctorId, run.patientId);
        CitizenNavigationService.stop(level, run.doctorId);
        restoreDoctorStatus(level, run);
        removeHospitalOrder(level, run);
        startNextQueuedDelivery(level, runtime, run.doctorId, gameTime);
    }

    private static void cancel(ServerLevel level, LevelRuntime runtime, DeliveryRun run, long gameTime) {
        runtime.active.remove(run.patientId);
        runtime.doctorActive.remove(run.doctorId, run.patientId);
        CitizenNavigationService.stop(level, run.doctorId);
        restoreDoctorStatus(level, run);
        startNextQueuedDelivery(level, runtime, run.doctorId, gameTime);
    }

    private static void removeHospitalOrder(ServerLevel level, DeliveryRun run) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(run.restaurantBoxPos);
        if (data != null) {
            data.orders().removeIf(o -> o.customerId().equals(run.patientId) && o.seatPos().equals(HOSPITAL_SEAT_MARKER));
            manager.persist(data);
        }
    }

    private static void removeHospitalOrderByPatient(ServerLevel level, UUID patientId, BlockPos restaurantBoxPos) {
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(restaurantBoxPos);
        if (data != null) {
            data.orders().removeIf(o -> o.customerId().equals(patientId) && o.seatPos().equals(HOSPITAL_SEAT_MARKER));
            manager.persist(data);
        }
    }

    private static LevelRuntime runtime(ServerLevel level) {
        String key = level.dimension().location().toString();
        return RUNTIMES.computeIfAbsent(key, k -> {
            LevelRuntime rt = new LevelRuntime();
            HospitalMealOrderSavedData saved = HospitalMealOrderSavedData.get(level);
            rt.orderTargets.putAll(saved.orderTargets());
            rt.doctorQueues.putAll(saved.doctorQueues());
            rt.orderedToday.putAll(saved.orderedToday());
            rt.savedData = saved;
            return rt;
        });
    }

    private static void markPersisted(ServerLevel level) {
        LevelRuntime rt = RUNTIMES.get(level.dimension().location().toString());
        if (rt != null && rt.savedData != null) {
            rt.savedData.markChanged();
        }
    }

    public static void clearServerCaches() {
        RUNTIMES.clear();
    }

    private enum DeliveryPhase { TO_RESTAURANT, TO_HOSPITAL }

    private record HospitalContext(BlockPos boxPos, UUID doctorId, List<UUID> patientIds, UUID cityId) {}

    public record OrderTarget(BlockPos hospitalBoxPos, BlockPos restaurantBoxPos, String dishItemId,
                              double dishPrice, UUID cityId, long day, long createdTick) {

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Hospital", hospitalBoxPos.asLong());
            tag.putLong("Restaurant", restaurantBoxPos.asLong());
            tag.putString("Dish", dishItemId == null ? "" : dishItemId);
            tag.putDouble("Price", dishPrice);
            if (cityId != null) tag.putUUID("City", cityId);
            tag.putLong("Day", day);
            tag.putLong("Created", createdTick);
            return tag;
        }

        public static OrderTarget fromTag(CompoundTag tag) {
            BlockPos hospital = BlockPos.of(tag.getLong("Hospital"));
            BlockPos restaurant = BlockPos.of(tag.getLong("Restaurant"));
            String dish = tag.getString("Dish");
            double price = tag.getDouble("Price");
            UUID cityId = tag.contains("City", Tag.TAG_INT_ARRAY) ? tag.getUUID("City") : null;
            long day = tag.getLong("Day");
            long created = tag.getLong("Created");
            return new OrderTarget(hospital, restaurant, dish, price, cityId, day, created);
        }
    }

    private static final class DeliveryRun {
        final UUID doctorId;
        final UUID patientId;
        final UUID cityId;
        final BlockPos hospitalBoxPos;
        final BlockPos restaurantBoxPos;
        final String dishItemId;
        final double dishPrice;
        final long day;
        final String previousStatusLabel;
        final String previousWorkNeedDetail;
        final long createdTick;
        DeliveryPhase phase = DeliveryPhase.TO_RESTAURANT;
        boolean pickedUp = false;
        long nextMoveTick;

        DeliveryRun(UUID doctorId, UUID patientId, UUID cityId,
                    BlockPos hospitalBoxPos, BlockPos restaurantBoxPos,
                    String dishItemId, double dishPrice, long day,
                    String previousStatusLabel, String previousWorkNeedDetail, long createdTick) {
            this.doctorId = doctorId;
            this.patientId = patientId;
            this.cityId = cityId;
            this.hospitalBoxPos = hospitalBoxPos.immutable();
            this.restaurantBoxPos = restaurantBoxPos.immutable();
            this.dishItemId = dishItemId;
            this.dishPrice = dishPrice;
            this.day = day;
            this.previousStatusLabel = previousStatusLabel != null ? previousStatusLabel : "";
            this.previousWorkNeedDetail = previousWorkNeedDetail != null ? previousWorkNeedDetail : "";
            this.createdTick = createdTick;
        }
    }

    private static final class LevelRuntime {
        final ConcurrentMap<UUID, DeliveryRun> active = new ConcurrentHashMap<>();
        final ConcurrentMap<UUID, UUID> doctorActive = new ConcurrentHashMap<>();
        final ConcurrentMap<UUID, Deque<UUID>> doctorQueues = new ConcurrentHashMap<>();
        final ConcurrentMap<UUID, OrderTarget> orderTargets = new ConcurrentHashMap<>();
        final ConcurrentMap<UUID, Long> orderedToday = new ConcurrentHashMap<>();
        HospitalMealOrderSavedData savedData;
    }
}
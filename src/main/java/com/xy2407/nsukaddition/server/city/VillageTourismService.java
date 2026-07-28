package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.TourismConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinition;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.cooking.RestaurantDiningService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.commercial.*;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 村庄旅游服务，管理游客的生成、漫游、购物消费及日落清除。 */
public final class VillageTourismService {

    private VillageTourismService() {}

    private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Tourist>> ACTIVE_TOURISTS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Long> LAST_SPAWN_TICK = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, List<Caravan>> ACTIVE_CARAVANS = new ConcurrentHashMap<>();
    private static long lastCaravanSpawnDay = -1;

    private static final ConcurrentHashMap<UUID, Double> TOURIST_INCOME = new ConcurrentHashMap<>();

    public static double getTouristIncome(UUID cityId) {
        return TOURIST_INCOME.getOrDefault(cityId, 0.0D);
    }

    public static void saveTouristIncome(MinecraftServer server) {
        CompoundTag root = new CompoundTag();
        CompoundTag incomes = new CompoundTag();
        for (Map.Entry<UUID, Double> entry : TOURIST_INCOME.entrySet()) {
            if (entry.getValue() > 0.0D) {
                incomes.putDouble(entry.getKey().toString(), entry.getValue());
            }
        }
        root.put("incomes", incomes);
        try {
            Path file = incomeFilePath(server);
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to save tourist income", e);
        }
    }

    public static void loadTouristIncome(MinecraftServer server) {
        Path file = incomeFilePath(server);
        if (!Files.exists(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            CompoundTag incomes = root.getCompound("incomes");
            for (String key : incomes.getAllKeys()) {
                try {
                    TOURIST_INCOME.put(UUID.fromString(key), incomes.getDouble(key));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("Failed to load tourist income", e);
        }
    }

    private static Path incomeFilePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("nsuk_tourist_income.nbt");
    }

    public static void onServerTick(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long timeOfDay = level.getDayTime() % 24000L;
        long currentDay = level.getDayTime() / 24000L;
        long gameTime = level.getGameTime();

        boolean isNight = timeOfDay >= 12000L;

        if (isNight) {
            if (gameTime % 1000L == 0L) {
                clearAllTourists(level);
            }
        } else {
            if (timeOfDay >= 1000L && timeOfDay < 11000L) {
                for (CityData city : getVillageCities(level)) {
                    spawnTouristsForCity(level, city, timeOfDay, gameTime);
                }
            }

            if (currentDay != lastCaravanSpawnDay && timeOfDay >= 2000L && timeOfDay < 3000L) {
                lastCaravanSpawnDay = currentDay;
                for (CityData city : getVillageCities(level)) {
                    if (CityLevel.fromLevel(city.cityLevel()).atLeast(CityLevel.TOWN) && level.random.nextFloat() < 1.0f / 3.0f) {
                        spawnCaravanForCity(level, city);
                    }
                }
            }

            if (gameTime % 1000L == 0L) {
                for (CityData city : getVillageCities(level)) {
                    processShopping(level, city);
                }
            }

            if (gameTime % 3000L == 0L) {
                for (CityData city : getVillageCities(level)) {
                    processRestaurantVisit(level, city, gameTime);
                    processCaravanRestaurantVisit(level, city, gameTime);
                }
            }

            wanderTourists(level);

            tickTouristArrivals(level);

            tickCaravans(level);
        }
    }

    public static void beginDay(ServerLevel level, UUID cityId, long day) {
        ACTIVE_TOURISTS.remove(cityId);

        Double accumulated = TOURIST_INCOME.remove(cityId);
        if (accumulated != null && accumulated > 0.0D) {
            EconomyService.depositCityFunds(level, cityId, null, accumulated, "tourist_caravan_income", false);
        }

        List<Caravan> removedCaravans = ACTIVE_CARAVANS.remove(cityId);
        if (removedCaravans != null) {
            for (Caravan caravan : removedCaravans) {
                discardCaravanEntities(level, caravan);
            }
        }

        clearAllTourists(level);
    }

    public static void onCityDeleted(UUID cityId) {
        ACTIVE_TOURISTS.remove(cityId);
        LAST_SPAWN_TICK.remove(cityId);
        ACTIVE_CARAVANS.remove(cityId);
        TOURIST_INCOME.remove(cityId);
    }

    private static List<CityData> getVillageCities(ServerLevel level) {
        List<CityData> result = new ArrayList<>();
        for (CityData city : common.cn.kafei.simukraft.city.CityService.allCities(level)) {
            if (CityLevel.fromLevel(city.cityLevel()).atLeast(CityLevel.VILLAGE)) {
                result.add(city);
            }
        }
        return result;
    }

    private static void spawnTouristsForCity(ServerLevel level, CityData city, long timeOfDay, long gameTime) {
        UUID cityId = city.cityId();
        CopyOnWriteArrayList<Tourist> tourists = ACTIVE_TOURISTS.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>());

        int maxTourists = maxTouristsFor(city);
        if (tourists.size() >= maxTourists) return;

        Long lastSpawn = LAST_SPAWN_TICK.get(cityId);
        if (lastSpawn != null && gameTime - lastSpawn < 2000L) return;

        BlockPos core = city.cityCorePos();
        if (core == null) return;

        BlockPos spawnPos = findSpawnPos(level, core, cityId);
        if (spawnPos == null) return;

        CitizenEntity entity = spawnTouristEntity(level, spawnPos);
        if (entity == null) return;

        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;

        data.setCityId(null);
        data.setJobType(CityJobType.UNEMPLOYED);
        data.setHomeId(null);
        data.setWorkplaceId(null);
        data.setWorkplacePos(null);
        data.setStatusLabel(TourismConstants.TOURIST_STATUS_LABEL);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
        entity.setHunger(4.0F);

        double funds = switch (CityLevel.fromLevel(city.cityLevel())) {
            case VILLAGE -> 20.0 + level.random.nextDouble() * 180.0;
            case TOWN -> 30.0 + level.random.nextDouble() * 270.0;
            case CITY_STATE -> 40.0 + level.random.nextDouble() * 360.0;
            case METROPOLIS -> 50.0 + level.random.nextDouble() * 450.0;
            default -> 20.0 + level.random.nextDouble() * 180.0;
        };
        tourists.add(new Tourist(entity.getUUID(), funds));
        LAST_SPAWN_TICK.put(cityId, gameTime);
    }

    private static CitizenEntity spawnTouristEntity(ServerLevel level, BlockPos pos) {
        CitizenEntity entity = ModEntities.CITIZEN.get().create(level);
        if (entity == null) {
            return null;
        }
        Vec3 target = Vec3.atBottomCenterOf(pos).add(0.0D, 1.0D, 0.0D);
        entity.moveTo(target.x, target.y, target.z, level.random.nextFloat() * 360.0F, 0.0F);
        entity.setStatusLabel(TourismConstants.TOURIST_STATUS_LABEL);
        level.addFreshEntity(entity);
        return entity;
    }

    private static int maxTouristsFor(CityData city) {
        return switch (CityLevel.fromLevel(city.cityLevel())) {
            case VILLAGE -> 5;
            case TOWN -> 8;
            case CITY_STATE -> 12;
            case METROPOLIS -> 18;
            default -> 0;
        };
    }

    private static void processShopping(ServerLevel level, CityData city) {
        UUID cityId = city.cityId();
        List<Tourist> tourists = ACTIVE_TOURISTS.get(cityId);
        if (tourists == null || tourists.isEmpty()) {
            return;
        }

        List<CommercialBoxData> shops = findCityShops(level, cityId);
        if (shops.isEmpty()) {
            return;
        }

        RandomSource random = level.random;
        for (Tourist tourist : tourists) {
            CommercialBoxData shop = pickBestShop(level, shops, random);
            if (shop == null) continue;
            tourist.targetShop = shop.boxPos();

            CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
            if (entity == null) {
                tourist.targetShop = null;
                continue;
            }
            CitizenNavigationService.requestMove(level, tourist.citizenId,
                    Vec3.atBottomCenterOf(shop.boxPos().above()), MovementIntent.WALK);
        }
    }

    private static void processRestaurantVisit(ServerLevel level, CityData city, long gameTime) {
        UUID cityId = city.cityId();
        List<Tourist> tourists = ACTIVE_TOURISTS.get(cityId);
        if (tourists == null || tourists.isEmpty()) return;

        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        double multiplier = getCityLevelMultiplier(city);
        RandomSource random = level.random;

        for (Tourist tourist : tourists) {
            if (tourist.restaurantVisits >= 2) continue;
            if (RestaurantDiningService.isDining(tourist.citizenId)) continue;
            if (tourist.isDeparting() || tourist.isWaiting()) continue;

            CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
            if (entity == null) continue;
            if (entity.getHungerValue() > 4.0D) continue;

            for (RestaurantBoxData data : manager.all()) {
                if (!data.running() || data.selectedCookItems().isEmpty()) continue;
                if (!entity.blockPosition().closerThan(data.boxPos(), 256.0D)) continue;

                PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
                if (building == null) continue;
                RestaurantDefinitionLoader.LoadResult lr = RestaurantDefinitionLoader.loadForBuilding(building);
                if (!lr.valid() || lr.definition() == null) continue;

                List<String> pool = new ArrayList<>(data.selectedCookItems());
                String itemId = pool.get(random.nextInt(pool.size()));
                double finalPrice = lr.definition().cookPrice(itemId) * multiplier;
                if (tourist.funds < finalPrice) continue;

                var opt = CitizenService.findCitizen(level, tourist.citizenId);
                if (opt.isEmpty()) continue;
                if (RestaurantDiningService.startDining(level, opt.get(), building, lr.definition(), data)) {
                    tourist.restaurantVisits++;
                    tourist.lastRestaurantTick = gameTime;
                    break;
                }
            }
        }
    }

    private static void processCaravanRestaurantVisit(ServerLevel level, CityData city, long gameTime) {
        UUID cityId = city.cityId();
        List<Caravan> caravans = ACTIVE_CARAVANS.get(cityId);
        if (caravans == null || caravans.isEmpty()) return;

        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        double multiplier = getCityLevelMultiplier(city);
        RandomSource random = level.random;

        for (Caravan caravan : caravans) {
            if (RestaurantDiningService.isDining(caravan.leaderId)) continue;

            CitizenEntity leaderEntity = findLoadedEntity(level, caravan.leaderId);
            if (leaderEntity == null) continue;
            if (leaderEntity.getHungerValue() > 4.0D) continue;

            for (RestaurantBoxData data : manager.all()) {
                if (!data.running() || data.selectedCookItems().isEmpty()) continue;
                if (!leaderEntity.blockPosition().closerThan(data.boxPos(), 256.0D)) continue;

                PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
                if (building == null) continue;
                RestaurantDefinitionLoader.LoadResult lr = RestaurantDefinitionLoader.loadForBuilding(building);
                if (!lr.valid() || lr.definition() == null) continue;

                List<String> pool = new ArrayList<>(data.selectedCookItems());
                String itemId = pool.get(random.nextInt(pool.size()));
                double finalPrice = lr.definition().cookPrice(itemId) * multiplier;
                if (caravan.funds < finalPrice) continue;

                var leaderOpt = CitizenService.findCitizen(level, caravan.leaderId);
                if (leaderOpt.isEmpty()) continue;
                if (RestaurantDiningService.startDining(level, leaderOpt.get(), building, lr.definition(), data)) {
                    caravan.funds -= finalPrice;

                    for (UUID followerId : caravan.followerIds) {
                        if (RestaurantDiningService.isDining(followerId)) continue;
                        var fOpt = CitizenService.findCitizen(level, followerId);
                        if (fOpt.isEmpty()) continue;
                        CitizenEntity fEntity = findLoadedEntity(level, followerId);
                        if (fEntity == null || fEntity.getHungerValue() > 4.0D) continue;
                        if (RestaurantDiningService.startDining(level, fOpt.get(), building, lr.definition(), data)) {
                            if (!pool.isEmpty()) {
                                String fItemId = pool.get(random.nextInt(pool.size()));
                                double fPrice = lr.definition().cookPrice(fItemId) * multiplier;
                                if (caravan.funds >= fPrice) {
                                    caravan.funds -= fPrice;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    public static boolean canTouristAffordCheapestMeal(ServerLevel level, UUID citizenId) {
        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            for (Tourist t : entry.getValue()) {
                if (!t.citizenId.equals(citizenId)) continue;
                CitizenEntity entity = findLoadedEntity(level, citizenId);
                if (entity == null) return false;
                RestaurantBoxManager manager = RestaurantBoxManager.get(level);
                CityData city = CityDataService.getCity(level, entry.getKey());
                double multiplier = getCityLevelMultiplier(city);
                double minPrice = Double.MAX_VALUE;
                for (RestaurantBoxData data : manager.all()) {
                    if (!data.running() || data.selectedCookItems().isEmpty()) continue;
                    if (!entity.blockPosition().closerThan(data.boxPos(), 256.0D)) continue;
                    PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, data.boxPos());
                    if (building == null) continue;
                    RestaurantDefinitionLoader.LoadResult lr = RestaurantDefinitionLoader.loadForBuilding(building);
                    if (!lr.valid() || lr.definition() == null) continue;
                    for (String itemId : data.selectedCookItems()) {
                        double price = lr.definition().cookPrice(itemId) * multiplier;
                        if (price < minPrice) minPrice = price;
                    }
                }
                if (minPrice == Double.MAX_VALUE) return false;
                return t.funds >= minPrice;
            }
        }
        return true;
    }

    private static double getCityLevelMultiplier(CityData city) {
        if (city == null) return 1.0;
        return switch (CityLevel.fromLevel(city.cityLevel())) {
            case SETTLEMENT, VILLAGE -> 1.0;
            case TOWN -> 1.5;
            case CITY_STATE -> 2.0;
            case METROPOLIS -> 2.5;
        };
    }

    private static void tickTouristArrivals(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % 20L != 0L) return;

        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            UUID cityId = entry.getKey();
            CityData city = CityDataService.getCity(level, cityId);
            if (city == null) continue;

            for (Tourist tourist : entry.getValue()) {
                syncTouristFundsDisplay(level, tourist);

                if (tourist.isWaiting()) {
                    tourist.waitTimer--;
                    continue;
                }

                if (tourist.isDeparting()) {
                    tourist.departingTimer--;
                    if (tourist.departingTimer <= 0) {
                        despawnAndReplace(level, city, tourist);
                    }
                    continue;
                }

                if (!tourist.isTraveling()) continue;

                CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
                if (entity == null) {
                    tourist.targetShop = null;
                    continue;
                }

                Vec3 targetCenter = Vec3.atBottomCenterOf(tourist.targetShop.above());
                double distSqr = entity.distanceToSqr(targetCenter);
                if (distSqr > 16.0) continue;

                tryPurchase(level, city, tourist);
                tourist.targetShop = null;
            }
        }
    }

    private static void tryPurchase(ServerLevel level, CityData city, Tourist tourist) {
        BlockPos shopPos = tourist.targetShop;
        if (shopPos == null) {
            return;
        }

        RandomSource random = level.random;

        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, shopPos);
        if (building == null) {
            return;
        }

        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) {
            return;
        }
        CommercialDefinition definition = loadResult.definition();

        List<CommercialOffer> sellable = definition.offers().stream()
                .filter(o -> o.itemLeavesStock() && totalMoney(o.cost(), 1) > 0 && totalMoney(o.cost(), 1) <= tourist.funds)
                .toList();

        if (sellable.isEmpty()) {
            tourist.waitTimer = 200;
            return;
        }

        CommercialOffer offer = sellable.get(random.nextInt(sellable.size()));
        double pricePer = totalMoney(offer.cost(), 1);
        double spendTarget = tourist.funds * 0.2;
        int quantity = Math.max(1, (int) (spendTarget / pricePer));
        double totalCost = pricePer * quantity;

        boolean consumed = CommercialTradeSupplyService.apply(level, shopPos, offer, quantity);
        if (!consumed) {
            tourist.waitTimer = 200;
            return;
        }

        TOURIST_INCOME.merge(city.cityId(), totalCost, Double::sum);

        tourist.funds -= totalCost;

        if (tourist.funds < 10.0) {
            tourist.departingTimer = 200;
        }
    }

    private static void despawnAndReplace(ServerLevel level, CityData city, Tourist tourist) {
        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity != null) {
            entity.discard();
        }
        CitizenManager.get(level).removeCitizen(tourist.citizenId);

        CopyOnWriteArrayList<Tourist> tourists = ACTIVE_TOURISTS.get(city.cityId());
        if (tourists != null) {
            tourists.remove(tourist);
        }

        BlockPos core = city.cityCorePos();
        if (core == null) return;
        BlockPos spawnPos = findSpawnPos(level, core, city.cityId());
        if (spawnPos == null) return;

        CitizenEntity newEntity = spawnTouristEntity(level, spawnPos);
        if (newEntity == null) return;
        CitizenData data = CitizenService.ensureCitizen(level, newEntity);
        if (data == null) return;

        data.setCityId(null);
        data.setJobType(CityJobType.UNEMPLOYED);
        data.setHomeId(null);
        data.setWorkplaceId(null);
        data.setWorkplacePos(null);
        data.setStatusLabel(TourismConstants.TOURIST_STATUS_LABEL);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, newEntity);
        newEntity.setHunger(4.0F);

        double funds = switch (CityLevel.fromLevel(city.cityLevel())) {
            case VILLAGE -> 20.0 + level.random.nextDouble() * 180.0;
            case TOWN -> 30.0 + level.random.nextDouble() * 270.0;
            case CITY_STATE -> 40.0 + level.random.nextDouble() * 360.0;
            case METROPOLIS -> 50.0 + level.random.nextDouble() * 450.0;
            default -> 20.0 + level.random.nextDouble() * 180.0;
        };
        if (tourists != null) {
            tourists.add(new Tourist(newEntity.getUUID(), funds));
        }
    }

    private static double totalMoney(List<CommercialResource> resources, int times) {
        double total = 0;
        for (CommercialResource res : resources) {
            if (res.type() == CommercialResource.Type.MONEY) {
                total += res.money() * times;
            }
        }
        return total;
    }

    private static void wanderTourists(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % 400L != 0L) return;

        RandomSource random = level.random;
        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            for (Tourist tourist : entry.getValue()) {
                if (tourist.targetShop != null || tourist.isDeparting() || tourist.isWaiting()) continue;
                CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
                if (entity == null || CitizenNavigationService.isNavigating(level, entity.getUUID())) continue;

                Vec3 target = randomTargetNearby(level, entity.position(), random);
                if (target != null) {
                    CitizenNavigationService.requestMove(level, entity.getUUID(), target, MovementIntent.WANDER);
                }
            }
        }
    }

    private static Vec3 randomTargetNearby(ServerLevel level, Vec3 origin, RandomSource random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(11) - 5;
            int x = (int) Math.floor(origin.x) + dx;
            int z = (int) Math.floor(origin.z) + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, feet)) {
                return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private static void syncTouristFundsDisplay(ServerLevel level, Tourist tourist) {
        CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
        if (entity == null) return;
        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;
        String funds = String.format("%.1f", tourist.funds);
        data.setStatusLabel("gui.xy2407_nsuk_addition.tourist.visiting||" + funds);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
    }

    private static void syncCaravanFundsDisplay(ServerLevel level, Caravan caravan) {
        CitizenEntity entity = findLoadedEntity(level, caravan.leaderId);
        if (entity == null) return;
        CitizenData data = CitizenService.ensureCitizen(level, entity);
        if (data == null) return;
        String funds = String.format("%.1f", caravan.funds);
        data.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS + "||" + funds);
        CitizenService.save(level, data.uuid());
        CitizenService.syncEntity(level, entity);
    }


    private static boolean isOpenForCitizen(ServerLevel level, BlockPos feet) {
        return feet != null
                && level.isEmptyBlock(feet)
                && level.isEmptyBlock(feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static void clearAllTourists(ServerLevel level) {
        CitizenManager manager = CitizenManager.get(level);
        List<UUID> toRemove = new ArrayList<>();
        for (CitizenData data : manager.allCitizens()) {
            if (data.statusLabel() == null || !data.statusLabel().startsWith(TourismConstants.TOURIST_STATUS_LABEL)) {
                continue;
            }
            toRemove.add(data.uuid());
            CitizenEntity entity = findLoadedEntity(level, data.uuid());
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID uuid : toRemove) {
            manager.removeCitizen(uuid);
        }

        ACTIVE_TOURISTS.clear();

        clearAllCaravans(level);
    }

    private static Entity findAnyLoadedEntity(ServerLevel level, UUID entityId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.getUUID().equals(entityId)) return entity;
        }
        return null;
    }

    private static CitizenEntity findLoadedEntity(ServerLevel level, UUID citizenId) {
        Entity entity = findAnyLoadedEntity(level, citizenId);
        return entity instanceof CitizenEntity citizen ? citizen : null;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos core, UUID cityId) {
        RandomSource random = level.random;
        for (int i = 0; i < 10; i++) {
            int x = core.getX() + random.nextInt(7) - 3;
            int z = core.getZ() + random.nextInt(7) - 3;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, pos)) {
                return pos;
            }
        }
        return core.above();
    }

    private static List<CommercialBoxData> findCityShops(ServerLevel level, UUID cityId) {
        List<CommercialBoxData> result = new ArrayList<>();
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (!cityId.equals(rec.cityId())) continue;
            if (!"commercial".equals(rec.category())) continue;
            BlockPos ctrlBoxPos = findControlBoxInBuilding(level, rec);
            if (ctrlBoxPos == null) continue;
            CommercialBoxData box = CommercialBoxManager.get(level).getOrCreate(ctrlBoxPos);
            result.add(box);
        }
        return result;
    }

    @Nullable
    private static BlockPos findControlBoxInBuilding(ServerLevel level, PlacedBuildingRecord building) {
        for (var block : building.blocks()) {
            BlockPos worldPos = building.worldOrigin().offset(block.relativePos());
            if (CommercialControlBoxService.isCommercialControlBox(level, worldPos)) {
                return worldPos.immutable();
            }
        }
        BlockPos min = building.minPos();
        BlockPos max = building.maxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (CommercialControlBoxService.isCommercialControlBox(level, pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    @Nullable
    private static CommercialBoxData pickBestShop(ServerLevel level, List<CommercialBoxData> shops, RandomSource random) {
        List<CommercialBoxData> withWorkers = new ArrayList<>();
        List<CommercialBoxData> withoutWorkers = new ArrayList<>();
        for (CommercialBoxData shop : shops) {
            if (CommercialControlBoxService.findAssignedWorker(level, shop.boxPos()) != null) {
                withWorkers.add(shop);
            } else {
                withoutWorkers.add(shop);
            }
        }
        if (!withWorkers.isEmpty()) {
            return withWorkers.get(random.nextInt(withWorkers.size()));
        }
        if (!withoutWorkers.isEmpty()) {
            return withoutWorkers.get(random.nextInt(withoutWorkers.size()));
        }
        return null;
    }

    private static final class Tourist {
        final UUID citizenId;
        double funds;
        BlockPos targetShop;
        int departingTimer;
        int waitTimer;
        int restaurantVisits;
        long lastRestaurantTick;

        Tourist(UUID citizenId, double funds) {
            this.citizenId = citizenId;
            this.funds = funds;
            this.targetShop = null;
            this.departingTimer = 0;
            this.waitTimer = 0;
            this.restaurantVisits = 0;
            this.lastRestaurantTick = 0;
        }

        boolean isTraveling() { return targetShop != null; }
        boolean isDeparting() { return departingTimer > 0; }
        boolean isWaiting() { return waitTimer > 0; }
    }

    private static final class Caravan {
        final UUID cityId;
        final UUID leaderId;
        final List<UUID> followerIds;
        final List<UUID> muleIds;
        double funds;
        BlockPos targetShop;
        int tradeCooldown;

        Caravan(UUID cityId, UUID leaderId, List<UUID> followerIds, List<UUID> muleIds, double funds) {
            this.cityId = cityId;
            this.leaderId = leaderId;
            this.followerIds = followerIds;
            this.muleIds = muleIds;
            this.funds = funds;
            this.targetShop = null;
            this.tradeCooldown = 0;
        }

        boolean isOnCooldown() { return tradeCooldown > 0; }
    }

    private static void spawnCaravanForCity(ServerLevel level, CityData city) {
        BlockPos core = city.cityCorePos();
        if (core == null) return;
        UUID cityId = city.cityId();

        BlockPos spawnPos = findSpawnPos(level, core, cityId);
        if (spawnPos == null) spawnPos = core.above();
        Vec3 center = Vec3.atBottomCenterOf(spawnPos);

        double funds = switch (CityLevel.fromLevel(city.cityLevel())) {
            case TOWN -> 5000.0;
            case CITY_STATE -> 10000.0;
            case METROPOLIS -> 20000.0;
            default -> 5000.0;
        };

        CitizenEntity leader = ModEntities.CITIZEN.get().create(level);
        if (leader == null) return;
        leader.moveTo(center.x, center.y, center.z, level.random.nextFloat() * 360.0F, 0.0F);
        leader.setGlowingTag(true);
        leader.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS);
        level.addFreshEntity(leader);

        CitizenData leaderData = CitizenService.ensureCitizen(level, leader);
        if (leaderData != null) {
            leaderData.setCityId(cityId);
            leaderData.setJobType(CityJobType.UNEMPLOYED);
            leaderData.setStatusLabel(TourismConstants.CARAVAN_LEADER_STATUS);
            CitizenService.save(level, leaderData.uuid());
            CitizenService.syncEntity(level, leader);
        }
        leader.setHunger(4.0F);

        List<UUID> followerIds = new ArrayList<>();
        List<UUID> muleIds = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            Vec3 offset = center.add((i - 0.5) * 2.0, 0, -2.0);
            CitizenEntity follower = ModEntities.CITIZEN.get().create(level);
            if (follower == null) continue;
            follower.moveTo(offset.x, offset.y, offset.z, level.random.nextFloat() * 360.0F, 0.0F);
            follower.setStatusLabel(TourismConstants.CARAVAN_FOLLOWER_STATUS);
            level.addFreshEntity(follower);

            CitizenData fData = CitizenService.ensureCitizen(level, follower);
            if (fData != null) {
                fData.setCityId(cityId);
                fData.setJobType(CityJobType.UNEMPLOYED);
                fData.setStatusLabel(TourismConstants.CARAVAN_FOLLOWER_STATUS);
                CitizenService.save(level, fData.uuid());
                CitizenService.syncEntity(level, follower);
            }
            follower.setHunger(4.0F);
            followerIds.add(follower.getUUID());

            Entity mule = EntityType.MULE.create(level);
            if (mule instanceof Mob mobMule) {
                mobMule.setPersistenceRequired();
                mobMule.moveTo(offset.x, offset.y, offset.z, level.random.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(mobMule);
                mobMule.setLeashedTo(follower, true);
                muleIds.add(mobMule.getUUID());
            }
        }

        ACTIVE_CARAVANS.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>())
                .add(new Caravan(cityId, leader.getUUID(), followerIds, muleIds, funds));
    }

    private static void tickCaravans(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (ACTIVE_CARAVANS.isEmpty()) return;

        boolean shouldNavigate = gameTime % 200L == 0L;
        boolean shouldCheckArrival = gameTime % 40L == 0L;

        for (Map.Entry<UUID, List<Caravan>> entry : ACTIVE_CARAVANS.entrySet()) {
            UUID cityId = entry.getKey();
            List<Caravan> caravans = entry.getValue();
            if (caravans == null || caravans.isEmpty()) continue;

            CityData city = CityDataService.getCity(level, cityId);
            if (city == null) continue;

            List<CommercialBoxData> shops = findCityShops(level, cityId);
            if (shops.isEmpty()) continue;

            for (Caravan caravan : caravans) {
                if (caravan.isOnCooldown()) {
                    caravan.tradeCooldown--;
                    continue;
                }

                if (caravan.targetShop == null) {
                    CommercialBoxData shop = pickBestShop(level, shops, level.random);
                    if (shop == null) continue;
                    caravan.targetShop = shop.boxPos().above();
                }

                if (shouldNavigate) {
                    navigateCaravanMember(level, caravan.leaderId, caravan.targetShop);
                    syncCaravanFundsDisplay(level, caravan);
                }

                if (shouldCheckArrival) {
                    CitizenEntity leader = findLoadedEntity(level, caravan.leaderId);
                    if (leader != null) {
                        Vec3 leaderPos = leader.position();
                        for (UUID followerId : caravan.followerIds) {
                            CitizenEntity follower = findLoadedEntity(level, followerId);
                            if (follower == null) continue;
                            if (follower.distanceToSqr(leaderPos) > 9.0) {
                                CitizenNavigationService.requestMove(level, followerId, leaderPos, MovementIntent.WALK);
                            }
                        }
                    }
                }

                if (shouldCheckArrival) {
                    CitizenEntity leader = findLoadedEntity(level, caravan.leaderId);
                    if (leader == null) {
                        caravan.targetShop = null;
                        continue;
                    }
                    double distSqr = leader.distanceToSqr(Vec3.atBottomCenterOf(caravan.targetShop));
                    if (distSqr <= 16.0) {
                        caravanTrade(level, city, caravan);
                        caravan.targetShop = null;
                        caravan.tradeCooldown = 500;
                    }
                }
            }
        }
    }

    private static void navigateCaravanMember(ServerLevel level, UUID entityId, BlockPos target) {
        if (CitizenNavigationService.isNavigating(level, entityId)) return;
        CitizenNavigationService.requestMove(level, entityId, Vec3.atBottomCenterOf(target), MovementIntent.WORK);
    }

    private static void caravanTrade(ServerLevel level, CityData city, Caravan caravan) {
        if (caravan.funds <= 0) return;

        RandomSource random = level.random;
        List<CommercialBoxData> shops = findCityShops(level, city.cityId());
        if (shops.isEmpty()) return;

        CitizenEntity leader = findLoadedEntity(level, caravan.leaderId);
        if (leader == null) return;

        CommercialBoxData nearestShop = null;
        double nearestDist = Double.MAX_VALUE;
        for (CommercialBoxData shop : shops) {
            double dist = leader.distanceToSqr(Vec3.atBottomCenterOf(shop.boxPos()));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestShop = shop;
            }
        }
        if (nearestShop == null) return;

        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, nearestShop.boxPos());
        if (building == null) return;

        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) return;
        CommercialDefinition definition = loadResult.definition();

        List<CommercialOffer> buyable = definition.offers().stream()
                .filter(o -> o.itemLeavesStock() && totalMoney(o.cost(), 1) > 0 && totalMoney(o.cost(), 1) <= caravan.funds)
                .toList();

        if (buyable.isEmpty()) return;

        CommercialOffer offer = buyable.get(random.nextInt(buyable.size()));
        double price = totalMoney(offer.cost(), 1);

        boolean consumed = CommercialTradeSupplyService.apply(level, nearestShop.boxPos(), offer, 1);
        if (!consumed) return;

        TOURIST_INCOME.merge(city.cityId(), price, Double::sum);
        caravan.funds -= price;
    }

    private static void clearAllCaravans(ServerLevel level) {
        CitizenManager manager = CitizenManager.get(level);
        List<UUID> toRemove = new ArrayList<>();
        for (CitizenData data : manager.allCitizens()) {
            if (data.statusLabel() == null) continue;
            if (!data.statusLabel().startsWith(TourismConstants.CARAVAN_LEADER_STATUS)
                    && !data.statusLabel().startsWith(TourismConstants.CARAVAN_FOLLOWER_STATUS)) {
                continue;
            }
            toRemove.add(data.uuid());
            CitizenEntity entity = findLoadedEntity(level, data.uuid());
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID uuid : toRemove) {
            manager.removeCitizen(uuid);
        }

        for (List<Caravan> caravans : ACTIVE_CARAVANS.values()) {
            for (Caravan caravan : caravans) {
                discardCaravanEntities(level, caravan);
                manager.removeCitizen(caravan.leaderId);
                for (UUID id : caravan.followerIds) {
                    manager.removeCitizen(id);
                }
            }
        }
        ACTIVE_CARAVANS.clear();
    }

    private static void discardCaravanEntities(ServerLevel level, Caravan caravan) {
        discardEntity(level, caravan.leaderId);
        for (UUID id : caravan.followerIds) {
            discardEntity(level, id);
        }
        for (UUID id : caravan.muleIds) {
            discardEntity(level, id);
        }
    }

    private static void discardEntity(ServerLevel level, UUID uuid) {
        Entity entity = findAnyLoadedEntity(level, uuid);
        if (entity != null) {
            entity.discard();
        }
    }
}

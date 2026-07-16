package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.common.city.CityDataService;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.TourismConstants;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.commercial.*;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 村庄旅游服务，管理游客的生成、漫游、购物消费及日落清除。 */
public final class VillageTourismService {

    private VillageTourismService() {}

    private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Tourist>> ACTIVE_TOURISTS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<UUID, Long> LAST_SPAWN_TICK = new ConcurrentHashMap<>();

    private static long lastShopCheckTick = -1;

    private static long lastClearTick = -1;

    public static void onServerTick(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long timeOfDay = level.getDayTime() % 24000L;
        long gameTime = level.getGameTime();

        if (timeOfDay >= 12000L) {
            if (gameTime - lastClearTick >= 1000L) {
                lastClearTick = gameTime;
                clearAllTourists(level);
            }
            return;
        }

        if (timeOfDay >= 1000L && timeOfDay < 11000L) {
            for (CityData city : getVillageCities(level)) {
                spawnTouristsForCity(level, city, timeOfDay, gameTime);
            }
        }

        if (gameTime - lastShopCheckTick >= 1000L) {
            lastShopCheckTick = gameTime;
            for (CityData city : getVillageCities(level)) {
                processShopping(level, city);
            }
        }

        wanderTourists(level);
    }

    public static void beginDay(ServerLevel level, UUID cityId, long day) {
        ACTIVE_TOURISTS.remove(cityId);

        clearAllTourists(level);
    }

    public static void onCityDeleted(UUID cityId) {
        ACTIVE_TOURISTS.remove(cityId);
        LAST_SPAWN_TICK.remove(cityId);
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

        double funds = 20.0 + level.random.nextDouble() * 180.0;
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
        if (tourists == null || tourists.isEmpty()) return;

        List<CommercialBoxData> shops = findCityShops(level, cityId);
        if (shops.isEmpty()) return;

        RandomSource random = level.random;
        for (Tourist tourist : tourists) {

            CommercialBoxData shop = shops.get(random.nextInt(shops.size()));

            double spend = Math.min(tourist.funds * (0.2 + random.nextDouble() * 0.3), tourist.funds);
            if (spend < 1.0) continue;

            boolean success = buyFromShop(level, shop, spend);
            if (success) {
                tourist.funds -= spend;
                EconomyService.depositCityFunds(level, cityId, null, spend, "tourist_purchase", true);
            }
        }
    }

    private static boolean buyFromShop(ServerLevel level, CommercialBoxData shop, double spend) {
        PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, shop.boxPos());
        if (building == null) return false;

        CommercialDefinitionLoader.LoadResult loadResult = CommercialDefinitionLoader.loadForBuilding(building);
        if (!loadResult.valid()) return false;
        CommercialDefinition definition = loadResult.definition();

        List<CommercialOffer> sellOffers = definition.npcOffers().stream()
                .filter(CommercialOffer::itemLeavesStock)
                .toList();
        if (sellOffers.isEmpty()) return false;

        RandomSource random = level.random;
        CommercialOffer offer = sellOffers.get(random.nextInt(sellOffers.size()));

        double pricePer = totalMoney(offer.cost(), 1);
        if (pricePer <= 0 || spend < pricePer) return false;

        CommercialTradeService.TradeResult result = CommercialTradeService.executeNpcOffer(level, shop.boxPos(), definition, offer);
        return result != null && result.success();
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
        if (gameTime % 40L != 0L) return;

        RandomSource random = level.random;
        for (Map.Entry<UUID, CopyOnWriteArrayList<Tourist>> entry : ACTIVE_TOURISTS.entrySet()) {
            UUID cityId = entry.getKey();
            CityData city = CityDataService.getCity(level, cityId);
            if (city == null) continue;
            Set<Long> chunks = CityChunkManager.get(level).getCityChunks(cityId);
            if (chunks.isEmpty()) continue;

            for (Tourist tourist : entry.getValue()) {
                CitizenEntity entity = findLoadedEntity(level, tourist.citizenId);
                if (entity == null || CitizenNavigationService.isNavigating(level, entity.getUUID())) continue;

                Vec3 target = randomTargetInCity(level, chunks, entity.position(), random);
                if (target != null) {
                    CitizenNavigationService.requestMove(level, entity.getUUID(), target, MovementIntent.WANDER);
                }
            }
        }
    }

    private static Vec3 randomTargetInCity(ServerLevel level, Set<Long> cityChunks, Vec3 origin, RandomSource random) {
        List<Long> chunks = new ArrayList<>(cityChunks);
        for (int attempt = 0; attempt < 20; attempt++) {
            long chunkLong = chunks.get(random.nextInt(chunks.size()));
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunkLong);
            int x = cp.getMinBlockX() + random.nextInt(16);
            int z = cp.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, feet)) {
                return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return null;
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
            if (!TourismConstants.TOURIST_STATUS_LABEL.equals(data.statusLabel())) {
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
    }

    private static CitizenEntity findLoadedEntity(ServerLevel level, UUID citizenId) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof CitizenEntity citizen && citizen.getUUID().equals(citizenId)) {
                return citizen;
            }
        }
        return null;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos core, UUID cityId) {
        Set<Long> chunks = CityChunkManager.get(level).getCityChunks(cityId);
        if (chunks.isEmpty()) return core.above();
        List<Long> chunkList = new ArrayList<>(chunks);
        RandomSource random = level.random;
        for (int i = 0; i < 10; i++) {
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunkList.get(random.nextInt(chunkList.size())));
            int x = cp.getMinBlockX() + random.nextInt(16);
            int z = cp.getMinBlockZ() + random.nextInt(16);
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
        for (CommercialBoxData box : CommercialBoxManager.get(level).all()) {
            if (!box.running()) continue;
            PlacedBuildingRecord building = CommercialControlBoxService.resolveBuilding(level, box.boxPos());
            if (building == null || !cityId.equals(building.cityId())) continue;
            result.add(box);
        }
        return result;
    }

    private static final class Tourist {
        final UUID citizenId;
        double funds;

        Tourist(UUID citizenId, double funds) {
            this.citizenId = citizenId;
            this.funds = funds;
        }
    }
}

package com.xy2407.nsukaddition.server.city;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.ImmigrantData;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 城镇移民服务，管理移民的生成、审批、拒绝及过期清理。 */
public final class TownImmigrationService {

    private TownImmigrationService() {}

    private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<ImmigrantData>> PENDING = new ConcurrentHashMap<>();

    private static final Set<UUID> SPAWNED_THIS_DAY = ConcurrentHashMap.newKeySet();

    private static final ConcurrentHashMap<UUID, Vec3> SPAWN_POSITIONS = new ConcurrentHashMap<>();

    public static void onServerTick(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        long timeOfDay = level.getDayTime() % 24000L;

        if (timeOfDay == 0L) {
            SPAWNED_THIS_DAY.clear();
            cleanupExpired(level, level.getDayTime() / 24000L);
        }

        keepImmigrantsStill(level);
    }

    public static void trySpawnImmigrant(ServerLevel level, UUID cityId, long day) {
        if (SPAWNED_THIS_DAY.contains(cityId)) return;
        CityData city = CityService.findCity(level, cityId).orElse(null);
        if (city == null) return;
        if (!CityLevel.fromLevel(city.cityLevel()).atLeast(CityLevel.TOWN)) return;

        if (level.random.nextDouble() >= 0.3) return;
        SPAWNED_THIS_DAY.add(cityId);

        BlockPos core = city.cityCorePos();
        if (core == null) return;
        BlockPos spawnPos = findSpawnNearCore(level, core);

        Optional<CitizenEntity> spawned = CitizenService.spawnCitizen(level, spawnPos, cityId, true);
        if (spawned.isEmpty()) return;

        CitizenEntity entity = spawned.get();
        CitizenData data = CitizenService.findCitizen(level, entity.getUUID()).orElse(null);
        if (data == null) return;

        double grant = 10.0 + level.random.nextDouble() * 90.0;
        ImmigrantData immigrant = new ImmigrantData(
                UUID.randomUUID(), cityId, entity.getUUID(),
                entity.getCitizenName(), grant, day
        );
        PENDING.computeIfAbsent(cityId, k -> new CopyOnWriteArrayList<>()).add(immigrant);
        SPAWN_POSITIONS.put(entity.getUUID(), entity.position());

        CitizenService.setWorkplace(level, data.uuid(), null);
    }

    public static boolean approve(ServerLevel level, ServerPlayer player, UUID cityId, UUID requestId) {
        ImmigrantData immigrant = removeRequest(cityId, requestId);
        if (immigrant == null) return false;

        CitizenEntity entity = findEntity(level, immigrant.citizenId());
        if (entity == null) return false;

        CitizenService.setCity(level, immigrant.citizenId(), cityId);
        SPAWN_POSITIONS.remove(immigrant.citizenId());

        if (player != null && immigrant.grantFunds() > 0) {
            EconomyService.depositCityFunds(level, cityId, player, immigrant.grantFunds(), "immigrant_grant", true);
        }

        return true;
    }

    public static boolean reject(ServerLevel level, UUID cityId, UUID requestId) {
        ImmigrantData immigrant = removeRequest(cityId, requestId);
        if (immigrant == null) return false;

        CitizenEntity entity = findEntity(level, immigrant.citizenId());
        if (entity != null) {
            entity.discard();
        }
        CitizenManager.get(level).removeCitizen(immigrant.citizenId());
        SPAWN_POSITIONS.remove(immigrant.citizenId());
        return true;
    }

    public static List<ImmigrantData> pendingForCity(UUID cityId) {
        CopyOnWriteArrayList<ImmigrantData> list = PENDING.get(cityId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public static void cleanupExpired(ServerLevel level, long currentDay) {
        for (Map.Entry<UUID, CopyOnWriteArrayList<ImmigrantData>> entry : PENDING.entrySet()) {
            UUID cityId = entry.getKey();
            for (ImmigrantData immigrant : entry.getValue()) {
                if (currentDay - immigrant.createdDay() > 3) {
                    reject(level, cityId, immigrant.requestId());
                }
            }
        }
    }

    private static ImmigrantData removeRequest(UUID cityId, UUID requestId) {
        CopyOnWriteArrayList<ImmigrantData> list = PENDING.get(cityId);
        if (list == null) return null;
        for (ImmigrantData immigrant : list) {
            if (immigrant.requestId().equals(requestId)) {
                list.remove(immigrant);
                return immigrant;
            }
        }
        return null;
    }

    private static void keepImmigrantsStill(ServerLevel level) {
        for (CopyOnWriteArrayList<ImmigrantData> list : PENDING.values()) {
            for (ImmigrantData immigrant : list) {
                CitizenEntity entity = findEntity(level, immigrant.citizenId());
                if (entity == null) continue;

                CitizenNavigationService.stop(level, immigrant.citizenId());

                if (level.getGameTime() % 100L == 0L) {
                    Vec3 spawn = SPAWN_POSITIONS.get(immigrant.citizenId());
                    if (spawn != null) {
                        entity.setPos(spawn.x, spawn.y, spawn.z);
                        entity.setDeltaMovement(Vec3.ZERO);
                    }
                }
            }
        }
    }

    private static CitizenEntity findEntity(ServerLevel level, UUID citizenId) {
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof CitizenEntity citizen && citizen.getUUID().equals(citizenId)) {
                return citizen;
            }
        }
        return null;
    }

    private static BlockPos findSpawnNearCore(ServerLevel level, BlockPos core) {
        Random random = new Random();
        for (int i = 0; i < 16; i++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(11) - 5;
            if (dx == 0 && dz == 0) continue;
            int x = core.getX() + dx;
            int z = core.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (isOpenForCitizen(level, pos)) {
                return pos;
            }
        }
        return core.above();
    }

    private static boolean isOpenForCitizen(ServerLevel level, BlockPos feet) {
        return feet != null
                && level.isEmptyBlock(feet)
                && level.isEmptyBlock(feet.above())
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }
}

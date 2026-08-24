package com.xy2407.nsukaddition.server.village;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.citycore.VillageCityConversionTrigger;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityGrade;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import com.xy2407.nsukaddition.common.village.VillageNamePool;
import com.xy2407.nsukaddition.server.city.CityCorePositionsSync;
import com.xy2407.nsukaddition.mixin.simukraft.CityDataUpgradeInvoker;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.city.chunk.CityChunkSyncService;
import common.cn.kafei.simukraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 村庄城市转换器：只把真正包含村庄结构 piece 的区块列为城市领土，避免整块大矩形。 */
public final class VillageCityConverter {

    private static final UUID SYSTEM_MAYOR_ID = UUID.nameUUIDFromBytes("nsuk:village_city_mayor".getBytes());
    private static final String SYSTEM_MAYOR_NAME = "村庄自治";

    private static final Set<String> CASTLE_STRUCTURE_IDS = Set.of(
            "aegis_castle", "barathian_castle", "burgundian_castle", "hospitaller_castle",
            "orleanian_castle", "templar_castle", "valarian_castle", "visgothian_castle");
    private static final String CASTLE_VILLAGE_TYPE = "castle";

    private static final ArrayDeque<PendingChunk> PENDING = new ArrayDeque<>();
    private static final ConcurrentHashMap<String, Boolean> PROCESSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> CLAIMED_CORES = new ConcurrentHashMap<>();
    private static final ArrayDeque<DeferredAssign> DEFERRED_ASSIGN = new ArrayDeque<>();
    private static final ConcurrentHashMap<UUID, Boolean> DEFERRED_REGISTERED = new ConcurrentHashMap<>();

    private static long lastTickTime = 0;

    static {
        VillageCityConversionTrigger.install(VillageCityConverter::onCityCorePlaced);
    }

    private VillageCityConverter() {}

    public static void onCityCorePlaced(ServerLevel level, BlockPos corePos) {
        if (level == null || corePos == null) {
            return;
        }
        try {
            ChunkPos coreChunk = new ChunkPos(corePos);
            var chunk = level.getChunk(coreChunk.x, coreChunk.z);
            var structureRegistry = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (var entry : chunk.getAllStarts().entrySet()) {
                ResourceLocation id = structureRegistry.getKey(entry.getKey());
                if (!isCityStructure(id)) {
                    continue;
                }
                StructureStart start = entry.getValue();
                if (start == StructureStart.INVALID_START || !start.isValid()) {
                    continue;
                }
                VillageBox vb = collectVillageBox(start, villageTypeFor(id));
                if (vb == null || !vb.chunks().contains(coreChunk.toLong())) {
                    continue;
                }
                String coreKey = dimensionId(level) + "@" + corePos.asLong();
                if (CLAIMED_CORES.putIfAbsent(coreKey, Boolean.TRUE) != null) {
                    continue;
                }
                createVillageCity(level, corePos.immutable(), vb);
                break;
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("VillageCityConverter: failed to convert village city at {}", corePos, e);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        var chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();
        String chunkKey = dimensionId(level) + "@" + chunkPos.toLong();
        if (PROCESSED.containsKey(chunkKey)) return;

        List<VillageBox> boxes = new ArrayList<>();
        var structureRegistry = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (var entry : chunk.getAllStarts().entrySet()) {
            ResourceLocation id = structureRegistry.getKey(entry.getKey());
            if (isCityStructure(id)) {
                StructureStart start = entry.getValue();
                if (start != StructureStart.INVALID_START && start.isValid()) {
                    VillageBox vb = collectVillageBox(start, villageTypeFor(id));
                    if (vb != null) {
                        boxes.add(vb);
                    }
                }
            }
        }

        if (boxes.isEmpty()) {
            PROCESSED.put(chunkKey, Boolean.TRUE);
            return;
        }

        synchronized (PENDING) {
            PENDING.addLast(new PendingChunk(level, chunkPos, chunkKey, boxes));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        long currentTick = server.getTickCount();

        if (!DEFERRED_ASSIGN.isEmpty()) {
            synchronized (DEFERRED_ASSIGN) {
                var iterator = DEFERRED_ASSIGN.iterator();
                while (iterator.hasNext()) {
                    DeferredAssign da = iterator.next();
                    if (currentTick < da.executeAtTick) continue;
                    assignNpcsInTerritory(da.level, da.cityId, da.vb);
                    iterator.remove();
                }
            }
        }

        if (currentTick - lastTickTime < 200) return;
        lastTickTime = currentTick;

        while (true) {
            PendingChunk pc;
            synchronized (PENDING) {
                pc = PENDING.pollFirst();
            }
            if (pc == null) break;

            if (PROCESSED.putIfAbsent(pc.chunkKey, Boolean.TRUE) != null) continue;

            processChunk(pc);
        }
    }

    private static void processChunk(PendingChunk pc) {
        for (VillageBox vb : pc.villageBoxes) {
            findCityCores(pc.level, vb);
        }
    }

    private static void findCityCores(ServerLevel level, VillageBox vb) {
        int yMin = Math.max(level.getMinBuildHeight(), vb.minY());
        int yMax = Math.min(level.getMaxBuildHeight(), vb.maxY());
        for (long chunkLong : vb.chunks()) {
            int cx = ChunkPos.getX(chunkLong);
            int cz = ChunkPos.getZ(chunkLong);
            int baseX = cx << 4;
            int baseZ = cz << 4;
            for (int x = baseX; x < baseX + 16; x++) {
                for (int z = baseZ; z < baseZ + 16; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).is(ModBlocks.CITY_CORE.get())) {
                            continue;
                        }
                        BlockPos corePos = pos.immutable();
                        String coreKey = dimensionId(level) + "@" + corePos.asLong();
                        if (CLAIMED_CORES.putIfAbsent(coreKey, Boolean.TRUE) != null) continue;
                        createVillageCity(level, corePos, vb);
                    }
                }
            }
        }
    }

    private static VillageBox collectVillageBox(StructureStart start, String villageType) {
        Set<Long> chunks = new HashSet<>();
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox pb = piece.getBoundingBox();
            int minCX = pb.minX() >> 4;
            int minCZ = pb.minZ() >> 4;
            int maxCX = pb.maxX() >> 4;
            int maxCZ = pb.maxZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    chunks.add(ChunkPos.asLong(cx, cz));
                }
            }
            if (pb.minY() < minY) minY = pb.minY();
            if (pb.maxY() > maxY) maxY = pb.maxY();
        }
        if (chunks.isEmpty()) {
            return null;
        }
        return new VillageBox(chunks, minY, maxY, villageType);
    }

    private static void createVillageCity(ServerLevel level, BlockPos corePos, VillageBox vb) {
        if (CityService.hasCityAtCorePos(level, corePos)) {
            var existing = CityService.findCityByCorePos(level, corePos);
            if (existing.isPresent() && VillageCityTypeStorage.getVillageType(level, existing.get().cityId()) == null) {
                VillageCityTypeStorage.saveVillageType(level, existing.get().cityId(), vb.villageType());
            }
            if (existing.isPresent()) {
                VillageCityGrade.save(level, existing.get().cityId(), vb.chunks().size());
            }
            return;
        }

        String cityName = generateUniqueName(level);
        CityData city = CityService.createCity(level, cityName, SYSTEM_MAYOR_ID, SYSTEM_MAYOR_NAME, corePos);
        if (city == null) return;

        VillageCityTypeStorage.saveVillageType(level, city.cityId(), vb.villageType());
        applyGradeLevel(level, city.cityId(), vb.chunks().size(), CASTLE_VILLAGE_TYPE.equals(vb.villageType()));
        claimTerritoryChunks(level, city.cityId(), vb.chunks());

        CityChunkSyncService.syncToAll(level);

        level.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (p.serverLevel() == level) CityCorePositionsSync.sendPositionsToPlayer(p);
        });

        assignNpcsInTerritory(level, city.cityId(), vb);
        if (DEFERRED_REGISTERED.putIfAbsent(city.cityId(), Boolean.TRUE) == null) {
            long executeAtTick = level.getServer().getTickCount() + 200;
            synchronized (DEFERRED_ASSIGN) {
                DEFERRED_ASSIGN.addLast(new DeferredAssign(executeAtTick, level, city.cityId(), vb));
            }
        }
    }

    private static void applyGradeLevel(ServerLevel level, UUID cityId, int chunkCount, boolean forceVillage) {
        VillageCityGrade.save(level, cityId, chunkCount);
        CityService.findCity(level, cityId).ifPresent(city -> {
            int lvl = forceVillage ? 2 : switch (VillageCityGrade.gradeForChunks(chunkCount)) {
                case VillageCityGrade.HAMLET -> 1;
                case VillageCityGrade.VILLAGE -> 2;
                case VillageCityGrade.TOWN -> 3;
                default -> 4;
            };
            ((CityDataUpgradeInvoker) (Object) city).nsuk$setCityLevel(lvl);
        });
    }

    private static void assignNpcsInTerritory(ServerLevel level, UUID cityId, VillageBox vb) {
        int minCX = Integer.MAX_VALUE;
        int maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE;
        int maxCZ = Integer.MIN_VALUE;
        for (long chunkLong : vb.chunks()) {
            int cx = ChunkPos.getX(chunkLong);
            int cz = ChunkPos.getZ(chunkLong);
            if (cx < minCX) minCX = cx;
            if (cx > maxCX) maxCX = cx;
            if (cz < minCZ) minCZ = cz;
            if (cz > maxCZ) maxCZ = cz;
        }
        AABB area = new AABB(
                minCX << 4, level.getMinBuildHeight(), minCZ << 4,
                (maxCX + 1) << 4, level.getMaxBuildHeight(), (maxCZ + 1) << 4);
        for (CitizenEntity npc : level.getEntitiesOfClass(CitizenEntity.class, area)) {
            if (npc.getUUID() == null) continue;
            if (!vb.chunks().contains(new ChunkPos(npc.blockPosition()).toLong())) continue;
            CitizenService.setCity(level, npc.getUUID(), cityId);
        }
    }

    private static void claimTerritoryChunks(ServerLevel level, UUID cityId, Set<Long> chunks) {
        CityChunkManager chunkManager = CityChunkManager.get(level);
        for (long chunkLong : chunks) {
            chunkManager.claimChunk(cityId, chunkLong);
        }
        chunkManager.saveToSqlite(level);
    }

    private static boolean isCityStructure(ResourceLocation id) {
        if (id == null) return false;
        if (id.getNamespace().equals("minecraft") && id.getPath().startsWith("village_")) return true;
        if (id.getNamespace().equals("joshie") && id.getPath().equals("village_ocean")) return true;
        return id.getNamespace().equals("valarian_conquest") && CASTLE_STRUCTURE_IDS.contains(id.getPath());
    }

    private static String villageTypeFor(ResourceLocation id) {
        if (id.getNamespace().equals("valarian_conquest")) return CASTLE_VILLAGE_TYPE;
        return id.getPath().substring("village_".length());
    }

    private static String generateUniqueName(ServerLevel level) {
        Random random = new Random();
        for (int i = 0; i < 50; i++) {
            String name = VillageNamePool.generate(random);
            if (!CityService.hasCityNamed(level, name)) return name;
        }
        return "村庄" + Integer.toHexString(random.nextInt(0xFFFF));
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private record PendingChunk(ServerLevel level, ChunkPos chunkPos, String chunkKey, List<VillageBox> villageBoxes) {}

    private record VillageBox(Set<Long> chunks, int minY, int maxY, String villageType) {}

    private record DeferredAssign(long executeAtTick, ServerLevel level, UUID cityId, VillageBox vb) {}
}
package com.xy2407.nsukaddition.server.village;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.citycore.VillageCityConversionTrigger;
import com.xy2407.nsukaddition.common.foreigntrade.VillageCityTypeStorage;
import com.xy2407.nsukaddition.common.village.VillageNamePool;
import com.xy2407.nsukaddition.server.city.CityCorePositionsSync;
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
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 村庄城市转换器：区块加载时直接提取村庄结构包围盒，服务端tick时非阻塞处理。 */
public final class VillageCityConverter {

    private static final UUID SYSTEM_MAYOR_ID = UUID.nameUUIDFromBytes("nsuk:village_city_mayor".getBytes());
    private static final String SYSTEM_MAYOR_NAME = "村庄自治";

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
            ChunkPos chunkPos = new ChunkPos(corePos);
            var chunk = level.getChunk(chunkPos.x, chunkPos.z);
            var structureRegistry = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            for (var entry : chunk.getAllStarts().entrySet()) {
                ResourceLocation id = structureRegistry.getKey(entry.getKey());
                if (!isVillageStructure(id)) {
                    continue;
                }
                StructureStart start = entry.getValue();
                if (start == StructureStart.INVALID_START || !start.isValid()) {
                    continue;
                }
                BoundingBox box = start.getBoundingBox();
                if (!inside(corePos, box)) {
                    continue;
                }
                String villageType = id.getPath().substring("village_".length());
                String coreKey = dimensionId(level) + "@" + corePos.asLong();
                if (CLAIMED_CORES.putIfAbsent(coreKey, Boolean.TRUE) != null) {
                    continue;
                }
                createVillageCity(level, corePos.immutable(), box, villageType);
                break;
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("VillageCityConverter: failed to convert village city at {}", corePos, e);
        }
    }

    private static boolean inside(BlockPos pos, BoundingBox box) {
        return pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getY() >= box.minY() && pos.getY() <= box.maxY()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ();
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
            if (isVillageStructure(id)) {
                StructureStart start = entry.getValue();
                if (start != StructureStart.INVALID_START && start.isValid()) {
                    String villageType = id.getPath().substring("village_".length());
                    boxes.add(new VillageBox(start.getBoundingBox(), villageType));
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
                    assignNpcsInTerritory(da.level, da.cityId, da.box);
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
        BoundingBox box = vb.box();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.CITY_CORE.get())) {
                        BlockPos corePos = pos.immutable();
                        String coreKey = dimensionId(level) + "@" + corePos.asLong();
                        if (CLAIMED_CORES.putIfAbsent(coreKey, Boolean.TRUE) != null) continue;
                        createVillageCity(level, corePos, box, vb.villageType());
                    }
                }
            }
        }
    }

    private static void createVillageCity(ServerLevel level, BlockPos corePos, BoundingBox box, String villageType) {
        if (CityService.hasCityAtCorePos(level, corePos)) {
            var existing = CityService.findCityByCorePos(level, corePos);
            if (existing.isPresent() && VillageCityTypeStorage.getVillageType(level, existing.get().cityId()) == null) {
                VillageCityTypeStorage.saveVillageType(level, existing.get().cityId(), villageType);
            }
            return;
        }

        String cityName = generateUniqueName(level);
        CityData city = CityService.createCity(level, cityName, SYSTEM_MAYOR_ID, SYSTEM_MAYOR_NAME, corePos);
        if (city == null) return;

        VillageCityTypeStorage.saveVillageType(level, city.cityId(), villageType);
        claimTerritoryChunks(level, city.cityId(), box);

        CityChunkSyncService.syncToAll(level);

        level.getServer().getPlayerList().getPlayers().forEach(p -> {
            if (p.serverLevel() == level) CityCorePositionsSync.sendPositionsToPlayer(p);
        });

        assignNpcsInTerritory(level, city.cityId(), box);

        if (DEFERRED_REGISTERED.putIfAbsent(city.cityId(), Boolean.TRUE) == null) {
            long executeAtTick = level.getServer().getTickCount() + 200;
            synchronized (DEFERRED_ASSIGN) {
                DEFERRED_ASSIGN.addLast(new DeferredAssign(executeAtTick, level, city.cityId(), box));
            }
        }
    }

    private static void assignNpcsInTerritory(ServerLevel level, UUID cityId, BoundingBox box) {
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        for (CitizenEntity npc : level.getEntitiesOfClass(CitizenEntity.class, area)) {
            if (npc.getUUID() == null) continue;
            CitizenService.setCity(level, npc.getUUID(), cityId);
        }
    }

    private static void claimTerritoryChunks(ServerLevel level, UUID cityId, BoundingBox box) {
        CityChunkManager chunkManager = CityChunkManager.get(level);
        int minCX = box.minX() >> 4;
        int minCZ = box.minZ() >> 4;
        int maxCX = box.maxX() >> 4;
        int maxCZ = box.maxZ() >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunkManager.claimChunk(cityId, ChunkPos.asLong(cx, cz));
            }
        }
        chunkManager.saveToSqlite(level);
    }

    private static boolean isVillageStructure(ResourceLocation id) {
        if (id == null) return false;
        return id.getNamespace().equals("minecraft") && id.getPath().startsWith("village_");
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

    private record VillageBox(BoundingBox box, String villageType) {}

    private record DeferredAssign(long executeAtTick, ServerLevel level, UUID cityId, BoundingBox box) {}
}
package com.xy2407.nsukaddition.common.network.clientbound;

import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenResponsePacket;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 附属地核心界面桥接，解耦公共包对客户端类的直接依赖。 */
public final class ColonyCoreBridge {

    private static Consumer<ColonyCoreOpenResponsePacket> openHandler = p -> {};
    private static BiConsumer<UUID, List<ColonyCoreOpenResponsePacket.ChunkCoord>> chunkRemoveHandler = (id, chunks) -> {};
    private static ChunkSyncHandler chunkSyncHandler = (id, name, parentName, parentId, chunks) -> {};

    private ColonyCoreBridge() {}

    @FunctionalInterface
    public interface ChunkSyncHandler {
        void accept(UUID colonyId, String colonyName, String parentCityName,
                     UUID parentCityId,
                     List<ColonyCoreOpenResponsePacket.ChunkCoord> chunks);
    }

    public static void install(Consumer<ColonyCoreOpenResponsePacket> open,
                               BiConsumer<UUID, List<ColonyCoreOpenResponsePacket.ChunkCoord>> chunkRemove,
                               ChunkSyncHandler chunkSync) {
        openHandler = open != null ? open : p -> {};
        chunkRemoveHandler = chunkRemove != null ? chunkRemove : (id, chunks) -> {};
        chunkSyncHandler = chunkSync != null ? chunkSync : (id, name, parentName, parentId, chunks) -> {};
    }

    public static void reset() {
        openHandler = p -> {};
        chunkRemoveHandler = (id, chunks) -> {};
        chunkSyncHandler = (id, name, parentName, parentId, chunks) -> {};
    }

    public static void open(ColonyCoreOpenResponsePacket p) {
        openHandler.accept(p);
    }

    /** 附属地区块变更同步到客户端缓存。 */
    public static void syncChunks(UUID colonyId, String colonyName, String parentCityName,
                                   UUID parentCityId,
                                   List<ColonyCoreOpenResponsePacket.ChunkCoord> chunks) {
        chunkSyncHandler.accept(colonyId, colonyName, parentCityName, parentCityId, chunks);
    }

    /** 附属地销毁时清除客户端缓存。 */
    public static void removeColonyChunks(UUID colonyId, List<ColonyCoreOpenResponsePacket.ChunkCoord> emptyChunks) {
        chunkRemoveHandler.accept(colonyId, emptyChunks);
    }
}

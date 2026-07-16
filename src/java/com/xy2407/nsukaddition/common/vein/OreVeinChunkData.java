package com.xy2407.nsukaddition.common.vein;

import net.minecraft.world.level.ChunkPos;

/** 矿脉区块数据记录，存储区块坐标、矿脉类型及矿脉中心位置。 */
public record OreVeinChunkData(
        long chunkPos,
        OreVeinType oreType,
        long veinId
) {
    public int chunkX() { return ChunkPos.getX(chunkPos); }
    public int chunkZ() { return ChunkPos.getZ(chunkPos); }

    public int veinCenterX() { return ChunkPos.getX(veinId); }
    public int veinCenterZ() { return ChunkPos.getZ(veinId); }
}

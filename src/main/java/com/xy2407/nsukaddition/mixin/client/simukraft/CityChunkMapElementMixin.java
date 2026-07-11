package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.UUID;

/** 修改城市核心地图和 Xaero 领地渲染，3色区分自己城市/附属地/他人领地，禁止操作附属地区块。 */
@Mixin(targets = "client.cn.kafei.simukraft.client.city.CityCoreScreenOpener$CityChunkMapElement", remap = false)
public abstract class CityChunkMapElementMixin {

    private static final int NSUK_COLONY_ALLY_FILL = 0x554488FF;
    private static final int NSUK_COLONY_ALLY_BORDER = 0xCC4488FF;
    private static final int NSUK_COLONY_ENEMY_FILL = 0x55FF2222;
    private static final int NSUK_COLONY_ENEMY_BORDER = 0xCCFF2222;

    @Shadow(remap = false) private volatile common.cn.kafei.simukraft.network.city.map.CityCoreMapResponsePacket packet;
    @Shadow(remap = false) private final ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
    @Shadow(remap = false) private int contextMenuChunkX;
    @Shadow(remap = false) private int contextMenuChunkZ;
    @Shadow(remap = false) private LinkedHashSet<Long> batchClaimChunks;

    @Shadow(remap = false)
    private void drawChunkFill(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int fillColor) {}

    @Shadow(remap = false)
    private void drawChunkOwnershipBorder(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int chunkX, int chunkZ, int borderColor) {}

    // 判断区块是否属于自己城市的附属地。
    private static boolean nsuk$isOwnColonyChunk(long chunkLong) {
        ColonyChunkClientCache colonyCache = ColonyChunkClientCache.getInstance();
        UUID owner = colonyCache.getColonyOwner(chunkLong);
        if (owner == null) return false;
        ColonyChunkClientCache.ColonyEntry entry = colonyCache.getColonyEntry(owner);
        if (entry == null || entry.parentCityId() == null) return false;
        ClientCityChunkCache cityCache = ClientCityChunkCache.getInstance();
        return cityCache.getCurrentCityId() != null
                && cityCache.getCurrentCityId().equals(entry.parentCityId());
    }

    // 判断区块是否属于他人附属地。
    private static boolean nsuk$isEnemyColonyChunk(long chunkLong) {
        ColonyChunkClientCache colonyCache = ColonyChunkClientCache.getInstance();
        UUID owner = colonyCache.getColonyOwner(chunkLong);
        return owner != null && !nsuk$isOwnColonyChunk(chunkLong);
    }

    // 完全重写 renderOwnedChunkBorders，加入3色方案：绿色=自己城市，蓝色=自己附属地，红色=他人。
    @Inject(method = "renderOwnedChunkBorders", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$renderOwnedChunkBorders(GUIContext guiContext, int startX, int startY, int width, int height, double centerX, double centerY, double chunkSize, int startChunkX, int endChunkX, int startChunkZ, int endChunkZ, CallbackInfo ci) {
        for (int chunkX = startChunkX; chunkX <= endChunkX; chunkX++) {
            for (int chunkZ = startChunkZ; chunkZ <= endChunkZ; chunkZ++) {
                long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
                if (!cache.isChunkOwned(chunkLong)) continue;

                if (nsuk$isOwnColonyChunk(chunkLong)) {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ALLY_FILL);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ALLY_BORDER);
                } else if (nsuk$isEnemyColonyChunk(chunkLong)) {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ENEMY_FILL);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ENEMY_BORDER);
                } else if (cache.isChunkInCurrentCity(chunkLong)) {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, 0x5500DD00);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, 0xCC00DD00);
                } else {
                    drawChunkFill(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ENEMY_FILL);
                    drawChunkOwnershipBorder(guiContext, startX, startY, width, height, centerX, centerY, chunkSize, chunkX, chunkZ, NSUK_COLONY_ENEMY_BORDER);
                }
            }
        }
        ci.cancel();
    }

    // 重写 isChunkInCurrentCity 在 renderContextMenu 中的调用，排除附属地区块使放弃按钮不可用。
    @Redirect(method = "renderContextMenu", at = @At(value = "INVOKE", target = "Lclient/cn/kafei/simukraft/client/city/ClientCityChunkCache;isChunkInCurrentCity(J)Z", remap = false), remap = false)
    private boolean nsuk$redirectIsChunkInCurrentCityRender(ClientCityChunkCache instance, long chunkLong) {
        if (nsuk$isOwnColonyChunk(chunkLong)) return false;
        return instance.isChunkInCurrentCity(chunkLong);
    }

    // 重写 isChunkInCurrentCity 在 handleContextMenuClick 中的调用，防止操作附属地区块。
    @Redirect(method = "handleContextMenuClick", at = @At(value = "INVOKE", target = "Lclient/cn/kafei/simukraft/client/city/ClientCityChunkCache;isChunkInCurrentCity(J)Z", remap = false), remap = false)
    private boolean nsuk$redirectIsChunkInCurrentCityClick(ClientCityChunkCache instance, long chunkLong) {
        if (nsuk$isOwnColonyChunk(chunkLong)) return false;
        return instance.isChunkInCurrentCity(chunkLong);
    }
}

package com.xy2407.nsukaddition.mixin.client;

import client.cn.kafei.simukraft.client.CityEntryHud;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** 修改 CityEntryHud.onClientTick，当玩家进入附属地区块时显示附属地名称和所属城市。 */
@Mixin(value = CityEntryHud.class, remap = false)
public class ColonyEntryHudMixin {

    private static UUID lastColonyId = null;
    private static long lastColonyChunkLong = Long.MIN_VALUE;

    @Inject(method = "onClientTick", at = @At("TAIL"))
    private static void nsuk$checkColonyEntry(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            lastColonyId = null;
            lastColonyChunkLong = Long.MIN_VALUE;
            return;
        }

        long chunkLong = mc.player.chunkPosition().toLong();
        if (chunkLong == lastColonyChunkLong) return;
        lastColonyChunkLong = chunkLong;

        ColonyChunkClientCache cache = ColonyChunkClientCache.getInstance();
        UUID colonyId = cache.getColonyOwner(chunkLong);

        if (colonyId == null || colonyId.equals(lastColonyId)) {
            lastColonyId = colonyId;
            return;
        }
        lastColonyId = colonyId;

        ColonyChunkClientCache.ColonyEntry entry = cache.getColonyEntry(colonyId);
        if (entry == null) return;

        String display = entry.colonyName() + "\n(所属城市:" + entry.parentCityName() + ")";
        CityEntryHud.show(display);
    }
}

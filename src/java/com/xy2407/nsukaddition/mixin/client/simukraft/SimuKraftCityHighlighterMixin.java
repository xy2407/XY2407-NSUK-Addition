package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 修改 SimuKraftCityHighlighter，3色方案：自己城市=绿色，附属地区块让给 ColonyTerritoryHighlighter 渲染蓝色，他人城市=红色。 */
@Mixin(value = client.cn.kafei.simukraft.client.compat.xaero.SimuKraftCityHighlighter.class, remap = false)
public class SimuKraftCityHighlighterMixin {

    private final int[] nsuk$resultStore = new int[5];

    private static final int NSUK_SELF_CITY_ARGB = 0xFF00DD00;
    private static final int NSUK_ENEMY_CITY_ARGB = 0xFFFF2222;
    private static final int NSUK_FILL_ALPHA = 0x40;
    private static final int NSUK_BORDER_ALPHA = 0xCC;

    @Inject(method = "getColors", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ, CallbackInfoReturnable<int[]> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            cir.setReturnValue(null);
            return;
        }

        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        UUID ownerCity = cache.getChunkOwner(chunkLong);
        if (ownerCity == null) {
            cir.setReturnValue(null);
            return;
        }

        ColonyChunkClientCache colonyCache = ColonyChunkClientCache.getInstance();
        if (colonyCache.getColonyOwner(chunkLong) != null) {
            cir.setReturnValue(null);
            return;
        }

        boolean isSelf = ownerCity.equals(cache.getCurrentCityId());
        int baseColor = isSelf ? NSUK_SELF_CITY_ARGB : NSUK_ENEMY_CITY_ARGB;
        int fillColor = xaeroColor(baseColor, NSUK_FILL_ALPHA);
        int borderColor = xaeroColor(baseColor, NSUK_BORDER_ALPHA);

        nsuk$resultStore[0] = fillColor;
        nsuk$resultStore[1] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX, chunkZ - 1))) ? fillColor : borderColor;
        nsuk$resultStore[2] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX + 1, chunkZ))) ? fillColor : borderColor;
        nsuk$resultStore[3] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX, chunkZ + 1))) ? fillColor : borderColor;
        nsuk$resultStore[4] = ownerCity.equals(cache.getChunkOwner(ChunkPos.asLong(chunkX - 1, chunkZ))) ? fillColor : borderColor;
        cir.setReturnValue(nsuk$resultStore);
    }

    private static int xaeroColor(int argb, int alpha) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$tooltip(ResourceKey<Level> dimension, long chunkLong, CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            cir.setReturnValue(null);
            return;
        }
        ClientCityChunkCache cache = ClientCityChunkCache.getInstance();
        UUID cityId = cache.getChunkOwner(chunkLong);
        if (cityId == null) {
            cir.setReturnValue(null);
            return;
        }
        ColonyChunkClientCache colonyCache = ColonyChunkClientCache.getInstance();
        if (colonyCache.getColonyOwner(chunkLong) != null) {
            cir.setReturnValue(null);
            return;
        }
        ClientCityChunkCache.CityCoreEntry core = cache.getAllCityCores().get(cityId);
        String cityName = core != null && core.cityName() != null && !core.cityName().isBlank()
                ? core.cityName() : cityId.toString();
        boolean isSelf = cityId.equals(cache.getCurrentCityId());
        cir.setReturnValue(net.minecraft.network.chat.Component.literal(cityName)
                .withStyle(isSelf ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
    }
}

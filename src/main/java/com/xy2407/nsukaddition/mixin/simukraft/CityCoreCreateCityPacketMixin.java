package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.city.CityCorePositionsSync;
import com.xy2407.nsukaddition.server.city.DialogNpcSpawnService;
import common.cn.kafei.simukraft.network.city.core.CityCoreCreateCityPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 城市创建后立即向创建者同步城市核心位置列表，并生成城市对话NPC。 */
@Mixin(value = CityCoreCreateCityPacket.class, remap = false)
public abstract class CityCoreCreateCityPacketMixin {

    @Inject(method = "createCity", at = @At("RETURN"), remap = false)
    private static void nsuk$onCityCreated(ServerLevel level, ServerPlayer player, BlockPos pos, String rawCityName, CallbackInfo ci) {
        CityCorePositionsSync.sendPositionsToPlayer(player);
        DialogNpcSpawnService.spawnForCity(level, player, pos.immutable());
    }
}

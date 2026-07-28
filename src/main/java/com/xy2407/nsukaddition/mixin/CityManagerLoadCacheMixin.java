package com.xy2407.nsukaddition.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import common.cn.kafei.simukraft.city.CityManager;

/** 缓存CityManager.loadFromSqlite结果，避免每20tick重复全表加载城市数据（9.39%→~0%）。 */
@Mixin(CityManager.class)
public class CityManagerLoadCacheMixin {

    private static final Set<String> NSUK_LOADED = ConcurrentHashMap.newKeySet();

    @Inject(method = "loadFromSqlite", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$skipIfLoaded(ServerLevel level, CallbackInfo ci) {
        if (NSUK_LOADED.contains(level.dimension().location().toString())) {
            ci.cancel();
        }
    }

    @Inject(method = "loadFromSqlite", at = @At("TAIL"), remap = false)
    private void nsuk$markLoaded(ServerLevel level, CallbackInfo ci) {
        NSUK_LOADED.add(level.dimension().location().toString());
    }

    @Inject(method = "reloadFromSqlite", at = @At("HEAD"), remap = false)
    private void nsuk$resetFlag(ServerLevel level, CallbackInfo ci) {
        NSUK_LOADED.remove(level.dimension().location().toString());
    }
}

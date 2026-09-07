package com.xy2407.nsukaddition.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 防御性修复：ChunkMap.addEntity 的 `entityMap.containsKey(id)` 判定改为始终 false，避免重复追踪时抛 "Entity is already tracked!" 崩溃。 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapAddEntityGuardMixin {

    @Redirect(method = "addEntity",
            at = @At(value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;containsKey(I)Z"))
    private boolean nsuk$skipAlreadyTracked(Int2ObjectMap<?> map, int id) {
        // 正常新增时该 id 本就不存在(返回 false 与原逻辑一致)；重复追踪时也不再抛异常，
        // 让后面的 entityMap.put(id, ...) 覆盖重建，达到自愈而不崩服。
        return false;
    }
}
package com.xy2407.nsukaddition.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.xy2407.nsukaddition.common.material.MaterialCategoryExpander;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashSet;

/** 修改 WorkMaterialPolicy，扩展材料匹配规则使同组材料可互相替换。 */
@Mixin(value = common.cn.kafei.simukraft.material.WorkMaterialPolicy.class, remap = false)
public class WorkMaterialPolicyMixin {

    @ModifyVariable(
            method = "buildRequest(Lnet/minecraft/world/level/block/state/BlockState;Ljava/lang/String;Lcommon/cn/kafei/simukraft/material/WorkMaterialPolicy$ConfigSnapshot;)Lcommon/cn/kafei/simukraft/material/WorkMaterialRequest;",
            at = @At(value = "INVOKE", target = "Ljava/util/LinkedHashSet;isEmpty()Z", ordinal = 0),
            ordinal = 0,
            remap = false
    )
    private static LinkedHashSet<Item> nsuk$expandMemberMatching(
            LinkedHashSet<Item> acceptedItems,
            @Local(argsOnly = true) String blockId
    ) {
        MaterialCategoryExpander.expandGroupMembers(blockId, acceptedItems);
        return acceptedItems;
    }
}

package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingControlBoxService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingInventory;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLookupResult;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修改矿物钻井控制箱的启动/选深校验：
 * 1. 深度无矿脉时也能启动/继续（firstSlotAtDepth 返回 null 时替换为圆石兜底槽位）；
 * 2. 不区分钻头类型（drillBitSupportsDepth 恒按"钻头非空"判定），浅/深钻头均可。
 */
@Mixin(value = MineralDrillingControlBoxService.class, remap = false)
public abstract class MineralDrillingControlBoxServiceMixin {

    private static final String COBBLE_VEIN_ID = "xy2407_cobble_fallback";

    private static final VirtualVeinSlot COBBLE_FALLBACK_SLOT = new VirtualVeinSlot(
            COBBLE_VEIN_ID,
            "圆石",
            BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE),
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            192,
            1_200,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            VirtualVeinSlotState.ACTIVE
    );

    @Redirect(
            method = {"toggleRunning", "setDrillDepth"},
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/mineraldrilling/MineralDrillingControlBoxService;firstSlotAtDepth(Lcommon/cn/kafei/simukraft/virtualvein/VirtualVeinLookupResult;I)Lcommon/cn/kafei/simukraft/virtualvein/VirtualVeinSlot;", remap = false),
            require = 0, allow = 2
    )
    private static VirtualVeinSlot nsuk$fallbackSlot(VirtualVeinLookupResult lookup, int depth) {
        VirtualVeinSlot original = firstSlotAtDepthOriginal(lookup, depth);
        return original != null ? original : COBBLE_FALLBACK_SLOT;
    }

    @Inject(method = "drillBitSupportsDepth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void nsuk$supportsAnyBit(MineralDrillingInventory inventory, int depth,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (inventory == null) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack bit = inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT);
        cir.setReturnValue(bit != null && !bit.isEmpty());
    }

    private static VirtualVeinSlot firstSlotAtDepthOriginal(VirtualVeinLookupResult lookup, int depth) {
        if (lookup == null || !lookup.isReady() || lookup.profile() == null) {
            return null;
        }
        return lookup.profile().slots().stream()
                .filter(slot -> slot.state() == VirtualVeinSlotState.ACTIVE)
                .filter(slot -> slot.acceptsY(depth))
                .findFirst()
                .orElse(null);
    }
}
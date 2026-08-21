package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingInventory;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingOutputService;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingWorkService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinConsumption;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinLocatedSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlot;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinSlotState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 修改矿物钻井工作服务：
 * 1. 深度无矿脉时不再暂停，产出圆石（每 1200 tick 192 个）；
 * 2. 正常矿脉开采时额外产出圆石，数量 = 当次产出矿物总数 × 2；
 * 3. 不区分钻头类型，浅/深钻头均可；
 * 4. 每次成功产出固定消耗钻头 60 点耐久，耐久不足也继续运行直到钻头爆掉。
 * 持久化由官方 manager.persist 自动接管。
 */
@Mixin(value = MineralDrillingWorkService.class, remap = false)
public abstract class MineralDrillingWorkServiceMixin {

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
            method = "process",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/virtualvein/VirtualVeinService;findVeinsAtY(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Ljava/util/List;", remap = false),
            require = 0, allow = 1
    )
    private static List<VirtualVeinLocatedSlot> nsuk$veinsWithCobbleFallback(ServerLevel level, BlockPos pos) {
        List<VirtualVeinLocatedSlot> original = VirtualVeinService.findVeinsAtY(level, pos);
        if (!original.isEmpty()) {
            return original;
        }
        return List.of(new VirtualVeinLocatedSlot(0, COBBLE_FALLBACK_SLOT));
    }

    @Redirect(
            method = "process",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/virtualvein/VirtualVeinService;consume(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/String;I)Ljava/util/Optional;", remap = false),
            require = 0, allow = 1
    )
    private static Optional<VirtualVeinConsumption> nsuk$consume(ServerLevel level, BlockPos pos, String veinId, int amount) {
        if (COBBLE_VEIN_ID.equals(veinId)) {
            return Optional.of(new VirtualVeinConsumption(amount, Integer.MAX_VALUE - amount, false));
        }
        return VirtualVeinService.consume(level, pos, veinId, amount);
    }

    @Redirect(
            method = "process",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/mineraldrilling/MineralDrillingOutputService;storeAll(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/building/PlacedBuildingRecord;Ljava/util/List;)Z", remap = false),
            require = 0, allow = 2
    )
    private static boolean nsuk$storeAllWithCobbleBonus(ServerLevel level,
                                                         common.cn.kafei.simukraft.building.PlacedBuildingRecord building,
                                                         List<ItemStack> outputs) {
        return MineralDrillingOutputService.storeAll(level, building, appendCobbleBonus(outputs));
    }

    private static List<ItemStack> appendCobbleBonus(List<ItemStack> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return outputs;
        }
        boolean anyCobble = outputs.stream().anyMatch(stack -> stack != null && stack.is(Items.COBBLESTONE));
        if (anyCobble) {
            return outputs;
        }
        int total = outputs.stream()
                .filter(stack -> stack != null)
                .mapToInt(ItemStack::getCount)
                .sum();
        if (total <= 0) {
            return outputs;
        }
        List<ItemStack> result = new ArrayList<>(outputs);
        result.add(new ItemStack(Items.COBBLESTONE, Math.min(total * 2, 64)));
        return result;
    }

    @Inject(method = "supportsDepth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void nsuk$supportsAnyBit(ItemStack bit, int depth, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(bit != null && !bit.isEmpty());
    }

    @Inject(method = "consumeDrillBitDurability", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void nsuk$fixedDurabilityCost(ServerLevel level,
                                                 MineralDrillingInventory inventory,
                                                 int produced,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (level == null || inventory == null) {
            cir.setReturnValue(false);
            return;
        }
        synchronized (inventory) {
            ItemStack bit = inventory.getItem(MineralDrillingInventory.DRILL_BIT_SLOT);
            if (bit.isEmpty()) {
                cir.setReturnValue(true);
                return;
            }
            if (!bit.isDamageableItem()) {
                inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT, ItemStack.EMPTY);
                cir.setReturnValue(true);
                return;
            }
            bit.hurtAndBreak(60, level, (net.minecraft.server.level.ServerPlayer) null, ignored -> {
            });
            inventory.setItem(MineralDrillingInventory.DRILL_BIT_SLOT, bit);
            cir.setReturnValue(bit.isEmpty());
        }
    }
}
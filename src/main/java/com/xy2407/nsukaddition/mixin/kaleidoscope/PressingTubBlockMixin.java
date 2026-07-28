package com.xy2407.nsukaddition.mixin.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IPressingTub;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.PressingTubBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让 Kaleidoscope 空瓶右键满果盆时产出 Vinery 对应葡萄汁瓶而非铁桶。 */
@Mixin(PressingTubBlock.class)
public class PressingTubBlockMixin {

    private static final String[][] JUICE_FLUID_TO_VINERY_ITEM = {
            {"red_grape_juice", "red_grapejuice"},
            {"red_savanna_grape_juice", "red_savanna_grapejuice"},
            {"red_taiga_grape_juice", "red_taiga_grapejuice"},
            {"red_jungle_grape_juice", "red_jungle_grapejuice"},
            {"white_grape_juice", "white_grapejuice"},
            {"white_savanna_grape_juice", "white_savanna_grapejuice"},
            {"white_taiga_grape_juice", "white_taiga_grapejuice"},
            {"white_jungle_grape_juice", "white_jungle_grapejuice"},
            {"apple_juice", "apple_juice"},
    };

    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lcom/github/ysbbbbbb/kaleidoscopetavern/api/blockentity/IPressingTub;getResult(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"), cancellable = true)
    private void nsuk$interceptGetResult(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult,
                                          CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!stack.is(ModItems.EMPTY_BOTTLE.get())) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof IPressingTub pressingTub)) {
            return;
        }
        if (pressingTub.getFluidAmount() < IPressingTub.MAX_FLUID_AMOUNT) {
            return;
        }

        FluidTank fluidTank = pressingTub.getFluid();
        FluidStack fluidStack = fluidTank.getFluid();
        if (fluidStack.isEmpty()) {
            return;
        }

        Fluid fluid = fluidStack.getFluid();
        String fluidPath = BuiltInRegistries.FLUID.getKey(fluid).getPath();
        boolean isOurJuice = false;
        for (String[] mapping : JUICE_FLUID_TO_VINERY_ITEM) {
            if (mapping[0].equals(fluidPath)) {
                isOurJuice = true;
                break;
            }
        }
        if (!isOurJuice) {
            return;
        }

        Item juiceItem = null;
        for (String[] mapping : JUICE_FLUID_TO_VINERY_ITEM) {
            if (mapping[0].equals(fluidPath)) {
                juiceItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("vinery", mapping[1]));
                break;
            }
        }
        if (juiceItem == null || juiceItem == net.minecraft.world.item.Items.AIR) {
            return;
        }

        stack.shrink(1);
        if (!player.getInventory().add(new ItemStack(juiceItem))) {
            player.drop(new ItemStack(juiceItem), false);
        }
        fluidTank.drain(IPressingTub.MAX_FLUID_AMOUNT, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}

package com.xy2407.nsukaddition.common.fluid;

import com.xy2407.nsukaddition.common.registry.ModMilkFluids;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/** 奶流体类型：复用原版水纹理并按牛奶种类染色，区分 7 种奶。 */
public class MilkFluidType extends FluidType {
    private final int tint;

    public MilkFluidType(ModMilkFluids.MilkType type) {
        super(FluidType.Properties.create()
                .descriptionId(Util.makeDescriptionId("block", type.id()))
                .fallDistanceModifier(0)
                .canExtinguish(true)
                .canConvertToSource(false)
                .supportsBoating(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
        this.tint = type.tint;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
            private static final ResourceLocation FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

            @Override
            public ResourceLocation getStillTexture() {
                return STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLOW;
            }

            @Override
            public int getTintColor() {
                return tint;
            }
        });
    }
}

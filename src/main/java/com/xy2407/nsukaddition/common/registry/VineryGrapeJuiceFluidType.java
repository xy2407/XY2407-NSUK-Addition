package com.xy2407.nsukaddition.common.registry;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/** Vinery 葡萄汁流体类型，复用 Kaleidoscope JuiceFluidType 的渲染逻辑，纹理指向项目自身资源。 */
public class VineryGrapeJuiceFluidType extends FluidType {
    private final ModFluids.GrapeJuice juice;
    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;

    public VineryGrapeJuiceFluidType(ModFluids.GrapeJuice juice) {
        super(FluidType.Properties.create()
                .descriptionId(Util.makeDescriptionId("block", juice.id()))
                .fallDistanceModifier(0)
                .canExtinguish(true)
                .canConvertToSource(false)
                .supportsBoating(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .canHydrate(true)
                .lightLevel(0));
        this.juice = juice;
        this.stillTexture = ResourceLocation.fromNamespaceAndPath(juice.id().getNamespace(),
                "block/" + juice.id().getPath() + "_still");
        this.flowingTexture = ResourceLocation.fromNamespaceAndPath(juice.id().getNamespace(),
                "block/" + juice.id().getPath() + "_flow");
    }

    public ModFluids.GrapeJuice getJuice() {
        return juice;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowingTexture;
            }

            @Override
            public int getTintColor() {
                return juice.colorTint;
            }
        });
    }
}

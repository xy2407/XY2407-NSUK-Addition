package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.block.CoagulatingMilkBlock;
import com.xy2407.nsukaddition.common.fluid.MilkFluidType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** 七种奶流体注册：对应 Meadow 七种木奶桶/奶酪轮，放置后 1200 tick 凝固为对应奶酪（按 ID 匹配）。 */
public final class ModMilkFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, NsukAddition.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, NsukAddition.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NsukAddition.MOD_ID);

    public enum MilkType {
        MILK("milk", "meadow:cheese_wheel", "meadow:wooden_milk_bucket", 0xFFFFFFFF),
        SHEEP("sheep_milk", "meadow:sheep_cheese_wheel", "meadow:wooden_sheep_milk_bucket", 0xFFF7F3EA),
        BUFFALO("buffalo_milk", "meadow:buffalo_cheese_wheel", "meadow:wooden_buffalo_milk_bucket", 0xFFFFF6E8),
        GOAT("goat_milk", "meadow:goat_cheese_wheel", "meadow:wooden_goat_milk_bucket", 0xFFFDFBF4),
        WARPED("warped_milk", "meadow:warped_cheese_wheel", "meadow:wooden_warped_milk_bucket", 0xFF8FE3E3),
        GRAIN("grain_milk", "meadow:grain_cheese_wheel", "meadow:wooden_grain_milk_bucket", 0xFFF5D976),
        AMETHYST("amethyst_milk", "meadow:amethyst_cheese_wheel", "meadow:wooden_amethyst_milk_bucket", 0xFFD3B5F0);

        public final String name;
        public final String cheeseBlockId;
        public final String bucketItemId;
        public final int tint;

        MilkType(String name, String cheeseBlockId, String bucketItemId, int tint) {
            this.name = name;
            this.cheeseBlockId = cheeseBlockId;
            this.bucketItemId = bucketItemId;
            this.tint = tint;
        }

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, name + "_fluid");
        }
    }

    public static MilkType milkTypeByBucket(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String idStr = id.toString();
        for (MilkType type : MilkType.values()) {
            if (type.bucketItemId.equals(idStr)) {
                return type;
            }
        }
        return null;
    }

    public static final Map<MilkType, Entry> ENTRIES = new EnumMap<>(MilkType.class);

    static {
        for (MilkType type : MilkType.values()) {
            ENTRIES.put(type, registerMilk(type));
        }
    }

    private static Entry registerMilk(MilkType type) {
        Supplier<FluidType> fluidType = FLUID_TYPES.register(type.name, () -> new MilkFluidType(type));
        Supplier<BaseFlowingFluid.Source> sourceRef = () -> (BaseFlowingFluid.Source) BuiltInRegistries.FLUID.get(type.id());
        Supplier<BaseFlowingFluid.Flowing> flowingRef = () -> (BaseFlowingFluid.Flowing) BuiltInRegistries.FLUID.get(flowingId(type));

        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(fluidType, sourceRef, flowingRef)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1);

        Supplier<BaseFlowingFluid.Source> source = FLUIDS.register(type.name + "_fluid", () -> new BaseFlowingFluid.Source(props));
        Supplier<BaseFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + type.name + "_fluid", () -> new BaseFlowingFluid.Flowing(props));

        DeferredBlock<LiquidBlock> block = BLOCKS.register(type.name + "_fluid",
                () -> new CoagulatingMilkBlock(source.get(),
                        BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable().randomTicks(),
                        ResourceLocation.tryParse(type.cheeseBlockId)));
        props.block(block);
        return new Entry(fluidType, source, flowing, block);
    }

    private static ResourceLocation flowingId(MilkType type) {
        return ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "flowing_" + type.name + "_fluid");
    }

    public record Entry(Supplier<FluidType> fluidType,
                        Supplier<BaseFlowingFluid.Source> source,
                        Supplier<BaseFlowingFluid.Flowing> flowing,
                        DeferredBlock<LiquidBlock> block) {
    }

    public static Block cheeseBlockOf(MilkType type) {
        Entry entry = ENTRIES.get(type);
        return entry == null ? null : entry.block().get();
    }

    private ModMilkFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
    }
}

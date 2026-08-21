package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Vinery 葡萄汁流体注册中心，统一管理 8 种葡萄汁对应的 FluidType/Fluid/Bucket。 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, NsukAddition.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, NsukAddition.MOD_ID);
    public static final DeferredRegister.Items BUCKETS = DeferredRegister.createItems(NsukAddition.MOD_ID);

    public enum GrapeJuice {
        RED_GENERAL("red_grape_juice", "red_general", 0xFF8B0000),
        RED_SAVANNA("red_savanna_grape_juice", "red_savanna", 0xFFA52A2A),
        RED_TAIGA("red_taiga_grape_juice", "red_taiga", 0xFFB22222),
        RED_JUNGLE("red_jungle_grape_juice", "red_jungle", 0xFFCD5C5C),
        WHITE_GENERAL("white_grape_juice", "white_general", 0xFFF0E68C),
        WHITE_SAVANNA("white_savanna_grape_juice", "white_savanna", 0xFFFFE4B5),
        WHITE_TAIGA("white_taiga_grape_juice", "white_taiga", 0xFFFFEFD5),
        WHITE_JUNGLE("white_jungle_grape_juice", "white_jungle", 0xFFFFF8DC),
        APPLE("apple_juice", "apple", 0xFFDAA520);

        public final String name;
        public final String vineryType;
        public final int colorTint;

        GrapeJuice(String name, String vineryType, int colorTint) {
            this.name = name;
            this.vineryType = vineryType;
            this.colorTint = colorTint;
        }

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, name);
        }
    }

    public static final Map<GrapeJuice, JuiceEntry> ENTRIES = new HashMap<>();

    static {
        for (GrapeJuice juice : GrapeJuice.values()) {
            JuiceEntry entry = registerJuice(juice);
            ENTRIES.put(juice, entry);
        }
    }

    private static JuiceEntry registerJuice(GrapeJuice juice) {
        Supplier<FluidType> type = FLUID_TYPES.register(juice.name,
                () -> new VineryGrapeJuiceFluidType(juice));
        java.util.function.Supplier<BaseFlowingFluid.Source> sourceRef = () -> (BaseFlowingFluid.Source) BuiltInRegistries.FLUID.get(juice.id());
        java.util.function.Supplier<BaseFlowingFluid.Flowing> flowingRef = () -> (BaseFlowingFluid.Flowing) BuiltInRegistries.FLUID.get(flowingId(juice));

        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(type, sourceRef, flowingRef);
        Supplier<BaseFlowingFluid.Source> source = FLUIDS.register(juice.name,
                () -> new BaseFlowingFluid.Source(props));
        Supplier<BaseFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + juice.name,
                () -> new BaseFlowingFluid.Flowing(props));

        DeferredItem<Item> bucket = BUCKETS.register(juice.name + "_bucket",
                () -> new net.minecraft.world.item.BucketItem(source.get(),
                        new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
        props.bucket(bucket);
        return new JuiceEntry(type, source, flowing, bucket);
    }

    private static ResourceLocation flowingId(GrapeJuice juice) {
        return ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "flowing_" + juice.name);
    }

    public record JuiceEntry(
            Supplier<FluidType> type,
            Supplier<BaseFlowingFluid.Source> source,
            Supplier<BaseFlowingFluid.Flowing> flowing,
            DeferredItem<Item> bucket
    ) {
    }

    private ModFluids() {
    }

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BUCKETS.register(modEventBus);
    }

    public static Fluid getByVineryJuiceType(String vineryType) {
        for (GrapeJuice juice : GrapeJuice.values()) {
            if (juice.vineryType.equals(vineryType)) {
                JuiceEntry entry = ENTRIES.get(juice);
                return entry == null ? null : entry.source().get();
            }
        }
        return null;
    }

    public static Fluid getSourceByName(String name) {
        for (GrapeJuice juice : GrapeJuice.values()) {
            if (juice.name.equals(name)) {
                JuiceEntry entry = ENTRIES.get(juice);
                return entry == null ? null : entry.source().get();
            }
        }
        return null;
    }
}

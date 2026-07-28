package com.xy2407.nsukaddition.common.compat.vinerykaleidoscope;

import com.xy2407.nsukaddition.common.registry.ModFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** 给 Vinery 果汁瓶/空酒瓶附加 IFluidHandlerItem，使 Kaleidoscope 酿酒桶能识别它们为流体容器。 */
public final class VineryFluidCompat {

    private static final String VINERY = "vinery";
    private static final int BOTTLE_CAPACITY = 1000;
    private static final Map<Item, Fluid> JUICE_ITEM_TO_FLUID = new HashMap<>();
    private static boolean initialized = false;

    private VineryFluidCompat() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded(VINERY)) {
            return;
        }

        initMappings();

        Item wineBottle = getItem("wine_bottle");
        if (wineBottle != null) {
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) ->
                    new VineryBottleFluidHandler(stack, false, FluidStack.EMPTY, new ItemStack(wineBottle)),
                    wineBottle);
        }

        for (Map.Entry<Item, Fluid> entry : JUICE_ITEM_TO_FLUID.entrySet()) {
            Item juiceItem = entry.getKey();
            Fluid fluid = entry.getValue();
            event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) ->
                    new VineryBottleFluidHandler(stack, true, new FluidStack(fluid, BOTTLE_CAPACITY),
                            wineBottle != null ? new ItemStack(wineBottle) : ItemStack.EMPTY),
                    juiceItem);
        }
    }

    private static synchronized void initMappings() {
        if (initialized) return;
        initialized = true;

        registerJuiceMapping("red_grapejuice", ModFluids.GrapeJuice.RED_GENERAL);
        registerJuiceMapping("white_grapejuice", ModFluids.GrapeJuice.WHITE_GENERAL);
        registerJuiceMapping("red_savanna_grapejuice", ModFluids.GrapeJuice.RED_SAVANNA);
        registerJuiceMapping("white_savanna_grapejuice", ModFluids.GrapeJuice.WHITE_SAVANNA);
        registerJuiceMapping("red_taiga_grapejuice", ModFluids.GrapeJuice.RED_TAIGA);
        registerJuiceMapping("white_taiga_grapejuice", ModFluids.GrapeJuice.WHITE_TAIGA);
        registerJuiceMapping("red_jungle_grapejuice", ModFluids.GrapeJuice.RED_JUNGLE);
        registerJuiceMapping("white_jungle_grapejuice", ModFluids.GrapeJuice.WHITE_JUNGLE);
        registerJuiceMapping("apple_juice", ModFluids.GrapeJuice.APPLE);
    }

    private static void registerJuiceMapping(String vineryItemName, ModFluids.GrapeJuice juice) {
        Item item = getItem(vineryItemName);
        if (item != null) {
            ModFluids.JuiceEntry entry = ModFluids.ENTRIES.get(juice);
            if (entry != null) {
                JUICE_ITEM_TO_FLUID.put(item, entry.source().get());
            }
        }
    }

    private static Item getItem(String name) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(VINERY, name));
        return item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    /** Vinery 酒瓶/果汁瓶的 IFluidHandlerItem 实现。 */
    private static class VineryBottleFluidHandler implements IFluidHandlerItem {
        private final ItemStack container;
        private final boolean isFilled;
        private final ItemStack emptyBottle;
        private FluidStack heldFluid;

        VineryBottleFluidHandler(ItemStack container, boolean isFilled, FluidStack heldFluid, ItemStack emptyBottle) {
            this.container = container;
            this.isFilled = isFilled;
            this.heldFluid = heldFluid;
            this.emptyBottle = emptyBottle;
        }

        @Override
        public @NotNull ItemStack getContainer() {
            if (!isFilled && !heldFluid.isEmpty()) {
                Item juiceItem = getItemForFluid(heldFluid.getFluid());
                if (juiceItem != null) {
                    return new ItemStack(juiceItem);
                }
                return emptyBottle.copy();
            }
            if (isFilled && heldFluid.isEmpty()) {
                return emptyBottle.copy();
            }
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return heldFluid;
        }

        @Override
        public int getTankCapacity(int tank) {
            return BOTTLE_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return isJuiceFluid(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            if (isFilled || resource.isEmpty()) return 0;
            if (!isFluidValid(0, resource)) return 0;
            int filled = Math.min(resource.getAmount(), BOTTLE_CAPACITY);
            if (action.execute()) {
                heldFluid = new FluidStack(resource.getFluid(), filled);
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            if (!isFilled || resource.isEmpty() || heldFluid.getFluid() != resource.getFluid()) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            if (!isFilled || heldFluid.isEmpty()) return FluidStack.EMPTY;
            int drained = Math.min(heldFluid.getAmount(), maxDrain);
            FluidStack result = heldFluid.copyWithAmount(drained);
            if (action.execute()) {
                heldFluid = heldFluid.copyWithAmount(heldFluid.getAmount() - drained);
            }
            return result;
        }

        private Item getItemForFluid(Fluid fluid) {
            for (Map.Entry<Item, Fluid> entry : JUICE_ITEM_TO_FLUID.entrySet()) {
                if (entry.getValue().equals(fluid)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        private boolean isJuiceFluid(Fluid fluid) {
            return JUICE_ITEM_TO_FLUID.containsValue(fluid);
        }
    }
}

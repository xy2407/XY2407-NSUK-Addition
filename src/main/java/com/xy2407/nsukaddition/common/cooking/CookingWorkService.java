package com.xy2407.nsukaddition.common.cooking;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StoveBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.decoration.TableBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.hakimen.kawaiidishes.block_entities.BlenderBlockEntity;
import com.hakimen.kawaiidishes.block_entities.CoffeeMachineBlockEntity;
import com.hakimen.kawaiidishes.block_entities.IceCreamMakerBlockEntity;
import com.hakimen.kawaiidishes.recipes.BlenderRecipe;
import com.hakimen.kawaiidishes.recipes.CoffeeMachineRecipe;
import com.hakimen.kawaiidishes.recipes.IceCreamMakerRecipe;
import com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.GlasswareBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.mixology.ShakerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.mixology.SignatureCocktailBlockEntity;
import com.github.ysbbbbbb.kaleidoscopetavern.crafting.container.SimpleInput;
import com.github.ysbbbbbb.kaleidoscopetavern.crafting.recipe.ShakerRecipe;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopetavern.item.BottleBlockItem;
import com.github.ysbbbbbb.kaleidoscopetavern.item.ShakerItem;
import com.github.ysbbbbbb.kaleidoscopetavern.util.CocktailEffectHelper;
import com.github.ysbbbbbb.kaleidoscopetavern.util.ColorUtils;
import com.renyigesai.bakeries.common.blocks.oven.OvenBlockEntity;
import com.renyigesai.bakeries.common.recipe.BreadKnifeRecipe;
import com.renyigesai.bakeries.common.recipe.oven.OvenRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockService;
import com.xy2407.nsukaddition.common.breeding.BreedingInventoryHelper;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData.OrderStatus;
import com.xy2407.nsukaddition.common.cooking.RestaurantRecipes.DeviceType;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.BlockItem;
import net.satisfy.brewery.core.block.entity.BrewstationBlockEntity;
import net.satisfy.brewery.core.block.property.Heat;
import net.satisfy.brewery.core.block.property.Liquid;
import net.satisfy.brewery.core.event.brew_event.BrewEvent;
import net.satisfy.brewery.core.event.brew_event.BrewHelper;
import net.satisfy.brewery.core.event.brew_event.KettleEvent;
import net.satisfy.brewery.core.event.brew_event.OvenEvent;
import net.satisfy.brewery.core.event.brew_event.TimerEvent;
import net.satisfy.brewery.core.event.brew_event.WhistleEvent;
import net.satisfy.brewery.core.recipe.BrewingRecipe;
import net.satisfy.brewery.core.registry.BlockStateRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 烹饪工作服务，每5 tick驱动餐厅：订单队列→厨师烹饪→服务员上菜。原料由 recipe manager 反查，不硬编码。 */
@SuppressWarnings("null")
public final class CookingWorkService {
    private static final long VALIDATE_INTERVAL = 40L;
    private static final long TICK_INTERVAL = 5L;
    private static final long CHEF_INTERVAL = 15L;
    private static final long WAITER_INTERVAL = 40L;
    private static final long ORDER_RECOVER_INTERVAL = 2000L;
    private static final int DEVICE_SEARCH_RADIUS = 5;
    private static final int DIRECT_CRAFT_TICKS = 40;

    private static final ConcurrentMap<BlockPos, BoxRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private CookingWorkService() {}

    public static void tick(ServerLevel level) {
        if (level == null) return;
        long gameTime = level.getGameTime();
        if (gameTime % TICK_INTERVAL != 0L) return;
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        Set<BlockPos> activePositions = ConcurrentHashMap.newKeySet();
        for (RestaurantBoxData data : manager.all()) {
            BlockPos key = data.boxPos().immutable();
            activePositions.add(key);
            BoxRuntime rt = RUNTIMES.computeIfAbsent(key, k -> new BoxRuntime());
            if (!data.running()) { rt.reset(); continue; }
            if (!level.isLoaded(data.boxPos())) continue;
            tickBox(level, manager, data, rt, gameTime);
            RestaurantControlBoxViewSyncService.syncStatusIfChanged(level, data);
        }
        RUNTIMES.keySet().retainAll(activePositions);
    }

    public static void resetBoxRuntime(BlockPos boxPos) {
        if (boxPos != null) {
            RUNTIMES.remove(boxPos.immutable());
        }
    }

    private static void tickBox(ServerLevel level, RestaurantBoxManager manager, RestaurantBoxData data, BoxRuntime rt, long gameTime) {
        BlockPos boxPos = data.boxPos();
        if (gameTime - rt.lastValidate >= VALIDATE_INTERVAL) {
            rt.lastValidate = gameTime;
            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, boxPos);
            RestaurantDefinitionLoader.LoadResult loadResult = RestaurantDefinitionLoader.loadForBuilding(building);
            RestaurantDefinition definition = loadResult.definition();
            RestaurantControlBoxService.synchronizeBoxMetadata(level, data, building, definition);
            CitizenData chef = RestaurantControlBoxService.findAssignedWorker(level, boxPos, RestaurantConstants.HIRE_ROLE_CHEF);
            CitizenData waiter = RestaurantControlBoxService.findAssignedWorker(level, boxPos, RestaurantConstants.HIRE_ROLE_WAITER);
            if (building == null) { setStatus(manager, data, RestaurantConstants.STATUS_NO_BUILDING, ""); return; }
            if (!loadResult.valid()) { setStatus(manager, data, RestaurantConstants.STATUS_INVALID_DEFINITION, ""); return; }
            rt.building = building; rt.definition = definition; rt.chef = chef; rt.waiter = waiter;
            rt.dataPos = boxPos;
        }
        if (rt.building == null || rt.definition == null) return;
        if (common.cn.kafei.simukraft.citizen.CitizenHomeRestService.isRestTime(level)) {
            if (rt.chef != null && gameTime - rt.lastCookTick >= CHEF_INTERVAL) {
                rt.lastCookTick = gameTime;
                tickChef(level, manager, data, rt);
            }
            if (rt.waiter != null && gameTime - rt.lastWaiterTick >= WAITER_INTERVAL) {
                rt.lastWaiterTick = gameTime;
                tickWaiter(level, data, rt, gameTime);
            }
            if (!data.orders().isEmpty()) {
                setTransientStatus(manager, data, RestaurantConstants.STATUS_PAUSED, "");
                return;
            }
            if (!rt.nightClosed) {
                recoverLostOrders(level, manager, data, rt);
                if (data.orders().isEmpty()) {
                    rt.nightClosed = true;
                }
            }
            setTransientStatus(manager, data, RestaurantConstants.STATUS_PAUSED, "");
            return;
        }
        rt.nightClosed = false;
        if (data.selectedCookItems().isEmpty()) {
            setStatus(manager, data, RestaurantConstants.STATUS_NO_RECIPE, "");
            return;
        }

        if (rt.chef != null && gameTime - rt.lastCookTick >= CHEF_INTERVAL) {
            rt.lastCookTick = gameTime;
            tickChef(level, manager, data, rt);
        }
        if (rt.waiter != null && gameTime - rt.lastWaiterTick >= WAITER_INTERVAL) {
            rt.lastWaiterTick = gameTime;
            tickWaiter(level, data, rt, gameTime);
        }
        if (gameTime - rt.lastOrderRecoverTick >= ORDER_RECOVER_INTERVAL) {
            rt.lastOrderRecoverTick = gameTime;
            recoverLostOrders(level, manager, data, rt);
        }
        setWorkerHeldItems(rt);
        fillHunger(level, rt.chef, rt.waiter);
        if (data.orders().stream().noneMatch(o -> o.status() == OrderStatus.COOKING || o.status() == OrderStatus.PENDING)) {
            setTransientStatus(manager, data, RestaurantConstants.STATUS_RUNNING, "");
        }
    }

    private static void recoverLostOrders(ServerLevel level, RestaurantBoxManager manager,
                                           RestaurantBoxData data, BoxRuntime rt) {
        if (rt.definition == null || rt.building == null) return;
        java.util.Set<UUID> orderedCitizens = new java.util.HashSet<>();
        for (var o : data.orders()) {
            orderedCitizens.add(o.customerId());
        }
        List<BlockPos> worldSeats = IndustrialCoordinateResolver.resolvePositions(rt.building, rt.definition.allSeatPositions());
        for (BlockPos seat : worldSeats) {
            if (data.isSeatFree(seat)) continue;
            UUID occupantId = RestaurantDiningService.findOccupantAt(seat, data.boxPos());
            if (occupantId == null || orderedCitizens.contains(occupantId)) continue;
            java.util.List<String> pool = data.selectedCookItems().isEmpty()
                    ? rt.definition.cook() : new java.util.ArrayList<>(data.selectedCookItems());
            String outputItemId = pool.isEmpty() ? "" : pool.get(level.random.nextInt(pool.size()));
            if (!outputItemId.isEmpty()) {
                data.addOrder(occupantId, seat, outputItemId);
                manager.persist(data);
            }
        }
    }

    private static void tickChef(ServerLevel level, RestaurantBoxManager manager, RestaurantBoxData data, BoxRuntime rt) {
        if (rt.chefCooking) { tickDeviceCooking(level, rt); return; }

        boolean hasStuck = false;
        for (int i = 0; i < data.orders().size(); i++) {
            if (data.orders().get(i).status() == OrderStatus.COOKING) {
                var o = data.orders().get(i);
                data.orders().set(i, new RestaurantBoxData.OrderEntry(o.customerId(), o.seatPos(), o.recipeId(), OrderStatus.PENDING));
                hasStuck = true;
            }
        }
        if (hasStuck) manager.persist(data);

        RestaurantBoxData.OrderEntry order = data.nextPendingOrder();
        if (order == null) return;

        String outputItemId = order.recipeId();

        ResolvedRecipe resolved = findRecipe(level, outputItemId);
        if (resolved == null) {
            return;
        }
        rt.resolvedRecipe = resolved;

        List<BlockPos> inputs = resolvePositions(rt.building, rt.definition, "input", rt.dataPos);
        if (!hasInputs(level, inputs, resolved.ingredients())) {
            if (AutoRestockConfig.isEnabled(data.boxPos())) {
                AutoRestockService.restockRestaurantInputs(level, data.boxPos());
                if (!hasInputs(level, inputs, resolved.ingredients())) {
                    return;
                }
            } else {
                return;
            }
        }

        List<BlockPos> workPositions = resolvePositions(rt.building, rt.definition, "work", rt.dataPos);
        if (workPositions.isEmpty()) return;
        BlockPos workPos = workPositions.getFirst();
        BlockPos devicePos;
        if (resolved.device() == DeviceType.DIRECT) {
            devicePos = workPos;
        } else if (resolved.device() == DeviceType.TAVERN_SHAKER && !rt.definition.outputBlock().isEmpty()) {
            devicePos = resolveOutputBlocks(rt).getFirst();
        } else {
            devicePos = findDevice(level, workPos, resolved.device());
            if (devicePos == null) {
                setTransientStatus(manager, data, RestaurantConstants.STATUS_NO_DEVICE, outputItemId);
                return;
            }
        }

        data.orders().remove(order);
        data.orders().add(new RestaurantBoxData.OrderEntry(order.customerId(), order.seatPos(), order.recipeId(), OrderStatus.COOKING));
        manager.persist(data);
        rt.chefOutputItemId = outputItemId;
        rt.deviceType = resolved.device();
        rt.devicePos = devicePos.immutable();
        rt.chefCooking = true;
        rt.putIngredientIndex = 0;

        CitizenEntity entity = findChefEntity(level, rt);
        if (entity != null) {
            Vec3 target = Vec3.atBottomCenterOf(devicePos);
            CitizenNavigationService.requestMove(level, rt.chef.uuid(), target, MovementIntent.WORK);
        }
        setTransientStatus(manager, data, RestaurantConstants.STATUS_COOKING, outputItemId);
    }

    private static void tickDeviceCooking(ServerLevel level, BoxRuntime rt) {
        if (rt.pendingOutput) { finishCooking(level, rt); return; }
        if (rt.devicePos == null) { finishCooking(level, rt); return; }
        if (!level.isLoaded(rt.devicePos)) return;
        BlockEntity be = level.getBlockEntity(rt.devicePos);
        if (rt.deviceType == DeviceType.TAVERN_SHAKER && rt.definition != null
                && !rt.definition.outputBlock().isEmpty() && !(be instanceof ShakerBlockEntity)) {
            level.setBlock(rt.devicePos, ModBlocks.SHAKER.get().defaultBlockState(), 3);
            be = level.getBlockEntity(rt.devicePos);
        }
        if (be == null && rt.deviceType != DeviceType.DIRECT) {
            finishCooking(level, rt);
            return;
        }

        CitizenEntity chef = findChefEntity(level, rt);
        if (chef == null) return;

        switch (rt.deviceType) {
            case POT -> tickPot(level, rt, (PotBlockEntity) be, chef);
            case STOCKPOT -> tickStockpot(level, rt, (StockpotBlockEntity) be, chef);
            case STEAMER -> tickSteamer(level, rt, (SteamerBlockEntity) be, chef);
            case BAKERY_OVEN -> tickBakeryOven(level, rt, (OvenBlockEntity) be, chef);
            case TAVERN_SHAKER -> tickShaker(level, rt, (ShakerBlockEntity) be, chef);
            case KAWAII_BLENDER -> tickKawaiiMachine(level, rt, ((BlenderBlockEntity) be).getInventory(),
                    4, 3, new int[]{0, 1, 2}, -1, false, false, chef, null);
            case KAWAII_COFFEE_MACHINE -> tickKawaiiMachine(level, rt, ((CoffeeMachineBlockEntity) be).getInventory(),
                    6, 5, new int[]{2, 3, 4}, 0, false, true, chef, ((CoffeeMachineBlockEntity) be).getWaterTank());
            case KAWAII_ICE_CREAM_MAKER -> tickKawaiiMachine(level, rt, ((IceCreamMakerBlockEntity) be).getInventory(),
                    5, 4, new int[]{1, 2, 3}, 0, true, false, chef, null);
            case BREWERY_BREWSTATION -> tickBreweryBrewstation(level, rt, (BrewstationBlockEntity) be, chef);
            case DIRECT -> tickDirectConversion(level, rt, chef);
        }
    }

    private static void tickDirectConversion(ServerLevel level, BoxRuntime rt, CitizenEntity chef) {
        if (rt.directProgress == 0) {
            rt.directProgress = DIRECT_CRAFT_TICKS;
        }
        rt.directProgress--;
        if (rt.directProgress > 0) {
            return;
        }
        if (!consumeIngredients(level, rt)) {
            rt.directProgress = 0;
            return;
        }
        rt.outputDeposited = false;
        rt.pendingOutput = true;
        finishCooking(level, rt);
    }

    private static boolean consumeIngredients(ServerLevel level, BoxRuntime rt) {
        List<BlockPos> inputs = resolvePositions(rt.building, rt.definition, "input", rt.dataPos);
        for (Ingredient ing : rt.resolvedRecipe.ingredients()) {
            if (ing.isEmpty()) continue;
            boolean taken = false;
            for (BlockPos pos : inputs) {
                var c = BreedingInventoryHelper.containerAt(level, pos);
                if (c == null) continue;
                for (int i = 0; i < c.getContainerSize(); i++) {
                    ItemStack s = c.getItem(i);
                    if (!s.isEmpty() && ing.test(s)) {
                        s.shrink(1);
                        taken = true;
                        break;
                    }
                }
                if (taken) break;
            }
            if (!taken) return false;
        }
        return true;
    }

    private static void tickPot(ServerLevel level, BoxRuntime rt, PotBlockEntity pot, CitizenEntity chef) {
        if (!pot.hasHeatSource(level)) { tryLightStoveBelow(level, pot.getBlockPos(), chef); return; }

        switch (pot.getStatus()) {
            case IPot.PUT_INGREDIENT -> {
                BlockState state = level.getBlockState(pot.getBlockPos());
                if (!state.getValue(PotBlock.HAS_OIL)) {
                    ItemStack oil = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kaleidoscope_cookery:oil")));
                    if (!oil.isEmpty()) {
                        pot.onPlaceOil(level, chef, oil);
                        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                    }
                    return;
                }
                if (rt.putIngredientIndex < rt.resolvedRecipe.ingredients().size()) {
                    Ingredient ing = rt.resolvedRecipe.ingredients().get(rt.putIngredientIndex);
                    if (ing.isEmpty()) { rt.putIngredientIndex++; return; }
                    ItemStack item = extractByIngredient(level, rt, ing);
                    if (!item.isEmpty()) {
                        pot.addIngredient(level, chef, item);
                        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                        rt.putIngredientIndex++;
                    }
                    return;
                }
                ItemStack shovel = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kaleidoscope_cookery:kitchen_shovel")));
                if (!shovel.isEmpty()) {
                    pot.onShovelHit(level, chef, shovel);
                    chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                }
            }
            case IPot.COOKING -> {
                ItemStack shovel = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kaleidoscope_cookery:kitchen_shovel")));
                if (!shovel.isEmpty()) {
                    pot.onShovelHit(level, chef, shovel);
                    chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                }
            }
            case IPot.FINISHED -> {
                pot.reset();
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                rt.pendingOutput = true;
            }
            case IPot.BURNT -> {
                pot.reset();
                rt.chefCooking = false;
            }
        }
    }

    private static void tickStockpot(ServerLevel level, BoxRuntime rt, StockpotBlockEntity pot, CitizenEntity chef) {
        if (!pot.hasHeatSource(level)) { tryLightStoveBelow(level, pot.getBlockPos(), chef); return; }

        switch (pot.getStatus()) {
            case IStockpot.PUT_SOUP_BASE -> {
                if (pot.hasLid()) { takeStockpotLid(level, pot); return; }
                ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                ItemStack savedMainHand = chef.getMainHandItem().copy();
                chef.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                pot.addSoupBase(level, chef, waterBucket);
                chef.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
            }
            case IStockpot.PUT_INGREDIENT -> {
                if (pot.hasLid()) { takeStockpotLid(level, pot); return; }
                if (rt.putIngredientIndex < rt.resolvedRecipe.ingredients().size()) {
                    Ingredient ing = rt.resolvedRecipe.ingredients().get(rt.putIngredientIndex);
                    if (ing.isEmpty()) { rt.putIngredientIndex++; return; }
                    ItemStack item = extractByIngredient(level, rt, ing);
                    if (!item.isEmpty()) {
                        pot.addIngredient(level, chef, item);
                        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                        rt.putIngredientIndex++;
                    }
                    return;
                }
                ItemStack lid = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kaleidoscope_cookery:stockpot_lid")));
                if (!lid.isEmpty()) {
                    pot.onLitClick(level, chef, lid);
                    chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                }
            }
            case IStockpot.COOKING -> {
                if (!pot.hasLid()) {
                    ItemStack lid = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kaleidoscope_cookery:stockpot_lid")));
                    if (!lid.isEmpty()) pot.onLitClick(level, chef, lid);
                }
            }
            case IStockpot.FINISHED -> {
                if (pot.hasLid()) { takeStockpotLid(level, pot); return; }
                ItemStack savedMainHand = chef.getMainHandItem().copy();
                chef.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
                int guard = 0;
                while (pot.takeOutProduct(level, chef, chef.getMainHandItem()) && guard++ < 64) {
                    chef.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOWL));
                }
                chef.setItemInHand(InteractionHand.MAIN_HAND, savedMainHand);
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                rt.pendingOutput = true;
            }
        }
    }

    private static void takeStockpotLid(ServerLevel level, StockpotBlockEntity pot) {
        pot.setLidItem(ItemStack.EMPTY);
        pot.setChanged();
        level.setBlockAndUpdate(pot.getBlockPos(),
                level.getBlockState(pot.getBlockPos()).setValue(StockpotBlock.HAS_LID, false));
    }

    private static void tryLightStoveBelow(ServerLevel level, BlockPos devicePos, CitizenEntity chef) {
        BlockPos belowPos = devicePos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (!belowState.hasProperty(BlockStateProperties.LIT)) return;
        if (belowState.getValue(BlockStateProperties.LIT)) return;
        if (!(belowState.getBlock() instanceof StoveBlock)) return;
        level.setBlockAndUpdate(belowPos, belowState.setValue(BlockStateProperties.LIT, true));
        level.playSound(null, belowPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        if (chef != null) chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
    }

    private static void tickSteamer(ServerLevel level, BoxRuntime rt, SteamerBlockEntity steamer, CitizenEntity chef) {
        if (!steamer.hasHeatSource(level)) { tryLightStoveBelow(level, steamer.getBlockPos(), chef); return; }

        var items = steamer.getItems();
        var times = steamer.getCookingTime();
        int slots = items.size();

        for (int i = 0; i < slots; i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && times[i] <= 0) {
                items.set(i, ItemStack.EMPTY);
                steamer.setChanged();
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                rt.pendingOutput = true;
                return;
            }
        }

        if (rt.putIngredientIndex == 0 && !rt.resolvedRecipe.ingredients().isEmpty()) {
            Ingredient ing = rt.resolvedRecipe.ingredients().get(0);
            if (!ing.isEmpty()) {
                for (int i = 0; i < slots; i++) {
                    if (items.get(i).isEmpty()) {
                        ItemStack item = extractByIngredient(level, rt, ing);
                        if (!item.isEmpty()) {
                            steamer.placeFood(level, chef, item);
                            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                            rt.putIngredientIndex = 1;
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void tickBakeryOven(ServerLevel level, BoxRuntime rt, OvenBlockEntity oven, CitizenEntity chef) {
        if (rt.resolvedRecipe == null) { finishCooking(level, rt); return; }
        ItemStackHandler handler = oven.getItemHandler();
        if (handler == null) { finishCooking(level, rt); return; }

        if (rt.putIngredientIndex < rt.resolvedRecipe.ingredients().size()) {
            Ingredient ing = rt.resolvedRecipe.ingredients().get(rt.putIngredientIndex);
            if (ing.isEmpty()) { rt.putIngredientIndex++; return; }
            int slot = -1;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) { slot = i; break; }
            }
            if (slot < 0) return;
            ItemStack item = extractByIngredient(level, rt, ing);
            if (item.isEmpty()) return;
            rt.ovenSlot = slot;
            rt.ovenInput = item.copy();
            handler.setStackInSlot(slot, item);
            rt.putIngredientIndex++;
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
            return;
        }

        if (rt.ovenSlot < 0 || rt.ovenSlot >= handler.getSlots()) { finishCooking(level, rt); return; }
        Optional<RecipeHolder<OvenRecipe>> current = oven.getCurrentRecipe(rt.ovenSlot);
        if (current.isEmpty()) { finishCooking(level, rt); return; }
        OvenRecipe recipe = current.get().value();
        int targetTemp = Math.max(recipe.getMinTemperature(), recipe.getPerfectTemperature());
        if (oven.getTemperature() != targetTemp) {
            oven.setTemperature(targetTemp);
        }

        ItemStack inSlot = handler.getStackInSlot(rt.ovenSlot);
        if (inSlot.isEmpty()) return;
        if (inSlot.getItem() != rt.ovenInput.getItem()) {
            List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, inSlot.copy());
            handler.setStackInSlot(rt.ovenSlot, ItemStack.EMPTY);
            rt.ovenSlot = -1;
            rt.ovenInput = ItemStack.EMPTY;
            rt.pendingOutput = true;
            rt.outputDeposited = true;
        }
    }

    private static void tickBreweryBrewstation(ServerLevel level, BoxRuntime rt, BrewstationBlockEntity station, CitizenEntity chef) {
        if (rt.resolvedRecipe == null) { finishCooking(level, rt); return; }
        switch (rt.breweryStage) {
            case 0 -> stageBreweryFill(level, rt, station, chef);
            case 1 -> stageBreweryBrewing(level, rt, station, chef);
            case 2 -> stageBreweryCollect(level, rt, station, chef);
        }
    }

    private static void stageBreweryFill(ServerLevel level, BoxRuntime rt, BrewstationBlockEntity station, CitizenEntity chef) {
        NonNullList<Ingredient> ingredients = rt.resolvedRecipe.ingredients();
        while (rt.breweryPutIndex < ingredients.size()) {
            Ingredient ing = ingredients.get(rt.breweryPutIndex);
            if (ing.isEmpty()) { rt.breweryPutIndex++; continue; }
            ItemStack item = extractByIngredient(level, rt, ing);
            if (item.isEmpty()) return;
            station.addIngredient(item);
            rt.breweryPutIndex++;
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
            return;
        }
        BlockState mainState = level.getBlockState(station.getBlockPos());
        if (mainState.getValue(BlockStateRegistry.LIQUID) != Liquid.FILLED) {
            level.setBlockAndUpdate(station.getBlockPos(), mainState.setValue(BlockStateRegistry.LIQUID, Liquid.FILLED));
        }
        BlockPos ovenPos = BrewHelper.getBlock(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("brewery:brew_oven")), station.getComponents(), level);
        if (ovenPos != null) {
            BlockState ovenState = level.getBlockState(ovenPos);
            if (ovenState.getValue(BlockStateRegistry.HEAT) != Heat.LIT) {
                level.setBlockAndUpdate(ovenPos, ovenState.setValue(BlockStateRegistry.HEAT, Heat.LIT));
            }
        }
        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
        rt.breweryStage = 1;
    }

    private static void stageBreweryBrewing(ServerLevel level, BoxRuntime rt, BrewstationBlockEntity station, CitizenEntity chef) {
        BlockState mainState = level.getBlockState(station.getBlockPos());
        if (mainState.getValue(BlockStateRegistry.LIQUID) == Liquid.BEER) { rt.breweryStage = 2; return; }
        if (level.getGameTime() - rt.breweryEventTick < 10) return;
        rt.breweryEventTick = level.getGameTime();
        for (BrewEvent ev : station.getRunningEvents()) {
            if (ev instanceof KettleEvent || ev instanceof WhistleEvent) {
                if (mainState.getValue(BlockStateRegistry.LIQUID) != Liquid.FILLED) {
                    level.setBlockAndUpdate(station.getBlockPos(), mainState.setValue(BlockStateRegistry.LIQUID, Liquid.FILLED));
                    chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                    return;
                }
            } else if (ev instanceof OvenEvent) {
                BlockPos ovenPos = BrewHelper.getBlock(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("brewery:brew_oven")), station.getComponents(), level);
                if (ovenPos != null) {
                    BlockState ovenState = level.getBlockState(ovenPos);
                    if (ovenState.getValue(BlockStateRegistry.HEAT) != Heat.LIT) {
                        level.setBlockAndUpdate(ovenPos, ovenState.setValue(BlockStateRegistry.HEAT, Heat.LIT));
                        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                        return;
                    }
                }
            } else if (ev instanceof TimerEvent) {
                BlockPos timerPos = BrewHelper.getBlock(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("brewery:brew_timer")), station.getComponents(), level);
                if (timerPos != null) {
                    BlockState timerState = level.getBlockState(timerPos);
                    if (timerState.getValue(BlockStateRegistry.TIME)) {
                        level.setBlockAndUpdate(timerPos, timerState.setValue(BlockStateRegistry.TIME, false));
                        chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                        return;
                    }
                }
            }
        }
    }

    private static void stageBreweryCollect(ServerLevel level, BoxRuntime rt, BrewstationBlockEntity station, CitizenEntity chef) {
        List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
        while (true) {
            ItemStack beer = station.getBeer();
            if (beer == null || beer.isEmpty()) break;
            BreedingInventoryHelper.depositItemStack(level, outputs, beer);
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        rt.breweryStage = 0;
        rt.breweryPutIndex = 0;
        rt.pendingOutput = true;
        rt.outputDeposited = true;
    }

    private static void tickShaker(ServerLevel level, BoxRuntime rt, ShakerBlockEntity shaker, CitizenEntity chef) {
        if (rt.resolvedRecipe == null) { finishCooking(level, rt); return; }
        switch (rt.shakerStage) {
            case 0 -> stageShakerFill(level, rt, shaker, chef);
            case 1 -> stageShakerShake(level, rt, chef);
            case 2 -> stageShakerPour(level, rt, chef);
        }
    }

    private static void stageShakerFill(ServerLevel level, BoxRuntime rt, ShakerBlockEntity shaker, CitizenEntity chef) {
        NonNullList<Ingredient> ingredients = rt.resolvedRecipe.ingredients();
        while (rt.shakerPutIndex < ingredients.size()) {
            Ingredient ing = ingredients.get(rt.shakerPutIndex);
            if (ing.isEmpty()) { rt.shakerPutIndex++; continue; }
            ItemStack item = extractValidShakerIngredient(level, rt, ing);
            if (item.isEmpty()) return;
            if (shaker.addIngredient(item, null)) {
                rt.shakerPutIndex++;
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                return;
            }
            rt.shakerPutIndex++;
            return;
        }
        rt.shakerStage = 1;
        rt.shakeStartTick = level.getGameTime();
    }

    private static ItemStack extractValidShakerIngredient(ServerLevel level, BoxRuntime rt, Ingredient ingredient) {
        if (ingredient.isEmpty()) return ItemStack.EMPTY;
        List<BlockPos> containers = resolvePositions(rt.building, rt.definition, "input", rt.dataPos);
        for (BlockPos pos : containers) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && ingredient.test(s) && BottleBlockItem.isValidForShaker(s)) return s.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static void stageShakerShake(ServerLevel level, BoxRuntime rt, CitizenEntity chef) {
        if (rt.chef == null) {
            return;
        }
        if (rt.shakerStack.isEmpty()) {
            rt.shakerStack = buildShakerStack(rt, level);
            CitizenJobVisualService.setMainHandOverride(rt.chef.uuid(), rt.shakerStack);
            rt.shakeStartTick = level.getGameTime();
            return;
        }
        long elapsed = level.getGameTime() - rt.shakeStartTick;
        if (elapsed % 10 == 0) {
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        if (elapsed >= 90) {
            handShakeRecipe(level, rt.shakerStack, rt.resolvedRecipe.result());
            rt.shakerStage = 2;
        }
    }

    private static ItemStack buildShakerStack(BoxRuntime rt, ServerLevel level) {
        ItemStackHandler target = new ItemStackHandler(3);
        BlockEntity be = level.getBlockEntity(rt.devicePos);
        if (be instanceof ShakerBlockEntity shaker) {
            ItemStackHandler storage = shaker.getStorage();
            for (int i = 0; i < Math.min(storage.getSlots(), target.getSlots()); i++) {
                target.setStackInSlot(i, storage.getStackInSlot(i));
            }
        }
        ItemStack shakerStack = new ItemStack(ModItems.SHAKER.get());
        ShakerItem.setStorage(shakerStack, target);
        return shakerStack;
    }

    private static void handShakeRecipe(ServerLevel level, ItemStack shakerStack, ItemStack fallbackResult) {
        ItemStackHandler handler = ShakerItem.getStorage(shakerStack);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) stacks.add(s);
        }
        SimpleInput container = new SimpleInput(stacks);
        level.getRecipeManager().getRecipeFor(com.github.ysbbbbbb.kaleidoscopetavern.init.ModRecipes.SHAKER_RECIPE, container, level)
                .ifPresentOrElse(
                        recipe -> ShakerItem.setResult(shakerStack, recipe.value().assemble(container, level.registryAccess())),
                        () -> ShakerItem.setResult(shakerStack, fallbackResult.copy()));
    }

    private static void stageShakerPour(ServerLevel level, BoxRuntime rt, CitizenEntity chef) {
        ItemStack result = ShakerItem.getResult(rt.shakerStack);
        if (result.isEmpty()) { finishCooking(level, rt); return; }

        List<BlockPos> outBlocks = resolveOutputBlocks(rt);
        if (!outBlocks.isEmpty()) {
            BlockPos pos = outBlocks.getFirst();
            level.removeBlock(pos, false);
            level.setBlock(pos, ModBlocks.EMPTY_GLASSWARE.get().defaultBlockState(), 3);
            pourCocktail(level, rt.shakerStack, pos);
        } else {
            BlockPos glass = findGlassware(level, rt);
            if (glass != null) {
                pourCocktail(level, rt.shakerStack, glass);
            }
            List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, result.copy());
        }

        if (rt.chef != null) {
            CitizenJobVisualService.clearMainHandOverride(rt.chef.uuid());
        }
        rt.shakerStack = ItemStack.EMPTY;
        rt.shakerStage = 0;
        rt.shakerPutIndex = 0;
        rt.pendingOutput = true;
        rt.outputDeposited = true;
    }

    private static BlockPos findGlassware(ServerLevel level, BoxRuntime rt) {
        if (rt.devicePos == null) return null;
        int r = DEVICE_SEARCH_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = rt.devicePos.offset(x, y, z);
                    if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.EMPTY_GLASSWARE.get())) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static void pourCocktail(ServerLevel level, ItemStack shakerStack, BlockPos pos) {
        BlockState rawState = level.getBlockState(pos);
        ItemStack result = ShakerItem.getResult(shakerStack);
        if (result.getItem() instanceof BlockItem blockItem) {
            BlockState blockState = blockItem.getBlock().defaultBlockState();
            if (blockState.hasProperty(GlasswareBlock.FACING)) {
                blockState = blockState.setValue(GlasswareBlock.FACING, rawState.getValue(GlasswareBlock.FACING));
            }
            level.setBlockAndUpdate(pos, blockState);
        }
        if (level.getBlockEntity(pos) instanceof SignatureCocktailBlockEntity ordinary) {
            ItemStackHandler storage = ShakerItem.getStorage(shakerStack);
            CocktailEffectHelper.CollectedData data = CocktailEffectHelper.collectFromStorage(storage);
            ordinary.setColor(ColorUtils.mixColors(data.colors()));
            ordinary.setEffects(CocktailEffectHelper.mergeEffects(data.effects()));
            ordinary.refresh();
        }
        ShakerItem.removeAll(shakerStack);
    }

    private static void tickKawaiiMachine(ServerLevel level, BoxRuntime rt, ItemStackHandler handler,
                                          int outputSlot, int outputContainerSlot, int[] ingredientSlots, int specialSlot,
                                          boolean needSnowball, boolean needWater, CitizenEntity chef,
                                          FluidTank waterTank) {
        if (rt.resolvedRecipe == null) { finishCooking(level, rt); return; }

        ItemStack out = handler.getStackInSlot(outputSlot);
        if (!out.isEmpty()) {
            List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, out.copy());
            handler.setStackInSlot(outputSlot, ItemStack.EMPTY);
            rt.putIngredientIndex = 0;
            rt.pendingOutput = true;
            rt.outputDeposited = true;
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
            return;
        }

        if (specialSlot >= 0) {
            if (needSnowball) {
                int need = rt.resolvedRecipe.specialCost() - handler.getStackInSlot(specialSlot).getCount();
                if (need > 0) {
                    ItemStack snow = new ItemStack(Items.SNOWBALL, need);
                    handler.insertItem(specialSlot, snow, false);
                    return;
                }
            } else if (needWater && waterTank != null) {
                if (waterTank.getFluidAmount() <= 0) {
                    ItemStack slot0 = handler.getStackInSlot(specialSlot);
                    if (!slot0.isEmpty() && slot0.getItem() != Items.WATER_BUCKET) {
                        handler.setStackInSlot(specialSlot, ItemStack.EMPTY);
                    }
                    if (handler.getStackInSlot(specialSlot).isEmpty()) {
                        handler.setStackInSlot(specialSlot, new ItemStack(Items.WATER_BUCKET));
                    }
                    return;
                }
                if (!handler.getStackInSlot(specialSlot).isEmpty()) {
                    handler.setStackInSlot(specialSlot, ItemStack.EMPTY);
                }
            }
        }

        NonNullList<Ingredient> ingredients = rt.resolvedRecipe.ingredients();
        Ingredient ing = ingredients.isEmpty() ? Ingredient.EMPTY : ingredients.get(0);
        ItemStack[] candidates = ing.getItems();
        for (int s : ingredientSlots) {
            ItemStack inSlot = handler.getStackInSlot(s);
            if (!inSlot.isEmpty() && inSlot.getItem() == Items.BUCKET) {
                handler.setStackInSlot(s, ItemStack.EMPTY);
            }
        }
        while (rt.putIngredientIndex < candidates.length) {
            ItemStack want = candidates[rt.putIngredientIndex];
            if (want == null || want.isEmpty()) { rt.putIngredientIndex++; continue; }
            if (rt.putIngredientIndex >= ingredientSlots.length) { rt.putIngredientIndex++; continue; }
            int slot = ingredientSlots[rt.putIngredientIndex];
            if (!handler.getStackInSlot(slot).isEmpty()) { rt.putIngredientIndex++; continue; }
            ItemStack item = extractExactFromInput(level, rt, new ItemStack(want.getItem(), 1));
            if (item.isEmpty()) {
                return;
            }
            handler.setStackInSlot(slot, item);
            rt.putIngredientIndex++;
            chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
            return;
        }

        if (outputContainerSlot >= 0 && !rt.resolvedRecipe.outputContainerItem().isEmpty()) {
            ItemStack needContainer = rt.resolvedRecipe.outputContainerItem();
            ItemStack inContainer = handler.getStackInSlot(outputContainerSlot);
            if (!inContainer.is(needContainer.getItem())) {
                if (!inContainer.isEmpty()) {
                    handler.setStackInSlot(outputContainerSlot, ItemStack.EMPTY);
                }
                handler.setStackInSlot(outputContainerSlot, needContainer.copy());
                chef.triggerWorkSwing(InteractionHand.MAIN_HAND);
                return;
            }
        }
    }

    private static ItemStack extractExactFromInput(ServerLevel level, BoxRuntime rt, ItemStack wanted) {
        List<BlockPos> containers = resolvePositions(rt.building, rt.definition, "input", rt.dataPos);
        for (BlockPos pos : containers) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            int remaining = wanted.getCount();
            for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && s.is(wanted.getItem())) {
                    ItemStack take = s.split(Math.min(remaining, s.getCount()));
                    remaining -= take.getCount();
                }
            }
            if (remaining == 0) {
                return wanted.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static void finishCooking(ServerLevel level, BoxRuntime rt) {
        if (rt.chefOutputItemId == null) { rt.chefCooking = false; return; }

        if (!rt.outputDeposited) {
            ItemStack result = rt.resolvedRecipe != null ? rt.resolvedRecipe.result().copy() : ItemStack.EMPTY;
            if (!result.isEmpty()) {
                List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
                BreedingInventoryHelper.depositItemStack(level, outputs, result);
            }
        }

        UUID cid = findCustomerForCooked(level, rt);
        if (cid != null) {
            BlockPos seat = findCustomerSeat(level, rt.dataPos, cid);
            clearCookingOrder(level, rt.dataPos, cid);
            addCookedOrder(level, rt.dataPos, cid, seat, rt.chefOutputItemId);
        }

        rt.chefCooking = false;
        rt.pendingOutput = false;
        rt.outputDeposited = false;
        rt.chefOutputItemId = null;
        rt.deviceType = null;
        rt.resolvedRecipe = null;
        rt.devicePos = null;
        rt.putIngredientIndex = 0;
        rt.ovenSlot = -1;
        rt.ovenInput = ItemStack.EMPTY;
        rt.breweryStage = 0;
        rt.breweryPutIndex = 0;
        rt.breweryEventTick = 0;
        rt.directProgress = 0;
    }

    private static void tickWaiter(ServerLevel level, RestaurantBoxData data, BoxRuntime rt, long gameTime) {
        if (rt.waiter == null) return;
        switch (rt.waiterStage) {
            case 0 -> waiterGrab(level, data, rt);
            case 1 -> waiterWalk(level, data, rt);
            case 2 -> waiterServe(level, data, rt);
            default -> rt.waiterStage = 0;
        }
    }

    private static void waiterGrab(ServerLevel level, RestaurantBoxData data, BoxRuntime rt) {
        RestaurantBoxData.OrderEntry order = null;
        for (var o : data.orders()) {
            if (o.status() == OrderStatus.SERVING) continue;
            if (o.seatPos().equals(BlockPos.ZERO)) continue;
            order = o;
            break;
        }
        if (order == null) {
            BlockPos stand = resolveWaiterStand(rt);
            if (stand != null) {
                CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.waiter.uuid());
                if (entity != null && entity.position().distanceToSqr(Vec3.atCenterOf(stand)) > 4.0D) {
                    CitizenNavigationService.requestMove(level, rt.waiter.uuid(), Vec3.atCenterOf(stand), MovementIntent.WORK);
                }
            }
            return;
        }
        BlockPos deskPos = findAdjacentDesk(level, rt, order.seatPos());
        if (deskPos == null) return;
        List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
        ItemStack food = takeMatchingFood(level, outputs, order.recipeId(), rt.definition, rt.building);
        if (food.isEmpty()) return;
        rt.waiterCarry = food;
        rt.waiterDesk = deskPos;
        if (rt.waiter != null) {
            CitizenJobVisualService.setMainHandOverride(rt.waiter.uuid(), food);
        }
        rt.waiterStage = 1;
    }

    private static BlockPos resolveWaiterStand(BoxRuntime rt) {
        if (rt == null || rt.building == null || rt.definition == null) return null;
        List<BlockPos> stand = resolvePositions(rt.building, rt.definition, "waiter_work", rt.dataPos);
        if (stand.isEmpty()) {
            stand = resolvePositions(rt.building, rt.definition, "work", rt.dataPos);
        }
        return stand.isEmpty() ? null : stand.getFirst();
    }

    private static void waiterWalk(ServerLevel level, RestaurantBoxData data, BoxRuntime rt) {
        if (rt.waiterDesk == null) { rt.waiterStage = 0; return; }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.waiter.uuid());
        if (entity == null) { rt.waiterStage = 0; return; }
        if (entity.position().distanceToSqr(Vec3.atCenterOf(rt.waiterDesk)) <= 4.0D) {
            rt.waiterStage = 2;
            return;
        }
        CitizenNavigationService.requestMove(level, rt.waiter.uuid(), Vec3.atCenterOf(rt.waiterDesk), MovementIntent.WORK);
    }

    private static void waiterServe(ServerLevel level, RestaurantBoxData data, BoxRuntime rt) {
        if (rt.waiterCarry.isEmpty() || rt.waiterDesk == null) { rt.waiterStage = 0; return; }
        boolean ok = placeFoodOnDesk(level, rt.waiterDesk, rt.waiterCarry);
        if (ok) {
            for (var o : data.orders()) {
                if (o.status() == OrderStatus.SERVING) continue;
                if (o.seatPos().equals(BlockPos.ZERO)) continue;
                if (rt.waiterDesk.equals(findAdjacentDesk(level, rt, o.seatPos()))) {
                    data.orders().remove(o);
                    data.orders().add(new RestaurantBoxData.OrderEntry(o.customerId(), o.seatPos(), o.recipeId(), OrderStatus.SERVING));
                    break;
                }
            }
            RestaurantBoxManager.get(level).persist(data);
        } else {
            List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, rt.waiterCarry);
        }
        if (rt.waiter != null) {
            CitizenJobVisualService.clearMainHandOverride(rt.waiter.uuid());
        }
        rt.waiterCarry = ItemStack.EMPTY;
        rt.waiterDesk = null;
        rt.waiterStage = 0;
    }

    public static boolean placeFoodOnDesk(ServerLevel level, BlockPos deskPos, ItemStack food) {
        BlockState state = level.getBlockState(deskPos);
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setPos(deskPos.getX(), deskPos.getY(), deskPos.getZ());
        fake.setItemInHand(InteractionHand.MAIN_HAND, food.copy());
        Vec3 hitVec = Vec3.atCenterOf(deskPos);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, deskPos, false);
        var result = state.useItemOn(fake.getItemInHand(InteractionHand.MAIN_HAND), level, fake, InteractionHand.MAIN_HAND, hit);
        fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return result.consumesAction();
    }

    public static BlockPos findAdjacentDesk(ServerLevel level, BoxRuntime rt, BlockPos seatPos) {
        List<BlockPos> desks = resolvePositions(rt.building, rt.definition, "desk", rt.dataPos);
        for (BlockPos desk : desks) {
            if (desk.getY() != seatPos.getY()) continue;
            int dx = Math.abs(desk.getX() - seatPos.getX());
            int dz = Math.abs(desk.getZ() - seatPos.getZ());
            if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
                return desk;
            }
        }
        return null;
    }

    public static ResolvedRecipe findRecipe(ServerLevel level, String outputItemId) {
        ResourceLocation targetId = ResourceLocation.tryParse(outputItemId);
        if (targetId == null) return null;
        RecipeManager rm = level.getRecipeManager();
        var registryAccess = level.registryAccess();

        for (RecipeHolder<PotRecipe> h : rm.getAllRecipesFor(ModRecipes.POT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.POT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<FlexPotRecipe> h : rm.getAllRecipesFor(ModRecipes.FLEX_POT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.POT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<StockpotRecipe> h : rm.getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.STOCKPOT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<FlexStockpotRecipe> h : rm.getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.STOCKPOT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<SteamerRecipe> h : rm.getAllRecipesFor(ModRecipes.STEAMER_RECIPE)) {
            if (matchesOutput(h.value().getResult(), targetId)) {
                NonNullList<Ingredient> steamerIngs = NonNullList.create();
                steamerIngs.add(h.value().getIngredient());
                return new ResolvedRecipe(DeviceType.STEAMER, steamerIngs, h.value().getResult(), 0);
            }
        }
        for (RecipeHolder<OvenRecipe> h : rm.getAllRecipesFor(OvenRecipe.Type.INSTANCE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.BAKERY_OVEN, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<ShakerRecipe> h : rm.getAllRecipesFor(com.github.ysbbbbbb.kaleidoscopetavern.init.ModRecipes.SHAKER_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.TAVERN_SHAKER, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<BlenderRecipe> h : rm.getAllRecipesFor(BlenderRecipe.Type.INSTANCE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.KAWAII_BLENDER, toNonNullList(h.value().getRecipeItems()), h.value().getResultItem(registryAccess), 0,
                        h.value().getItemOnOutput());
        }
        for (RecipeHolder<CoffeeMachineRecipe> h : rm.getAllRecipesFor(CoffeeMachineRecipe.Type.INSTANCE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.KAWAII_COFFEE_MACHINE, toNonNullList(h.value().getRecipeItems()), h.value().getResultItem(registryAccess), h.value().getWaterNeeded(),
                        h.value().getItemOnOutput());
        }
        for (RecipeHolder<IceCreamMakerRecipe> h : rm.getAllRecipesFor(IceCreamMakerRecipe.Type.INSTANCE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.KAWAII_ICE_CREAM_MAKER, toNonNullList(h.value().getRecipeItems()), h.value().getResultItem(registryAccess), h.value().getSnowballs(),
                        h.value().getItemOnOutput());
        }
        @SuppressWarnings("unchecked")
        RecipeType<BrewingRecipe> brewingType = (RecipeType<BrewingRecipe>) BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.parse("brewery:brewing"));
        if (brewingType != null) {
            for (RecipeHolder<BrewingRecipe> h : rm.getAllRecipesFor(brewingType)) {
                if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                    return new ResolvedRecipe(DeviceType.BREWERY_BREWSTATION, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
            }
        }
        ResolvedRecipe direct = findBakeryDirectRecipe(level, targetId);
        if (direct != null) return direct;
        return null;
    }

    private static ResolvedRecipe findBakeryDirectRecipe(ServerLevel level, ResourceLocation targetId) {
        if (!targetId.getNamespace().equals("bakeries")) {
            return null;
        }
        var registryAccess = level.registryAccess();
        RecipeManager rm = level.getRecipeManager();
        for (RecipeHolder<CraftingRecipe> h : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.DIRECT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess), 0);
        }
        for (RecipeHolder<CampfireCookingRecipe> h : rm.getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId)) {
                NonNullList<Ingredient> ings = NonNullList.create();
                if (!h.value().getIngredients().isEmpty()) ings.add(h.value().getIngredients().getFirst());
                return new ResolvedRecipe(DeviceType.DIRECT, ings, h.value().getResultItem(registryAccess), 0);
            }
        }
        if (com.renyigesai.bakeries.common.init.BakeriesRecipeTypes.BREAD_KNIFE_TYPE.get() != null) {
            for (RecipeHolder<BreadKnifeRecipe> h : rm.getAllRecipesFor(com.renyigesai.bakeries.common.init.BakeriesRecipeTypes.BREAD_KNIFE_TYPE.get())) {
                NonNullList<Ingredient> ings = NonNullList.create();
                if (!h.value().getIngredients().isEmpty()) ings.add(h.value().getIngredients().getFirst());
                for (ItemStack out : h.value().getAllResults()) {
                    if (matchesOutput(out, targetId))
                        return new ResolvedRecipe(DeviceType.DIRECT, ings, out, 0);
                }
            }
        }
        return switch (targetId.getPath()) {
            case "toast" -> directOf("bakeries:mould_toast_dough", targetId);
            case "cheese_cocoa_toast" -> directOf("bakeries:mould_cheese_cocoa_toast_dough", targetId);
            default -> null;
        };
    }

    private static ResolvedRecipe directOf(String ingredientItem, ResourceLocation targetId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredientItem));
        Item resultItem = BuiltInRegistries.ITEM.get(targetId);
        if (item == null || item == Items.AIR || resultItem == null || resultItem == Items.AIR) {
            return null;
        }
        NonNullList<Ingredient> ings = NonNullList.create();
        ings.add(Ingredient.of(item));
        return new ResolvedRecipe(DeviceType.DIRECT, ings, new ItemStack(resultItem), 0);
    }

    private static NonNullList<Ingredient> toNonNullList(List<Ingredient> list) {
        NonNullList<Ingredient> result = NonNullList.create();
        result.addAll(list);
        return result;
    }

    private static boolean matchesOutput(ItemStack result, ResourceLocation targetId) {
        if (result.isEmpty()) return false;
        return BuiltInRegistries.ITEM.getKey(result.getItem()).equals(targetId);
    }

    private static ItemStack extractByIngredient(ServerLevel level, BoxRuntime rt, Ingredient ingredient) {
        if (ingredient.isEmpty()) return ItemStack.EMPTY;
        List<BlockPos> containers = resolvePositions(rt.building, rt.definition, "input", rt.dataPos);
        for (BlockPos pos : containers) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && ingredient.test(s)) return s.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasInputs(ServerLevel level, List<BlockPos> containers, NonNullList<Ingredient> ingredients) {
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            boolean found = false;
            for (BlockPos pos : containers) {
                var c = BreedingInventoryHelper.containerAt(level, pos);
                if (c == null) continue;
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (ing.test(c.getItem(i))) { found = true; break; }
                }
                if (found) break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static BlockPos findDevice(ServerLevel level, BlockPos workPos, DeviceType deviceType) {
        if (workPos == null) return null;
        int r = DEVICE_SEARCH_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = workPos.offset(x, y, z);
                    if (!level.isLoaded(pos)) continue;
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be == null) continue;
                    switch (deviceType) {
                        case POT -> { if (be instanceof PotBlockEntity) return pos; }
                        case STOCKPOT -> { if (be instanceof StockpotBlockEntity) return pos; }
                        case STEAMER -> { if (be instanceof SteamerBlockEntity) return pos; }
                        case BAKERY_OVEN -> { if (be instanceof OvenBlockEntity) return pos; }
                        case TAVERN_SHAKER -> { if (be instanceof ShakerBlockEntity) return pos; }
                        case KAWAII_BLENDER -> { if (be instanceof BlenderBlockEntity) return pos; }
                        case KAWAII_COFFEE_MACHINE -> { if (be instanceof CoffeeMachineBlockEntity) return pos; }
                        case KAWAII_ICE_CREAM_MAKER -> { if (be instanceof IceCreamMakerBlockEntity) return pos; }
                        case BREWERY_BREWSTATION -> { if (be instanceof BrewstationBlockEntity) return pos; }
                    }
                }
            }
        }
        return null;
    }

    public static ItemStack takeMatchingFood(ServerLevel level, List<BlockPos> outputs, String outputItemId,
                                               RestaurantDefinition def, PlacedBuildingRecord building) {
        if (outputItemId == null || outputItemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation targetId = ResourceLocation.tryParse(outputItemId);
        if (targetId == null) return ItemStack.EMPTY;
        if (def != null && !def.outputBlock().isEmpty() && building != null) {
            int rot = rot(building.facing());
            for (BlockPos o : def.outputBlock()) {
                if (o == null) continue;
                BlockPos pos = building.worldOrigin().offset(BuildingTransform.rotatePosition(o, rot));
                if (!level.isLoaded(pos)) continue;
                BlockState st = level.getBlockState(pos);
                if (st.isAir()) continue;
                Item item = st.getBlock().asItem();
                if (item == Items.AIR) continue;
                if (BuiltInRegistries.ITEM.getKey(item).equals(targetId)) {
                    level.removeBlock(pos, false);
                    return new ItemStack(item);
                }
            }
        }
        for (BlockPos pos : outputs) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(targetId)) {
                    ItemStack taken = s.split(1);
                    c.setChanged();
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<BlockPos> resolveOutputBlocks(BoxRuntime rt) {
        if (rt.definition == null || rt.definition.outputBlock().isEmpty() || rt.building == null) {
            return List.of();
        }
        List<BlockPos> r = new ArrayList<>();
        int rot = rot(rt.building.facing());
        for (BlockPos o : rt.definition.outputBlock()) {
            if (o == null) continue;
            r.add(rt.building.worldOrigin().offset(BuildingTransform.rotatePosition(o, rot)).immutable());
        }
        return r;
    }

    private static ItemStack takeFood(ServerLevel level, List<BlockPos> outputs) {
        for (BlockPos pos : outputs) {
            var c = BreedingInventoryHelper.containerAt(level, pos);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && s.getFoodProperties(null) != null) {
                    ItemStack t = s.split(1);
                    c.setChanged();
                    return t;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static CitizenEntity findChefEntity(ServerLevel level, BoxRuntime rt) {
        if (rt.chef == null) return null;
        return CitizenTeleportService.findCitizenEntity(level, rt.chef.uuid());
    }

    private static UUID findCustomerForCooked(ServerLevel level, BoxRuntime rt) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).get(rt.dataPos);
        if (data == null) return null;
        for (var o : data.orders()) { if (o.status() == OrderStatus.COOKING) return o.customerId(); }
        return null;
    }

    private static BlockPos findCustomerSeat(ServerLevel level, BlockPos boxPos, UUID cid) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).get(boxPos);
        if (data == null) return BlockPos.ZERO;
        for (var o : data.orders()) { if (o.customerId().equals(cid)) return o.seatPos(); }
        return BlockPos.ZERO;
    }

    private static void clearCookingOrder(ServerLevel level, BlockPos boxPos, UUID cid) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).get(boxPos);
        if (data != null) data.orders().removeIf(o -> o.customerId().equals(cid) && o.status() == OrderStatus.COOKING);
    }

    private static void addCookedOrder(ServerLevel level, BlockPos boxPos, UUID cid, BlockPos seat, String recipeId) {
        RestaurantBoxData data = RestaurantBoxManager.get(level).get(boxPos);
        if (data != null) {
            for (var o : data.orders()) {
                if (o.customerId().equals(cid) && o.status() == OrderStatus.SERVING) return;
            }
            data.orders().add(new RestaurantBoxData.OrderEntry(cid, seat, recipeId, OrderStatus.COOKED));
            RestaurantBoxManager.get(level).persist(data);
        }
    }

    private static void setWorkerHeldItems(BoxRuntime rt) {
        if (rt.definition == null) return;
        String chefItem = rt.definition.job().heldItem();
        if (!chefItem.isBlank() && rt.chef != null) {
            ResourceLocation id = ResourceLocation.tryParse(chefItem);
            if (id != null) {
                var item = BuiltInRegistries.ITEM.get(id);
                if (item != null && item != Items.AIR) CitizenJobVisualService.setMainHandOverride(rt.chef.uuid(), new ItemStack(item));
            }
        }
    }

    private static void fillHunger(ServerLevel level, CitizenData chef, CitizenData waiter) {
        if (chef != null) {
            CitizenEntity e = CitizenTeleportService.findCitizenEntity(level, chef.uuid());
            if (e != null) e.setHunger(CitizenEntity.DEFAULT_HUNGER);
        }
        if (waiter != null) {
            CitizenEntity e = CitizenTeleportService.findCitizenEntity(level, waiter.uuid());
            if (e != null) e.setHunger(CitizenEntity.DEFAULT_HUNGER);
        }
    }

    private static void move(ServerLevel level, CitizenData worker, List<BlockPos> positions) {
        if (worker == null || positions.isEmpty()) return;
        CitizenEntity e = CitizenTeleportService.findCitizenEntity(level, worker.uuid());
        if (e == null) return;
        Vec3 o = e.position();
        BlockPos n = positions.getFirst();
        double nd = Double.MAX_VALUE;
        for (BlockPos p : positions) {
            double d = Vec3.atBottomCenterOf(p).distanceToSqr(o);
            if (d < nd) { nd = d; n = p; }
        }
        if (o.distanceToSqr(Vec3.atBottomCenterOf(n)) >= 1.0D) {
            CitizenNavigationService.requestMove(level, worker.uuid(), Vec3.atBottomCenterOf(n), MovementIntent.WORK);
        }
    }

    public static List<BlockPos> resolvePositions(PlacedBuildingRecord building, RestaurantDefinition def, String id, BlockPos boxPos) {
        if (def == null || building == null) return List.of();
        var p = def.points().get(id);
        if (p == null) {
            var c = def.containers().get(id);
            if (c == null) return List.of();
            return resolve(c.type(), c.positions(), building, boxPos);
        }
        return resolve(p.type(), p.positions(), building, boxPos);
    }

    private static List<BlockPos> resolve(String type, List<BlockPos> positions, PlacedBuildingRecord building, BlockPos boxPos) {
        if ("control_box_relative".equalsIgnoreCase(type)) {
            int rot = rot(building.facing());
            List<BlockPos> r = new ArrayList<>();
            for (BlockPos o : positions) {
                if (o == null) continue;
                r.add(boxPos.offset(BuildingTransform.rotatePosition(o, rot)).immutable());
            }
            return List.copyOf(r);
        }
        if ("structure_pos".equalsIgnoreCase(type) && building != null) {
            int rot = rot(building.facing());
            List<BlockPos> r = new ArrayList<>();
            for (BlockPos o : positions) {
                if (o == null) continue;
                r.add(building.worldOrigin().offset(BuildingTransform.rotatePosition(o, rot)).immutable());
            }
            return List.copyOf(r);
        }
        return IndustrialCoordinateResolver.resolvePositions(building, positions);
    }

    private static int rot(String facing) {
        String n = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private static void setStatus(RestaurantBoxManager m, RestaurantBoxData d, String k, String t) {
        d.setRunning(false);
        d.setProgressTicks(0);
        d.setStatusKey(k);
        d.setStatusText(t);
        m.persist(d);
    }

    private static void setTransientStatus(RestaurantBoxManager m, RestaurantBoxData d, String k, String t) {
        d.setStatusKey(k);
        d.setStatusText(t);
        m.persist(d);
    }

    public record ResolvedRecipe(DeviceType device, NonNullList<Ingredient> ingredients, ItemStack result,
                                 int specialCost, ItemStack outputContainerItem) {
        public ResolvedRecipe(DeviceType device, NonNullList<Ingredient> ingredients, ItemStack result, int specialCost) {
            this(device, ingredients, result, specialCost, ItemStack.EMPTY);
        }
    }

    private static final class BoxRuntime {
        PlacedBuildingRecord building;
        RestaurantDefinition definition;
        CitizenData chef, waiter;
        BlockPos dataPos;
        long lastValidate, lastCookTick, lastWaiterTick, lastOrderRecoverTick;
        boolean nightClosed;

        boolean chefCooking;
        String chefOutputItemId;
        DeviceType deviceType;
        ResolvedRecipe resolvedRecipe;
        BlockPos devicePos;
        boolean pendingOutput;
        int putIngredientIndex;
        int ovenSlot = -1;
        ItemStack ovenInput = ItemStack.EMPTY;
        boolean outputDeposited;
        int shakerStage;
        long shakeStartTick;
        ItemStack shakerStack = ItemStack.EMPTY;
        int shakerPutIndex;
        int breweryStage;
        int breweryPutIndex;
        long breweryEventTick;
        int directProgress;
        int waiterStage;
        BlockPos waiterDesk;
        ItemStack waiterCarry = ItemStack.EMPTY;

        void reset() {
            building = null; definition = null; chef = null; waiter = null; dataPos = null;
            lastValidate = 0; lastCookTick = 0; lastWaiterTick = 0; lastOrderRecoverTick = 0;
            nightClosed = false;
            chefCooking = false; chefOutputItemId = null; deviceType = null; resolvedRecipe = null; devicePos = null;
            pendingOutput = false; putIngredientIndex = 0;
            ovenSlot = -1; ovenInput = ItemStack.EMPTY; outputDeposited = false;
            shakerStage = 0; shakeStartTick = 0; shakerStack = ItemStack.EMPTY; shakerPutIndex = 0;
            breweryStage = 0; breweryPutIndex = 0; breweryEventTick = 0; directProgress = 0;
            waiterStage = 0; waiterDesk = null; waiterCarry = ItemStack.EMPTY;
        }
    }
}
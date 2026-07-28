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
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final long WAITER_INTERVAL = 100L;
    private static final long ORDER_RECOVER_INTERVAL = 2000L;
    private static final int DEVICE_SEARCH_RADIUS = 5;

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
            return;
        }

        List<BlockPos> workPositions = resolvePositions(rt.building, rt.definition, "work", rt.dataPos);
        if (workPositions.isEmpty()) return;
        BlockPos workPos = workPositions.getFirst();
        BlockPos devicePos = findDevice(level, workPos, resolved.device());
        if (devicePos == null) {
            setTransientStatus(manager, data, RestaurantConstants.STATUS_NO_DEVICE, outputItemId);
            return;
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

    /** 设备烹饪主循环。 */
    private static void tickDeviceCooking(ServerLevel level, BoxRuntime rt) {
        if (rt.pendingOutput) { finishCooking(level, rt); return; }
        if (rt.devicePos == null) { finishCooking(level, rt); return; }
        if (!level.isLoaded(rt.devicePos)) return;
        BlockEntity be = level.getBlockEntity(rt.devicePos);
        if (be == null) { finishCooking(level, rt); return; }

        CitizenEntity chef = findChefEntity(level, rt);
        if (chef == null) return;

        switch (rt.deviceType) {
            case POT -> tickPot(level, rt, (PotBlockEntity) be, chef);
            case STOCKPOT -> tickStockpot(level, rt, (StockpotBlockEntity) be, chef);
            case STEAMER -> tickSteamer(level, rt, (SteamerBlockEntity) be, chef);
        }
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

    private static void finishCooking(ServerLevel level, BoxRuntime rt) {
        if (rt.chefOutputItemId == null) { rt.chefCooking = false; return; }

        ItemStack result = rt.resolvedRecipe != null ? rt.resolvedRecipe.result().copy() : ItemStack.EMPTY;
        if (!result.isEmpty()) {
            List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
            BreedingInventoryHelper.depositItemStack(level, outputs, result);
        }

        UUID cid = findCustomerForCooked(level, rt);
        if (cid != null) {
            BlockPos seat = findCustomerSeat(level, rt.dataPos, cid);
            clearCookingOrder(level, rt.dataPos, cid);
            addCookedOrder(level, rt.dataPos, cid, seat, rt.chefOutputItemId);
        }

        rt.chefCooking = false;
        rt.pendingOutput = false;
        rt.chefOutputItemId = null;
        rt.deviceType = null;
        rt.resolvedRecipe = null;
        rt.devicePos = null;
        rt.putIngredientIndex = 0;
    }

    /** 调试输出到所有在线玩家聊天框。 */
    private static void debugChat(ServerLevel level, String msg) {
        if (level.getServer() != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
        }
    }

    /** 批量上菜：每 100 tick 检查 output，有对应菜品就上菜。不依赖厨师做完菜，PENDING 订单也可上菜。 */
    private static void tickWaiter(ServerLevel level, RestaurantBoxData data, BoxRuntime rt, long gameTime) {
        List<BlockPos> outputs = resolvePositions(rt.building, rt.definition, "output", rt.dataPos);
        List<RestaurantBoxData.OrderEntry> served = new ArrayList<>();

        for (var o : data.orders()) {
            if (o.status() == OrderStatus.SERVING) continue;

            BlockPos deskPos = findAdjacentDesk(level, rt, o.seatPos());
            if (deskPos == null) {
                debugChat(level, "[餐厅] 座位无相邻桌子，无法上菜 boxPos=" + rt.dataPos + " 座位=" + o.seatPos() + " 菜品=" + o.recipeId());
                continue;
            }

            ItemStack food = takeMatchingFood(level, outputs, o.recipeId());
            if (food.isEmpty()) continue;

            if (!placeFoodOnDesk(level, deskPos, food)) {
                BreedingInventoryHelper.depositItemStack(level, outputs, food);
                continue;
            }
            served.add(o);
        }

        if (!served.isEmpty()) {
            for (var o : served) {
                data.orders().remove(o);
                data.orders().add(new RestaurantBoxData.OrderEntry(o.customerId(), o.seatPos(), o.recipeId(), OrderStatus.SERVING));
            }
            RestaurantBoxManager.get(level).persist(data);
        }
    }

    /** 用 FakePlayer 模拟右键桌子放置菜品（kaleidoscope 桌子必须用假人操作）。 */
    private static boolean placeFoodOnDesk(ServerLevel level, BlockPos deskPos, ItemStack food) {
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

    /** 找到与座位水平相邻（四面）的桌子位置。 */
    private static BlockPos findAdjacentDesk(ServerLevel level, BoxRuntime rt, BlockPos seatPos) {
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
                return new ResolvedRecipe(DeviceType.POT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess));
        }
        for (RecipeHolder<FlexPotRecipe> h : rm.getAllRecipesFor(ModRecipes.FLEX_POT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.POT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess));
        }
        for (RecipeHolder<StockpotRecipe> h : rm.getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.STOCKPOT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess));
        }
        for (RecipeHolder<FlexStockpotRecipe> h : rm.getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE)) {
            if (matchesOutput(h.value().getResultItem(registryAccess), targetId))
                return new ResolvedRecipe(DeviceType.STOCKPOT, toNonNullList(h.value().getIngredients()), h.value().getResultItem(registryAccess));
        }
        for (RecipeHolder<SteamerRecipe> h : rm.getAllRecipesFor(ModRecipes.STEAMER_RECIPE)) {
            if (matchesOutput(h.value().getResult(), targetId)) {
                NonNullList<Ingredient> steamerIngs = NonNullList.create();
                steamerIngs.add(h.value().getIngredient());
                return new ResolvedRecipe(DeviceType.STEAMER, steamerIngs, h.value().getResult());
            }
        }
        return null;
    }

    /** 将 List<Ingredient> 转为 NonNullList<Ingredient>。 */
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

    /** 检查 input 容器是否有配方所需的所有原料（不消耗）。 */
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
                    }
                }
            }
        }
        return null;
    }

    /** 从 output 容器中按物品 id 精确匹配取出菜品。 */
    private static ItemStack takeMatchingFood(ServerLevel level, List<BlockPos> outputs, String outputItemId) {
        if (outputItemId == null || outputItemId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation targetId = ResourceLocation.tryParse(outputItemId);
        if (targetId == null) return ItemStack.EMPTY;
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

    public record ResolvedRecipe(DeviceType device, NonNullList<Ingredient> ingredients, ItemStack result) {}

    private static final class BoxRuntime {
        PlacedBuildingRecord building;
        RestaurantDefinition definition;
        CitizenData chef, waiter;
        BlockPos dataPos;
        long lastValidate, lastCookTick, lastWaiterTick, lastOrderRecoverTick;

        boolean chefCooking;
        String chefOutputItemId;
        DeviceType deviceType;
        ResolvedRecipe resolvedRecipe;
        BlockPos devicePos;
        boolean pendingOutput;
        int putIngredientIndex;

        void reset() {
            building = null; definition = null; chef = null; waiter = null; dataPos = null;
            lastValidate = 0; lastCookTick = 0; lastWaiterTick = 0; lastOrderRecoverTick = 0;
            chefCooking = false; chefOutputItemId = null; deviceType = null; resolvedRecipe = null; devicePos = null;
            pendingOutput = false; putIngredientIndex = 0;
        }
    }
}

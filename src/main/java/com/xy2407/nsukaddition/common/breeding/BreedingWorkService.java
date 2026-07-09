package com.xy2407.nsukaddition.common.breeding;

import com.xy2407.nsukaddition.common.compat.LetFishLoveCompat;
import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenSelfFeedingService;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 繁殖工作服务，每 tick 驱动繁殖箱运行，处理喂食、繁殖、捕杀与采集。 */
@SuppressWarnings("null")
public final class BreedingWorkService {
    private static final long VALIDATE_INTERVAL = 20L;
    private static final long ENTITY_SCAN_INTERVAL = 40L;

    private static final long FEED_INTERVAL = 600L;

    private static final ConcurrentMap<BlockPos, BoxRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private BreedingWorkService() {}

    public static void tick(ServerLevel level) {
        if (level == null) return;
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        long gameTime = level.getGameTime();
        for (BreedingBoxData data : manager.all()) {
            BoxRuntime rt = RUNTIMES.computeIfAbsent(data.boxPos().immutable(), k -> new BoxRuntime());
            if (!data.running()) {
                rt.reset();
                continue;
            }
            if (gameTime < rt.nextTick) {
                continue;
            }
            tickBox(level, manager, data, rt, gameTime);
            BreedingControlBoxViewSyncService.syncStatusIfChanged(level, data);
        }
    }

    private static void tickBox(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data, BoxRuntime rt, long gameTime) {
        BlockPos boxPos = data.boxPos();

        if (gameTime - rt.lastValidate >= VALIDATE_INTERVAL) {
            rt.lastValidate = gameTime;
            PlacedBuildingRecord building = BreedingControlBoxService.resolveBuilding(level, boxPos);
            BreedingDefinitionLoader.LoadResult loadResult = BreedingDefinitionLoader.loadForBuilding(building);
            BreedingDefinition definition = loadResult.definition();
            BreedingControlBoxService.synchronizeBoxMetadata(level, data, building, definition);
            CitizenData worker = BreedingControlBoxService.findAssignedWorker(level, boxPos);

            if (worker == null) {
                setStatus(manager, data, BreedingConstants.STATUS_NO_WORKER, "");
                return;
            }
            if (building == null) {
                setStatus(manager, data, BreedingConstants.STATUS_NO_BUILDING, "");
                return;
            }
            if (!loadResult.valid()) {
                setStatus(manager, data, BreedingConstants.STATUS_INVALID_DEFINITION, String.join(",", loadResult.errors()));
                return;
            }
            BreedingDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
            if (recipe == null) {
                setStatus(manager, data, BreedingConstants.STATUS_NO_RECIPE, "");
                return;
            }

            rt.building = building;
            rt.definition = definition;
            rt.recipe = recipe;
            rt.worker = worker;
        }

        if (rt.recipe == null || rt.worker == null || rt.building == null) return;

        if (CitizenHomeRestService.isRestTime(level)) {
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.worker.uuid());
            if (entity != null) entity.setHasActiveVisualTask(false);
            setTransientStatus(manager, data, BreedingConstants.STATUS_RESTING, "");
            return;
        }
        if (CitizenSelfFeedingService.isSelfFeeding(level, rt.worker.uuid())) {
            CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, rt.worker.uuid());
            if (entity != null) entity.setHasActiveVisualTask(false);
            setTransientStatus(manager, data, BreedingConstants.STATUS_FEEDING, "");
            return;
        }

        if (rt.dropBounds != null && rt.dropOutputs != null && gameTime >= rt.collectDropsAt) {
            collectBuildingDrops(level, rt);
        }

        if (gameTime - rt.lastFeedTick >= FEED_INTERVAL) {
            rt.lastFeedTick = gameTime;
            executeFeedCycle(level, manager, data, rt);
            triggerWorkSwing(level, rt.worker);
            CitizenJobVisualService.clearMainHandOverride(rt.worker.uuid());
        }

        if (gameTime - rt.lastEntityScan >= ENTITY_SCAN_INTERVAL) {
            rt.lastEntityScan = gameTime;
            List<BlockPos> workPositions = resolvePointPositions(rt.building, rt.definition, "work", data.boxPos());
            if (!workPositions.isEmpty()) {
                moveToNearestPoint(level, rt.worker.uuid(), workPositions);
            }
        }
        setWorkerHeldItem(rt.worker, rt.recipe, rt.definition);
        rt.nextTick = gameTime + 1;
    }

    private static void triggerWorkSwing(ServerLevel level, CitizenData worker) {
        if (worker == null) return;
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, worker.uuid());
        if (entity != null) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
    }

    private static void executeFeedCycle(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data, BoxRuntime rt) {
        BlockPos boxPos = data.boxPos();

        if (rt.recipe.entityType().isBlank()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(rt.recipe.targetEntityType());
        if (id == null) return;
        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty()) return;
        EntityType<?> targetType = typeOpt.get();
        AABB bounds = buildingBounds(rt.building);
        List<BlockPos> foodContainers = resolveContainerPositions(rt.building, rt.definition, "input", boxPos);
        List<BlockPos> outputPositions = resolveContainerPositions(rt.building, rt.definition, "output", boxPos);
        int maxEntities = rt.recipe.maxEntities() > 0 ? rt.recipe.maxEntities() : Integer.MAX_VALUE;
        BreedingDefinition.RecipeType recipeType = rt.recipe.type();

        if (isOutputFull(level, outputPositions)) {
            setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.output_full", "");
            return;
        }

        List<Entity> existingEntities = level.getEntitiesOfClass(Entity.class, bounds,
                entity -> entity.getType() == targetType && entity.isAlive());
        boolean hasWaterAnimalInstance = existingEntities.stream().anyMatch(WaterAnimal.class::isInstance);
        boolean hasSchoolingFishInstance = existingEntities.stream().anyMatch(AbstractSchoolingFish.class::isInstance);
        boolean hasAnimalInstance = existingEntities.stream().anyMatch(Animal.class::isInstance);

        if (hasWaterAnimalInstance || hasSchoolingFishInstance || WaterAnimal.class.isAssignableFrom(targetType.getBaseClass())
                || AbstractSchoolingFish.class.isAssignableFrom(targetType.getBaseClass())) {
            handleFishCycle(level, manager, data, rt, boxPos, bounds, foodContainers, outputPositions, maxEntities, recipeType);
            return;
        }

        if ("tide".equals(id.getNamespace()) && !hasAnimalInstance) {
            handleLavaFishCycle(level, manager, data, rt, boxPos, bounds, foodContainers, outputPositions, maxEntities, targetType, recipeType);
            return;
        }

        if (hasAnimalInstance || Animal.class.isAssignableFrom(targetType.getBaseClass())) {
            handleAnimalCycle(level, manager, data, rt, boxPos, bounds, foodContainers, outputPositions, maxEntities, recipeType);
            return;
        }

        handleGenericCycle(level, manager, data, rt, boxPos, bounds, foodContainers, outputPositions, maxEntities, targetType, recipeType);
    }

    private static void handleFishCycle(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data, BoxRuntime rt,
                                        BlockPos boxPos, AABB bounds, List<BlockPos> food, List<BlockPos> out,
                                        int max, BreedingDefinition.RecipeType recipeType) {
        ResourceLocation typeId = ResourceLocation.tryParse(rt.recipe.targetEntityType());
        if (typeId == null) return;
        EntityType<?> targetType = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (targetType == null) return;
        List<WaterAnimal> all = level.getEntitiesOfClass(WaterAnimal.class, bounds,
                fish -> fish.getType() == targetType && !fish.isBaby());
        int adults = all.size();

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER && max < Integer.MAX_VALUE && adults > max) {
            int toKill = adults - max;
            int killed = 0;
            long gameTime = level.getGameTime();
            for (WaterAnimal target : all) {
                if (killed >= toKill) break;
                killWithDrops(level, target);
                killed++;
            }
            adults -= killed;
            rt.collectDropsAt = gameTime + 20;
            rt.dropOutputs = out;
            rt.dropBounds = bounds;
        }

        if (max < Integer.MAX_VALUE) {
            boolean stopBreed = recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER
                    ? adults > max : adults >= max;
            if (stopBreed) {
                manager.persist(data);
                return;
            }
        }

        if (adults != all.size()) {
            all = level.getEntitiesOfClass(WaterAnimal.class, bounds,
                    fish -> fish.getType() == targetType && !fish.isBaby());
            adults = all.size();
        }

        if (all.size() < 2) {
            return;
        }
        if (!LetFishLoveCompat.isLoaded()) {
            return;
        }
        WaterAnimal a = all.get(0);
        WaterAnimal b = null;
        for (int j = 1; j < all.size(); j++) {
            WaterAnimal c = all.get(j);
            if (c.isAlive() && c.getClass() == a.getClass() && c != a) {
                b = c;
                break;
            }
        }
        if (b != null && LetFishLoveCompat.canFallInLove(a) && LetFishLoveCompat.canFallInLove(b)) {

            if (!hasFishFood(level, food, rt.recipe, 2)) {
                setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.no_input", "");
                manager.persist(data);
                return;
            }
            if (LetFishLoveCompat.triggerPairInLove(level, a, b)) {
                tryConsumeFishFood(level, food, rt.recipe, 2);
            }
        }

        manager.persist(data);
    }

    private static void handleLavaFishCycle(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data, BoxRuntime rt,
                                            BlockPos boxPos, AABB bounds, List<BlockPos> food, List<BlockPos> out,
                                            int max, EntityType<?> targetType, BreedingDefinition.RecipeType recipeType) {
        List<net.minecraft.world.entity.Mob> all = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bounds,
                e -> e.getType() == targetType && e.isAlive() && !e.isBaby());
        int adults = all.size();

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER && max < Integer.MAX_VALUE && adults > max) {
            int toKill = adults - max;
            int killed = 0;
            long gameTime = level.getGameTime();
            for (net.minecraft.world.entity.Mob target : all) {
                if (killed >= toKill) break;
                killWithDrops(level, target);
                killed++;
            }
            adults -= killed;
            rt.collectDropsAt = gameTime + 20;
            rt.dropOutputs = out;
            rt.dropBounds = bounds;
        }

        if (adults != all.size()) {
            all = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bounds,
                    e -> e.getType() == targetType && e.isAlive() && !e.isBaby());
            adults = all.size();
        }

        if (max < Integer.MAX_VALUE) {
            boolean stopSpawn = recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER
                    ? adults > max : adults >= max;
            if (stopSpawn) { manager.persist(data); return; }
        }

        if (!LetFishLoveCompat.isLoaded()) {
            return;
        }
        if (all.size() < 2) {
            return;
        }
        net.minecraft.world.entity.Mob a = all.get(0);
        net.minecraft.world.entity.Mob b = null;
        for (int j = 1; j < all.size(); j++) {
            net.minecraft.world.entity.Mob c = all.get(j);
            if (c.isAlive() && c != a) {
                b = c;
                break;
            }
        }
        if (b != null && LetFishLoveCompat.canFallInLove(a) && LetFishLoveCompat.canFallInLove(b)) {

            if (!hasFishFood(level, food, rt.recipe, 2)) {
                setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.no_input", "");
                manager.persist(data);
                return;
            }
            if (LetFishLoveCompat.triggerPairInLove(level, a, b)) {
                tryConsumeFishFood(level, food, rt.recipe, 2);
            }
        }
        manager.persist(data);
    }

    private static void handleGenericCycle(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data,
                                            BoxRuntime rt, BlockPos boxPos, AABB bounds, List<BlockPos> food,
                                            List<BlockPos> out, int max, EntityType<?> targetType,
                                            BreedingDefinition.RecipeType recipeType) {
        List<Entity> all = level.getEntitiesOfClass(Entity.class, bounds,
                e -> e.getType() == targetType && e.isAlive() && !(e instanceof Mob m && m.isBaby()));
        int adults = all.size();

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER && max < Integer.MAX_VALUE && adults > max) {
            int toKill = adults - max;
            int killed = 0;
            long gameTime = level.getGameTime();
            for (Entity target : all) {
                if (killed >= toKill) break;
                if (target instanceof net.minecraft.world.entity.LivingEntity le) {
                    killWithDrops(level, le);
                }
                killed++;
            }
            adults -= killed;
            rt.collectDropsAt = gameTime + 20;
            rt.dropOutputs = out;
            rt.dropBounds = bounds;
        }

        if (max < Integer.MAX_VALUE) {
            boolean stopSpawn = recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER
                    ? adults > max : adults >= max;
            if (stopSpawn) { manager.persist(data); return; }
        }

        manager.persist(data);
    }

    private static ItemStack tryConsumeFishFood(ServerLevel level, List<BlockPos> positions,
                                                 BreedingDefinition.RecipeDefinition recipe, int count) {
        if (!recipe.requireFood() || recipe.inputItems().isEmpty()) return ItemStack.EMPTY;
        String foodId = recipe.inputItems().getFirst().itemId();
        if (foodId.isBlank()) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(foodId);
        if (id == null) return ItemStack.EMPTY;
        int remaining = count;
        for (BlockPos pos : positions) {
            net.minecraft.world.Container container = BreedingInventoryHelper.containerAt(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (stackId != null && stackId.equals(id)) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.shrink(take);
                    remaining -= take;
                    container.setChanged();
                }
            }
            if (remaining <= 0) return ItemStack.EMPTY;
        }
        return null;
    }

    private static boolean hasFishFood(ServerLevel level, List<BlockPos> positions,
                                        BreedingDefinition.RecipeDefinition recipe, int count) {
        if (!recipe.requireFood() || recipe.inputItems().isEmpty()) return true;
        String foodId = recipe.inputItems().getFirst().itemId();
        if (foodId.isBlank()) return true;
        ResourceLocation id = ResourceLocation.tryParse(foodId);
        if (id == null) return true;
        int found = 0;
        for (BlockPos pos : positions) {
            net.minecraft.world.Container container = BreedingInventoryHelper.containerAt(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (stackId != null && stackId.equals(id)) {
                    found += stack.getCount();
                    if (found >= count) return true;
                }
            }
        }
        return false;
    }

    private static void handleAnimalCycle(ServerLevel level, BreedingBoxManager manager, BreedingBoxData data, BoxRuntime rt,
                                          BlockPos boxPos, AABB bounds, List<BlockPos> food, List<BlockPos> out,
                                          int max, BreedingDefinition.RecipeType recipeType) {
        ResourceLocation typeId = ResourceLocation.tryParse(rt.recipe.targetEntityType());
        if (typeId == null) return;
        EntityType<?> targetType = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (targetType == null) return;
        List<Animal> all = level.getEntitiesOfClass(Animal.class, bounds,
                a -> a.getType() == targetType && !a.isBaby());
        int adults = all.size();

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER && max < Integer.MAX_VALUE && adults > max) {
            int toKill = adults - max;
            int killed = 0;
            long gameTime = level.getGameTime();
            for (Animal target : all) {
                if (killed >= toKill) break;
                killWithDrops(level, target);
                killed++;
            }
            adults -= killed;
            rt.collectDropsAt = gameTime + 20;
            rt.dropOutputs = out;
            rt.dropBounds = bounds;
        }

        if (adults != all.size()) {
            all = level.getEntitiesOfClass(Animal.class, bounds,
                    a -> a.getType() == targetType && !a.isBaby());
            adults = all.size();
        }

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_COLLECT) {
            CollectAction action = resolveCollectAction(rt.recipe.id());
            if (action != CollectAction.NONE) {
                executeCollectAction(level, manager, data, rt, bounds, food, out, action, targetType);

                all = level.getEntitiesOfClass(Animal.class, bounds,
                        a -> a.getType() == targetType && !a.isBaby());
                adults = all.size();
            }
        }

        if (max < Integer.MAX_VALUE) {
            boolean stopBreed = recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER
                    ? adults > max : adults >= max;
            if (stopBreed) {
                manager.persist(data);
                return;
            }
        }

        List<Animal> breedable = all.stream()
                .filter(a -> a.isAlive() && !a.isBaby() && a.canFallInLove())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (breedable.size() >= 2) {
            Animal first = breedable.get(0);
            Animal second = null;
            for (int j = 1; j < breedable.size(); j++) {
                Animal c = breedable.get(j);
                if (c.getClass() == first.getClass() && c.canFallInLove()) { second = c; break; }
            }
            if (second != null) {
                if (rt.recipe.requireFood() && !BreedingInventoryHelper.consumeFood(level, food, first, 2)) {
                    setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.no_input", "");
                    return;
                }
                first.setInLove(null);
                second.setInLove(null);
                if (first.canMate(second)) {
                    first.spawnChildFromBreeding(level, second);
                }
            }
        } else {
            return;
        }
        data.setStatusKey(BreedingConstants.STATUS_RUNNING);
        data.setStatusText("");
        manager.persist(data);
    }

    private static void collectBuildingDrops(ServerLevel level, BoxRuntime rt) {
        if (rt.dropOutputs == null || rt.dropOutputs.isEmpty() || rt.dropBounds == null) {
            clearDropState(rt);
            return;
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, rt.dropBounds);
        if (items.isEmpty()) {
            clearDropState(rt);
            return;
        }
        boolean allDeposited = true;
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem().copy();
            ItemStack leftover = BreedingInventoryHelper.depositItemStack(level, rt.dropOutputs, stack);
            if (leftover.isEmpty()) {
                itemEntity.discard();
            } else {

                itemEntity.setItem(leftover);
                allDeposited = false;
            }
        }
        if (allDeposited) {
            clearDropState(rt);
        }

    }

    private static void clearDropState(BoxRuntime rt) {
        rt.collectDropsAt = 0;
        rt.dropOutputs = null;
        rt.dropBounds = null;
    }

    private static boolean isOutputFull(ServerLevel level, List<BlockPos> outputPositions) {
        if (outputPositions == null || outputPositions.isEmpty()) return true;
        for (BlockPos pos : outputPositions) {
            net.minecraft.world.Container container = BreedingInventoryHelper.containerAt(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty() || slot.getCount() < slot.getMaxStackSize()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AABB buildingBounds(PlacedBuildingRecord building) {
        int minX = Math.min(building.minPos().getX(), building.maxPos().getX());
        int minY = Math.min(building.minPos().getY(), building.maxPos().getY());
        int minZ = Math.min(building.minPos().getZ(), building.maxPos().getZ());
        int maxX = Math.max(building.minPos().getX(), building.maxPos().getX()) + 1;
        int maxY = Math.max(building.minPos().getY(), building.maxPos().getY()) + 2;
        int maxZ = Math.max(building.minPos().getZ(), building.maxPos().getZ()) + 1;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void killWithDrops(ServerLevel level, net.minecraft.world.entity.LivingEntity entity) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers()
                .stream().findFirst().orElse(null);
        net.minecraft.world.damagesource.DamageSource src = player != null
                ? level.damageSources().playerAttack(player)
                : level.damageSources().generic();
        entity.setInvulnerable(false);
        entity.hurt(src, Float.MAX_VALUE);
        if (entity.isAlive()) {
            entity.kill();
        }
    }

    private static void setWorkerHeldItem(CitizenData worker, BreedingDefinition.RecipeDefinition recipe, BreedingDefinition definition) {
        String heldItem = recipe.effectiveHeldItem(definition != null ? definition.heldItem() : "");
        if (heldItem.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(heldItem);
        if (id == null) return;
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return;
        CitizenJobVisualService.setMainHandOverride(worker.uuid(), new ItemStack(item));
    }

    private static void moveToNearestPoint(ServerLevel level, UUID citizenId, List<BlockPos> positions) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (entity == null || positions.isEmpty()) return;
        Vec3 origin = entity.position();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : positions) {
            double dist = Vec3.atBottomCenterOf(pos).distanceToSqr(origin);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        if (nearest != null) {
            Vec3 target = Vec3.atBottomCenterOf(nearest);
            if (origin.distanceToSqr(target) >= 1.0D) {
                CitizenNavigationService.requestMove(level, citizenId, target, MovementIntent.WORK);
            }
        }
    }

    private static void moveToNearestBreedableAnimal(ServerLevel level, UUID citizenId,
                                                     PlacedBuildingRecord building, BreedingDefinition.RecipeDefinition recipe) {
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, citizenId);
        if (entity == null) return;
        ResourceLocation id = ResourceLocation.tryParse(recipe.targetEntityType());
        if (id == null) return;
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) return;
        EntityType<?> targetType = type.get();
        AABB bounds = buildingBounds(building);

        if (WaterAnimal.class.isAssignableFrom(targetType.getBaseClass())) {
            List<WaterAnimal> fishCandidates = level.getEntitiesOfClass(WaterAnimal.class, bounds,
                    fish -> fish.getType() == targetType && !fish.isBaby());
            if (fishCandidates.isEmpty()) return;
            Vec3 origin = entity.position();
            WaterAnimal nearest = fishCandidates.getFirst();
            double nearestDist = Double.MAX_VALUE;
            for (WaterAnimal fish : fishCandidates) {
                double dist = fish.position().distanceToSqr(origin);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = fish;
                }
            }
            Vec3 target = nearest.position();
            if (origin.distanceToSqr(target) >= 1.0D) {
                CitizenNavigationService.requestMove(level, citizenId, target, MovementIntent.WORK);
            }
            return;
        }

        List<Animal> candidates = level.getEntitiesOfClass(Animal.class, bounds,
                animal -> animal.getType() == targetType && !animal.isBaby() && animal.canFallInLove());
        if (candidates.isEmpty()) return;
        Vec3 origin = entity.position();
        Animal nearest = candidates.getFirst();
        double nearestDist = Double.MAX_VALUE;
        for (Animal animal : candidates) {
            double dist = animal.position().distanceToSqr(origin);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = animal;
            }
        }
        Vec3 target = nearest.position();
        if (origin.distanceToSqr(target) >= 1.0D) {
            CitizenNavigationService.requestMove(level, citizenId, target, MovementIntent.WORK);
        }
    }

    private static List<BlockPos> resolvePointPositions(PlacedBuildingRecord building, BreedingDefinition definition,
                                                        String id, BlockPos boxPos) {
        if (definition == null || building == null || boxPos == null) return List.of();
        BreedingDefinition.PointDefinition point = definition.points().get(id);
        if (point == null) return List.of();
        if ("control_box_relative".equalsIgnoreCase(point.type())) {
            return resolveControlBoxRelativePositions(building, point.positions(), boxPos);
        }
        if ("structure_pos".equalsIgnoreCase(point.type())) {
            return IndustrialCoordinateResolver.resolvePositions(building, point.positions());
        }
        return List.of();
    }

    private static List<BlockPos> resolveContainerPositions(PlacedBuildingRecord building, BreedingDefinition definition,
                                                            String id, BlockPos boxPos) {
        if (definition == null || building == null || boxPos == null) return List.of();
        BreedingDefinition.ContainerDefinition container = definition.containers().get(id);
        if (container == null) return List.of();
        if ("control_box_relative".equalsIgnoreCase(container.type())) {
            return resolveControlBoxRelativePositions(building, container.positions(), boxPos);
        }
        if ("structure_pos".equalsIgnoreCase(container.type())) {
            return IndustrialCoordinateResolver.resolvePositions(building, container.positions());
        }
        return List.of();
    }

    private static List<BlockPos> resolveControlBoxRelativePositions(PlacedBuildingRecord building,
                                                                     List<BlockPos> offsets, BlockPos boxPos) {
        int rotation = rotationDegrees(building.facing());
        List<BlockPos> positions = new ArrayList<>(offsets.size());
        for (BlockPos offset : offsets) {
            if (offset == null) continue;
            BlockPos rotated = BuildingTransform.rotatePosition(offset, rotation);
            positions.add(boxPos.offset(rotated).immutable());
        }
        return List.copyOf(positions);
    }

    private static int rotationDegrees(String facing) {
        String normalized = facing == null ? "" : facing.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private static Optional<BlockPos> resolveSpawnPos(PlacedBuildingRecord building, BreedingDefinition definition, BlockPos boxPos) {
        List<BlockPos> spawn = resolvePointPositions(building, definition, "spawn", boxPos);
        if (!spawn.isEmpty()) return Optional.of(spawn.getFirst());
        List<BlockPos> work = resolvePointPositions(building, definition, "work", boxPos);
        return work.isEmpty() ? Optional.empty() : Optional.of(work.getFirst());
    }

    private static void setStatus(BreedingBoxManager manager, BreedingBoxData data, String statusKey, String statusText) {
        data.setRunning(false);
        data.setProgressTicks(0);
        data.setCooldownTicks(0);
        data.setWorkState("");
        data.setStatusKey(statusKey);
        data.setStatusText(statusText);
        manager.persist(data);
    }

    private static void setTransientStatus(BreedingBoxManager manager, BreedingBoxData data, String statusKey, String statusText) {
        data.setStatusKey(statusKey);
        data.setStatusText(statusText);
        manager.persist(data);
    }

    private enum CollectAction {
        NONE,
        MILK,
        MUSHROOM_STEW,
        MUSHROOM,
        SHEAR_WOOL,
        SHEAR_WOOL_4,
        COLLECT_EGG
    }

    private static CollectAction resolveCollectAction(String recipeId) {
        if (recipeId == null) return CollectAction.NONE;
        return switch (recipeId) {
            case "cow_milk" -> CollectAction.MILK;
            case "mooshroom_stew" -> CollectAction.MUSHROOM_STEW;
            case "mooshroom_mushroom" -> CollectAction.MUSHROOM;
            case "sheep_wool" -> CollectAction.SHEAR_WOOL;
            case "minisheep_wool" -> CollectAction.SHEAR_WOOL_4;
            case "chicken_egg" -> CollectAction.COLLECT_EGG;
            default -> CollectAction.NONE;
        };
    }

    private static void executeCollectAction(ServerLevel level, BreedingBoxManager manager,
            BreedingBoxData data, BoxRuntime rt, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, CollectAction action, EntityType<?> targetType) {
        if (action == CollectAction.NONE || out.isEmpty()) return;
        switch (action) {
            case MILK -> collectMilk(level, bounds, food, out, targetType);
            case MUSHROOM_STEW -> collectMushroomStew(level, bounds, food, out, targetType);
            case MUSHROOM -> collectMushroom(level, bounds, out, targetType);
            case SHEAR_WOOL -> collectShearWool(level, bounds, out, targetType);
            case SHEAR_WOOL_4 -> collectShearWool4(level, bounds, out, targetType);
            case COLLECT_EGG -> collectEgg(level, bounds, out);
            default -> {}
        }
    }

    private static void collectMilk(ServerLevel level, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, EntityType<?> targetType) {
        List<Animal> cows = level.getEntitiesOfClass(Animal.class, bounds,
                a -> a.getType() == targetType && !a.isBaby());
        if (cows.isEmpty()) return;
        int milked = 0;
        for (Animal cow : cows) {
            if (milked >= 4) break;
            if (!BreedingInventoryHelper.hasItem(level, food, "minecraft:bucket", 1)) break;
            if (!BreedingInventoryHelper.consumeItem(level, food, "minecraft:bucket", 1)) break;
            BreedingInventoryHelper.depositItem(level, out, "minecraft:milk_bucket", 1);
            milked++;
        }
    }

    private static void collectMushroomStew(ServerLevel level, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, EntityType<?> targetType) {
        List<Animal> mooshrooms = level.getEntitiesOfClass(Animal.class, bounds,
                a -> a.getType() == targetType && !a.isBaby());
        if (mooshrooms.isEmpty()) return;
        int collected = 0;
        for (Animal mooshroom : mooshrooms) {
            if (collected >= 4) break;
            if (!BreedingInventoryHelper.hasItem(level, food, "minecraft:bowl", 1)) break;
            if (!BreedingInventoryHelper.consumeItem(level, food, "minecraft:bowl", 1)) break;
            BreedingInventoryHelper.depositItem(level, out, "minecraft:mushroom_stew", 1);
            collected++;
        }
    }

    private static void collectMushroom(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType) {
        List<MushroomCow> mooshrooms = level.getEntitiesOfClass(MushroomCow.class, bounds,
                m -> m.getType() == targetType && !m.isBaby());
        if (mooshrooms.isEmpty()) return;

        MushroomCow target = mooshrooms.get(0);
        boolean isRed = target.getVariant() == MushroomCow.MushroomType.RED;
        String mushroomId = isRed ? "minecraft:red_mushroom" : "minecraft:brown_mushroom";

        BreedingInventoryHelper.depositItem(level, out, mushroomId, 5);

        Cow cow = EntityType.COW.create(level);
        if (cow != null) {
            cow.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            cow.setHealth(target.getHealth());
            level.addFreshEntity(cow);
        }
        target.discard();
    }

    private static void collectShearWool(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType) {
        List<Sheep> sheep = level.getEntitiesOfClass(Sheep.class, bounds,
                s -> s.getType() == targetType && !s.isBaby() && !s.isSheared());
        if (sheep.isEmpty()) return;
        int sheared = 0;
        for (Sheep s : sheep) {
            if (sheared >= 4) break;
            String woolId = "minecraft:" + s.getColor().getName() + "_wool";
            BreedingInventoryHelper.depositItem(level, out, woolId, 2);
            s.setSheared(true);
            sheared++;
        }
    }

    private static void collectShearWool4(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType) {
        List<Animal> sheep = level.getEntitiesOfClass(Animal.class, bounds,
                a -> a.getType() == targetType && !a.isBaby());
        if (sheep.isEmpty()) return;

        Animal animal = sheep.get(0);

        if (animal instanceof Sheep vanillaSheep && vanillaSheep.isSheared()) return;
        if (animal instanceof Sheep vanillaSheep) vanillaSheep.setSheared(true);
        BreedingInventoryHelper.depositItem(level, out, "minecraft:white_wool", 4);
    }

    private static void collectEgg(ServerLevel level, AABB bounds, List<BlockPos> out) {
        ResourceLocation eggId = ResourceLocation.tryParse("minecraft:egg");
        if (eggId == null) return;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, bounds,
                ie -> ie.isAlive() && !ie.getItem().isEmpty());
        int collected = 0;
        for (ItemEntity ie : items) {
            ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem());
            if (stackId != null && stackId.equals(eggId)) {
                ItemStack stack = ie.getItem().copy();
                ItemStack leftover = BreedingInventoryHelper.depositItemStack(level, out, stack);
                if (leftover.isEmpty()) {
                    ie.discard();
                    collected += stack.getCount();
                } else {
                    ie.setItem(leftover);
                    collected += stack.getCount() - leftover.getCount();
                }
            }
        }
    }

    private static final class BoxRuntime {
        PlacedBuildingRecord building;
        BreedingDefinition definition;
        BreedingDefinition.RecipeDefinition recipe;
        CitizenData worker;
        long lastValidate;
        long lastEntityScan;
        long lastFeedTick;
        long nextTick;

        long collectDropsAt;
        List<BlockPos> dropOutputs;
        AABB dropBounds;

        void reset() {
            building = null;
            definition = null;
            recipe = null;
            worker = null;
            lastValidate = 0;
            lastEntityScan = 0;
            lastFeedTick = 0;
            nextTick = 0;
            collectDropsAt = 0;
            dropOutputs = null;
            dropBounds = null;
        }
    }
}

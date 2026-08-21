package com.xy2407.nsukaddition.common.breeding;

import com.xy2407.nsukaddition.common.compat.LetFishLoveCompat;
import common.cn.kafei.simukraft.city.group.CityUserGroup;
import common.cn.kafei.simukraft.city.group.CityUserGroupService;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 繁殖工作服务，每 tick 驱动繁殖箱运行，处理喂食、繁殖、捕杀与采集。 */
@SuppressWarnings("null")
public final class BreedingWorkService {
    private static final long VALIDATE_INTERVAL = 20L;
    private static final long ENTITY_SCAN_INTERVAL = 40L;

    private static final long FEED_INTERVAL = 2400L;
    private static final long DROP_COLLECT_INTERVAL = 600L;
    private static final int BREEDING_CAP = 24;

    private static final ConcurrentMap<BlockPos, BoxRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private BreedingWorkService() {}

    public static void tick(ServerLevel level) {
        if (level == null) return;
        BreedingBoxManager manager = BreedingBoxManager.get(level);
        long gameTime = level.getGameTime();
        Set<BlockPos> activePositions = ConcurrentHashMap.newKeySet();
        for (BreedingBoxData data : manager.all()) {
            BlockPos key = data.boxPos().immutable();
            activePositions.add(key);
            BoxRuntime rt = RUNTIMES.computeIfAbsent(key, k -> new BoxRuntime());
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
        RUNTIMES.keySet().retainAll(activePositions);
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

        if (rt.nextUnifiedCollect == 0) {
            rt.nextUnifiedCollect = gameTime + 40;
        }
        if (gameTime >= rt.nextUnifiedCollect) {
            rt.nextUnifiedCollect = gameTime + DROP_COLLECT_INTERVAL;
            unifiedCollectDrops(level, data.boxPos(), rt);
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

        List<BlockPos> outputPositions = resolveContainerPositions(rt.building, rt.definition, "output", boxPos);
        if (isOutputFull(level, outputPositions)) {
            setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.output_full", "");
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(rt.recipe.targetEntityType());
        if (id == null) return;
        Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (typeOpt.isEmpty()) return;
        EntityType<?> targetType = typeOpt.get();
        AABB bounds = buildingBounds(rt.building);
        List<BlockPos> foodContainers = resolveContainerPositions(rt.building, rt.definition, "input", boxPos);
        int maxEntities = rt.recipe.maxEntities() > 0 ? rt.recipe.maxEntities() : Integer.MAX_VALUE;
        BreedingDefinition.RecipeType recipeType = rt.recipe.type();

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
                fish -> fish.getType() == targetType && fish.isAlive());
        if (all.isEmpty()) return;
        List<WaterAnimal> adults = all.stream().filter(f -> !f.isBaby()).toList();
        int adultCount = adults.size();

        int totalCount = all.size();
        if (max < Integer.MAX_VALUE && totalCount > max) {
            int toKill = Math.min(totalCount - max, adultCount);
            int killed = 0;
            for (WaterAnimal target : adults) {
                if (killed >= toKill) break;
                killWithDrops(level, target, out, cityIdOf(rt));
                killed++;
            }
            adultCount -= killed;
        }

        adults = level.getEntitiesOfClass(WaterAnimal.class, bounds,
                fish -> fish.getType() == targetType && !fish.isBaby());
        adultCount = adults.size();

        if (adultCount < 2) {
            manager.persist(data);
            return;
        }
        if (!LetFishLoveCompat.isLoaded()) {
            manager.persist(data);
            return;
        }

        WaterAnimal a = adults.get(0);
        WaterAnimal b = null;
        for (int j = 1; j < adults.size(); j++) {
            WaterAnimal c = adults.get(j);
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
                e -> e.getType() == targetType && e.isAlive());
        if (all.isEmpty()) return;
        List<net.minecraft.world.entity.Mob> adults = all.stream().filter(e -> !e.isBaby()).toList();
        int adultCount = adults.size();

        int totalCount = all.size();
        if (max < Integer.MAX_VALUE && totalCount > max) {
            int toKill = Math.min(totalCount - max, adultCount);
            int killed = 0;
            for (net.minecraft.world.entity.Mob target : adults) {
                if (killed >= toKill) break;
                killWithDrops(level, target, out, cityIdOf(rt));
                killed++;
            }
            adultCount -= killed;
        }

        adults = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, bounds,
                e -> e.getType() == targetType && e.isAlive() && !e.isBaby());
        adultCount = adults.size();

        if (adultCount < 2) {
            manager.persist(data);
            return;
        }
        if (!LetFishLoveCompat.isLoaded()) {
            manager.persist(data);
            return;
        }

        net.minecraft.world.entity.Mob a = adults.get(0);
        net.minecraft.world.entity.Mob b = null;
        for (int j = 1; j < adults.size(); j++) {
            net.minecraft.world.entity.Mob c = adults.get(j);
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

        if (max < Integer.MAX_VALUE && adults > max) {
            int toKill = adults - max;
            int killed = 0;
            long gameTime = level.getGameTime();
            for (Entity target : all) {
                if (killed >= toKill) break;
                if (target instanceof net.minecraft.world.entity.LivingEntity le) {
                    killWithDrops(level, le, out, cityIdOf(rt));
                }
                killed++;
            }
            adults -= killed;
        }

        if (max < Integer.MAX_VALUE && adults >= max) {
            manager.persist(data);
            return;
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

        Mob base = consolidateAnimals(level, bounds, targetType);
        if (base == null) return;
        int virtualCount = getVirtualCount(base);

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_SLAUGHTER && virtualCount > BREEDING_CAP) {
            int toKill = virtualCount - BREEDING_CAP;
            spawnAndKill(level, base, targetType, toKill, out, cityIdOf(rt));
            virtualCount = getVirtualCount(base);
        }

        if (recipeType == BreedingDefinition.RecipeType.BREEDING_COLLECT) {
            CollectAction action = resolveCollectAction(rt.recipe.id());
            if (action != CollectAction.NONE) {
                executeCollectAction(level, manager, data, rt, bounds, food, out, action, targetType, virtualCount);
            }
        }

        if (rt.recipe.requireFood() && virtualCount > 0) {
            int available = countFeed(level, food, rt.recipe);
            int pairGrowth = virtualCount / 2;
            int growthCap = Math.max(0, BREEDING_CAP - virtualCount);
            int growth = Math.max(0, Math.min(pairGrowth, growthCap));
            int toConsume = Math.max(0, Math.min(available, growth));
            if (toConsume > 0) {
                consumeFeedCount(level, food, rt.recipe, toConsume);
                List<CompoundTag> entries = readVirtualEntries(base);
                for (int i = 0; i < toConsume; i++) {
                    CompoundTag def = new CompoundTag();
                    def.putInt("Age", 0);
                    entries.add(def);
                }
                setVirtualEntries(base, entries);
            } else if (growthCap == 0) {
                setTransientStatus(manager, data, BreedingConstants.STATUS_REACHED_CAP, "");
            } else {
                setTransientStatus(manager, data, "gui.xy2407_nsuk_addition.breeding.status.no_input", "");
            }
        }
        updateBaseDisplay(base);
        manager.persist(data);
    }

    private static final String VIRTUAL_ENTRIES_KEY = "nsuk_breeding_entries";

    private static List<CompoundTag> readVirtualEntries(Mob entity) {
        List<CompoundTag> result = new ArrayList<>();
        if (entity == null) return result;
        CompoundTag data = entity.getPersistentData();
        if (data.contains(VIRTUAL_ENTRIES_KEY, Tag.TAG_LIST)) {
            ListTag list = data.getList(VIRTUAL_ENTRIES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                result.add(list.getCompound(i).copy());
            }
        }
        return result;
    }

    private static void setVirtualEntries(Mob entity, List<CompoundTag> entries) {
        if (entity == null) return;
        ListTag list = new ListTag();
        for (CompoundTag e : entries) {
            list.add(e.copy());
        }
        entity.getPersistentData().put(VIRTUAL_ENTRIES_KEY, list);
    }

    private static int getVirtualCount(Mob entity) {
        return readVirtualEntries(entity).size();
    }

    private static CompoundTag sanitizeEntityNbt(Mob mob) {
        CompoundTag raw = new CompoundTag();
        mob.saveWithoutId(raw);
        CompoundTag entry = com.xy2407.nsukaddition.common.capture.EntityNbtSanitizer.sanitize(raw);
        entry.remove("Age");
        return entry;
    }

    private static Mob consolidateAnimals(ServerLevel level, AABB bounds, EntityType<?> targetType) {
        List<Mob> all = level.getEntitiesOfClass(Mob.class, bounds,
                m -> m.getType() == targetType && m.isAlive());
        if (all.isEmpty()) return null;

        Mob base = null;
        for (Mob m : all) {
            if (!readVirtualEntries(m).isEmpty()) {
                base = m;
                break;
            }
        }
        if (base == null) {
            base = all.get(0);
            List<CompoundTag> init = new ArrayList<>();
            init.add(sanitizeEntityNbt(base));
            setVirtualEntries(base, init);
        }
        List<CompoundTag> entries = readVirtualEntries(base);
        for (Mob m : all) {
            if (m != base && readVirtualEntries(m).isEmpty()) {
                entries.add(sanitizeEntityNbt(m));
                m.discard();
            }
        }
        setVirtualEntries(base, entries);
        return base;
    }

    private static void updateBaseDisplay(Mob base) {
        if (base == null) return;
        base.setCustomName(Component.literal("x" + getVirtualCount(base)));
        base.setCustomNameVisible(true);
    }

    public static void handleBaseDeath(Mob base) {
        if (base == null) return;
        List<CompoundTag> entries = readVirtualEntries(base);
        if (entries.isEmpty()) return;
        if (entries.size() == 1) {
            return;
        }
        if (!(base.level() instanceof ServerLevel level)) return;
        entries.remove(entries.size() - 1);
        EntityType<?> type = base.getType();
        Mob fresh = (Mob) type.create(level);
        if (fresh == null) return;
        CompoundTag raw = base.saveWithoutId(new CompoundTag());
        raw.remove("Pos");
        raw.remove("Rotation");
        raw.remove("UUID");
        raw.remove(VIRTUAL_ENTRIES_KEY);
        raw.remove("Health");
        raw.remove("DeathTime");
        raw.remove("HurtTime");
        raw.remove("HurtByTimestamp");
        try {
            fresh.load(raw);
        } catch (RuntimeException ignored) {
        }
        fresh.setHealth(fresh.getMaxHealth());
        fresh.moveTo(base.getX(), base.getY(), base.getZ(), base.getYRot(), base.getXRot());
        if (!entries.isEmpty()) {
            setVirtualEntries(fresh, entries);
            updateBaseDisplay(fresh);
        } else {
            fresh.setCustomName(null);
            fresh.setCustomNameVisible(false);
        }
        level.addFreshEntity(fresh);
    }

    public static boolean isBaseEntity(Mob entity) {
        return entity != null && !readVirtualEntries(entity).isEmpty();
    }

    public static boolean baseToCapture(ServerLevel level, Player player, InteractionHand hand, Mob base) {
        if (level == null || player == null || base == null) {
            return false;
        }
        List<CompoundTag> entries = readVirtualEntries(base);
        if (entries.isEmpty()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof EntityCaptureItem) || stack.getCount() <= 0) {
            return false;
        }
        if (EntityCaptureItem.getEntryCount(stack) >= EntityCaptureItem.MAX_CAPTURES) {
            return false;
        }
        EntityType<?> baseType = base.getType();
        CompoundTag entry = entries.remove(entries.size() - 1);
        entry.remove("Age");
        EntityType<?> stackType = EntityCaptureItem.getEntityType(stack);
        if (stackType == null) {
            ItemStack captured = new ItemStack(stack.getItem(), 1);
            EntityCaptureItem.transferIn(captured, baseType, entry);
            if (player.getAbilities().instabuild) {
                if (!player.getInventory().add(captured)) {
                    player.drop(captured, false);
                }
            } else if (stack.getCount() == 1) {
                player.setItemInHand(hand, captured);
            } else {
                stack.shrink(1);
                if (!player.getInventory().add(captured)) {
                    player.drop(captured, false);
                }
            }
        } else if (stackType.equals(baseType)) {
            EntityCaptureItem.transferIn(stack, baseType, entry);
        } else {
            entries.add(entry);
            setVirtualEntries(base, entries);
            updateBaseDisplay(base);
            return false;
        }
        setVirtualEntries(base, entries);
        if (entries.isEmpty()) {
            base.discard();
        } else {
            updateBaseDisplay(base);
        }
        return true;
    }

    private static void spawnAndKill(ServerLevel level, Mob base, EntityType<?> targetType, int count, List<BlockPos> out, UUID cityId) {
        if (count <= 0) return;
        List<CompoundTag> entries = readVirtualEntries(base);
        double cx = (base.getX() + base.getBoundingBox().getXsize() / 2.0D);
        double cy = base.getY();
        double cz = (base.getZ() + base.getBoundingBox().getZsize() / 2.0D);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            CompoundTag entry = entries.isEmpty() ? null : entries.remove(entries.size() - 1);
            try {
                Entity e = targetType.create(level);
                if (e == null) continue;
                if (entry != null && e instanceof net.minecraft.world.entity.LivingEntity le) {
                    try {
                        le.load(entry.copy());
                    } catch (RuntimeException ignored) {
                    }
                }
                e.absMoveTo(cx, cy, cz, level.random.nextFloat() * 360.0F, 0);
                level.addFreshEntity(e);
                if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                    killWithDrops(level, le, out, cityId);
                    spawned++;
                } else {
                    e.discard();
                }
            } catch (RuntimeException ex) {
                NsukAddition.LOGGER.error("spawnAndKill failed for {}", targetType, ex);
            }
        }
        setVirtualEntries(base, entries);
    }

    private static int countFeed(ServerLevel level, List<BlockPos> positions, BreedingDefinition.RecipeDefinition recipe) {
        if (!recipe.requireFood() || recipe.inputItems().isEmpty()) return Integer.MAX_VALUE;
        String foodId = recipe.inputItems().getFirst().itemId();
        if (foodId.isBlank()) return Integer.MAX_VALUE;
        ResourceLocation id = ResourceLocation.tryParse(foodId);
        if (id == null) return 0;
        int found = 0;
        for (BlockPos pos : positions) {
            net.minecraft.world.Container container = BreedingInventoryHelper.containerAt(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                ResourceLocation sid = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (sid != null && sid.equals(id)) found += s.getCount();
            }
        }
        return found;
    }

    private static void consumeFeedCount(ServerLevel level, List<BlockPos> positions, BreedingDefinition.RecipeDefinition recipe, int count) {
        if (count <= 0) return;
        String foodId = recipe.inputItems().getFirst().itemId();
        ResourceLocation id = ResourceLocation.tryParse(foodId);
        if (id == null) return;
        int remaining = count;
        for (BlockPos pos : positions) {
            net.minecraft.world.Container container = BreedingInventoryHelper.containerAt(level, pos);
            if (container == null) continue;
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack s = container.getItem(i);
                ResourceLocation sid = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (sid != null && sid.equals(id)) {
                    int take = Math.min(s.getCount(), remaining);
                    s.shrink(take);
                    remaining -= take;
                    container.setChanged();
                }
            }
            if (remaining <= 0) return;
        }
    }

    private static void unifiedCollectDrops(ServerLevel level, BlockPos boxPos, BoxRuntime rt) {
        if (rt.building == null || rt.definition == null) return;
        List<BlockPos> outputPositions = resolveContainerPositions(rt.building, rt.definition, "output", boxPos);
        if (outputPositions == null || outputPositions.isEmpty()) return;
        AABB bounds = buildingBounds(rt.building);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, bounds);
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem().copy();
            ItemStack leftover = BreedingInventoryHelper.depositItemStack(level, outputPositions, stack);
            if (leftover.isEmpty()) {
                item.discard();
            } else {
                item.setItem(leftover);
            }
        }

        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, bounds);
        if (!orbs.isEmpty()) {
            BreedingInventoryHelper.depositItem(level, outputPositions, "minecraft:experience_bottle", orbs.size());
            for (ExperienceOrb orb : orbs) {
                orb.discard();
            }
        }
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

    private static ServerPlayer resolveKillSource(ServerLevel level, UUID cityId) {
        if (cityId != null) {
            List<ServerPlayer> mayors = CityUserGroupService.onlinePlayers(level, CityUserGroup.mayors(cityId));
            if (!mayors.isEmpty()) {
                return mayors.get(0);
            }
            List<ServerPlayer> officials = CityUserGroupService.onlinePlayers(level, CityUserGroup.officials(cityId));
            if (!officials.isEmpty()) {
                return officials.get(0);
            }
        }
        return level.getServer().getPlayerList().getPlayers()
                .stream().findFirst().orElse(null);
    }

    private static UUID cityIdOf(BoxRuntime rt) {
        return rt != null && rt.building != null ? rt.building.cityId() : null;
    }

    private static void killWithDrops(ServerLevel level, net.minecraft.world.entity.LivingEntity entity, List<BlockPos> out, UUID cityId) {
        ServerPlayer player = resolveKillSource(level, cityId);
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
        MUSHROOM,
        SHEAR_WOOL,
        SHEAR_WOOL_4,
        WOODEN_MILK,
        WOODEN_SHEEP_MILK,
        WOODEN_GOAT_MILK,
        WOODEN_BUFFALO_MILK,
        WOODEN_WARPED_MILK,
        CHICKEN_EGG
    }

    private static CollectAction resolveCollectAction(String recipeId) {
        if (recipeId == null) return CollectAction.NONE;
        return switch (recipeId) {
            case "cow_milk" -> CollectAction.MILK;
            case "mooshroom_mushroom" -> CollectAction.MUSHROOM;
            case "sheep_wool" -> CollectAction.SHEAR_WOOL;
            case "minisheep_wool" -> CollectAction.SHEAR_WOOL_4;
            case "cow_wooden_milk" -> CollectAction.WOODEN_MILK;
            case "sheep_milk" -> CollectAction.WOODEN_SHEEP_MILK;
            case "goat_milk" -> CollectAction.WOODEN_GOAT_MILK;
            case "buffalo_milk" -> CollectAction.WOODEN_BUFFALO_MILK;
            case "wooly_cow_milk" -> CollectAction.WOODEN_WARPED_MILK;
            case "chicken_egg" -> CollectAction.CHICKEN_EGG;
            default -> CollectAction.NONE;
        };
    }

    private static void executeCollectAction(ServerLevel level, BreedingBoxManager manager,
            BreedingBoxData data, BoxRuntime rt, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, CollectAction action, EntityType<?> targetType,
            int virtualCount) {
        if (action == CollectAction.NONE || out.isEmpty()) return;
        switch (action) {
            case MILK -> collectMilk(level, bounds, food, out, targetType, virtualCount);
            case MUSHROOM -> collectMushroom(level, bounds, out, targetType, virtualCount);
            case SHEAR_WOOL -> collectShearWool(level, bounds, out, targetType, virtualCount);
            case SHEAR_WOOL_4 -> collectShearWool4(level, bounds, out, targetType);
            case WOODEN_MILK -> collectWoodenMilk(level, bounds, food, out, targetType, "meadow:wooden_milk_bucket", virtualCount);
            case WOODEN_SHEEP_MILK -> collectWoodenMilk(level, bounds, food, out, targetType, "meadow:wooden_sheep_milk_bucket", virtualCount);
            case WOODEN_GOAT_MILK -> collectWoodenMilk(level, bounds, food, out, targetType, "meadow:wooden_goat_milk_bucket", virtualCount);
            case WOODEN_BUFFALO_MILK -> collectWoodenMilk(level, bounds, food, out, targetType, "meadow:wooden_buffalo_milk_bucket", virtualCount);
            case WOODEN_WARPED_MILK -> collectWoodenMilk(level, bounds, food, out, targetType, "meadow:wooden_warped_milk_bucket", virtualCount);
            case CHICKEN_EGG -> collectChickenEgg(level, bounds, out, targetType, virtualCount);
            default -> {}
        }
    }

    private static void collectMilk(ServerLevel level, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, EntityType<?> targetType, int virtualCount) {
        if (virtualCount <= 0) return;
        BreedingInventoryHelper.depositItem(level, out, "minecraft:milk_bucket", Math.min(virtualCount, BREEDING_CAP));
    }

    private static void collectWoodenMilk(ServerLevel level, AABB bounds,
            List<BlockPos> food, List<BlockPos> out, EntityType<?> targetType, String bucketId, int virtualCount) {
        if (virtualCount <= 0) return;
        BreedingInventoryHelper.depositItem(level, out, bucketId, Math.min(virtualCount, BREEDING_CAP));
    }

    private static void collectChickenEgg(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType, int virtualCount) {
        if (virtualCount <= 0) return;
        BreedingInventoryHelper.depositItem(level, out, "minecraft:egg", Math.min(virtualCount, BREEDING_CAP));
    }

    private static void collectMushroom(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType, int virtualCount) {
        if (virtualCount <= 0) return;
        int totalMushrooms = virtualCount * 5;
        String mushroomId = "minecraft:red_mushroom";
        BreedingInventoryHelper.depositItem(level, out, mushroomId, Math.min(totalMushrooms, 20));
    }

    private static void collectShearWool(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType, int virtualCount) {
        if (virtualCount <= 0) return;
        int woolCount = virtualCount * 2;
        BreedingInventoryHelper.depositItem(level, out, "minecraft:white_wool", Math.min(woolCount, 8));
    }

    private static void collectShearWool4(ServerLevel level, AABB bounds,
            List<BlockPos> out, EntityType<?> targetType) {
        if (getVirtualCountOnAny(level, bounds, targetType) <= 0) return;
        BreedingInventoryHelper.depositItem(level, out, "minecraft:white_wool", 4);
    }

    private static int getVirtualCountOnAny(ServerLevel level, AABB bounds, EntityType<?> targetType) {
        List<Mob> all = level.getEntitiesOfClass(Mob.class, bounds,
                m -> m.getType() == targetType && m.isAlive() && getVirtualCount(m) > 0);
        return all.isEmpty() ? 0 : getVirtualCount(all.get(0));
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
        long nextUnifiedCollect;

        void reset() {
            building = null;
            definition = null;
            recipe = null;
            worker = null;
            lastValidate = 0;
            lastEntityScan = 0;
            lastFeedTick = 0;
            nextTick = 0;
            nextUnifiedCollect = 0;
        }
    }
}

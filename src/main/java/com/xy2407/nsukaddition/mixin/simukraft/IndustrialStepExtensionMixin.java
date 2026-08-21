package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.industrial.FlowerFertilizeService;
import com.xy2407.nsukaddition.common.industrial.JumpRescueSkipHolder;
import com.xy2407.nsukaddition.common.industrial.StepResultAccess;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialControlBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialWorkService;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 修改 IndustrialWorkService，扩展步骤类型以支持右键交互和跳跃动作。 */
@Mixin(IndustrialWorkService.class)
public class IndustrialStepExtensionMixin {

    private static final Map<UUID, long[]> JUMP_STATE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> RIGHT_CLICK_FAIL_COUNT = new ConcurrentHashMap<>();
    private static final int RIGHT_CLICK_MAX_RETRIES = 3;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "executeStep", at = @At("HEAD"), cancellable = true, remap = false)
    private static void xy2407$extendSteps(ServerLevel level, IndustrialBoxManager manager,
                                            IndustrialBoxData data, @Coerce Object boxRuntime,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            IndustrialDefinition.RecipeDefinition recipe,
                                            CitizenData worker, CitizenEntity entity,
                                            IndustrialDefinition.StepDefinition step,
                                            long gameTime, CallbackInfoReturnable cir) {
        String type = step.type().toLowerCase(Locale.ROOT);
        if ("right_click".equals(type)) {
            String failKey = entity.getUUID() + ":" + data.currentStep();
            boolean ok = handleRightClick(level, building, definition, entity, step);
            if (ok) {
                RIGHT_CLICK_FAIL_COUNT.remove(failKey);
                cir.setReturnValue(StepResultAccess.progressed());
            } else {
                int fails = RIGHT_CLICK_FAIL_COUNT.merge(failKey, 1, Integer::sum);
                if (fails >= RIGHT_CLICK_MAX_RETRIES) {
                    RIGHT_CLICK_FAIL_COUNT.remove(failKey);
                    cir.setReturnValue(StepResultAccess.progressed());
                } else {
                    cir.setReturnValue(StepResultAccess.waitingRetry());
                }
            }
        } else if ("jump".equals(type)) {
            cir.setReturnValue(handleJump(level, building, definition, worker, entity, step, gameTime));
        } else if ("fertilize_flowers".equals(type)) {
            FlowerFertilizeService.Result result = FlowerFertilizeService.execute(
                    level, manager, data, building, definition, step, worker, entity);
            cir.setReturnValue(switch (result) {
                case PROGRESSED -> StepResultAccess.progressed();
                case WAITING -> StepResultAccess.waiting();
                case WAITING_RETRY -> StepResultAccess.waitingRetry();
            });
        }
    }

    private static boolean handleRightClick(ServerLevel level, PlacedBuildingRecord building,
                                             IndustrialDefinition definition, CitizenEntity entity,
                                             IndustrialDefinition.StepDefinition step) {
        BlockPos target = resolveStepPosition(building, definition, entity, step);
        if (target == null) return false;

        if (step.swing()) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        BlockState state = level.getBlockState(target);

        if (!step.item().isBlank()) {
            return handleItemRightClick(level, building, definition, entity, step, target, state);
        }
        ItemStack savedHand = entity.getItemInHand(InteractionHand.MAIN_HAND).copy();
        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean result = tryInteractGeneric(level, entity, target, state);
        entity.setItemInHand(InteractionHand.MAIN_HAND, savedHand);
        return result;
    }

    private static boolean handleItemRightClick(ServerLevel level, PlacedBuildingRecord building,
                                                 IndustrialDefinition definition, CitizenEntity entity,
                                                 IndustrialDefinition.StepDefinition step,
                                                 BlockPos pos, BlockState state) {
        String containerId = step.container().isBlank() ? step.input() : step.container();
        if (containerId.isBlank()) return false;
        List<BlockPos> positions = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerId);
        if (positions.isEmpty()) return false;

        Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(step.item()));
        if (targetItem == null || targetItem == Items.AIR) return false;

        ItemStack taken = null;
        int takeCount = step.count() > 0 ? step.count() : 1;
        for (BlockPos cp : positions) {
            Container c = containerAt(level, cp);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.is(targetItem) && s.getCount() > 0) {
                    int take = Math.min(Math.min(s.getCount(), s.getMaxStackSize()), takeCount);
                    taken = s.split(take);
                    c.setChanged();
                    break;
                }
            }
            if (taken != null) break;
        }
        if (taken == null) return false;

        FakePlayer fake = prepareFakePlayer(level, entity, taken);
        Vec3 hitVec = Vec3.atCenterOf(pos);
        Vec3 entityPos = entity.position();
        Direction face = Direction.getNearest(hitVec.x - entityPos.x, 0.0, hitVec.z - entityPos.z);
        if (face == null) face = Direction.NORTH;
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
        ItemInteractionResult result = state.useItemOn(fake.getItemInHand(InteractionHand.MAIN_HAND), level, fake, InteractionHand.MAIN_HAND, hit);
        ItemStack handAfter = fake.getItemInHand(InteractionHand.MAIN_HAND);

        if (!handAfter.isEmpty()) {
            if (handAfter.getItem() != targetItem) {
                if (!step.output().isBlank()) {
                    storeToContainer(level, building, definition, step, handAfter.copy());
                } else {
                    entity.setItemInHand(InteractionHand.MAIN_HAND, handAfter.copy());
                }
            } else {
                putBackToInput(level, building, definition, step, handAfter.copy());
            }
            fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return result.consumesAction() || result == ItemInteractionResult.SUCCESS;
    }

    private static Object handleJump(ServerLevel level, PlacedBuildingRecord building,
                                      IndustrialDefinition definition, CitizenData worker, CitizenEntity entity,
                                      IndustrialDefinition.StepDefinition step, long gameTime) {
        int total = Math.max(1, step.count() > 0 ? step.count() : 1);
        UUID id = entity.getUUID();
        long[] state = JUMP_STATE.computeIfAbsent(id, k -> new long[]{0, 0, 0});
        if (state[0] != total) {
            state[0] = total;
            state[1] = 0;
            state[2] = 0;
        }
        if (state[1] >= total) {
            JUMP_STATE.remove(id);
            JumpRescueSkipHolder.setSkip(id, false);
            entity.setPose(Pose.STANDING);
            return StepResultAccess.progressed();
        }

        JumpRescueSkipHolder.setSkip(id, true);

        CitizenNavigationService.stop(level, entity.getUUID());
        entity.getNavigation().stop();

        BlockPos targetPos = null;
        if (!step.positions().isEmpty()) {
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, step.positions());
            if (!resolved.isEmpty()) {
                targetPos = resolved.getFirst();
            }
        } else {
            targetPos = resolveStepPosition(building, definition, entity, step);
        }
        if (targetPos != null) {
            Vec3 standTarget = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
            if (entity.position().distanceToSqr(standTarget) > 0.01) {
                entity.teleportTo(standTarget.x, standTarget.y, standTarget.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }

        if (state[1] == 0) {
            if (step.swing()) entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
            entity.setPose(Pose.STANDING);
        }

        if (gameTime - state[2] >= 10 || state[1] == 0) {
            entity.getJumpControl().jump();
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.49, entity.getDeltaMovement().z);
            state[1]++;
            state[2] = gameTime;
        }
        return StepResultAccess.waitingRetry();
    }

    private static boolean tryInteractGeneric(ServerLevel level, CitizenEntity entity, BlockPos pos, BlockState state) {
        ItemStack npcHand = entity.getItemInHand(InteractionHand.MAIN_HAND);
        FakePlayer fake = prepareFakePlayer(level, entity, npcHand.copy());
        Vec3 hitVec = Vec3.atCenterOf(pos);
        Vec3 entityPos = entity.position();
        Direction face = Direction.getNearest(hitVec.x - entityPos.x, 0.0, hitVec.z - entityPos.z);
        if (face == null) face = Direction.NORTH;
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);

        ItemInteractionResult itemResult = state.useItemOn(fake.getItemInHand(InteractionHand.MAIN_HAND), level, fake, InteractionHand.MAIN_HAND, hit);
        ItemStack handAfter = fake.getItemInHand(InteractionHand.MAIN_HAND);
        if (itemResult.consumesAction() || itemResult == ItemInteractionResult.SUCCESS) {
            entity.setItemInHand(InteractionHand.MAIN_HAND, handAfter);
            return true;
        }
        state.useWithoutItem(level, fake, hit);
        return true;
    }

    private static FakePlayer prepareFakePlayer(ServerLevel level, CitizenEntity entity, ItemStack item) {
        FakePlayer fake = FakePlayerFactory.getMinecraft(level);
        fake.setPos(entity.getX(), entity.getY(), entity.getZ());
        fake.setYRot(entity.getYRot());
        fake.setXRot(entity.getXRot());
        fake.setItemInHand(InteractionHand.MAIN_HAND, item);
        return fake;
    }

    private static void storeToContainer(ServerLevel level, PlacedBuildingRecord building,
                                          IndustrialDefinition definition, IndustrialDefinition.StepDefinition step,
                                          ItemStack stack) {
        String outId = step.output().isBlank() ? step.container() : step.output();
        if (outId.isBlank() || stack.isEmpty()) return;
        List<BlockPos> positions = IndustrialControlBoxService.resolveContainerPositions(building, definition, outId);
        for (BlockPos cp : positions) {
            Container c = containerAt(level, cp);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack slot = c.getItem(i);
                if (slot.isEmpty()) { c.setItem(i, stack); c.setChanged(); return; }
                if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                    int room = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                    slot.grow(room); stack.shrink(room); c.setChanged();
                    if (stack.isEmpty()) return;
                }
            }
        }
    }

    private static void putBackToInput(ServerLevel level, PlacedBuildingRecord building,
                                        IndustrialDefinition definition, IndustrialDefinition.StepDefinition step,
                                        ItemStack stack) {
        String containerId = step.container().isBlank() ? step.input() : step.container();
        if (containerId.isBlank() || stack.isEmpty()) return;
        List<BlockPos> positions = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerId);
        for (BlockPos cp : positions) {
            Container c = containerAt(level, cp);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack slot = c.getItem(i);
                if (slot.isEmpty()) { c.setItem(i, stack); c.setChanged(); return; }
                if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                    int room = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                    slot.grow(room); stack.shrink(room); c.setChanged();
                    if (stack.isEmpty()) return;
                }
            }
        }
    }

    @Nullable private static Container containerAt(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) return null;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container c ? c : null;
    }

    @Nullable private static BlockPos resolveStepPosition(PlacedBuildingRecord building, IndustrialDefinition definition,
                                                           CitizenEntity entity, IndustrialDefinition.StepDefinition step) {

        if (!step.point().isBlank())
            return IndustrialControlBoxService.resolvePoint(building, definition, step.point(), entity.position());

        if (!step.positions().isEmpty()) {
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, step.positions());
            if (!resolved.isEmpty()) {
                Vec3 origin = entity.position();
                return resolved.stream()
                        .min(Comparator.comparingDouble(p -> origin.distanceToSqr(Vec3.atCenterOf(p))))
                        .orElse(resolved.getFirst());
            }
        }

        return getLookedAtBlock(entity);
    }

    private static BlockPos getLookedAtBlock(CitizenEntity entity) {
        Vec3 eyes = entity.getEyePosition();
        Vec3 look = entity.getLookAngle();
        for (double d = 0; d <= 4.0; d += 0.5) {
            Vec3 pos = eyes.add(look.scale(d));
            BlockPos bp = BlockPos.containing(pos);
            if (!entity.level().getBlockState(bp).isAir()) {
                return bp;
            }
        }

        BlockPos feet = entity.blockPosition();
        if (!entity.level().getBlockState(feet).isAir()) return feet;
        BlockPos above = feet.above();
        return above;
    }
}
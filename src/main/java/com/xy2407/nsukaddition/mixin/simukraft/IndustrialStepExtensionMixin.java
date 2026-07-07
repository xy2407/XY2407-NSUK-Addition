package com.xy2407.nsukaddition.mixin.simukraft;

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
import net.minecraft.network.chat.Component;
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

    private static void log(String msg) {
        try {
            var srv = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (srv != null) srv.getPlayerList().broadcastSystemMessage(
                    Component.literal("[IndustrialStep] " + msg), false);
        } catch (Exception ignored) {}
    }

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
            boolean ok = handleRightClick(level, building, definition, entity, step);
            cir.setReturnValue(ok ? StepResultAccess.progressed() : StepResultAccess.waitingRetry());
        } else if ("jump".equals(type)) {
            cir.setReturnValue(handleJump(level, building, definition, entity, step, gameTime));
        }
    }

    private static boolean handleRightClick(ServerLevel level, PlacedBuildingRecord building,
                                             IndustrialDefinition definition, CitizenEntity entity,
                                             IndustrialDefinition.StepDefinition step) {
        BlockPos target = resolveStepPosition(building, definition, entity, step);
        if (target == null) { log("right_click: resolvePosition=null, point=" + step.point() + " pos=" + step.positions()); return false; }
        log(String.format("right_click: target=%s item=%s block=%s npcPos=(%.1f,%.1f,%.1f)",
                target, step.item().isBlank() ? "HAND" : step.item(),
                level.getBlockState(target).getBlock().getDescriptionId(),
                entity.getX(), entity.getY(), entity.getZ()));

        if (step.swing()) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
        }
        BlockState state = level.getBlockState(target);

        if (!step.item().isBlank()) {
            return handleItemRightClick(level, building, definition, entity, step, target, state);
        }
        return tryInteractGeneric(level, entity, target, state);
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

        log(String.format("handleItemRightClick: item=%s count=%d target=%s",
                BuiltInRegistries.ITEM.getKey(taken.getItem()), taken.getCount(), pos.toShortString()));
        FakePlayer fake = prepareFakePlayer(level, entity, taken);
        Vec3 hitVec = Vec3.atCenterOf(pos);
        Direction face = Direction.getNearest(entity.getLookAngle().x, entity.getLookAngle().y, entity.getLookAngle().z);
        if (face == null) face = Direction.UP;
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
        ItemStack handBefore = fake.getItemInHand(InteractionHand.MAIN_HAND).copy();
        ItemInteractionResult result = state.useItemOn(fake.getItemInHand(InteractionHand.MAIN_HAND), level, fake, InteractionHand.MAIN_HAND, hit);
        ItemStack handAfter = fake.getItemInHand(InteractionHand.MAIN_HAND);
        log(String.format("useItemOn result=%s handBefore=%s handAfter=%s",
                result,
                handBefore.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(handBefore.getItem()),
                handAfter.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(handAfter.getItem())));

        if (!ItemStack.isSameItemSameComponents(handBefore, handAfter) && !handAfter.isEmpty()
                && handAfter.getItem() != targetItem) {
            log("hand changed! storing/syncing...");
            if (!step.output().isBlank()) {
                storeToContainer(level, building, definition, step, handAfter.copy());
                fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                entity.setItemInHand(InteractionHand.MAIN_HAND, handAfter.copy());
            }
        }
        return result.consumesAction() || result == ItemInteractionResult.SUCCESS;
    }

    private static Object handleJump(ServerLevel level, PlacedBuildingRecord building,
                                      IndustrialDefinition definition, CitizenEntity entity,
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
            entity.setPose(Pose.STANDING);
            return StepResultAccess.progressed();
        }

        if (state[1] == 0) {
            if (step.swing()) entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
            entity.setPose(Pose.STANDING);

            BlockPos jumpTarget = resolveStepPosition(building, definition, entity, step);
            if (jumpTarget != null && entity.position().distanceToSqr(Vec3.atBottomCenterOf(jumpTarget)) > 2.0) {
                entity.teleportTo(jumpTarget.getX() + 0.5, jumpTarget.getY(), jumpTarget.getZ() + 0.5);
            }
        }

        if (gameTime - state[2] >= 10 || state[1] == 0) {
            entity.getJumpControl().jump();
            state[1]++;
            state[2] = gameTime;
        }
        return StepResultAccess.waitingRetry();
    }

    private static boolean tryInteractGeneric(ServerLevel level, CitizenEntity entity, BlockPos pos, BlockState state) {
        ItemStack npcHand = entity.getItemInHand(InteractionHand.MAIN_HAND);
        log(String.format("tryInteractGeneric: pos=%s npcHand=%s face=%s",
                pos.toShortString(),
                npcHand.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(npcHand.getItem()),
                Direction.getNearest(entity.getLookAngle().x, entity.getLookAngle().y, entity.getLookAngle().z)));
        FakePlayer fake = prepareFakePlayer(level, entity, npcHand.copy());
        Vec3 hitVec = Vec3.atCenterOf(pos);
        Direction face = Direction.getNearest(entity.getLookAngle().x, entity.getLookAngle().y, entity.getLookAngle().z);
        if (face == null) face = Direction.UP;
        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);

        ItemInteractionResult itemResult = state.useItemOn(fake.getItemInHand(InteractionHand.MAIN_HAND), level, fake, InteractionHand.MAIN_HAND, hit);
        ItemStack handAfter = fake.getItemInHand(InteractionHand.MAIN_HAND);
        log(String.format("useItemOn result=%s handAfter=%s", itemResult,
                handAfter.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(handAfter.getItem())));
        if (itemResult.consumesAction() || itemResult == ItemInteractionResult.SUCCESS) {
            entity.setItemInHand(InteractionHand.MAIN_HAND, handAfter);
            return true;
        }
        log("useItemOn skipped, trying useWithoutItem...");
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

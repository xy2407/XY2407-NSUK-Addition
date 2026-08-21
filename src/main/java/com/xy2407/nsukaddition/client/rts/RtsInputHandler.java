package com.xy2407.nsukaddition.client.rts;

import com.xy2407.nsukaddition.common.network.rts.RtsInteractBlockPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsMountPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsMoveCommandPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** RTS 鼠标按键事件分发：左键单击/框选、中键旋转、右键命令移动。 */
@OnlyIn(Dist.CLIENT)
public final class RtsInputHandler {

    private RtsInputHandler() {
    }

    public static void onMouseButton(int button, int action) {
        if (button == 0) {
            if (action == GLFW.GLFW_PRESS) {
                double[] pos = RtsModeManager.getGuiScaledMouse();
                int hit = RtsSelectionRenderer.hitTestLockButton(pos[0], pos[1]);
                if (hit == 0) {
                    RtsModeManager.lockSelection();
                    return;
                } else if (hit == 1) {
                    RtsModeManager.unlockSelection();
                    return;
                }
                int form = RtsSelectionRenderer.hitTestFormationButton(pos[0], pos[1]);
                if (form >= 0) {
                    RtsModeManager.setFormation(RtsModeManager.RtsFormation.values()[form]);
                    return;
                }
                int view = RtsSelectionRenderer.hitTestViewButton(pos[0], pos[1]);
                if (view >= 0) {
                    RtsModeManager.setViewMode(switch (view) {
                        case 0 -> RtsModeManager.RtsViewMode.ISO_60;
                        case 1 -> RtsModeManager.RtsViewMode.ISO_45;
                        default -> RtsModeManager.RtsViewMode.FREE;
                    });
                    return;
                }
                RtsBuildingListHudLayer.Hit listHit = RtsBuildingListHudLayer.hitTest(pos[0], pos[1]);
                if (listHit != null) {
                    RtsBuildingListHudLayer.handleClick(listHit);
                    return;
                }
                RtsBuildingListHudLayer.handleClick(null);
                if (RtsBuildingPlacementManager.isMoveActive()) {
                    RtsBuildingPlacementManager.beginMoveDrag();
                    return;
                }
                if (RtsBuildingPlacementManager.beginPendingDrag(pos[0], pos[1])) {
                    RtsModeManager.onLeftMouseDown();
                    return;
                }
                moveClickStartX = pos[0];
                moveClickStartY = pos[1];
                RtsModeManager.onLeftMouseDown();
            } else if (action == GLFW.GLFW_RELEASE) {
                double[] pos = RtsModeManager.getGuiScaledMouse();
                if (RtsBuildingPlacementManager.isMoveActive()) {
                    RtsBuildingPlacementManager.endMoveDrag();
                    return;
                }
                if (RtsBuildingPlacementManager.isPendingDragging()) {
                    RtsModeManager.cancelLeftClick();
                    RtsBuildingPlacementManager.endPendingDrag();
                    return;
                }
                boolean shortClick = moveClickStartX >= 0
                        && Math.abs(pos[0] - moveClickStartX) < 5.0
                        && Math.abs(pos[1] - moveClickStartY) < 5.0;
                if (shortClick && RtsBuildingPlacementManager.tryStartMoveFromClick(moveClickStartX, moveClickStartY)) {
                    moveClickStartX = -1;
                    moveClickStartY = -1;
                    RtsModeManager.cancelLeftClick();
                    return;
                }
                moveClickStartX = -1;
                moveClickStartY = -1;
                RtsModeManager.onLeftMouseUp();
                RtsBuildingListHudLayer.setDraggingScrollbar(false);
            }
        } else if (button == 1) {
            if (action == GLFW.GLFW_PRESS) {
                if (RtsBuildingPlacementManager.isActive()) {
                    RtsBuildingPlacementManager.confirmPlace();
                } else if (!RtsBuildingPlacementManager.isMoveActive()) {
                    onRightClickCommand();
                }
            }
        } else if (button == 2) {
            if (action == GLFW.GLFW_PRESS) {
                RtsModeManager.onMiddleDown();
            } else if (action == GLFW.GLFW_RELEASE) {
                RtsModeManager.onMiddleUp();
            }
        }
    }

    public static void onTick() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (RtsBuildingPlacementManager.isActive() || RtsBuildingPlacementManager.isMoveActive()
                || RtsBuildingPlacementManager.hasPending()) {
            boolean rDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            if (rDown && !rKeyHeld) {
                if (RtsBuildingPlacementManager.isMoveActive()) {
                    RtsBuildingPlacementManager.rotateMove();
                } else if (RtsBuildingPlacementManager.isActive()) {
                    RtsBuildingPlacementManager.rotate();
                } else if (RtsBuildingPlacementManager.hasPending()) {
                    RtsBuildingPlacementManager.rotatePending();
                }
            }
            rKeyHeld = rDown;
        } else {
            rKeyHeld = false;
        }
        if (RtsModeManager.isActive() && RtsBuildingPlacementManager.hasPending()) {
            boolean cDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
            if (cDown && !cKeyHeld) {
                RtsBuildingPlacementManager.undoLastPlacement();
            }
            cKeyHeld = cDown;
        } else {
            cKeyHeld = false;
        }
        if (RtsModeManager.isActive()) {
            boolean enterDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS;
            if (enterDown && !enterKeyHeld) {
                if (RtsBuildingPlacementManager.isMoveActive()) {
                    RtsBuildingPlacementManager.confirmMove();
                } else if (RtsBuildingPlacementManager.hasPending()
                        && RtsPlacedBuildingCache.hasBuilderOrPlanner(RtsModeManager.getSelectedEntities())) {
                    RtsBuildingPlacementManager.confirmAll();
                }
            }
            enterKeyHeld = enterDown;
        } else {
            enterKeyHeld = false;
        }
    }

    private static boolean rKeyHeld;    private static boolean cKeyHeld;
    private static boolean enterKeyHeld;
    private static double moveClickStartX = -1;
    private static double moveClickStartY = -1;

    public static void onFrame() {
        if (!RtsModeManager.isActive()) return;
        if (Minecraft.getInstance().screen != null) return;
        RtsModeManager.onLeftMouseDrag();
        RtsModeManager.onMiddleDrag();
        RtsBuildingListHudLayer.updateScrollbarDrag((float) RtsModeManager.getGuiScaledMouse()[0]);
        RtsBuildingPlacementManager.tick();
        RtsBuildingPlacementManager.tickMove();
        RtsBuildingPlacementManager.tickPendingDrag();
    }

    private static void onRightClickCommand() {
        if (RtsModeManager.getSelectedEntities().isEmpty()) return;
        double[] pos = RtsModeManager.getGuiScaledMouse();

        Entity picked = RtsPicker.pickEntityAtScreen(pos[0], pos[1]);
        if (picked instanceof AbstractHorse horse) {
            UUID npcId = RtsModeManager.getSelectedEntities().iterator().next();
            if (horse.isVehicle() && horse.getFirstPassenger() != null
                    && horse.getFirstPassenger().getUUID().equals(npcId)) {
                PacketDistributor.sendToServer(new RtsMountPacket(npcId, horse.getUUID(), false));
                return;
            }
            if (!horse.isVehicle() && horse.isSaddled()) {
                PacketDistributor.sendToServer(new RtsMountPacket(npcId, horse.getUUID(), true));
                return;
            }
        }

        RtsPicker.BlockHit hit = RtsPicker.pickBlockHit(pos[0], pos[1]);
        if (hit == null) return;

        if (RtsModeManager.getSelectedEntities().size() == 1 && isInteractableBlock(hit.blockPos)) {
            UUID fakeId = RtsModeManager.getSelectedEntities().iterator().next();
            if (isFakePlayer(fakeId)) {
                PacketDistributor.sendToServer(new RtsInteractBlockPacket(fakeId, hit.blockPos, hit.direction, hit.location));
                return;
            }
        }

        RtsModeManager.setMoveTargets(new HashSet<>(RtsModeManager.getSelectedEntities()), hit.standPos);
        List<UUID> ids = new ArrayList<>(RtsModeManager.getSelectedEntities());
        PacketDistributor.sendToServer(new RtsMoveCommandPacket(ids, hit.standPos,
                RtsModeManager.getFormation().ordinal()));
    }

    private static boolean isInteractableBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) return false;
        BlockState state = level.getBlockState(pos);
        Block b = state.getBlock();
        if (b instanceof EntityBlock) {
            return true;
        }
        return b instanceof CraftingTableBlock || b instanceof EnchantingTableBlock
                || b instanceof AnvilBlock || b instanceof ButtonBlock || b instanceof LeverBlock
                || b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
                || b instanceof BedBlock || b instanceof NoteBlock || b instanceof JukeboxBlock
                || b instanceof ComposterBlock || b instanceof BellBlock;
    }

    private static boolean isFakePlayer(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity instanceof com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
            }
        }
        return false;
    }
}
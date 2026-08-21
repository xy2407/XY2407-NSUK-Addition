package com.xy2407.nsukaddition.common.rts.path;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 假人寻路(四面通道 A*，自研方案，替代移植的 HybridPathfinder)：
 * 范围 = 起终点差值 AABB，任意轴差低于 24 的边 +16 固定增加；
 * 两阶段扫描：第一次只收集空气/水等通道格（身体+头部可通行），第二次查脚下支撑方块得到可站立点；
 * 相邻四面通道 A*：仅 4 个水平方向展开，每方向匹配同层/上 1 格/下 1 格高度，并支持向下跳落(连续空洞直达落点，最多 20 格)；
 * 高度惩罚：向上 +1.1/格；向下 1 格(有踮脚) +1.2，向下 ≥2 格(无踮脚跳落) +2.4；
 * 方块规则：门/活版门/栅栏门可通行(铁门/铁活版门除外)，藤蔓/木梯/地毯/雪层/踏板可通行，细雪不可通行；
 * 栅栏(非栅栏门)格不可通行，但栅栏周围存在可通行格时，栅栏上方判为可通行道路(跨栅栏)。
 */
public final class FakePlayerPathfinder {

    private static final int PADDING = 16;
    private static final int UPWARD_RANGE = 8;
    private static final int DOWNWARD_RANGE = 20;
    private static final double COST_STEP_UP = 1.1;
    private static final double COST_STEP_DOWN_STEPPED = 1.2;
    private static final double COST_STEP_DOWN_DROP = 2.4;
    private static final double HEURISTIC_WEIGHT = 1.2;
    private static final int MAX_NODES = 200000;
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final double HALF_WIDTH = 0.31D;
    private static final double HEAD_CHECK_MIN_Y = 0.45D;
    private static final double HEAD_OCCUPY_HEIGHT = 0.8D;

    private FakePlayerPathfinder() {
    }

    public static List<BlockPos> findPath(ServerLevel level, BlockPos start, BlockPos end) {
        if (level == null || start == null || end == null) {
            return List.of();
        }
        int xPad = PADDING;
        int zPad = PADDING;
        int minX = Math.min(start.getX(), end.getX()) - xPad;
        int maxX = Math.max(start.getX(), end.getX()) + xPad;
        int minZ = Math.min(start.getZ(), end.getZ()) - zPad;
        int maxZ = Math.max(start.getZ(), end.getZ()) + zPad;
        int minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), end.getY()) - DOWNWARD_RANGE);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Math.max(start.getY(), end.getY()) + UPWARD_RANGE);

        Set<BlockPos> channel = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (isChannelClear(level, pos.set(x, y, z))) {
                        channel.add(pos.immutable());
                    }
                }
            }
        }
        if (channel.isEmpty()) {
            return List.of();
        }
        Set<BlockPos> passable = new HashSet<>(channel.size());
        for (BlockPos c : channel) {
            BlockState floor = level.getBlockState(c.below());
            if (isWalkableFloor(level, c.below(), floor)) {
                passable.add(c);
            } else if (isFenceLike(floor)) {
                for (Direction d : HORIZONTALS) {
                    if (channel.contains(c.relative(d))) {
                        passable.add(c);
                        break;
                    }
                }
            }
        }
        if (passable.isEmpty()) {
            return List.of();
        }
        BlockPos s = passable.contains(start) ? start : nearest(passable, start);
        BlockPos e = passable.contains(end) ? end : nearest(passable, end);
        if (s == null || e == null) {
            return List.of();
        }
        if (s.equals(e) || s.closerThan(e, 2)) {
            return List.of(e);
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        gScore.put(s, 0.0D);
        open.offer(new Node(s, 0.0D, dist(s, e)));
        int explored = 0;
        boolean found = false;
        while (!open.isEmpty() && explored < MAX_NODES) {
            Node cur = open.poll();
            if (closed.contains(cur.pos)) {
                continue;
            }
            closed.add(cur.pos);
            explored++;
            if (cur.pos.equals(e)) {
                found = true;
                break;
            }
            for (BlockPos nb : getAdjacent4(level, cur.pos, passable)) {
                if (closed.contains(nb)) {
                    continue;
                }
                double tentativeG = gScore.getOrDefault(cur.pos, Double.MAX_VALUE) + calcStepCost(cur.pos, nb);
                if (tentativeG < gScore.getOrDefault(nb, Double.MAX_VALUE)) {
                    parent.put(nb, cur.pos);
                    gScore.put(nb, tentativeG);
                    open.offer(new Node(nb, tentativeG, tentativeG + HEURISTIC_WEIGHT * dist(nb, e)));
                }
            }
        }
        if (!found) {
            return List.of();
        }
        LinkedList<BlockPos> path = new LinkedList<>();
        BlockPos cur = e;
        while (cur != null) {
            path.addFirst(cur);
            cur = parent.get(cur);
        }
        if (path.isEmpty() || !path.getFirst().equals(s)) {
            path.addFirst(s);
        }
        return path;
    }

    private static List<BlockPos> getAdjacent4(ServerLevel level, BlockPos pos, Set<BlockPos> area) {
        List<BlockPos> result = new ArrayList<>(12);
        for (Direction d : HORIZONTALS) {
            BlockPos base = pos.relative(d);
            if (area.contains(base)) {
                result.add(base);
            }
            if (area.contains(base.above())) {
                result.add(base.above());
            }
            if (area.contains(base.below())) {
                result.add(base.below());
            }
            BlockPos drop = base.below();
            while (drop.getY() >= base.getY() - DOWNWARD_RANGE && isChannelClear(level, drop)) {
                BlockState floorState = level.getBlockState(drop.below());
                if (isWalkableFloor(level, drop.below(), floorState)) {
                    if (drop.getY() < base.getY() - 1) {
                        result.add(drop);
                    }
                    break;
                }
                drop = drop.below();
            }
        }
        return result;
    }

    private static double calcStepCost(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        double cost = 1.0D;
        if (dy > 0) {
            cost += COST_STEP_UP * dy;
        } else if (dy < 0) {
            cost += (dy == -1 ? COST_STEP_DOWN_STEPPED : COST_STEP_DOWN_DROP) * Math.abs(dy);
        }
        return cost;
    }

    private static boolean isChannelClear(ServerLevel level, BlockPos pos) {
        return isBodyPassable(level, pos) && isHeadPassable(level, pos.above());
    }

    public static boolean isBodyPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.getFluidState().is(Fluids.WATER)) {
            return true;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        Block b = state.getBlock();
        if (b == Blocks.IRON_DOOR) {
            return false;
        }
        if (b == Blocks.IRON_TRAPDOOR) {
            return false;
        }
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) {
            return true;
        }
        if (b instanceof LadderBlock || b instanceof VineBlock) {
            return true;
        }
        if (b instanceof CarpetBlock || b instanceof SnowLayerBlock || b instanceof BasePressurePlateBlock) {
            return true;
        }
        return clearsBodySlice(state.getCollisionShape(level, pos), 0.0D, 1.0D);
    }

    private static boolean isHeadPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.getFluidState().is(Fluids.WATER)) {
            return true;
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        Block b = state.getBlock();
        if (b == Blocks.IRON_DOOR) {
            return false;
        }
        if (b == Blocks.IRON_TRAPDOOR) {
            return false;
        }
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) {
            return true;
        }
        return clearsBodySlice(state.getCollisionShape(level, pos), HEAD_CHECK_MIN_Y, HEAD_OCCUPY_HEIGHT);
    }

    private static boolean clearsBodySlice(VoxelShape shape, double localMinY, double localMaxY) {
        if (shape.isEmpty()) {
            return true;
        }
        double minX = 0.5D - HALF_WIDTH;
        double maxX = 0.5D + HALF_WIDTH;
        double minZ = 0.5D - HALF_WIDTH;
        double maxZ = 0.5D + HALF_WIDTH;
        for (net.minecraft.world.phys.AABB box : shape.toAabbs()) {
            if (box.maxX > minX && box.minX < maxX
                    && box.maxY > localMinY && box.minY < localMaxY
                    && box.maxZ > minZ && box.minZ < maxZ) {
                return false;
            }
        }
        return true;
    }

    public static boolean isWalkableFloor(ServerLevel level, BlockPos floorPos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        Block b = state.getBlock();
        if (b instanceof StairBlock || b instanceof SlabBlock) {
            return true;
        }
        if (b instanceof FenceGateBlock) {
            return true;
        }
        if (b instanceof BedBlock) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, floorPos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.8D;
    }

    public static boolean isFenceLike(BlockState state) {
        return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(Blocks.IRON_BARS);
    }

    private static double dist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static BlockPos nearest(Set<BlockPos> set, BlockPos target) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : set) {
            double d = p.distSqr(target);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static final class Node {
        final BlockPos pos;
        final double g;
        final double f;

        Node(BlockPos pos, double g, double f) {
            this.pos = pos;
            this.g = g;
            this.f = f;
        }
    }
}

package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.rts.path.SableStructureReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 坐骑专用格子 A* 寻路：按坐骑碰撞箱自动适配尺寸。
 * 高度=需连续放行的格数(马 1.6 → 2 格)；宽度≥1.0 视为宽坐骑，要求移动方向两侧至少一侧有空间(近似两格宽通道)。
 * 较优即可：主世界 + Sable 物理结构格子通行判定(门/栅栏门/台阶/梯子/薄块/水可通行)，垂直仅允许 ±1 格。
 */
public final class MountPathfinder {

    private static final int PADDING = 16;
    private static final int UPWARD_RANGE = 8;
    private static final int DOWNWARD_RANGE = 20;
    private static final double COST_STEP_UP = 1.2;
    private static final double COST_STEP_DOWN_STEPPED = 1.1;
    private static final double COST_STEP_DOWN_DROP = 1.8;
    private static final double HEURISTIC_WEIGHT = 1.4;
    private static final int MAX_NODES = 200000;
    private static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Direction[] VERTICALS = {Direction.UP, Direction.DOWN};

    private MountPathfinder() {
    }

    public static List<BlockPos> findPath(ServerLevel level, Mob mount, BlockPos start, BlockPos end) {
        int heightCells = Math.max(1, (int) Math.ceil(mount.getBbHeight()));
        boolean wide = mount.getBbWidth() >= 1.0D;

        int minX = Math.min(start.getX(), end.getX()) - PADDING;
        int maxX = Math.max(start.getX(), end.getX()) + PADDING;
        int minZ = Math.min(start.getZ(), end.getZ()) - PADDING;
        int maxZ = Math.max(start.getZ(), end.getZ()) + PADDING;
        int minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), end.getY()) - DOWNWARD_RANGE);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, Math.max(start.getY(), end.getY()) + UPWARD_RANGE);

        boolean[][][] passable = new boolean[maxX - minX + 1][maxY - minY + 1][maxZ - minZ + 1];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    passable[x - minX][y - minY][z - minZ] = isTraversable(level, p, heightCells);
                }
            }
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        gScore.put(start, 0D);
        open.add(new Node(start, 0D, heuristic(start, end)));
        int explored = 0;

        while (!open.isEmpty() && explored < MAX_NODES) {
            Node cur = open.poll();
            explored++;
            if (cur.pos.equals(end)) {
                return reconstruct(parent, cur.pos);
            }
            for (BlockPos nb : neighbors(level, cur.pos, passable, minX, minY, minZ, heightCells, wide)) {
                double g = gScore.getOrDefault(cur.pos, 0D) + stepCost(cur.pos, nb);
                if (g < gScore.getOrDefault(nb, Double.MAX_VALUE)) {
                    gScore.put(nb, g);
                    parent.put(nb, cur.pos);
                    open.add(new Node(nb, g, g + heuristic(nb, end) * HEURISTIC_WEIGHT));
                }
            }
        }
        return null;
    }

    private static boolean isTraversable(ServerLevel level, BlockPos pos, int heightCells) {
        for (int i = 0; i < heightCells; i++) {
            BlockPos p = pos.above(i);
            BlockState s = level.getBlockState(p);
            if (isSolidBlocker(s)) return false;
            if (s.isAir()) {
                BlockState sub = SableStructureReader.getBlockStateAt(level, p);
                if (sub != null && isSolidBlocker(sub)) return false;
            }
        }
        BlockPos below = pos.below();
        BlockState bs = level.getBlockState(below);
        if (bs.isAir() && SableStructureReader.getBlockStateAt(level, below) == null) {
            return !bs.getFluidState().isEmpty();
        }
        return true;
    }

    private static boolean isSolidBlocker(BlockState s) {
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty()) {
            return !s.getFluidState().is(Fluids.WATER);
        }
        if (s.getBlock() instanceof DoorBlock) return false;
        if (s.getBlock() instanceof FenceGateBlock) return false;
        if (s.getBlock() instanceof StairBlock) return false;
        if (s.getBlock() instanceof LadderBlock) return false;
        if (s.getBlock() instanceof PressurePlateBlock) return false;
        if (s.getBlock() instanceof SnowLayerBlock) return false;
        if (s.is(BlockTags.WOOL_CARPETS)) return false;
        if (s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.FLOWERS)) return false;
        return true;
    }

    private static List<BlockPos> neighbors(ServerLevel level, BlockPos cur, boolean[][][] passable,
                                            int minX, int minY, int minZ, int heightCells, boolean wide) {
        List<BlockPos> result = new ArrayList<>(14);
        for (Direction d : HORIZONTALS) {
            BlockPos nb = cur.relative(d);
            if (checkPassable(passable, nb, minX, minY, minZ)) {
                if (!wide || hasSideSpace(cur, d, passable, minX, minY, minZ)) {
                    result.add(nb);
                }
            }
            for (Direction v : VERTICALS) {
                BlockPos nbv = cur.relative(d).relative(v);
                if (checkPassable(passable, nbv, minX, minY, minZ)) {
                    result.add(nbv);
                }
            }
        }
        for (Direction v : VERTICALS) {
            BlockPos nbv = cur.relative(v);
            if (checkPassable(passable, nbv, minX, minY, minZ)) {
                result.add(nbv);
            }
        }
        return result;
    }

    private static boolean hasSideSpace(BlockPos cur, Direction moveDir, boolean[][][] passable,
                                        int minX, int minY, int minZ) {
        Direction left = moveDir.getClockWise();
        Direction right = moveDir.getCounterClockWise();
        BlockPos l = cur.relative(left);
        BlockPos r = cur.relative(right);
        return checkPassable(passable, l, minX, minY, minZ) || checkPassable(passable, r, minX, minY, minZ);
    }

    private static boolean checkPassable(boolean[][][] passable, BlockPos p, int minX, int minY, int minZ) {
        int x = p.getX() - minX, y = p.getY() - minY, z = p.getZ() - minZ;
        if (x < 0 || y < 0 || z < 0 || x >= passable.length || y >= passable[0].length || z >= passable[0][0].length) {
            return false;
        }
        return passable[x][y][z];
    }

    private static double stepCost(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        if (dy > 0) return COST_STEP_UP;
        if (dy == -1) return COST_STEP_DOWN_STEPPED;
        if (dy < 0) return COST_STEP_DOWN_DROP;
        return 1.0D;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) + Math.abs(a.getY() - b.getY());
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> parent, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cur = end;
        while (cur != null) {
            path.add(0, cur);
            cur = parent.get(cur);
        }
        if (!path.isEmpty()) {
            path.remove(0);
        }
        return path;
    }

    private record Node(BlockPos pos, double g, double f) {
    }
}

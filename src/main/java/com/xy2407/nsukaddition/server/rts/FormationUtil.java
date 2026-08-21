package com.xy2407.nsukaddition.server.rts;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** RTS 阵型站位偏移生成：直线/方形/三角形。人数不足时按序优先填充靠前位置(择优填充)，生成 count 个偏移点。 */
public final class FormationUtil {

    public enum Formation {
        NONE(0), LINE(1), SQUARE(2), TRIANGLE(3);

        public final int id;

        Formation(int id) {
            this.id = id;
        }

        public static Formation fromId(int id) {
            for (Formation f : values()) {
                if (f.id == id) return f;
            }
            return NONE;
        }
    }

    /** 相邻站位间距(格)：每格方块仅站一人，偏移半格对齐方块中心。 */
    private static final double SPACING = 2.0D;

    private FormationUtil() {
    }

    /** 生成 count 个站位偏移(相对阵型锚点，水平面)。 */
    public static List<Vec3> generateOffsets(int count, Formation type) {
        switch (type) {
            case LINE:
                return line(count);
            case SQUARE:
                return square(count);
            case TRIANGLE:
                return triangle(count);
            default:
                List<Vec3> all = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    all.add(Vec3.ZERO);
                }
                return all;
        }
    }

    /** 直线：一排沿 x 轴居中展开。 */
    private static List<Vec3> line(int count) {
        List<Vec3> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double x = (i - (count - 1) / 2.0D) * SPACING;
            list.add(new Vec3(x, 0, 0));
        }
        return list;
    }

    /** 方形：side = ceil(sqrt(count))，按行填充(前几行优先填满)。 */
    private static List<Vec3> square(int count) {
        List<Vec3> list = new ArrayList<>();
        int side = (int) Math.ceil(Math.sqrt(count));
        int filled = 0;
        for (int row = 0; row < side && filled < count; row++) {
            for (int col = 0; col < side && filled < count; col++) {
                double x = (col - (side - 1) / 2.0D) * SPACING;
                double z = (row - (side - 1) / 2.0D) * SPACING;
                list.add(new Vec3(x, 0, z));
                filled++;
            }
        }
        return list;
    }

    /** 三角形：第 n 层 n 个点(1,2,3...)，每层居中排开，层间向 z 纵深展开。 */
    private static List<Vec3> triangle(int count) {
        List<Vec3> list = new ArrayList<>();
        int layer = 1;
        while (list.size() < count) {
            int inLayer = Math.min(layer, count - list.size());
            for (int i = 0; i < inLayer; i++) {
                double x = (i - (inLayer - 1) / 2.0D) * SPACING;
                double z = (layer - 1) * SPACING;
                list.add(new Vec3(x, 0, z));
            }
            layer++;
        }
        return list;
    }
}

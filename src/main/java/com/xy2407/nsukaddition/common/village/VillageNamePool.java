package com.xy2407.nsukaddition.common.village;

import java.util.List;
import java.util.Random;

/** 村庄名称库，提供随机城市名生成。 */
public final class VillageNamePool {

    private static final List<String> PREFIXES = List.of(
            "晨曦", "暮光", "星落", "云隐", "风吟", "霜叶", "翠微", "碧波",
            "幽谷", "青崖", "枫林", "银杏", "白鹭", "红石", "蓝烟", "紫荆",
            "金穗", "银月", "铁砧", "琥珀", "松涛", "竹影", "梅岭", "兰亭",
            "鹤鸣", "鹿溪", "凤栖", "龙脊", "虎啸", "鹰巢", "燕归", "鹤望",
            "桃溪", "柳岸", "荷塘", "莲池", "兰谷", "菊坡", "梅涧", "杏雨"
    );

    private static final List<String> SUFFIXES = List.of(
            "镇", "村", "庄", "寨", "堡", "坞", "屯", "营",
            "港", "渡", "驿", "坊", "阁", "庐", "苑", "居"
    );

    private static final List<String> FULL_NAMES = List.of(
            "望乡", "归田园", "桃源", "稻香", "烟雨", "落霞", "朝阳", "清河",
            "长风", "明月", "南山", "北原", "东山", "西岭", "春华", "秋实",
            "古渡", "新桥", "老泉", "青石", "白马", "金鸡", "玉龙", "凤凰",
            "听雨", "观潮", "映雪", "临风", "枕流", "漱石", "采菊", "折柳"
    );

    private VillageNamePool() {}

    public static String generate(Random random) {
        if (random.nextInt(3) < 2) {
            String prefix = PREFIXES.get(random.nextInt(PREFIXES.size()));
            String suffix = SUFFIXES.get(random.nextInt(SUFFIXES.size()));
            return prefix + suffix;
        }
        return FULL_NAMES.get(random.nextInt(FULL_NAMES.size()));
    }
}

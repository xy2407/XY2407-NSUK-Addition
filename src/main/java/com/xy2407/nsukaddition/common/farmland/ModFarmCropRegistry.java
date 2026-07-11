package com.xy2407.nsukaddition.common.farmland;

import com.xy2407.nsukaddition.common.util.EnumExtender;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 注册模组添加的额外农作物到农场系统枚举中，并维护 ID→实例 备份映射防止反序列化丢失。 */
public final class ModFarmCropRegistry {

    private static final Map<String, FarmCrop> CROP_MAP = new ConcurrentHashMap<>();

    private ModFarmCropRegistry() {
    }

    public static Map<String, FarmCrop> getCropMap() {
        return Collections.unmodifiableMap(CROP_MAP);
    }

    public static FarmCrop findById(String id) {
        if (id == null || id.isBlank()) return null;
        return CROP_MAP.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public static void registerAll() {
        register("culturaldelights_cucumber", "culturaldelights:cucumber_seeds", "culturaldelights:cucumbers", FarmCrop.Layout.FULL, null);
        register("culturaldelights_eggplant", "culturaldelights:eggplant_seeds", "culturaldelights:eggplants", FarmCrop.Layout.FULL, null);
        register("culturaldelights_corn", "culturaldelights:corn_kernels", "culturaldelights:corn", FarmCrop.Layout.FULL, null);

        register("farm_and_charm_barley", "farm_and_charm:barley_seeds", "farm_and_charm:barley_crop", FarmCrop.Layout.FULL, null);
        register("farm_and_charm_oat", "farm_and_charm:oat_seeds", "farm_and_charm:oat_crop", FarmCrop.Layout.FULL, null);
        register("farm_and_charm_strawberry", "farm_and_charm:strawberry_seeds", "farm_and_charm:strawberry_crop", FarmCrop.Layout.FULL, null);

        register("farmersdelight_cabbage", "farmersdelight:cabbage_seeds", "farmersdelight:cabbages", FarmCrop.Layout.FULL, null);
        register("farmersdelight_onion", "farmersdelight:onion", "farmersdelight:onions", FarmCrop.Layout.FULL, null);

        register("farmersdelight_rice", "farmersdelight:rice", "farmersdelight:rice", FarmCrop.Layout.FULL, "farmersdelight:rice_panicles");

        register("kaleidoscope_cookery_tomato", "kaleidoscope_cookery:tomato_seed", "kaleidoscope_cookery:tomato_crop", FarmCrop.Layout.FULL, null);
        register("kaleidoscope_cookery_chili", "kaleidoscope_cookery:chili_seed", "kaleidoscope_cookery:chili_crop", FarmCrop.Layout.FULL, null);
        register("kaleidoscope_cookery_lettuce", "kaleidoscope_cookery:lettuce_seed", "kaleidoscope_cookery:lettuce_crop", FarmCrop.Layout.FULL, null);

        register("vinery_red_grape",            "vinery:red_grape_seeds",          "vinery:red_grape_bush",           FarmCrop.Layout.FULL, null);
        register("vinery_white_grape",          "vinery:white_grape_seeds",        "vinery:white_grape_bush",         FarmCrop.Layout.FULL, null);
        register("vinery_savanna_grape_red",    "vinery:savanna_grape_seeds_red",  "vinery:savanna_grape_bush_red",   FarmCrop.Layout.FULL, null);
        register("vinery_savanna_grape_white",  "vinery:savanna_grape_seeds_white","vinery:savanna_grape_bush_white", FarmCrop.Layout.FULL, null);
        register("vinery_taiga_grape_red",      "vinery:taiga_grape_seeds_red",    "vinery:taiga_grape_bush_red",     FarmCrop.Layout.FULL, null);
        register("vinery_taiga_grape_white",    "vinery:taiga_grape_seeds_white",  "vinery:taiga_grape_bush_white",   FarmCrop.Layout.FULL, null);

        register("kt_grape",      "kaleidoscope_tavern:grapevine", "kaleidoscope_tavern:grape_crop",      FarmCrop.Layout.FULL, null);
        register("kt_ice_grape",  "kaleidoscope_tavern:grapevine", "kaleidoscope_tavern:ice_grape_crop",  FarmCrop.Layout.FULL, null);
        register("kt_gold_grape", "kaleidoscope_tavern:grapevine", "kaleidoscope_tavern:gold_grape_crop", FarmCrop.Layout.FULL, null);

    }

    private static void register(String cropId, String seedItemId, String plantBlockId,
                                  FarmCrop.Layout layout, String produceBlockId) {
        ResourceLocation seedId = ResourceLocation.tryParse(seedItemId);
        ResourceLocation plantId = ResourceLocation.tryParse(plantBlockId);
        if (seedId == null || plantId == null) {
            return;
        }

        if (!BuiltInRegistries.ITEM.containsKey(seedId) || !BuiltInRegistries.BLOCK.containsKey(plantId)) {
            return;
        }
        Item seed = BuiltInRegistries.ITEM.get(seedId);
        Block plantBlock = BuiltInRegistries.BLOCK.get(plantId);
        Block produceBlock = null;
        if (produceBlockId != null) {
            ResourceLocation produceId = ResourceLocation.tryParse(produceBlockId);
            if (produceId != null && BuiltInRegistries.BLOCK.containsKey(produceId)) {
                produceBlock = BuiltInRegistries.BLOCK.get(produceId);
            }
        }

        FarmCrop instance = EnumExtender.addEnumConstant(FarmCrop.class, cropId,
                new EnumExtender.FieldValue("seed", seed),
                new EnumExtender.FieldValue("plantBlock", plantBlock),
                new EnumExtender.FieldValue("layout", layout),
                new EnumExtender.FieldValue("produceBlock", produceBlock)
        );

        if (instance != null) {
            CROP_MAP.put(cropId.toLowerCase(java.util.Locale.ROOT), instance);
        }
    }
}

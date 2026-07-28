package com.xy2407.nsukaddition.common.compat;

import com.chinaex123.letfishlove.capabilities.FishBreedingCap;
import com.chinaex123.letfishlove.capabilities.FishBreedingCapAttacher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Aquaculture模组鱼类的LetFishLove繁殖能力兼容注册。 */
public final class AquacultureFishCompat {

    private static final String[] AQUACULTURE_FISH_IDS = {
            "aquaculture:atlantic_cod", "aquaculture:blackfish",
            "aquaculture:pacific_halibut", "aquaculture:atlantic_halibut",
            "aquaculture:atlantic_herring", "aquaculture:pink_salmon",
            "aquaculture:pollock", "aquaculture:rainbow_trout",
            "aquaculture:bayad", "aquaculture:boulti",
            "aquaculture:capitaine", "aquaculture:synodontis",
            "aquaculture:smallmouth_bass", "aquaculture:bluegill",
            "aquaculture:brown_trout", "aquaculture:carp",
            "aquaculture:catfish", "aquaculture:gar",
            "aquaculture:minnow", "aquaculture:muskellunge",
            "aquaculture:perch", "aquaculture:arapaima",
            "aquaculture:piranha", "aquaculture:tambaqui",
            "aquaculture:brown_shrooma", "aquaculture:red_shrooma",
            "aquaculture:jellyfish", "aquaculture:red_grouper",
            "aquaculture:tuna"
    };

    private AquacultureFishCompat() {}

    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (!ModList.get().isLoaded("letfishlove") || !ModList.get().isLoaded("aquaculture")) {
            return;
        }

        FishBreedingCapAttacher.registerModFishCapabilities(event, AQUACULTURE_FISH_IDS);
    }
}

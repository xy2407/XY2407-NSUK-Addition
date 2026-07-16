package com.xy2407.nsukaddition.common.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.*;

/** 建材类别注册表，维护物品ID到类别的映射。 */
public final class MaterialCategoryRegistry {

    public static final String OTHER_KEY = "other";

    private static final Map<String, MaterialCategory> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> ITEM_TO_CATEGORY = new HashMap<>();

    static {
        registerWood();
        registerStone();
        registerBrick();
        registerSand();
        registerPrismarine();
        registerQuartz();
        registerWool();
        registerTerracotta();
        registerConcrete();
        registerGlass();
        registerLeaves();
        registerLighting();
    }

    private MaterialCategoryRegistry() {
    }

    private static void register(String key, String displayName, String... itemIds) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(itemIds));
        MaterialCategory category = new MaterialCategory(key, displayName, set);
        CATEGORIES.put(key, category);
        for (String id : set) {
            ITEM_TO_CATEGORY.put(id, key);
        }
    }

    public static MaterialCategory get(String key) {
        return CATEGORIES.get(key);
    }

    public static String getCategoryKey(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        return ITEM_TO_CATEGORY.get(itemId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static String getCategoryKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? null : getCategoryKey(id.toString());
    }

    public static String getCategoryKey(Block block) {
        if (block == null) {
            return null;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? null : getCategoryKey(id.toString());
    }

    public static Collection<MaterialCategory> getAll() {
        return Collections.unmodifiableCollection(CATEGORIES.values());
    }

    public static Set<String> getAllKeys() {
        return Collections.unmodifiableSet(CATEGORIES.keySet());
    }

    public static boolean isKnown(String itemId) {
        return getCategoryKey(itemId) != null;
    }

    public static ItemStack getDisplayStack(String categoryKey) {
        MaterialCategory category = get(categoryKey);
        if (category == null) {
            return ItemStack.EMPTY;
        }
        for (String itemId : category.itemIds()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item);
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null) {
                Item blockItem = block.asItem();
                if (blockItem != null && blockItem != Items.AIR) {
                    return new ItemStack(blockItem);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void registerWood() {
        register("wood", "木质材料",

                "minecraft:oak_log", "minecraft:oak_wood", "minecraft:stripped_oak_log", "minecraft:stripped_oak_wood",
                "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:oak_slab", "minecraft:oak_fence",
                "minecraft:oak_fence_gate", "minecraft:oak_door", "minecraft:oak_trapdoor", "minecraft:oak_pressure_plate",
                "minecraft:oak_button",

                "minecraft:spruce_log", "minecraft:spruce_wood", "minecraft:stripped_spruce_log", "minecraft:stripped_spruce_wood",
                "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:spruce_slab", "minecraft:spruce_fence",
                "minecraft:spruce_fence_gate", "minecraft:spruce_door", "minecraft:spruce_trapdoor", "minecraft:spruce_pressure_plate",
                "minecraft:spruce_button",

                "minecraft:birch_log", "minecraft:birch_wood", "minecraft:stripped_birch_log", "minecraft:stripped_birch_wood",
                "minecraft:birch_planks", "minecraft:birch_stairs", "minecraft:birch_slab", "minecraft:birch_fence",
                "minecraft:birch_fence_gate", "minecraft:birch_door", "minecraft:birch_trapdoor", "minecraft:birch_pressure_plate",
                "minecraft:birch_button",

                "minecraft:jungle_log", "minecraft:jungle_wood", "minecraft:stripped_jungle_log", "minecraft:stripped_jungle_wood",
                "minecraft:jungle_planks", "minecraft:jungle_stairs", "minecraft:jungle_slab", "minecraft:jungle_fence",
                "minecraft:jungle_fence_gate", "minecraft:jungle_door", "minecraft:jungle_trapdoor", "minecraft:jungle_pressure_plate",
                "minecraft:jungle_button",

                "minecraft:acacia_log", "minecraft:acacia_wood", "minecraft:stripped_acacia_log", "minecraft:stripped_acacia_wood",
                "minecraft:acacia_planks", "minecraft:acacia_stairs", "minecraft:acacia_slab", "minecraft:acacia_fence",
                "minecraft:acacia_fence_gate", "minecraft:acacia_door", "minecraft:acacia_trapdoor", "minecraft:acacia_pressure_plate",
                "minecraft:acacia_button",

                "minecraft:dark_oak_log", "minecraft:dark_oak_wood", "minecraft:stripped_dark_oak_log", "minecraft:stripped_dark_oak_wood",
                "minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:dark_oak_fence",
                "minecraft:dark_oak_fence_gate", "minecraft:dark_oak_door", "minecraft:dark_oak_trapdoor", "minecraft:dark_oak_pressure_plate",
                "minecraft:dark_oak_button",

                "minecraft:mangrove_log", "minecraft:mangrove_wood", "minecraft:stripped_mangrove_log", "minecraft:stripped_mangrove_wood",
                "minecraft:mangrove_planks", "minecraft:mangrove_stairs", "minecraft:mangrove_slab", "minecraft:mangrove_fence",
                "minecraft:mangrove_fence_gate", "minecraft:mangrove_door", "minecraft:mangrove_trapdoor", "minecraft:mangrove_pressure_plate",
                "minecraft:mangrove_button",

                "minecraft:cherry_log", "minecraft:cherry_wood", "minecraft:stripped_cherry_log", "minecraft:stripped_cherry_wood",
                "minecraft:cherry_planks", "minecraft:cherry_stairs", "minecraft:cherry_slab", "minecraft:cherry_fence",
                "minecraft:cherry_fence_gate", "minecraft:cherry_door", "minecraft:cherry_trapdoor", "minecraft:cherry_pressure_plate",
                "minecraft:cherry_button",

                "minecraft:bamboo_block", "minecraft:stripped_bamboo_block", "minecraft:bamboo_planks", "minecraft:bamboo_mosaic",
                "minecraft:bamboo_stairs", "minecraft:bamboo_mosaic_stairs", "minecraft:bamboo_slab", "minecraft:bamboo_mosaic_slab",
                "minecraft:bamboo_fence", "minecraft:bamboo_fence_gate", "minecraft:bamboo_door", "minecraft:bamboo_trapdoor",
                "minecraft:bamboo_pressure_plate", "minecraft:bamboo_button",

                "minecraft:crimson_stem", "minecraft:crimson_hyphae", "minecraft:stripped_crimson_stem", "minecraft:stripped_crimson_hyphae",
                "minecraft:crimson_planks", "minecraft:crimson_stairs", "minecraft:crimson_slab", "minecraft:crimson_fence",
                "minecraft:crimson_fence_gate", "minecraft:crimson_door", "minecraft:crimson_trapdoor", "minecraft:crimson_pressure_plate",
                "minecraft:crimson_button",
                "minecraft:warped_stem", "minecraft:warped_hyphae", "minecraft:stripped_warped_stem", "minecraft:stripped_warped_hyphae",
                "minecraft:warped_planks", "minecraft:warped_stairs", "minecraft:warped_slab", "minecraft:warped_fence",
                "minecraft:warped_fence_gate", "minecraft:warped_door", "minecraft:warped_trapdoor", "minecraft:warped_pressure_plate",
                "minecraft:warped_button",

                "beachparty:palm_log", "beachparty:palm_wood", "beachparty:stripped_palm_log", "beachparty:stripped_palm_wood",
                "beachparty:palm_floorboard", "beachparty:palm_planks", "beachparty:palm_stairs", "beachparty:palm_slab",
                "beachparty:palm_fence", "beachparty:palm_fence_gate", "beachparty:palm_door", "beachparty:palm_trapdoor",
                "beachparty:palm_pressure_plate", "beachparty:palm_button",

                "culturaldelights:avocado_log", "culturaldelights:avocado_wood",

                "meadow:alpine_birch_log",
                "meadow:pine_log", "meadow:stripped_pine_log", "meadow:pine_wood", "meadow:stripped_pine_wood",
                "meadow:pine_planks", "meadow:pine_stairs", "meadow:pine_slab",
                "meadow:reclaimed_pine_planks", "meadow:reclaimed_pine_stairs", "meadow:reclaimed_pine_slab",
                "meadow:pine_fence", "meadow:pine_fence_gate", "meadow:pine_door", "meadow:pine_trapdoor",
                "meadow:reclaimed_pine_door", "meadow:reclaimed_pine_trapdoor",
                "meadow:pine_pressure_plate", "meadow:pine_button",

                "vinery:dark_cherry_log", "vinery:dark_cherry_wood", "vinery:stripped_dark_cherry_log", "vinery:stripped_dark_cherry_wood",
                "vinery:dark_cherry_beam", "vinery:dark_cherry_planks", "vinery:dark_cherry_floorboard",
                "vinery:dark_cherry_stairs", "vinery:dark_cherry_slab", "vinery:dark_cherry_fence",
                "vinery:dark_cherry_fence_gate", "vinery:dark_cherry_door", "vinery:dark_cherry_trapdoor",
                "vinery:dark_cherry_pressure_plate", "vinery:dark_cherry_button",

                "mysticsbiomes:strawberry_log", "mysticsbiomes:strawberry_wood", "mysticsbiomes:stripped_strawberry_log",
                "mysticsbiomes:stripped_strawberry_wood", "mysticsbiomes:strawberry_planks", "mysticsbiomes:strawberry_stairs",
                "mysticsbiomes:strawberry_slab", "mysticsbiomes:strawberry_fence", "mysticsbiomes:strawberry_fence_gate",
                "mysticsbiomes:strawberry_button", "mysticsbiomes:strawberry_pressure_plate", "mysticsbiomes:strawberry_trapdoor",
                "mysticsbiomes:strawberry_door",
                "mysticsbiomes:black_cherry_log", "mysticsbiomes:black_cherry_wood", "mysticsbiomes:stripped_black_cherry_log",
                "mysticsbiomes:stripped_black_cherry_wood", "mysticsbiomes:black_cherry_planks", "mysticsbiomes:black_cherry_stairs",
                "mysticsbiomes:black_cherry_slab", "mysticsbiomes:black_cherry_fence", "mysticsbiomes:black_cherry_fence_gate",
                "mysticsbiomes:black_cherry_button", "mysticsbiomes:black_cherry_pressure_plate", "mysticsbiomes:black_cherry_trapdoor",
                "mysticsbiomes:black_cherry_door",
                "mysticsbiomes:lavender_log", "mysticsbiomes:lavender_wood", "mysticsbiomes:stripped_lavender_log",
                "mysticsbiomes:stripped_lavender_wood", "mysticsbiomes:lavender_planks", "mysticsbiomes:lavender_stairs",
                "mysticsbiomes:lavender_slab", "mysticsbiomes:lavender_fence", "mysticsbiomes:lavender_fence_gate",
                "mysticsbiomes:lavender_button", "mysticsbiomes:lavender_pressure_plate", "mysticsbiomes:lavender_trapdoor",
                "mysticsbiomes:lavender_door",
                "mysticsbiomes:peach_log", "mysticsbiomes:peach_wood", "mysticsbiomes:stripped_peach_log",
                "mysticsbiomes:stripped_peach_wood", "mysticsbiomes:peach_planks", "mysticsbiomes:peach_stairs",
                "mysticsbiomes:peach_slab", "mysticsbiomes:peach_fence", "mysticsbiomes:peach_fence_gate",
                "mysticsbiomes:peach_button", "mysticsbiomes:peach_pressure_plate", "mysticsbiomes:peach_trapdoor",
                "mysticsbiomes:peach_door",
                "mysticsbiomes:maple_log", "mysticsbiomes:maple_wood", "mysticsbiomes:white_maple_log", "mysticsbiomes:white_maple_wood",
                "mysticsbiomes:stripped_maple_log", "mysticsbiomes:stripped_maple_wood", "mysticsbiomes:maple_planks",
                "mysticsbiomes:maple_stairs", "mysticsbiomes:maple_slab", "mysticsbiomes:maple_fence",
                "mysticsbiomes:maple_fence_gate", "mysticsbiomes:maple_button", "mysticsbiomes:maple_pressure_plate",
                "mysticsbiomes:maple_trapdoor", "mysticsbiomes:maple_door",
                "mysticsbiomes:spring_bamboo_block", "mysticsbiomes:stripped_spring_bamboo_block", "mysticsbiomes:spring_planks",
                "mysticsbiomes:spring_mosaic", "mysticsbiomes:spring_stairs", "mysticsbiomes:spring_mosaic_stairs",
                "mysticsbiomes:spring_slab", "mysticsbiomes:spring_mosaic_slab", "mysticsbiomes:spring_fence",
                "mysticsbiomes:spring_fence_gate", "mysticsbiomes:spring_button", "mysticsbiomes:spring_pressure_plate",
                "mysticsbiomes:spring_trapdoor", "mysticsbiomes:spring_door",
                "mysticsbiomes:sea_foam_log", "mysticsbiomes:sea_foam_wood", "mysticsbiomes:stripped_sea_foam_log",
                "mysticsbiomes:stripped_sea_foam_wood", "mysticsbiomes:sea_foam_planks", "mysticsbiomes:sea_foam_stairs",
                "mysticsbiomes:sea_foam_slab", "mysticsbiomes:sea_foam_fence", "mysticsbiomes:sea_foam_fence_gate",
                "mysticsbiomes:sea_foam_button", "mysticsbiomes:sea_foam_pressure_plate", "mysticsbiomes:sea_foam_trapdoor",
                "mysticsbiomes:sea_foam_door",
                "mysticsbiomes:tropical_log", "mysticsbiomes:tropical_wood", "mysticsbiomes:stripped_tropical_log",
                "mysticsbiomes:stripped_tropical_wood", "mysticsbiomes:tropical_planks", "mysticsbiomes:tropical_stairs",
                "mysticsbiomes:tropical_slab", "mysticsbiomes:tropical_fence", "mysticsbiomes:tropical_fence_gate",
                "mysticsbiomes:tropical_button", "mysticsbiomes:tropical_pressure_plate", "mysticsbiomes:tropical_trapdoor",
                "mysticsbiomes:tropical_door",
                "mysticsbiomes:vanilla_log", "mysticsbiomes:vanilla_wood", "mysticsbiomes:stripped_vanilla_log",
                "mysticsbiomes:stripped_vanilla_wood", "mysticsbiomes:vanilla_planks", "mysticsbiomes:vanilla_stairs",
                "mysticsbiomes:vanilla_slab", "mysticsbiomes:vanilla_fence", "mysticsbiomes:vanilla_fence_gate",
                "mysticsbiomes:vanilla_button", "mysticsbiomes:vanilla_pressure_plate", "mysticsbiomes:vanilla_trapdoor",
                "mysticsbiomes:vanilla_door");
    }

    private static void registerStone() {
        register("stone", "石质材料",
                "minecraft:stone", "minecraft:stone_stairs", "minecraft:stone_slab", "minecraft:stone_pressure_plate",
                "minecraft:stone_button", "minecraft:cobblestone", "minecraft:cobblestone_stairs", "minecraft:cobblestone_slab",
                "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone", "minecraft:mossy_cobblestone_stairs",
                "minecraft:mossy_cobblestone_slab", "minecraft:mossy_cobblestone_wall", "minecraft:smooth_stone",
                "minecraft:smooth_stone_slab", "minecraft:granite", "minecraft:granite_stairs", "minecraft:granite_slab",
                "minecraft:granite_wall", "minecraft:polished_granite", "minecraft:polished_granite_stairs",
                "minecraft:polished_granite_slab", "minecraft:diorite", "minecraft:diorite_stairs", "minecraft:diorite_slab",
                "minecraft:diorite_wall", "minecraft:polished_diorite", "minecraft:polished_diorite_stairs",
                "minecraft:polished_diorite_slab", "minecraft:andesite", "minecraft:andesite_stairs", "minecraft:andesite_slab",
                "minecraft:andesite_wall", "minecraft:polished_andesite", "minecraft:polished_andesite_stairs",
                "minecraft:polished_andesite_slab", "minecraft:deepslate", "minecraft:cobbled_deepslate",
                "minecraft:cobbled_deepslate_stairs", "minecraft:cobbled_deepslate_slab", "minecraft:cobbled_deepslate_wall",
                "minecraft:chiseled_deepslate", "minecraft:polished_deepslate", "minecraft:polished_deepslate_stairs",
                "minecraft:polished_deepslate_slab", "minecraft:polished_deepslate_wall", "minecraft:deepslate_tiles",
                "minecraft:cracked_deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
                "minecraft:deepslate_tile_wall", "minecraft:reinforced_deepslate", "minecraft:tuff", "minecraft:tuff_stairs",
                "minecraft:tuff_slab", "minecraft:tuff_wall", "minecraft:chiseled_tuff", "minecraft:polished_tuff",
                "minecraft:polished_tuff_stairs", "minecraft:polished_tuff_slab", "minecraft:polished_tuff_wall",
                "minecraft:netherrack", "minecraft:basalt", "minecraft:smooth_basalt", "minecraft:polished_basalt",
                "minecraft:blackstone", "minecraft:gilded_blackstone", "minecraft:blackstone_stairs", "minecraft:blackstone_slab",
                "minecraft:blackstone_wall", "minecraft:chiseled_polished_blackstone", "minecraft:polished_blackstone",
                "minecraft:polished_blackstone_stairs", "minecraft:polished_blackstone_slab", "minecraft:polished_blackstone_wall",
                "minecraft:polished_blackstone_pressure_plate", "minecraft:polished_blackstone_button",
                "meadow:cobbled_limestone", "meadow:cobbled_limestone_stairs", "meadow:cobbled_limestone_slab",
                "meadow:cobbled_limestone_wall", "meadow:mossy_cobbled_limestone", "meadow:mossy_cobbled_limestone_stairs",
                "meadow:mossy_cobbled_limestone_slab", "meadow:mossy_cobbled_limestone_wall", "meadow:limestone",
                "meadow:limestone_stairs", "meadow:limestone_slab", "meadow:limestone_wall");
    }

    private static void registerBrick() {
        register("brick", "砖石材料",
                "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_brick_stairs",
                "minecraft:stone_brick_slab", "minecraft:stone_brick_wall", "minecraft:chiseled_stone_bricks",
                "minecraft:mossy_stone_bricks", "minecraft:mossy_stone_brick_stairs", "minecraft:mossy_stone_brick_slab",
                "minecraft:mossy_stone_brick_wall", "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks",
                "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_slab", "minecraft:deepslate_brick_wall",
                "minecraft:tuff_bricks", "minecraft:tuff_brick_stairs", "minecraft:tuff_brick_slab", "minecraft:tuff_brick_wall",
                "minecraft:chiseled_tuff_bricks", "minecraft:bricks", "minecraft:brick_stairs", "minecraft:brick_slab",
                "minecraft:brick_wall", "minecraft:packed_mud", "minecraft:mud_bricks", "minecraft:mud_brick_stairs",
                "minecraft:mud_brick_slab", "minecraft:mud_brick_wall", "minecraft:nether_bricks", "minecraft:cracked_nether_bricks",
                "minecraft:nether_brick_stairs", "minecraft:nether_brick_slab", "minecraft:nether_brick_wall",
                "minecraft:nether_brick_fence", "minecraft:chiseled_nether_bricks", "minecraft:red_nether_bricks",
                "minecraft:red_nether_brick_stairs", "minecraft:red_nether_brick_slab", "minecraft:red_nether_brick_wall",
                "minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks",
                "minecraft:polished_blackstone_brick_stairs", "minecraft:polished_blackstone_brick_slab",
                "minecraft:polished_blackstone_brick_wall", "minecraft:end_stone_bricks", "minecraft:end_stone_brick_stairs",
                "minecraft:end_stone_brick_slab", "minecraft:end_stone_brick_wall", "minecraft:purpur_block",
                "minecraft:purpur_pillar", "minecraft:purpur_stairs", "minecraft:purpur_slab");
    }

    private static void registerSand() {
        register("sand", "沙质材料",
                "minecraft:sandstone", "minecraft:sandstone_stairs", "minecraft:sandstone_slab", "minecraft:sandstone_wall",
                "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone", "minecraft:smooth_sandstone_stairs",
                "minecraft:smooth_sandstone_slab", "minecraft:cut_sandstone", "minecraft:cut_sandstone_slab",
                "minecraft:red_sandstone", "minecraft:red_sandstone_stairs", "minecraft:red_sandstone_slab",
                "minecraft:red_sandstone_wall", "minecraft:chiseled_red_sandstone", "minecraft:smooth_red_sandstone",
                "minecraft:smooth_red_sandstone_stairs", "minecraft:smooth_red_sandstone_slab", "minecraft:cut_red_sandstone",
                "minecraft:cut_red_sandstone_slab", "minecraft:sand", "minecraft:red_sand",
                "mysticsbiomes:lush_sand", "mysticsbiomes:grassy_lush_sand", "mysticsbiomes:lush_sandstone",
                "mysticsbiomes:lush_sandstone_stairs", "mysticsbiomes:lush_sandstone_slab", "mysticsbiomes:lush_sandstone_wall",
                "mysticsbiomes:chiseled_lush_sandstone", "mysticsbiomes:cut_lush_sandstone", "mysticsbiomes:cut_lush_sandstone_slab",
                "mysticsbiomes:smooth_lush_sandstone", "mysticsbiomes:smooth_lush_sandstone_stairs",
                "mysticsbiomes:smooth_lush_sandstone_slab");
    }

    private static void registerPrismarine() {
        register("prismarine", "海晶材料",
                "minecraft:prismarine", "minecraft:prismarine_stairs", "minecraft:prismarine_slab", "minecraft:prismarine_wall",
                "minecraft:prismarine_bricks", "minecraft:prismarine_brick_stairs", "minecraft:prismarine_brick_slab",
                "minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs", "minecraft:dark_prismarine_slab");
    }

    private static void registerQuartz() {
        register("quartz", "石英材料",
                "minecraft:quartz_block", "minecraft:quartz_stairs", "minecraft:quartz_slab", "minecraft:chiseled_quartz_block",
                "minecraft:quartz_bricks", "minecraft:quartz_pillar", "minecraft:smooth_quartz",
                "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_slab");
    }

    private static void registerWool() {
        register("wool", "羊毛材料",
                "minecraft:white_wool", "minecraft:light_gray_wool", "minecraft:gray_wool", "minecraft:black_wool",
                "minecraft:brown_wool", "minecraft:red_wool", "minecraft:orange_wool", "minecraft:yellow_wool",
                "minecraft:lime_wool", "minecraft:green_wool", "minecraft:cyan_wool", "minecraft:light_blue_wool",
                "minecraft:blue_wool", "minecraft:purple_wool", "minecraft:magenta_wool", "minecraft:pink_wool",
                "minecraft:white_carpet", "minecraft:light_gray_carpet", "minecraft:gray_carpet", "minecraft:black_carpet",
                "minecraft:brown_carpet", "minecraft:red_carpet", "minecraft:orange_carpet", "minecraft:yellow_carpet",
                "minecraft:lime_carpet", "minecraft:green_carpet", "minecraft:cyan_carpet", "minecraft:light_blue_carpet",
                "minecraft:blue_carpet", "minecraft:purple_carpet", "minecraft:magenta_carpet", "minecraft:pink_carpet",
                "minecraft:white_banner", "minecraft:light_gray_banner", "minecraft:gray_banner", "minecraft:black_banner",
                "minecraft:brown_banner", "minecraft:red_banner", "minecraft:orange_banner", "minecraft:yellow_banner",
                "minecraft:lime_banner", "minecraft:green_banner", "minecraft:cyan_banner", "minecraft:light_blue_banner",
                "minecraft:blue_banner", "minecraft:purple_banner", "minecraft:magenta_banner", "minecraft:pink_banner");
    }

    private static void registerTerracotta() {
        register("terracotta", "陶瓦材料",
                "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:light_gray_terracotta",
                "minecraft:gray_terracotta", "minecraft:black_terracotta", "minecraft:brown_terracotta",
                "minecraft:red_terracotta", "minecraft:orange_terracotta", "minecraft:yellow_terracotta",
                "minecraft:lime_terracotta", "minecraft:green_terracotta", "minecraft:cyan_terracotta",
                "minecraft:light_blue_terracotta", "minecraft:blue_terracotta", "minecraft:purple_terracotta",
                "minecraft:magenta_terracotta", "minecraft:pink_terracotta", "minecraft:white_glazed_terracotta",
                "minecraft:light_gray_glazed_terracotta", "minecraft:gray_glazed_terracotta", "minecraft:black_glazed_terracotta",
                "minecraft:brown_glazed_terracotta", "minecraft:red_glazed_terracotta", "minecraft:orange_glazed_terracotta",
                "minecraft:yellow_glazed_terracotta", "minecraft:lime_glazed_terracotta", "minecraft:green_glazed_terracotta",
                "minecraft:cyan_glazed_terracotta", "minecraft:light_blue_glazed_terracotta", "minecraft:blue_glazed_terracotta",
                "minecraft:purple_glazed_terracotta", "minecraft:magenta_glazed_terracotta", "minecraft:pink_glazed_terracotta");
    }

    private static void registerConcrete() {
        register("concrete", "混凝土材料",
                "minecraft:white_concrete", "minecraft:light_gray_concrete", "minecraft:gray_concrete",
                "minecraft:black_concrete", "minecraft:brown_concrete", "minecraft:red_concrete", "minecraft:orange_concrete",
                "minecraft:yellow_concrete", "minecraft:lime_concrete", "minecraft:green_concrete", "minecraft:cyan_concrete",
                "minecraft:light_blue_concrete", "minecraft:blue_concrete", "minecraft:purple_concrete",
                "minecraft:magenta_concrete", "minecraft:pink_concrete", "minecraft:white_concrete_powder",
                "minecraft:light_gray_concrete_powder", "minecraft:gray_concrete_powder", "minecraft:black_concrete_powder",
                "minecraft:brown_concrete_powder", "minecraft:red_concrete_powder", "minecraft:orange_concrete_powder",
                "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder", "minecraft:green_concrete_powder",
                "minecraft:cyan_concrete_powder", "minecraft:light_blue_concrete_powder", "minecraft:blue_concrete_powder",
                "minecraft:purple_concrete_powder", "minecraft:magenta_concrete_powder", "minecraft:pink_concrete_powder");
    }

    private static void registerGlass() {
        register("glass", "玻璃材料",
                "minecraft:glass", "minecraft:tinted_glass",
                "minecraft:white_stained_glass", "minecraft:light_gray_stained_glass", "minecraft:gray_stained_glass",
                "minecraft:black_stained_glass", "minecraft:brown_stained_glass", "minecraft:red_stained_glass",
                "minecraft:orange_stained_glass", "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass",
                "minecraft:green_stained_glass", "minecraft:cyan_stained_glass", "minecraft:light_blue_stained_glass",
                "minecraft:blue_stained_glass", "minecraft:purple_stained_glass", "minecraft:magenta_stained_glass",
                "minecraft:pink_stained_glass", "minecraft:glass_pane", "minecraft:white_stained_glass_pane",
                "minecraft:light_gray_stained_glass_pane", "minecraft:gray_stained_glass_pane", "minecraft:black_stained_glass_pane",
                "minecraft:brown_stained_glass_pane", "minecraft:red_stained_glass_pane", "minecraft:orange_stained_glass_pane",
                "minecraft:yellow_stained_glass_pane", "minecraft:lime_stained_glass_pane", "minecraft:green_stained_glass_pane",
                "minecraft:cyan_stained_glass_pane", "minecraft:light_blue_stained_glass_pane", "minecraft:blue_stained_glass_pane",
                "minecraft:purple_stained_glass_pane", "minecraft:magenta_stained_glass_pane", "minecraft:pink_stained_glass_pane");
    }

    private static void registerLeaves() {
        register("leaves", "树叶材料",
                "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves", "minecraft:jungle_leaves",
                "minecraft:acacia_leaves", "minecraft:dark_oak_leaves", "minecraft:mangrove_leaves", "minecraft:cherry_leaves",
                "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves",
                "culturaldelights:avocado_leaves", "culturaldelights:fruiting_avocado_leaves",
                "meadow:pine_leaves", "meadow:yellow_pine_leaves", "meadow:alpine_birch_leaves",
                "mysticsbiomes:strawberry_blossoms", "mysticsbiomes:pink_cherry_blossoms", "mysticsbiomes:white_cherry_blossoms",
                "mysticsbiomes:lavender_blossoms", "mysticsbiomes:peach_leaves", "mysticsbiomes:maple_leaves",
                "mysticsbiomes:orange_maple_leaves", "mysticsbiomes:yellow_maple_leaves", "mysticsbiomes:sea_shrub_leaves",
                "mysticsbiomes:tropical_leaves", "mysticsbiomes:vanilla_leaves", "mysticsbiomes:butterfly_nest",
                "mysticsbiomes:peony_leaves", "mysticsbiomes:hydrangea_leaves");
    }

    private static void registerLighting() {
        register("lighting", "照明材料",
                "minecraft:torch", "minecraft:soul_torch", "minecraft:redstone_torch",
                "minecraft:lantern", "minecraft:soul_lantern", "minecraft:end_rod",
                "minecraft:glowstone", "minecraft:shroomlight", "minecraft:sea_lantern",
                "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight",
                "minecraft:campfire", "minecraft:soul_campfire",
                "beachparty:palm_torch_item", "beachparty:tall_palm_torch");
    }
}

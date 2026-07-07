package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.block.BreedingControlBoxBlock;
import com.xy2407.nsukaddition.common.block.DynamicRoeBlock;
import com.xy2407.nsukaddition.common.block.entity.DynamicRoeBlockEntity;
import com.xy2407.nsukaddition.common.block.entity.MiningControlBoxBlockEntity;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 模组方块与方块实体注册中心，统一管理方块、对应物品及方块实体的注册。 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NsukAddition.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NsukAddition.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, NsukAddition.MOD_ID);

    public static final DeferredBlock<Block> BREEDING_CONTROL_BOX = BLOCKS.register(
            "breeding_control_box", BreedingControlBoxBlock::new);
    public static final DeferredItem<BlockItem> BREEDING_CONTROL_BOX_ITEM = ITEMS.register(
            "breeding_control_box", () -> new BlockItem(BREEDING_CONTROL_BOX.get(), new Item.Properties()));

    public static final DeferredBlock<Block> MINING_CONTROL_BOX = BLOCKS.register(
            "mining_control_box", MiningControlBoxBlock::new);
    public static final DeferredItem<BlockItem> MINING_CONTROL_BOX_ITEM = ITEMS.register(
            "mining_control_box", () -> new BlockItem(MINING_CONTROL_BOX.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiningControlBoxBlockEntity>> MINING_CONTROL_BOX_ENTITY =
            BLOCK_ENTITIES.register("mining_control_box",
                    () -> BlockEntityType.Builder.of(MiningControlBoxBlockEntity::new, MINING_CONTROL_BOX.get()).build(null));

    public static final DeferredBlock<Block> DYNAMIC_ROE_BLOCK = BLOCKS.register(
            "dynamic_roe_block", () -> new DynamicRoeBlock(Block.Properties.of().strength(0.3F).noOcclusion().noCollission().sound(SoundType.FROGSPAWN)));
    public static final DeferredItem<BlockItem> DYNAMIC_ROE_BLOCK_ITEM = ITEMS.register(
            "dynamic_roe_block", () -> new BlockItem(DYNAMIC_ROE_BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DynamicRoeBlockEntity>> DYNAMIC_ROE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("dynamic_roe_block",
                    () -> BlockEntityType.Builder.of(DynamicRoeBlockEntity::new, DYNAMIC_ROE_BLOCK.get()).build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(ModBlocks::onCreativeTabBuild);
    }

    private static void onCreativeTabBuild(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == common.cn.kafei.simukraft.registry.ModCreativeModeTabs.SIMUKRAFT_TAB.getKey()) {
            event.accept(BREEDING_CONTROL_BOX_ITEM);
            event.accept(MINING_CONTROL_BOX_ITEM);
            event.accept(DYNAMIC_ROE_BLOCK_ITEM);
        }
    }
}

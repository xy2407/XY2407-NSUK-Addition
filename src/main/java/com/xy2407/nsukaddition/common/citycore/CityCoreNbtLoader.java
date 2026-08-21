package com.xy2407.nsukaddition.common.citycore;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.citycore.CityCoreStructure.CityCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 城市核心建筑 NBT 加载器，从 classpath 读取 jar 内 data 目录的 citycore.nbt（客户端渲染与服务端放置共用）。 */
public final class CityCoreNbtLoader {

    private static final String STRUCTURE_CLASSPATH = "/data/" + NsukAddition.MOD_ID + "/core/citycore.nbt";

    private static volatile CityCoreStructure cached;

    private CityCoreNbtLoader() {
    }

    public static CityCoreStructure get() {
        CityCoreStructure result = cached;
        if (result == null) {
            result = load();
            cached = result;
        }
        return result;
    }

    public static void clearCache() {
        cached = null;
    }

    private static CityCoreStructure load() {
        try (InputStream input = CityCoreNbtLoader.class.getResourceAsStream(STRUCTURE_CLASSPATH)) {
            if (input == null) {
                NsukAddition.LOGGER.error("Nsuk: citycore.nbt not found on classpath at {}", STRUCTURE_CLASSPATH);
                return CityCoreStructure.EMPTY;
            }
            CompoundTag rootTag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            return parse(rootTag);
        } catch (IOException exception) {
            NsukAddition.LOGGER.error("Nsuk: Failed to read citycore.nbt", exception);
            return CityCoreStructure.EMPTY;
        }
    }

    private static CityCoreStructure parse(CompoundTag rootTag) {
        if (rootTag.contains("Schematic", Tag.TAG_COMPOUND)) {
            rootTag = rootTag.getCompound("Schematic");
        }
        if (!rootTag.contains("palette", Tag.TAG_LIST) || !rootTag.contains("blocks", Tag.TAG_LIST)) {
            return CityCoreStructure.EMPTY;
        }

        ListTag paletteTag = rootTag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> paletteStates = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            paletteStates.add(parseState(paletteTag.getCompound(i)));
        }

        ListTag blockTags = rootTag.getList("blocks", Tag.TAG_COMPOUND);
        List<CityCoreBlock> blocks = new ArrayList<>(blockTags.size());
        for (int i = 0; i < blockTags.size(); i++) {
            CompoundTag blockTag = blockTags.getCompound(i);
            if (!blockTag.contains("pos", Tag.TAG_LIST)) {
                continue;
            }
            ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
            if (posList.size() < 3) {
                continue;
            }
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= paletteStates.size()) {
                continue;
            }
            BlockState state = paletteStates.get(stateIndex);
            if (state == null || state.isAir()) {
                continue;
            }
            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));
            CompoundTag blockEntityData = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt")
                    : null;
            blocks.add(new CityCoreBlock(pos, state, blockEntityData));
        }
        return new CityCoreStructure(List.copyOf(blocks), CityCoreStructure.computeOrigin(blocks));
    }

    private static BlockState parseState(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        if (name == null || name.isBlank()) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name)).orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = stateTag.getCompound("Properties");
            StateDefinition<Block, BlockState> definition = block.getStateDefinition();
            for (String key : properties.getAllKeys()) {
                Property<?> property = definition.getProperty(key);
                if (property == null) {
                    continue;
                }
                state = applyProperty(state, property, properties.getString(key));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }
}

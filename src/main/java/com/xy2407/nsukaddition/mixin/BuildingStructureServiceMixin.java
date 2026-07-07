package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 拦截养殖建筑结构加载，从文件系统直接读取 .nbt。 */
@Mixin(value = BuildingStructureService.class, remap = false)
public class BuildingStructureServiceMixin {

    private static final Path BREEDING_DIR = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/breeding");
    private static final Map<String, String> LEGACY_BLOCK_REMAPS = Map.of(
            "minecraft:grass", "minecraft:short_grass"
    );

    @Inject(method = "loadStructure", at = @At("HEAD"), cancellable = true)
    private static void nsuk$loadBreedingStructure(String category, String buildingFileName,
                                                    CallbackInfoReturnable<Optional<BuildingStructure>> cir) {
        if (!"breeding".equalsIgnoreCase(category)) {
            return;
        }

        if (!Files.isDirectory(BREEDING_DIR)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        String nbtFileName = buildingFileName;
        if (nbtFileName.toLowerCase(Locale.ROOT).endsWith(".sk")) {
            nbtFileName = nbtFileName.substring(0, nbtFileName.length() - 3) + ".nbt";
        } else if (!nbtFileName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            nbtFileName = nbtFileName + ".nbt";
        }

        Path nbtPath = BREEDING_DIR.resolve(nbtFileName);

        if (!Files.isRegularFile(nbtPath)) {
            Path found = findNbtFile(nbtFileName);
            if (found != null) {
                nbtPath = found;
                nbtFileName = found.getFileName().toString();
            }
        }

        if (!Files.isRegularFile(nbtPath)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        Optional<BuildingStructure> result = loadFromNbt(category, buildingFileName, nbtFileName, nbtPath);
        cir.setReturnValue(result);
    }

    private static Path findNbtFile(String targetName) {
        String lower = targetName.toLowerCase(Locale.ROOT);
        try (var stream = Files.list(BREEDING_DIR)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (name.toLowerCase(Locale.ROOT).equals(lower)) {
                    return path;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static Optional<BuildingStructure> loadFromNbt(String category, String metaFileName,
                                                            String nbtFileName, Path nbtPath) {
        CompoundTag rootTag;
        try (InputStream in = Files.newInputStream(nbtPath)) {
            rootTag = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return Optional.empty();
        }

        List<BuildingBlockData> blocks = parseBlocks(rootTag);

        if (blocks.isEmpty()) {
            return Optional.empty();
        }

        Path skPath = BREEDING_DIR.resolve(metaFileName);
        String displayName = metaFileName;
        String size = "-";
        String amount = "-";
        String author = "External";

        if (Files.isRegularFile(skPath)) {
            try {
                String metaText = Files.readString(skPath);
                displayName = findValue(metaText, "name", stripExtension(metaFileName));
                size = findValue(metaText, "size", "-");
                amount = findValue(metaText, "amount", findValue(metaText, "price", "-"));
                author = findValue(metaText, "author", "External");
            } catch (IOException e) {

            }
        }

        BlockPos sizePos = parseSize(size);

        BuildingStructure structure = new BuildingStructure(
                category, displayName, stripExtension(metaFileName),
                amount, nbtFileName, author, size,
                sizePos, List.copyOf(blocks),
                List.of(), BlockPos.ZERO, blocks.size()
        );

        return Optional.of(structure);
    }

    private static List<BuildingBlockData> parseBlocks(CompoundTag rootTag) {
        List<BuildingBlockData> blocks = new ArrayList<>();
        if (rootTag.contains("Schematic", Tag.TAG_COMPOUND)) {
            return parseBlocks(rootTag.getCompound("Schematic"));
        }
        if (rootTag.contains("blocks", Tag.TAG_LIST) && rootTag.contains("palette", Tag.TAG_LIST)) {
            ListTag palette = rootTag.getList("palette", Tag.TAG_COMPOUND);
            ListTag blockTags = rootTag.getList("blocks", Tag.TAG_COMPOUND);

            int skippedNoPos = 0, skippedBadPos = 0, skippedBadIndex = 0, skippedNullState = 0;
            for (int i = 0; i < blockTags.size(); i++) {
                CompoundTag blockTag = blockTags.getCompound(i);
                if (!blockTag.contains("pos", Tag.TAG_LIST)) { skippedNoPos++; continue; }
                ListTag posList = blockTag.getList("pos", Tag.TAG_INT);
                if (posList.size() < 3) { skippedBadPos++; continue; }
                int x = posList.getInt(0);
                int y = posList.getInt(1);
                int z = posList.getInt(2);
                int stateIndex = blockTag.getInt("state");
                if (stateIndex < 0 || stateIndex >= palette.size()) { skippedBadIndex++; continue; }
                BlockState state = parseState(palette.getCompound(stateIndex));
                if (state == null) { skippedNullState++; continue; }
                BlockPos relative = new BlockPos(x, y, z);
                blocks.add(new BuildingBlockData(relative, state, relative));
            }
        }
        return blocks;
    }

    private static BlockState parseState(CompoundTag stateTag) {
        String name = stateTag.getString("Name");
        if (name == null || name.isBlank()) return null;
        name = LEGACY_BLOCK_REMAPS.getOrDefault(name, name);
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name)).orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (stateTag.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = stateTag.getCompound("Properties");
            for (String key : properties.getAllKeys()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property == null) continue;
                state = applyProperty(state, property, properties.getString(key));
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static BlockPos parseSize(String value) {
        if (value == null || value.isBlank()) return BlockPos.ZERO;
        String normalized = value.toLowerCase(Locale.ROOT).replace('\u00d7', 'x').replace(' ', 'x');
        String[] parts = normalized.split("x");
        if (parts.length != 3) return BlockPos.ZERO;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return BlockPos.ZERO;
        }
    }

    private static String findValue(String text, String key, String fallback) {
        String prefix = key + ":";
        for (String line : text.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String val = trimmedLine.substring(prefix.length()).trim();
            return val.isEmpty() ? fallback : val;
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private static long safeFileSize(Path path) {
        try { return Files.size(path); } catch (IOException ignored) { return -1; }
    }
}

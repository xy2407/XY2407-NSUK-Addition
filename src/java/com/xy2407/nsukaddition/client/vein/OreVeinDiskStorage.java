package com.xy2407.nsukaddition.client.vein;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 矿脉数据的客户端磁盘持久化，以 NBT 格式按维度存储矿脉区块映射。 */
@OnlyIn(Dist.CLIENT)
public final class OreVeinDiskStorage {

    private static final int DATA_VERSION = 1;
    private static final String FILE_NAME = "veins.dat";

    private OreVeinDiskStorage() {}

    public static Path getWorldStorageDir() {
        Minecraft mc = Minecraft.getInstance();
        String worldName = resolveWorldName(mc);
        return mc.gameDirectory.toPath()
                .resolve("xy2407_nsuk_addition")
                .resolve("vein_data")
                .resolve(sanitizeFileName(worldName));
    }

    public static void saveVeins(Map<ResourceKey<Level>, ConcurrentHashMap<Long, OreVeinType>> veinsByDimension) {
        if (veinsByDimension == null || veinsByDimension.isEmpty()) return;

        try {
            Path dir = getWorldStorageDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE_NAME);

            CompoundTag root = new CompoundTag();
            root.putInt("data_version", DATA_VERSION);

            CompoundTag dimsTag = new CompoundTag();
            for (Map.Entry<ResourceKey<Level>, ConcurrentHashMap<Long, OreVeinType>> dimEntry : veinsByDimension.entrySet()) {
                String dimKey = dimEntry.getKey().location().toString();
                ConcurrentHashMap<Long, OreVeinType> veins = dimEntry.getValue();
                if (veins == null || veins.isEmpty()) continue;

                ListTag veinList = new ListTag();
                for (Map.Entry<Long, OreVeinType> veinEntry : veins.entrySet()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putLong("pos", veinEntry.getKey());
                    entry.putInt("type", veinEntry.getValue().ordinal());
                    veinList.add(entry);
                }
                dimsTag.put(dimKey, veinList);
            }
            root.put("dimensions", dimsTag);

            NbtIo.writeCompressed(root, file);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("Failed to save vein data to disk", e);
        }
    }

    public static Map<ResourceKey<Level>, Map<Long, OreVeinType>> loadVeins() {
        Map<ResourceKey<Level>, Map<Long, OreVeinType>> result = new HashMap<>();
        Path file = getWorldStorageDir().resolve(FILE_NAME);
        if (!Files.exists(file)) return result;

        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (root == null) return result;

            CompoundTag dimsTag = root.getCompound("dimensions");
            OreVeinType[] types = OreVeinType.values();
            for (String dimKey : dimsTag.getAllKeys()) {
                ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimKey));
                ListTag veinList = dimsTag.getList(dimKey, CompoundTag.TAG_COMPOUND);
                Map<Long, OreVeinType> veins = new HashMap<>();
                for (int i = 0; i < veinList.size(); i++) {
                    CompoundTag entry = veinList.getCompound(i);
                    long pos = entry.getLong("pos");
                    int ordinal = entry.getInt("type");
                    if (ordinal >= 0 && ordinal < types.length) {
                        veins.put(pos, types[ordinal]);
                    }
                }
                if (!veins.isEmpty()) {
                    result.put(dimensionKey, veins);
                }
            }
        } catch (IOException e) {
            NsukAddition.LOGGER.error("Failed to load vein data from disk", e);
        }
        return result;
    }

    private static String resolveWorldName(Minecraft mc) {

        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }

        if (mc.getCurrentServer() != null) {
            return "mp_" + mc.getCurrentServer().ip;
        }
        return "unknown";
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}

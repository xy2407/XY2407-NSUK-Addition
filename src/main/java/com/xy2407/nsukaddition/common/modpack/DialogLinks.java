package com.xy2407.nsukaddition.common.modpack;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** 对话框跳转链接配置：首次启动部署 dialog_links.json 到游戏目录，并按 key 提供 url 解析。 */
public final class DialogLinks {

    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition";
    private static final String FILE_NAME = "dialog_links.json";
    private static final String RESOURCE_PATH = "/data/xy2407_nsuk_addition/dialog_links.json";
    private static final String VERSION_ENTRY = "_nsuk_dialog_links_version.txt";
    private static final String CURRENT_VERSION = "1";

    private static final Map<String, String> LINKS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private DialogLinks() {
    }

    public static void deployFiles() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to create modpack directory", e);
            return;
        }
        if (isUpToDate(dirPath)) {
            return;
        }
        try (InputStream is = DialogLinks.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                NsukAddition.LOGGER.warn("nsuk_addition: Missing dialog links resource: {}", RESOURCE_PATH);
                return;
            }
            Files.copy(is, dirPath.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to deploy dialog links json", e);
            return;
        }
        writeVersion(dirPath);
        loaded = false;
    }

    public static String get(String key) {
        if (key == null) {
            return null;
        }
        if (!loaded) {
            load();
        }
        return LINKS.get(key);
    }

    private static void load() {
        Path path = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR).resolve(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
                for (String name : obj.keySet()) {
                    JsonElement el = obj.get(name);
                    if (el != null && el.isJsonPrimitive()) {
                        LINKS.put(name, el.getAsString());
                    }
                }
            } catch (Exception e) {
                NsukAddition.LOGGER.warn("nsuk_addition: Failed to load dialog links json, use empty", e);
            }
        }
        loaded = true;
    }

    private static boolean isUpToDate(Path dirPath) {
        Path versionFile = dirPath.resolve(VERSION_ENTRY);
        if (!Files.isRegularFile(versionFile)) {
            return false;
        }
        try {
            return CURRENT_VERSION.equals(Files.readString(versionFile, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeVersion(Path dirPath) {
        try {
            Files.writeString(dirPath.resolve(VERSION_ENTRY), CURRENT_VERSION, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to write dialog links version file", e);
        }
    }
}
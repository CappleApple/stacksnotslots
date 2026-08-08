package com.cappleapple.stacksnotslots.category;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cappleapple.stacksnotslots.StacksNotSlots;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/** Loads category templates from the dedicated pack/server-owned preset file. */
public final class CategoryPresetManager {
    private static final String BUNDLED_PRESET = "/default_categories.json";
    private static volatile List<CategoryDefinition> defaults = List.of();

    private CategoryPresetManager() {}

    public static synchronized void reload() {
        Path path = presetPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path) || Files.size(path) == 0) {
                try (InputStream input = CategoryPresetManager.class.getResourceAsStream(BUNDLED_PRESET)) {
                    if (input == null) throw new IOException("Bundled category preset is missing");
                    Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            defaults = parse(Files.readString(path, StandardCharsets.UTF_8));
            StacksNotSlots.LOGGER.info("Loaded {} default category presets from {}", defaults.size(), path);
        } catch (Exception exception) {
            StacksNotSlots.LOGGER.error("Could not load category presets; retaining the previous valid set", exception);
            if (defaults.isEmpty()) {
                try {
                    defaults = parse(readBundledPreset());
                    StacksNotSlots.LOGGER.warn("Using bundled category presets because no valid external preset has been loaded");
                } catch (Exception bundledException) {
                    StacksNotSlots.LOGGER.error("Bundled category presets are also invalid", bundledException);
                }
            }
        }
    }

    public static List<CategoryDefinition> defaults() {
        if (defaults.isEmpty()) reload();
        return List.copyOf(defaults);
    }

    public static void initialize(PlayerCategoryData data) {
        if (!data.initializedFromDefaults()) data.replaceAll(defaults(), true);
    }

    public static void reset(PlayerCategoryData data) {
        data.replaceAll(defaults(), true);
    }

    public static Path presetPath() {
        return FMLPaths.CONFIGDIR.get().resolve(StacksNotSlots.MOD_ID).resolve("default_categories.json");
    }

    static List<CategoryDefinition> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schemaVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported category preset schema version " + schemaVersion);
        JsonArray presets = root.getAsJsonArray("presets");
        if (presets == null) throw new IllegalArgumentException("Category preset file has no presets array");
        ArrayList<CategoryDefinition> result = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        int fallbackOrder = 0;
        for (JsonElement element : presets) {
            JsonObject object = element.getAsJsonObject();
            ResourceLocation id = parseId(requiredString(object, "id"), StacksNotSlots.MOD_ID);
            ResourceLocation icon = parseId(requiredString(object, "icon"), "minecraft");
            if (id == null || icon == null) throw new IllegalArgumentException("Invalid category id or icon in " + object);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate category id " + id);
            result.add(new CategoryDefinition(
                    id,
                    requiredString(object, "name"),
                    icon,
                    object.has("order") ? object.get("order").getAsInt() : fallbackOrder,
                    parseRules(object.getAsJsonArray("include")),
                    parseRules(object.getAsJsonArray("exclude")),
                    object.has("pickupLimit") ? object.get("pickupLimit").getAsLong() : -1,
                    parseSort(object.has("sort") ? object.get("sort").getAsString() : "name"),
                    !object.has("enabled") || object.get("enabled").getAsBoolean(),
                    object.has("allItems") && object.get("allItems").getAsBoolean()
            ));
            fallbackOrder++;
        }
        return List.copyOf(result);
    }

    private static List<CategoryRule> parseRules(JsonArray array) {
        if (array == null) return List.of();
        ArrayList<CategoryRule> rules = new ArrayList<>();
        for (JsonElement element : array) {
            String encoded = element.getAsString();
            CategoryRule.Type type = encoded.startsWith("#") ? CategoryRule.Type.TAG
                    : encoded.startsWith("@") ? CategoryRule.Type.MOD_ID : CategoryRule.Type.ITEM;
            ResourceLocation target = type == CategoryRule.Type.MOD_ID
                    ? ResourceLocation.tryBuild(encoded.substring(1), "mod")
                    : parseId(type == CategoryRule.Type.TAG ? encoded.substring(1) : encoded, "minecraft");
            if (target == null) throw new IllegalArgumentException("Invalid category rule: " + encoded);
            rules.add(new CategoryRule(type, target));
        }
        return rules;
    }

    private static SortMode parseSort(String value) {
        return switch (value.toLowerCase()) {
            case "name", "name_ascending" -> SortMode.NAME_ASCENDING;
            case "name_descending" -> SortMode.NAME_DESCENDING;
            case "quantity", "quantity_descending" -> SortMode.QUANTITY_DESCENDING;
            case "quantity_ascending" -> SortMode.QUANTITY_ASCENDING;
            case "registry", "registry_id" -> SortMode.REGISTRY_ID;
            case "namespace", "mod_namespace" -> SortMode.MOD_NAMESPACE;
            default -> throw new IllegalArgumentException("Unknown sort mode: " + value);
        };
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key)) throw new IllegalArgumentException("Missing category field " + key);
        return object.get(key).getAsString();
    }

    private static ResourceLocation parseId(String value, String defaultNamespace) {
        return ResourceLocation.tryParse(value.indexOf(':') >= 0 ? value : defaultNamespace + ':' + value);
    }

    private static String readBundledPreset() throws IOException {
        try (InputStream input = CategoryPresetManager.class.getResourceAsStream(BUNDLED_PRESET)) {
            if (input == null) throw new IOException("Bundled category preset is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

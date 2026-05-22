package com.glowmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GlowConfig {

    public static boolean playerGlowEnabled = true;
    public static int playerGlowColor = 0xFFFFFF;

    public static boolean mobGlowEnabled = true;
    public static int mobGlowColor = 0xFF4444;

    public static boolean blockOutlineEnabled = true;
    public static int defaultOutlineColor = 0x00FF00;

    public static float outlineLineWidth  = 2.5f;
    public static float outlineOpacity    = 1.0f;
    public static float glowRange         = 64.0f;
    public static int   maxGlowEntities   = 16;
    public static int   chunkScanRate        = 3;
    public static int   chunkRefreshInterval = 200;

    public static float guiOpacity     = 1.0f;
    public static int   guiAccentColor = 0x6366F1; // stored as 0xRRGGBB

    public static final LinkedHashMap<String, Integer> blockWhitelist = new LinkedHashMap<>();
    public static final Set<String> mobGlowWhitelist = new LinkedHashSet<>();

    public static final Set<String> disabledBlocks = new LinkedHashSet<>();
    public static final Set<String> disabledMobs   = new LinkedHashSet<>();

    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("glowmod.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            if (obj.has("playerGlowEnabled"))  playerGlowEnabled  = obj.get("playerGlowEnabled").getAsBoolean();
            if (obj.has("playerGlowColor"))    playerGlowColor    = obj.get("playerGlowColor").getAsInt();
            if (obj.has("mobGlowEnabled"))     mobGlowEnabled     = obj.get("mobGlowEnabled").getAsBoolean();
            if (obj.has("mobGlowColor"))       mobGlowColor       = obj.get("mobGlowColor").getAsInt();
            if (obj.has("blockOutlineEnabled")) blockOutlineEnabled = obj.get("blockOutlineEnabled").getAsBoolean();
            if (obj.has("defaultOutlineColor")) defaultOutlineColor = obj.get("defaultOutlineColor").getAsInt();
            if (obj.has("outlineLineWidth"))  outlineLineWidth  = obj.get("outlineLineWidth").getAsFloat();
            if (obj.has("outlineOpacity"))    outlineOpacity    = obj.get("outlineOpacity").getAsFloat();
            if (obj.has("glowRange"))         glowRange         = obj.get("glowRange").getAsFloat();
            if (obj.has("maxGlowEntities"))   maxGlowEntities   = obj.get("maxGlowEntities").getAsInt();
            if (obj.has("chunkScanRate"))        chunkScanRate        = obj.get("chunkScanRate").getAsInt();
            if (obj.has("chunkRefreshInterval")) chunkRefreshInterval = obj.get("chunkRefreshInterval").getAsInt();
            if (obj.has("guiOpacity"))        guiOpacity        = obj.get("guiOpacity").getAsFloat();
            if (obj.has("guiAccentColor"))    guiAccentColor    = obj.get("guiAccentColor").getAsInt();
            if (obj.has("blockWhitelist")) {
                blockWhitelist.clear();
                for (JsonElement e : obj.getAsJsonArray("blockWhitelist")) {
                    if (e.isJsonObject()) {
                        JsonObject entry = e.getAsJsonObject();
                        String id = entry.get("id").getAsString();
                        int color = entry.has("color") ? entry.get("color").getAsInt() : defaultOutlineColor;
                        blockWhitelist.put(id, color);
                    } else {
                        blockWhitelist.put(e.getAsString(), defaultOutlineColor);
                    }
                }
            }
            if (obj.has("mobGlowWhitelist")) {
                mobGlowWhitelist.clear();
                for (JsonElement e : obj.getAsJsonArray("mobGlowWhitelist"))
                    mobGlowWhitelist.add(e.getAsString());
            }
            if (obj.has("disabledBlocks")) {
                disabledBlocks.clear();
                for (JsonElement e : obj.getAsJsonArray("disabledBlocks"))
                    disabledBlocks.add(e.getAsString());
            }
            if (obj.has("disabledMobs")) {
                disabledMobs.clear();
                for (JsonElement e : obj.getAsJsonArray("disabledMobs"))
                    disabledMobs.add(e.getAsString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("playerGlowEnabled", playerGlowEnabled);
        obj.addProperty("playerGlowColor", playerGlowColor);
        obj.addProperty("mobGlowEnabled", mobGlowEnabled);
        obj.addProperty("mobGlowColor", mobGlowColor);
        obj.addProperty("blockOutlineEnabled", blockOutlineEnabled);
        obj.addProperty("defaultOutlineColor", defaultOutlineColor);
        obj.addProperty("outlineLineWidth",  outlineLineWidth);
        obj.addProperty("outlineOpacity",    outlineOpacity);
        obj.addProperty("glowRange",         glowRange);
        obj.addProperty("maxGlowEntities",   maxGlowEntities);
        obj.addProperty("chunkScanRate",        chunkScanRate);
        obj.addProperty("chunkRefreshInterval", chunkRefreshInterval);
        obj.addProperty("guiOpacity",        guiOpacity);
        obj.addProperty("guiAccentColor",    guiAccentColor);
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Integer> entry : blockWhitelist.entrySet()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", entry.getKey());
            e.addProperty("color", entry.getValue());
            arr.add(e);
        }
        obj.add("blockWhitelist", arr);
        JsonArray mobArr = new JsonArray();
        for (String s : mobGlowWhitelist) mobArr.add(s);
        obj.add("mobGlowWhitelist", mobArr);
        JsonArray disBlocks = new JsonArray();
        for (String s : disabledBlocks) disBlocks.add(s);
        obj.add("disabledBlocks", disBlocks);
        JsonArray disMobs = new JsonArray();
        for (String s : disabledMobs) disMobs.add(s);
        obj.add("disabledMobs", disMobs);
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

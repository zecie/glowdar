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

    public static final LinkedHashMap<String, Integer> blockWhitelist = new LinkedHashMap<>();
    public static final Set<String> mobGlowWhitelist = new LinkedHashSet<>();

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
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

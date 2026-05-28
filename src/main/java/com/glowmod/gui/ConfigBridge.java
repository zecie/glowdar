package com.glowmod.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.glowmod.GlowConfig;
import com.glowmod.render.BlockScanner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigBridge {

    private static final String CONSOLE_PREFIX = "!GLOWDAR!|";
    private static final Gson GSON = new GsonBuilder().create();
    private static List<String> cachedBlockIds;
    private static List<String> cachedMobIds;
    private static boolean handlersRegistered = false;
    private static MCEFBrowser bound;

    private ConfigBridge() {}

    public static synchronized void registerHandlers() {
        if (handlersRegistered) return;
        MCEF.getClient().addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                if (message == null || !message.startsWith(CONSOLE_PREFIX)) return false;
                String body = message.substring(CONSOLE_PREFIX.length());
                int sep = body.indexOf('|');
                if (sep < 0) return true;
                final String op = body.substring(0, sep);
                final String payload = body.substring(sep + 1);
                final MCEFBrowser mb = (browser instanceof MCEFBrowser b) ? b : bound;
                Minecraft.getInstance().execute(() -> applyFromJson(mb, op, payload));
                return true;
            }
        });
        handlersRegistered = true;
        System.out.println("[glowdar] console bridge installed");
    }

    public static void attach(MCEFBrowser browser) {
        bound = browser;
        MCEF.getClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser b, CefFrame frame, int statusCode) {
                if (b == browser && frame != null && frame.isMain()) {
                    Minecraft.getInstance().execute(() -> pushState(browser));
                }
            }
        });
    }

    public static void pushState(MCEFBrowser browser) {
        if (browser == null) return;
        JsonObject root = new JsonObject();
        root.add("config", buildConfig());
        root.add("registries", buildRegistries());

        String payload = GSON.toJson(root);
        String js = "(function(){"
                + "var s = " + payload + ";"
                + "window.glowdar = window.glowdar || {};"
                + "window.glowdar.state = s;"
                + "if (window.glowdar.onStateUpdate) window.glowdar.onStateUpdate(s);"
                + "})();";
        browser.executeJavaScript(js, "", 0);
    }

    private static JsonObject buildConfig() {
        JsonObject c = new JsonObject();
        c.addProperty("playerGlowEnabled",   GlowConfig.playerGlowEnabled);
        c.addProperty("playerGlowColor",     GlowConfig.playerGlowColor);
        c.addProperty("mobGlowEnabled",      GlowConfig.mobGlowEnabled);
        c.addProperty("mobGlowColor",        GlowConfig.mobGlowColor);
        c.addProperty("blockOutlineEnabled", GlowConfig.blockOutlineEnabled);
        c.addProperty("defaultOutlineColor", GlowConfig.defaultOutlineColor);
        c.addProperty("outlineLineWidth",    GlowConfig.outlineLineWidth);
        c.addProperty("outlineOpacity",      GlowConfig.outlineOpacity);
        c.addProperty("glowRange",           GlowConfig.glowRange);
        c.addProperty("maxGlowEntities",     GlowConfig.maxGlowEntities);
        c.addProperty("chunkScanRate",       GlowConfig.chunkScanRate);
        c.addProperty("chunkRefreshInterval", GlowConfig.chunkRefreshInterval);
        c.addProperty("espAnimMode",          GlowConfig.espAnimMode);
        c.addProperty("guiOpacity",          GlowConfig.guiOpacity);
        c.addProperty("guiAccentColor",      GlowConfig.guiAccentColor);

        JsonArray blocks = new JsonArray();
        for (Map.Entry<String, Integer> e : GlowConfig.blockWhitelist.entrySet()) {
            JsonObject b = new JsonObject();
            b.addProperty("id", e.getKey());
            b.addProperty("color", e.getValue());
            blocks.add(b);
        }
        c.add("blockWhitelist", blocks);

        JsonArray mobs = new JsonArray();
        for (String s : GlowConfig.mobGlowWhitelist) mobs.add(s);
        c.add("mobGlowWhitelist", mobs);

        return c;
    }

    private static JsonObject buildRegistries() {
        if (cachedBlockIds == null) {
            cachedBlockIds = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) cachedBlockIds.add(id.toString());
            cachedBlockIds.sort(String::compareTo);
        }
        if (cachedMobIds == null) {
            cachedMobIds = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) cachedMobIds.add(id.toString());
            cachedMobIds.sort(String::compareTo);
        }

        JsonObject r = new JsonObject();
        JsonArray blocks = new JsonArray();
        for (String id : cachedBlockIds) blocks.add(id);
        r.add("blocks", blocks);
        JsonArray mobs = new JsonArray();
        for (String id : cachedMobIds) mobs.add(id);
        r.add("mobs", mobs);
        return r;
    }

    public static void applyFromJson(MCEFBrowser browser, String op, String payload) {
        try {
            JsonObject obj = payload == null || payload.isEmpty()
                    ? new JsonObject()
                    : GSON.fromJson(payload, JsonObject.class);
            boolean mutated = true;
            switch (op) {
                case "set" -> {
                    applyField(obj.get("key").getAsString(), obj.get("value"));
                }
                case "blockAdd" -> {
                    String id = obj.get("id").getAsString();
                    int color = obj.has("color") ? obj.get("color").getAsInt() : GlowConfig.defaultOutlineColor;
                    GlowConfig.blockWhitelist.put(id, color);
                    BlockScanner.rescanAll();
                }
                case "blockRemove" -> {
                    GlowConfig.blockWhitelist.remove(obj.get("id").getAsString());
                    BlockScanner.rescanAll();
                }
                case "blockColor" -> {
                    GlowConfig.blockWhitelist.put(obj.get("id").getAsString(), obj.get("color").getAsInt());
                }
                case "mobAdd" -> {
                    GlowConfig.mobGlowWhitelist.add(obj.get("id").getAsString());
                }
                case "mobRemove" -> {
                    GlowConfig.mobGlowWhitelist.remove(obj.get("id").getAsString());
                }
                case "refresh" -> {
                    pushState(browser);
                    return;
                }
                case "close" -> {
                    Minecraft.getInstance().setScreen(null);
                    return;
                }
                default -> {
                    mutated = false;
                    System.err.println("[glowdar] unknown op: " + op);
                }
            }
            if (mutated) {
                GlowConfig.save();
                pushState(browser);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void applyField(String key, com.google.gson.JsonElement v) {
        switch (key) {
            case "playerGlowEnabled"   -> GlowConfig.playerGlowEnabled   = v.getAsBoolean();
            case "playerGlowColor"     -> GlowConfig.playerGlowColor     = v.getAsInt();
            case "mobGlowEnabled"      -> GlowConfig.mobGlowEnabled      = v.getAsBoolean();
            case "mobGlowColor"        -> GlowConfig.mobGlowColor        = v.getAsInt();
            case "blockOutlineEnabled" -> GlowConfig.blockOutlineEnabled = v.getAsBoolean();
            case "defaultOutlineColor" -> GlowConfig.defaultOutlineColor = v.getAsInt();
            case "outlineLineWidth"    -> GlowConfig.outlineLineWidth    = v.getAsFloat();
            case "outlineOpacity"      -> GlowConfig.outlineOpacity      = v.getAsFloat();
            case "glowRange"           -> GlowConfig.glowRange           = v.getAsFloat();
            case "maxGlowEntities"     -> GlowConfig.maxGlowEntities     = v.getAsInt();
            case "chunkScanRate"       -> GlowConfig.chunkScanRate       = v.getAsInt();
            case "chunkRefreshInterval"-> GlowConfig.chunkRefreshInterval= v.getAsInt();
            case "espAnimMode"         -> GlowConfig.espAnimMode          = v.getAsString();
            case "guiOpacity"          -> GlowConfig.guiOpacity          = v.getAsFloat();
            case "guiAccentColor"      -> GlowConfig.guiAccentColor      = v.getAsInt();
        }
    }
}

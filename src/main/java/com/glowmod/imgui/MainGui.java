package com.glowmod.imgui;

import com.glowmod.GlowConfig;
import com.glowmod.render.BlockScanner;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainGui {

    private static List<String> blockIds = null;
    private static List<String> mobIds   = null;

    private static final ImString blockSearch = new ImString(64);
    private static final ImString mobSearch   = new ImString(64);

    private static String pickerOpenBlockId = null;
    private static int    pickerOriginalColor = 0;

    private MainGui() {}

    public static void draw() {
        if (!GuiHostScreen.isOpen()) return;
        ensureRegistryCaches();

        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        float w  = Math.min(820f, dw * 0.7f);
        float h  = Math.min(600f, dh * 0.78f);

        drawBackdrop(dw, dh);

        ImGui.setNextWindowSize(w, h, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos((dw - w) * 0.5f, (dh - h) * 0.5f, ImGuiCond.FirstUseEver);

        ImGuiTheme.pushFrostPanel();
        ImGui.begin("##glowmod_root",
                ImGuiWindowFlags.NoTitleBar |
                ImGuiWindowFlags.NoCollapse);
        ImGuiTheme.popFrostPanel();

        drawTitleBar();
        ImGui.spacing();

        if (ImGui.beginTabBar("##glowmod_tabs")) {
            if (ImGui.beginTabItem("Glow")) {
                drawGlowTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Outlines")) {
                drawOutlinesTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Performance")) {
                drawPerfTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Settings")) {
                drawSettingsTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }

        ImGui.end();
    }

    private static void drawBackdrop(float w, float h) {
        ImDrawList dl = ImGui.getBackgroundDrawList();
        dl.addRectFilled(0, 0, w, h, ImGuiTheme.COL_BG_DIM);
    }

    private static void drawTitleBar() {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 12f, 8f);
        ImGui.pushFont(ImGui.getFont());
        ImGui.textColored(0.96f, 0.97f, 0.99f, 1f, "GlowMod");
        ImGui.sameLine();
        ImGui.textColored(0.55f, 0.60f, 0.68f, 1f, "  •  frosted overlay");
        ImGui.sameLine();
        float close_w = 70f;
        ImGui.setCursorPosX(ImGui.getWindowWidth() - close_w - 16f);
        ImGui.pushStyleColor(ImGuiCol.Button,        0.30f, 0.10f, 0.12f, 0.6f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.55f, 0.18f, 0.22f, 0.9f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  0.70f, 0.22f, 0.26f, 1.0f);
        if (ImGui.button("Close", close_w, 0)) {
            net.minecraft.client.Minecraft.getInstance().setScreen(null);
        }
        ImGui.popStyleColor(3);
        ImGui.popFont();
        ImGui.popStyleVar();
        ImGui.separator();
    }

    private static void drawGlowTab() {
        ImGui.spacing();
        sectionHeader("Entities");

        boolean dirty = false;

        ImBoolean playerOn = new ImBoolean(GlowConfig.playerGlowEnabled);
        if (ImGui.checkbox("Player Glow", playerOn)) {
            GlowConfig.playerGlowEnabled = playerOn.get(); dirty = true;
        }
        ImGui.sameLine();
        ImGui.dummy(20, 0);
        ImGui.sameLine();
        if (colorEdit("##playerColor", "Player Color")) dirty = true;

        ImBoolean mobOn = new ImBoolean(GlowConfig.mobGlowEnabled);
        if (ImGui.checkbox("Mob Glow", mobOn)) {
            GlowConfig.mobGlowEnabled = mobOn.get(); dirty = true;
        }
        ImGui.sameLine();
        ImGui.dummy(20, 0);
        ImGui.sameLine();
        if (mobColorEdit()) dirty = true;

        ImGui.spacing();
        sectionHeader("Mob Whitelist");

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##mob_search", "Search mobs…", mobSearch);

        if (ImGui.beginChild("##mob_results", 0, 160, true)) {
            String q = mobSearch.get().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (String id : mobIds) {
                if (shown >= 30) break;
                if (!q.isEmpty() && !id.contains(q)) continue;
                boolean present = GlowConfig.mobGlowWhitelist.contains(id);
                if (present) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.45f, 0.25f, 0.6f);
                    if (ImGui.smallButton("✓##"+id)) {
                        GlowConfig.mobGlowWhitelist.remove(id); dirty = true;
                    }
                    ImGui.popStyleColor();
                } else {
                    if (ImGui.smallButton("+##"+id)) {
                        GlowConfig.mobGlowWhitelist.add(id); dirty = true;
                    }
                }
                ImGui.sameLine();
                ImGui.text(prettyId(id));
                shown++;
            }
        }
        ImGui.endChild();

        ImGui.spacing();
        sectionHeader("Active Mobs");
        if (ImGui.beginChild("##active_mobs", 0, 120, true)) {
            if (GlowConfig.mobGlowWhitelist.isEmpty()) {
                ImGui.textDisabled("no mobs whitelisted");
            } else {
                List<String> list = new ArrayList<>(GlowConfig.mobGlowWhitelist);
                for (String id : list) {
                    if (ImGui.smallButton("x##rm" + id)) {
                        GlowConfig.mobGlowWhitelist.remove(id); dirty = true;
                    }
                    ImGui.sameLine();
                    ImGui.text(prettyId(id));
                }
            }
        }
        ImGui.endChild();

        if (dirty) GlowConfig.save();
    }

    private static boolean colorEdit(String id, String label) {
        float[] rgb = unpackRGB(GlowConfig.playerGlowColor);
        ImGui.text(label);
        ImGui.sameLine();
        if (ImGui.colorEdit3(id, rgb)) {
            GlowConfig.playerGlowColor = packRGB(rgb);
            return true;
        }
        return false;
    }

    private static boolean mobColorEdit() {
        float[] rgb = unpackRGB(GlowConfig.mobGlowColor);
        ImGui.text("Mob Color");
        ImGui.sameLine();
        if (ImGui.colorEdit3("##mobColor", rgb)) {
            GlowConfig.mobGlowColor = packRGB(rgb);
            return true;
        }
        return false;
    }

    private static void drawOutlinesTab() {
        ImGui.spacing();
        sectionHeader("Outlines");
        boolean dirty = false;

        ImBoolean on = new ImBoolean(GlowConfig.blockOutlineEnabled);
        if (ImGui.checkbox("Enabled", on)) {
            GlowConfig.blockOutlineEnabled = on.get(); dirty = true;
        }

        float[] defColor = unpackRGB(GlowConfig.defaultOutlineColor);
        ImGui.text("Default Color");
        ImGui.sameLine();
        if (ImGui.colorEdit3("##defOutline", defColor)) {
            GlowConfig.defaultOutlineColor = packRGB(defColor); dirty = true;
        }

        float[] lw = { GlowConfig.outlineLineWidth };
        if (ImGui.sliderFloat("Line Width", lw, 0.5f, 6f, "%.1f")) {
            GlowConfig.outlineLineWidth = lw[0]; dirty = true;
        }

        float[] op = { GlowConfig.outlineOpacity };
        if (ImGui.sliderFloat("Opacity", op, 0.1f, 1f, "%.2f")) {
            GlowConfig.outlineOpacity = op[0]; dirty = true;
        }

        ImGui.spacing();
        sectionHeader("Block Whitelist");

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##block_search", "Search blocks…", blockSearch);

        if (ImGui.beginChild("##block_results", 0, 180, true)) {
            String q = blockSearch.get().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (String id : blockIds) {
                if (shown >= 50) break;
                if (!q.isEmpty() && !id.contains(q)) continue;
                boolean present = GlowConfig.blockWhitelist.containsKey(id);
                if (present) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.45f, 0.25f, 0.6f);
                    if (ImGui.smallButton("✓##b"+id)) {
                        GlowConfig.blockWhitelist.remove(id);
                        BlockScanner.rescanAll();
                        dirty = true;
                    }
                    ImGui.popStyleColor();
                } else {
                    if (ImGui.smallButton("+##b"+id)) {
                        GlowConfig.blockWhitelist.put(id, GlowConfig.defaultOutlineColor);
                        BlockScanner.rescanAll();
                        dirty = true;
                    }
                }
                ImGui.sameLine();
                ImGui.text(prettyId(id));
                shown++;
            }
        }
        ImGui.endChild();

        ImGui.spacing();
        sectionHeader("Active Blocks");

        if (ImGui.beginChild("##active_blocks", 0, 180, true)) {
            if (GlowConfig.blockWhitelist.isEmpty()) {
                ImGui.textDisabled("no blocks whitelisted");
            } else {
                Iterator<Map.Entry<String, Integer>> it = GlowConfig.blockWhitelist.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Integer> entry = it.next();
                    String id    = entry.getKey();
                    int    color = entry.getValue();

                    if (ImGui.smallButton("x##rmb" + id)) {
                        it.remove();
                        BlockScanner.rescanAll();
                        dirty = true;
                        continue;
                    }
                    ImGui.sameLine();
                    float[] rgb = unpackRGB(color);
                    if (ImGui.colorEdit3("##bc" + id, rgb)) {
                        entry.setValue(packRGB(rgb));
                        dirty = true;
                    }
                    ImGui.sameLine();
                    ImGui.text(prettyId(id));
                }
            }
        }
        ImGui.endChild();

        if (dirty) GlowConfig.save();
    }

    private static void drawPerfTab() {
        ImGui.spacing();
        sectionHeader("Entity Glow");
        boolean dirty = false;

        float[] gr = { GlowConfig.glowRange };
        if (ImGui.sliderFloat("Glow Range", gr, 16f, 256f, "%.0f blocks")) {
            GlowConfig.glowRange = gr[0]; dirty = true;
        }
        ImInt mx = new ImInt(GlowConfig.maxGlowEntities);
        if (ImGui.sliderInt("Max Glowing Entities", mx.getData(), 1, 64)) {
            GlowConfig.maxGlowEntities = mx.get(); dirty = true;
        }

        ImGui.spacing();
        sectionHeader("Block Outlines");
        ImInt sr = new ImInt(GlowConfig.chunkScanRate);
        if (ImGui.sliderInt("Chunk Scan Rate", sr.getData(), 1, 10)) {
            GlowConfig.chunkScanRate = sr.get(); dirty = true;
        }
        ImInt ri = new ImInt(GlowConfig.chunkRefreshInterval);
        if (ImGui.sliderInt("Refresh Interval (ticks)", ri.getData(), 20, 1200)) {
            GlowConfig.chunkRefreshInterval = ri.get(); dirty = true;
        }

        if (dirty) GlowConfig.save();
    }

    private static void drawSettingsTab() {
        ImGui.spacing();
        sectionHeader("Appearance");
        boolean dirty = false;

        float[] op = { GlowConfig.guiOpacity };
        if (ImGui.sliderFloat("Panel Opacity", op, 0.4f, 1.0f, "%.2f")) {
            GlowConfig.guiOpacity = op[0]; dirty = true;
        }

        float[] accent = unpackRGB(GlowConfig.guiAccentColor);
        ImGui.text("Accent Color");
        ImGui.sameLine();
        if (ImGui.colorEdit3("##accent", accent)) {
            GlowConfig.guiAccentColor = packRGB(accent);
            ImGuiTheme.apply();
            dirty = true;
        }

        ImGui.spacing();
        sectionHeader("Keybinds");
        ImGui.text("Press");
        ImGui.sameLine();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.85f, 0.90f, 1.00f, 1f);
        ImGui.text("[ G ]");
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.text("to toggle this panel (rebindable in vanilla Controls menu).");

        if (dirty) GlowConfig.save();
    }

    private static void sectionHeader(String label) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.62f, 0.70f, 0.85f, 1f);
        ImGui.text(label.toUpperCase(Locale.ROOT));
        ImGui.popStyleColor();
        ImGui.separator();
    }

    private static String prettyId(String id) {
        int colon = id.indexOf(':');
        String body = colon >= 0 ? id.substring(colon + 1) : id;
        return body.replace('_', ' ');
    }

    private static float[] unpackRGB(int rgb) {
        return new float[]{
            ((rgb >> 16) & 0xFF) / 255f,
            ((rgb >>  8) & 0xFF) / 255f,
            ( rgb        & 0xFF) / 255f
        };
    }

    private static int packRGB(float[] f) {
        int r = clamp(f[0]); int g = clamp(f[1]); int b = clamp(f[2]);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int)(v * 255f)));
    }

    private static void ensureRegistryCaches() {
        if (blockIds == null) {
            blockIds = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.BLOCK.keySet()) blockIds.add(id.toString());
            blockIds.sort(String::compareTo);
        }
        if (mobIds == null) {
            mobIds = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) mobIds.add(id.toString());
            mobIds.sort(String::compareTo);
        }
    }
}

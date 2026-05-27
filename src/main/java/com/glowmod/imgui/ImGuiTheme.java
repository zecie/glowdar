package com.glowmod.imgui;

import com.glowmod.GlowConfig;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;

public final class ImGuiTheme {

    public static final int COL_TEXT          = 0xFFEAEFF6;
    public static final int COL_TEXT_DIM      = 0x99B5BDC9;
    public static final int COL_PANEL_TOP     = 0xB81B1F2A;
    public static final int COL_PANEL_BOT     = 0xB8101421;
    public static final int COL_PANEL_BORDER  = 0x40FFFFFF;
    public static final int COL_SIDEBAR       = 0xC014182A;
    public static final int COL_BG_DIM        = 0x66000000;
    public static final int COL_DIVIDER       = 0x20FFFFFF;

    private ImGuiTheme() {}

    public static void apply() {
        ImGuiStyle s = ImGui.getStyle();

        s.setWindowRounding(14f);
        s.setChildRounding(10f);
        s.setFrameRounding(8f);
        s.setPopupRounding(10f);
        s.setScrollbarRounding(12f);
        s.setGrabRounding(8f);
        s.setTabRounding(8f);

        s.setWindowPadding(20f, 20f);
        s.setFramePadding(10f, 6f);
        s.setItemSpacing(10f, 10f);
        s.setItemInnerSpacing(8f, 6f);
        s.setIndentSpacing(20f);
        s.setScrollbarSize(10f);
        s.setGrabMinSize(14f);

        s.setWindowBorderSize(1f);
        s.setChildBorderSize(1f);
        s.setFrameBorderSize(0f);
        s.setPopupBorderSize(1f);

        s.setColor(ImGuiCol.WindowBg,           0.07f, 0.08f, 0.11f, 0.72f);
        s.setColor(ImGuiCol.ChildBg,            0.10f, 0.11f, 0.15f, 0.55f);
        s.setColor(ImGuiCol.PopupBg,            0.08f, 0.09f, 0.13f, 0.94f);
        s.setColor(ImGuiCol.Border,             1.00f, 1.00f, 1.00f, 0.12f);
        s.setColor(ImGuiCol.BorderShadow,       0.00f, 0.00f, 0.00f, 0.00f);

        s.setColor(ImGuiCol.Text,               0.92f, 0.94f, 0.97f, 1.00f);
        s.setColor(ImGuiCol.TextDisabled,       0.55f, 0.60f, 0.68f, 1.00f);

        s.setColor(ImGuiCol.FrameBg,            0.16f, 0.18f, 0.24f, 0.55f);
        s.setColor(ImGuiCol.FrameBgHovered,     0.20f, 0.23f, 0.30f, 0.70f);
        s.setColor(ImGuiCol.FrameBgActive,      0.24f, 0.28f, 0.36f, 0.85f);

        ImVec4 a = accent();
        s.setColor(ImGuiCol.CheckMark,          a.x, a.y, a.z, 1.00f);
        s.setColor(ImGuiCol.SliderGrab,         a.x, a.y, a.z, 0.95f);
        s.setColor(ImGuiCol.SliderGrabActive,   a.x, a.y, a.z, 1.00f);
        s.setColor(ImGuiCol.Button,             a.x, a.y, a.z, 0.28f);
        s.setColor(ImGuiCol.ButtonHovered,      a.x, a.y, a.z, 0.50f);
        s.setColor(ImGuiCol.ButtonActive,       a.x, a.y, a.z, 0.75f);
        s.setColor(ImGuiCol.Header,             a.x, a.y, a.z, 0.30f);
        s.setColor(ImGuiCol.HeaderHovered,      a.x, a.y, a.z, 0.45f);
        s.setColor(ImGuiCol.HeaderActive,       a.x, a.y, a.z, 0.65f);

        s.setColor(ImGuiCol.Tab,                  0.12f, 0.14f, 0.20f, 0.80f);
        s.setColor(ImGuiCol.TabHovered,           a.x, a.y, a.z, 0.55f);
        s.setColor(ImGuiCol.TabActive,            a.x, a.y, a.z, 0.85f);
        s.setColor(ImGuiCol.TabUnfocused,         0.10f, 0.11f, 0.15f, 0.80f);
        s.setColor(ImGuiCol.TabUnfocusedActive,   a.x * 0.6f, a.y * 0.6f, a.z * 0.6f, 0.85f);

        s.setColor(ImGuiCol.ScrollbarBg,        0.00f, 0.00f, 0.00f, 0.30f);
        s.setColor(ImGuiCol.ScrollbarGrab,      0.40f, 0.44f, 0.52f, 0.65f);
        s.setColor(ImGuiCol.ScrollbarGrabHovered, 0.50f, 0.54f, 0.62f, 0.85f);
        s.setColor(ImGuiCol.ScrollbarGrabActive,  0.60f, 0.64f, 0.72f, 1.00f);

        s.setColor(ImGuiCol.Separator,          1.00f, 1.00f, 1.00f, 0.10f);
        s.setColor(ImGuiCol.SeparatorHovered,   a.x, a.y, a.z, 0.50f);
        s.setColor(ImGuiCol.SeparatorActive,    a.x, a.y, a.z, 0.85f);

        s.setColor(ImGuiCol.ResizeGrip,         0.00f, 0.00f, 0.00f, 0.00f);
        s.setColor(ImGuiCol.ResizeGripHovered,  a.x, a.y, a.z, 0.40f);
        s.setColor(ImGuiCol.ResizeGripActive,   a.x, a.y, a.z, 0.75f);

        s.setColor(ImGuiCol.NavWindowingHighlight, a.x, a.y, a.z, 0.85f);
    }

    public static ImVec4 accent() {
        int c = GlowConfig.guiAccentColor;
        return new ImVec4(((c >> 16) & 0xFF) / 255f,
                          ((c >>  8) & 0xFF) / 255f,
                          ( c        & 0xFF) / 255f,
                          1f);
    }

    public static int packRGBA(int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        int a = Math.max(0, Math.min(255, (int)(alpha * 255f)));
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>  16) & 0xFF;
        int g = (argb >>   8) & 0xFF;
        int b =  argb         & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    public static void pushFrostPanel() {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 14f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 1f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.08f, 0.09f, 0.13f, GlowConfig.guiOpacity * 0.78f);
        ImGui.pushStyleColor(ImGuiCol.Border,   1.00f, 1.00f, 1.00f, 0.14f);
    }

    public static void popFrostPanel() {
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(2);
    }
}

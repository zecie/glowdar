package com.glowmod.imgui;

import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;

import com.glowmod.render.Esp;

public final class ImGuiBackend {

    private static boolean initialized = false;
    private static ImGuiImplGlfw imguiGlfw;
    private static ImGuiImplGl3  imguiGl3;

    private ImGuiBackend() {}

    public static void init() {
        if (initialized) return;
        long handle = Minecraft.getInstance().getWindow().handle();

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

        loadFonts(io);
        ImGuiTheme.apply();

        imguiGlfw = new ImGuiImplGlfw();
        imguiGlfw.init(handle, true);

        imguiGl3 = new ImGuiImplGl3();
        imguiGl3.init("#version 150");

        initialized = true;
    }

    private static void loadFonts(ImGuiIO io) {
        ImFontAtlas atlas = io.getFonts();
        atlas.clear();
        ImFontConfig cfg = new ImFontConfig();
        cfg.setSizePixels(16f);
        cfg.setOversampleH(3);
        cfg.setOversampleV(3);
        cfg.setPixelSnapH(true);
        atlas.addFontDefault(cfg);
        cfg.destroy();
        atlas.build();
    }

    public static void frame() {
        if (!initialized) return;
        if (imguiGlfw == null || imguiGl3 == null) return;

        imguiGlfw.newFrame();
        ImGui.newFrame();

        Esp.draw();
        MainGui.draw();

        ImGui.render();
        imguiGl3.renderDrawData(ImGui.getDrawData());
    }

    public static void shutdown() {
        if (!initialized) return;
        if (imguiGl3  != null) imguiGl3.shutdown();
        if (imguiGlfw != null) imguiGlfw.shutdown();
        ImGui.destroyContext();
        initialized = false;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}

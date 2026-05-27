package com.glowmod.imgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class GuiHostScreen extends Screen {

    private static GuiHostScreen current;

    public GuiHostScreen() {
        super(Component.literal("GlowMod"));
    }

    public static boolean isOpen() {
        return current != null;
    }

    @Override
    protected void init() {
        current = this;
    }

    @Override
    public void onClose() {
        current = null;
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}

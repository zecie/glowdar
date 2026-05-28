package com.glowmod.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public class GuiHostScreen extends Screen {

    private static final Method INVALIDATE;
    private int lastBrowserMouseX = 0;
    private int lastBrowserMouseY = 0;

    static {
        Method m = null;
        Class<?> c = org.cef.browser.CefBrowserOsr.class;
        while (c != null && m == null) {
            try {
                m = c.getDeclaredMethod("invalidate");
                m.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
        INVALIDATE = m;
    }

    public GuiHostScreen() {
        super(Component.literal("Glowdar"));
    }

    @Override
    protected void init() {
        super.init();
        MCEFBrowser b = BrowserHost.get();
        if (b != null) {
            Minecraft mc = Minecraft.getInstance();
            BrowserHost.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            b.setFocus(true);
            ConfigBridge.pushState(b);
        }
    }

    @Override
    public void resize(int w, int h) {
        super.resize(w, h);
        Minecraft mc = Minecraft.getInstance();
        BrowserHost.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    @Override
    public void onClose() {
        MCEFBrowser b = BrowserHost.get();
        if (b != null) b.setFocus(false);
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        if (!MCEF.isInitialized()) {
            drawCenter(graphics, "MCEF is still downloading Chromium. Watch the launcher progress.");
            return;
        }

        MCEFBrowser b = BrowserHost.get();
        if (b == null) {
            drawCenter(graphics, "Glowdar browser failed to start. Check log for details.");
            return;
        }

        if (INVALIDATE != null) {
            try { INVALIDATE.invoke(b); } catch (Throwable ignored) {}
        }
        b.sendMouseMove(lastBrowserMouseX, lastBrowserMouseY);

        if (!b.isTextureReady()) {
            drawCenter(graphics, "Loading…");
            return;
        }

        Identifier tex = b.getTextureIdentifier();
        if (tex != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, tex,
                    0, 0, 0f, 0f,
                    this.width, this.height,
                    this.width, this.height);
        }
    }

    private void drawCenter(GuiGraphics graphics, String text) {
        graphics.drawCenteredString(this.font, text, this.width / 2, this.height / 2 - 4, 0xFFD0D8E5);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
    }

    private int localMouseX(double x) {
        return (int) (x * Minecraft.getInstance().getWindow().getGuiScale());
    }

    private int localMouseY(double y) {
        return (int) (y * Minecraft.getInstance().getWindow().getGuiScale());
    }

    private boolean inBounds(double x, double y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height;
    }

    private void forceRepaint(MCEFBrowser b) {
        if (INVALIDATE != null) try { INVALIDATE.invoke(b); } catch (Throwable ignored) {}
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MCEFBrowser b = BrowserHost.get();
        if (b == null || !inBounds(event.x(), event.y())) return false;
        b.sendMousePress(localMouseX(event.x()), localMouseY(event.y()), event.button());
        b.setFocus(true);
        forceRepaint(b);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MCEFBrowser b = BrowserHost.get();
        if (b == null) return false;
        b.sendMouseRelease(localMouseX(event.x()), localMouseY(event.y()), event.button());
        forceRepaint(b);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        MCEFBrowser b = BrowserHost.get();
        if (b != null && inBounds(mouseX, mouseY)) {
            lastBrowserMouseX = localMouseX(mouseX);
            lastBrowserMouseY = localMouseY(mouseY);
            b.sendMouseMove(lastBrowserMouseX, lastBrowserMouseY);
            forceRepaint(b);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        MCEFBrowser b = BrowserHost.get();
        if (b == null || !inBounds(mouseX, mouseY)) return false;
        b.sendMouseWheel(localMouseX(mouseX), localMouseY(mouseY), vertical, 0);
        forceRepaint(b);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        MCEFBrowser b = BrowserHost.get();
        if (b == null) return false;
        b.sendKeyPress(event.key(), event.scancode(), event.modifiers());
        forceRepaint(b);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        MCEFBrowser b = BrowserHost.get();
        if (b == null) return false;
        b.sendKeyRelease(event.key(), event.scancode(), event.modifiers());
        forceRepaint(b);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        MCEFBrowser b = BrowserHost.get();
        if (b == null) return false;
        int cp = event.codepoint();
        if (cp <= 0 || cp > 0xFFFF) return false;
        b.sendKeyTyped((char) cp, event.modifiers());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

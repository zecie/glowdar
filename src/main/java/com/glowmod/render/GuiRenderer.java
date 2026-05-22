package com.glowmod.render;

import net.minecraft.client.gui.GuiGraphics;

public class GuiRenderer {

    public static void roundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int argb) {
        g.fill((int)x, (int)y, (int)(x + w), (int)(y + h), argb);
    }

    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, int steps) {
        for (int i = steps; i >= 1; i--) {
            float ex    = i * 1.4f;
            int   alpha = Math.max(0, (int)(50f * (1f - (float)i / (steps + 1))));
            g.fill((int)(x - ex),       (int)(y - ex * 0.2f + ex * 0.7f),
                   (int)(x + w + ex * 2), (int)(y + h + ex * 2), alpha << 24);
        }
    }
}

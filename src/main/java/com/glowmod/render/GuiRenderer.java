package com.glowmod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public class GuiRenderer {

    private static final int ARC_STEPS = 10;

    public static void roundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int argb) {
        PoseStack.Pose pose = g.pose().last();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        float a  = ((argb >> 24) & 0xFF) / 255f;
        float rr = ((argb >> 16) & 0xFF) / 255f;
        float rg = ((argb >>  8) & 0xFF) / 255f;
        float rb = ( argb        & 0xFF) / 255f;

        float rad = Math.min(radius, Math.min(w * 0.5f, h * 0.5f));

        quad(vc, pose, x + rad, y,       x + w - rad, y + h,       rr, rg, rb, a);
        quad(vc, pose, x,       y + rad, x + rad,     y + h - rad, rr, rg, rb, a);
        quad(vc, pose, x+w-rad, y + rad, x + w,       y + h - rad, rr, rg, rb, a);

        arc(vc, pose, x + rad,     y + rad,     rad, (float)Math.PI,         (float)(Math.PI * 1.5), rr, rg, rb, a);
        arc(vc, pose, x + w - rad, y + rad,     rad, (float)(Math.PI * 1.5), (float)(Math.PI * 2.0), rr, rg, rb, a);
        arc(vc, pose, x + w - rad, y + h - rad, rad, 0f,                     (float)(Math.PI * 0.5), rr, rg, rb, a);
        arc(vc, pose, x + rad,     y + h - rad, rad, (float)(Math.PI * 0.5), (float)Math.PI,         rr, rg, rb, a);
    }

    public static void shadow(GuiGraphics g, float x, float y, float w, float h, float radius, int steps) {
        for (int i = steps; i >= 1; i--) {
            float ex = i * 1.5f;
            int alpha = Math.max(0, (int)(55f * (1f - (float)i / (steps + 1))));
            roundedRect(g, x - ex, y - ex * 0.3f + ex * 0.8f, w + ex * 2, h + ex * 2, radius + ex, alpha << 24);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                              float x1, float y1, float x2, float y2,
                              float r, float g, float b, float a) {
        vc.addVertex(pose, x1, y1, 0f).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y2, 0f).setColor(r, g, b, a);
        vc.addVertex(pose, x2, y2, 0f).setColor(r, g, b, a);
        vc.addVertex(pose, x2, y1, 0f).setColor(r, g, b, a);
    }

    private static void arc(VertexConsumer vc, PoseStack.Pose pose,
                             float cx, float cy, float r,
                             float start, float end,
                             float rr, float rg, float rb, float a) {
        float step = (end - start) / ARC_STEPS;
        for (int i = 0; i < ARC_STEPS; i++) {
            float a1  = start + i * step;
            float a2  = start + (i + 1) * step;
            float p1x = cx + (float)Math.cos(a1) * r;
            float p1y = cy + (float)Math.sin(a1) * r;
            float p2x = cx + (float)Math.cos(a2) * r;
            float p2y = cy + (float)Math.sin(a2) * r;
            vc.addVertex(pose, cx,  cy,  0f).setColor(rr, rg, rb, a);
            vc.addVertex(pose, p1x, p1y, 0f).setColor(rr, rg, rb, a);
            vc.addVertex(pose, p2x, p2y, 0f).setColor(rr, rg, rb, a);
            vc.addVertex(pose, p2x, p2y, 0f).setColor(rr, rg, rb, a);
        }
    }
}

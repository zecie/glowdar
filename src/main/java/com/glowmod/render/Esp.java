package com.glowmod.render;

import com.glowmod.GlowConfig;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.Map;

public final class Esp {

    private static final int[][] EDGES = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
    };

    private static final float[][] GLOW_PASSES = {
            {10f, 0.05f},
            { 6f, 0.12f},
            { 3.5f, 0.28f},
            { 1.6f, 1.00f}
    };

    private Esp() {}

    public static void draw() {
        if (!FrameData.isValid()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ImDrawList dl = ImGui.getBackgroundDrawList();

        if (GlowConfig.blockOutlineEnabled) drawBlocks(dl);
        drawEntities(dl, mc);
    }

    private static void drawBlocks(ImDrawList dl) {
        Map<Long, Map<BlockPos, Integer>> data = BlockScanner.getBlocks();
        if (data.isEmpty()) return;

        double cullSq = 256.0 * 256.0;
        float alpha   = GlowConfig.outlineOpacity;

        Vec3[] corners = new Vec3[8];
        for (Map<BlockPos, Integer> chunkMap : data.values()) {
            for (Map.Entry<BlockPos, Integer> e : chunkMap.entrySet()) {
                BlockPos p = e.getKey();
                double dx = p.getX() + 0.5 - FrameData.camX;
                double dy = p.getY() + 0.5 - FrameData.camY;
                double dz = p.getZ() + 0.5 - FrameData.camZ;
                if (dx*dx + dy*dy + dz*dz > cullSq) continue;

                int rgb = e.getValue();
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(),
                                    p.getX() + 1, p.getY() + 1, p.getZ() + 1).inflate(0.002);
                drawBox(dl, box, rgb, alpha, corners);
            }
        }
    }

    private static void drawEntities(ImDrawList dl, Minecraft mc) {
        boolean wantPlayers = GlowConfig.playerGlowEnabled;
        boolean wantMobs    = GlowConfig.mobGlowEnabled && !GlowConfig.mobGlowWhitelist.isEmpty();
        if (!wantPlayers && !wantMobs) return;

        double range   = GlowConfig.glowRange;
        double rangeSq = range * range;
        Vec3 center    = mc.player.position();
        AABB box       = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);

        Vec3[] corners = new Vec3[8];
        int drawn = 0;
        int cap   = GlowConfig.maxGlowEntities;

        if (wantPlayers) {
            for (Player pl : mc.level.getEntitiesOfClass(Player.class, box)) {
                if (pl == mc.player) continue;
                if (drawn >= cap) break;
                if (pl.distanceToSqr(mc.player) > rangeSq) continue;
                drawBox(dl, pl.getBoundingBox().inflate(0.05), GlowConfig.playerGlowColor, 1f, corners);
                drawn++;
            }
        }

        if (wantMobs) {
            for (Mob m : mc.level.getEntitiesOfClass(Mob.class, box)) {
                if (drawn >= cap) break;
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
                if (id == null) continue;
                String idStr = id.toString();
                if (!GlowConfig.mobGlowWhitelist.contains(idStr)) continue;
                if (GlowConfig.disabledMobs.contains(idStr))      continue;
                if (m.distanceToSqr(mc.player) > rangeSq)         continue;
                drawBox(dl, m.getBoundingBox().inflate(0.05), GlowConfig.mobGlowColor, 1f, corners);
                drawn++;
            }
        }
    }

    private static void drawBox(ImDrawList dl, AABB box, int rgb, float alpha, Vec3[] reuse) {
        reuse[0] = new Vec3(box.minX, box.minY, box.minZ);
        reuse[1] = new Vec3(box.maxX, box.minY, box.minZ);
        reuse[2] = new Vec3(box.maxX, box.minY, box.maxZ);
        reuse[3] = new Vec3(box.minX, box.minY, box.maxZ);
        reuse[4] = new Vec3(box.minX, box.maxY, box.minZ);
        reuse[5] = new Vec3(box.maxX, box.maxY, box.minZ);
        reuse[6] = new Vec3(box.maxX, box.maxY, box.maxZ);
        reuse[7] = new Vec3(box.minX, box.maxY, box.maxZ);

        ImVec2[] proj = new ImVec2[8];
        boolean[] ok  = new boolean[8];
        boolean anyVisible = false;
        for (int i = 0; i < 8; i++) {
            proj[i] = new ImVec2();
            ok[i]   = project(reuse[i].x, reuse[i].y, reuse[i].z, proj[i]);
            if (ok[i]) anyVisible = true;
        }
        if (!anyVisible) return;

        float lineCore = GlowConfig.outlineLineWidth;

        for (float[] pass : GLOW_PASSES) {
            float thickness = pass[0] * (lineCore / 2.5f);
            float a = pass[1] * alpha;
            int color = pack(rgb, a);
            for (int[] edge : EDGES) {
                if (!ok[edge[0]] || !ok[edge[1]]) continue;
                ImVec2 p0 = proj[edge[0]];
                ImVec2 p1 = proj[edge[1]];
                dl.addLine(p0.x, p0.y, p1.x, p1.y, color, thickness);
            }
        }
    }

    private static boolean project(double wx, double wy, double wz, ImVec2 out) {
        float rx = (float)(wx - FrameData.camX);
        float ry = (float)(wy - FrameData.camY);
        float rz = (float)(wz - FrameData.camZ);
        Vector4f v = new Vector4f(rx, ry, rz, 1f);
        FrameData.modelView.transform(v);
        FrameData.projection.transform(v);
        if (v.w <= 0.0001f) return false;
        float ndcX = v.x / v.w;
        float ndcY = v.y / v.w;
        float dw = ImGui.getIO().getDisplaySizeX();
        float dh = ImGui.getIO().getDisplaySizeY();
        out.x = (ndcX * 0.5f + 0.5f) * dw;
        out.y = (1f - (ndcY * 0.5f + 0.5f)) * dh;
        return true;
    }

    private static int pack(int rgb, float alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        int a = Math.max(0, Math.min(255, (int)(alpha * 255f)));
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}

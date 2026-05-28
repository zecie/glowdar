package com.glowmod.render;

import com.glowmod.GlowConfig;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public final class Esp {

    private static final float[][] GLOW_PASSES = {
        {0.65f, 0.016f, 2.5f},
        {1.00f, 0.0f,   1.5f},
    };
    private static final float  OCCLUDED_ALPHA = 0.45f;
    private static final double BLOCK_CULL_SQ  = 384.0 * 384.0;

    private static RenderType espOccluded;
    private static RenderType espVisible;

    private static RenderType buildPipeline(String id, DepthTestFunction depth) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("glowdar", id))
                .withDepthTestFunction(depth)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .build();
        return RenderType.create(
                "glowdar_" + id,
                RenderSetup.builder(pipeline)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .setOutputTarget(OutputTarget.MAIN_TARGET)
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup()
        );
    }

    private Esp() {}

    public static void init() {
        espOccluded = buildPipeline("esp_occluded", DepthTestFunction.NO_DEPTH_TEST);
        espVisible  = buildPipeline("esp_visible",  DepthTestFunction.LEQUAL_DEPTH_TEST);
        WorldRenderEvents.END_MAIN.register(Esp::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!GlowConfig.blockOutlineEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack matrices = new PoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (float[] pass : GLOW_PASSES) {
            VertexConsumer lines = buffers.getBuffer(espOccluded);
            drawBlocks(matrices, lines, cam, pass[0] * OCCLUDED_ALPHA, pass[1], pass[2]);
            buffers.endBatch(espOccluded);
        }
        for (float[] pass : GLOW_PASSES) {
            VertexConsumer lines = buffers.getBuffer(espVisible);
            drawBlocks(matrices, lines, cam, pass[0], pass[1], pass[2]);
            buffers.endBatch(espVisible);
        }
    }

    private static void drawBlocks(PoseStack matrices, VertexConsumer lines, Vec3 cam,
                                   float alpha, float inflate, float lineWidthMult) {
        Map<Long, Map<BlockPos, Integer>> data = BlockScanner.getBlocks();
        if (data.isEmpty()) return;

        float finalAlpha = alpha * GlowConfig.outlineOpacity;

        for (Map<BlockPos, Integer> chunkMap : data.values()) {
            for (Map.Entry<BlockPos, Integer> e : chunkMap.entrySet()) {
                BlockPos p = e.getKey();
                double dx = p.getX() + 0.5 - cam.x;
                double dy = p.getY() + 0.5 - cam.y;
                double dz = p.getZ() + 0.5 - cam.z;
                if (dx * dx + dy * dy + dz * dz > BLOCK_CULL_SQ) continue;

                float widthBoost = 1.0f + (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.008f;
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(),
                                    p.getX() + 1, p.getY() + 1, p.getZ() + 1).inflate(inflate);
                VoxelShape shape = Shapes.create(box);
                int a = Mth.clamp((int)(finalAlpha * 255f), 0, 255);
                int color = (a << 24) | (e.getValue() & 0xFFFFFF);
                ShapeRenderer.renderShape(matrices, lines, shape,
                        -cam.x, -cam.y, -cam.z,
                        color, GlowConfig.outlineLineWidth * lineWidthMult * widthBoost);
            }
        }
    }
}

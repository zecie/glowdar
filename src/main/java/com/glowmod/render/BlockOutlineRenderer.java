package com.glowmod.render;

import com.glowmod.GlowConfig;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockOutlineRenderer {

    private static final int CHUNKS_PER_TICK = 3;

    private static final Map<Long, Map<BlockPos, Integer>> blocksByChunk = new ConcurrentHashMap<>();
    private static final Map<Long, LevelChunk> loadedChunks              = new ConcurrentHashMap<>();
    private static final Queue<Long> scanQueue                           = new ConcurrentLinkedQueue<>();

    private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("glowmod", "pipeline/lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create(
            "glowmod_lines_no_depth",
            RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            loadedChunks.put(key, chunk);
            if (!GlowConfig.blockWhitelist.isEmpty()) scanQueue.offer(key);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            loadedChunks.remove(key);
            blocksByChunk.remove(key);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || GlowConfig.blockWhitelist.isEmpty()) return;
            int processed = 0;
            while (processed < CHUNKS_PER_TICK) {
                Long key = scanQueue.poll();
                if (key == null) break;
                LevelChunk chunk = loadedChunks.get(key);
                if (chunk != null) scanChunk(chunk);
                processed++;
            }
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            if (!GlowConfig.blockOutlineEnabled || blocksByChunk.isEmpty()) return;

            Vec3 cam = context.gameRenderer().getMainCamera().position();
            PoseStack poseStack = context.matrices();
            VertexConsumer buffer = context.consumers().getBuffer(LINES_NO_DEPTH_LAYER);

            poseStack.pushPose();
            poseStack.translate(-cam.x, -cam.y, -cam.z);

            for (Map<BlockPos, Integer> posColors : blocksByChunk.values()) {
                for (Map.Entry<BlockPos, Integer> entry : posColors.entrySet()) {
                    BlockPos pos = entry.getKey();
                    int color = entry.getValue();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8)  & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    renderBox(poseStack, buffer,
                            new AABB(pos.getX(), pos.getY(), pos.getZ(),
                                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(0.0015),
                            r, g, b);
                }
            }

            poseStack.popPose();
            ((net.minecraft.client.renderer.MultiBufferSource.BufferSource) context.consumers()).endBatch();
        });
    }

    public static void rescanAll() {
        blocksByChunk.clear();
        scanQueue.clear();
        if (GlowConfig.blockWhitelist.isEmpty()) return;
        scanQueue.addAll(loadedChunks.keySet());
    }

    private static void scanChunk(LevelChunk chunk) {
        if (GlowConfig.blockWhitelist.isEmpty()) {
            blocksByChunk.remove(chunk.getPos().toLong());
            return;
        }

        int minY      = chunk.getMinY();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        Map<BlockPos, Integer> found = new HashMap<>();

        for (int sectionIdx = 0; sectionIdx < chunk.getSectionsCount(); sectionIdx++) {
            LevelChunkSection section = chunk.getSection(sectionIdx);
            if (section.hasOnlyAir()) continue;

            int sectionMinY = minY + sectionIdx * 16;

            for (int ly = 0; ly < 16; ly++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Block block = section.getBlockState(lx, ly, lz).getBlock();
                        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
                        if (key == null) continue;
                        Integer color = GlowConfig.blockWhitelist.get(key.toString());
                        if (color != null) {
                            found.put(new BlockPos(chunkMinX + lx, sectionMinY + ly, chunkMinZ + lz), color);
                        }
                    }
                }
            }
        }

        if (!found.isEmpty()) blocksByChunk.put(chunk.getPos().toLong(), found);
        else blocksByChunk.remove(chunk.getPos().toLong());
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer buffer, AABB box, float r, float g, float b) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        addLine(poseStack, buffer, x1, y1, z1, x2, y1, z1, r, g, b);
        addLine(poseStack, buffer, x1, y1, z2, x2, y1, z2, r, g, b);
        addLine(poseStack, buffer, x1, y1, z1, x1, y1, z2, r, g, b);
        addLine(poseStack, buffer, x2, y1, z1, x2, y1, z2, r, g, b);
        addLine(poseStack, buffer, x1, y2, z1, x2, y2, z1, r, g, b);
        addLine(poseStack, buffer, x1, y2, z2, x2, y2, z2, r, g, b);
        addLine(poseStack, buffer, x1, y2, z1, x1, y2, z2, r, g, b);
        addLine(poseStack, buffer, x2, y2, z1, x2, y2, z2, r, g, b);
        addLine(poseStack, buffer, x1, y1, z1, x1, y2, z1, r, g, b);
        addLine(poseStack, buffer, x2, y1, z1, x2, y2, z1, r, g, b);
        addLine(poseStack, buffer, x1, y1, z2, x1, y2, z2, r, g, b);
        addLine(poseStack, buffer, x2, y1, z2, x2, y2, z2, r, g, b);
    }

    private static void addLine(PoseStack poseStack, VertexConsumer buffer,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b) {
        PoseStack.Pose pose = poseStack.last();
        float dx = (float)(x2-x1), dy = (float)(y2-y1), dz = (float)(z2-z1);
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 1e-6f) return;
        buffer.addVertex(pose, (float)x1, (float)y1, (float)z1).setColor(r,g,b,1f).setNormal(pose,dx/len,dy/len,dz/len).setLineWidth(2.5f);
        buffer.addVertex(pose, (float)x2, (float)y2, (float)z2).setColor(r,g,b,1f).setNormal(pose,dx/len,dy/len,dz/len).setLineWidth(2.5f);
    }
}

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

public class BlockOutlineRenderer {

    private static int refreshTimer = 0;

    private static final Map<Long, Map<BlockPos, Integer>> blocksByChunk = new ConcurrentHashMap<>();
    private static final Map<Long, LevelChunk>             loadedChunks  = new ConcurrentHashMap<>();

    private static final Set<Long> scanQueue = ConcurrentHashMap.newKeySet();

    private static final Map<Block, Integer> blockColorLookup = new HashMap<>();

    private static final RenderPipeline FILLED_NO_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("glowmod", "pipeline/filled_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderType FILLED_NO_DEPTH_LAYER = RenderType.create(
            "glowmod_filled_no_depth",
            RenderSetup.builder(FILLED_NO_DEPTH_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            loadedChunks.put(key, chunk);
            if (!GlowConfig.blockWhitelist.isEmpty()) scanQueue.add(key);
        });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            long key = chunk.getPos().toLong();
            loadedChunks.remove(key);
            blocksByChunk.remove(key);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || GlowConfig.blockWhitelist.isEmpty()) return;

            refreshTimer++;
            if (refreshTimer >= GlowConfig.chunkRefreshInterval) {
                refreshTimer = 0;
                scanQueue.addAll(loadedChunks.keySet());
            }

            int processed = 0;
            Iterator<Long> it = scanQueue.iterator();
            while (processed < GlowConfig.chunkScanRate && it.hasNext()) {
                Long key = it.next();
                it.remove();
                LevelChunk chunk = loadedChunks.get(key);
                if (chunk != null) scanChunk(chunk);
                processed++;
            }
        });

        rebuildBlockLookup();

        WorldRenderEvents.END_MAIN.register(context -> {
            if (!GlowConfig.blockOutlineEnabled || blocksByChunk.isEmpty()) return;

            Vec3 cam = context.gameRenderer().getMainCamera().position();
            PoseStack poseStack = context.matrices();
            VertexConsumer buffer = context.consumers().getBuffer(FILLED_NO_DEPTH_LAYER);

            poseStack.pushPose();
            poseStack.translate(-cam.x, -cam.y, -cam.z);

            double cullRadSq = 256.0 * 256.0;

            for (Map<BlockPos, Integer> posColors : blocksByChunk.values()) {
                for (Map.Entry<BlockPos, Integer> entry : posColors.entrySet()) {
                    BlockPos pos = entry.getKey();
                    double dx = pos.getX() + 0.5 - cam.x;
                    double dy = pos.getY() + 0.5 - cam.y;
                    double dz = pos.getZ() + 0.5 - cam.z;
                    if (dx*dx + dy*dy + dz*dz > cullRadSq) continue;

                    int color = entry.getValue();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8)  & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    float a = GlowConfig.outlineOpacity;
                    renderFilledBox(poseStack, buffer,
                            new AABB(pos.getX(), pos.getY(), pos.getZ(),
                                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(0.0015),
                            r, g, b, a);
                }
            }

            poseStack.popPose();
            ((net.minecraft.client.renderer.MultiBufferSource.BufferSource) context.consumers()).endBatch();
        });
    }

    public static void rescanAll() {
        blocksByChunk.clear();
        scanQueue.clear();
        rebuildBlockLookup();
        if (GlowConfig.blockWhitelist.isEmpty()) return;
        for (LevelChunk chunk : loadedChunks.values()) {
            scanChunk(chunk);
        }
    }

    private static void rebuildBlockLookup() {
        blockColorLookup.clear();
        for (Map.Entry<String, Integer> entry : GlowConfig.blockWhitelist.entrySet()) {
            if (GlowConfig.disabledBlocks.contains(entry.getKey())) continue;
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            Block b = BuiltInRegistries.BLOCK.getValue(id);
            if (!id.equals(BuiltInRegistries.BLOCK.getKey(b))) continue;
            blockColorLookup.put(b, entry.getValue());
        }
    }

    private static void scanChunk(LevelChunk chunk) {
        if (blockColorLookup.isEmpty()) {
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
                        Integer color = blockColorLookup.get(block);
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

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        float x1 = (float)box.minX, y1 = (float)box.minY, z1 = (float)box.minZ;
        float x2 = (float)box.maxX, y2 = (float)box.maxY, z2 = (float)box.maxZ;
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(pose, x2, y1, z2).setColor(r, g, b, a);
    }
}

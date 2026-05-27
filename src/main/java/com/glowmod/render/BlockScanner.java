package com.glowmod.render;

import com.glowmod.GlowConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockScanner {

    private static int refreshTimer = 0;

    private static final Map<Long, Map<BlockPos, Integer>> blocksByChunk = new ConcurrentHashMap<>();
    private static final Map<Long, LevelChunk>             loadedChunks  = new ConcurrentHashMap<>();
    private static final Set<Long>                         scanQueue     = ConcurrentHashMap.newKeySet();
    private static final Map<Block, Integer>               blockLookup   = new HashMap<>();

    private BlockScanner() {}

    public static Map<Long, Map<BlockPos, Integer>> getBlocks() {
        return blocksByChunk;
    }

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
    }

    public static void rescanAll() {
        blocksByChunk.clear();
        scanQueue.clear();
        rebuildBlockLookup();
        if (GlowConfig.blockWhitelist.isEmpty()) return;
        for (LevelChunk chunk : loadedChunks.values()) scanChunk(chunk);
    }

    private static void rebuildBlockLookup() {
        blockLookup.clear();
        for (Map.Entry<String, Integer> entry : GlowConfig.blockWhitelist.entrySet()) {
            if (GlowConfig.disabledBlocks.contains(entry.getKey())) continue;
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null) continue;
            Block b = BuiltInRegistries.BLOCK.getValue(id);
            if (!id.equals(BuiltInRegistries.BLOCK.getKey(b))) continue;
            blockLookup.put(b, entry.getValue());
        }
    }

    private static void scanChunk(LevelChunk chunk) {
        if (blockLookup.isEmpty()) {
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
                        Integer color = blockLookup.get(block);
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
}

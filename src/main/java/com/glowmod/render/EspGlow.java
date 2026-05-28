package com.glowmod.render;

import com.glowmod.GlowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EspGlow {

    private static final ConcurrentHashMap<Integer, Integer> targets = new ConcurrentHashMap<>();
    private static final Set<Integer> ourGlowing = new HashSet<>();

    private EspGlow() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            clearGlow(mc);
            return;
        }

        double range = GlowConfig.glowRange;
        double rangeSq = range * range;
        Vec3 center = mc.player.position();
        AABB box = new AABB(
                center.x - range, center.y - range, center.z - range,
                center.x + range, center.y + range, center.z + range);

        ConcurrentHashMap<Integer, Integer> next = new ConcurrentHashMap<>();

        if (GlowConfig.playerGlowEnabled) {
            for (Player pl : mc.level.getEntitiesOfClass(Player.class, box)) {
                if (pl == mc.player) continue;
                if (pl.distanceToSqr(mc.player) > rangeSq) continue;
                next.put(pl.getId(), GlowConfig.playerGlowColor);
            }
        }

        if (GlowConfig.mobGlowEnabled && !GlowConfig.mobGlowWhitelist.isEmpty()) {
            for (Mob m : mc.level.getEntitiesOfClass(Mob.class, box)) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
                if (id == null) continue;
                String idStr = id.toString();
                if (!GlowConfig.mobGlowWhitelist.contains(idStr)) continue;
                if (GlowConfig.disabledMobs.contains(idStr)) continue;
                if (m.distanceToSqr(mc.player) > rangeSq) continue;
                next.put(m.getId(), GlowConfig.mobGlowColor);
            }
        }

        for (Integer id : ourGlowing) {
            if (!next.containsKey(id)) {
                Entity e = mc.level.getEntity(id);
                if (e != null) e.setGlowingTag(false);
            }
        }
        ourGlowing.clear();

        for (Integer id : next.keySet()) {
            Entity e = mc.level.getEntity(id);
            if (e != null) {
                e.setGlowingTag(true);
                ourGlowing.add(id);
            }
        }

        targets.clear();
        targets.putAll(next);
    }

    private static void clearGlow(Minecraft mc) {
        if (mc.level != null) {
            for (Integer id : ourGlowing) {
                Entity e = mc.level.getEntity(id);
                if (e != null) e.setGlowingTag(false);
            }
        }
        ourGlowing.clear();
        targets.clear();
    }

    public static boolean isTarget(Entity e) {
        return targets.containsKey(e.getId());
    }

    public static int getAnimatedColor(Entity e) {
        Integer base = targets.get(e.getId());
        if (base == null) return 0xFFFFFF;
        return animate(base);
    }

    private static int animate(int rgb) {
        long t = System.currentTimeMillis();
        return switch (GlowConfig.espAnimMode) {
            case "PULSE" -> {
                float phase = (float) (Math.sin(t * Math.PI / 900.0) * 0.5 + 0.5);
                float k = 0.35f + 0.65f * phase;
                int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
                int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
                int b = Math.min(255, (int) ((rgb & 0xFF) * k));
                yield (r << 16) | (g << 8) | b;
            }
            case "RAINBOW" -> Color.HSBtoRGB((t % 3600) / 3600f, 1f, 1f) & 0xFFFFFF;
            default -> rgb;
        };
    }
}

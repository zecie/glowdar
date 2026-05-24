package com.glowmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class EntityGlowManager {

    private static volatile int[] glowIds = new int[0];

    private static int           tickCounter  = 0;
    private static final int     UPDATE_EVERY = 3;

    private record Candidate(int id, double distSq) {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || mc.player == null) {
                glowIds = new int[0];
                tickCounter = 0;
                return;
            }

            if (++tickCounter < UPDATE_EVERY) return;
            tickCounter = 0;

            boolean wantPlayers = GlowConfig.playerGlowEnabled;
            boolean wantMobs    = GlowConfig.mobGlowEnabled && !GlowConfig.mobGlowWhitelist.isEmpty();
            if (!wantPlayers && !wantMobs) {
                glowIds = new int[0];
                return;
            }

            double range   = GlowConfig.glowRange;
            double rangeSq = range * range;
            Vec3   center  = mc.player.position();
            AABB   box     = new AABB(
                    center.x - range, center.y - range, center.z - range,
                    center.x + range, center.y + range, center.z + range);

            List<Candidate> candidates = new ArrayList<>();

            if (wantPlayers) {
                for (Player p : mc.level.getEntitiesOfClass(Player.class, box)) {
                    if (p == mc.player) continue;
                    double d = p.distanceToSqr(mc.player);
                    if (d <= rangeSq) candidates.add(new Candidate(p.getId(), d));
                }
            }

            if (wantMobs) {
                for (Mob mob : mc.level.getEntitiesOfClass(Mob.class, box)) {
                    Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                    if (id == null) continue;
                    String idStr = id.toString();
                    if (!GlowConfig.mobGlowWhitelist.contains(idStr)) continue;
                    if (GlowConfig.disabledMobs.contains(idStr)) continue;
                    double d = mob.distanceToSqr(mc.player);
                    if (d <= rangeSq) candidates.add(new Candidate(mob.getId(), d));
                }
            }

            candidates.sort(Comparator.comparingDouble(Candidate::distSq));

            int cap  = GlowConfig.maxGlowEntities;
            int size = Math.min(candidates.size(), cap);
            int[] next = new int[size];
            for (int i = 0; i < size; i++) next[i] = candidates.get(i).id();
            Arrays.sort(next);
            glowIds = next;
        });
    }

    public static boolean shouldGlow(int entityId) {
        int[] ids = glowIds;
        return ids.length > 0 && Arrays.binarySearch(ids, entityId) >= 0;
    }
}

package com.glowmod.mixin;

import com.glowmod.GlowConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) return;

        if (self instanceof Player) {
            if (!GlowConfig.playerGlowEnabled) return;
            if (self == Minecraft.getInstance().player) return;
            cir.setReturnValue(true);
            return;
        }

        if (self instanceof Mob && GlowConfig.mobGlowEnabled && !GlowConfig.mobGlowWhitelist.isEmpty()) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
            if (id != null && GlowConfig.mobGlowWhitelist.contains(id.toString())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) return;

        if (self instanceof Player) {
            if (!GlowConfig.playerGlowEnabled) return;
            if (self == Minecraft.getInstance().player) return;
            cir.setReturnValue(GlowConfig.playerGlowColor);
            return;
        }

        if (self instanceof Mob && GlowConfig.mobGlowEnabled && !GlowConfig.mobGlowWhitelist.isEmpty()) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
            if (id != null && GlowConfig.mobGlowWhitelist.contains(id.toString())) {
                cir.setReturnValue(GlowConfig.mobGlowColor);
            }
        }
    }
}

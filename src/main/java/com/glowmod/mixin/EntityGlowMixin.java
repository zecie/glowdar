package com.glowmod.mixin;

import com.glowmod.EntityGlowManager;
import com.glowmod.GlowConfig;
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
        Entity self = (Entity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (EntityGlowManager.shouldGlow(self.getId())) cir.setReturnValue(true);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity)(Object)this;
        if (!self.level().isClientSide()) return;
        if (!EntityGlowManager.shouldGlow(self.getId())) return;

        if (self instanceof Player && GlowConfig.playerGlowEnabled) {
            cir.setReturnValue(GlowConfig.playerGlowColor);
        } else if (self instanceof Mob && GlowConfig.mobGlowEnabled) {
            cir.setReturnValue(GlowConfig.mobGlowColor);
        }
    }
}

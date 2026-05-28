package com.glowmod.mixin;

import com.glowmod.render.EspGlow;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void espCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide() && EspGlow.isTarget(self)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void espTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level() != null && self.level().isClientSide() && EspGlow.isTarget(self)) {
            cir.setReturnValue(EspGlow.getAnimatedColor(self));
        }
    }
}

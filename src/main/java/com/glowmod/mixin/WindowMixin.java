package com.glowmod.mixin;

import com.glowmod.imgui.ImGuiBackend;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void glowmod$beforeSwap(CallbackInfo ci) {
        if (!ImGuiBackend.isInitialized()) ImGuiBackend.init();
        ImGuiBackend.frame();
    }
}

package com.glowmod;

import com.glowmod.imgui.GuiHostScreen;
import com.glowmod.render.BlockScanner;
import com.glowmod.render.FrameData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class GlowModClient implements ClientModInitializer {

    public static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        GlowConfig.load();
        BlockScanner.init();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.glowmod.open_gui",
                GLFW.GLFW_KEY_G,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.screen instanceof GuiHostScreen) client.setScreen(null);
                else                                       client.setScreen(new GuiHostScreen());
            }
        });

        WorldRenderEvents.END_EXTRACTION.register(ctx -> {
            Matrix4f proj = new Matrix4f(ctx.cullProjectionMatrix());
            Matrix4f view = new Matrix4f(ctx.viewMatrix());
            Vec3 p = ctx.camera().position();
            float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
            FrameData.update(proj, view, p.x, p.y, p.z, pt);
        });
    }
}

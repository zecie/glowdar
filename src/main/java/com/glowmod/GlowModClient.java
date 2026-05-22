package com.glowmod;

import com.glowmod.EntityGlowManager;
import com.glowmod.render.BlockOutlineRenderer;
import com.glowmod.screen.BlockWhitelistScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class GlowModClient implements ClientModInitializer {

    public static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        GlowConfig.load();
        BlockOutlineRenderer.init();
        EntityGlowManager.init();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.glowmod.open_gui",
                GLFW.GLFW_KEY_G,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.consumeClick()) {
                client.setScreen(new BlockWhitelistScreen(client.screen));
            }
        });
    }
}

package net.nj.lavasponge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hotkeys implements ClientModInitializer {

    public static final KeyBinding TOGGLE_LAVA_SPONGE_KEY = new KeyBinding(
            "key.lavasponge.toggle", GLFW.GLFW_KEY_G, "category.lavasponge"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger("LavaSponge");

    private BucketHack bucketHack;

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_LAVA_SPONGE_KEY);
        bucketHack = new BucketHack(MinecraftClient.getInstance());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (TOGGLE_LAVA_SPONGE_KEY.isPressed()) {
                //LOGGER.info("Lava Sponge Key Pressed!");
                bucketHack.useBucketer();
            }
        });
    }
}
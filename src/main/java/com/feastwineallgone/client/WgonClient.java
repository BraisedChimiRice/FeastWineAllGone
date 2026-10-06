package com.feastwineallgone.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Client-only registration entrypoint.
 *
 * Called through DistExecutor from the common mod constructor so dedicated
 * servers never need to load Minecraft client classes.
 */
public final class WgonClient {

    private WgonClient() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get()
                .registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (minecraft, parent) ->
                                        new WgonConfigScreen(parent)
                        )
                );
    }
}

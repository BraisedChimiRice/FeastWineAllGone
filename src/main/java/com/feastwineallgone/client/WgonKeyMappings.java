package com.feastwineallgone.client;

import com.feastwineallgone.FeastWineAllGone;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Rebindable observation-camera controls.
 *
 * These are real Minecraft key mappings, so players can change them through
 * Options -> Controls -> Key Binds.  The observation HUD reads the current
 * binding dynamically instead of hard-coding R/G into its labels.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class WgonKeyMappings {

    public static final String CATEGORY =
            "key.categories.feastwineallgone";

    public static final KeyMapping LOCATE_MAID =
            new KeyMapping(
                    "key.feastwineallgone.observation.locate_maid",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    CATEGORY
            );

    public static final KeyMapping SKIP_AND_SLEEP =
            new KeyMapping(
                    "key.feastwineallgone.observation.skip_sleep",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    CATEGORY
            );


    /**
     * Dedicated drunk-sleep interaction key.
     *
     * Outside close-up:
     *   find the nearest owned drunk-sleeping maid and enter face close-up.
     *
     * Inside close-up:
     *   poke her cheek and wake her.
     *
     * Default H, fully rebindable in Minecraft Controls.
     */
    public static final KeyMapping DRUNK_WAKE_INTERACT =
            new KeyMapping(
                    "key.feastwineallgone.drunk_wake.interact",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H,
                    CATEGORY
            );

    private WgonKeyMappings() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(
            RegisterKeyMappingsEvent event
    ) {
        event.register(LOCATE_MAID);
        event.register(SKIP_AND_SLEEP);
        event.register(DRUNK_WAKE_INTERACT);
    }
}

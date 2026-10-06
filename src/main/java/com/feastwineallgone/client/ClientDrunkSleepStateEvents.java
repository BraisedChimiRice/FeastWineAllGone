package com.feastwineallgone.client;

import com.feastwineallgone.FeastWineAllGone;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Prevent a previous world/session from leaving stale entity ids in the
 * client drunk-sleep mirror.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientDrunkSleepStateEvents {

    private ClientDrunkSleepStateEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        ClientDrunkSleepState.clearAll();
    }
}

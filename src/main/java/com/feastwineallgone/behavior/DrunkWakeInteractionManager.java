package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.network.WgonNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Owner interaction entry for the drunk-sleep face-poke sequence.
 *
 * First right-click:
 *   do NOT wake the maid.
 *   Cancel TLM's normal maid interaction and move the owner's camera to a
 *   close-up view of the sleeping maid.
 *
 * Second right-click is captured client-side by ClientObservationManager and
 * sent through PokeDrunkMaidC2S.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class DrunkWakeInteractionManager {

    private static final double MAX_START_DISTANCE_SQR = 64.0D;

    private DrunkWakeInteractionManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }

        if (!tryStartCloseup(player, maid)) {
            return;
        }

        /*
         * Right-click remains as a fallback, but the primary path is now the
         * dedicated rebindable drunk-wake key.
         */
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Shared server validation for both right-click and the dedicated key.
     */
    public static boolean tryStartCloseup(
            ServerPlayer player,
            EntityMaid maid
    ) {
        if (!DrunkSleepData.isActive(maid)) {
            return false;
        }

        if (maid.getOwnerUUID() == null
                || !maid.getOwnerUUID().equals(player.getUUID())) {
            return false;
        }

        if (player.level() != maid.level()
                || player.distanceToSqr(maid) > MAX_START_DISTANCE_SQR) {
            return false;
        }

        WgonNetwork.sendStartDrunkWake(
                player,
                maid.getId()
        );

        return true;
    }

}

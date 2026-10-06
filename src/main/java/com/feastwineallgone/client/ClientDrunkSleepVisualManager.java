package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Pose;

/**
 * Read-only helper used by Gecko/YSM compatibility.
 *
 * IMPORTANT:
 * This class never moves the maid. Previous experimental builds used a client
 * tick moveTo() as a safety net, but that fought server position packets and
 * produced visible up/down twitching.
 *
 * The synchronized state is the single client-side source of truth. Floor sleep itself is pose-only, with no real SleepingPos.
 */
public final class ClientDrunkSleepVisualManager {

    private ClientDrunkSleepVisualManager() {
    }

    public static boolean isFloorSleeping(
            EntityMaid maid
    ) {
        if (!ClientDrunkSleepState.isActive(maid)) {
            return false;
        }

        return maid.isSleeping()
                || maid.getPose() == Pose.SLEEPING;
    }
}

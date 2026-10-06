package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.DrunkSleepData;
import com.feastwineallgone.client.ClientDrunkSleepState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TLM's private EntityMaid#onMaidSleep() assumes every SleepingPos belongs to a
 * bed and moves the maid to:
 *
 *     sleepingPos.y + 0.5625
 *
 * every tick.
 *
 * FWAG deliberately uses a real SleepingPos on ordinary floor so model queries
 * such as query.is_sleeping remain true. The only thing we must suppress is
 * TLM's BED-SPECIFIC relocation.
 *
 * Server source of truth:
 *     DrunkSleepData persistent state.
 *
 * Client source of truth:
 *     ClientDrunkSleepState, explicitly mirrored by WgonNetwork.
 *
 * No coordinate guessing, no "nearby bed" heuristic and no client moveTo().
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidDrunkSleepMixin {

    @Inject(
            method = "onMaidSleep",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$skipBedOffsetDuringDrunkSleep(
            CallbackInfo ci
    ) {
        EntityMaid maid =
                (EntityMaid) (Object) this;

        if (maid.level().isClientSide) {
            if (ClientDrunkSleepState.isActive(maid)) {
                ci.cancel();
            }
            return;
        }

        if (DrunkSleepData.isActive(maid)) {
            ci.cancel();
        }
    }
}

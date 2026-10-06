package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.builtin.QueryBinding;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.IContext;
import com.feastwineallgone.client.ClientDrunkSleepState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TLM's Gecko/YSM Molang query "query.is_sleeping" is backed directly by
 * LivingEntity#isSleeping().
 *
 * FWAG floor sleep deliberately does NOT call startSleeping(), because a real
 * SleepingPos activates Minecraft/TLM bed-position semantics and produces the
 * stubborn +0.5625 hover.
 *
 * TLM's main sleep animation is already selected from Pose.SLEEPING, so the
 * only missing piece is this Molang query used by Wine Fox eyelids and other
 * sleep-only model details.
 *
 * For a synchronized FWAG drunk-sleep maid, report TRUE to this one model
 * query while leaving the actual LivingEntity sleeping flag false.
 */
@Mixin(value = QueryBinding.class, remap = false)
public abstract class QueryBindingDrunkSleepMixin {

    @Inject(
            method = "lambda$new$35",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void feastwineallgone$drunkSleepQuery(
            IContext context,
            CallbackInfoReturnable<Object> cir
    ) {
        Object entity = context.entity();

        if (entity instanceof EntityMaid maid
                && ClientDrunkSleepState.isActive(maid)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}

package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.DrunkSleepData;
import com.feastwineallgone.behavior.MaidActionLock;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TLM's follow-owner brain normally keeps assigning the owner as a
 * WALK_TARGET whenever the maid is in follow mode.
 *
 * While WGON owns any maid action, a native follow-owner WALK_TARGET can
 * fight WGON's explicit dining/drinking navigation. That tug-of-war is most
 * visible when a favorite is discovered immediately: the maid reaches the
 * food, then briefly surges or hops as two movement controllers disagree.
 *
 * Suppress only the follow task while a WGON action lock is active. The
 * user's actual follow/home configuration is never changed.
 */
@Mixin(
        value = MaidFollowOwnerTask.class,
        remap = false
)
public abstract class MaidFollowOwnerTaskMixin {

    @Inject(
            method = "checkExtraStartConditions",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$pauseFollowDuringWgonAction(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (DrunkSleepData.isActive(maid)
                || MaidActionLock.isLocked(maid)) {
            cir.setReturnValue(false);
        }
    }
}

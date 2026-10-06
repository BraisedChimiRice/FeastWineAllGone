package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearSleepTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.DrunkSleepData;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TLM normally stops sleeping when the maid's Brain leaves the REST activity.
 * FWAG drunk sleep intentionally survives into daytime until a real wake
 * condition occurs, so suppress that one native wake task while our marker is
 * active. FWAG keeps the real sleeping flag for model queries while a
 * separate EntityMaid mixin suppresses only TLM's bed-specific Y offset.
 */
@Mixin(
        value = MaidClearSleepTask.class,
        remap = false
)
public abstract class MaidClearSleepTaskMixin {

    @Inject(
            method = "start",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$keepDrunkMaidAsleep(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo ci
    ) {
        if (DrunkSleepData.isActive(maid)) {
            ci.cancel();
        }
    }
}

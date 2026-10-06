package com.feastwineallgone.mixin;

import com.feastwineallgone.behavior.NightStealManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Prevents vanilla from skipping directly to morning while
 * a Wine Gone One Night theft session is active.
 *
 * Once the maid has finished the session and returned to bed,
 * vanilla sleep processing is allowed to continue normally.
 */
@Mixin(SleepStatus.class)
public abstract class SleepStatusMixin {

    @Inject(
            method = "areEnoughDeepSleeping",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$holdNight(
            int requiredSleepPercentage,
            List<ServerPlayer> sleepingPlayers,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (NightStealManager.shouldHoldNight(sleepingPlayers)) {
            cir.setReturnValue(false);
        }
    }
}
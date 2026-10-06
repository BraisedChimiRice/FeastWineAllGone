package com.feastwineallgone.mixin;

import com.feastwineallgone.behavior.NightStealManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility guard for sleep/day mods that bypass vanilla SleepStatus
 * and advance Minecraft dayTime directly.
 *
 * The guard is active only during WGON's short pre-lock or an actual
 * midnight theft session. It does not freeze gameTime.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeMixin {

    @Inject(
            method = "setDayTime",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$guardNightTime(
            long proposedDayTime,
            CallbackInfo ci
    ) {
        ServerLevel level =
                (ServerLevel) (Object) this;

        if (NightStealManager
                .shouldBlockDayTimeChange(
                        level,
                        proposedDayTime
                )) {

            ci.cancel();
        }
    }
}

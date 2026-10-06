package com.feastwineallgone.mixin;

import com.feastwineallgone.client.ClientObservationManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While WGON owns the observation camera, the local player's normal gameplay
 * action dispatcher must stay dormant.  The camera reads its controls from
 * physical GLFW state / WGON key mappings instead, so cancelling vanilla's
 * gameplay dispatcher does not affect free-camera flight, locate, or skip.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftObservationInputMixin {

    @Inject(
            method = "handleKeybinds",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$blockGameplayKeybindActions(CallbackInfo ci) {
        if (ClientObservationManager.isActive()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "continueAttack",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$blockHeldBlockBreaking(
            boolean attackHeld,
            CallbackInfo ci
    ) {
        if (ClientObservationManager.isActive()) {
            ci.cancel();
        }
    }
}

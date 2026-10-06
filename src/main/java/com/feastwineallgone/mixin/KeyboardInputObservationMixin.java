package com.feastwineallgone.mixin;

import com.feastwineallgone.client.ClientObservationManager;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Free-camera WASD/Space/Shift are read directly from GLFW by
 * ClientObservationManager.  Zero the LocalPlayer movement input after
 * vanilla samples it so those same physical keys cannot move or crouch the
 * sleeping player's real body.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputObservationMixin {

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void feastwineallgone$freezeSleepingBodyInput(
            boolean slowDown,
            float slowDownFactor,
            CallbackInfo ci
    ) {
        if (!ClientObservationManager.isActive()) {
            return;
        }

        KeyboardInput self = (KeyboardInput) (Object) this;

        self.leftImpulse = 0.0F;
        self.forwardImpulse = 0.0F;
        self.up = false;
        self.down = false;
        self.left = false;
        self.right = false;
        self.jumping = false;
        self.shiftKeyDown = false;
    }
}

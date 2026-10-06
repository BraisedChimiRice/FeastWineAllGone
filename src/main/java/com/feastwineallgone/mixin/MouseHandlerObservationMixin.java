package com.feastwineallgone.mixin;

import com.feastwineallgone.client.ClientObservationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observation mouse routing for Minecraft 1.20.1.
 *
 * FIX21.17d deliberately separates two jobs:
 *
 * 1) onMove() is allowed to run normally.  At HEAD we only observe the raw
 *    GLFW cursor positions and derive our own relative delta.  We never
 *    cancel onMove(), so Minecraft can keep its internal mouse state valid.
 *
 * 2) turnPlayer() is cancelled while observation is active so the sleeping
 *    player's real body/head is not rotated.  WGON has already received the
 *    raw delta from onMove(), so camera look no longer depends on
 *    turnPlayer() being reached or on accumulatedDX/accumulatedDY surviving
 *    other client mods.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerObservationMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Unique
    private boolean feastwineallgone$rawLookInitialized = false;

    @Unique
    private double feastwineallgone$previousRawX = 0.0D;

    @Unique
    private double feastwineallgone$previousRawY = 0.0D;

    @Inject(
            method = "onMove",
            at = @At("HEAD")
    )
    private void feastwineallgone$captureObservationMouse(
            long window,
            double mouseX,
            double mouseY,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!ClientObservationManager.isActive()
                || minecraft.screen != null
                || !minecraft.mouseHandler.isMouseGrabbed()) {
            feastwineallgone$rawLookInitialized = false;
            return;
        }

        if (!feastwineallgone$rawLookInitialized) {
            feastwineallgone$rawLookInitialized = true;
            feastwineallgone$previousRawX = mouseX;
            feastwineallgone$previousRawY = mouseY;
            return;
        }

        double deltaX = mouseX - feastwineallgone$previousRawX;
        double deltaY = mouseY - feastwineallgone$previousRawY;

        feastwineallgone$previousRawX = mouseX;
        feastwineallgone$previousRawY = mouseY;

        ClientObservationManager.onMouseDelta(deltaX, deltaY);
    }

    @Inject(
            method = "turnPlayer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$keepSleepingBodyStill(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!ClientObservationManager.isActive()
                || minecraft.screen != null
                || !minecraft.mouseHandler.isMouseGrabbed()) {
            return;
        }

        // Do not leave stale movement for a later vanilla frame.
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;

        ci.cancel();
    }
}

package com.feastwineallgone.mixin;

import com.feastwineallgone.client.ClientObservationManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(
            double x,
            double y,
            double z
    );

    @Shadow
    protected abstract void setRotation(
            float yaw,
            float pitch
    );

    /**
     * Minecraft first performs its completely normal camera setup.
     *
     * WGON then overrides only the final camera transform.
     *
     * No fake camera Entity.
     * No ArmorStand interpolation.
     * No setCameraEntity battle.
     */
    @Inject(
            method = "setup",
            at = @At("TAIL")
    )
    private void feastwineallgone$applyObservationCamera(
            BlockGetter level,
            Entity cameraEntity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        if (!ClientObservationManager.isCameraOverrideActive()) {
            return;
        }

        Vec3 position =
                ClientObservationManager.getCameraPosition();

        this.setPosition(
                position.x,
                position.y,
                position.z
        );

        this.setRotation(
                ClientObservationManager.getCameraYaw(),
                ClientObservationManager.getCameraPitch()
        );
    }
}

package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.IContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.util.StringPool;
import com.github.tartaricacid.touhoulittlemaid.molang.runtime.ExecutionContext;
import com.feastwineallgone.client.ClientWakeStretchState;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V7 native surface route for Sleepwear Wine Fox.
 *
 * The sleepwear model already contains an authored "parallel4" wake stretch.
 * It animates:
 *
 *   SleepStretchUpBody / UpperBody / Head
 *   SleepStretchLeftArm / LeftForeArm
 *   SleepStretchRightArm / RightForeArm
 *   Left/RightEyelid
 *   Left/RightEyebrow
 *
 * Those channels are controlled by:
 *
 *   v.sw_stretch
 *   v.sw_t
 *
 * The model's pre_animation script currently fails to parse on TLM 1.5.3
 * because it contains a block-style Molang expression that this parser rejects.
 * That leaves sw_stretch/sw_t at their default values, so the authored
 * parallel4 animation never reaches the visible model.
 *
 * Instead of replacing that animation with another controller, FWAG overrides
 * only the READS of these two variables while our wake state is active.
 * This lets the model itself render the original body and facial keyframes.
 */
@Mixin(
        targets = "com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.binding.variable.ScopedVariableBinding$ScopedVariable",
        remap = false
)
public abstract class ScopedVariableWakeStretchMixin {

    @Unique
    private static final Logger feastwineallgone$LOGGER =
            LogUtils.getLogger();

    @Unique
    private static final String feastwineallgone$SLEEPWEAR_WINEFOX_MODEL =
            "sleepwear_winefox:sleepwear_winefox";

    @Unique
    private static final Set<Integer> feastwineallgone$LOGGED =
            ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private int name;

    @Inject(
            method = "evaluate",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$forceNativeWakeStretch(
            ExecutionContext<?> executionContext,
            CallbackInfoReturnable<Object> cir
    ) {
        /*
         * Fast rejection first because ScopedVariable.evaluate is hot code.
         */
        String variableName =
                StringPool.getString(this.name);

        if (!"sw_stretch".equals(variableName)
                && !"sw_t".equals(variableName)) {
            return;
        }

        Object evaluationEntity =
                executionContext.entity();

        if (!(evaluationEntity instanceof IContext<?> context)) {
            return;
        }

        Object contextEntity =
                context.entity();

        if (!(contextEntity instanceof EntityMaid maid)) {
            return;
        }

        if (!feastwineallgone$SLEEPWEAR_WINEFOX_MODEL.equals(
                maid.getModelId()
        )) {
            feastwineallgone$LOGGED.remove(maid.getId());
            return;
        }

        if (!ClientWakeStretchState.isActive(maid)) {
            feastwineallgone$LOGGED.remove(maid.getId());
            return;
        }

        if ("sw_stretch".equals(variableName)) {
            boolean active =
                    ClientWakeStretchState.isAnimationPhase(maid);

            if (active
                    && feastwineallgone$LOGGED.add(maid.getId())) {
                feastwineallgone$LOGGER.info(
                        "[WGON WakeStretch] NATIVE MOLANG surface route ACTIVE entityId={} modelId={}",
                        maid.getId(),
                        maid.getModelId()
                );
            }

            cir.setReturnValue(
                    active
                            ? 1.0D
                            : 0.0D
            );
            return;
        }

        /*
         * The authored sleepwear timeline is exactly 0 -> 5.5 seconds.
         */
        cir.setReturnValue(
                ClientWakeStretchState.animationElapsedSeconds(maid)
        );
    }
}

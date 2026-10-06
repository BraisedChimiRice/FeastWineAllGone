package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.api.animation.ICustomAnimation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.world.entity.Mob;

/**
 * Compatibility correction for the custom "sleepwear_winefox" Gecko model.
 *
 * That model intentionally contains several bed-oriented sleep variants.
 * Its sleep animation randomly sets Root Y to values such as +10 / +13 model
 * units for quilt poses, while the ordinary floor-safe Wine Fox sleep baseline
 * is -5. On a bed this looks correct; on bare floor it visually floats by
 * roughly half a block after render_entity_scale=0.65 is applied.
 *
 * During an actual floor sleep we normalize only positive/raised Root Y values
 * back to the model's own -5 baseline. Normal bed sleeping and all other
 * models are untouched.
 */
public final class DrunkSleepFloorAnimation
        implements ICustomAnimation<Mob> {

    private static final String SLEEPWEAR_WINEFOX_MODEL =
            "sleepwear_winefox:sleepwear_winefox";

    private static final float FLOOR_ROOT_Y = -5.0F;

    @Override
    public void setGeckoRotationAngles(
            Mob entity,
            AnimatedGeoModel model,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(entity instanceof EntityMaid maid)) {
            return;
        }

        if (!SLEEPWEAR_WINEFOX_MODEL.equals(
                maid.getModelId()
        )) {
            return;
        }

        if (!ClientDrunkSleepVisualManager
                .isFloorSleeping(maid)) {
            return;
        }

        AnimatedGeoBone root =
                model.bones().get("Root");

        if (root == null) {
            return;
        }

        /*
         * Leave the model's already-lowered normal/prone sleep variants alone.
         * Only the quilt/fox variants that lift Root upward are normalized.
         */
        if (root.getPositionY() > FLOOR_ROOT_Y) {
            root.setPositionY(FLOOR_ROOT_Y);
        }
    }
}

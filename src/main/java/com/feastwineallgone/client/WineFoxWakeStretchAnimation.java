package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.api.animation.ICustomAnimation;
import com.github.tartaricacid.touhoulittlemaid.api.animation.IModelRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.snapshot.BoneSnapshot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Wine Fox wake-up stretch animation.
 *
 * Why this is a hardcoded overlay instead of a model-id-specific Molang hack:
 *
 * 1. TLM's built-in Wine Fox family and third-party Wine Fox packs do not all
 *    expose the same "parallel" variables.
 * 2. They DO share the characteristic Wine Fox humanoid skeleton:
 *       UpBody / UpperBody / Head
 *       LeftArm / LeftForeArm
 *       RightArm / RightForeArm
 * 3. TLM's official ICustomAnimation hook is invoked for both the classic
 *    model renderer path and the Gecko/YSM-compatible renderer path.
 *
 * The keyframes below are based on the stretch segment from the original TLM
 * Wine Fox idle animation (roughly 16.67s -> 22.17s), shifted onto a clean
 * 0 -> 5.5 second wake timeline.
 *
 * V5 treats wake stretch as an EXCLUSIVE body animation rather than an
 * additive overlay.
 *
 * Why:
 * Gecko has already evaluated the normal sleep/idle controllers before TLM
 * calls ICustomAnimation. If FWAG simply adds rotations, the tail end of the
 * sleep animation and the wake stretch are literally composed together.
 *
 * During the short wake sequence FWAG therefore restores the core body bones
 * to their model-authored initial transforms first, then applies the stretch.
 * Clothing, ears and other decorative bones outside the core list remain under
 * the skin author's own controllers.
 */
public final class WineFoxWakeStretchAnimation
        implements ICustomAnimation<Mob> {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final float DEG_TO_RAD =
            Mth.DEG_TO_RAD;

    private static final String SLEEPWEAR_WINEFOX_MODEL =
            "sleepwear_winefox:sleepwear_winefox";

    /**
     * Bones whose previous sleep/idle controller transforms must not leak into
     * the authored wake stretch.
     *
     * Names that do not exist in a particular Wine Fox model are ignored.
     */
    private static final String[] EXCLUSIVE_CORE_BONES = {
            "MRoot",
            "Root",
            "MAllbody",
            "AllBody",
            "DownBody",

            "MUpBody",
            "SleepStretchUpBody",
            "UpBody",

            "MUpperBody",
            "SleepStretchUpperBody",
            "UpperBody",

            "MHead",
            "DozeNod",
            "AllHead",
            "SleepStretchHead",
            "Head",

            "Arm",
            "SleepStretchLeftArm",
            "LeftArm",
            "SleepStretchLeftForeArm",
            "LeftForeArm",
            "SleepStretchRightArm",
            "RightArm",
            "SleepStretchRightForeArm",
            "RightForeArm",

            "LeftLeg",
            "LeftLowerLeg",
            "LeftFoot",
            "RightLeg",
            "RightLowerLeg",
            "RightFoot",

            "LeftEyelid",
            "RightEyelid",
            "LeftEyebrow",
            "RightEyebrow"
    };

    private static final Frame[] UP_BODY = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.292F, 8.00F, 0.00F, 0.00F),
            f(0.667F, 9.66F, 0.00F, 0.00F),
            f(0.917F, 3.44F, 0.00F, 0.00F),
            f(1.250F, -7.50F, 0.00F, 0.00F),
            f(2.375F, -10.00F, 0.50F, -3.00F),
            f(3.042F, -9.61F, 0.97F, 1.93F),
            f(3.500F, -8.25F, 5.00F, 2.30F),
            f(3.958F, -0.35F, -4.50F, 0.00F),
            f(4.458F, 0.00F, -1.00F, -0.50F),
            f(5.000F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] UPPER_BODY = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.625F, 17.50F, 0.00F, 0.00F),
            f(1.250F, -10.00F, 0.00F, 0.00F),
            f(1.875F, -13.94F, -0.74F, 0.50F),
            f(2.542F, -10.00F, -1.20F, -1.00F),
            f(3.208F, -2.64F, 6.82F, -0.64F),
            f(3.792F, -1.64F, -6.76F, -0.50F),
            f(4.292F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] HEAD = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.625F, 20.00F, 0.00F, 0.00F),
            f(1.250F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] RIGHT_ARM = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.333F, -10.77F, -14.95F, 18.82F),
            f(0.625F, -41.98F, -23.90F, 31.56F),
            f(0.917F, -64.05F, 6.39F, 5.55F),
            f(1.083F, -75.36F, 65.31F, 39.03F),
            f(1.250F, -35.69F, 83.70F, 96.30F),
            f(1.833F, -27.39F, 90.50F, 111.40F),
            f(3.125F, -22.49F, 83.40F, 99.30F),
            f(3.750F, -13.87F, 53.41F, 65.33F),
            f(4.292F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] LEFT_ARM = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.333F, -10.77F, 14.95F, -18.82F),
            f(0.625F, -18.68F, 7.29F, -20.49F),
            f(0.917F, -64.05F, -6.39F, -5.55F),
            f(1.083F, -75.36F, -65.31F, -39.03F),
            f(1.250F, -35.69F, -83.70F, -96.30F),
            f(1.833F, -27.39F, -90.50F, -111.40F),
            f(3.125F, -22.49F, -83.40F, -99.30F),
            f(3.750F, -13.87F, -53.41F, -65.33F),
            f(4.292F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] RIGHT_FORE_ARM = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.625F, -107.50F, 0.00F, 0.00F),
            f(0.917F, -87.97F, 0.00F, 0.00F),
            f(1.250F, 0.00F, 0.00F, 0.00F),
            f(3.083F, 0.00F, 0.00F, 0.00F),
            f(3.667F, -33.93F, 0.00F, 0.00F),
            f(4.125F, -30.00F, 0.00F, 0.00F),
            f(4.542F, -7.41F, 0.00F, 0.00F),
            f(5.000F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    private static final Frame[] LEFT_FORE_ARM = {
            f(0.000F, 0.00F, 0.00F, 0.00F),
            f(0.625F, -130.00F, 0.00F, 0.00F),
            f(0.917F, -87.97F, 0.00F, 0.00F),
            f(1.250F, 0.00F, 0.00F, 0.00F),
            f(3.083F, 0.00F, 0.00F, 0.00F),
            f(3.667F, -33.93F, 0.00F, 0.00F),
            f(4.125F, -30.00F, 0.00F, 0.00F),
            f(4.542F, -7.41F, 0.00F, 0.00F),
            f(5.000F, 0.00F, 0.00F, 0.00F),
            f(5.500F, 0.00F, 0.00F, 0.00F)
    };

    @Override
    public void setRotationAngles(
            Mob entity,
            HashMap<String, ? extends IModelRenderer> parts,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(entity instanceof EntityMaid maid)
                || !shouldAnimate(maid)) {
            return;
        }

        /*
         * Hardcoded animations run after TLM's normal animation controller.
         * Restore core bones first so the previous sleep/idle frame cannot be
         * blended with this wake animation.
         */
        resetClassicCore(parts);

        if (!ClientWakeStretchState.isAnimationPhase(maid)) {
            return;
        }

        float t =
                (float) ClientWakeStretchState.animationElapsedSeconds(maid);

        /*
         * Classic TLM BedrockPart rotations use DEGREES.
         * V2 incorrectly multiplied this branch by DEG_TO_RAD, making the
         * movement nearly invisible on classic model renderers.
         */
        applyClassic(
                firstPart(parts, "SleepStretchUpBody", "UpBody"),
                sample(UP_BODY, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchUpperBody", "UpperBody"),
                sample(UPPER_BODY, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchHead", "Head"),
                sample(HEAD, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchRightArm", "RightArm"),
                sample(RIGHT_ARM, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchRightForeArm", "RightForeArm"),
                sample(RIGHT_FORE_ARM, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchLeftArm", "LeftArm"),
                sample(LEFT_ARM, t)
        );
        applyClassic(
                firstPart(parts, "SleepStretchLeftForeArm", "LeftForeArm"),
                sample(LEFT_FORE_ARM, t)
        );

        logClassicFirstFrame(maid, parts);
    }

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
        if (!(entity instanceof EntityMaid maid)
                || !shouldAnimate(maid)) {
            return;
        }

        Map<String, AnimatedGeoBone> bones =
                model.bones();

        /*
         * V8: Sleepwear Wine Fox must be NATIVE-ONLY here.
         *
         * ScopedVariableWakeStretchMixin already forces v.sw_stretch / v.sw_t,
         * so the model's own parallel4 has finished evaluating BEFORE this
         * ICustomAnimation hook runs.
         *
         * V7 then reset the same SleepStretch* bones and wrote the Java
         * fallback on top. That destroyed the authored native result and made
         * the arm motion appear reversed.
         *
         * Do not touch those bones at all for the sleepwear model. Its own
         * parallel4 controls torso, head, both arms/forearms, eyelids and
         * eyebrows exactly as authored.
         */
        if (SLEEPWEAR_WINEFOX_MODEL.equals(maid.getModelId())) {
            return;
        }

        /*
         * IMPORTANT:
         * TLM calls this hook AFTER Gecko has evaluated the normal controllers.
         * That means this is the correct place to erase the remaining sleep
         * pose before the wake stretch is drawn.
         *
         * Resetting only Root Y was not enough: the sleep animation also owns
         * Root rotation, AllBody, torso, arms, head and legs. The old V4 left
         * those transforms alive, so the wake stretch was visibly stacked on
         * top of the tail end of the sleep animation.
         */
        resetGeckoCore(bones);

        if (!ClientWakeStretchState.isAnimationPhase(maid)) {
            return;
        }

        float t =
                (float) ClientWakeStretchState.animationElapsedSeconds(maid);

        /*
         * Sleepwear/YSM Wine Fox variants may deliberately wrap the ordinary
         * body bones in dedicated SleepStretch* parents. Prefer those wrappers
         * so the wake animation does not fight the model's normal idle/walk
         * controllers. Original TLM Wine Fox has no wrappers, so we fall back
         * to its standard bones.
         */
        applyGecko(
                firstBone(bones, "SleepStretchUpBody", "UpBody"),
                sample(UP_BODY, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchUpperBody", "UpperBody"),
                sample(UPPER_BODY, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchHead", "Head"),
                sample(HEAD, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchRightArm", "RightArm"),
                sample(RIGHT_ARM, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchRightForeArm", "RightForeArm"),
                sample(RIGHT_FORE_ARM, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchLeftArm", "LeftArm"),
                sample(LEFT_ARM, t)
        );
        applyGecko(
                firstBone(bones, "SleepStretchLeftForeArm", "LeftForeArm"),
                sample(LEFT_FORE_ARM, t)
        );

        /*
         * The original TLM Wine Fox stretch is not body-only.
         * Its idle animation also closes both eyelids and lowers both eyebrows
         * during the two "squeeze" beats of the stretch.
         *
         * V8 copied the torso/arm/head keyframes but did not drive these face
         * bones, so geckolib:winefox moved correctly while keeping its normal
         * idle expression. If a Wine Fox model exposes the standard face bones,
         * reproduce the authored expression here as well.
         *
         * Sleepwear Wine Fox never reaches this branch: it returned above and
         * keeps using its model-native parallel4 face animation.
         */
        applyWineFoxFaceExpression(
                bones,
                t
        );

        logGeckoFirstFrame(maid, bones);
    }

    private static boolean shouldAnimate(
            EntityMaid maid
    ) {
        if (!ClientWakeStretchState.isActive(maid)) {
            return false;
        }

        if (WineFoxModelFamily.isWineFox(maid)) {
            return true;
        }

        if (ClientWakeStretchState.markFamilyRejected(maid)) {
            LOGGER.warn(
                    "[WGON WakeStretch] active packet rejected by WineFox matcher: entityId={} modelId={} isYsm={} ysmId={} ysmTexture={} ysmName={}",
                    maid.getId(),
                    maid.getModelId(),
                    maid.isYsmModel(),
                    maid.getYsmModelId(),
                    maid.getYsmModelTexture(),
                    maid.getYsmModelName() == null
                            ? "<null>"
                            : maid.getYsmModelName().getString()
            );
        }

        return false;
    }

    private static IModelRenderer firstPart(
            Map<String, ? extends IModelRenderer> parts,
            String preferred,
            String fallback
    ) {
        IModelRenderer part =
                parts.get(preferred);

        return part != null
                ? part
                : parts.get(fallback);
    }

    private static AnimatedGeoBone firstBone(
            Map<String, AnimatedGeoBone> bones,
            String preferred,
            String fallback
    ) {
        AnimatedGeoBone bone =
                bones.get(preferred);

        return bone != null
                ? bone
                : bones.get(fallback);
    }

    private static void resetClassicCore(
            Map<String, ? extends IModelRenderer> parts
    ) {
        for (String name : EXCLUSIVE_CORE_BONES) {
            IModelRenderer part =
                    parts.get(name);

            if (part == null) {
                continue;
            }

            part.setRotateAngleX(
                    part.getInitRotateAngleX()
            );
            part.setRotateAngleY(
                    part.getInitRotateAngleY()
            );
            part.setRotateAngleZ(
                    part.getInitRotateAngleZ()
            );
        }
    }

    private static void resetGeckoCore(
            Map<String, AnimatedGeoBone> bones
    ) {
        for (String name : EXCLUSIVE_CORE_BONES) {
            AnimatedGeoBone bone =
                    bones.get(name);

            if (bone == null) {
                continue;
            }

            BoneSnapshot initial =
                    bone.getInitialSnapshot();

            if (initial == null) {
                continue;
            }

            /*
             * Restore the exact model-authored bind transform. This handles
             * BOTH positive and negative Root offsets, plus lingering sleep
             * rotations on the torso/head/limbs.
             */
            bone.setPosition(
                    initial.positionOffsetX,
                    initial.positionOffsetY,
                    initial.positionOffsetZ
            );
            bone.setRotation(
                    initial.rotationValueX,
                    initial.rotationValueY,
                    initial.rotationValueZ
            );
            bone.setScale(
                    initial.scaleValueX,
                    initial.scaleValueY,
                    initial.scaleValueZ
            );
        }
    }

    private static void applyClassic(
            IModelRenderer part,
            Vec3Deg rotation
    ) {
        if (part == null) {
            return;
        }

        /*
         * Absolute over the model's authored bind angle, not over the result of
         * the previous controller.
         */
        part.setRotateAngleX(
                part.getInitRotateAngleX()
                        + rotation.x()
        );
        part.setRotateAngleY(
                part.getInitRotateAngleY()
                        + rotation.y()
        );
        part.setRotateAngleZ(
                part.getInitRotateAngleZ()
                        + rotation.z()
        );
    }

    private static void applyGecko(
            AnimatedGeoBone bone,
            Vec3Deg rotation
    ) {
        if (bone == null) {
            return;
        }

        /*
         * SleepStretch* bones are zero-rotation helper parents authored
         * specifically for the wake stretch. Drive them directly.
         *
         * V5 required getInitialSnapshot() for every bone. If a helper parent
         * existed in model.bones() but its snapshot was unavailable, the log
         * still said the bone existed while applyGecko silently returned.
         * That produces exactly the "hook fired but arms did not move" symptom.
         */
        if (bone.getName().startsWith("SleepStretch")) {
            /*
             * TLM's Gecko JSON parser uses RotationValue:
             *   X -> -radians(value)
             *   Y -> -radians(value)
             *   Z -> +radians(value)
             *
             * Our old Java fallback used +/+ /+, so motions copied verbatim
             * from animation.json were mirrored on X/Y.
             */
            bone.setRotation(
                    -rotation.x() * DEG_TO_RAD,
                    -rotation.y() * DEG_TO_RAD,
                    rotation.z() * DEG_TO_RAD
            );
            return;
        }

        BoneSnapshot initial =
                bone.getInitialSnapshot();

        if (initial != null) {
            bone.setRotation(
                    initial.rotationValueX
                            - rotation.x() * DEG_TO_RAD,
                    initial.rotationValueY
                            - rotation.y() * DEG_TO_RAD,
                    initial.rotationValueZ
                            + rotation.z() * DEG_TO_RAD
            );
            return;
        }

        /*
         * Last-resort compatibility fallback for unusual third-party Gecko
         * Wine Fox skeletons that expose the bone but no bind snapshot.
         */
        bone.setRotation(
                -rotation.x() * DEG_TO_RAD,
                -rotation.y() * DEG_TO_RAD,
                rotation.z() * DEG_TO_RAD
        );
    }

    private static void applyWineFoxFaceExpression(
            Map<String, AnimatedGeoBone> bones,
            float t
    ) {
        float eyebrowY =
                wineFoxEyebrowOffset(t);

        float eyelidYScale =
                wineFoxEyelidScaleY(t);

        applyFacePositionY(
                bones.get("RightEyebrow"),
                eyebrowY
        );
        applyFacePositionY(
                bones.get("LeftEyebrow"),
                eyebrowY
        );

        applyFaceScaleY(
                bones.get("RightEyelid"),
                eyelidYScale
        );
        applyFaceScaleY(
                bones.get("LeftEyelid"),
                eyelidYScale
        );
    }

    private static void applyFacePositionY(
            AnimatedGeoBone bone,
            float authoredOffset
    ) {
        if (bone == null) {
            return;
        }

        BoneSnapshot initial =
                bone.getInitialSnapshot();

        bone.setPositionY(
                (initial == null
                        ? 0.0F
                        : initial.positionOffsetY)
                        + authoredOffset
        );
    }

    private static void applyFaceScaleY(
            AnimatedGeoBone bone,
            float authoredScale
    ) {
        if (bone == null) {
            return;
        }

        BoneSnapshot initial =
                bone.getInitialSnapshot();

        float base =
                initial == null
                        ? 1.0F
                        : initial.scaleValueY;

        bone.setScaleY(
                base * authoredScale
        );
    }

    /**
     * Original TLM Wine Fox stretch eyebrow timing, shifted from idle 16.6667s -> 0s.
     */
    private static float wineFoxEyebrowOffset(
            float t
    ) {
        if (t < 0.9583F) {
            return 0.0F;
        }
        if (t < 1.0416F) {
            return lerpLinear(
                    t,
                    0.9583F,
                    1.0416F,
                    0.0F,
                    -1.4F
            );
        }
        if (t < 2.4583F) {
            return -1.4F;
        }
        if (t < 3.0000F) {
            return lerpLinear(
                    t,
                    2.4583F,
                    3.0000F,
                    -1.4F,
                    0.0F
            );
        }
        if (t < 4.1250F) {
            return 0.0F;
        }
        if (t < 4.2083F) {
            return lerpLinear(
                    t,
                    4.1250F,
                    4.2083F,
                    0.0F,
                    -1.4F
            );
        }
        if (t < 4.7083F) {
            return -1.4F;
        }
        if (t < 4.8333F) {
            return lerpLinear(
                    t,
                    4.7083F,
                    4.8333F,
                    -1.4F,
                    0.0F
            );
        }
        return 0.0F;
    }

    /**
     * Original TLM Wine Fox stretch eyelid timing, shifted from idle 16.6667s -> 0s.
     * 1 = eyes open, 0 = eyelids collapsed/closed.
     */
    private static float wineFoxEyelidScaleY(
            float t
    ) {
        if (t < 0.9583F) {
            return 1.0F;
        }
        if (t < 1.0416F) {
            return lerpLinear(
                    t,
                    0.9583F,
                    1.0416F,
                    1.0F,
                    0.0F
            );
        }
        if (t < 2.4583F) {
            return 0.0F;
        }
        if (t < 3.0000F) {
            return lerpLinear(
                    t,
                    2.4583F,
                    3.0000F,
                    0.0F,
                    1.0F
            );
        }
        if (t < 4.1250F) {
            return 1.0F;
        }
        if (t < 4.2083F) {
            return lerpLinear(
                    t,
                    4.1250F,
                    4.2083F,
                    1.0F,
                    0.0F
            );
        }
        if (t < 4.7083F) {
            return 0.0F;
        }
        if (t < 4.8333F) {
            return lerpLinear(
                    t,
                    4.7083F,
                    4.8333F,
                    0.0F,
                    1.0F
            );
        }
        return 1.0F;
    }

    private static float lerpLinear(
            float t,
            float t0,
            float t1,
            float v0,
            float v1
    ) {
        float alpha =
                Mth.clamp(
                        (t - t0)
                                / Math.max(
                                        0.0001F,
                                        t1 - t0
                                ),
                        0.0F,
                        1.0F
                );

        return Mth.lerp(
                alpha,
                v0,
                v1
        );
    }

    private static void logClassicFirstFrame(
            EntityMaid maid,
            Map<String, ? extends IModelRenderer> parts
    ) {
        if (!ClientWakeStretchState.markRenderStarted(maid)) {
            return;
        }

        LOGGER.info(
                "[WGON WakeStretch] RENDER classic EXCLUSIVE entityId={} modelId={} isYsm={} ysmId={} bones(up={}, upper={}, head={}, LA={}, RA={})",
                maid.getId(),
                maid.getModelId(),
                maid.isYsmModel(),
                maid.getYsmModelId(),
                hasEither(parts, "SleepStretchUpBody", "UpBody"),
                hasEither(parts, "SleepStretchUpperBody", "UpperBody"),
                hasEither(parts, "SleepStretchHead", "Head"),
                hasEither(parts, "SleepStretchLeftArm", "LeftArm"),
                hasEither(parts, "SleepStretchRightArm", "RightArm")
        );
    }

    private static void logGeckoFirstFrame(
            EntityMaid maid,
            Map<String, AnimatedGeoBone> bones
    ) {
        if (!ClientWakeStretchState.markRenderStarted(maid)) {
            return;
        }

        LOGGER.info(
                "[WGON WakeStretch] RENDER gecko EXCLUSIVE entityId={} modelId={} isYsm={} ysmId={} ysmName={} bones(up={}, upper={}, head={}, LA={}, RA={})",
                maid.getId(),
                maid.getModelId(),
                maid.isYsmModel(),
                maid.getYsmModelId(),
                maid.getYsmModelName() == null
                        ? "<null>"
                        : maid.getYsmModelName().getString(),
                selectedBoneName(bones, "SleepStretchUpBody", "UpBody"),
                selectedBoneName(bones, "SleepStretchUpperBody", "UpperBody"),
                selectedBoneName(bones, "SleepStretchHead", "Head"),
                selectedBoneName(bones, "SleepStretchLeftArm", "LeftArm"),
                selectedBoneName(bones, "SleepStretchRightArm", "RightArm")
        );

        AnimatedGeoBone left =
                firstBone(
                        bones,
                        "SleepStretchLeftArm",
                        "LeftArm"
                );

        AnimatedGeoBone right =
                firstBone(
                        bones,
                        "SleepStretchRightArm",
                        "RightArm"
                );

        LOGGER.info(
                "[WGON WakeStretch] surface probe entityId={} leftSnapshot={} rightSnapshot={} leftRot=({}, {}, {}) rightRot=({}, {}, {}) faceBones(LE={},RE={},LB={},RB={}) faceExpressionEligible={}",
                maid.getId(),
                left != null && left.getInitialSnapshot() != null,
                right != null && right.getInitialSnapshot() != null,
                left == null ? 0.0F : left.getRotationX(),
                left == null ? 0.0F : left.getRotationY(),
                left == null ? 0.0F : left.getRotationZ(),
                right == null ? 0.0F : right.getRotationX(),
                right == null ? 0.0F : right.getRotationY(),
                right == null ? 0.0F : right.getRotationZ(),
                bones.containsKey("LeftEyelid"),
                bones.containsKey("RightEyelid"),
                bones.containsKey("LeftEyebrow"),
                bones.containsKey("RightEyebrow"),
                bones.containsKey("LeftEyelid")
                        && bones.containsKey("RightEyelid")
                        && bones.containsKey("LeftEyebrow")
                        && bones.containsKey("RightEyebrow")
        );
    }

    private static boolean hasEither(
            Map<String, ? extends IModelRenderer> parts,
            String preferred,
            String fallback
    ) {
        return parts.containsKey(preferred)
                || parts.containsKey(fallback);
    }

    private static String selectedBoneName(
            Map<String, AnimatedGeoBone> bones,
            String preferred,
            String fallback
    ) {
        if (bones.containsKey(preferred)) {
            return preferred;
        }

        if (bones.containsKey(fallback)) {
            return fallback;
        }

        return "<missing>";
    }

    private static Vec3Deg sample(
            Frame[] frames,
            float time
    ) {
        if (frames.length == 0) {
            return Vec3Deg.ZERO;
        }

        if (time <= frames[0].time()) {
            return frames[0].rotation();
        }

        for (int i = 1; i < frames.length; i++) {
            Frame previous =
                    frames[i - 1];

            Frame next =
                    frames[i];

            if (time <= next.time()) {
                float span =
                        Math.max(
                                0.0001F,
                                next.time() - previous.time()
                        );

                float alpha =
                        Mth.clamp(
                                (time - previous.time()) / span,
                                0.0F,
                                1.0F
                        );

                /*
                 * Smoothstep keeps the gesture soft instead of making the maid
                 * snap between authored key poses.
                 */
                alpha =
                        alpha * alpha
                                * (3.0F - 2.0F * alpha);

                return previous.rotation().lerp(
                        next.rotation(),
                        alpha
                );
            }
        }

        return frames[frames.length - 1].rotation();
    }

    private static Frame f(
            float time,
            float x,
            float y,
            float z
    ) {
        return new Frame(
                time,
                new Vec3Deg(x, y, z)
        );
    }

    private record Frame(
            float time,
            Vec3Deg rotation
    ) {
    }

    private record Vec3Deg(
            float x,
            float y,
            float z
    ) {
        private static final Vec3Deg ZERO =
                new Vec3Deg(
                        0.0F,
                        0.0F,
                        0.0F
                );

        private Vec3Deg lerp(
                Vec3Deg other,
                float alpha
        ) {
            return new Vec3Deg(
                    Mth.lerp(alpha, x, other.x),
                    Mth.lerp(alpha, y, other.y),
                    Mth.lerp(alpha, z, other.z)
            );
        }
    }
}

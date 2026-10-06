package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodConsumeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soft compatibility for Bountiful Fares' placed pastries.
 *
 * Bountiful Fares pies/tarts are eaten directly from the block. Their
 * BlockItem is intentionally not an edible ItemStack, so WGON's generic
 * placed-food fallback cannot safely infer a detached serving. This handler
 * recognises the known pastry class hierarchy by class name and advances the
 * block's own "bites" state without linking against the mod at compile time.
 */
public final class BountifulFaresPastryHandler
        implements FoodBlockHandler {

    private static final String ID =
            "bountiful_fares_pastry";

    private static final String QUARTER_PASTRY_CLASS =
            "net.hecco.bountifulfares.block.custom.QuarterPastryBlock";

    private static final String SIX_SLICE_PASTRY_CLASS =
            "net.hecco.bountifulfares.block.custom.SixSlicePastry";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (!WgonConfig.BOUNTIFUL_FARES_PASTRY_COMPAT_ENABLED.get()) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (id == null
                || !"bountifulfares".equals(id.getNamespace())) {
            return false;
        }

        IntegerProperty bites =
                findIntegerProperty(
                        state,
                        "bites"
                );

        if (bites == null) {
            return false;
        }

        Block block =
                state.getBlock();

        return block instanceof CakeBlock
                || hasClassInHierarchy(
                block,
                QUARTER_PASTRY_CLASS
        )
                || hasClassInHierarchy(
                block,
                SIX_SLICE_PASTRY_CLASS
        );
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (!supports(state)) {
            return -1;
        }

        IntegerProperty bites =
                findIntegerProperty(
                        state,
                        "bites"
                );

        if (bites == null) {
            return -1;
        }

        int current =
                state.getValue(
                        bites
                );

        int max =
                maximumValue(
                        bites
                );

        /*
         * These blocks use the same convention as vanilla cake:
         * bite=0 is a fresh pastry and bite=max still has one final piece.
         */
        return Math.max(
                0,
                max - current + 1
        );
    }

    @Override
    public FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (level.isClientSide) {
            return FoodConsumeResult.clientSide();
        }

        if (!supports(state)) {
            return FoodConsumeResult.unsupported();
        }

        IntegerProperty bites =
                findIntegerProperty(
                        state,
                        "bites"
                );

        if (bites == null) {
            return FoodConsumeResult.unsupported();
        }

        int before =
                getRemainingServings(
                        level,
                        pos,
                        state
                );

        if (before <= 0) {
            return FoodConsumeResult.empty(
                    ID,
                    0
            );
        }

        int current =
                state.getValue(
                        bites
                );

        int max =
                maximumValue(
                        bites
                );

        if (current >= max) {
            level.destroyBlock(
                    pos,
                    false
            );
        } else {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            bites,
                            current + 1
                    )
            );
        }

        /*
         * TLM's own vanilla-cake adapter also treats placed cake mostly as a
         * block interaction rather than a normal ItemStack meal. Keep this
         * handler equally conservative: update the source, play the eating
         * sound, and let WGON own presentation/hand-swing timing.
         */
        maid.playSound(
                SoundEvents.GENERIC_EAT,
                0.75F,
                1.0F
        );

        BlockState afterState =
                level.getBlockState(
                        pos
                );

        int after =
                supports(afterState)
                        ? getRemainingServings(
                        level,
                        pos,
                        afterState
                )
                        : 0;

        return FoodConsumeResult.success(
                ID,
                before,
                after
        );
    }

    private static boolean hasClassInHierarchy(
            Block block,
            String className
    ) {
        Class<?> type =
                block.getClass();

        while (type != null
                && type != Object.class) {
            if (className.equals(type.getName())) {
                return true;
            }

            type =
                    type.getSuperclass();
        }

        return false;
    }

    private static IntegerProperty findIntegerProperty(
            BlockState state,
            String name
    ) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty
                    && name.equals(integerProperty.getName())) {
                return integerProperty;
            }
        }

        return null;
    }

    private static int maximumValue(
            IntegerProperty property
    ) {
        int maximum =
                Integer.MIN_VALUE;

        for (Integer value : property.getPossibleValues()) {
            if (value != null) {
                maximum = Math.max(
                        maximum,
                        value
                );
            }
        }

        return maximum == Integer.MIN_VALUE
                ? 0
                : maximum;
    }
}

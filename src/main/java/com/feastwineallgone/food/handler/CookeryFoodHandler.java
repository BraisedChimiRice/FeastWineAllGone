package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.compat.kaleidoscope.edible.BlockFoodEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteThreeByThreeBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.NinePart;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodConsumeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WGON adapter for Kaleidoscope Cookery placed foods.
 *
 * WGON decides when and why the maid eats. Actual Cookery bite handling
 * is delegated to TLM's compatibility implementation.
 *
 * Two details are deliberately handled here:
 *
 * 1. the terminal max-bites state is kept as a visible remnant instead
 *    of being hit once more and destroyed;
 * 2. Cookery's 3x3 foods store their authoritative bites/model state on
 *    the centre part. Scanning may find any of the nine parts, so WGON
 *    normalises the clicked/scanned position to the centre before eating.
 */
public final class CookeryFoodHandler
        implements FoodBlockHandler {

    private static final String ID =
            "kaleidoscope_cookery";

    private final BlockFoodEdible delegate =
            new BlockFoodEdible();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (!(state.getBlock()
                instanceof FoodBiteBlock foodBlock)) {
            return false;
        }

        int bites =
                state.getValue(
                        foodBlock.getBites()
                );

        return bites
                < foodBlock.getMaxBites();
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        BlockPos actualPos =
                resolveCanonicalFoodPos(
                        pos,
                        state
                );

        BlockState actualState =
                level.getBlockState(
                        actualPos
                );

        if (!(actualState.getBlock()
                instanceof FoodBiteBlock foodBlock)) {
            return -1;
        }

        int bites =
                actualState.getValue(
                        foodBlock.getBites()
                );

        int maxBites =
                foodBlock.getMaxBites();

        return Math.max(
                0,
                maxBites - bites
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

        BlockPos actualPos =
                resolveCanonicalFoodPos(
                        pos,
                        state
                );

        BlockState actualState =
                level.getBlockState(
                        actualPos
                );

        if (!(actualState.getBlock()
                instanceof FoodBiteBlock)) {
            return FoodConsumeResult.unsupported();
        }

        int servingsBefore =
                getRemainingServings(
                        level,
                        actualPos,
                        actualState
                );

        if (servingsBefore <= 0) {
            return FoodConsumeResult.empty(
                    ID,
                    0
            );
        }

        /*
         * Do not call shouldMoveTo(). TLM uses that method to enforce its
         * snack-stand rules; WGON already owns target selection.
         *
         * For FoodBiteThreeByThreeBlock it is essential that the delegate
         * receives the centre block state/position. Otherwise an outside
         * ham tile can receive a bites update while the centre renderer
         * remains unchanged, making it look as though nothing was eaten.
         */
        boolean consumed =
                delegate.consume(
                        maid,
                        actualPos,
                        actualState
                );

        if (!consumed) {
            return FoodConsumeResult.failed(
                    ID,
                    servingsBefore
            );
        }

        BlockState stateAfter =
                level.getBlockState(
                        actualPos
                );

        int servingsAfter;

        if (stateAfter.getBlock()
                instanceof FoodBiteBlock) {

            servingsAfter =
                    getRemainingServings(
                            level,
                            actualPos,
                            stateAfter
                    );

        } else {
            servingsAfter = 0;
        }

        return FoodConsumeResult.success(
                ID,
                servingsBefore,
                servingsAfter
        );
    }

    /**
     * Cookery FoodBiteThreeByThreeBlock stores the authoritative bites
     * property and renderer block entity on its centre tile.
     */
    private static BlockPos resolveCanonicalFoodPos(
            BlockPos pos,
            BlockState state
    ) {
        if (!(state.getBlock()
                instanceof FoodBiteThreeByThreeBlock)
                || !state.hasProperty(
                FoodBiteThreeByThreeBlock.PART
        )) {
            return pos;
        }

        NinePart part =
                state.getValue(
                        FoodBiteThreeByThreeBlock.PART
                );

        return pos.offset(
                -part.getPosX(),
                0,
                -part.getPosY()
        );
    }
}

package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.CakeEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodConsumeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WGON adapter for the vanilla Minecraft cake.
 *
 * Actual cake consumption is delegated to Touhou Little Maid's
 * built-in CakeEdible implementation.
 *
 * WGON is responsible for deciding when and why the maid eats.
 * TLM is responsible for performing the actual block-food action.
 */
public final class VanillaCakeHandler implements FoodBlockHandler {

    private static final String ID = "minecraft_cake";

    /*
     * Vanilla cake has BITES values 0 through 6.
     *
     * BITES = 0 means a fresh cake.
     * BITES = 6 means one final slice remains.
     *
     * Therefore there are 7 edible portions in total.
     */
    private static final int TOTAL_SERVINGS =
            CakeBlock.MAX_BITES + 1;

    private final CakeEdible delegate =
            new CakeEdible();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.CAKE);
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

        int bites = state.getValue(CakeBlock.BITES);

        return Math.max(
                0,
                TOTAL_SERVINGS - bites
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

        int servingsBefore =
                getRemainingServings(
                        level,
                        pos,
                        state
                );

        if (servingsBefore <= 0) {
            return FoodConsumeResult.empty(
                    ID,
                    0
            );
        }

        /*
         * Important:
         *
         * We deliberately call TLM's consume() directly instead
         * of shouldMoveTo().
         *
         * TLM's normal autonomous snack behaviour requires the
         * cake to be placed on a registered snack stand.
         *
         * WGON has its own rules for selecting food targets, so
         * the decision to eat has already been made before this
         * method is called.
         */
        boolean consumed =
                delegate.consume(
                        maid,
                        pos,
                        state
                );

        if (!consumed) {
            return FoodConsumeResult.failed(
                    ID,
                    servingsBefore
            );
        }

        BlockState stateAfter =
                level.getBlockState(pos);

        int servingsAfter;

        if (stateAfter.is(Blocks.CAKE)) {
            servingsAfter =
                    getRemainingServings(
                            level,
                            pos,
                            stateAfter
                    );
        } else {
            /*
             * The last slice removes the cake block.
             */
            servingsAfter = 0;
        }

        return FoodConsumeResult.success(
                ID,
                servingsBefore,
                servingsAfter
        );
    }
}
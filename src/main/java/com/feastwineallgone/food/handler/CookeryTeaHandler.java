package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.ClayPotMilkTeaBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.EmptyCupBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.drink.TeacupBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.feastwineallgone.food.FoodConsumeResult;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WGON adapter for Kaleidoscope Cookery placed tea drinks.
 *
 * Supported:
 * - TeacupBlock, including multiple filled cups placed together;
 * - ClayPotMilkTeaBlock.
 *
 * The serving is detached into the maid's hand first, then the idle
 * food behaviour plays a DRINK animation and applies Cookery's native
 * drink effects.
 */
public final class CookeryTeaHandler
        implements HandheldFoodBlockHandler {

    private static final String ID =
            "kaleidoscope_cookery_tea";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (state.getBlock()
                instanceof ClayPotMilkTeaBlock) {

            return true;
        }

        if (state.getBlock()
                instanceof TeacupBlock teacupBlock) {

            return state.getValue(
                    teacupBlock.getTeaCountProperty()
            ) > 0;
        }

        return false;
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.getBlock()
                instanceof ClayPotMilkTeaBlock) {

            return 1;
        }

        if (state.getBlock()
                instanceof TeacupBlock teacupBlock) {

            return Math.max(
                    0,
                    state.getValue(
                            teacupBlock.getTeaCountProperty()
                    )
            );
        }

        return -1;
    }

    @Override
    public ItemStack takeOneServing(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.getBlock()
                instanceof ClayPotMilkTeaBlock) {

            ItemStack serving =
                    ModItems.CLAY_POT_MILK_TEA
                            .get()
                            .getDefaultInstance();

            level.removeBlock(
                    pos,
                    false
            );

            return serving;
        }

        if (!(state.getBlock()
                instanceof TeacupBlock teacupBlock)) {

            return ItemStack.EMPTY;
        }

        int cupCount =
                state.getValue(
                        teacupBlock.getCupCountProperty()
                );

        int teaCount =
                state.getValue(
                        teacupBlock.getTeaCountProperty()
                );

        if (cupCount <= 0
                || teaCount <= 0) {

            return ItemStack.EMPTY;
        }

        /*
         * The BlockItem associated with this exact TeacupBlock is a
         * TeacupItem, so ItemStack(block) preserves the tea variant.
         */
        ItemStack serving =
                new ItemStack(
                        teacupBlock
                );

        if (cupCount == 1) {

            level.removeBlock(
                    pos,
                    false
            );

        } else if (teaCount == 1) {

            /*
             * We just removed the last filled cup, while other empty
             * cups remain on the table. Mirror Cookery's own block-use
             * behaviour and convert the block to EmptyCupBlock.
             */
            BlockState emptyCups =
                    ModBlocks.EMPTY_CUP
                            .get()
                            .defaultBlockState()
                            .setValue(
                                    EmptyCupBlock.CUP_COUNT,
                                    cupCount - 1
                            );

            if (state.hasProperty(
                    HorizontalDirectionalBlock.FACING
            )
                    && emptyCups.hasProperty(
                    HorizontalDirectionalBlock.FACING
            )) {

                emptyCups =
                        emptyCups.setValue(
                                HorizontalDirectionalBlock.FACING,
                                state.getValue(
                                        HorizontalDirectionalBlock.FACING
                                )
                        );
            }

            level.setBlock(
                    pos,
                    emptyCups,
                    3
            );

        } else {

            level.setBlock(
                    pos,
                    state
                            .setValue(
                                    teacupBlock.getCupCountProperty(),
                                    cupCount - 1
                            )
                            .setValue(
                                    teacupBlock.getTeaCountProperty(),
                                    teaCount - 1
                            ),
                    3
            );
        }

        return serving;
    }

    @Override
    public boolean consumeDetachedServing(
            EntityMaid maid,
            ServerLevel level,
            ItemStack serving
    ) {
        return CookeryDrinkAccess
                .consumeDetachedDrink(
                        level,
                        maid,
                        serving
                );
    }

    @Override
    public FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        return HandheldFoodBlockHandler.super.consumeOne(
                maid,
                level,
                pos,
                state
        );
    }
}

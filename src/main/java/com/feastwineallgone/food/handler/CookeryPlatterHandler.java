package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.PlateBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.StackableFoodBlock;
import com.feastwineallgone.food.FoodConsumeResult;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import com.feastwineallgone.integration.cookery.CookeryPlatterAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * WGON adapter for Kaleidoscope Cookery placed platter foods.
 *
 * Cookery actually uses two separate block implementations:
 *
 * - StackableFoodBlock: stacked individual foods;
 * - PlateBlock: apple platter, baozi plate, qingtuan plate, etc.
 *
 * Both are exposed to WGON as one handheld-food family.
 */
public final class CookeryPlatterHandler
        implements HandheldFoodBlockHandler {

    private static final String ID =
            "kaleidoscope_cookery_platter";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (state.getBlock()
                instanceof StackableFoodBlock) {

            return true;
        }

        if (state.getBlock()
                instanceof PlateBlock plate) {

            return state.hasProperty(
                    plate.getServingsProperty()
            )
                    && state.getValue(
                    plate.getServingsProperty()
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
                instanceof StackableFoodBlock platter) {

            return state.getValue(
                    platter.getCountProperty()
            );
        }

        if (state.getBlock()
                instanceof PlateBlock plate) {

            if (!state.hasProperty(
                    plate.getServingsProperty()
            )) {
                return -1;
            }

            return Math.max(
                    0,
                    state.getValue(
                            plate.getServingsProperty()
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
                instanceof StackableFoodBlock platter) {

            return takeFromStackableFood(
                    level,
                    pos,
                    state,
                    platter
            );
        }

        if (state.getBlock()
                instanceof PlateBlock plate) {

            return takeFromPlate(
                    maid,
                    level,
                    pos,
                    state,
                    plate
            );
        }

        return ItemStack.EMPTY;
    }

    private ItemStack takeFromStackableFood(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            StackableFoodBlock platter
    ) {
        int count =
                state.getValue(
                        platter.getCountProperty()
                );

        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack serving =
                CookeryPlatterAccess
                        .createServing(
                                platter
                        );

        if (serving.isEmpty()) {
            return ItemStack.EMPTY;
        }

        /*
         * Mirror StackableFoodBlock's empty-hand take behaviour.
         */
        if (count <= 1) {
            level.removeBlock(
                    pos,
                    false
            );

        } else {
            level.setBlock(
                    pos,
                    state.setValue(
                            platter.getCountProperty(),
                            count - 1
                    ),
                    3
            );
        }

        return serving;
    }

    private ItemStack takeFromPlate(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            PlateBlock plate
    ) {
        int count =
                state.getValue(
                        plate.getServingsProperty()
                );

        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        List<ItemStack> bundle =
                new ArrayList<>(
                        CookeryPlatterAccess
                                .createServingBundle(
                                        plate
                                )
                );

        if (bundle.isEmpty()) {
            return ItemStack.EMPTY;
        }

        /*
         * Prefer an actually edible item for the visual/eating serving.
         * This covers normal platters such as apple_platter while still
         * preserving extra items from multi-item plate definitions.
         */
        int primaryIndex =
                0;

        for (int i = 0;
             i < bundle.size();
             i++) {

            if (bundle.get(i)
                    .isEdible()) {

                primaryIndex =
                        i;

                break;
            }
        }

        ItemStack primary =
                bundle.remove(
                        primaryIndex
                );

        /*
         * Cookery's own PlateBlock gives every item in the serving-item
         * list when one serving is taken. WGON lets the maid eat one in
         * hand and stores any companion items in her backpack.
         */
        for (ItemStack extra : bundle) {
            CookeryDrinkAccess.storeOrDrop(
                    level,
                    maid,
                    extra
            );
        }

        /*
         * PlateBlock keeps the empty plate at servings=0. It is removed
         * only when a player interacts with the already-empty plate.
         * Preserve that native behaviour here.
         */
        level.setBlock(
                pos,
                state.setValue(
                        plate.getServingsProperty(),
                        count - 1
                ),
                3
        );

        primary.setCount(
                1
        );

        return primary;
    }

    @Override
    public boolean consumeDetachedServing(
            EntityMaid maid,
            ServerLevel level,
            ItemStack serving
    ) {
        if (serving == null
                || serving.isEmpty()
                || !serving.isEdible()) {

            return false;
        }

        ItemStack one =
                serving.copy();

        one.setCount(
                1
        );

        ItemStack remainder =
                one.finishUsingItem(
                        level,
                        maid
                );

        CookeryDrinkAccess.storeOrDrop(
                level,
                maid,
                remainder
        );

        return true;
    }

    @Override
    public boolean isPlatter() {
        return true;
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

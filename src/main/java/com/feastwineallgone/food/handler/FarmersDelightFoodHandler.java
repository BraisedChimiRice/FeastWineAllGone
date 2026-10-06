package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.food.FoodConsumeResult;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import vectorwing.farmersdelight.common.block.FeastBlock;
import vectorwing.farmersdelight.common.block.PieBlock;

/**
 * Optional Farmer's Delight placed-food compatibility.
 *
 * This class is only instantiated when Farmer's Delight is actually
 * loaded. WGON itself does not require Farmer's Delight.
 *
 * Supported families:
 * - FeastBlock and subclasses;
 * - PieBlock and subclasses.
 *
 * WGON detaches one real serving/slice, shows it in the maid's hand,
 * then lets the actual ItemStack finishUsingItem() so Farmer's Delight
 * food properties/effects remain authoritative.
 */
public final class FarmersDelightFoodHandler
        implements HandheldFoodBlockHandler {

    private static final String ID =
            "farmersdelight_placed_food";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (state.getBlock()
                instanceof FeastBlock feast) {

            return state.hasProperty(
                    feast.getServingsProperty()
            )
                    && state.getValue(
                    feast.getServingsProperty()
            ) > 0;
        }

        if (state.getBlock()
                instanceof PieBlock pie) {

            return state.hasProperty(
                    PieBlock.BITES
            )
                    && state.getValue(
                    PieBlock.BITES
            ) < pie.getMaxBites();
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
                instanceof FeastBlock feast) {

            if (!state.hasProperty(
                    feast.getServingsProperty()
            )) {
                return -1;
            }

            return Math.max(
                    0,
                    state.getValue(
                            feast.getServingsProperty()
                    )
            );
        }

        if (state.getBlock()
                instanceof PieBlock pie) {

            if (!state.hasProperty(
                    PieBlock.BITES
            )) {
                return -1;
            }

            int bites =
                    state.getValue(
                            PieBlock.BITES
                    );

            return Math.max(
                    0,
                    pie.getMaxBites()
                            - bites
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
                instanceof FeastBlock feast) {

            return takeFromFeast(
                    level,
                    pos,
                    state,
                    feast
            );
        }

        if (state.getBlock()
                instanceof PieBlock pie) {

            return takeFromPie(
                    level,
                    pos,
                    state,
                    pie
            );
        }

        return ItemStack.EMPTY;
    }

    private ItemStack takeFromFeast(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            FeastBlock feast
    ) {
        int servings =
                state.getValue(
                        feast.getServingsProperty()
                );

        if (servings <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack serving =
                feast.getServingItem(
                        state
                );

        if (serving == null
                || serving.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ItemStack detached =
                serving.copy();

        detached.setCount(
                1
        );

        int after =
                servings - 1;

        /*
         * Most Farmer's Delight feasts intentionally keep a visible final
         * plate/remnant for WGON. Stuffed Pumpkin is the exception requested
         * by the mod: its model has no meaningful empty plate, so the final
         * serving consumes the whole block instead of leaving servings == 0.
         */
        if (after <= 0 && isStuffedPumpkin(feast)) {
            level.destroyBlock(pos, false);
        } else {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            feast.getServingsProperty(),
                            Math.max(0, after)
                    )
            );
        }

        return detached;
    }

    private ItemStack takeFromPie(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            PieBlock pie
    ) {
        int bites =
                state.getValue(
                        PieBlock.BITES
                );

        int maxBites =
                pie.getMaxBites();

        if (bites >= maxBites) {
            return ItemStack.EMPTY;
        }

        ItemStack serving =
                pie.getPieSliceItem();

        if (serving == null
                || serving.isEmpty()) {

            return ItemStack.EMPTY;
        }

        ItemStack detached =
                serving.copy();

        detached.setCount(
                1
        );

        if (bites
                >= maxBites - 1) {

            level.destroyBlock(
                    pos,
                    false
            );

        } else {
            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            PieBlock.BITES,
                            bites + 1
                    )
            );
        }

        return detached;
    }

    private static boolean isStuffedPumpkin(FeastBlock feast) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(feast);
        return id != null
                && "farmersdelight".equals(id.getNamespace())
                && "stuffed_pumpkin_block".equals(id.getPath());
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

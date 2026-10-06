package com.feastwineallgone.food;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Food blocks whose serving is first taken into the maid's hand
 * and then eaten/drunk as an ItemStack.
 *
 * Examples:
 * - Kaleidoscope Cookery stackable platters;
 * - Kaleidoscope Cookery tea cups;
 * - clay-pot milk tea.
 */
public interface HandheldFoodBlockHandler
        extends FoodBlockHandler {

    /**
     * Removes exactly one serving from the placed block and
     * returns the detached item that should be shown in hand.
     */
    ItemStack takeOneServing(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    );

    /**
     * Applies the detached serving's normal consumption effects.
     *
     * WGON calls this after a short EAT/DRINK presentation.
     */
    boolean consumeDetachedServing(
            EntityMaid maid,
            ServerLevel level,
            ItemStack serving
    );

    /**
     * Whether the detached stack should play the normal EAT/DRINK use
     * animation before consumeDetachedServing is called.
     *
     * Most foods return true. A handler may return false for a pickup-only
     * action, such as Farming Tales' visually stacked drinks: WGON briefly
     * shows the bundle in hand and then stores it in the maid backpack.
     */
    default boolean shouldAnimateDetachedUse(
            ItemStack serving
    ) {
        return true;
    }

    /**
     * Whether this detached stack is a pickup-only bundle rather than a
     * serving that should be eaten or drunk.
     *
     * Pickup-only bundles may still be shown briefly in the maid's hand, but
     * dining managers must never call startUsingItem() for them. This keeps
     * display stacks such as Farming Tales' multi-bottle drinks from being
     * collapsed to one item and accidentally consumed.
     */
    default boolean isPickupOnlyDetachedServing(
            ItemStack serving
    ) {
        return false;
    }

    /**
     * Whether this block represents a platter that may be consumed
     * repeatedly during one idle-food session.
     */
    default boolean isPlatter() {
        return false;
    }

    /**
     * Synchronous fallback used by debug commands and other callers
     * that do not run the staged idle-food presentation.
     */
    @Override
    default FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (level.isClientSide) {
            return FoodConsumeResult.clientSide();
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return FoodConsumeResult.failed(
                    getId(),
                    getRemainingServings(level, pos, state)
            );
        }

        int before =
                getRemainingServings(
                        level,
                        pos,
                        state
                );

        if (before == 0) {
            return FoodConsumeResult.empty(
                    getId(),
                    0
            );
        }

        ItemStack serving =
                takeOneServing(
                        maid,
                        serverLevel,
                        pos,
                        state
                );

        if (serving.isEmpty()) {
            return FoodConsumeResult.failed(
                    getId(),
                    before
            );
        }

        if (!consumeDetachedServing(
                maid,
                serverLevel,
                serving
        )) {
            return FoodConsumeResult.failed(
                    getId(),
                    before
            );
        }

        BlockState afterState =
                level.getBlockState(pos);

        int after = supports(afterState)
                ? getRemainingServings(
                        level,
                        pos,
                        afterState
                )
                : 0;

        return FoodConsumeResult.success(
                getId(),
                before,
                after
        );
    }
}

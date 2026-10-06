package com.feastwineallgone.food;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Adapter interface for placed food blocks.
 *
 * Each supported food system implements this interface.
 *
 * Examples:
 * - Kaleidoscope Cookery
 * - Farmer's Delight
 * - Vanilla Cake
 */
public interface FoodBlockHandler {

    /**
     * Unique identifier used for debugging and registration.
     */
    String getId();

    /**
     * Returns whether this handler understands the supplied block.
     */
    boolean supports(BlockState state);

    /**
     * Returns how many edible servings remain.
     *
     * 0 means the food has no edible serving remaining.
     * A negative value means the amount cannot be determined.
     */
    int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    );

    /**
     * Makes the maid consume exactly one serving.
     *
     * This method is responsible for:
     *
     * - applying the food's effects to the maid;
     * - reducing the block's serving count;
     * - updating/removing the food block correctly;
     * - handling the final serving;
     * - preserving multi-block food state where necessary.
     */
    FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos,
            BlockState state
    );
}
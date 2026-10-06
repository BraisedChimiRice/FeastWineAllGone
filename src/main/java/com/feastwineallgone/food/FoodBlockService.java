package com.feastwineallgone.food;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Central service for all placed-food interactions.
 *
 * Behaviour systems such as:
 *
 * - idle foraging;
 * - dinner bell;
 * - night snacking;
 *
 * must use this service instead of interacting with
 * individual food mods directly.
 *
 * This service also owns common presentation behaviour,
 * such as the maid's hand-swing animation after a
 * successful bite.
 */
public final class FoodBlockService {

    private static final List<FoodBlockHandler> HANDLERS =
            new ArrayList<>();

    private FoodBlockService() {
    }

    /**
     * Registers a food handler.
     *
     * If another handler with the same ID already exists,
     * it will be replaced.
     */
    public static void register(FoodBlockHandler handler) {
        HANDLERS.removeIf(existing ->
                existing.getId().equals(handler.getId())
        );

        HANDLERS.add(handler);
    }

    /**
     * Returns all registered handlers.
     *
     * Mainly useful for debugging.
     */
    public static List<FoodBlockHandler> getHandlers() {
        return Collections.unmodifiableList(HANDLERS);
    }

    /**
     * Finds the first handler capable of understanding
     * the supplied block state.
     */
    public static Optional<FoodBlockHandler> findHandler(
            BlockState state
    ) {
        for (FoodBlockHandler handler : HANDLERS) {
            if (handler.supports(state)) {
                return Optional.of(handler);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns whether WGON currently recognises this block
     * as a supported placed food.
     */
    public static boolean isSupportedFood(
            Level level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);

        if (FoodSafetyRules.isHardBlacklisted(state.getBlock().asItem())) {
            return false;
        }

        Optional<FoodBlockHandler> handler =
                findHandler(state);

        if (handler.isEmpty()) {
            return false;
        }

        /*
         * A recognised block may already be in its final empty/remnant
         * state. Do not keep selecting that block as edible. Negative
         * means the handler cannot expose a count but still supports it.
         */
        return handler.get()
                .getRemainingServings(
                        level,
                        pos,
                        state
                ) != 0;
    }

    /**
     * Returns the remaining number of servings.
     */
    public static int getRemainingServings(
            Level level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);

        Optional<FoodBlockHandler> handler =
                findHandler(state);

        if (handler.isEmpty()) {
            return -1;
        }

        return handler.get().getRemainingServings(
                level,
                pos,
                state
        );
    }

    /**
     * Makes a maid consume exactly one serving
     * from the food block at the supplied position.
     *
     * Actual world modification must only happen
     * on the logical server.
     *
     * The individual handler owns the food-specific
     * consumption logic.
     *
     * FoodBlockService owns common maid presentation,
     * such as swinging the main hand after a successful bite.
     */
    public static FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos
    ) {
        if (level.isClientSide) {
            return FoodConsumeResult.clientSide();
        }

        BlockState state = level.getBlockState(pos);

        if (FoodSafetyRules.isHardBlacklisted(state.getBlock().asItem())) {
            return FoodConsumeResult.unsupported();
        }

        Optional<FoodBlockHandler> optionalHandler =
                findHandler(state);

        if (optionalHandler.isEmpty()) {
            return FoodConsumeResult.unsupported();
        }

        FoodBlockHandler handler =
                optionalHandler.get();

        int servings =
                handler.getRemainingServings(
                        level,
                        pos,
                        state
                );

        if (servings == 0) {
            return FoodConsumeResult.empty(
                    handler.getId(),
                    0
            );
        }

        FoodConsumeResult result =
                handler.consumeOne(
                        maid,
                        level,
                        pos,
                        state
                );

        /*
         * TLM's edible-block implementations perform the
         * actual food interaction, sounds and particles,
         * while TLM's normal AI task performs the hand swing
         * after consume() returns true.
         *
         * WGON calls edible implementations directly, so
         * WGON must perform that presentation step itself.
         */
        if (result.consumed()) {
            maid.swing(
                    InteractionHand.MAIN_HAND
            );
        }

        return result;
    }
}
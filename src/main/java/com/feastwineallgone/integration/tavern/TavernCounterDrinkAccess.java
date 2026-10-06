package com.feastwineallgone.integration.tavern;

import com.feastwineallgone.config.WgonConfig;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.DrinkBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.CocktailBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.DrinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Tavern drinks physically displayed directly on top of a support
 * block registered in WGON's idle-counter whitelist.
 *
 * The original Kaleidoscope Tavern bar counter is only the default config
 * entry. It is deliberately not hard-coded here: pack makers may remove it,
 * add other registered blocks, or leave the list empty.
 */
public final class TavernCounterDrinkAccess {

    private TavernCounterDrinkAccess() {
    }

    /**
     * Find drink positions.
     *
     * We scan for bar counters first and only inspect
     * the single block directly above each counter.
     */
    public static List<BlockPos> findAvailableDrinks(
            ServerLevel level,
            BlockPos origin,
            int radius
    ) {
        List<BlockPos> result =
                new ArrayList<>();

        int radiusSquared =
                radius * radius;

        BlockPos min =
                origin.offset(
                        -radius,
                        -radius,
                        -radius
                );

        BlockPos max =
                origin.offset(
                        radius,
                        radius,
                        radius
                );

        for (BlockPos pos :
                BlockPos.betweenClosed(
                        min,
                        max
                )) {

            if (pos.distSqr(origin)
                    > radiusSquared) {

                continue;
            }

            BlockState counterState =
                    level.getBlockState(
                            pos
                    );

            if (!isAllowedCounterSurface(
                    counterState
            )) {

                continue;
            }

            BlockPos drinkPos =
                    pos.above();

            /*
             * Structural compatibility is not enough here. Some functional
             * furniture / rack blocks can expose drink-like block states but
             * do not actually contain a removable Tavern serving. Discovery
             * must only publish targets that can be previewed right now.
             * Otherwise an invalid custom counter can poison the idle selector
             * with a target that can never complete.
             */
            if (isExtractableDrinkAt(
                    level,
                    drinkPos
            )) {

                result.add(
                        drinkPos.immutable()
                );
            }
        }

        return result;
    }

    /**
     * Strong bottom-level safety check.
     *
     * Even if another WGON behaviour accidentally calls this
     * class, the drink MUST physically stand on a currently configured
     * idle-counter support block.
     */
    public static boolean isSupportedDrinkAt(
            ServerLevel level,
            BlockPos drinkPos
    ) {
        BlockState below =
                level.getBlockState(
                        drinkPos.below()
                );

        if (!isAllowedCounterSurface(
                below
        )) {

            return false;
        }

        Block block =
                level.getBlockState(
                        drinkPos
                ).getBlock();

        return block instanceof DrinkBlock
                || block instanceof CocktailBlock;
    }

    /**
     * True only when this block's registry ID is currently present in the
     * user-editable idle-counter list. No Tavern block receives an implicit
     * exemption, including the default bar_counter.
     */
    public static boolean isAllowedCounterSurface(
            BlockState state
    ) {
        ResourceLocation id =
                ForgeRegistries.BLOCKS
                        .getKey(
                                state.getBlock()
                        );

        if (id == null) {
            return false;
        }

        String registryId = id.toString();

        for (String configured :
                WgonConfig.COUNTER_DRINK_SURFACE_WHITELIST.get()) {

            if (configured != null
                    && registryId.equals(configured.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Strong runtime target check used by idle discovery and approach logic.
     *
     * isSupportedDrinkAt() answers only "is this the right kind of block on
     * an allowed surface?".  A functional rack or unusual addon can still
     * present a structurally compatible block without a real removable serving.
     * This method additionally requires a real preview ItemStack.
     */
    public static boolean isExtractableDrinkAt(
            ServerLevel level,
            BlockPos drinkPos
    ) {
        if (!isSupportedDrinkAt(
                level,
                drinkPos
        )) {
            return false;
        }

        return !peekOne(
                level,
                drinkPos
        ).isEmpty();
    }

    /**
     * Number of real servings currently available at this
     * one displayed position.
     *
     * DrinkBlock:
     * count is cross-checked against actual BlockEntity items.
     *
     * CocktailBlock:
     * one physical cocktail block = one serving.
     */
    public static int getAvailableCount(
            ServerLevel level,
            BlockPos drinkPos
    ) {
        if (!isSupportedDrinkAt(
                level,
                drinkPos
        )) {

            return 0;
        }

        BlockState state =
                level.getBlockState(
                        drinkPos
                );

        if (state.getBlock()
                instanceof DrinkBlock drinkBlock) {

            BlockEntity blockEntity =
                    level.getBlockEntity(
                            drinkPos
                    );

            if (!(blockEntity
                    instanceof DrinkBlockEntity be)) {

                return 0;
            }

            int visualCount =
                    state.getValue(
                            drinkBlock
                                    .getCountProperty()
                    );

            int actualCount =
                    0;

            for (ItemStack stack :
                    be.getItems()) {

                if (!stack.isEmpty()) {
                    actualCount++;
                }
            }

            return Math.min(
                    visualCount,
                    actualCount
            );
        }

        if (state.getBlock()
                instanceof CocktailBlock) {

            return peekOne(
                    level,
                    drinkPos
            ).isEmpty()
                    ? 0
                    : 1;
        }

        return 0;
    }

    /**
     * Look at the next drink without modifying the world.
     *
     * Useful before stealing a second bottle:
     * WGON can verify that the maid backpack has room before
     * physically removing anything.
     */
    public static ItemStack peekOne(
            ServerLevel level,
            BlockPos drinkPos
    ) {
        if (!isSupportedDrinkAt(
                level,
                drinkPos
        )) {

            return ItemStack.EMPTY;
        }

        BlockState state =
                level.getBlockState(
                        drinkPos
                );

        if (state.getBlock()
                instanceof DrinkBlock) {

            BlockEntity blockEntity =
                    level.getBlockEntity(
                            drinkPos
                    );

            if (!(blockEntity
                    instanceof DrinkBlockEntity be)) {

                return ItemStack.EMPTY;
            }

            List<ItemStack> items =
                    be.getItems();

            /*
             * Match DrinkBlockEntity.removeItem():
             *
             * it searches from the last slot toward zero.
             */
            for (int i =
                 items.size() - 1;
                 i >= 0;
                 i--) {

                ItemStack stack =
                        items.get(i);

                if (!stack.isEmpty()) {
                    ItemStack one =
                            stack.copy();

                    one.setCount(1);

                    return one;
                }
            }

            return ItemStack.EMPTY;
        }

        if (state.getBlock()
                instanceof CocktailBlock) {

            BlockEntity blockEntity =
                    level.getBlockEntity(
                            drinkPos
                    );

            List<ItemStack> drops =
                    Block.getDrops(
                            state,
                            level,
                            drinkPos,
                            blockEntity
                    );

            for (ItemStack stack :
                    drops) {

                if (TavernDrinkAccess
                        .isDrink(stack)) {

                    ItemStack one =
                            stack.copy();

                    one.setCount(1);

                    return one;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Atomically remove exactly ONE drink serving.
     *
     * For DrinkBlock:
     *
     * BlockEntity item count AND BlockState visual count are
     * updated together.
     *
     * For CocktailBlock:
     *
     * one cocktail block is converted back to its real loot
     * ItemStack and then removed.
     */
    public static ItemStack takeOne(
            ServerLevel level,
            BlockPos drinkPos
    ) {
        if (!isSupportedDrinkAt(
                level,
                drinkPos
        )) {

            return ItemStack.EMPTY;
        }

        BlockState state =
                level.getBlockState(
                        drinkPos
                );

        if (state.getBlock()
                instanceof DrinkBlock drinkBlock) {

            return takeOneBottle(
                    level,
                    drinkPos,
                    state,
                    drinkBlock
            );
        }

        if (state.getBlock()
                instanceof CocktailBlock) {

            return takeOneCocktail(
                    level,
                    drinkPos,
                    state
            );
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack takeOneBottle(
            ServerLevel level,
            BlockPos drinkPos,
            BlockState state,
            DrinkBlock drinkBlock
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(
                        drinkPos
                );

        if (!(blockEntity
                instanceof DrinkBlockEntity be)) {

            return ItemStack.EMPTY;
        }

        /*
         * First remove the REAL ItemStack.
         *
         * This preserves brew level, NBT and any other
         * per-bottle data stored by Kaleidoscope Tavern.
         */
        ItemStack taken =
                be.removeItem();

        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }

        taken.setCount(1);

        be.refresh();

        int count =
                state.getValue(
                        drinkBlock
                                .getCountProperty()
                );

        if (count > 1) {

            level.setBlock(
                    drinkPos,
                    state.setValue(
                            drinkBlock
                                    .getCountProperty(),
                            count - 1
                    ),
                    Block.UPDATE_ALL
            );

        } else {

            /*
             * Mirror DrinkBlock's own final-bottle behaviour.
             */
            level.destroyBlock(
                    drinkPos,
                    false
            );
        }

        return taken;
    }

    private static ItemStack takeOneCocktail(
            ServerLevel level,
            BlockPos drinkPos,
            BlockState state
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(
                        drinkPos
                );

        List<ItemStack> drops =
                Block.getDrops(
                        state,
                        level,
                        drinkPos,
                        blockEntity
                );

        ItemStack drink =
                ItemStack.EMPTY;

        for (ItemStack stack :
                drops) {

            if (TavernDrinkAccess
                    .isDrink(stack)) {

                drink =
                        stack.copy();

                drink.setCount(1);

                break;
            }
        }

        /*
         * If the loot result cannot produce a recognised
         * CocktailBlockItem, DO NOT remove the world block.
         */
        if (drink.isEmpty()) {
            return ItemStack.EMPTY;
        }

        /*
         * Mirrors GlasswareBlock's empty-hand pickup logic.
         */
        level.setBlock(
                drinkPos,
                Blocks.AIR
                        .defaultBlockState(),
                35
        );

        return drink;
    }
}
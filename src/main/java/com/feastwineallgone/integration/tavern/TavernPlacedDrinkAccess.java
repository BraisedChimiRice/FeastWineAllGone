package com.feastwineallgone.integration.tavern;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.DrinkBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.CocktailBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.DrinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Low-level access for Tavern drink blocks physically placed in the world.
 *
 * Unlike TavernCounterDrinkAccess this class does NOT decide where a drink is
 * allowed to be consumed from. Callers must provide that safety boundary.
 * SeatedDiningManager only calls this helper for the block directly above a
 * confirmed Kaleidoscope Cookery TableBlock, so shelves/floors/decorative
 * collections remain untouched.
 */
public final class TavernPlacedDrinkAccess {

    private TavernPlacedDrinkAccess() {
    }

    public static boolean isSupportedDrinkAt(ServerLevel level, BlockPos drinkPos) {
        Block block = level.getBlockState(drinkPos).getBlock();
        return block instanceof DrinkBlock || block instanceof CocktailBlock;
    }

    /**
     * Peek the exact next physical serving without changing the world.
     * DrinkBlock preserves its real bottle NBT/brew level from the block entity.
     */
    public static ItemStack peekOne(ServerLevel level, BlockPos drinkPos) {
        if (!isSupportedDrinkAt(level, drinkPos)) {
            return ItemStack.EMPTY;
        }

        BlockState state = level.getBlockState(drinkPos);

        if (state.getBlock() instanceof DrinkBlock) {
            BlockEntity blockEntity = level.getBlockEntity(drinkPos);
            if (!(blockEntity instanceof DrinkBlockEntity be)) {
                return ItemStack.EMPTY;
            }

            List<ItemStack> items = be.getItems();
            for (int i = items.size() - 1; i >= 0; i--) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    ItemStack one = stack.copy();
                    one.setCount(1);
                    return one;
                }
            }
            return ItemStack.EMPTY;
        }

        if (state.getBlock() instanceof CocktailBlock) {
            BlockEntity blockEntity = level.getBlockEntity(drinkPos);
            List<ItemStack> drops = Block.getDrops(
                    state,
                    level,
                    drinkPos,
                    blockEntity
            );

            for (ItemStack stack : drops) {
                if (TavernDrinkAccess.isDrink(stack)) {
                    ItemStack one = stack.copy();
                    one.setCount(1);
                    return one;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Remove exactly one real serving from the displayed Tavern drink block.
     */
    public static ItemStack takeOne(ServerLevel level, BlockPos drinkPos) {
        if (!isSupportedDrinkAt(level, drinkPos)) {
            return ItemStack.EMPTY;
        }

        BlockState state = level.getBlockState(drinkPos);

        if (state.getBlock() instanceof DrinkBlock drinkBlock) {
            return takeOneBottle(level, drinkPos, state, drinkBlock);
        }

        if (state.getBlock() instanceof CocktailBlock) {
            return takeOneCocktail(level, drinkPos, state);
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack takeOneBottle(
            ServerLevel level,
            BlockPos drinkPos,
            BlockState state,
            DrinkBlock drinkBlock
    ) {
        BlockEntity blockEntity = level.getBlockEntity(drinkPos);
        if (!(blockEntity instanceof DrinkBlockEntity be)) {
            return ItemStack.EMPTY;
        }

        ItemStack taken = be.removeItem();
        if (taken.isEmpty()) {
            return ItemStack.EMPTY;
        }

        taken.setCount(1);
        be.refresh();

        int count = state.getValue(drinkBlock.getCountProperty());
        if (count > 1) {
            level.setBlock(
                    drinkPos,
                    state.setValue(drinkBlock.getCountProperty(), count - 1),
                    Block.UPDATE_ALL
            );
        } else {
            level.destroyBlock(drinkPos, false);
        }

        return taken;
    }

    private static ItemStack takeOneCocktail(
            ServerLevel level,
            BlockPos drinkPos,
            BlockState state
    ) {
        BlockEntity blockEntity = level.getBlockEntity(drinkPos);
        List<ItemStack> drops = Block.getDrops(
                state,
                level,
                drinkPos,
                blockEntity
        );

        ItemStack drink = ItemStack.EMPTY;
        for (ItemStack stack : drops) {
            if (TavernDrinkAccess.isDrink(stack)) {
                drink = stack.copy();
                drink.setCount(1);
                break;
            }
        }

        if (drink.isEmpty()) {
            return ItemStack.EMPTY;
        }

        level.setBlock(
                drinkPos,
                Blocks.AIR.defaultBlockState(),
                35
        );

        return drink;
    }
}

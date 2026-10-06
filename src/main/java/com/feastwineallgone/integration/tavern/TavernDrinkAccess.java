package com.feastwineallgone.integration.tavern;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopetavern.item.CocktailBlockItem;
import com.github.ysbbbbbb.kaleidoscopetavern.item.DrinkBlockItem;
import com.github.ysbbbbbb.kaleidoscopetavern.item.IHasContainer;
import com.feastwineallgone.mixin.CocktailBlockItemInvoker;
import com.feastwineallgone.mixin.DrinkBlockItemInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public final class TavernDrinkAccess {

    private TavernDrinkAccess() {
    }

    public static boolean isDrink(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return false;
        }

        return stack.getItem()
                instanceof DrinkBlockItem
                || stack.getItem()
                instanceof CocktailBlockItem;
    }

    public static boolean isBottleDrink(
            ItemStack stack
    ) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem()
                instanceof DrinkBlockItem;
    }

    public static boolean isCocktail(
            ItemStack stack
    ) {
        return stack != null
                && !stack.isEmpty()
                && stack.getItem()
                instanceof CocktailBlockItem;
    }

    /**
     * Consume one detached Tavern drink.
     *
     * This method deliberately DOES NOT call
     * finishUsingItem().
     *
     * Tavern's finishUsingItem() only shrinks the drink for
     * Player entities. For a maid it also drops the returned
     * empty container into the world.
     *
     * WGON instead:
     *
     * 1. directly invokes Tavern's native drink effect
     * 2. creates exactly ONE empty container
     * 3. inserts that container into the maid backpack
     */
    public static boolean consumeDetachedDrink(
            ServerLevel level,
            EntityMaid maid,
            ItemStack stack
    ) {
        return consumeDetachedDrink(
                level,
                maid,
                stack,
                true
        );
    }

    /**
     * Same as consumeDetachedDrink(), but allows callers such
     * as night-skip resolution to suppress the drinking sound.
     */
    public static boolean consumeDetachedDrink(
            ServerLevel level,
            EntityMaid maid,
            ItemStack stack,
            boolean playSound
    ) {
        if (!isDrink(
                stack
        )) {

            return false;
        }

        ItemStack one =
                stack.copy();

        one.setCount(
                1
        );

        Item item =
                one.getItem();

        if (item
                instanceof DrinkBlockItem drinkItem) {

            ((DrinkBlockItemInvoker)
                    (Object) drinkItem)
                    .feastwineallgone$invokeAddDrinkEffect(
                            one,
                            level,
                            maid
                    );

        } else if (item
                instanceof CocktailBlockItem cocktailItem) {

            ((CocktailBlockItemInvoker)
                    (Object) cocktailItem)
                    .feastwineallgone$invokeAddDrinkEffect(
                            one,
                            level,
                            maid
                    );

        } else {

            return false;
        }

        giveContainerAfterDrinking(
                level,
                maid,
                item
        );

        if (playSound) {

            level.playSound(
                    null,
                    maid.blockPosition(),
                    SoundEvents.GENERIC_DRINK,
                    SoundSource.NEUTRAL,
                    0.9F,
                    0.95F
                            + level.random.nextFloat()
                            * 0.1F
            );
        }

        return true;
    }

    private static void giveContainerAfterDrinking(
            ServerLevel level,
            EntityMaid maid,
            Item drinkItem
    ) {
        if (!(drinkItem
                instanceof IHasContainer containerDrink)) {

            return;
        }

        Item containerItem =
                containerDrink.getContainerItem();

        if (containerItem == null) {
            return;
        }

        ItemStack container =
                containerItem
                        .getDefaultInstance();

        if (container.isEmpty()) {
            return;
        }

        ItemStack remainder =
                ItemHandlerHelper
                        .insertItemStacked(
                                maid.getAvailableBackpackInv(),
                                container,
                                false
                        );

        if (!remainder.isEmpty()) {

            ItemEntity dropped =
                    new ItemEntity(
                            level,
                            maid.getX(),
                            maid.getY() + 0.5D,
                            maid.getZ(),
                            remainder
                    );

            level.addFreshEntity(
                    dropped
            );
        }
    }

    /**
     * Test whether one complete stack can fit into the
     * maid's currently available backpack inventory.
     */
    public static boolean canStoreFully(
            EntityMaid maid,
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return true;
        }

        ItemStack remainder =
                ItemHandlerHelper
                        .insertItemStacked(
                                maid.getAvailableBackpackInv(),
                                stack.copy(),
                                true
                        );

        return remainder.isEmpty();
    }

    /**
     * Store the supplied stack into the maid backpack.
     *
     * Returns true only if the whole stack was inserted.
     */
    public static boolean storeInBackpack(
            EntityMaid maid,
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return true;
        }

        ItemStack remainder =
                ItemHandlerHelper
                        .insertItemStacked(
                                maid.getAvailableBackpackInv(),
                                stack.copy(),
                                false
                        );

        return remainder.isEmpty();
    }
}
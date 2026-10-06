package com.feastwineallgone.integration.cookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.api.item.IHasContainer;
import com.github.ysbbbbbb.kaleidoscopecookery.item.ClayPotMilkTeaItem;
import com.github.ysbbbbbb.kaleidoscopecookery.item.TeacupItem;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Maid-safe consumption helpers for Kaleidoscope Cookery drinks.
 *
 * Cookery's TeacupItem only shrinks the stack for Player entities.
 * Calling its normal finishUsingItem() on a maid therefore leaves the
 * tea item intact and may return/drop the empty cup in a way that is not
 * suitable for TLM backpack handling.
 *
 * WGON therefore invokes Cookery's own protected addTeaEffect(...) method
 * and handles the empty cup explicitly.
 *
 * This bridge deliberately does NOT use a Mixin accessor. Keeping this
 * tiny compatibility call reflective prevents a missing/stale mixin JSON
 * from crashing the server the moment a maid drinks tea.
 */
public final class CookeryDrinkAccess {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final Method TEA_EFFECT_METHOD =
            findTeaEffectMethod();

    private CookeryDrinkAccess() {
    }

    public static boolean consumeDetachedDrink(
            ServerLevel level,
            EntityMaid maid,
            ItemStack serving
    ) {
        if (serving == null
                || serving.isEmpty()) {

            return false;
        }

        Item item =
                serving.getItem();

        if (item
                instanceof TeacupItem teacupItem) {

            if (!applyTeaEffect(
                    teacupItem,
                    level,
                    maid
            )) {

                return false;
            }

            giveContainer(
                    level,
                    maid,
                    teacupItem
            );

        } else if (item
                instanceof ClayPotMilkTeaItem) {

            ItemStack one =
                    serving.copy();

            one.setCount(
                    1
            );

            /*
             * ClayPotMilkTeaItem already handles a non-player
             * LivingEntity safely enough when the detached stack contains
             * exactly one serving, so keep Cookery's own lifecycle here.
             */
            ItemStack remainder =
                    one.finishUsingItem(
                            level,
                            maid
                    );

            storeOrDrop(
                    level,
                    maid,
                    remainder
            );

        } else {
            return false;
        }

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

        return true;
    }

    /**
     * Calls TeacupItem's own protected addTeaEffect(Level, LivingEntity)
     * implementation.
     *
     * WGON does not duplicate any tea-effect table. If Cookery changes a
     * particular tea's effect data, this still executes Cookery's own code.
     */
    private static boolean applyTeaEffect(
            TeacupItem teacupItem,
            Level level,
            LivingEntity entity
    ) {
        if (TEA_EFFECT_METHOD == null) {
            return false;
        }

        try {
            TEA_EFFECT_METHOD.invoke(
                    teacupItem,
                    level,
                    entity
            );

            return true;

        } catch (IllegalAccessException
                 | InvocationTargetException
                 | RuntimeException exception) {

            LOGGER.error(
                    "WGON failed to apply a Kaleidoscope Cookery tea effect",
                    exception
            );

            return false;
        }
    }

    private static Method findTeaEffectMethod() {
        try {
            Method method =
                    TeacupItem.class
                            .getDeclaredMethod(
                                    "addTeaEffect",
                                    Level.class,
                                    LivingEntity.class
                            );

            if (!method.trySetAccessible()) {
                method.setAccessible(
                        true
                );
            }

            return method;

        } catch (NoSuchMethodException
                 | SecurityException exception) {

            LOGGER.error(
                    "WGON could not access Kaleidoscope Cookery TeacupItem.addTeaEffect",
                    exception
            );

            return null;
        }
    }

    private static void giveContainer(
            ServerLevel level,
            EntityMaid maid,
            Item drinkItem
    ) {
        if (!(drinkItem
                instanceof IHasContainer hasContainer)) {

            return;
        }

        Item containerItem =
                hasContainer.getContainerItem();

        if (containerItem == null) {
            return;
        }

        storeOrDrop(
                level,
                maid,
                containerItem.getDefaultInstance()
        );
    }

    public static void storeOrDrop(
            ServerLevel level,
            EntityMaid maid,
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {

            return;
        }

        ItemStack remainder =
                ItemHandlerHelper.insertItemStacked(
                        maid.getAvailableBackpackInv(),
                        stack.copy(),
                        false
                );

        if (remainder.isEmpty()) {
            return;
        }

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

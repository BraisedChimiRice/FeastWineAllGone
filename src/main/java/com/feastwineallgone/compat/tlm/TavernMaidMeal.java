package com.feastwineallgone.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.meal.IMaidMeal;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler;
import com.github.tartaricacid.touhoulittlemaid.network.message.SpawnParticleMessage;
import com.github.ysbbbbbb.kaleidoscopetavern.item.BottleBlockItem;
import com.feastwineallgone.integration.tavern.TavernDrinkAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Kaleidoscope Tavern drink compatibility for
 * Touhou Little Maid's native meal system.
 *
 * Important separation of responsibilities:
 *
 * 1. WGON theft behaviours consume the bottle they just stole directly.
 *    Their own quality/quantity rules belong only to that theft session.
 *
 * 2. A bottle that has already been stored in the maid backpack is no
 *    longer part of the theft session. It goes back to TLM's normal
 *    WORK_MEAL / HOME_MEAL / HEAL_MEAL scheduling.
 *
 * The native TLM meal AIs use FavorabilityManager cooldown types to stop
 * themselves from immediately eating again. Because Tavern drinks are not
 * vanilla food, WGON must explicitly apply the corresponding TLM cooldown
 * when a Tavern drink is accepted as a work/home meal.
 *
 * Without that cooldown, the TLM task can see another bottle in the
 * backpack on its next check and start drinking again, which looks like
 * an endless Vintage-wine binge.
 */
public final class TavernMaidMeal
        implements IMaidMeal {

    private final MaidMealType mealType;

    public TavernMaidMeal(
            MaidMealType mealType
    ) {
        this.mealType =
                mealType;
    }

    @Override
    public boolean canMaidEat(
            EntityMaid maid,
            ItemStack stack,
            InteractionHand hand
    ) {
        return TavernDrinkAccess
                .isDrink(
                        stack
                );
    }

    @Override
    public void onMaidEat(
            EntityMaid maid,
            ItemStack stack,
            InteractionHand hand
    ) {
        if (!TavernDrinkAccess
                .isDrink(
                        stack
                )) {

            return;
        }

        /*
         * Notify maid baubles exactly as normal TLM meals do.
         */
        maid.getMaidBauble()
                .fireEvent(
                        (bauble, baubleStack) -> {

                            bauble.onMaidEat(
                                    maid,
                                    baubleStack,
                                    stack,
                                    mealType
                            );

                            return false;
                        }
                );

        /*
         * This is the important fix for backpack wine.
         *
         * TLM checks these cooldowns before starting another work/home
         * meal. We apply zero favourability points here because WGON is
         * only restoring TLM's scheduling gate, not inventing an extra
         * affection reward for drinking from the backpack.
         *
         * The theft systems never call this method, so their immediate
         * first bottle and their quality-based quantity remain unchanged.
         */
        applyNativeMealCooldown(
                maid
        );

        /*
         * Start Kaleidoscope Tavern's normal item-use lifecycle.
         *
         * Tavern itself provides:
         *
         * - DRINK animation
         * - duration
         * - alcohol effects
         * - BrewLevel effects
         */
        maid.startUsingItem(
                hand
        );

        /*
         * Only HEAL_MEAL receives WGON's direct healing.
         *
         * Work meals and leisure drinking do not magically heal
         * unless Tavern's own drink effect happens to do so.
         */
        if (mealType
                == MaidMealType.HEAL_MEAL) {

            float healAmount =
                    getHealAmount(
                            stack
                    );

            if (maid.getHealth()
                    < maid.getMaxHealth()) {

                maid.heal(
                        healAmount
                );

                /*
                 * Reuse Touhou Little Maid's own healing-food
                 * particles so alcohol behaves visually like a
                 * normal emergency meal.
                 */
                NetworkHandler.sendToNearby(
                        maid,
                        new SpawnParticleMessage(
                                maid.getId(),
                                SpawnParticleMessage.Type.HEAL,
                                stack.getUseDuration()
                        )
                );
            }
        }
    }

    /**
     * Restores the same cooldown gate used by TLM's own meal AIs.
     *
     * WORK_MEAL and HOME_MEAL are periodic behaviours. Their brain tasks
     * check FavorabilityManager.canAdd(...) before another meal can start.
     * Applying the matching Type here closes that gate for the normal TLM
     * cooldown duration.
     *
     * HEAL_MEAL is health-driven rather than a normal leisure/work meal,
     * so it is deliberately not given either of these cooldowns here.
     */
    private void applyNativeMealCooldown(
            EntityMaid maid
    ) {
        if (mealType
                == MaidMealType.WORK_MEAL) {

            maid.getFavorabilityManager()
                    .apply(
                            Type.WORK_MEAL,
                            0
                    );

            return;
        }

        if (mealType
                == MaidMealType.HOME_MEAL) {

            maid.getFavorabilityManager()
                    .apply(
                            Type.HOME_MEAL,
                            0
                    );
        }
    }

    /**
     * WGON emergency-meal healing:
     *
     * BrewLevel 0 ->  2 HP
     * BrewLevel 1 ->  4 HP
     * BrewLevel 2 ->  6 HP
     * BrewLevel 3 ->  8 HP
     * BrewLevel 4 -> 10 HP
     * BrewLevel 5 -> 12 HP
     * BrewLevel 6 -> 14 HP
     *
     * This affects HEAL_MEAL healing amount only.
     * It does NOT control how many bottles the maid drinks.
     *
     * BottleBlockItem.getBrewLevel() returns 0 when an item has
     * no BrewLevel tag, so cocktails naturally fall back to 2 HP.
     */
    private static float getHealAmount(
            ItemStack stack
    ) {
        int brewLevel =
                BottleBlockItem
                        .getBrewLevel(
                                stack
                        );

        int clampedLevel =
                Math.max(
                        0,
                        Math.min(
                                6,
                                brewLevel
                        )
                );

        return 2.0F
                * (clampedLevel + 1);
    }
}

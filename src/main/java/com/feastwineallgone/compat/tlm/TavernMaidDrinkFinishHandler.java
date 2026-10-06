package com.feastwineallgone.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.integration.compat.SpecialDrinkSoundAccess;
import com.feastwineallgone.integration.tavern.TavernDrinkAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Compatibility fix between:
 *
 * Touhou Little Maid
 * +
 * Kaleidoscope Tavern
 *
 * Kaleidoscope Tavern's DrinkBlockItem and CocktailBlockItem
 * only shrink their ItemStack when the drinking entity is
 * a Player.
 *
 * EntityMaid is a LivingEntity, not a Player, so without this
 * compatibility handler a maid can finish drinking normally
 * while the original drink remains in her hand/inventory.
 *
 * Result:
 *
 * infinite wine.
 *
 * This handler does NOT modify Touhou Little Maid or
 * Kaleidoscope Tavern source/JAR files.
 *
 * It uses Forge's normal LivingEntityUseItemEvent.Finish hook
 * and corrects the result stack only for:
 *
 * EntityMaid + Kaleidoscope Tavern drink.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class TavernMaidDrinkFinishHandler {

    private TavernMaidDrinkFinishHandler() {
    }

    @SubscribeEvent
    public static void onDrinkUseTick(
            LivingEntityUseItemEvent.Tick event
    ) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }

        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        ItemStack drinking = event.getItem();
        if (drinking == null || drinking.isEmpty()) {
            return;
        }

        /*
         * World Liquor's 劲凉冰红茶 clip is a use-tick sound, not a finish
         * sound.  Reproduce its original cadence (every four elapsed ticks)
         * for a maid while WGON/TLM is visibly drinking the item.
         */
        int elapsedUseTicks = Math.max(
                0,
                drinking.getUseDuration() - event.getDuration()
        );

        SpecialDrinkSoundAccess.playDuringDrinkTickIfNeeded(
                level,
                maid,
                drinking,
                elapsedUseTicks
        );
    }

    @SubscribeEvent
    public static void onDrinkFinished(
            LivingEntityUseItemEvent.Finish event
    ) {
        /*
         * Only Touhou Little Maid entities.
         */
        if (!(event.getEntity()
                instanceof EntityMaid maid)) {

            return;
        }

        /*
         * event.getItem() is the ItemStack from BEFORE the
         * item finished being used.
         *
         * Only intervene for drinks WGON recognises as
         * Kaleidoscope Tavern drinks.
         */
        ItemStack original =
                event.getItem();

        if (!TavernDrinkAccess
                .isDrink(
                        original
                )) {

            return;
        }

        /*
         * Kaleidoscope Tavern does not shrink drinks for a maid
         * because the maid is not a Player.
         *
         * Recreate the correct post-drink stack here.
         */
        ItemStack remaining =
                original.copy();

        remaining.shrink(
                1
        );

        /*
         * If that was the final drink in the stack, replace the
         * used hand with EMPTY.
         *
         * Otherwise leave the remaining stack count.
         */
        if (remaining.isEmpty()) {

            event.setResultStack(
                    ItemStack.EMPTY
            );

        } else {

            event.setResultStack(
                    remaining
            );
        }

        /*
         * World Liquor intentionally gates its ice-tea MAN! event behind
         * Player, so Touhou Little Maid never receives it naturally.  Mirror
         * the registered finish sound on the server after a real maid drink.
         */
        if (maid.level() instanceof ServerLevel level) {
            SpecialDrinkSoundAccess.playAfterDrinkIfNeeded(
                    level,
                    maid,
                    original
            );
        }
    }
}
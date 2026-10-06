package com.feastwineallgone.food;

import com.feastwineallgone.food.handler.BountifulFaresPastryHandler;
import com.feastwineallgone.food.handler.CookeryFoodHandler;
import com.feastwineallgone.food.handler.CookeryPlatterHandler;
import com.feastwineallgone.food.handler.CookeryTeaHandler;
import com.feastwineallgone.food.handler.FarmersDelightFoodHandler;
import com.feastwineallgone.food.handler.FarmingTalesDirectBiteHandler;
import com.feastwineallgone.food.handler.FarmingTalesPlacedConsumableHandler;
import com.feastwineallgone.food.handler.GenericPlacedFoodHandler;
import com.feastwineallgone.food.handler.VanillaCakeHandler;
import net.minecraftforge.fml.ModList;

/**
 * Registers all food-block handlers used by WGON.
 *
 * Behaviour systems such as idle foraging, dinner mode
 * and night snacking should never register handlers themselves.
 */
public final class FoodBlockHandlers {

    private static boolean initialized = false;

    private FoodBlockHandlers() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        /*
         * Vanilla Minecraft.
         */
        FoodBlockService.register(
                new VanillaCakeHandler()
        );

        /*
         * Kaleidoscope Cookery normal bite-based dishes.
         */
        FoodBlockService.register(
                new CookeryFoodHandler()
        );

        /*
         * Kaleidoscope Cookery stackable foods and PlateBlock platters.
         */
        FoodBlockService.register(
                new CookeryPlatterHandler()
        );

        /*
         * Kaleidoscope Cookery tea / milk tea.
         */
        FoodBlockService.register(
                new CookeryTeaHandler()
        );

        /*
         * Farmer's Delight is optional.
         *
         * The compatibility class is only instantiated when the mod is
         * actually loaded, so WGON still starts normally without it.
         */
        if (ModList.get()
                .isLoaded(
                        "farmersdelight"
                )) {

            FoodBlockService.register(
                    new FarmersDelightFoodHandler()
            );
        }

        /*
         * Farming Tales pack-specific placeable consumables.
         *
         * This is tag-driven and has no compile-time dependency on the pack.
         * It can recognise KubeJS placeable foods and drinks that are marked
         * with Farming Tales' official compatibility tags.
         */
        /*
         * Farming Tales direct-eat staged dishes behave like Cookery bite
         * foods: the maid eats directly from the placed block, advances one
         * visual stage, and leaves the terminal empty bowl/pot/steamer model
         * in the world.  Register this before the detached-item compatibility.
         */
        FoodBlockService.register(
                new FarmingTalesDirectBiteHandler()
        );

        FoodBlockService.register(
                new FarmingTalesPlacedConsumableHandler()
        );

        /*
         * Bountiful Fares pies/tarts/cakes use direct block bites even when
         * their BlockItem is not edible, so they need to run before the
         * conservative generic fallback. No hard dependency is introduced.
         */
        FoodBlockService.register(
                new BountifulFaresPastryHandler()
        );

        /*
         * Generic fallback comes last on purpose.
         *
         * It has no direct dependency on Let's Do or any other food mod; it
         * recognises common placed-food conventions and only claims blocks
         * for which it can resolve a real edible serving ItemStack.
         */
        FoodBlockService.register(
                new GenericPlacedFoodHandler()
        );
    }
}

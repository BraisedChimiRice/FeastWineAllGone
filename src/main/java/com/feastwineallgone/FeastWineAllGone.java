package com.feastwineallgone;

import com.feastwineallgone.behavior.CounterDrinkManager;
import com.feastwineallgone.behavior.DrunkSleepManager;
import com.feastwineallgone.behavior.FavoriteAttentionManager;
import com.feastwineallgone.behavior.FavoriteTableDrinkManager;
import com.feastwineallgone.behavior.IdleActivityManager;
import com.feastwineallgone.behavior.IdleFoodManager;
import com.feastwineallgone.behavior.FruitTastingManager;
import com.feastwineallgone.behavior.NightStealManager;
import com.feastwineallgone.behavior.MealProgressManager;
import com.feastwineallgone.behavior.SeatedDiningManager;
import com.feastwineallgone.client.WgonClient;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.food.FoodBlockHandlers;
import com.feastwineallgone.network.WgonNetwork;
import com.feastwineallgone.registry.ModItems;
import com.feastwineallgone.registry.ModCreativeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FeastWineAllGone.MOD_ID)
public final class FeastWineAllGone {

    public static final String MOD_ID =
            "feastwineallgone";

    public FeastWineAllGone() {
        /*
         * Network must be registered first.
         */
        WgonNetwork.register();

        IEventBus modBus =
                FMLJavaModLoadingContext
                        .get()
                        .getModEventBus();

        /*
         * WGON registries.
         */
        ModItems.REGISTER.register(
                modBus
        );

        ModCreativeTabs.REGISTER.register(
                modBus
        );

        /*
         * Configuration.
         */
        ModLoadingContext
                .get()
                .registerConfig(
                        ModConfig.Type.COMMON,
                        WgonConfig.SPEC
                );

        /*
         * Built-in translated config screen (Mods -> WGON -> Config).
         * Keep all Minecraft client classes behind DistExecutor so a
         * dedicated server can still load the same universal JAR safely.
         */
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> WgonClient::registerConfigScreen
        );

        /*
         * Placed-food compatibility layer.
         */
        FoodBlockHandlers.init();

        /*
         * Runtime behaviour managers.
         *
         * Night stealing remains the high-priority sleep behaviour.
         */
        MinecraftForge.EVENT_BUS.register(
                NightStealManager.class
        );

        /*
         * Persistent post-drinking sleep state. This deliberately does not
         * hold the night-time lock; it only keeps the maid asleep until a
         * later wake condition is met.
         */
        MinecraftForge.EVENT_BUS.register(
                DrunkSleepManager.class
        );

        /*
         * These two managers execute already-selected sessions.
         * They no longer compete independently for idle maids.
         */
        MinecraftForge.EVENT_BUS.register(
                CounterDrinkManager.class
        );

        MinecraftForge.EVENT_BUS.register(
                IdleFoodManager.class
        );

        /*
         * Executes already-selected non-destructive orchard/berry tasting
         * sessions. Selection itself remains centralized in IdleActivityManager.
         */
        MinecraftForge.EVENT_BUS.register(
                FruitTastingManager.class
        );

        /*
         * Favorite-food attention runs before the ordinary selector.
         * It owns only the short 2-3 second begging/wagging reaction, then
         * hands control to the existing food or drink manager.
         */
        MinecraftForge.EVENT_BUS.register(
                FavoriteAttentionManager.class
        );

        MinecraftForge.EVENT_BUS.register(
                FavoriteTableDrinkManager.class
        );

        /*
         * Shared ordinary-idle selector.
         *
         * FOOD and bar-counter DRINK receive equal 50/50 weight when
         * both are available.
         */
        MinecraftForge.EVENT_BUS.register(
                IdleActivityManager.class
        );

        /*
         * Manual-sit dining integration for Kaleidoscope Cookery chairs
         * and table displays. Night stealing can pre-empt this manager.
         */
        MinecraftForge.EVENT_BUS.register(
                SeatedDiningManager.class
        );

        MinecraftForge.EVENT_BUS.register(
                MealProgressManager.class
        );
    }
}

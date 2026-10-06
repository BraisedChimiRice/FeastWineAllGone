package com.feastwineallgone.integration.compat;

import com.feastwineallgone.FeastWineAllGone;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Suppresses the vanilla GENERIC_EAT sound emitted by Item.finishUsingItem()
 * while WGON is finishing a Farming Tales item that is visually and
 * semantically a drink.
 *
 * Farming Tales/KubeJS drinks are ordinary edible Item instances with a DRINK
 * use animation. Vanilla Item.finishUsingItem() delegates those items to
 * LivingEntity.eat(), which applies the correct food/effect data but also
 * emits GENERIC_EAT. WGON still wants all of those effects and callbacks, so
 * replacing finishUsingItem() would be unnecessarily destructive. Instead we
 * cancel only that synchronous sound event for the duration of the call.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class FarmingTalesDrinkSoundGuard {

    private static final ThreadLocal<Integer> SUPPRESSION_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private FarmingTalesDrinkSoundGuard() {
    }

    public static ItemStack finishUsingItemWithoutGenericEat(
            ItemStack stack,
            ServerLevel level,
            LivingEntity entity
    ) {
        int depth = SUPPRESSION_DEPTH.get();
        SUPPRESSION_DEPTH.set(depth + 1);

        try {
            return stack.finishUsingItem(level, entity);
        } finally {
            if (depth <= 0) {
                SUPPRESSION_DEPTH.remove();
            } else {
                SUPPRESSION_DEPTH.set(depth);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayLevelSound(
            PlayLevelSoundEvent event
    ) {
        if (SUPPRESSION_DEPTH.get() <= 0) {
            return;
        }

        Holder<SoundEvent> sound =
                event.getSound();

        if (sound != null
                && sound.value() == SoundEvents.GENERIC_EAT) {
            event.setCanceled(true);
        }
    }
}

package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.FeastWineAllGone;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Temporary damage protection used only while a maid is
 * participating in WGON's nighttime barrel-stealing session.
 *
 * WGON deliberately does NOT touch Minecraft/TLM invulnerability
 * flags here.
 *
 * This prevents temporary WGON protection from becoming persistent
 * entity data or leaking into the maid's saved NBT.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class NightStealProtectionHandler {

    private NightStealProtectionHandler() {
    }

    @SubscribeEvent
    public static void onLivingAttack(
            LivingAttackEvent event
    ) {
        if (!(event.getEntity()
                instanceof EntityMaid maid)) {

            return;
        }

        if (MaidActionLock.isLockedBy(
                maid,
                NightStealManager.ACTION_ID
        )) {

            event.setCanceled(true);
        }
    }

    /**
     * Defensive second layer.
     *
     * Most ordinary damage is already stopped by LivingAttackEvent,
     * but cancelling LivingHurtEvent as well prevents damage from
     * proceeding if another mod injects later in the damage pipeline.
     */
    @SubscribeEvent
    public static void onLivingHurt(
            LivingHurtEvent event
    ) {
        if (!(event.getEntity()
                instanceof EntityMaid maid)) {

            return;
        }

        if (MaidActionLock.isLockedBy(
                maid,
                NightStealManager.ACTION_ID
        )) {

            event.setCanceled(true);
        }
    }
}
package com.feastwineallgone.integration.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Optional post-drink sound hooks for third-party drinks whose signature
 * audio is normally triggered only by their own player consumption path.
 *
 * No third-party classes are linked here.  Both item and sound detection are
 * registry-ID based so the compatibility remains completely optional.
 */
public final class SpecialDrinkSoundAccess {

    private static final ResourceLocation SMC_ICE_TEA =
            new ResourceLocation(
                    "smc",
                    "ice_tea"
            );

    private static final ResourceLocation WORLD_LIQUOR_ICE_TEA_EAT_SOUND =
            new ResourceLocation(
                    "kaleidoscope_world_liquor",
                    "ice_tea_eat"
            );

    private static final ResourceLocation WORLD_LIQUOR_COOL_TEA =
            new ResourceLocation(
                    "kaleidoscope_world_liquor",
                    "cool_tea"
            );

    private static final ResourceLocation WORLD_LIQUOR_COOL_TEA_DRINK_SOUND =
            new ResourceLocation(
                    "kaleidoscope_world_liquor",
                    "cool_ice_tea_drink"
            );

    private SpecialDrinkSoundAccess() {
    }

    /**
     * Play any signature sound that belongs to the consumed drink.
     *
     * Kaleidoscope World Liquor supplies the signature ice_tea_eat sound for
     * smc:ice_tea, but its original event handler only plays that sound for
     * Player entities.  A maid therefore needs this compatibility hook.
     *
     * Farming Tales also creates aged copies under IDs such as
     * farmingtales:smc_ice_tea_i / _g / _d; those copies keep the same
     * signature sound when consumed by a maid.
     */
    public static void playAfterDrinkIfNeeded(
            ServerLevel level,
            EntityMaid maid,
            ItemStack consumed
    ) {
        if (level == null
                || maid == null
                || consumed == null
                || consumed.isEmpty()) {
            return;
        }

        if (isSmcIceTea(consumed)) {
            /*
             * Normal ice tea: World Liquor plays ice_tea_eat for players.
             * Reproduce its distinctive finishing MAN! for maid drinking.
             */
            playOptionalSound(
                    level,
                    maid,
                    WORLD_LIQUOR_ICE_TEA_EAT_SOUND,
                    1.3F,
                    0.7F
            );
            return;
        }

        /*
         * 劲凉冰红茶 is intentionally NOT handled here.  World Liquor does
         * not play its signature clip at finish time: it plays it every four
         * use ticks while the drink is being raised.  WGON mirrors that in
         * playDuringDrinkTickIfNeeded() so the sound is not lost behind the
         * generic finishing gulp and does not get duplicated at the end.
         */
    }

    /**
     * Mirror World Liquor's use-tick sounds for maids.
     *
     * Normal ice tea plays ice_tea_eat every ten drinking ticks, while cool
     * tea plays cool_ice_tea_drink every four. WGON starts a normal
     * LivingEntity item-use animation for its visible drinking paths, so this
     * server-side Forge use-tick hook can reproduce both cadences for
     * EntityMaid without linking World Liquor classes directly.
     */
    public static void playDuringDrinkTickIfNeeded(
            ServerLevel level,
            EntityMaid maid,
            ItemStack drinking,
            int elapsedUseTicks
    ) {
        if (level == null
                || maid == null
                || drinking == null
                || drinking.isEmpty()
                || elapsedUseTicks < 0) {
            return;
        }

        ResourceLocation itemId =
                ForgeRegistries.ITEMS.getKey(
                        drinking.getItem()
                );

        /*
         * World Liquor's normal ice tea is not finish-only.  Its own player
         * handler starts a counter at zero and plays ICE_TEA_EAT every ten
         * drinking ticks, then plays the same event once more at Finish with
         * a louder/lower-pitched final hit.
         *
         * The original mod refuses that counter path for EntityMaid because
         * it requires Player.  Mirror the audible use-tick part here.
         */
        if (isSmcIceTea(drinking)
                && elapsedUseTicks % 10 == 0) {
            playOptionalSound(
                    level,
                    maid,
                    WORLD_LIQUOR_ICE_TEA_EAT_SOUND,
                    0.5F,
                    1.0F
            );
        }

        /*
         * 劲凉冰红茶 uses a separate four-tick clip.  Its World Liquor
         * implementation is also unsuitable for EntityMaid (it casts the
         * drinking entity to Player), so keep WGON's maid-safe mirror.
         */
        if (isWorldLiquorCoolTea(itemId)
                && elapsedUseTicks % 4 == 0) {
            playOptionalSound(
                    level,
                    maid,
                    WORLD_LIQUOR_COOL_TEA_DRINK_SOUND,
                    0.5F,
                    1.0F
            );
        }
    }

    private static void playOptionalSound(
            ServerLevel level,
            EntityMaid maid,
            ResourceLocation soundId,
            float volume,
            float pitch
    ) {
        SoundEvent sound =
                ForgeRegistries.SOUND_EVENTS.getValue(
                        soundId
                );

        /*
         * World Liquor is optional. If it is absent, or a future version
         * renames the event, keep normal drinking behaviour instead of
         * failing the maid action.
         */
        if (sound == null) {
            return;
        }

        level.playSound(
                null,
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private static boolean isWorldLiquorCoolTea(
            ResourceLocation itemId
    ) {
        if (itemId == null) {
            return false;
        }

        if (WORLD_LIQUOR_COOL_TEA.equals(itemId)) {
            return true;
        }

        /*
         * Farming Tales aging-barrel copies generated from
         * kaleidoscope_world_liquor:cool_tea:
         *   farmingtales:world_liquor_cool_tea
         *   farmingtales:world_liquor_cool_tea_i
         *   farmingtales:world_liquor_cool_tea_g
         *   farmingtales:world_liquor_cool_tea_d
         */
        return "farmingtales".equals(itemId.getNamespace())
                && ("world_liquor_cool_tea".equals(itemId.getPath())
                || itemId.getPath().startsWith("world_liquor_cool_tea_"));
    }

    private static boolean isSmcIceTea(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId =
                ForgeRegistries.ITEMS.getKey(
                        stack.getItem()
                );

        if (SMC_ICE_TEA.equals(itemId)) {
            return true;
        }

        /*
         * Farming Tales aging-barrel copies:
         *   farmingtales:smc_ice_tea
         *   farmingtales:smc_ice_tea_i
         *   farmingtales:smc_ice_tea_g
         *   farmingtales:smc_ice_tea_d
         */
        if (itemId != null
                && "farmingtales".equals(itemId.getNamespace())
                && ("smc_ice_tea".equals(itemId.getPath())
                || itemId.getPath().startsWith("smc_ice_tea_"))) {
            return true;
        }

        /*
         * Match World Liquor's own fallback semantics as closely as possible.
         * Its isIceTeaItem() accepts the SMC IceTea class OR any item whose
         * description ID contains "ice_tea".  The registry checks above keep
         * our known variants precise; this fallback protects compatibility
         * with the actual SMC item and future wrapped copies.
         */
        String className =
                stack.getItem()
                        .getClass()
                        .getName();

        if ("com.starmeow.smc.items.IceTea".equals(className)) {
            return true;
        }

        String descriptionId =
                stack.getItem()
                        .getDescriptionId();

        return descriptionId != null
                && descriptionId
                .toLowerCase(java.util.Locale.ROOT)
                .contains("ice_tea");
    }
}

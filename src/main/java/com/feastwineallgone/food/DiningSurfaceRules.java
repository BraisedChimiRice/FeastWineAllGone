package com.feastwineallgone.food;

import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.feastwineallgone.config.WgonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Controls which blocks may act as an ordinary dining surface for WGON.
 *
 * Kaleidoscope Cookery tables are always accepted. Pack makers/players may
 * additionally whitelist arbitrary block registry IDs through the common
 * config, which lets modded tables, counters, picnic furniture, etc. host
 * placed food without hard dependencies on those furniture mods.
 */
public final class DiningSurfaceRules {

    private DiningSurfaceRules() {
    }

    public static boolean isAllowedForFood(
            Level level,
            BlockPos foodPos
    ) {
        return isAllowedSurface(
                level.getBlockState(foodPos.below())
        );
    }

    public static boolean isAllowedSurface(
            BlockState state
    ) {
        if (state.getBlock() instanceof TableBlock) {
            return true;
        }

        return matchesConfiguredList(
                state,
                WgonConfig.DINING_SURFACE_WHITELIST.get()
        );
    }

    /**
     * Seated dining inherits every ordinary dining surface and may also have
     * extra seated-only surfaces. This avoids forcing pack makers to duplicate
     * the same table ID in two lists while still allowing stricter idle rules.
     */
    public static boolean isAllowedSeatedSurface(
            BlockState state
    ) {
        return isAllowedSurface(state)
                || matchesConfiguredList(
                state,
                WgonConfig.SEATED_DINING_SURFACE_WHITELIST.get()
        );
    }

    private static boolean matchesConfiguredList(
            BlockState state,
            Iterable<? extends String> configuredIds
    ) {
        ResourceLocation id =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (id == null) {
            return false;
        }

        String expected = id.toString();

        for (String configured : configuredIds) {
            if (configured != null
                    && expected.equals(configured.trim())) {
                return true;
            }
        }

        return false;
    }
}

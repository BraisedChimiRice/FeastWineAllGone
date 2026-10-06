package com.feastwineallgone.food;

import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.feastwineallgone.config.WgonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Resolves WGON's user-configurable favorite whitelist.
 *
 * The same list intentionally accepts both block registry IDs and item IDs:
 * - placed foods are normally matched by block ID;
 * - Tavern display blocks can contain real drink ItemStacks with richer IDs/NBT,
 *   so those are matched by item ID first and block ID as a fallback.
 */
public final class FavoriteFoodRules {

    private FavoriteFoodRules() {
    }

    /**
     * Favorite matching for a real placed food block.
     *
     * Do not require one specific table implementation underneath. A vanilla
     * cake can sit on many decorative/slab/snack-stand/table blocks, and the
     * whitelist is about the food itself, not the furniture supporting it.
     * This also means partially eaten cakes keep matching because their block
     * ID remains minecraft:cake while only the BITES state changes.
     */
    public static boolean isFavoritePlacedFood(
            ServerLevel level,
            BlockPos foodPos
    ) {
        if (!FoodBlockService.isSupportedFood(level, foodPos)) {
            return false;
        }

        return isFavoriteBlock(level.getBlockState(foodPos));
    }

    /**
     * Kept for compatibility with older WGON call sites.
     */
    public static boolean isFavoritePlacedFoodOnCookeryTable(
            ServerLevel level,
            BlockPos foodPos
    ) {
        if (!(level.getBlockState(foodPos.below()).getBlock() instanceof TableBlock)) {
            return false;
        }
        return isFavoritePlacedFood(level, foodPos);
    }

    public static boolean isFavoriteBlock(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return contains(id);
    }

    public static boolean isFavoriteItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return contains(id);
    }

    public static boolean isFavoriteDrink(
            ServerLevel level,
            BlockPos drinkPos,
            ItemStack actualDrink
    ) {
        if (isFavoriteItem(actualDrink)) {
            return true;
        }

        return isFavoriteBlock(level.getBlockState(drinkPos));
    }

    private static boolean contains(ResourceLocation id) {
        if (id == null) {
            return false;
        }

        String expected = id.toString();

        for (String configured : WgonConfig.FAVORITE_FOOD_WHITELIST.get()) {
            if (configured != null && expected.equals(configured.trim())) {
                return true;
            }
        }

        return false;
    }
}

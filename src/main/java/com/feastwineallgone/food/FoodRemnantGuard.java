package com.feastwineallgone.food;

import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.PlateBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteThreeByThreeBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.NinePart;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Final-remnant safety net shared with TLM's native stealing AI.
 *
 * WGON deliberately leaves empty plates / the last visible remnant after
 * autonomous meals. TLM's native Cookery edible adapter destroys a
 * FoodBiteBlock when consume() is called once more at max bites, so these
 * terminal states must not be handed back to that task later.
 *
 * Farmer's Delight is optional, therefore its terminal "servings == 0"
 * check is registry/property based instead of directly linking FD classes.
 */
public final class FoodRemnantGuard {

    private FoodRemnantGuard() {
    }

    public static boolean isProtectedRemnant(
            ServerLevel level,
            BlockPos pos
    ) {
        if (level == null || pos == null) {
            return false;
        }

        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof PlateBlock plate
                && state.hasProperty(plate.getServingsProperty())) {
            return state.getValue(plate.getServingsProperty()) <= 0;
        }

        if (state.getBlock() instanceof FoodBiteThreeByThreeBlock
                && state.hasProperty(FoodBiteThreeByThreeBlock.PART)) {
            NinePart part = state.getValue(FoodBiteThreeByThreeBlock.PART);
            BlockPos center = pos.offset(-part.getPosX(), 0, -part.getPosY());
            BlockState centerState = level.getBlockState(center);
            if (centerState.getBlock() instanceof FoodBiteBlock centerFood
                    && centerState.hasProperty(centerFood.getBites())) {
                return centerState.getValue(centerFood.getBites())
                        >= centerFood.getMaxBites();
            }
        }

        if (state.getBlock() instanceof FoodBiteBlock food
                && state.hasProperty(food.getBites())) {
            return state.getValue(food.getBites()) >= food.getMaxBites();
        }

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !"farmersdelight".equals(id.getNamespace())) {
            return false;
        }

        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof IntegerProperty integerProperty)) {
                continue;
            }
            if (!"servings".equals(integerProperty.getName())) {
                continue;
            }
            return state.getValue(integerProperty) <= 0;
        }

        return false;
    }
}

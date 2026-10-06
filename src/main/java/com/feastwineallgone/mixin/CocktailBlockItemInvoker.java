package com.feastwineallgone.mixin;

import com.github.ysbbbbbb.kaleidoscopetavern.item.CocktailBlockItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Gives WGON access to Kaleidoscope Tavern's original
 * cocktail effect application.
 */
@Mixin(
        value = CocktailBlockItem.class,
        remap = false
)
public interface CocktailBlockItemInvoker {

    @Invoker("addDrinkEffect")
    void feastwineallgone$invokeAddDrinkEffect(
            ItemStack drink,
            Level level,
            LivingEntity entity
    );
}
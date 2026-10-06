package com.feastwineallgone.mixin;

import com.github.ysbbbbbb.kaleidoscopetavern.item.DrinkBlockItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Gives WGON access to Kaleidoscope Tavern's own
 * protected drink-effect method.
 *
 * We deliberately use Tavern's original effect system
 * instead of copying potion/buff logic.
 */
@Mixin(
        value = DrinkBlockItem.class,
        remap = false
)
public interface DrinkBlockItemInvoker {

    @Invoker("addDrinkEffect")
    void feastwineallgone$invokeAddDrinkEffect(
            ItemStack drink,
            Level level,
            LivingEntity entity
    );
}
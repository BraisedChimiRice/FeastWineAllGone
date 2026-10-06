package com.feastwineallgone.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Visual-only cup used during barrel theft. It is registered as a real Item so
 * Touhou Little Maid's normal held-item renderer can place it on arbitrary maid models.
 * It is never added to a creative tab or recipe by this mod.
 */
public final class WoodenCupItem extends Item {
    public WoodenCupItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 40;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }
}

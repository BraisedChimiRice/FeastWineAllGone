package com.feastwineallgone.food;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Non-configurable safety exclusions shared by every FWAG food entry point. */
public final class FoodSafetyRules {
    private static final Set<Item> HARD_BLACKLIST = Set.of(
            Items.ROTTEN_FLESH,
            Items.SPIDER_EYE,
            Items.PUFFERFISH
    );

    private FoodSafetyRules() {}

    public static boolean isHardBlacklisted(ItemStack stack) {
        return stack != null && !stack.isEmpty() && HARD_BLACKLIST.contains(stack.getItem());
    }

    public static boolean isHardBlacklisted(Item item) {
        return item != null && HARD_BLACKLIST.contains(item);
    }
}

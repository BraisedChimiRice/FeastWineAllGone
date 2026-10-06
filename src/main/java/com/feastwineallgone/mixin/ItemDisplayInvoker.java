package com.feastwineallgone.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla 1.20.1 keeps ItemDisplay's item/transform setters non-public.
 * WGON uses a tiny Mixin invoker instead of reflection so the temporary mug
 * display remains fully synced through vanilla SynchedEntityData.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayInvoker {

    @Invoker("setItemStack")
    void feastwineallgone$setItemStack(ItemStack stack);

    @Invoker("setItemTransform")
    void feastwineallgone$setItemTransform(ItemDisplayContext context);
}

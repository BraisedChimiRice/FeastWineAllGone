package com.feastwineallgone.mixin;

import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accesses Kaleidoscope Tavern's own barrel cleanup logic.
 *
 * WGON does not reproduce or replace Tavern's internal
 * barrel-reset implementation.
 *
 * It only asks the real BarrelBlockEntity to perform the
 * same cleanup that normal tap extraction performs when
 * the final serving is removed.
 */
@Mixin(
        value = BarrelBlockEntity.class,
        remap = false
)
public interface BarrelBlockEntityInvoker {

    @Invoker("resetIfOutputEmpty")
    void feastwineallgone$invokeResetIfOutputEmpty();
}
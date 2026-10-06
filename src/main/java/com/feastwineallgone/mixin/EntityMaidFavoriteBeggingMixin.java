package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.FavoriteBeggingController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps WGON's favorite-food begging reaction independent from TLM's native
 * player-held temptation item task. Native code may still set begging true,
 * but it cannot clear WGON's forced reaction until WGON finishes it.
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidFavoriteBeggingMixin {

    @Inject(
            method = "setBegging",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$keepFavoriteBegging(
            boolean begging,
            CallbackInfo ci
    ) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (!begging && FavoriteBeggingController.isForced(maid)) {
            ci.cancel();
        }
    }
}

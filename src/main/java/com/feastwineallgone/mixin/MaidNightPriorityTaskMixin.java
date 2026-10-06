package com.feastwineallgone.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBedTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindHomeMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidHomeMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidStealEdibleMoveBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidStealEdibleUseTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidWorkMealTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.DrunkSleepData;
import com.feastwineallgone.behavior.MaidActionLock;
import com.feastwineallgone.behavior.NightStealManager;
import com.feastwineallgone.food.FoodRemnantGuard;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Night-barrel theft owns the maid exclusively once its action lock has
 * been acquired. TLM's normal bed and meal behaviours must not restart in
 * the middle of the theft and pull the maid away from the barrel/tap.
 */
@Mixin(
        value = {
                MaidBedTask.class,
                MaidFindHomeMealTask.class,
                MaidHomeMealTask.class,
                MaidWorkMealTask.class,
                MaidStealEdibleMoveBlockTask.class,
                MaidStealEdibleUseTask.class
        },
        remap = false
)
public abstract class MaidNightPriorityTaskMixin {

    @Inject(
            method = "checkExtraStartConditions",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$blockCompetingTaskCheck(
            ServerLevel level,
            EntityMaid maid,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (DrunkSleepData.isActive(maid)) {
            cir.setReturnValue(false);
            return;
        }

        if (MaidActionLock.isLockedBy(
                maid,
                NightStealManager.ACTION_ID
        )) {
            cir.setReturnValue(false);
            return;
        }

        /*
         * Do not let TLM's native steal-edible tasks "finish" the plate
         * WGON intentionally left behind. Cookery's native edible adapter
         * destroys a max-bites FoodBiteBlock if consume() is called again.
         */
        if (feastwineallgone$isNativeStealEdibleTask()
                && feastwineallgone$clearProtectedRemnantTarget(level, maid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "start",
            at = @At("HEAD"),
            cancellable = true
    )
    private void feastwineallgone$blockCompetingTaskStart(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            CallbackInfo ci
    ) {
        if (DrunkSleepData.isActive(maid)) {
            ci.cancel();
            return;
        }

        if (MaidActionLock.isLockedBy(
                maid,
                NightStealManager.ACTION_ID
        )) {
            ci.cancel();
            return;
        }

        /*
         * Re-check immediately before start(). The native use task performs
         * the actual consume operation from start(), so this second gate
         * closes the small race where the target becomes an empty plate or
         * max-bites remnant after checkExtraStartConditions() already passed.
         */
        if (feastwineallgone$isNativeStealEdibleTask()
                && feastwineallgone$clearProtectedRemnantTarget(level, maid)) {
            ci.cancel();
        }
    }

    private boolean feastwineallgone$isNativeStealEdibleTask() {
        return (Object) this instanceof MaidStealEdibleMoveBlockTask
                || (Object) this instanceof MaidStealEdibleUseTask;
    }

    private boolean feastwineallgone$clearProtectedRemnantTarget(
            ServerLevel level,
            EntityMaid maid
    ) {
        Optional<PositionTracker> target =
                maid.getBrain()
                        .getMemory(InitEntities.TARGET_POS.get());

        if (target.isEmpty()) {
            return false;
        }

        BlockPos targetPos = target.get().currentBlockPosition();
        if (!FoodRemnantGuard.isProtectedRemnant(level, targetPos)) {
            return false;
        }

        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return true;
    }
}

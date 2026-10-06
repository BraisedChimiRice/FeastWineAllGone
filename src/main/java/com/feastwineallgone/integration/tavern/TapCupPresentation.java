package com.feastwineallgone.integration.tavern;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.TapBlock;
import com.feastwineallgone.mixin.ItemDisplayInvoker;
import com.feastwineallgone.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Visual-only wooden mug placed under a Kaleidoscope Tavern faucet while a
 * maid performs the midnight fake-extraction sequence.
 *
 * The display entity never owns the real drink. TavernBarrelAccess remains
 * the single authority that removes/consumes a serving after the maid has
 * visibly drunk it. This keeps the animation compatible with Tavern's barrel
 * inventory rules while making the presentation match Tavern's normal
 * bottle-under-the-tap workflow.
 */
public final class TapCupPresentation {

    /*
     * These offsets intentionally live in one tiny compatibility class so the
     * mug can be visually tuned after an in-game test without touching the
     * night-steal state machine.
     */
    private static final double FRONT_OFFSET = 0.08D;
    private static final double FIXED_DISPLAY_SCALE = 0.78D;
    private static final double MODEL_CENTER_Y = 0.5D;
    private static final double GROUND_EPSILON = 0.002D;

    private TapCupPresentation() {
    }

    public static Display.ItemDisplay spawnEmptyCup(
            ServerLevel level,
            BlockPos tapPos
    ) {
        BlockState state = level.getBlockState(tapPos);
        if (!(state.getBlock() instanceof TapBlock)) {
            return null;
        }

        Display.ItemDisplay display =
                EntityType.ITEM_DISPLAY.create(level);

        if (display == null) {
            return null;
        }

        Direction facing = state.getValue(TapBlock.FACING);

        double x = tapPos.getX() + 0.5D
                + facing.getStepX() * FRONT_OFFSET;
        /*
         * ItemDisplay renders an item model around the vanilla model centre
         * (8/16), not from the model's bottom edge.  Our mug geometry begins
         * at model Y=0 and the FIXED display transform scales it to 0.78.
         * Therefore placing the ItemDisplay origin at local Y=0 buries the
         * mug by exactly 0.5 * 0.78 = 0.39 blocks.  The previous +1.0 put it
         * one block too high; +0.0 put it inside the floor.
         *
         * Lift the display origin by the scaled half-block model-centre
         * offset so the mug's actual bottom lands on the carrier block's
         * floor plane.  The tiny epsilon only prevents z-fighting.
         */
        double modelOriginLift = MODEL_CENTER_Y * FIXED_DISPLAY_SCALE;
        double y = tapPos.below().getY()
                + modelOriginLift
                + GROUND_EPSILON;
        double z = tapPos.getZ() + 0.5D
                + facing.getStepZ() * FRONT_OFFSET;

        display.setPos(x, y, z);
        display.setYRot(facing.toYRot());
        display.setXRot(0.0F);
        ItemDisplayInvoker invoker =
                (ItemDisplayInvoker) (Object) display;

        invoker.feastwineallgone$setItemTransform(
                ItemDisplayContext.FIXED
        );
        invoker.feastwineallgone$setItemStack(
                new ItemStack(ModItems.WOODEN_CUP.get())
        );

        if (!level.addFreshEntity(display)) {
            return null;
        }

        return display;
    }

    public static void showWine(
            Display.ItemDisplay display
    ) {
        if (display == null || display.isRemoved()) {
            return;
        }

        ((ItemDisplayInvoker) (Object) display)
                .feastwineallgone$setItemStack(
                        new ItemStack(
                                ModItems.WOODEN_CUP_WINE.get()
                        )
                );
    }

    public static void remove(
            Display.ItemDisplay display
    ) {
        if (display == null || display.isRemoved()) {
            return;
        }

        display.discard();
    }
}

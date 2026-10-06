package com.feastwineallgone.integration.tavern;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IBarrel;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.TapBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity;
import com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.TapBlockEntity;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModParticles;
import com.github.ysbbbbbb.kaleidoscopetavern.item.BottleBlockItem;
import com.feastwineallgone.mixin.BarrelBlockEntityInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TavernBarrelAccess {

    private TavernBarrelAccess() {
    }

    /**
     * One complete Tavern stealing station.
     *
     * barrelPos is the canonical origin of the whole multi-block barrel.
     * tapPos is a real TapBlock whose rear connection resolves to that exact
     * BarrelBlockEntity. A loose faucet by itself is therefore never a WGON
     * stealing target.
     */
    public record BarrelStation(
            BlockPos barrelPos,
            BlockPos tapPos
    ) {
        public BarrelStation {
            barrelPos = barrelPos.immutable();
            tapPos = tapPos.immutable();
        }
    }

    /**
     * Find complete barrel + faucet stations around the maid.
     *
     * The Tavern barrel is a multi-block structure. Older WGON code scanned
     * individual block entities/parts, which could make one physical barrel
     * appear several times. Here every matching barrel block is collapsed to
     * BarrelBlock.getOriginPos(), then paired with a genuinely connected tap.
     */
    public static List<BarrelStation> findValidBarrelStations(
            ServerLevel level,
            EntityMaid maid,
            int radius
    ) {
        BlockPos searchOrigin = maid.getBrainSearchPos();
        List<BarrelStation> result = new ArrayList<>();
        Set<BlockPos> visitedOrigins = new HashSet<>();

        int radiusSquared = radius * radius;
        BlockPos min = searchOrigin.offset(-radius, -radius, -radius);
        BlockPos max = searchOrigin.offset(radius, radius, radius);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.distSqr(searchOrigin) > radiusSquared) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BarrelBlock)) {
                continue;
            }

            BlockPos origin = BarrelBlock.getOriginPos(pos, state).immutable();
            if (!visitedOrigins.add(origin)) {
                continue;
            }

            BarrelBlockEntity barrelEntity = resolveBarrelEntity(level, origin);
            if (barrelEntity == null) {
                continue;
            }

            repairStaleEmptyBarrel(barrelEntity, barrelEntity);
            if (!canSteal(barrelEntity)) {
                continue;
            }

            BlockPos tap = findConnectedTap(level, origin);
            if (tap == null) {
                continue;
            }

            result.add(new BarrelStation(origin, tap));
        }

        return result;
    }

    /**
     * Compatibility wrapper used by older WGON call sites/debug helpers.
     * New stealing logic should prefer findValidBarrelStations().
     */
    public static List<BlockPos> findValidBarrels(
            ServerLevel level,
            EntityMaid maid,
            int radius
    ) {
        List<BlockPos> result = new ArrayList<>();
        for (BarrelStation station : findValidBarrelStations(level, maid, radius)) {
            result.add(station.barrelPos());
        }
        return result;
    }

    /**
     * Find a TapBlock that is physically connected to the same complete
     * BarrelBlockEntity as barrelPos.
     */
    public static BlockPos findConnectedTap(
            ServerLevel level,
            BlockPos barrelPos
    ) {
        BlockPos origin = canonicalBarrelOrigin(level, barrelPos);
        BarrelBlockEntity targetBarrel = resolveBarrelEntity(level, origin);

        if (targetBarrel == null) {
            return null;
        }

        /*
         * Search around the complete barrel, not merely the one block that
         * happened to be discovered. This also makes large Tavern barrels
         * behave as one logical station.
         */
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos tapPos = origin.offset(dx, dy, dz);
                    BlockState tapState = level.getBlockState(tapPos);

                    if (!(tapState.getBlock() instanceof TapBlock)) {
                        continue;
                    }

                    Direction facing = tapState.getValue(TapBlock.FACING);
                    BlockPos sourcePos = tapPos.relative(facing.getOpposite());
                    BarrelBlockEntity connected = resolveBarrelEntity(level, sourcePos);

                    if (connected == targetBarrel) {
                        return tapPos.immutable();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Revalidate a previously selected complete station.
     */
    public static boolean isValidStation(
            ServerLevel level,
            BarrelStation station
    ) {
        if (station == null) {
            return false;
        }

        BarrelBlockEntity barrel = resolveBarrelEntity(level, station.barrelPos());
        if (barrel == null) {
            return false;
        }

        repairStaleEmptyBarrel(barrel, barrel);
        if (!canSteal(barrel)) {
            return false;
        }

        BlockState tapState = level.getBlockState(station.tapPos());
        if (!(tapState.getBlock() instanceof TapBlock)) {
            return false;
        }

        Direction facing = tapState.getValue(TapBlock.FACING);
        BlockPos sourcePos = station.tapPos().relative(facing.getOpposite());
        BarrelBlockEntity connected = resolveBarrelEntity(level, sourcePos);

        return connected == barrel;
    }

    /**
     * Start the visual half of Kaleidoscope Tavern's normal tap extraction.
     *
     * Night stealing does not call TapBlock#use directly because that API is
     * player-oriented and Tavern normally expects a carrier block/item below
     * the faucet. WGON's maid instead holds its own visual wooden cup. This
     * method mirrors Tavern's presentation only: open the faucet, play its
     * native opening sound and ask the real TapBlockEntity to emit the same
     * drip particles. The barrel contents are NOT changed here.
     *
     * @return true when WGON itself changed the faucet from closed to open.
     *         The caller must pass this back to finishTapPourPresentation so
     *         an already-open/redstone-controlled faucet is not closed by us.
     */
    public static boolean beginTapPourPresentation(
            ServerLevel level,
            BlockPos tapPos
    ) {
        BlockState tapState = level.getBlockState(tapPos);
        if (!(tapState.getBlock() instanceof TapBlock)) {
            return false;
        }

        boolean openedByWgon =
                !tapState.getValue(TapBlock.OPEN);

        if (openedByWgon) {
            BlockState openState =
                    tapState.setValue(TapBlock.OPEN, true);

            level.setBlock(
                    tapPos,
                    openState,
                    Block.UPDATE_ALL
            );

            level.playSound(
                    null,
                    tapPos,
                    SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F
            );

            tapState = openState;
        }

        BlockEntity blockEntity =
                level.getBlockEntity(tapPos);

        if (blockEntity instanceof TapBlockEntity tapEntity) {
            ParticleOptions particle =
                    ModParticles.WATER_TAP_DRIP.get();

            Direction facing =
                    tapState.getValue(TapBlock.FACING);

            BarrelBlockEntity barrel =
                    resolveBarrelEntity(
                            level,
                            tapPos.relative(facing.getOpposite())
                    );

            if (barrel != null
                    && barrel.getOutput()
                    .getStackInSlot(0)
                    .is(ModItems.MOLOTOV.get())) {
                particle =
                        ModParticles.LAVA_TAP_DRIP.get();
            }

            tapEntity.setParticle(particle);
            tapEntity.setState(
                    TapBlockEntity.TAKE_DRINK_STATE
            );
        }

        return openedByWgon;
    }

    /**
     * End WGON's faucet-pouring presentation.
     *
     * completed=true plays the exact sound event Tavern itself uses when a
     * barrel tap finishes extracting a serving. This is presentation only;
     * WGON still removes/consumes the serving later, after the maid visibly
     * drinks from the wooden cup.
     */
    public static void finishTapPourPresentation(
            ServerLevel level,
            BlockPos tapPos,
            boolean openedByWgon,
            boolean completed
    ) {
        BlockEntity blockEntity =
                level.getBlockEntity(tapPos);

        if (blockEntity instanceof TapBlockEntity tapEntity) {
            tapEntity.setParticle(null);
            tapEntity.setState(
                    TapBlockEntity.DEFAULT_STATE
            );
        }

        BlockState tapState = level.getBlockState(tapPos);
        if (tapState.getBlock() instanceof TapBlock
                && openedByWgon
                && tapState.getValue(TapBlock.OPEN)
                && !tapState.getValue(TapBlock.TRIGGERED)) {
            level.setBlock(
                    tapPos,
                    tapState.setValue(TapBlock.OPEN, false),
                    Block.UPDATE_ALL
            );

            level.playSound(
                    null,
                    tapPos,
                    SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F
            );
        }

        if (completed) {
            level.playSound(
                    null,
                    tapPos.below(),
                    SoundEvents.BREWING_STAND_BREW,
                    SoundSource.BLOCKS,
                    1.0F,
                    1.0F
            );
        }
    }

    /**
     * Collapse any barrel body block to Tavern's canonical barrel origin.
     */
    public static BlockPos canonicalBarrelOrigin(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            return BarrelBlock.getOriginPos(pos, state).immutable();
        }

        BarrelBlockEntity entity = resolveBarrelEntity(level, pos);
        if (entity != null) {
            return entity.getBlockPos().immutable();
        }

        return pos.immutable();
    }

    private static BarrelBlockEntity resolveBarrelEntity(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof BarrelBlock) {
            BarrelBlockEntity resolved = BarrelBlock.getBarrelEntity(level, pos, state);
            if (resolved != null) {
                return resolved;
            }
        }

        BlockEntity direct = level.getBlockEntity(pos);
        if (direct instanceof BarrelBlockEntity barrel) {
            return barrel;
        }

        return null;
    }

    /**
     * A barrel can be stolen from when:
     *
     * - Kaleidoscope Tavern considers brewing active
     * - at least one output serving exists
     *
     * Brew quality itself does not control eligibility.
     *
     * Even poor / unfinished-quality wine may be tasted by
     * WGON later when the quality-based behaviour is added.
     */
    public static boolean canSteal(
            IBarrel barrel
    ) {
        return barrel.isBrewing()
                && !barrel
                .getOutput()
                .getStackInSlot(0)
                .isEmpty();
    }

    /**
     * Get the current Kaleidoscope Tavern brew level.
     */
    public static int getBrewLevel(
            ServerLevel level,
            BlockPos pos
    ) {
        BarrelBlockEntity barrel =
                resolveBarrelEntity(
                        level,
                        pos
                );

        return barrel != null
                ? barrel.getBrewLevel()
                : 0;
    }

    /**
     * Remove and consume exactly one serving.
     *
     * Includes normal WGON drinking sound.
     */
    public static boolean stealOneServing(
            ServerLevel level,
            BlockPos pos,
            EntityMaid maid
    ) {
        return stealOneServingInternal(
                level,
                pos,
                maid,
                true
        );
    }

    /**
     * Remove and consume exactly one serving without
     * deliberately playing another WGON drinking sound.
     *
     * Used when observation mode is skipped and the remaining
     * theft must be resolved immediately.
     */
    public static boolean stealOneServingSilently(
            ServerLevel level,
            BlockPos pos,
            EntityMaid maid
    ) {
        return stealOneServingInternal(
                level,
                pos,
                maid,
                false
        );
    }

    private static boolean stealOneServingInternal(
            ServerLevel level,
            BlockPos pos,
            EntityMaid maid,
            boolean playSound
    ) {
        BarrelBlockEntity blockEntity =
                resolveBarrelEntity(
                        level,
                        pos
                );

        if (blockEntity == null) {
            return false;
        }

        IBarrel barrel = blockEntity;

        /*
         * If this barrel was broken by an older WGON build,
         * repair it before doing anything else.
         */
        repairStaleEmptyBarrel(
                blockEntity,
                barrel
        );

        if (!canSteal(
                barrel
        )) {

            return false;
        }

        /*
         * IMPORTANT:
         *
         * Read quality BEFORE extracting the serving.
         *
         * If this happens to be the final serving,
         * resetIfOutputEmpty() may reset the barrel's
         * brew level immediately afterwards.
         */
        int brewLevel =
                barrel.getBrewLevel();

        ItemStack extracted =
                barrel.getOutput()
                        .extractItem(
                                0,
                                1,
                                false
                        );

        if (extracted.isEmpty()) {

            return false;
        }

        /*
         * Kaleidoscope Tavern stores a BottleBlockItem in the
         * barrel output and uses the barrel's current brew level
         * to create the actual quality-specific drink stack.
         */
        ItemStack drink;

        if (extracted.getItem()
                instanceof BottleBlockItem bottleItem) {

            drink =
                    bottleItem.getFilledStack(
                            brewLevel
                    );

            drink.setCount(
                    1
            );

        } else {

            /*
             * Defensive fallback in case another Tavern barrel
             * implementation directly stores a finished drink.
             */
            drink =
                    extracted.copy();

            drink.setCount(
                    1
            );
        }

        /*
         * This is essential.
         *
         * Normal Kaleidoscope Tavern extraction runs its own
         * resetIfOutputEmpty() logic after removing a serving.
         *
         * If the maid stole the final serving, this restores
         * the barrel to a genuine empty state rather than
         * leaving it "brewing air".
         */
        invokeOriginalBarrelCleanup(
                blockEntity
        );

        blockEntity.setChanged();

        BlockPos updatePos =
                blockEntity.getBlockPos();

        var state =
                level.getBlockState(
                        updatePos
                );

        level.sendBlockUpdated(
                updatePos,
                state,
                state,
                Block.UPDATE_CLIENTS
        );

        /*
         * Do NOT call:
         *
         * drink.finishUsingItem(level, maid)
         *
         * Kaleidoscope Tavern treats non-player entities
         * differently and may create an empty-container entity.
         * Older WGON code then also created another empty bottle,
         * resulting in duplicate containers.
         *
         * TavernDrinkAccess now owns the complete detached-drink
         * lifecycle:
         *
         * - native Tavern drink effect
         * - exactly one empty bottle / glass
         * - backpack insertion
         * - optional sound
         */
        return TavernDrinkAccess
                .consumeDetachedDrink(
                        level,
                        maid,
                        drink,
                        playSound
                );
    }

    /**
     * Repair the stale barrel state produced by older
     * Feast Wine All Gone versions:
     *
     * brewing = true
     * output = empty
     *
     * Normal Tavern extraction should reset such a barrel
     * immediately.
     */
    private static void repairStaleEmptyBarrel(
            BlockEntity blockEntity,
            IBarrel barrel
    ) {
        if (!barrel.isBrewing()) {

            return;
        }

        if (!barrel
                .getOutput()
                .getStackInSlot(0)
                .isEmpty()) {

            return;
        }

        invokeOriginalBarrelCleanup(
                blockEntity
        );
    }

    /**
     * Call Kaleidoscope Tavern's own resetIfOutputEmpty()
     * implementation through WGON's Mixin Invoker.
     *
     * WGON does not duplicate Tavern's internal reset logic
     * and does not modify the Tavern jar.
     */
    private static void invokeOriginalBarrelCleanup(
            BlockEntity blockEntity
    ) {
        if (!(blockEntity
                instanceof BarrelBlockEntity barrelBlockEntity)) {

            return;
        }

        ((BarrelBlockEntityInvoker)
                (Object) barrelBlockEntity)
                .feastwineallgone$invokeResetIfOutputEmpty();
    }
}
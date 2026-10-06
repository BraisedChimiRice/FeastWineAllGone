package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPlaySoundEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.network.WgonNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the persistent "drank too much and slept somewhere strange" state.
 *
 * This intentionally does not freeze time. Once the night-steal camera ends,
 * morning may arrive normally while the maid remains in TLM's sleeping pose.
 */
public final class DrunkSleepManager {

    private static final ResourceLocation TRUMPET =
            new ResourceLocation(
                    "touhou_little_maid",
                    "trumpet"
            );

    private static final ResourceLocation SERVANT_BELL =
            new ResourceLocation(
                    "touhou_little_maid",
                    "servant_bell"
            );

    private DrunkSleepManager() {
    }

    /**
     * Roll the post-drinking drunk-sleep chance once.
     */
    public static boolean shouldDrunkSleep(
            ServerLevel level
    ) {
        int chance = Mth.clamp(
                WgonConfig.DRUNK_SLEEP_CHANCE.get(),
                0,
                100
        );

        return chance > 0
                && level.random.nextInt(100) < chance;
    }

    /**
     * Called by the finished NightSteal session after gifts have been handled.
     *
     * Candidate pool:
     * 1) a safe floor tile near the barrel used tonight;
     * 2) up to three player-recorded locations for this maid.
     *
     * Only currently-loaded, same-dimension and still-safe locations enter the
     * random pool. If none survive, the caller falls back to the normal bed.
     */
    public static boolean tryStartAfterNightSteal(
            ServerLevel level,
            EntityMaid maid,
            BlockPos barrelPos,
            BlockPos interactionPos,
            BlockPos barrelStandPos
    ) {
        List<SleepCandidate> candidates = new ArrayList<>();

        /*
         * The Tavern barrel is a large multi-block model. Picking an arbitrary
         * "safe air block near the barrel origin" is not enough for a lying
         * maid: her rendered sleep model can extend well outside the normal
         * standing collision box and visually clip into the barrel body.
         *
         * NightSteal already found a legitimate standing point in front of the
         * connected tap. Use that tap -> stand direction as the barrel's
         * practical "front", then move the drunk-sleep point roughly two
         * horizontal blocks out from the tap. This keeps the whole sleeping
         * model clear of the barrel instead of merely keeping the entity's feet
         * out of a solid block.
         */
        BlockPos barrelSleep = findSafeBarrelFrontSleepSpot(
                level,
                barrelPos,
                interactionPos,
                barrelStandPos
        );

        if (barrelSleep != null) {
            candidates.add(
                    new SleepCandidate(
                            barrelSleep,
                            yawFacing(
                                    barrelSleep,
                                    interactionPos
                            )
                    )
            );
        }

        ResourceLocation currentDimension =
                level.dimension().location();

        for (DrunkSleepData.RecordedLocation recorded
                : DrunkSleepData.getRecordedLocations(maid)) {

            if (!currentDimension.equals(recorded.dimension())) {
                continue;
            }

            if (!level.hasChunkAt(recorded.pos())) {
                continue;
            }

            BlockPos safe = findSafeSleepSpotNear(
                    level,
                    recorded.pos(),
                    0,
                    1
            );

            if (safe == null) {
                continue;
            }

            boolean duplicate = false;
            for (SleepCandidate candidate : candidates) {
                if (candidate.pos().equals(safe)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                candidates.add(
                        new SleepCandidate(
                                safe,
                                recorded.yaw()
                        )
                );
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        SleepCandidate selected =
                candidates.get(
                        level.random.nextInt(
                                candidates.size()
                        )
                );

        startDrunkSleep(
                level,
                maid,
                selected.pos(),
                selected.yaw()
        );

        return true;
    }

    /**
     * Validate a player-recorded foot position. We accept the exact point when
     * possible and then a one-block ring as a gentle correction for carpets,
     * decorations or an imprecise click.
     */
    public static BlockPos resolveRecordedSleepSpot(
            ServerLevel level,
            BlockPos preferred
    ) {
        return findSafeSleepSpotNear(
                level,
                preferred,
                0,
                1
        );
    }

    public static void startDrunkSleep(
            ServerLevel level,
            EntityMaid maid,
            BlockPos sleepPos,
            float yaw
    ) {
        stopMaidMovement(maid);

        DrunkSleepData.setActiveSleep(
                maid,
                level.dimension().location(),
                sleepPos,
                yaw,
                level.getGameTime()
        );

        /*
         * Mirror drunk-sleep before switching the pose. The client animation
         * query layer then knows immediately that Pose.SLEEPING is an FWAG
         * floor nap and should expose query.is_sleeping=true even though there
         * is intentionally no real SleepingPos.
         */
        WgonNetwork.broadcastDrunkSleepState(
                maid,
                true,
                sleepPos,
                yaw
        );

        placeAndSleep(
                maid,
                sleepPos,
                yaw,
                true
        );
    }

    public static final int OWNER_FACE_POKE_FAVORABILITY = 20;
    private static final double OWNER_FACE_POKE_DISTANCE_SQR = 64.0D;

    /**
     * Owner-only manual wake path used by the close-up face-poke interaction.
     *
     * DrunkSleepData is cleared by wakeWithoutReward(), so duplicate packets
     * cannot award the +20 twice.
     */
    public static boolean wakeByOwnerFacePoke(
            ServerPlayer player,
            EntityMaid maid
    ) {
        if (!DrunkSleepData.isActive(maid)) {
            return false;
        }

        if (maid.getOwnerUUID() == null
                || !maid.getOwnerUUID().equals(player.getUUID())) {
            return false;
        }

        if (player.level() != maid.level()
                || player.distanceToSqr(maid)
                > OWNER_FACE_POKE_DISTANCE_SQR) {
            return false;
        }

        /*
         * Wake first. If something invalidated the drunk state between packet
         * validation and here, no reward is issued.
         */
        if (!wakeWithoutReward(maid)) {
            return false;
        }

        maid.getFavorabilityManager().add(
                OWNER_FACE_POKE_FAVORABILITY
        );
        maid.spawnHeartParticle();

        player.displayClientMessage(
                Component.translatable(
                        "message.feastwineallgone.drunk_wake.face_poke",
                        maid.getDisplayName(),
                        OWNER_FACE_POKE_FAVORABILITY
                ),
                true
        );

        return true;
    }

    /**
     * Wake without favorability. Used for damage and TLM recall items.
     * The later face-poke interaction will use the same primitive but add its
     * own +20 reward after validating the owner.
     */
    public static boolean wakeWithoutReward(
            EntityMaid maid
    ) {
        if (!DrunkSleepData.isActive(maid)) {
            return false;
        }

        DrunkSleepData.ActiveSleep active =
                DrunkSleepData.getActiveSleep(maid)
                        .orElse(null);

        if (active == null) {
            return false;
        }

        DrunkSleepData.clearActiveSleep(maid);

        if (maid.isSleeping()) {
            maid.stopSleeping();
        }

        /*
         * The drunk-sleep model may have been held at a floor-only render
         * baseline. Snap the authoritative entity to the exact recorded floor
         * point before gravity/standing resumes, so the wake transition cannot
         * spend a few seconds visually hovering and then "fall" into place.
         */
        maid.moveTo(
                active.pos().getX() + 0.5D,
                active.pos().getY(),
                active.pos().getZ() + 0.5D,
                active.yaw(),
                0.0F
        );

        maid.setPose(Pose.STANDING);
        maid.setNoGravity(false);
        maid.setYRot(active.yaw());
        maid.setYHeadRot(active.yaw());
        maid.getNavigation().stop();
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;

        /*
         * Wake locally first, then clear the client mirror. This prevents an
         * in-flight client tick from briefly treating a still-sleeping maid as
         * ordinary bed sleep.
         */
        WgonNetwork.broadcastDrunkSleepState(
                maid,
                false,
                active.pos(),
                active.yaw()
        );

        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)) {
            return;
        }

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)
                    || !DrunkSleepData.isActive(maid)) {
                continue;
            }

            maintainDrunkSleep(
                    level,
                    maid
            );
        }
    }


    /**
     * TLM exposes exactly the same cancellable event used by its Mute Bauble.
     * While a maid is passed out, suppress ambient chatter and other routine
     * maid voice playback. If she is actually hurt, onLivingHurt clears drunk
     * sleep first, so the normal wake/hurt sound is allowed afterwards.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMaidPlaySound(
            MaidPlaySoundEvent event
    ) {
        if (DrunkSleepData.isActive(event.getMaid())) {
            event.setCanceled(true);
        }
    }

    /**
     * A hurt maid wakes immediately. Sleeping through a zombie attack would
     * turn a cute hangover mechanic into an accidental death trap.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(
            LivingHurtEvent event
    ) {
        if (event.getEntity() instanceof EntityMaid maid) {
            wakeWithoutReward(maid);
        }
    }

    /**
     * TLM's trumpet / servant bell are explicit owner recall actions. Wake
     * loaded drunk maids before TLM performs its normal recall/teleport logic.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (isRecallItem(event.getItemStack())) {
            wakeOwnedLoadedMaids(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (isRecallItem(event.getItemStack())) {
            wakeOwnedLoadedMaids(player);
        }
    }

    /**
     * A player may begin tracking a maid long after the original drunk-sleep
     * start packet was sent (walk into range, change dimension, reconnect).
     * Push an exact snapshot at that moment.
     */
    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }

        WgonNetwork.sendDrunkSleepState(
                player,
                maid
        );
    }

    /**
     * Clear the mirror when this client stops tracking the maid. StartTracking
     * will re-send the authoritative state if she comes back into range.
     */
    @SubscribeEvent
    public static void onStopTracking(
            PlayerEvent.StopTracking event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }

        WgonNetwork.sendDrunkSleepClear(
                player,
                maid
        );
    }

    private static void maintainDrunkSleep(
            ServerLevel level,
            EntityMaid maid
    ) {
        DrunkSleepData.ActiveSleep active =
                DrunkSleepData.getActiveSleep(maid)
                        .orElse(null);

        if (active == null) {
            return;
        }

        if (!level.dimension().location().equals(active.dimension())) {
            wakeWithoutReward(maid);
            return;
        }

        int autoWakeSeconds =
                Math.max(
                        0,
                        WgonConfig.DRUNK_SLEEP_AUTO_WAKE_SECONDS.get()
                );

        if (autoWakeSeconds > 0
                && level.getGameTime() - active.startGameTime()
                >= autoWakeSeconds * 20L
                && tryAutoWakeAndReturnToOwner(level, maid)) {
            return;
        }

        /*
         * Re-check every tick. If a player builds a wall, fire or lava onto a
         * previously safe nap spot, wake the maid rather than forcing her to
         * suffocate inside the changed environment.
         */
        if (!isSafeSleepSpot(level, active.pos())) {
            wakeWithoutReward(maid);
            return;
        }

        stopMaidMovement(maid);

        /*
         * IMPORTANT:
         * Do NOT create a real Minecraft/TLM SleepingPos for floor naps.
         *
         * A real startSleeping(pos) permanently pulls us back into Minecraft
         * and TLM's BED semantics. That is the source of the stubborn
         * half-block hover and, when counter-corrected, the up/down jitter.
         *
         * FWAG now uses a pose-only floor sleep:
         *   - Pose.SLEEPING selects TLM's normal "sleep" animation;
         *   - ClientDrunkSleepState tells the Gecko/YSM Molang layer that
         *     query.is_sleeping should also be true;
         *   - no SleepingPos exists, so no bed-height relocation can occur.
         */

        placeAndSleep(
                maid,
                active.pos(),
                active.yaw(),
                false
        );
    }

    private static void placeAndSleep(
            EntityMaid maid,
            BlockPos pos,
            float yaw,
            boolean forceInitialPlacement
    ) {
        /*
         * Floor drunk-sleep is intentionally NOT a real SleepingPos sleep.
         *
         * TLM's Gecko animation registry selects "sleep" from Pose.SLEEPING,
         * while the custom QueryBinding mixin supplies query.is_sleeping=true
         * to drunk-sleep models on the client. This gives us the full visual
         * sleep state without ever entering bed positioning code.
         */
        if (maid.isSleeping()) {
            maid.stopSleeping();
        }

        double targetX = pos.getX() + 0.5D;
        double targetY = pos.getY();
        double targetZ = pos.getZ() + 0.5D;

        double dx = maid.getX() - targetX;
        double dy = maid.getY() - targetY;
        double dz = maid.getZ() - targetZ;

        boolean displaced =
                dx * dx + dy * dy + dz * dz > 0.01D;

        if (forceInitialPlacement || displaced) {
            maid.moveTo(
                    targetX,
                    targetY,
                    targetZ,
                    yaw,
                    0.0F
            );
        }

        maid.setPose(Pose.SLEEPING);
        maid.setNoGravity(true);
        maid.setYRot(yaw);
        maid.setYHeadRot(yaw);
        maid.setXRot(0.0F);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
    }

    private static void stopMaidMovement(
            EntityMaid maid
    ) {
        maid.getNavigation().stop();
        maid.setSprinting(false);
        maid.setTarget(null);
        maid.setAggressive(false);
        maid.setDeltaMovement(Vec3.ZERO);

        maid.getBrain().eraseMemory(
                MemoryModuleType.WALK_TARGET
        );
        maid.getBrain().eraseMemory(
                MemoryModuleType.LOOK_TARGET
        );
        maid.getBrain().eraseMemory(
                MemoryModuleType.ATTACK_TARGET
        );
    }

    /**
     * Natural timeout wake-up.
     *
     * Only fires while the owner is online in the same dimension. If the
     * owner is offline or currently elsewhere, the maid remains safely asleep
     * and the already-expired timer is checked again on later ticks.
     *
     * No face-poke favorability is awarded here.
     */
    private static boolean tryAutoWakeAndReturnToOwner(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (maid.getOwnerUUID() == null) {
            return false;
        }

        ServerPlayer owner =
                level.getServer()
                        .getPlayerList()
                        .getPlayer(
                                maid.getOwnerUUID()
                        );

        if (owner == null
                || owner.serverLevel() != level) {
            return false;
        }

        if (!wakeWithoutReward(maid)) {
            return false;
        }

        /*
         * Prefer TLM's own safe teleport helper. It tries several nearby
         * positions and clears the maid's navigation memories.
         */
        boolean teleported =
                maid.teleportToOwner(owner);

        /*
         * Extremely crowded builds can make TLM's random attempts fail.
         * Reuse FWAG's conservative two-block safe-space scan as a fallback,
         * so "wake and return to master" remains deterministic.
         */
        if (!teleported
                && maid.distanceToSqr(owner) > 16.0D) {

            BlockPos safe =
                    findSafeSleepSpotNear(
                            level,
                            owner.blockPosition(),
                            1,
                            4
                    );

            if (safe != null) {
                maid.moveTo(
                        safe.getX() + 0.5D,
                        safe.getY(),
                        safe.getZ() + 0.5D,
                        owner.getYRot(),
                        0.0F
                );
                maid.getNavigation().stop();
                maid.setDeltaMovement(Vec3.ZERO);
                teleported = true;
            }
        }

        /*
         * If a close-up camera happened to be open exactly when the two-minute
         * timeout expired, close it cleanly.
         */
        WgonNetwork.sendEnd(owner);

        return true;
    }

    private static void wakeOwnedLoadedMaids(
            ServerPlayer player
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }

            if (maid.getOwnerUUID() != null
                    && maid.getOwnerUUID().equals(player.getUUID())) {
                wakeWithoutReward(maid);
            }
        }
    }

    private static boolean isRecallItem(
            ItemStack stack
    ) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation id =
                BuiltInRegistries.ITEM.getKey(
                        stack.getItem()
                );

        return TRUMPET.equals(id)
                || SERVANT_BELL.equals(id);
    }

    /**
     * Find a floor-nap point deliberately in front of the barrel/tap instead
     * of anywhere around the barrel origin.
     *
     * The order is intentional:
     *  - first try about two blocks in front of the connected faucet;
     *  - then the same distance one block to either side;
     *  - then about three blocks out as a roomy fallback.
     *
     * We use the already-proven NightSteal standing point to determine which
     * side is "front", so this also works when the barrel is rotated.
     */
    private static BlockPos findSafeBarrelFrontSleepSpot(
            ServerLevel level,
            BlockPos barrelPos,
            BlockPos interactionPos,
            BlockPos barrelStandPos
    ) {
        int dx = Integer.signum(
                barrelStandPos.getX()
                        - interactionPos.getX()
        );
        int dz = Integer.signum(
                barrelStandPos.getZ()
                        - interactionPos.getZ()
        );

        /*
         * Extremely defensive fallback. A valid night station should always
         * give us horizontal separation between tap and stand, but if another
         * mod changes the approach helper we can still derive an outward side
         * from the complete barrel origin.
         */
        if (dx == 0 && dz == 0) {
            dx = Integer.signum(
                    barrelStandPos.getX()
                            - barrelPos.getX()
            );
            dz = Integer.signum(
                    barrelStandPos.getZ()
                            - barrelPos.getZ()
            );
        }

        if (dx == 0 && dz == 0) {
            return findSafeSleepSpotNear(
                    level,
                    barrelStandPos,
                    0,
                    1
            );
        }

        /*
         * For diagonal approach vectors, keep the two components. This places
         * the maid diagonally out from a corner rather than snapping her back
         * toward one face of the barrel.
         */
        int sideX = -dz;
        int sideZ = dx;

        int[][] offsets = {
                {dx * 2, dz * 2},
                {dx * 2 + sideX, dz * 2 + sideZ},
                {dx * 2 - sideX, dz * 2 - sideZ},
                {dx * 3, dz * 3},
                {dx * 3 + sideX, dz * 3 + sideZ},
                {dx * 3 - sideX, dz * 3 - sideZ}
        };

        int baseY = barrelStandPos.getY();

        for (int[] offset : offsets) {
            int x = interactionPos.getX() + offset[0];
            int z = interactionPos.getZ() + offset[1];

            /*
             * Barrel/tap assemblies and surrounding floors can differ by one
             * block vertically, so gently probe around the known standing Y.
             */
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos candidate =
                        new BlockPos(
                                x,
                                baseY + dy,
                                z
                        );

                if (!level.hasChunkAt(candidate)) {
                    continue;
                }

                if (isSafeSleepSpot(level, candidate)) {
                    return candidate.immutable();
                }
            }

            BlockPos candidate =
                    new BlockPos(
                            x,
                            baseY + 1,
                            z
                    );

            if (level.hasChunkAt(candidate)
                    && isSafeSleepSpot(level, candidate)) {
                return candidate.immutable();
            }
        }

        /*
         * Do not fall back to a random ring around the barrel origin. That was
         * the source of the clipping bug. If no roomy front-side point exists,
         * simply omit the barrel candidate; recorded custom points may still
         * work, otherwise the normal bed route is safer.
         */
        return null;
    }

    private static BlockPos findSafeSleepSpotNear(
            ServerLevel level,
            BlockPos center,
            int minRadius,
            int maxRadius
    ) {
        int min = Math.max(0, minRadius);
        int max = Math.max(min, maxRadius);

        /* Prefer the exact recorded point first when radius 0 is allowed. */
        if (min == 0
                && level.hasChunkAt(center)
                && isSafeSleepSpot(level, center)) {
            return center.immutable();
        }

        for (int radius = Math.max(1, min);
             radius <= max;
             radius++) {

            List<BlockPos> valid = new ArrayList<>();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos candidate = center.offset(
                                dx,
                                dy,
                                dz
                        );

                        if (!level.hasChunkAt(candidate)) {
                            continue;
                        }

                        if (isSafeSleepSpot(level, candidate)) {
                            valid.add(candidate.immutable());
                        }
                    }
                }
            }

            if (!valid.isEmpty()) {
                return valid.get(
                        level.random.nextInt(
                                valid.size()
                        )
                );
            }
        }

        return null;
    }

    /**
     * Conservative two-block body-space validation for a maid sleeping on the
     * floor. FWAG keeps the authoritative entity at the integer floor surface;
     * client-side compatibility code suppresses TLM's bed-only +0.5625 offset.
     */
    public static boolean isSafeSleepSpot(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockPos groundPos = pos.below();

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(groundPos);

        if (!feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()) {
            return false;
        }

        if (!feet.getCollisionShape(level, pos).isEmpty()
                || !head.getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        if (!ground.isFaceSturdy(
                level,
                groundPos,
                Direction.UP
        )) {
            return false;
        }

        if (ground.is(Blocks.MAGMA_BLOCK)
                || ground.is(Blocks.CACTUS)
                || ground.is(Blocks.POWDER_SNOW)
                || ground.getBlock() instanceof CampfireBlock) {
            return false;
        }

        if (feet.is(Blocks.FIRE)
                || feet.is(Blocks.SOUL_FIRE)
                || feet.is(Blocks.POWDER_SNOW)
                || feet.is(Blocks.COBWEB)
                || head.is(Blocks.FIRE)
                || head.is(Blocks.SOUL_FIRE)
                || head.is(Blocks.POWDER_SNOW)
                || head.is(Blocks.COBWEB)) {
            return false;
        }

        return true;
    }

    private static float yawFacing(
            BlockPos from,
            BlockPos target
    ) {
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();

        if (dx * dx + dz * dz < 1.0E-6D) {
            return 0.0F;
        }

        return (float) (Mth.atan2(dz, dx)
                * (180.0D / Math.PI)) - 90.0F;
    }

    private record SleepCandidate(
            BlockPos pos,
            float yaw
    ) {
    }
}

package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.client.ClientWakeStretchState;
import com.feastwineallgone.network.WgonNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the short post-face-poke wake sequence.
 *
 * V6 changes the sequence from a timer-only lock into a small state machine:
 *
 *   WAKE / FALL
 *       -> wait at least DEFAULT_SETTLE_TICKS
 *       -> require several consecutive onGround ticks
 *       -> broadcast the 5.5 s stretch
 *       -> release after the stretch finishes
 *
 * Most importantly, this class no longer zeros the maid's Y velocity while she
 * is settling. V4/V5 did setDeltaMovement(Vec3.ZERO) every server tick, which
 * accidentally cancelled gravity for the entire wake lock. The maid therefore
 * stayed visually half-floating until the lock ended, exactly while the stretch
 * was playing.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class WakeStretchLockManager {

    public static final String ACTION_ID =
            "wake_stretch";

    /**
     * The old 1 second settle is kept as a MINIMUM, but no longer used as the
     * only start condition.
     */
    private static final int MIN_SETTLE_TICKS =
            ClientWakeStretchState.DEFAULT_SETTLE_TICKS;

    /**
     * Require a few real server ticks on the floor before starting the stretch.
     * This filters the one-tick onGround flicker that can happen while the
     * sleeping pose changes back to the standing hitbox.
     */
    private static final int REQUIRED_GROUNDED_TICKS = 4;

    /**
     * Safety valve. A maid should normally ground within a handful of ticks
     * because DrunkSleepManager wakes her at a validated floor position.
     * If another mod keeps her airborne, skip the cosmetic stretch instead of
     * holding her forever.
     */
    private static final int MAX_WAIT_FOR_GROUND_TICKS = 100;

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final Map<UUID, StretchLock> ACTIVE =
            new ConcurrentHashMap<>();

    private WakeStretchLockManager() {
    }

    public static void start(
            EntityMaid maid,
            int durationTicks
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }

        int safeDuration =
                Math.max(
                        1,
                        durationTicks
                );

        boolean lockAcquired =
                MaidActionLock.tryAcquire(
                        maid,
                        ACTION_ID
                );

        maid.getNavigation().stop();
        maid.setNoGravity(false);

        /*
         * Do NOT zero Y here.
         *
         * The maid may still be a fraction of a block above the final standing
         * floor after Pose.SLEEPING -> Pose.STANDING. Gravity needs to finish
         * that tiny fall before the authored stretch begins.
         */
        keepHorizontalLocked(maid);

        ACTIVE.put(
                maid.getUUID(),
                new StretchLock(
                        level.dimension(),
                        level.getGameTime(),
                        safeDuration
                )
        );

        LOGGER.info(
                "[WGON WakeStretch] server wake gate START entityId={} uuid={} minSettleTicks={} durationTicks={} actionLock={} onGround={} y={} dy={}",
                maid.getId(),
                maid.getUUID(),
                MIN_SETTLE_TICKS,
                safeDuration,
                lockAcquired,
                maid.onGround(),
                maid.getY(),
                maid.getDeltaMovement().y
        );
    }

    @SubscribeEvent
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (event.phase != TickEvent.Phase.END
                || !(event.level instanceof ServerLevel level)
                || ACTIVE.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, StretchLock>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, StretchLock> entry =
                    iterator.next();

            StretchLock lock =
                    entry.getValue();

            if (!lock.dimension.equals(level.dimension())) {
                continue;
            }

            EntityMaid maid =
                    level.getEntity(entry.getKey()) instanceof EntityMaid found
                            ? found
                            : null;

            if (maid == null
                    || maid.isRemoved()
                    || !maid.isAlive()) {
                if (maid != null) {
                    release(
                            maid,
                            iterator,
                            "maid_invalid"
                    );
                } else {
                    iterator.remove();
                }
                continue;
            }

            maid.getNavigation().stop();
            maid.setNoGravity(false);

            /*
             * Horizontal AI/movement stays locked, vertical physics does not.
             * This is the core V6 fix.
             */
            keepHorizontalLocked(maid);
            maid.fallDistance = 0.0F;

            long now =
                    level.getGameTime();

            if (!lock.stretchStarted) {
                long waited =
                        now - lock.startedGameTime;

                if (maid.onGround()) {
                    lock.groundedTicks++;
                } else {
                    lock.groundedTicks = 0;
                }

                boolean minimumDelayPassed =
                        waited >= MIN_SETTLE_TICKS;

                boolean actuallyGrounded =
                        lock.groundedTicks >= REQUIRED_GROUNDED_TICKS;

                if (minimumDelayPassed
                        && actuallyGrounded) {

                    lock.stretchStarted = true;
                    lock.stretchStartGameTime = now;

                    /*
                     * The server has already waited for the real landing, so
                     * the client receives settleTicks=0 and begins immediately.
                     */
                    WgonNetwork.broadcastWakeStretch(
                            maid,
                            0,
                            lock.durationTicks
                    );

                    LOGGER.info(
                            "[WGON WakeStretch] ground gate READY entityId={} waitedTicks={} groundedTicks={} y={} dy={} -> stretch",
                            maid.getId(),
                            waited,
                            lock.groundedTicks,
                            maid.getY(),
                            maid.getDeltaMovement().y
                    );

                    continue;
                }

                if (waited >= MAX_WAIT_FOR_GROUND_TICKS) {
                    LOGGER.warn(
                            "[WGON WakeStretch] ground gate TIMEOUT entityId={} waitedTicks={} onGround={} y={} dy={}; skip cosmetic stretch",
                            maid.getId(),
                            waited,
                            maid.onGround(),
                            maid.getY(),
                            maid.getDeltaMovement().y
                    );

                    release(
                            maid,
                            iterator,
                            "ground_timeout"
                    );
                }

                continue;
            }

            if (now - lock.stretchStartGameTime
                    >= lock.durationTicks) {
                release(
                        maid,
                        iterator,
                        "stretch_finished"
                );
            }
        }
    }

    /**
     * Prevent walking/navigation from stealing the wake sequence while still
     * allowing Minecraft gravity and collision resolution to move the maid on
     * the Y axis.
     */
    private static void keepHorizontalLocked(
            EntityMaid maid
    ) {
        Vec3 motion =
                maid.getDeltaMovement();

        maid.setDeltaMovement(
                0.0D,
                motion.y,
                0.0D
        );
    }

    private static void release(
            EntityMaid maid,
            Iterator<Map.Entry<UUID, StretchLock>> iterator,
            String reason
    ) {
        MaidActionLock.release(
                maid,
                ACTION_ID
        );

        iterator.remove();

        LOGGER.info(
                "[WGON WakeStretch] server wake gate END entityId={} uuid={} reason={} onGround={} y={}",
                maid.getId(),
                maid.getUUID(),
                reason,
                maid.onGround(),
                maid.getY()
        );
    }

    private static final class StretchLock {
        private final ResourceKey<Level> dimension;
        private final long startedGameTime;
        private final int durationTicks;

        private int groundedTicks;
        private boolean stretchStarted;
        private long stretchStartGameTime;

        private StretchLock(
                ResourceKey<Level> dimension,
                long startedGameTime,
                int durationTicks
        ) {
            this.dimension = dimension;
            this.startedGameTime = startedGameTime;
            this.durationTicks = durationTicks;
        }
    }
}

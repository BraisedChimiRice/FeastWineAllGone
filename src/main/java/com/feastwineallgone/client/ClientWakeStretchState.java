package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived client animation state used after a successful drunk-sleep
 * face poke.
 *
 * V4 has two phases:
 *   1) settle: maid is awake/standing and the old floor-sleep render offset is
 *      allowed to collapse cleanly;
 *   2) stretch: play the authored 5.5-second Wine Fox wake stretch.
 */
public final class ClientWakeStretchState {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    public static final int DEFAULT_SETTLE_TICKS = 20;   // 1 second
    public static final int DEFAULT_DURATION_TICKS = 110; // 5.5 seconds

    private static final Map<Integer, StretchState> ACTIVE =
            new ConcurrentHashMap<>();

    private static final Set<Integer> RENDER_LOGGED =
            ConcurrentHashMap.newKeySet();

    private static final Set<Integer> REJECT_LOGGED =
            ConcurrentHashMap.newKeySet();

    private ClientWakeStretchState() {
    }

    public static void start(
            int entityId,
            UUID maidUuid,
            int settleTicks,
            int durationTicks
    ) {
        int safeSettle =
                Math.max(
                        0,
                        settleTicks
                );

        int safeDuration =
                Math.max(
                        1,
                        durationTicks
                );

        long now =
                System.nanoTime();

        ACTIVE.put(
                entityId,
                new StretchState(
                        maidUuid,
                        now,
                        safeSettle / 20.0D,
                        safeDuration / 20.0D
                )
        );

        RENDER_LOGGED.remove(entityId);
        REJECT_LOGGED.remove(entityId);

        LOGGER.info(
                "[WGON WakeStretch] client state START entityId={} uuid={} settleTicks={} durationTicks={}",
                entityId,
                maidUuid,
                safeSettle,
                safeDuration
        );
    }

    public static boolean isActive(
            EntityMaid maid
    ) {
        StretchState state =
                getValidState(maid);

        if (state == null) {
            return false;
        }

        if (elapsedSeconds(state) >= totalSeconds(state)) {
            clearEntity(maid.getId());
            return false;
        }

        return true;
    }

    public static boolean isSettling(
            EntityMaid maid
    ) {
        StretchState state =
                getValidState(maid);

        if (state == null) {
            return false;
        }

        double elapsed =
                elapsedSeconds(state);

        if (elapsed >= totalSeconds(state)) {
            clearEntity(maid.getId());
            return false;
        }

        return elapsed < state.settleSeconds();
    }

    public static boolean isAnimationPhase(
            EntityMaid maid
    ) {
        StretchState state =
                getValidState(maid);

        if (state == null) {
            return false;
        }

        double elapsed =
                elapsedSeconds(state);

        if (elapsed >= totalSeconds(state)) {
            clearEntity(maid.getId());
            return false;
        }

        return elapsed >= state.settleSeconds();
    }

    /**
     * Elapsed time inside the authored stretch, excluding the settle delay.
     */
    public static double animationElapsedSeconds(
            EntityMaid maid
    ) {
        StretchState state =
                getValidState(maid);

        if (state == null) {
            return 0.0D;
        }

        double elapsed =
                elapsedSeconds(state);

        if (elapsed >= totalSeconds(state)) {
            clearEntity(maid.getId());
            return state.durationSeconds();
        }

        return Math.max(
                0.0D,
                Math.min(
                        state.durationSeconds(),
                        elapsed - state.settleSeconds()
                )
        );
    }

    /**
     * True exactly once per stretch cycle.
     */
    public static boolean markRenderStarted(
            EntityMaid maid
    ) {
        return RENDER_LOGGED.add(
                maid.getId()
        );
    }

    public static boolean markFamilyRejected(
            EntityMaid maid
    ) {
        return REJECT_LOGGED.add(
                maid.getId()
        );
    }

    public static void clearAll() {
        ACTIVE.clear();
        RENDER_LOGGED.clear();
        REJECT_LOGGED.clear();
    }

    private static StretchState getValidState(
            EntityMaid maid
    ) {
        StretchState state =
                ACTIVE.get(maid.getId());

        if (state == null) {
            return null;
        }

        if (!state.maidUuid().equals(maid.getUUID())) {
            clearEntity(maid.getId());
            return null;
        }

        return state;
    }

    private static void clearEntity(
            int entityId
    ) {
        ACTIVE.remove(entityId);
        RENDER_LOGGED.remove(entityId);
        REJECT_LOGGED.remove(entityId);
    }

    private static double elapsedSeconds(
            StretchState state
    ) {
        return (System.nanoTime() - state.startedAtNanos())
                / 1_000_000_000.0D;
    }

    private static double totalSeconds(
            StretchState state
    ) {
        return state.settleSeconds()
                + state.durationSeconds();
    }

    private record StretchState(
            UUID maidUuid,
            long startedAtNanos,
            double settleSeconds,
            double durationSeconds
    ) {
    }
}

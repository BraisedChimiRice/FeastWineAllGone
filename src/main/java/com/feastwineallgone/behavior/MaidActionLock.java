package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared action lock for WGON maid behaviours.
 *
 * One maid may only be controlled by one WGON behaviour at a time.
 *
 * Priority rule:
 *
 * night_barrel has absolute priority over ordinary WGON idle behaviours
 * such as idle_food and counter_drink once a theft session is starting.
 */
public final class MaidActionLock {

    private static final Map<UUID, String> LOCKS =
            new ConcurrentHashMap<>();

    private MaidActionLock() {
    }

    /**
     * Try to acquire the maid for one WGON behaviour.
     *
     * Re-entering with the same action id is allowed.
     *
     * Night stealing may pre-empt low-priority idle actions after the
     * maid has entered sleep.
     */
    public static boolean tryAcquire(
            EntityMaid maid,
            String actionId
    ) {
        clearStaleOrdinaryIdleLock(maid);

        UUID maidId =
                maid.getUUID();

        String existing =
                LOCKS.get(
                        maidId
                );

        if (existing == null) {
            return LOCKS.putIfAbsent(
                    maidId,
                    actionId
            ) == null;
        }

        if (existing.equals(
                actionId
        )) {
            return true;
        }

        if (canNightStealPreempt(
                maid,
                actionId,
                existing
        )) {

            preemptIdleAction(
                    maid,
                    existing
            );

            /*
             * Defensive cleanup in case the old manager had already
             * lost its session but left a stale lock behind.
             */
            LOCKS.remove(
                    maidId,
                    existing
            );

            String afterPreempt =
                    LOCKS.putIfAbsent(
                            maidId,
                            actionId
                    );

            return afterPreempt == null
                    || afterPreempt.equals(
                    actionId
            );
        }

        return false;
    }

    /**
     * During sleep, a low-priority idle lock must not cause
     * NightStealManager.findEligibleMaids() to reject the maid before
     * night_barrel gets a chance to acquire and pre-empt it.
     */
    public static boolean isLocked(
            EntityMaid maid
    ) {
        clearStaleOrdinaryIdleLock(maid);

        String existing =
                LOCKS.get(
                        maid.getUUID()
                );

        if (existing == null) {
            return false;
        }

        if (maid.isSleeping()
                && isPreemptibleIdleAction(
                existing
        )) {
            return false;
        }

        return true;
    }

    public static boolean isLockedBy(
            EntityMaid maid,
            String actionId
    ) {
        clearStaleOrdinaryIdleLock(maid);

        String existing =
                LOCKS.get(
                        maid.getUUID()
                );

        return actionId.equals(
                existing
        );
    }

    public static boolean isLockedByOther(
            EntityMaid maid,
            String actionId
    ) {
        clearStaleOrdinaryIdleLock(maid);

        String existing =
                LOCKS.get(
                        maid.getUUID()
                );

        return existing != null
                && !existing.equals(
                actionId
        );
    }

    /**
     * Release only if this action owns the lock.
     *
     * This prevents one behaviour accidentally unlocking another.
     */
    public static void release(
            EntityMaid maid,
            String actionId
    ) {
        LOCKS.remove(
                maid.getUUID(),
                actionId
        );
    }

    /**
     * Emergency cleanup only.
     */
    public static void forceRelease(
            EntityMaid maid
    ) {
        LOCKS.remove(
                maid.getUUID()
        );
    }

    /**
     * Ordinary idle locks are leases owned by their manager session.  If a
     * session disappeared because a target changed during the same tick, a
     * stale lock must not freeze the maid until she is recalled/re-spawned.
     *
     * Night and seated locks are intentionally excluded because those systems
     * have extra pre-lock / chair lifecycle states that are not represented by
     * one simple ACTIVE map.
     */
    private static void clearStaleOrdinaryIdleLock(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        String existing = LOCKS.get(maidId);

        if (existing == null) {
            return;
        }

        boolean stale =
                (IdleFoodManager.ACTION_ID.equals(existing)
                        && !IdleFoodManager.hasActiveSession(maid))
                || (CounterDrinkManager.ACTION_ID.equals(existing)
                        && !CounterDrinkManager.hasActiveSession(maid))
                || (FavoriteAttentionManager.ACTION_ID.equals(existing)
                        && !FavoriteAttentionManager.hasActiveSession(maid))
                || (FavoriteTableDrinkManager.ACTION_ID.equals(existing)
                        && !FavoriteTableDrinkManager.hasActiveSession(maid))
                || (FruitTastingManager.ACTION_ID.equals(existing)
                        && !FruitTastingManager.hasActiveSession(maid));

        if (stale) {
            LOCKS.remove(maidId, existing);
        }
    }

    private static boolean canNightStealPreempt(
            EntityMaid maid,
            String requestedAction,
            String existingAction
    ) {
        return NightStealManager.ACTION_ID.equals(
                requestedAction
        )
                && isPreemptibleIdleAction(
                existingAction
        );
    }

    private static boolean isPreemptibleIdleAction(
            String actionId
    ) {
        return IdleFoodManager.ACTION_ID.equals(
                actionId
        )
                || CounterDrinkManager.ACTION_ID.equals(
                actionId
        )
                || SeatedDiningManager.ACTION_ID.equals(
                actionId
        )
                || FavoriteAttentionManager.ACTION_ID.equals(
                actionId
        )
                || FavoriteTableDrinkManager.ACTION_ID.equals(
                actionId
        )
                || FruitTastingManager.ACTION_ID.equals(
                actionId
        );
    }

    private static void preemptIdleAction(
            EntityMaid maid,
            String existingAction
    ) {
        if (IdleFoodManager.ACTION_ID.equals(
                existingAction
        )) {
            IdleFoodManager.cancelForMaid(
                    maid
            );
            return;
        }

        if (CounterDrinkManager.ACTION_ID.equals(
                existingAction
        )) {
            CounterDrinkManager.cancelForMaid(
                    maid
            );
            return;
        }

        if (SeatedDiningManager.ACTION_ID.equals(
                existingAction
        )) {
            SeatedDiningManager.cancelForMaid(
                    maid
            );
            return;
        }

        if (FavoriteAttentionManager.ACTION_ID.equals(
                existingAction
        )) {
            FavoriteAttentionManager.cancelForMaid(
                    maid
            );
            return;
        }

        if (FavoriteTableDrinkManager.ACTION_ID.equals(
                existingAction
        )) {
            FavoriteTableDrinkManager.cancelForMaid(
                    maid
            );
            return;
        }

        if (FruitTastingManager.ACTION_ID.equals(
                existingAction
        )) {
            FruitTastingManager.cancelForMaid(
                    maid
            );
        }
    }
}

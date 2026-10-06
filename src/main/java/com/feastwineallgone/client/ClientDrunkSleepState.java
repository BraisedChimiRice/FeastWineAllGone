package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client mirror of FWAG's authoritative drunk-sleep state.
 *
 * Forge persistentData belongs to the server and is not automatically mirrored
 * to clients. This cache is fed explicitly by WgonNetwork so client mixins and
 * model code know that a real SleepingPos is a FLOOR nap rather than a bed.
 *
 * UUID + dimension are kept alongside the entity id so a stale numeric id can
 * never accidentally mark an unrelated maid as drunk after a reconnect.
 */
public final class ClientDrunkSleepState {

    private static final Map<Integer, SyncedSleep> ACTIVE =
            new ConcurrentHashMap<>();

    private ClientDrunkSleepState() {
    }

    public static void apply(
            int entityId,
            UUID maidUuid,
            boolean active,
            ResourceLocation dimension,
            BlockPos pos,
            float yaw
    ) {
        if (!active) {
            ACTIVE.computeIfPresent(
                    entityId,
                    (id, old) ->
                            old.maidUuid().equals(maidUuid)
                                    ? null
                                    : old
            );
            return;
        }

        ACTIVE.put(
                entityId,
                new SyncedSleep(
                        maidUuid,
                        dimension,
                        pos.immutable(),
                        yaw
                )
        );
    }

    public static boolean isActive(
            EntityMaid maid
    ) {
        SyncedSleep sleep =
                ACTIVE.get(maid.getId());

        if (sleep == null) {
            return false;
        }

        if (!sleep.maidUuid().equals(maid.getUUID())) {
            return false;
        }

        return sleep.dimension().equals(
                maid.level().dimension().location()
        );
    }

    public static SyncedSleep get(
            EntityMaid maid
    ) {
        return isActive(maid)
                ? ACTIVE.get(maid.getId())
                : null;
    }

    public static void clear(
            int entityId,
            UUID maidUuid
    ) {
        ACTIVE.computeIfPresent(
                entityId,
                (id, old) ->
                        old.maidUuid().equals(maidUuid)
                                ? null
                                : old
        );
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    public record SyncedSleep(
            UUID maidUuid,
            ResourceLocation dimension,
            BlockPos pos,
            float yaw
    ) {
    }
}

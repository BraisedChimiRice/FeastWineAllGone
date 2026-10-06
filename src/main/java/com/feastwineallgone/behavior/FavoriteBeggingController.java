package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns WGON's temporary favorite-food begging state.
 *
 * Touhou Little Maid also uses EntityMaid#setBegging for its native
 * temptation behavior (for example when a player holds cake). That task may
 * call setBegging(false) while WGON is still doing its 2-3 second favorite
 * reaction. The accompanying mixin prevents those unrelated false writes from
 * clearing WGON's reaction until WGON explicitly releases it.
 */
public final class FavoriteBeggingController {

    private static final Set<UUID> FORCED = ConcurrentHashMap.newKeySet();

    private FavoriteBeggingController() {
    }

    public static void start(EntityMaid maid) {
        FORCED.add(maid.getUUID());
        maid.setBegging(true);
    }

    public static void stop(EntityMaid maid) {
        FORCED.remove(maid.getUUID());
        maid.setBegging(false);
    }

    public static boolean isForced(EntityMaid maid) {
        return FORCED.contains(maid.getUUID());
    }
}

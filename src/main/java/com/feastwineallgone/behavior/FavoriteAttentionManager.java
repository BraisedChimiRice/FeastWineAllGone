package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.food.FavoriteFoodRules;
import com.feastwineallgone.integration.tavern.TavernCounterDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Short favorite-food reaction layer for ordinary idle maids.
 *
 * A favorite is discovered before the normal FOOD/DRINK 50/50 roll. The maid
 * begs/wags for 2-3 seconds, then hands control to the existing food or bar
 * drinking manager. Night stealing can pre-empt this action at any time.
 */
public final class FavoriteAttentionManager {

    public static final String ACTION_ID = "favorite_attention";

    private static final int MIN_BEG_TICKS = 40;
    private static final int MAX_BEG_TICKS = 60;

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, TargetKey> LAST_DISCOVERED = new HashMap<>();

    private FavoriteAttentionManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)
                || event.phase != TickEvent.Phase.END) {
            return;
        }

        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Session session = entry.getValue();
            if (session.level != level) {
                continue;
            }

            session.tick();
            if (session.finished) {
                iterator.remove();
            }
        }
    }

    public static Optional<Target> findPreferredTarget(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (!MaidBehaviorSettings.isFavoriteReactionEnabled(maid)) {
            return Optional.empty();
        }

        Target best = null;
        double bestDistance = Double.MAX_VALUE;

        int foodRadius = WgonConfig.IDLE_FOOD_SEARCH_RADIUS.get();
        Optional<BlockPos> favoriteFood =
                MaidBehaviorSettings.isIdleFoodEnabled(maid)
                        ? IdleFoodManager.findNearestAvailableFavoriteFood(
                                level,
                                maid,
                                foodRadius
                        )
                        : Optional.empty();

        if (favoriteFood.isPresent()) {
            BlockPos pos = favoriteFood.get();
            best = new Target(TargetKind.FOOD, pos.immutable());
            bestDistance = maid.position().distanceToSqr(Vec3.atCenterOf(pos));
        }

        List<BlockPos> drinkTargets =
                CounterDrinkManager.findAvailableTargets(level, maid);

        for (BlockPos pos : drinkTargets) {
            ItemStack preview = TavernCounterDrinkAccess.peekOne(level, pos);
            if (!FavoriteFoodRules.isFavoriteDrink(level, pos, preview)) {
                continue;
            }

            double distance = maid.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Target(TargetKind.BAR_DRINK, pos.immutable());
            }
        }

        /*
         * Favorite Tavern drinks directly above Cookery tables are also
         * discoverable while the maid is roaming. Normal non-favorite table
         * drinks remain seated-dining-only.
         */
        if (MaidBehaviorSettings.isCounterDrinkEnabled(maid)) {
            BlockPos origin = maid.blockPosition();
            int tableRadius = Math.max(4, foodRadius);
            for (BlockPos tablePos : BlockPos.betweenClosed(
                    origin.offset(-tableRadius, -3, -tableRadius),
                    origin.offset(tableRadius, 3, tableRadius)
            )) {
                if (!(level.getBlockState(tablePos).getBlock() instanceof TableBlock)) {
                    continue;
                }

                BlockPos drinkPos = tablePos.above();
                if (!FavoriteTableDrinkManager.isFavoriteTableDrink(level, drinkPos)) {
                    continue;
                }

                double distance = maid.position().distanceToSqr(Vec3.atCenterOf(drinkPos));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Target(TargetKind.TABLE_DRINK, drinkPos.immutable());
                }
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * The same still-present favorite does not make the maid beg before every
     * single bite. Once the target disappears from discovery, the memory is
     * cleared and a future reappearance can trigger the cute reaction again.
     */
    public static boolean tryStartOrDispatch(
            ServerLevel level,
            EntityMaid maid,
            Target target
    ) {
        if (!MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                || !IdleFoodManager.isBaseIdleEligible(maid)
                || NightStealManager.hasNightPriority(level, maid)
                || MaidActionLock.isLocked(maid)) {
            return false;
        }

        UUID maidId = maid.getUUID();
        TargetKey key = target.key();

        if (key.equals(LAST_DISCOVERED.get(maidId))) {
            return dispatch(level, maid, target);
        }

        if (!isStillValid(level, target)) {
            return false;
        }

        if (!MaidActionLock.tryAcquire(maid, ACTION_ID)) {
            return false;
        }

        Session session = new Session(level, maid, target);
        ACTIVE.put(maidId, session);
        session.begin();
        return true;
    }

    public static void clearDiscovery(EntityMaid maid) {
        if (!ACTIVE.containsKey(maid.getUUID())) {
            LAST_DISCOVERED.remove(maid.getUUID());
        }
    }

    public static void cancelForMaid(EntityMaid maid) {
        Session session = ACTIVE.remove(maid.getUUID());
        if (session != null) {
            /*
             * Only the session that actually owns the favorite-attention
             * reaction may release the shared begging flag. Night stealing
             * calls cancelForMaid() defensively while the owner is asleep;
             * clearing begging here when no favorite-attention session exists
             * would erase NightStealManager's PRAY animation every tick.
             */
            session.finish();
        } else {
            MaidActionLock.release(maid, ACTION_ID);
        }
    }

    private static boolean dispatch(
            ServerLevel level,
            EntityMaid maid,
            Target target
    ) {
        if (!isStillValid(level, target)) {
            return false;
        }

        if (target.kind() == TargetKind.FOOD) {
            return IdleFoodManager.tryStartForMaid(
                    level,
                    maid,
                    target.pos()
            );
        }

        if (target.kind() == TargetKind.TABLE_DRINK) {
            return FavoriteTableDrinkManager.tryStartForMaid(
                    level,
                    maid,
                    target.pos()
            );
        }

        return CounterDrinkManager.tryStartForMaid(
                level,
                maid,
                List.of(target.pos())
        );
    }

    private static boolean isStillValid(ServerLevel level, Target target) {
        if (target.kind() == TargetKind.FOOD) {
            return FavoriteFoodRules.isFavoritePlacedFood(
                    level,
                    target.pos()
            );
        }

        if (target.kind() == TargetKind.TABLE_DRINK) {
            return FavoriteTableDrinkManager.isFavoriteTableDrink(
                    level,
                    target.pos()
            );
        }

        if (!TavernCounterDrinkAccess.isSupportedDrinkAt(level, target.pos())) {
            return false;
        }

        ItemStack preview = TavernCounterDrinkAccess.peekOne(level, target.pos());
        return FavoriteFoodRules.isFavoriteDrink(level, target.pos(), preview);
    }

    private static void faceHorizontally(EntityMaid maid, BlockPos pos) {
        Vec3 target = Vec3.atCenterOf(pos);
        double dx = target.x - maid.getX();
        double dz = target.z - maid.getZ();

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        maid.setYRot(yaw);
        maid.setYHeadRot(yaw);
        maid.setXRot(0.0F);
    }

    static boolean hasActiveSession(EntityMaid maid) {
        return ACTIVE.containsKey(maid.getUUID());
    }

    public enum TargetKind {
        FOOD,
        BAR_DRINK,
        TABLE_DRINK
    }

    public record Target(TargetKind kind, BlockPos pos) {
        TargetKey key() {
            return new TargetKey(kind, pos);
        }
    }

    private record TargetKey(TargetKind kind, BlockPos pos) {
    }

    private static final class Session {
        private final ServerLevel level;
        private final EntityMaid maid;
        private final Target target;
        private final int begTicks;
        private int ticks = 0;
        private boolean finished = false;

        private Session(ServerLevel level, EntityMaid maid, Target target) {
            this.level = level;
            this.maid = maid;
            this.target = target;
            this.begTicks = MIN_BEG_TICKS
                    + level.random.nextInt(MAX_BEG_TICKS - MIN_BEG_TICKS + 1);
        }

        private void begin() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            FavoriteBeggingController.start(maid);
            faceHorizontally(maid, target.pos());
        }

        private void tick() {
            if (finished) {
                return;
            }

            if (!maid.isAlive()
                    || maid.isRemoved()
                    || !MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                    || NightStealManager.hasNightPriority(level, maid)
                    || !IdleFoodManager.isBaseIdleEligible(maid)
                    || !isStillValid(level, target)) {
                finish();
                return;
            }

            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            FavoriteBeggingController.start(maid);
            faceHorizontally(maid, target.pos());
            ticks++;

            if (ticks < begTicks) {
                return;
            }

            FavoriteBeggingController.stop(maid);
            LAST_DISCOVERED.put(maid.getUUID(), target.key());
            MaidActionLock.release(maid, ACTION_ID);

            dispatch(level, maid, target);
            finished = true;
        }

        private void finish() {
            if (finished) {
                return;
            }

            FavoriteBeggingController.stop(maid);
            MaidActionLock.release(maid, ACTION_ID);
            finished = true;
        }
    }
}

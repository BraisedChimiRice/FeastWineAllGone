package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.feastwineallgone.food.FavoriteFoodRules;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import com.feastwineallgone.integration.tavern.TavernDrinkAccess;
import com.feastwineallgone.integration.tavern.TavernPlacedDrinkAccess;
import com.feastwineallgone.config.MaidBehaviorSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Favorite-only drinking for Tavern bottles physically placed on top of a
 * Kaleidoscope Cookery table while the maid is roaming normally.
 *
 * Normal idle drinking intentionally remains bar-counter-only. This manager
 * exists solely so a whitelisted favorite wine on the dining table can cause
 * the same cute priority reaction as favorite food.
 */
public final class FavoriteTableDrinkManager {

    public static final String ACTION_ID = "favorite_table_drink";

    private static final double APPROACH_SPEED = 0.55D;
    private static final double REACH_DISTANCE_SQR = 0.90D * 0.90D;
    private static final double MAX_DRINK_HORIZONTAL_REACH = 1.35D;
    private static final double MAX_DRINK_VERTICAL_REACH = 2.25D;
    private static final int DRINK_VISUAL_TICKS = 24;
    private static final int WATCHDOG_TICKS = 300;

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<GlobalPos, UUID> RESERVED = new HashMap<>();

    private FavoriteTableDrinkManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)
                || event.phase != TickEvent.Phase.END) {
            return;
        }

        Iterator<Map.Entry<UUID, Session>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (session.level != level) {
                continue;
            }

            session.tick();
            if (session.finished) {
                iterator.remove();
            }
        }
    }

    public static boolean tryStartForMaid(
            ServerLevel level,
            EntityMaid maid,
            BlockPos drinkPos
    ) {
        UUID maidId = maid.getUUID();

        if (ACTIVE.containsKey(maidId)
                || !MaidBehaviorSettings.isCounterDrinkEnabled(maid)
                || !MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                || !IdleFoodManager.isBaseIdleEligible(maid)
                || NightStealManager.hasNightPriority(level, maid)
                || MaidActionLock.isLocked(maid)
                || !isFavoriteTableDrink(level, drinkPos)) {
            return false;
        }

        BlockPos standPos = GroundApproachHelper.findGroundSpotInHorizontalRange(
                level,
                maid,
                drinkPos,
                1,
                1,
                true
        );

        if (standPos == null) {
            return false;
        }

        GlobalPos reservation = GlobalPos.of(level.dimension(), drinkPos);
        UUID owner = RESERVED.get(reservation);
        if (owner != null && !owner.equals(maidId)) {
            return false;
        }

        if (!MaidActionLock.tryAcquire(maid, ACTION_ID)) {
            return false;
        }

        UUID previous = RESERVED.putIfAbsent(reservation, maidId);
        if (previous != null && !previous.equals(maidId)) {
            MaidActionLock.release(maid, ACTION_ID);
            return false;
        }

        Session session = new Session(
                level,
                maid,
                drinkPos.immutable(),
                standPos.immutable(),
                reservation
        );
        ACTIVE.put(maidId, session);
        session.begin();
        return true;
    }

    static boolean hasActiveSession(EntityMaid maid) {
        return ACTIVE.containsKey(maid.getUUID());
    }

    public static boolean isFavoriteTableDrink(ServerLevel level, BlockPos drinkPos) {
        if (!(level.getBlockState(drinkPos.below()).getBlock() instanceof TableBlock)) {
            return false;
        }

        if (!TavernPlacedDrinkAccess.isSupportedDrinkAt(level, drinkPos)) {
            return false;
        }

        ItemStack preview = TavernPlacedDrinkAccess.peekOne(level, drinkPos);
        return FavoriteFoodRules.isFavoriteDrink(level, drinkPos, preview);
    }

    public static void cancelForMaid(EntityMaid maid) {
        Session session = ACTIVE.remove(maid.getUUID());
        if (session != null) {
            session.finish();
        } else {
            MaidActionLock.release(maid, ACTION_ID);
        }
    }

    private enum Stage {
        MOVE,
        DRINK
    }

    private static final class Session {
        private final ServerLevel level;
        private final EntityMaid maid;
        private final BlockPos drinkPos;
        private final BlockPos standPos;
        private final GlobalPos reservation;
        private final ItemStack oldMainHand;

        private Stage stage = Stage.MOVE;
        private int stageTicks = 0;
        private int totalTicks = 0;
        private ItemStack drink = ItemStack.EMPTY;
        private boolean detachedDrinkPending = false;
        private boolean finished = false;

        private Session(
                ServerLevel level,
                EntityMaid maid,
                BlockPos drinkPos,
                BlockPos standPos,
                GlobalPos reservation
        ) {
            this.level = level;
            this.maid = maid;
            this.drinkPos = drinkPos;
            this.standPos = standPos;
            this.reservation = reservation;
            this.oldMainHand = maid.getMainHandItem().copy();
        }

        private void begin() {
            maid.getNavigation().stop();
        }

        private void tick() {
            if (finished) {
                return;
            }

            totalTicks++;
            stageTicks++;

            if (totalTicks > WATCHDOG_TICKS
                    || !MaidBehaviorSettings.isCounterDrinkEnabled(maid)
                    || !MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                    || NightStealManager.hasNightPriority(level, maid)
                    || !IdleFoodManager.isBaseIdleEligible(maid)
                    || !isFavoriteTableDrink(level, drinkPos)) {
                finish();
                return;
            }

            faceDrink();

            if (stage == Stage.MOVE) {
                tickMove();
            } else {
                tickDrink();
            }
        }

        private void tickMove() {
            Vec3 target = GroundApproachHelper.centerOfFeet(standPos);
            maid.setSprinting(false);

            if (maid.distanceToSqr(target) <= REACH_DISTANCE_SQR) {
                if (!GroundApproachHelper.settleNaturallyIfGrounded(maid)) {
                    return;
                }

                if (!isWithinPhysicalDrinkReach()) {
                    finish();
                    return;
                }

                ItemStack taken = TavernPlacedDrinkAccess.takeOne(level, drinkPos);
                if (taken.isEmpty() || !TavernDrinkAccess.isDrink(taken)) {
                    finish();
                    return;
                }

                drink = taken.copy();
                drink.setCount(1);
                detachedDrinkPending = true;
                maid.setItemInHand(InteractionHand.MAIN_HAND, drink.copy());
                maid.swing(InteractionHand.MAIN_HAND);
                maid.startUsingItem(InteractionHand.MAIN_HAND);
                stage = Stage.DRINK;
                stageTicks = 0;
                return;
            }

            if (stageTicks % 10 == 1 || maid.getNavigation().isDone()) {
                boolean started = GroundApproachHelper.startSafeNavigation(
                        level,
                        maid,
                        standPos,
                        APPROACH_SPEED
                );
                if (!started) {
                    finish();
                }
            }
        }

        private void tickDrink() {
            maid.getNavigation().stop();

            if (stageTicks < DRINK_VISUAL_TICKS) {
                return;
            }

            maid.stopUsingItem();
            boolean consumed = TavernDrinkAccess.consumeDetachedDrink(
                    level,
                    maid,
                    drink.copy()
            );

            if (consumed) {
                detachedDrinkPending = false;
            }

            drink = ItemStack.EMPTY;
            finish();
        }

        private boolean isWithinPhysicalDrinkReach() {
            Vec3 drinkCenter = Vec3.atCenterOf(drinkPos);
            double dx = drinkCenter.x - maid.getX();
            double dz = drinkCenter.z - maid.getZ();
            double horizontalSqr = dx * dx + dz * dz;
            double vertical = Math.abs(drinkCenter.y - maid.getY());

            return horizontalSqr
                    <= MAX_DRINK_HORIZONTAL_REACH
                    * MAX_DRINK_HORIZONTAL_REACH
                    && vertical <= MAX_DRINK_VERTICAL_REACH;
        }

        private void faceDrink() {
            Vec3 target = Vec3.atCenterOf(drinkPos);
            double dx = target.x - maid.getX();
            double dz = target.z - maid.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setXRot(0.0F);
        }

        private void finish() {
            if (finished) {
                return;
            }

            maid.stopUsingItem();

            if (detachedDrinkPending && !drink.isEmpty()) {
                CookeryDrinkAccess.storeOrDrop(
                        level,
                        maid,
                        drink.copy()
                );
                detachedDrinkPending = false;
                drink = ItemStack.EMPTY;
            }

            maid.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand.copy());
            RESERVED.remove(reservation, maid.getUUID());
            MaidActionLock.release(maid, ACTION_ID);
            finished = true;
        }
    }
}

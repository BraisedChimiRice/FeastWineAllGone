package com.feastwineallgone.behavior;

import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.integration.compat.FruitHarvestAccess;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fruits Delight dropped-durian compatibility.
 *
 * This is deliberately a fruit-tasting sub-session and therefore reuses
 * FruitTastingManager.ACTION_ID. Night stealing can pre-empt it through the
 * existing MaidActionLock rules, and the ordinary fruit-tasting toggle/chance
 * remains the single player-facing switch.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class GroundDurianTastingManager {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final double APPROACH_SPEED =
            0.70D;

    private static final int APPROACH_TIMEOUT_TICKS =
            20 * 12;

    private static final int SETTLE_TICKS =
            8;

    private static final int CHOP_TICKS =
            8;

    private static final int ADMIRE_TICKS =
            10;

    private static final int EAT_TICKS =
            24;

    private static final int WATCHDOG_TICKS =
            20 * 30;

    private static final int FAVORABILITY_PER_DURIAN =
            10;

    private static final double MAX_HORIZONTAL_REACH =
            2.75D;

    private static final double MAX_VERTICAL_REACH =
            3.0D;

    private static final Map<UUID, Session> ACTIVE =
            new HashMap<>();

    private static final Set<UUID> RESERVED_ITEM_ENTITIES =
            new HashSet<>();

    private GroundDurianTastingManager() {
    }

    /**
     * Called only after the ordinary fruit-tasting chance has passed.
     * Dropped durians get priority over block fruit because ItemEntities can
     * despawn and because this action was explicitly added as a cleanup/snack
     * behaviour.
     */
    public static boolean tryStartForMaid(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (level == null
                || maid == null
                || ACTIVE.containsKey(maid.getUUID())
                || !MaidBehaviorSettings.isFruitTastingEnabled(maid)
                || !isIdleEnough(maid)
                || !FruitHarvestAccess.hasCuttingTool(maid)) {
            return false;
        }

        Optional<ItemEntity> target =
                findNearestDroppedDurian(
                        level,
                        maid
                );

        if (target.isEmpty()) {
            return false;
        }

        ItemEntity item =
                target.get();

        BlockPos groundSpot =
                GroundApproachHelper.findFruitGroundSpot(
                        level,
                        maid,
                        item.blockPosition(),
                        true
                );

        if (groundSpot == null) {
            return false;
        }

        if (!MaidActionLock.tryAcquire(
                maid,
                FruitTastingManager.ACTION_ID
        )) {
            return false;
        }

        RESERVED_ITEM_ENTITIES.add(
                item.getUUID()
        );

        Session session =
                new Session(
                        level,
                        maid,
                        item,
                        groundSpot
                );

        ACTIVE.put(
                maid.getUUID(),
                session
        );

        LOGGER.info(
                "[WGON FruitDiag] ground-durian session-start maid={} itemEntity={} count={} pos={} groundSpot={} cutter={}",
                maid.getDisplayName().getString(),
                item.getUUID(),
                item.getItem().getCount(),
                item.blockPosition(),
                groundSpot,
                FruitHarvestAccess.findCuttingTool(maid).getHoverName().getString()
        );

        session.begin();
        return true;
    }

    public static boolean hasActiveSession(
            EntityMaid maid
    ) {
        return maid != null
                && ACTIVE.containsKey(
                        maid.getUUID()
                );
    }

    public static void cancelForMaid(
            EntityMaid maid
    ) {
        if (maid == null) {
            return;
        }

        Session session =
                ACTIVE.remove(
                        maid.getUUID()
                );

        if (session != null) {
            session.finish();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (!(event.level instanceof ServerLevel level)
                || event.phase != TickEvent.Phase.END) {
            return;
        }

        Iterator<Map.Entry<UUID, Session>> iterator =
                ACTIVE.entrySet()
                        .iterator();

        while (iterator.hasNext()) {
            Session session =
                    iterator.next()
                            .getValue();

            if (session.level != level) {
                continue;
            }

            session.tick();

            if (session.finished) {
                iterator.remove();
            }
        }
    }

    private static Optional<ItemEntity> findNearestDroppedDurian(
            ServerLevel level,
            EntityMaid maid
    ) {
        int radius =
                WgonConfig.FRUIT_TASTING_SEARCH_RADIUS.get();

        int verticalRadius =
                Math.min(
                        radius,
                        6
                );

        AABB box =
                maid.getBoundingBox()
                        .inflate(
                                radius,
                                verticalRadius,
                                radius
                        );

        return level.getEntitiesOfClass(
                        ItemEntity.class,
                        box,
                        entity ->
                                FruitHarvestAccess.isDroppedDurian(entity)
                                        && !RESERVED_ITEM_ENTITIES.contains(
                                                entity.getUUID()
                                        )
                )
                .stream()
                .min(
                        Comparator.comparingDouble(
                                maid::distanceToSqr
                        )
                );
    }

    private static boolean isIdleEnough(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()
                || maid.getOwnerUUID() == null
                || maid.isSleeping()
                || maid.isMaidInSittingPose()
                || maid.isUsingItem()
                || maid.getTarget() != null
                || maid.getTask() == null) {
            return false;
        }

        return TaskIdle.UID.equals(
                maid.getTask().getUid()
        );
    }

    private enum Stage {
        MOVE,
        SETTLE,
        CHOP,
        ADMIRE,
        EAT
    }

    private static final class Session {

        private final ServerLevel level;
        private final EntityMaid maid;
        private final ItemEntity target;
        private final UUID targetId;
        private final BlockPos groundSpot;
        private final ItemStack oldMainHand;

        private Stage stage =
                Stage.MOVE;

        private int stageTicks;
        private int totalTicks;

        private boolean finished;
        private boolean handOverridden;
        private boolean durianCut;

        private ItemStack fruit =
                ItemStack.EMPTY;

        private Session(
                ServerLevel level,
                EntityMaid maid,
                ItemEntity target,
                BlockPos groundSpot
        ) {
            this.level = level;
            this.maid = maid;
            this.target = target;
            this.targetId = target.getUUID();
            this.groundSpot = groundSpot.immutable();
            this.oldMainHand =
                    maid.getMainHandItem()
                            .copy();
        }

        private void begin() {
            GroundApproachHelper.clearCompetingWalkTarget(
                    maid
            );
            maid.getNavigation()
                    .stop();
            maid.setSprinting(false);
        }

        private void tick() {
            if (finished) {
                return;
            }

            totalTicks++;
            stageTicks++;

            if (totalTicks > WATCHDOG_TICKS
                    || !MaidBehaviorSettings.isFruitTastingEnabled(maid)
                    || NightStealManager.hasNightPriority(level, maid)
                    || !isSessionStillValid()) {
                finish();
                return;
            }

            switch (stage) {
                case MOVE -> tickMove();
                case SETTLE -> tickSettle();
                case CHOP -> tickChop();
                case ADMIRE -> tickAdmire();
                case EAT -> tickEat();
            }
        }

        private boolean isSessionStillValid() {
            if (!maid.isAlive()
                    || maid.isRemoved()
                    || maid.getOwnerUUID() == null
                    || maid.isSleeping()
                    || maid.isMaidInSittingPose()
                    || maid.getTarget() != null
                    || maid.getTask() == null
                    || !TaskIdle.UID.equals(maid.getTask().getUid())) {
                return false;
            }

            /*
             * After the durian has been cut, the source ItemEntity may have
             * disappeared because its count was 1. From that point on the
             * session is allowed to finish the eating animation.
             */
            return durianCut
                    || FruitHarvestAccess.isDroppedDurian(target);
        }

        private void tickMove() {
            if (!FruitHarvestAccess.isDroppedDurian(target)) {
                finish();
                return;
            }

            faceTarget();
            maid.setSprinting(false);

            Vec3 targetFeet =
                    GroundApproachHelper.centerOfFeet(
                            groundSpot
                    );

            double goalDistanceSqr =
                    maid.position()
                            .distanceToSqr(
                                    targetFeet
                            );

            boolean nearApproachPoint =
                    goalDistanceSqr <= 1.45D * 1.45D;

            boolean navigationNaturallyFinished =
                    stageTicks > 2
                            && maid.getNavigation()
                                    .isDone();

            if (isWithinReach()
                    && (nearApproachPoint
                    || navigationNaturallyFinished)) {

                if (!GroundApproachHelper.settleNaturallyIfGrounded(
                        maid
                )) {
                    return;
                }

                transition(
                        Stage.SETTLE
                );
                return;
            }

            if (stageTicks > APPROACH_TIMEOUT_TICKS) {
                finish();
                return;
            }

            if (stageTicks % 10 == 1
                    || maid.getNavigation()
                            .isDone()) {

                boolean started =
                        GroundApproachHelper.startSafeNavigation(
                                level,
                                maid,
                                groundSpot,
                                APPROACH_SPEED
                        );

                if (!started) {
                    finish();
                }
            }
        }

        private void tickSettle() {
            GroundApproachHelper.clearCompetingWalkTarget(
                    maid
            );
            maid.getNavigation()
                    .stop();
            maid.setSprinting(false);
            faceTarget();

            if (stageTicks < SETTLE_TICKS) {
                return;
            }

            if (!isWithinReach()
                    || !FruitHarvestAccess.isDroppedDurian(target)) {
                finish();
                return;
            }

            ItemStack cuttingTool =
                    FruitHarvestAccess.findCuttingTool(
                            maid
                    );

            if (cuttingTool.isEmpty()) {
                finish();
                return;
            }

            showInMainHand(
                    cuttingTool
            );
            maid.swing(
                    InteractionHand.MAIN_HAND
            );

            transition(
                    Stage.CHOP
            );
        }

        private void tickChop() {
            GroundApproachHelper.clearCompetingWalkTarget(
                    maid
            );
            maid.getNavigation()
                    .stop();
            maid.setSprinting(false);
            faceTarget();

            if (stageTicks < CHOP_TICKS) {
                return;
            }

            if (!FruitHarvestAccess.hasCuttingTool(maid)
                    || !FruitHarvestAccess.isDroppedDurian(target)) {
                finish();
                return;
            }

            fruit =
                    FruitHarvestAccess.cutOneDroppedDurian(
                            level,
                            maid,
                            target
                    );

            if (fruit.isEmpty()) {
                finish();
                return;
            }

            durianCut = true;

            LOGGER.info(
                    "[WGON FruitDiag] ground-durian cut maid={} flesh={} headNow={} sourceRemaining={}",
                    maid.getDisplayName().getString(),
                    fruit.getHoverName().getString(),
                    maid.getItemBySlot(
                            net.minecraft.world.entity.EquipmentSlot.HEAD
                    ).getHoverName().getString(),
                    target.isAlive()
                            ? target.getItem().getCount()
                            : 0
            );

            showInMainHand(
                    fruit
            );

            transition(
                    Stage.ADMIRE
            );
        }

        private void tickAdmire() {
            lockInPlace();

            if (stageTicks < ADMIRE_TICKS) {
                return;
            }

            maid.startUsingItem(
                    InteractionHand.MAIN_HAND
            );

            transition(
                    Stage.EAT
            );
        }

        private void tickEat() {
            lockInPlace();

            if (stageTicks < EAT_TICKS) {
                return;
            }

            maid.stopUsingItem();

            if (!fruit.isEmpty()) {
                fruit.copy()
                        .finishUsingItem(
                                level,
                                maid
                        );

                maid.getFavorabilityManager()
                        .add(
                                FAVORABILITY_PER_DURIAN
                        );
            }

            finish();
        }

        private void lockInPlace() {
            GroundApproachHelper.clearCompetingWalkTarget(
                    maid
            );
            maid.getNavigation()
                    .stop();
            maid.setSprinting(false);
            maid.setDeltaMovement(
                    Vec3.ZERO
            );
        }

        private boolean isWithinReach() {
            if (!target.isAlive()) {
                return false;
            }

            Vec3 center =
                    target.position();

            double dx =
                    center.x - maid.getX();

            double dz =
                    center.z - maid.getZ();

            double horizontalSqr =
                    dx * dx + dz * dz;

            double vertical =
                    Math.abs(
                            center.y - maid.getY()
                    );

            return horizontalSqr
                    <= MAX_HORIZONTAL_REACH * MAX_HORIZONTAL_REACH
                    && vertical <= MAX_VERTICAL_REACH;
        }

        private void faceTarget() {
            if (!target.isAlive()) {
                return;
            }

            Vec3 center =
                    target.position();

            double dx =
                    center.x - maid.getX();

            double dz =
                    center.z - maid.getZ();

            float yaw =
                    (float) (
                            Mth.atan2(
                                    dz,
                                    dx
                            )
                                    * (180.0D / Math.PI)
                    ) - 90.0F;

            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setXRot(0.0F);
        }

        private void showInMainHand(
                ItemStack stack
        ) {
            maid.stopUsingItem();

            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    stack.copy()
            );

            handOverridden = true;
        }

        private void restoreMainHand() {
            if (!handOverridden) {
                return;
            }

            maid.stopUsingItem();

            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    oldMainHand.copy()
            );

            handOverridden = false;
        }

        private void transition(
                Stage next
        ) {
            stage = next;
            stageTicks = 0;
        }

        private void finish() {
            if (finished) {
                return;
            }

            finished = true;

            GroundApproachHelper.clearCompetingWalkTarget(
                    maid
            );

            maid.getNavigation()
                    .stop();

            maid.setSprinting(false);

            restoreMainHand();

            RESERVED_ITEM_ENTITIES.remove(
                    targetId
            );

            MaidActionLock.release(
                    maid,
                    FruitTastingManager.ACTION_ID
            );

            IdleActivityManager.requestImmediateDecision(
                    maid
            );
        }
    }
}

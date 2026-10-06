package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.integration.tavern.TavernCounterDrinkAccess;
import com.feastwineallgone.integration.tavern.TavernDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idle maid bar-counter drinking behaviour.
 *
 * First stolen drink:
 * - removed from counter
 * - immediately drunk
 * - +10 favorability
 *
 * Optional second stolen drink:
 * - removed from counter
 * - stored in backpack
 * - +10 favorability
 *
 * Therefore stealing two servings successfully gives +20 total.
 */
public final class CounterDrinkManager {

    public static final String ACTION_ID =
            "counter_drink";

    private static final int MIN_NOTICE_TICKS =
            20;

    private static final int MAX_NOTICE_TICKS =
            40;

    private static final int DRINK_VISUAL_TICKS =
            24;

    private static final int SECOND_BOTTLE_SHOW_TICKS =
            12;

    private static final int SESSION_WATCHDOG_TICKS =
            400;

    /**
     * Calm walking speed while approaching a displayed drink.
     * The actual destination is a floor tile beside the bottle, not
     * the bottle block itself.
     */
    private static final double DRINK_APPROACH_SPEED =
            0.65D;

    private static final double DRINK_REACH_DISTANCE_SQR =
            0.90D * 0.90D;

    /**
     * A counter serving may only be removed while the maid is physically
     * beside it.  Target discovery can happen from the configured scan radius,
     * but extraction itself never gets that radius as interaction reach.
     */
    private static final double MAX_DRINK_HORIZONTAL_REACH =
            1.35D;

    private static final double MAX_DRINK_VERTICAL_REACH =
            2.25D;

    private static final int FAVORABILITY_PER_SERVING =
            10;

    private static final Map<UUID, Session> ACTIVE =
            new HashMap<>();

    /**
     * Prevent two maids from targeting the same displayed
     * drink at the same time.
     */
    private static final Map<GlobalPos, UUID> RESERVED_DRINKS =
            new HashMap<>();

    private CounterDrinkManager() {
    }

    /**
     * A configured custom counter is allowed to be any registered block,
     * including functional furniture with its own block entity. Removing such
     * a block must never leave WGON's counter state latched onto the maid.
     *
     * This is deliberately a recovery hook, not a special-case compatibility
     * list: any configured counter receives the same invalidation behaviour.
     */
    @SubscribeEvent
    public static void onBlockBreak(
            BlockEvent.BreakEvent event
    ) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos brokenPos =
                event.getPos();

        boolean configuredCounter =
                TavernCounterDrinkAccess
                        .isAllowedCounterSurface(
                                event.getState()
                        );

        GlobalPos brokenKey =
                GlobalPos.of(
                        level.dimension(),
                        brokenPos
                );

        boolean hadReservation =
                RESERVED_DRINKS.containsKey(
                        brokenKey
                );

        boolean cancelledSession =
                cancelSessionsTouching(
                        level,
                        brokenPos
                );

        purgeStaleReservations(
                level
        );

        if (!configuredCounter
                && !hadReservation
                && !cancelledSession) {
            return;
        }

        /*
         * Re-arm nearby idle decisions immediately. Breaking an experimental
         * rack/counter should behave like "that candidate vanished", not like
         * a permanent AI state change that only a recall talisman can clear.
         */
        int wakeRadius =
                WgonConfig
                        .COUNTER_DRINK_SEARCH_RADIUS
                        .get() + 2;

        double wakeRadiusSqr =
                (double) wakeRadius
                        * wakeRadius;

        Vec3 brokenCenter =
                Vec3.atCenterOf(
                        brokenPos
                );

        for (net.minecraft.world.entity.Entity entity :
                level.getAllEntities()) {

            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }

            if (maid.position()
                    .distanceToSqr(
                            brokenCenter
                    ) > wakeRadiusSqr) {
                continue;
            }

            if (!hasActiveSession(
                    maid
            )) {
                MaidActionLock.release(
                        maid,
                        ACTION_ID
                );
            }

            IdleActivityManager
                    .requestImmediateDecision(
                            maid
                    );
        }
    }

    @SubscribeEvent
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!WgonConfig
                .COUNTER_DRINK_ENABLED
                .get()) {

            finishActiveSessionsInLevel(
                    level
            );

            return;
        }

        /*
         * Session execution remains here.
         *
         * New idle sessions are now selected by IdleActivityManager so
         * FOOD and DRINK can share one fair 50/50 decision.
         */
        tickActiveSessions(
                level
        );
    }

    private static void finishActiveSessionsInLevel(
            ServerLevel level
    ) {
        Iterator<Map.Entry<UUID, Session>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Session session =
                    iterator.next().getValue();

            if (session.level != level) {
                continue;
            }

            session.finish();

            iterator.remove();
        }
    }

    private static void tickActiveSessions(
            ServerLevel level
    ) {
        Iterator<Map.Entry<UUID, Session>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Session> entry =
                    iterator.next();

            Session session =
                    entry.getValue();

            if (session.level != level) {
                continue;
            }

            session.tick();

            if (session.finished) {
                iterator.remove();
            }
        }
    }

    /**
     * Returns currently available displayed Tavern drinks for the maid.
     *
     * IdleActivityManager uses this only for target discovery.
     */
    public static List<BlockPos> findAvailableTargets(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (!MaidBehaviorSettings.isCounterDrinkEnabled(maid)) {
            return List.of();
        }

        if (!WgonConfig
                .COUNTER_DRINK_ENABLED
                .get()) {

            return List.of();
        }

        /*
         * Reservations are leases owned by live sessions. Clean any orphaned
         * entry before discovery so one removed/changed target can never poison
         * future drink selection.
         */
        purgeStaleReservations(
                level
        );

        int searchRadius =
                WgonConfig
                        .COUNTER_DRINK_SEARCH_RADIUS
                        .get();

        List<BlockPos> targets =
                TavernCounterDrinkAccess
                        .findAvailableDrinks(
                                level,
                                maid.blockPosition(),
                                searchRadius
                        );

        List<BlockPos> unreserved =
                new ArrayList<>();

        for (BlockPos target : targets) {
            GlobalPos key =
                    GlobalPos.of(
                            level.dimension(),
                            target
                    );

            UUID owner =
                    RESERVED_DRINKS.get(
                            key
                    );

            if (owner == null
                    || owner.equals(
                    maid.getUUID()
            )) {
                unreserved.add(
                        target.immutable()
                );
            }
        }

        return unreserved;
    }

    /**
     * Starts one bar-counter drinking session after DRINK has won the
     * shared idle-activity roll.
     */
    public static boolean tryStartForMaid(
            ServerLevel level,
            EntityMaid maid,
            List<BlockPos> candidateTargets
    ) {
        UUID maidId =
                maid.getUUID();

        if (ACTIVE.containsKey(
                maidId
        )) {
            return false;
        }

        if (!WgonConfig
                .COUNTER_DRINK_ENABLED
                .get()) {

            return false;
        }

        if (!isEligibleToStart(
                maid
        )) {
            return false;
        }

        if (NightStealManager.hasNightPriority(
                level,
                maid
        )) {
            return false;
        }

        if (MaidActionLock.isLocked(
                maid
        )) {
            return false;
        }

        List<DrinkApproach> targets =
                new ArrayList<>();

        for (BlockPos target : candidateTargets) {
            if (!TavernCounterDrinkAccess
                    .isExtractableDrinkAt(
                            level,
                            target
                    )) {
                continue;
            }

            GlobalPos key =
                    GlobalPos.of(
                            level.dimension(),
                            target
                    );

            UUID reservationOwner =
                    RESERVED_DRINKS.get(
                            key
                    );

            if (reservationOwner != null
                    && !reservationOwner.equals(
                    maidId
            )) {
                continue;
            }

            BlockPos standPos =
                    GroundApproachHelper
                            .findGroundSpotInHorizontalRange(
                                    level,
                                    maid,
                                    target,
                                    1,
                                    1,
                                    true
                            );

            if (standPos != null) {
                targets.add(
                        new DrinkApproach(
                                target.immutable(),
                                standPos
                        )
                );
            }
        }

        if (targets.isEmpty()) {
            return false;
        }

        DrinkApproach picked =
                targets.get(
                        level.random.nextInt(
                                targets.size()
                        )
                );

        BlockPos target =
                picked.drinkPos();

        BlockPos standPos =
                picked.standPos();

        if (!MaidActionLock.tryAcquire(
                maid,
                ACTION_ID
        )) {
            return false;
        }

        GlobalPos reservation =
                GlobalPos.of(
                        level.dimension(),
                        target
                );

        UUID previous =
                RESERVED_DRINKS.putIfAbsent(
                        reservation,
                        maidId
                );

        if (previous != null
                && !previous.equals(
                maidId
        )) {
            MaidActionLock.release(
                    maid,
                    ACTION_ID
            );

            return false;
        }

        Session session =
                new Session(
                        level,
                        maid,
                        target,
                        standPos,
                        reservation
                );

        ACTIVE.put(
                maidId,
                session
        );

        session.begin();

        return true;
    }

    static boolean hasActiveSession(EntityMaid maid) {
        Session session = ACTIVE.get(maid.getUUID());
        return session != null && !session.finished;
    }

    /**
     * Cancel only sessions whose world interaction geometry was changed.
     */
    private static boolean cancelSessionsTouching(
            ServerLevel level,
            BlockPos changedPos
    ) {
        boolean cancelled = false;

        Iterator<Map.Entry<UUID, Session>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Session session =
                    iterator.next().getValue();

            if (session.level != level
                    || !session.touches(
                    changedPos
            )) {
                continue;
            }

            session.finish();
            iterator.remove();
            cancelled = true;
        }

        return cancelled;
    }

    /**
     * Reservations must never outlive the Session that owns them.
     */
    private static void purgeStaleReservations(
            ServerLevel level
    ) {
        Iterator<Map.Entry<GlobalPos, UUID>> iterator =
                RESERVED_DRINKS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<GlobalPos, UUID> entry =
                    iterator.next();

            GlobalPos key =
                    entry.getKey();

            if (!key.dimension().equals(
                    level.dimension()
            )) {
                continue;
            }

            Session ownerSession =
                    ACTIVE.get(
                            entry.getValue()
                    );

            if (ownerSession == null
                    || ownerSession.finished
                    || ownerSession.level != level
                    || !ownerSession.reservation.equals(
                    key
            )) {
                iterator.remove();
            }
        }
    }

    /**
     * Used by night stealing when sleep begins.
     */
    public static void cancelForMaid(
            EntityMaid maid
    ) {
        Session session =
                ACTIVE.remove(
                        maid.getUUID()
                );

        if (session != null) {
            session.finish();
        } else {
            MaidActionLock.release(
                    maid,
                    ACTION_ID
            );
        }
    }

    private static boolean isEligibleToStart(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()) {

            return false;
        }

        if (!MaidBehaviorSettings.isCounterDrinkEnabled(maid)) {
            return false;
        }

        if (maid.getOwnerUUID() == null) {
            return false;
        }

        if (maid.isSleeping()) {
            return false;
        }

        if (maid.isUsingItem()) {
            return false;
        }

        if (maid.getTarget() != null) {
            return false;
        }

        if (maid.getTask() == null) {
            return false;
        }

        return TaskIdle.UID.equals(
                maid.getTask()
                        .getUid()
        );
    }

    /**
     * Once WGON owns the maid, isUsingItem() cannot be used
     * here because WGON itself starts the drinking animation.
     */
    private static boolean isStillIdleEnough(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()) {

            return false;
        }

        if (!MaidBehaviorSettings.isCounterDrinkEnabled(maid)) {
            return false;
        }

        if (maid.isSleeping()) {
            return false;
        }

        if (maid.getTarget() != null) {
            return false;
        }

        if (maid.getTask() == null) {
            return false;
        }

        return TaskIdle.UID.equals(
                maid.getTask()
                        .getUid()
        );
    }

    private record DrinkApproach(
            BlockPos drinkPos,
            BlockPos standPos
    ) {
    }

    private enum Stage {
        MOVE_TO_DRINK,
        NOTICE,
        DRINK_FIRST,
        TAKE_SECOND,
        HOLD_SECOND,
        DONE
    }

    private static final class Session {

        private final ServerLevel level;

        private final EntityMaid maid;

        private final UUID maidId;

        private final BlockPos drinkPos;

        private final BlockPos standPos;

        private final GlobalPos reservation;

        private final ItemStack oldMainHand;

        private final int noticeTicks;

        private final boolean wantsSecondBottle;

        private Stage stage =
                Stage.MOVE_TO_DRINK;

        private int stageTicks =
                0;

        private int totalTicks =
                0;

        private boolean handOverridden =
                false;

        private boolean finished =
                false;

        private ItemStack firstDrink =
                ItemStack.EMPTY;

        private ItemStack secondDrink =
                ItemStack.EMPTY;

        private Session(
                ServerLevel level,
                EntityMaid maid,
                BlockPos drinkPos,
                BlockPos standPos,
                GlobalPos reservation
        ) {
            this.level =
                    level;

            this.maid =
                    maid;

            this.maidId =
                    maid.getUUID();

            this.drinkPos =
                    drinkPos.immutable();

            this.standPos =
                    standPos.immutable();

            this.reservation =
                    reservation;

            this.oldMainHand =
                    maid.getMainHandItem()
                            .copy();

            this.noticeTicks =
                    MIN_NOTICE_TICKS
                            + level.random.nextInt(
                            MAX_NOTICE_TICKS
                                    - MIN_NOTICE_TICKS
                                    + 1
                    );

            int takeTwoChance =
                    WgonConfig
                            .COUNTER_DRINK_TAKE_TWO_CHANCE
                            .get();

            this.wantsSecondBottle =
                    level.random.nextInt(100)
                            < takeTwoChance;
        }

        private void begin() {
            maid.getNavigation()
                    .stop();

            stage =
                    Stage.MOVE_TO_DRINK;

            stageTicks =
                    0;
        }

        private void tick() {
            if (finished) {
                return;
            }

            totalTicks++;
            stageTicks++;

            if (totalTicks
                    > SESSION_WATCHDOG_TICKS) {

                finish();

                return;
            }

            if (NightStealManager.hasNightPriority(
                    level,
                    maid
            )) {

                finish();

                return;
            }

            if (!isStillIdleEnough(
                    maid
            )) {

                finish();

                return;
            }

            switch (stage) {

                case MOVE_TO_DRINK ->
                        tickMoveToDrink();

                case NOTICE ->
                        tickNotice();

                case DRINK_FIRST ->
                        tickDrinkFirst();

                case TAKE_SECOND ->
                        tickTakeSecond();

                case HOLD_SECOND ->
                        tickHoldSecond();

                case DONE ->
                        finish();
            }
        }

        private void tickMoveToDrink() {
            if (!TavernCounterDrinkAccess
                    .isExtractableDrinkAt(
                            level,
                            drinkPos
                    )) {

                finish();

                return;
            }

            if (!GroundApproachHelper
                    .isSafeGroundSpot(
                            level,
                            standPos,
                            drinkPos
                    )) {

                finish();

                return;
            }

            Vec3 target =
                    GroundApproachHelper
                            .centerOfFeet(
                                    standPos
                            );

            maid.setSprinting(
                    false
            );

            faceDrink();

            if (maid.distanceToSqr(
                    target
            ) <= DRINK_REACH_DISTANCE_SQR) {

                /*
                 * Navigation may stop slightly off-centre. Correct the
                 * final half block so low bottles/counters cannot end up
                 * underneath the maid's feet.
                 */
                GroundApproachHelper
                        .settleExactlyOnGroundSpot(
                                maid,
                                standPos
                        );

                transition(
                        Stage.NOTICE
                );

                return;
            }

            int approachTimeout =
                    WgonConfig
                            .COUNTER_DRINK_APPROACH_TIMEOUT_TICKS
                            .get();

            if (stageTicks
                    > approachTimeout) {

                finish();

                return;
            }

            if (stageTicks % 20 == 1
                    || maid.getNavigation()
                    .isDone()) {

                boolean started =
                        GroundApproachHelper
                                .startSafeNavigation(
                                        level,
                                        maid,
                                        standPos,
                                        DRINK_APPROACH_SPEED
                                );

                if (!started) {
                    finish();
                }
            }
        }

        private void tickNotice() {
            if (!TavernCounterDrinkAccess
                    .isExtractableDrinkAt(
                            level,
                            drinkPos
                    )) {

                finish();

                return;
            }

            maid.getNavigation()
                    .stop();

            faceDrink();

            if (stageTicks
                    < noticeTicks) {

                return;
            }

            /*
             * Do not let the notice delay become telekinesis.  The maid may
             * have been nudged away after reaching the counter, so verify real
             * physical reach again immediately before removing the serving.
             */
            if (!isWithinPhysicalDrinkReach()) {
                transition(Stage.MOVE_TO_DRINK);
                return;
            }

            ItemStack taken =
                    TavernCounterDrinkAccess
                            .takeOne(
                                    level,
                                    drinkPos
                            );

            if (taken.isEmpty()) {

                finish();

                return;
            }

            firstDrink =
                    taken.copy();

            firstDrink.setCount(
                    1
            );

            showInMainHand(
                    firstDrink
            );

            // The bottle was taken from the counter in this tick.
            // Animate that pickup before starting the drinking animation.
            maid.swing(
                    InteractionHand.MAIN_HAND
            );

            maid.startUsingItem(
                    InteractionHand.MAIN_HAND
            );

            level.playSound(
                    null,
                    maid.blockPosition(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.NEUTRAL,
                    0.6F,
                    1.1F
            );

            transition(
                    Stage.DRINK_FIRST
            );
        }

        private void tickDrinkFirst() {
            maid.getNavigation()
                    .stop();

            faceDrink();

            /*
             * Stop before Tavern's own 32-tick automatic
             * finishUsingItem() runs.
             */
            if (stageTicks
                    < DRINK_VISUAL_TICKS) {

                return;
            }

            maid.stopUsingItem();

            boolean consumed =
                    TavernDrinkAccess
                            .consumeDetachedDrink(
                                    level,
                                    maid,
                                    firstDrink
                            );

            /*
             * Favorability belongs to a SUCCESSFUL serving.
             *
             * If TavernDrinkAccess ever rejects the drink,
             * no affection is granted.
             */
            if (consumed) {

                maid.getFavorabilityManager()
                        .add(
                                FAVORABILITY_PER_SERVING
                        );
            }

            firstDrink =
                    ItemStack.EMPTY;

            restoreMainHand();

            if (!consumed) {

                finish();

                return;
            }

            if (!wantsSecondBottle) {

                finish();

                return;
            }

            transition(
                    Stage.TAKE_SECOND
            );
        }

        private void tickTakeSecond() {
            maid.getNavigation()
                    .stop();

            faceDrink();

            if (stageTicks < 8) {
                return;
            }

            /*
             * The optional take-away bottle is a second physical interaction.
             * If the maid has moved away after drinking the first serving,
             * cancel the take-away instead of pulling it through the air.
             */
            if (!isWithinPhysicalDrinkReach()) {
                finish();
                return;
            }

            ItemStack preview =
                    TavernCounterDrinkAccess
                            .peekOne(
                                    level,
                                    drinkPos
                            );

            if (preview.isEmpty()) {

                finish();

                return;
            }

            /*
             * Never physically remove the second serving unless
             * it can currently fit into the maid backpack.
             */
            if (!TavernDrinkAccess
                    .canStoreFully(
                            maid,
                            preview
                    )) {

                finish();

                return;
            }

            ItemStack taken =
                    TavernCounterDrinkAccess
                            .takeOne(
                                    level,
                                    drinkPos
                            );

            if (taken.isEmpty()) {

                finish();

                return;
            }

            secondDrink =
                    taken.copy();

            secondDrink.setCount(
                    1
            );

            showInMainHand(
                    secondDrink
            );

            maid.swing(
                    InteractionHand.MAIN_HAND
            );

            level.playSound(
                    null,
                    maid.blockPosition(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.NEUTRAL,
                    0.6F,
                    1.15F
            );

            transition(
                    Stage.HOLD_SECOND
            );
        }

        private void tickHoldSecond() {
            maid.getNavigation()
                    .stop();

            faceDrink();

            if (stageTicks
                    < SECOND_BOTTLE_SHOW_TICKS) {

                return;
            }

            boolean stored =
                    TavernDrinkAccess
                            .storeInBackpack(
                                    maid,
                                    secondDrink
                            );

            if (stored) {

                /*
                 * The second serving has now been successfully
                 * stolen and belongs to the maid.
                 *
                 * User rule:
                 *
                 * one serving = +10
                 * two servings = +20 total
                 */
                maid.getFavorabilityManager()
                        .add(
                                FAVORABILITY_PER_SERVING
                        );

                secondDrink =
                        ItemStack.EMPTY;

                restoreMainHand();

                finish();

                return;
            }

            /*
             * Defensive fallback.
             *
             * Do not grant affection if the second bottle was
             * not successfully stored.
             */
            restoreMainHand();

            secondDrink =
                    ItemStack.EMPTY;

            finish();
        }

        private boolean touches(
                BlockPos changedPos
        ) {
            return drinkPos.equals(
                    changedPos
            )
                    || drinkPos.below().equals(
                    changedPos
            )
                    || standPos.equals(
                    changedPos
            )
                    || standPos.below().equals(
                    changedPos
            );
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
            Vec3 target =
                    Vec3.atCenterOf(
                            drinkPos
                    );

            double dx =
                    target.x - maid.getX();

            double dz =
                    target.z - maid.getZ();

            float yaw =
                    (float) (Mth.atan2(
                            dz,
                            dx
                    ) * (180.0D / Math.PI))
                            - 90.0F;

            /*
             * Horizontal, untwisted presentation: body and head face the
             * same direction and pitch stays at zero. The item-use animation
             * itself brings the bottle to the mouth, so a dedicated look-at
             * point only creates neck/face clipping on custom maid models.
             */
            maid.setYRot(
                    yaw
            );

            maid.setYHeadRot(
                    yaw
            );

            maid.setXRot(
                    0.0F
            );
        }

        private void showInMainHand(
                ItemStack stack
        ) {
            maid.stopUsingItem();

            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    stack.copy()
            );

            handOverridden =
                    true;
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

            handOverridden =
                    false;
        }

        private void transition(
                Stage next
        ) {
            stage =
                    next;

            stageTicks =
                    0;
        }

        private void finish() {
            if (finished) {
                return;
            }

            restoreMainHand();

            maid.stopUsingItem();

            GroundApproachHelper
                    .clearCompetingWalkTarget(
                            maid
                    );

            maid.getNavigation()
                    .stop();

            maid.setSprinting(
                    false
            );

            RESERVED_DRINKS.remove(
                    reservation,
                    maidId
            );

            MaidActionLock.release(
                    maid,
                    ACTION_ID
            );

            stage =
                    Stage.DONE;

            finished =
                    true;
        }
    }
}
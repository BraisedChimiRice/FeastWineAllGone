package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.food.DiningSurfaceRules;
import com.feastwineallgone.food.FavoriteFoodRules;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodBlockService;
import com.feastwineallgone.food.FoodConsumeResult;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Executes autonomous placed-food eating after IdleActivityManager
 * has chosen FOOD for a maid.
 *
 * This class no longer decides whether FOOD or DRINK wins. It only
 * owns the actual food session.
 */
public final class IdleFoodManager {

    public static final String ACTION_ID =
            "idle_food";

    private static final int APPROACH_TIMEOUT_TICKS =
            200;

    /**
     * Dining movement is deliberately calmer than normal maid travel.
     * The maid walks to a floor position beside the food instead of
     * pathing directly onto the food/table block.
     */
    private static final double DINING_APPROACH_SPEED =
            0.55D;

    private static final double DINING_REACH_DISTANCE_SQR =
            1.20D * 1.20D;

    /**
     * A maid may discover food from farther away, but the actual serving
     * interaction is legal from up to roughly 4 blocks horizontally.
     * The larger reach prevents the maid from walking over/around a large
     * table merely to reach the opposite side, while the vertical guard still
     * blocks cross-floor eating.
     */
    private static final double MIN_FOOD_REACH = 0.65D;
    private static final double MAX_FOOD_REACH = 4.20D;
    private static final double MAX_FOOD_VERTICAL_REACH = 2.50D;
    private static final double MIN_FOOD_REACH_SQR =
            MIN_FOOD_REACH * MIN_FOOD_REACH;
    private static final double MAX_FOOD_REACH_SQR =
            MAX_FOOD_REACH * MAX_FOOD_REACH;

    private static final int SETTLE_AT_TABLE_TICKS =
            6;

    /**
     * Before starting the EAT/DRINK animation, let the maid visibly
     * settle and hold a steady forward gaze for a short moment.
     */
    private static final int LOOK_AT_HANDHELD_TICKS =
            8;

    /**
     * Stop the normal item-use countdown before Minecraft/Cookery
     * automatically calls finishUsingItem(). WGON then applies the
     * detached serving itself, which is required for maid-safe tea.
     */
    private static final int HANDHELD_VISUAL_TICKS =
            24;

    private static final int BETWEEN_PLATTER_BITES_TICKS =
            6;

    private static final Map<UUID, Session> ACTIVE =
            new HashMap<>();

    private static final Map<GlobalPos, UUID> RESERVED_FOODS =
            new HashMap<>();

    private IdleFoodManager() {
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

        tickActiveSessions(
                level
        );
    }

    /**
     * Generic idle-state check shared by favorite food/drink presentation.
     * It deliberately does not inspect the per-maid "idle food" switch.
     */
    public static boolean isBaseIdleEligible(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()) {

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

    public static boolean isEligibleToStart(
            EntityMaid maid
    ) {
        return MaidBehaviorSettings.isIdleFoodEnabled(maid)
                && isBaseIdleEligible(maid);
    }

    private static boolean isStillIdleEnough(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()) {

            return false;
        }

        if (!MaidBehaviorSettings.isIdleFoodEnabled(maid)) {
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

    /**
     * Finds the nearest food not already reserved by another maid.
     */
    public static Optional<BlockPos> findNearestAvailableFood(
            ServerLevel level,
            EntityMaid maid,
            int searchRadius
    ) {
        return findNearestAvailableFoodMatching(
                level,
                maid,
                searchRadius,
                pos -> true
        );
    }

    /**
     * Ordinary idle dining deliberately chooses a random valid placed
     * consumable instead of always locking onto the nearest one.  Farming
     * Tales drinks share the same placed-consumable layer as food, so this
     * prevents a nearby cup/bottle from becoming an automatic "see it, drink
     * it" target every time.  Favorites keep their separate priority path.
     */
    public static Optional<BlockPos> findRandomAvailableFood(
            ServerLevel level,
            EntityMaid maid,
            int searchRadius
    ) {
        if (MealProgressManager.isFoodCooldownActive(level, maid)) {
            return Optional.empty();
        }
        BlockPos center = maid.blockPosition();

        int verticalRange = Math.min(
                4,
                Math.max(2, searchRadius)
        );

        BlockPos chosen = null;
        int seen = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-searchRadius, -verticalRange, -searchRadius),
                center.offset(searchRadius, verticalRange, searchRadius)
        )) {
            if (!FoodBlockService.isSupportedFood(level, pos)) {
                continue;
            }

            if (!DiningSurfaceRules.isAllowedForFood(level, pos)) {
                continue;
            }

            GlobalPos reservation = GlobalPos.of(level.dimension(), pos);
            UUID reservationOwner = RESERVED_FOODS.get(reservation);

            if (reservationOwner != null
                    && !reservationOwner.equals(maid.getUUID())) {
                continue;
            }

            /*
             * Reservoir sampling gives every valid target equal probability
             * without allocating a temporary list for every maid scan.
             */
            seen++;
            if (level.random.nextInt(seen) == 0) {
                chosen = pos.immutable();
            }
        }

        return Optional.ofNullable(chosen);
    }

    /**
     * Favorite discovery matches the placed food itself. It intentionally does
     * not depend on one particular table block underneath, so vanilla cake on
     * decorative tables/snack stands and partially eaten cake are still found.
     */
    public static Optional<BlockPos> findNearestAvailableFavoriteFood(
            ServerLevel level,
            EntityMaid maid,
            int searchRadius
    ) {
        if (MealProgressManager.isFoodCooldownActive(level, maid)) {
            return Optional.empty();
        }
        return findNearestAvailableFoodMatching(
                level,
                maid,
                searchRadius,
                pos -> FavoriteFoodRules.isFavoritePlacedFood(
                        level,
                        pos
                )
        );
    }

    private static Optional<BlockPos> findNearestAvailableFoodMatching(
            ServerLevel level,
            EntityMaid maid,
            int searchRadius,
            Predicate<BlockPos> extraFilter
    ) {
        BlockPos center = maid.blockPosition();

        int verticalRange = Math.min(
                4,
                Math.max(2, searchRadius)
        );

        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-searchRadius, -verticalRange, -searchRadius),
                center.offset(searchRadius, verticalRange, searchRadius)
        )) {
            if (!FoodBlockService.isSupportedFood(level, pos)) {
                continue;
            }

            if (!DiningSurfaceRules.isAllowedForFood(level, pos)) {
                continue;
            }

            if (!extraFilter.test(pos)) {
                continue;
            }

            GlobalPos reservation = GlobalPos.of(level.dimension(), pos);
            UUID reservationOwner = RESERVED_FOODS.get(reservation);

            if (reservationOwner != null
                    && !reservationOwner.equals(maid.getUUID())) {
                continue;
            }

            Vec3 foodCenter = Vec3.atCenterOf(pos);
            double distance = maid.position().distanceToSqr(foodCenter);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = pos.immutable();
            }
        }

        return Optional.ofNullable(bestPos);
    }

    /**
     * Finds a standable floor position beside the food.
     *
     * WGON intentionally does not navigate to the food block itself:
     * doing that encourages Minecraft pathfinding to climb onto low
     * tables, platters and drink blocks. The preferred dining spot is
     * on the maid's current floor level, with small vertical fallbacks
     * for stairs/uneven terrain.
     */
    private static Optional<BlockPos> findDiningSpot(
            ServerLevel level,
            EntityMaid maid,
            BlockPos foodPos
    ) {
        return Optional.ofNullable(
                GroundApproachHelper
                        .findGroundSpotInHorizontalRange(
                                level,
                                maid,
                                foodPos,
                                1,
                                4,
                                true
                        )
        );
    }

    private static boolean isWithinFoodReach(
            EntityMaid maid,
            BlockPos foodPos
    ) {
        Vec3 foodCenter = Vec3.atCenterOf(foodPos);
        double dx = foodCenter.x - maid.getX();
        double dz = foodCenter.z - maid.getZ();
        double horizontalSqr = dx * dx + dz * dz;
        double vertical = Math.abs(foodCenter.y - maid.getY());

        return horizontalSqr >= MIN_FOOD_REACH_SQR
                && horizontalSqr <= MAX_FOOD_REACH_SQR
                && vertical <= MAX_FOOD_VERTICAL_REACH;
    }

    /**
     * Called by IdleActivityManager after FOOD has won the idle roll.
     */
    public static boolean tryStartForMaid(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target
    ) {
        UUID maidId =
                maid.getUUID();

        if (ACTIVE.containsKey(
                maidId
        )) {
            return false;
        }

        if (!isEligibleToStart(
                maid
        ) || MealProgressManager.isFoodCooldownActive(level, maid)) {
            return false;
        }

        if (NightStealManager.hasNightPriority(
                level,
                maid
        )) {
            return false;
        }

        if (!FoodBlockService.isSupportedFood(
                level,
                target
        )) {
            return false;
        }

        if (!DiningSurfaceRules.isAllowedForFood(
                level,
                target
        )) {
            return false;
        }

        /*
         * If the maid is already within the legal 4-block idle reach, do not
         * force her to walk around the food to a preselected side. This is
         * especially important for large tables where the pathfinder can pick
         * the opposite edge and briefly climb across the furniture.
         */
        BlockPos diningSpot;

        if (isWithinFoodReach(maid, target)
                && !GroundApproachHelper.isStandingOnForbiddenSurface(level, maid)) {
            diningSpot = maid.blockPosition().immutable();
        } else {
            Optional<BlockPos> diningSpotOptional =
                    findDiningSpot(
                            level,
                            maid,
                            target
                    );

            if (diningSpotOptional.isEmpty()) {
                return false;
            }

            diningSpot =
                    diningSpotOptional
                            .get();
        }

        if (MaidActionLock.isLocked(
                maid
        )) {
            return false;
        }

        GlobalPos reservation =
                GlobalPos.of(
                        level.dimension(),
                        target
                );

        UUID reservationOwner =
                RESERVED_FOODS.get(
                        reservation
                );

        if (reservationOwner != null
                && !reservationOwner.equals(
                maidId
        )) {
            return false;
        }

        if (!MaidActionLock.tryAcquire(
                maid,
                ACTION_ID
        )) {
            return false;
        }

        UUID previous =
                RESERVED_FOODS.putIfAbsent(
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
                        target.immutable(),
                        diningSpot.immutable(),
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
        return ACTIVE.containsKey(maid.getUUID());
    }

    /**
     * Used by the night-steal priority system.
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

    private static void tickActiveSessions(
            ServerLevel level
    ) {
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

    private enum Stage {
        MOVE_TO_FOOD,
        SETTLE_AT_TABLE,
        LOOK_AT_HANDHELD,
        USE_HANDHELD,
        BETWEEN_PLATTER_BITES,
        DONE
    }

    private static final class Session {

        private final ServerLevel level;

        private final EntityMaid maid;

        private final UUID maidId;

        private final BlockPos foodPos;

        private final BlockPos diningPos;

        private final GlobalPos reservation;

        private final ItemStack oldMainHand;

        private Stage stage =
                Stage.MOVE_TO_FOOD;

        private int stageTicks =
                0;

        private int totalTicks =
                0;

        private boolean handOverridden =
                false;

        private boolean finished =
                false;

        private HandheldFoodBlockHandler heldHandler;

        private ItemStack heldServing =
                ItemStack.EMPTY;

        private int platterServingsEaten =
                0;

        private boolean platterEatAllDecisionMade =
                false;

        private boolean platterEatAll =
                false;

        private Session(
                ServerLevel level,
                EntityMaid maid,
                BlockPos foodPos,
                BlockPos diningPos,
                GlobalPos reservation
        ) {
            this.level =
                    level;

            this.maid =
                    maid;

            this.maidId =
                    maid.getUUID();

            this.foodPos =
                    foodPos;

            this.diningPos =
                    diningPos;

            this.reservation =
                    reservation;

            this.oldMainHand =
                    maid.getMainHandItem()
                            .copy();
        }

        private void begin() {
            maid.getNavigation()
                    .stop();

            stage =
                    Stage.MOVE_TO_FOOD;

            stageTicks =
                    0;
        }

        private void tick() {
            if (finished) {
                return;
            }

            totalTicks++;
            stageTicks++;

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

            if (totalTicks
                    > APPROACH_TIMEOUT_TICKS
                    + 20 * 30) {

                finish();
                return;
            }

            switch (stage) {
                case MOVE_TO_FOOD ->
                        tickMoveToFood();

                case SETTLE_AT_TABLE ->
                        tickSettleAtTable();

                case LOOK_AT_HANDHELD ->
                        tickLookAtHandheld();

                case USE_HANDHELD ->
                        tickUseHandheld();

                case BETWEEN_PLATTER_BITES ->
                        tickBetweenPlatterBites();

                case DONE ->
                        finish();
            }
        }

        private void tickMoveToFood() {
            if (!FoodBlockService.isSupportedFood(
                    level,
                    foodPos
            )) {
                finish();
                return;
            }

            Vec3 diningTarget =
                    new Vec3(
                            diningPos.getX() + 0.5D,
                            diningPos.getY(),
                            diningPos.getZ() + 0.5D
                    );

            maid.setSprinting(
                    false
            );

            faceFood();

            if (maid.position()
                    .distanceToSqr(
                            diningTarget
                    ) <= DINING_REACH_DISTANCE_SQR) {

                /*
                 * Do not teleport/snap to diningPos here. The old exact
                 * centering could catch the maid during a pathfinding hop and
                 * look like a tiny sprint or one-second hover, especially on
                 * the immediate favorite-food path.
                 */
                if (!GroundApproachHelper
                        .settleNaturallyIfGrounded(maid)) {
                    return;
                }

                transition(
                        Stage.SETTLE_AT_TABLE
                );

                return;
            }

            if (stageTicks
                    > APPROACH_TIMEOUT_TICKS) {

                finish();
                return;
            }

            if (stageTicks % 10 == 1
                    || maid.getNavigation()
                    .isDone()) {

                boolean started =
                        GroundApproachHelper
                                .startSafeNavigation(
                                        level,
                                        maid,
                                        diningPos,
                                        DINING_APPROACH_SPEED
                                );

                if (!started) {
                    finish();
                }
            }
        }

        private void tickSettleAtTable() {
            maid.getNavigation()
                    .stop();

            maid.setSprinting(
                    false
            );

            faceFood();

            if (stageTicks
                    < SETTLE_AT_TABLE_TICKS) {
                return;
            }

            if (!isWithinFoodReach(maid, foodPos)) {
                finish();
                return;
            }

            beginConsumption();
        }

        private void beginConsumption() {
            if (!isWithinFoodReach(maid, foodPos)) {
                finish();
                return;
            }

            BlockState state =
                    level.getBlockState(
                            foodPos
                    );

            Optional<FoodBlockHandler> optionalHandler =
                    FoodBlockService.findHandler(
                            state
                    );

            if (optionalHandler.isEmpty()) {
                finish();
                return;
            }

            FoodBlockHandler handler =
                    optionalHandler.get();

            if (handler
                    instanceof HandheldFoodBlockHandler handheld) {

                ItemStack serving =
                        handheld.takeOneServing(
                                maid,
                                level,
                                foodPos,
                                state
                        );

                if (serving.isEmpty()) {
                    finish();
                    return;
                }

                heldHandler =
                        handheld;

                heldServing =
                        serving.copy();

                showInMainHand(
                        heldServing
                );

                // The serving has just been detached from the food block.
                // Swing now, not after the maid has already finished eating it.
                maid.swing(
                        InteractionHand.MAIN_HAND
                );

                /*
                 * Do not start eating immediately. Give the maid a
                 * short beat to settle into a natural forward-facing pose.
                 */
                transition(
                        Stage.LOOK_AT_HANDHELD
                );

                return;
            }

            // Immediate-consume foods still get their hand interaction at the
            // moment the source block is touched.
            maid.swing(
                    InteractionHand.MAIN_HAND
            );

            FoodConsumeResult result =
                    FoodBlockService.consumeOne(
                            maid,
                            level,
                            foodPos
                    );

            if (!result.consumed()) {
                finish();
                return;
            }

            finish();
        }

        private void tickLookAtHandheld() {
            maid.getNavigation()
                    .stop();

            maid.setSprinting(
                    false
            );

            faceHeldServing();

            if (stageTicks
                    < LOOK_AT_HANDHELD_TICKS) {
                return;
            }

            if (heldHandler != null
                    && (heldHandler.isPickupOnlyDetachedServing(heldServing)
                    || !heldHandler.shouldAnimateDetachedUse(heldServing))) {

                // Pickup-only bundles leave the world and enter the backpack
                // without ever entering Minecraft's EAT/DRINK use pipeline.
                finishHeldServing();
                return;
            }

            maid.startUsingItem(
                    InteractionHand.MAIN_HAND
            );

            transition(
                    Stage.USE_HANDHELD
            );
        }

        private void tickUseHandheld() {
            maid.getNavigation()
                    .stop();

            maid.setSprinting(
                    false
            );

            faceHeldServing();

            if (stageTicks
                    < HANDHELD_VISUAL_TICKS) {
                return;
            }

            maid.stopUsingItem();

            finishHeldServing();
        }

        private void finishHeldServing() {
            if (heldHandler == null
                    || heldServing.isEmpty()) {

                restoreMainHand();
                finish();
                return;
            }

            boolean consumed =
                    heldHandler.consumeDetachedServing(
                            maid,
                            level,
                            heldServing
                    );

            boolean wasPlatter =
                    heldHandler.isPlatter();

            heldServing =
                    ItemStack.EMPTY;

            heldHandler =
                    null;

            restoreMainHand();

            if (!consumed) {
                finish();
                return;
            }

            if (!wasPlatter) {
                finish();
                return;
            }

            platterServingsEaten++;

            if (!FoodBlockService.isSupportedFood(
                    level,
                    foodPos
            )) {
                finish();
                return;
            }

            BlockState current =
                    level.getBlockState(
                            foodPos
                    );

            Optional<FoodBlockHandler> nextHandler =
                    FoodBlockService.findHandler(
                            current
                    );

            if (nextHandler.isEmpty()
                    || !(nextHandler.get()
                    instanceof HandheldFoodBlockHandler handheld)
                    || !handheld.isPlatter()) {

                finish();
                return;
            }

            int remaining =
                    handheld.getRemainingServings(
                            level,
                            foodPos,
                            current
                    );

            if (remaining <= 0) {
                finish();
                return;
            }

            /*
             * Idle platter rule:
             *
             * serving #1: always eat
             * serving #2: always eat
             * after #2: roll once; default 25% means finish the platter
             */
            if (platterServingsEaten < 2) {
                transition(
                        Stage.BETWEEN_PLATTER_BITES
                );
                return;
            }

            if (!platterEatAllDecisionMade) {
                platterEatAllDecisionMade =
                        true;

                int chance =
                        WgonConfig
                                .PLATTER_FINISH_ALL_CHANCE
                                .get();

                platterEatAll =
                        level.random.nextInt(100)
                                < chance;
            }

            if (!platterEatAll) {
                finish();
                return;
            }

            transition(
                    Stage.BETWEEN_PLATTER_BITES
            );
        }

        private void tickBetweenPlatterBites() {
            maid.getNavigation()
                    .stop();

            faceFood();

            if (stageTicks
                    < BETWEEN_PLATTER_BITES_TICKS) {
                return;
            }

            beginConsumption();
        }

        private void faceFood() {
            float bodyYaw =
                    getFoodFacingYaw();

            /*
             * Keep the whole presentation horizontal and untwisted.
             * Do not hand the head back to LookControl while eating: even
             * a horizontal look-at target can make custom maid models twist
             * or clip. Body and head simply share one yaw, pitch stays zero.
             */
            maid.setYRot(
                    bodyYaw
            );

            maid.setYHeadRot(
                    bodyYaw
            );

            maid.setXRot(
                    0.0F
            );
        }

        private float getFoodFacingYaw() {
            Vec3 foodCenter =
                    Vec3.atCenterOf(
                            foodPos
                    );

            double foodDx =
                    foodCenter.x - maid.getX();

            double foodDz =
                    foodCenter.z - maid.getZ();

            return (float) (Mth.atan2(
                    foodDz,
                    foodDx
            ) * (180.0D / Math.PI))
                    - 90.0F;
        }

        /**
         * Handheld servings use the same horizontal dining gaze as the
         * table itself. The item-use animation already brings the food to
         * the mouth; forcing the head downward only creates clipping.
         */
        private void faceHeldServing() {
            faceFood();
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

            maid.getNavigation()
                    .stop();

            RESERVED_FOODS.remove(
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

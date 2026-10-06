package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared selector for ordinary idle WGON activities.
 *
 * Important rule:
 *
 * FOOD and DRINK keep their historical equal-weight selector. FRUIT is no
 * longer a permanently available third activity type: each ordinary idle
 * decision first rolls the configurable fruit-tasting trigger chance. Only a
 * successful roll may start a fruit cycle.
 */
public final class IdleActivityManager {

    private static final int DISCOVERY_INTERVAL_TICKS =
            10;

    private static final int NO_TARGET_RETRY_TICKS =
            100;

    /**
     * A discovered target can disappear between scan and session start (for
     * example when a custom functional counter is broken). Do not turn that
     * harmless race into a five-second "dead" maid.
     */
    private static final int FAILED_START_RETRY_TICKS =
            20;

    /**
     * When FOOD and DRINK are both present, do not leave a multi-second
     * dead gap between the two behaviours. The action lock still prevents
     * overlap, so this merely arms the next decision shortly after the
     * current action releases control.
     */
    private static final Map<UUID, Long> NEXT_DECISION =
            new HashMap<>();

    private IdleActivityManager() {
    }

    /**
     * World-interaction managers call this when a previously discovered
     * serving/counter disappears. The next discovery pass may decide again
     * immediately instead of inheriting an obsolete cooldown.
     */
    static void requestImmediateDecision(
            EntityMaid maid
    ) {
        NEXT_DECISION.remove(
                maid.getUUID()
        );
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

        long gameTime =
                level.getGameTime();

        if (gameTime % DISCOVERY_INTERVAL_TICKS != 0) {
            return;
        }

        for (Entity entity :
                level.getAllEntities()) {

            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }

            UUID maidId =
                    maid.getUUID();

            boolean fruitDiagEligible = isEligible(
                    level,
                    maid
            );
            FruitTastingManager.diagnosticEligibility(
                    level,
                    maid,
                    gameTime,
                    fruitDiagEligible
            );

            if (!fruitDiagEligible) {
                NEXT_DECISION.put(
                        maidId,
                        gameTime
                                + NO_TARGET_RETRY_TICKS
                );

                continue;
            }

            if (MaidActionLock.isLocked(
                    maid
            )) {
                continue;
            }

            /*
             * Favorites bypass the ordinary random delay and the normal
             * FOOD/DRINK 50/50 roll. Discovery is checked every 10 ticks, so
             * putting a favorite on the table produces a quick reaction.
             */
            Optional<FavoriteAttentionManager.Target> favoriteTarget =
                    MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                            ? FavoriteAttentionManager.findPreferredTarget(
                                    level,
                                    maid
                            )
                            : Optional.empty();

            if (favoriteTarget.isPresent()) {
                boolean favoriteStarted =
                        FavoriteAttentionManager.tryStartOrDispatch(
                                level,
                                maid,
                                favoriteTarget.get()
                        );

                if (favoriteStarted) {
                    NEXT_DECISION.put(
                            maidId,
                            gameTime + mixedActivityHandoffTicks()
                    );

                    continue;
                }

                /*
                 * A visible favorite must not become a permanent traffic
                 * light.  If its own path/serving cannot start this tick,
                 * fall through to the ordinary FOOD/DRINK selector.  This is
                 * especially important when food and a counter drink are both
                 * present: a temporarily unreachable favorite food must not
                 * suppress the reachable drink forever.
                 */
            }

            FavoriteAttentionManager.clearDiscovery(maid);

            long nextDecision =
                    NEXT_DECISION.getOrDefault(
                            maidId,
                            0L
                    );

            if (gameTime < nextDecision) {
                continue;
            }

            int foodRadius =
                    WgonConfig
                            .IDLE_FOOD_SEARCH_RADIUS
                            .get();

            Optional<BlockPos> foodTarget =
                    MaidBehaviorSettings.isIdleFoodEnabled(maid)
                            ? IdleFoodManager.findRandomAvailableFood(
                                    level,
                                    maid,
                                    foodRadius
                            )
                            : Optional.empty();

            List<BlockPos> drinkTargets =
                    MaidBehaviorSettings.isCounterDrinkEnabled(maid)
                            ? CounterDrinkManager.findAvailableTargets(
                                    level,
                                    maid
                            )
                            : List.of();

            /*
             * Fruit is an OCCASIONAL idle event now. Do not even scan the
             * orchard unless this decision passes the configured trigger roll.
             * That prevents nearby ripe plants from behaving like a permanent
             * navigation magnet.
             */
            boolean fruitEnabled =
                    MaidBehaviorSettings.isFruitTastingEnabled(maid);
            boolean fruitRollPassed =
                    fruitEnabled
                            && FruitTastingManager.rollIdleTrigger(level);

            FruitTastingManager.diagnosticDecision(
                    maid,
                    gameTime,
                    fruitEnabled,
                    fruitRollPassed
            );

            if (fruitRollPassed) {
                /*
                 * Fruits Delight compatibility:
                 * dropped whole durians are checked before block fruit. They
                 * can despawn, so an idle maid carrying an axe/sword should
                 * deal with the ground snack first.
                 */
                if (GroundDurianTastingManager.tryStartForMaid(
                        level,
                        maid
                )) {
                    NEXT_DECISION.put(
                            maidId,
                            gameTime + randomDecisionDelay(level)
                    );
                    continue;
                }

                List<BlockPos> fruitTargets =
                        FruitTastingManager.findAvailableTargets(
                                level,
                                maid
                        );

                if (!fruitTargets.isEmpty()
                        && FruitTastingManager.tryStartForMaid(
                                level,
                                maid,
                                fruitTargets
                        )) {
                    NEXT_DECISION.put(
                            maidId,
                            gameTime + randomDecisionDelay(level)
                    );
                    continue;
                }
            }

            boolean hasFood =
                    foodTarget.isPresent();

            boolean hasDrink =
                    !drinkTargets.isEmpty();

            int availableActivities =
                    (hasFood ? 1 : 0)
                            + (hasDrink ? 1 : 0);

            if (availableActivities == 0) {
                /*
                 * A failed fruit roll is still a real idle decision, not a
                 * missing-target error. Respect the normal idle cadence before
                 * rolling again instead of retrying every five seconds.
                 */
                NEXT_DECISION.put(
                        maidId,
                        gameTime + randomDecisionDelay(level)
                );
                continue;
            }

            /* FOOD + DRINK remains the historical exact 50/50 selector. */
            List<IdleChoice> choices =
                    new java.util.ArrayList<>();

            if (hasFood) {
                choices.add(IdleChoice.FOOD);
            }

            if (hasDrink) {
                choices.add(IdleChoice.DRINK);
            }

            boolean started = false;

            while (!choices.isEmpty() && !started) {
                IdleChoice choice = choices.remove(
                        level.random.nextInt(choices.size())
                );

                started = switch (choice) {
                    case FOOD -> IdleFoodManager.tryStartForMaid(
                            level,
                            maid,
                            foodTarget.orElseThrow()
                    );
                    case DRINK -> CounterDrinkManager.tryStartForMaid(
                            level,
                            maid,
                            drinkTargets
                    );
                };
            }

            if (started) {
                int delay = availableActivities >= 2
                        ? mixedActivityHandoffTicks()
                        : randomDecisionDelay(level);

                NEXT_DECISION.put(
                        maidId,
                        gameTime + delay
                );
            } else {
                int retryDelay = availableActivities >= 2
                        ? mixedActivityHandoffTicks()
                        : FAILED_START_RETRY_TICKS;

                NEXT_DECISION.put(
                        maidId,
                        gameTime + retryDelay
                );
            }
        }
    }

    private enum IdleChoice {
        FOOD,
        DRINK
    }

    private static boolean isEligible(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()) {

            return false;
        }

        if (!MaidBehaviorSettings.isMasterEnabled(maid)) {
            return false;
        }

        if (maid.getOwnerUUID() == null) {
            return false;
        }

        /*
         * The owner's sleep window is reserved for NightStealManager.
         * Do not start a fresh FOOD/DRINK idle decision while TLM is
         * trying to put the maid to bed or while midnight stealing is
         * running.
         */
        if (NightStealManager.hasNightPriority(
                level,
                maid
        )) {
            return false;
        }

        if (maid.isSleeping()) {
            return false;
        }

        /*
         * A manually seated maid belongs to SeatedDiningManager.
         * Ordinary roaming food/counter logic must not pull her off chair.
         */
        if (maid.isMaidInSittingPose()) {
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


    private static int mixedActivityHandoffTicks() {
        return Math.max(
                1,
                WgonConfig
                        .IDLE_ACTIVITY_MIXED_HANDOFF_TICKS
                        .get()
        );
    }

    private static int randomDecisionDelay(
            ServerLevel level
    ) {
        int configuredMin =
                WgonConfig
                        .IDLE_ACTIVITY_MIN_ATTEMPT_TICKS
                        .get();

        int configuredMax =
                WgonConfig
                        .IDLE_ACTIVITY_MAX_ATTEMPT_TICKS
                        .get();

        int min =
                Math.min(
                        configuredMin,
                        configuredMax
                );

        int max =
                Math.max(
                        configuredMin,
                        configuredMax
                );

        if (max <= min) {
            return min;
        }

        return min
                + level.random.nextInt(
                max - min + 1
        );
    }
}

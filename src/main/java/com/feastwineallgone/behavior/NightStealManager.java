package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IBarrel;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.integration.tavern.TavernBarrelAccess;
import com.feastwineallgone.integration.tavern.TapCupPresentation;
import com.feastwineallgone.network.WgonNetwork;
import com.feastwineallgone.registry.ModItems;
import com.feastwineallgone.reward.NightGiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NightStealManager {

    public static final String ACTION_ID =
            "night_barrel";

    /**
     * Successful midnight theft gives this amount of
     * Touhou Little Maid favorability.
     *
     * This is awarded once per entire theft session,
     * not once per serving.
     */
    private static final int NIGHT_STEAL_FAVORABILITY =
            20;

    /**
     * Active theft session for each sleeping player.
     */
    private static final Map<UUID, Session>
            ACTIVE_BY_PLAYER =
            new HashMap<>();

    /**
     * Short-lived pre-lock for a sleeping owner whose maid has a real
     * Tavern barrel candidate, but whose theft decision/session has not
     * been created yet.
     *
     * This closes the one-tick race where the player is already asleep
     * while TLM has not quite put the maid into her sleeping pose. Vanilla
     * or another sleep/time mod must not be allowed to skip to morning in
     * that gap.
     */
    private static final Map<UUID, Long>
            PRELOCK_NIGHT =
            new HashMap<>();

    /**
     * Records the Minecraft night in which each player has
     * already received their WGON decision.
     *
     * The entry survives leaving the bed during the same night,
     * so repeatedly entering/leaving bed cannot reroll the event.
     *
     * The entry is explicitly cleared once real daytime is observed.
     * This is important in development/testing too: commands such as
     * /time set night may reuse the same absolute dayTime value, so a
     * pure dayTime/24000 key is not sufficient by itself.
     */
    private static final Map<UUID, Long>
            PROCESSED_NIGHT =
            new HashMap<>();

    /**
     * Morning notices for nights where no actual theft
     * Session was created.
     */
    private static final Map<UUID, MorningNotice>
            PENDING_MORNING_NOTICE =
            new HashMap<>();

    /**
     * Five-second calm ending after the maid returns to bed.
     */
    private static final int POST_BED_WAIT_TICKS =
            100;

    /**
     * Visible drinking time for one serving.
     */
    private static final int DRINK_ANIMATION_TICKS =
            40;

    /**
     * Kaleidoscope Tavern keeps its faucet open for 30 ticks while a
     * serving is being drawn. WGON mirrors that timing before the maid
     * starts drinking from the wooden cup.
     */
    private static final int TAP_FILL_TICKS =
            30;

    /**
     * Brief handoff before the cup becomes a world display under the tap.
     * This makes the sequence read as "take out cup -> place cup -> turn tap"
     * instead of teleporting the mug from nowhere.
     */
    private static final int PLACE_CUP_TICKS =
            8;

    /**
     * Keep the freshly filled purple mug under the faucet for a moment before
     * the maid takes it back into her hand.
     */
    private static final int RETRIEVE_CUP_TICKS =
            8;

    /**
     * If the maid empties the barrel, keep the quality
     * reaction visible briefly before replacing it with the
     * accidental-empty reaction.
     */
    private static final int EMPTY_BUBBLE_DELAY_TICKS =
            20;

    /**
     * Bedside gift presentation timing.
     *
     * The maid holds each gift briefly before tossing it toward the sleeping
     * owner. Multiple gifts are presented one after another.
     */
    private static final int GIFT_THROW_TICKS =
            12;

    private static final int GIFT_CYCLE_TICKS =
            24;

    /**
     * Night theft walks to a floor tile beside the barrel rather than
     * pathing into the barrel itself.
     */
    private static final double NIGHT_APPROACH_SPEED =
            0.75D;

    private static final double NIGHT_APPROACH_REACH_DISTANCE_SQR =
            0.90D * 0.90D;

    /**
     * Actual barrel extraction is allowed only when the maid is standing
     * roughly one to two horizontal blocks from the connected faucet.
     * A small vertical guard prevents the classic "first floor maid steals
     * from basement barrel" trick when X/Z happen to line up.
     */
    private static final double NIGHT_INTERACTION_MIN_HORIZONTAL =
            0.75D;

    private static final double NIGHT_INTERACTION_MAX_HORIZONTAL =
            2.35D;

    private static final double NIGHT_INTERACTION_MAX_VERTICAL =
            2.25D;

    /**
     * If furniture / a closed route defeats normal pathfinding for five
     * seconds, use a safe short-range position correction beside the
     * barrel. TLM itself already uses corrective teleports for follow AI;
     * this keeps a guaranteed night event from stalling forever.
     */
    private static final int NIGHT_PATH_RECOVERY_TICKS =
            30;

    private NightStealManager() {
    }

    private enum MorningNotice {
        QUALITY_TOO_LOW,
        WINE_SURVIVED
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTick(
            TickEvent.LevelTickEvent event
    ) {
        if (!(event.level
                instanceof ServerLevel level)) {

            return;
        }

        if (event.phase
                == TickEvent.Phase.START) {

            /*
             * Sleep/night stealing outranks ordinary idle food and
             * counter drinking. Cancel those actions as soon as the
             * owner enters bed, before the maid has necessarily reached
             * her own sleeping state. This prevents TLM's sleep AI and
             * WGON's dining AI from pulling the maid in opposite
             * directions around a wine cellar / dining area.
             */
            prioritizeNightStealForSleepingOwners(
                    level
            );

            cleanupMorningState(
                    level
            );

            /*
             * Establish a pre-lock before the actual theft decision.
             * The second refresh immediately releases it when the roll
             * says "no theft" or the wine is too poor.
             */
            refreshNightPrelocks(
                    level
            );

            tryStartSessions(
                    level
            );

            refreshNightPrelocks(
                    level
            );

            return;
        }

        if (event.phase
                == TickEvent.Phase.END) {

            tickSessions(
                    level
            );
        }
    }

    /**
     * Called by SleepStatusMixin.
     *
     * Both the short pre-lock and a real active theft session hold the
     * night. The pre-lock is what prevents a sleep/time mod from winning
     * the race one tick before the maid has fully entered bed.
     */
    public static boolean shouldHoldNight(
            List<ServerPlayer> sleepingPlayers
    ) {
        for (ServerPlayer player :
                sleepingPlayers) {

            Session session =
                    ACTIVE_BY_PLAYER.get(
                            player.getUUID()
                    );

            if (session != null
                    && !session.finished) {

                return true;
            }

            Long prelockedNight =
                    PRELOCK_NIGHT.get(
                            player.getUUID()
                    );

            if (prelockedNight != null
                    && prelockedNight.longValue()
                    == getCurrentNightId(
                            player.serverLevel()
                    )) {

                return true;
            }
        }

        return false;
    }

    /**
     * Generic compatibility hook for vanilla sleep and mods that bypass
     * SleepStatus and call ServerLevel#setDayTime directly.
     *
     * While WGON owns the night, Minecraft dayTime is hard-frozen. gameTime
     * deliberately keeps moving so AI, animations, particles and timers
     * continue to work normally.
     */
    public static boolean shouldBlockDayTimeChange(
            ServerLevel level,
            long proposedDayTime
    ) {
        long currentDayTime =
                level.getDayTime();

        long currentTimeOfDay =
                Math.floorMod(
                        currentDayTime,
                        24000L
                );

        /*
         * WGON never turns a daytime world into a forced night. This also
         * keeps compatibility sane with mods that allow daytime sleeping.
         */
        if (currentTimeOfDay
                < 12000L) {
            return false;
        }

        /*
         * Self-prime the pre-lock here as well as on LevelTick.START.
         * If another mod tries to jump time immediately after the player
         * enters bed, this guard can still discover the candidate before
         * that first jump is accepted.
         */
        refreshNightPrelocks(
                level
        );

        if (!isNightHoldActive(
                level
        )) {
            return false;
        }

        /*
         * Hard-freeze Minecraft dayTime for the entire WGON hold window.
         *
         * Previously this method only rejected a jump to morning while
         * allowing the ordinary +1 dayTime update every server tick. That
         * prevented vanilla sleep from skipping the animation, but it did
         * not literally lock the night.
         *
         * gameTime is intentionally NOT frozen. Maid AI, navigation,
         * animations, particles and WGON timers therefore keep ticking
         * normally while the moon itself stays put. Once the pre-lock /
         * theft session ends, setDayTime is allowed again and vanilla (or a
         * compatible sleep mod) may advance to morning normally.
         */
        return proposedDayTime != currentDayTime;
    }

    private static boolean isNightHoldActive(
            ServerLevel level
    ) {
        long currentNight =
                getCurrentNightId(
                        level
                );

        for (ServerPlayer player :
                level.players()) {

            Session session =
                    ACTIVE_BY_PLAYER.get(
                            player.getUUID()
                    );

            if (session != null
                    && !session.finished
                    && session.level
                    == level) {

                return true;
            }

            Long prelockedNight =
                    PRELOCK_NIGHT.get(
                            player.getUUID()
                    );

            if (prelockedNight != null
                    && prelockedNight.longValue()
                    == currentNight) {

                return true;
            }
        }

        return false;
    }

    /**
     * Observation-mode skip button.
     *
     * This skips only the presentation.
     * Remaining drinking is still resolved on the server.
     */
    public static void requestSkip(
            ServerPlayer player
    ) {
        Session session =
                ACTIVE_BY_PLAYER.get(
                        player.getUUID()
                );

        if (session == null
                || session.finished) {

            return;
        }

        if (session.level
                != player.serverLevel()) {

            return;
        }

        session.skipPresentation();
    }

    /**
     * Returns true while the maid's owner is sleeping.
     *
     * During this window ordinary WGON idle activities must stand down,
     * even if the maid herself has not reached her bed yet. Night
     * stealing needs that quiet window so TLM can finish the maid's
     * sleep transition and WGON can then start the barrel session.
     */
    public static boolean hasNightPriority(
            ServerLevel level,
            EntityMaid maid
    ) {
        if (MaidActionLock.isLockedBy(
                maid,
                ACTION_ID
        )) {
            return true;
        }

        if (!MaidBehaviorSettings.isNightStealEnabled(maid)) {
            return false;
        }

        UUID ownerId =
                maid.getOwnerUUID();

        if (ownerId == null) {
            return false;
        }

        ServerPlayer owner =
                level.getServer()
                        .getPlayerList()
                        .getPlayer(ownerId);

        return owner != null
                && owner.isSleeping();
    }

    /**
     * Cancel ordinary WGON idle activities immediately when the owner
     * goes to sleep.
     *
     * This intentionally happens before the maid herself is sleeping.
     * Waiting for maid.isSleeping() was too late: an active food session
     * could keep pathing toward a table while TLM tried to path her back
     * to bed, producing the visible tug-of-war.
     */
    private static void prioritizeNightStealForSleepingOwners(
            ServerLevel level
    ) {
        for (ServerPlayer owner :
                level.players()) {

            if (!owner.isSleeping()) {
                continue;
            }

            AABB nearby =
                    new AABB(
                            owner.blockPosition()
                    ).inflate(
                            32.0D
                    );

            List<EntityMaid> maids =
                    level.getEntitiesOfClass(
                            EntityMaid.class,
                            nearby,
                            maid ->
                                    maid.isAlive()
                                            && !maid.isRemoved()
                                            && MaidBehaviorSettings.isNightStealEnabled(maid)
                                            && owner.getUUID()
                                            .equals(
                                                    maid.getOwnerUUID()
                                            )
                    );

            for (EntityMaid maid :
                    maids) {

                /*
                 * Once night_barrel owns the maid, the pre-emption pass is
                 * finished. Re-running every idle-manager cancellation on
                 * every START tick used to clear the PRAY begging flag before
                 * entity-data synchronization could send it to the client.
                 */
                if (MaidActionLock.isLockedBy(
                        maid,
                        ACTION_ID
                )) {
                    continue;
                }

                /*
                 * Before night_barrel acquires its lock, cancel any ordinary
                 * idle presentation that could fight the bed -> barrel route.
                 */
                FavoriteAttentionManager.cancelForMaid(
                        maid
                );

                FavoriteTableDrinkManager.cancelForMaid(
                        maid
                );

                IdleFoodManager.cancelForMaid(
                        maid
                );

                CounterDrinkManager.cancelForMaid(
                        maid
                );

                SeatedDiningManager.prepareForNight(
                        maid
                );
            }
        }
    }

    /**
     * Refresh transient pre-locks for sleeping owners.
     *
     * A potential candidate is deliberately looser than findEligibleMaids:
     * the maid does not need to be sleeping yet. That is the exact race
     * window this lock exists to protect.
     */
    private static void refreshNightPrelocks(
            ServerLevel level
    ) {
        long currentNight =
                getCurrentNightId(
                        level
                );

        for (ServerPlayer player :
                level.players()) {

            UUID playerId =
                    player.getUUID();

            Session session =
                    ACTIVE_BY_PLAYER.get(
                            playerId
                    );

            if (session != null
                    && !session.finished) {

                PRELOCK_NIGHT.remove(
                        playerId
                );

                continue;
            }

            if (!player.isSleeping()
                    || hasProcessedThisNight(
                    level,
                    playerId
            )) {

                PRELOCK_NIGHT.remove(
                        playerId
                );

                continue;
            }

            if (hasPotentialNightCandidate(
                    level,
                    player
            )) {

                PRELOCK_NIGHT.put(
                        playerId,
                        currentNight
                );

            } else {

                PRELOCK_NIGHT.remove(
                        playerId
                );
            }
        }
    }

    private static boolean hasPotentialNightCandidate(
            ServerLevel level,
            ServerPlayer owner
    ) {
        AABB nearby =
                new AABB(
                        owner.blockPosition()
                ).inflate(
                        32.0D
                );

        int barrelRadius =
                WgonConfig
                        .SEARCH_RADIUS
                        .get();

        List<EntityMaid> maids =
                level.getEntitiesOfClass(
                        EntityMaid.class,
                        nearby,
                        maid ->
                                maid.isAlive()
                                        && !maid.isRemoved()
                                        && MaidBehaviorSettings.isNightStealEnabled(maid)
                                        && owner
                                        .getUUID()
                                        .equals(
                                                maid.getOwnerUUID()
                                        )
                                        && maid.getTask()
                                        != null
                                        && TaskIdle.UID
                                        .equals(
                                                maid.getTask()
                                                        .getUid()
                                        )
                                        && !MaidActionLock
                                        .isLockedByOther(
                                                maid,
                                                ACTION_ID
                                        )
                );

        for (EntityMaid maid :
                maids) {

            if (!TavernBarrelAccess
                    .findValidBarrelStations(
                            level,
                            maid,
                            barrelRadius
                    )
                    .isEmpty()) {

                return true;
            }
        }

        return false;
    }

    /**
     * One Minecraft day = 24000 ticks.
     *
     * All sleep attempts during the same night therefore
     * share the same night ID.
     */
    private static long getCurrentNightId(
            ServerLevel level
    ) {
        return Math.floorDiv(
                level.getDayTime(),
                24000L
        );
    }

    /**
     * Whether this player has already spent their one WGON
     * decision for the current Minecraft night.
     */
    private static boolean hasProcessedThisNight(
            ServerLevel level,
            UUID playerId
    ) {
        Long processedNight =
                PROCESSED_NIGHT.get(
                        playerId
                );

        if (processedNight == null) {
            return false;
        }

        long currentNight =
                getCurrentNightId(
                        level
                );

        return processedNight.longValue()
                == currentNight;
    }

    /**
     * Deliver pending morning notices.
     *
     * PROCESSED_NIGHT is deliberately not cleared merely
     * because the player leaves the bed.
     */
    private static void cleanupMorningState(
            ServerLevel level
    ) {
        for (ServerPlayer player :
                level.players()) {

            UUID playerId =
                    player.getUUID();

            if (player.isSleeping()) {
                continue;
            }

            if (ACTIVE_BY_PLAYER
                    .containsKey(
                            playerId
                    )) {

                continue;
            }

            /*
             * Still nighttime.
             *
             * The player may have simply left the bed manually.
             * Keep the result pending.
             */
            if (!level.isDay()) {
                continue;
            }

            MorningNotice notice =
                    PENDING_MORNING_NOTICE
                            .remove(
                                    playerId
                            );

            if (notice != null) {

                showMorningNotice(
                        player,
                        notice
                );
            }

            /*
             * Daylight rearms WGON for the next night.
             *
             * Do NOT clear this merely because the player left bed:
             * that would allow same-night rerolls.
             *
             * Clearing on daytime also makes repeated test cycles with
             * /time set night reliable, even when Minecraft reuses the
             * same absolute dayTime/night id.
             */
            PROCESSED_NIGHT.remove(
                    playerId
            );
        }
    }

    private static void showMorningNotice(
            ServerPlayer player,
            MorningNotice notice
    ) {
        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        10,
                        60,
                        20
                )
        );

        Component title;
        Component subtitle;

        if (notice
                == MorningNotice.QUALITY_TOO_LOW) {

            title =
                    Component.translatable(
                            "title.feastwineallgone.night.no_action"
                    );

            subtitle =
                    Component.translatable(
                            "subtitle.feastwineallgone.night.quality_too_low"
                    );

        } else {

            title =
                    Component.translatable(
                            "title.feastwineallgone.night.wine_survived"
                    );

            subtitle =
                    Component.translatable(
                            "subtitle.feastwineallgone.night.wine_survived"
                    );
        }

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        subtitle
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        title
                )
        );
    }

    /**
     * Try to create a nighttime theft session.
     *
     * Rule:
     *
     * one decision per player per Minecraft night.
     */
    private static void tryStartSessions(
            ServerLevel level
    ) {
        for (ServerPlayer player :
                level.players()) {

            UUID playerId =
                    player.getUUID();

            if (!player.isSleeping()) {
                continue;
            }

            if (ACTIVE_BY_PLAYER
                    .containsKey(
                            playerId
                    )) {

                continue;
            }

            /*
             * Anti-reroll.
             */
            if (hasProcessedThisNight(
                    level,
                    playerId
            )) {

                continue;
            }

            /*
             * New night's first sleep attempt.
             */
            PENDING_MORNING_NOTICE.remove(
                    playerId
            );

            /*
             * Find idle sleeping maids with actual Tavern
             * barrels containing drink output.
             *
             * IMPORTANT:
             * Do not spend the night's decision before this succeeds.
             * The player can enter sleep one tick before the maid has
             * actually entered her sleeping state. Marking the night too
             * early permanently ate the event on that night.
             */
            List<EligibleMaid> eligible =
                    findEligibleMaids(
                            level,
                            player
                    );

            if (eligible.isEmpty()) {

                /*
                 * The maid may simply be one tick behind the player.
                 * Leave the night unprocessed and allow the next tick to
                 * try again while the player is still asleep.
                 */
                continue;
            }

            /*
             * We now have a real nighttime candidate, so this sleep
             * attempt owns the night's one WGON decision.
             */
            long currentNight =
                    getCurrentNightId(
                            level
                    );

            PROCESSED_NIGHT.put(
                    playerId,
                    currentNight
            );

            /*
             * Remove wine that is still too poor to drink.
             *
             * BrewLevel 1 = 难以下咽.
             */
            List<EligibleMaid> drinkable =
                    filterDrinkableBarrels(
                            level,
                            eligible
                    );

            if (drinkable.isEmpty()) {

                /*
                 * Wine exists, but every candidate is still
                 * too poor to justify getting out of bed.
                 *
                 * No camera.
                 * No thirsty bubble.
                 */
                PENDING_MORNING_NOTICE.put(
                        playerId,
                        MorningNotice.QUALITY_TOO_LOW
                );

                continue;
            }

            /*
             * Only after confirming drinkable wine do we roll
             * the configured theft chance.
             */
            int chance =
                    WgonConfig
                            .STEAL_CHANCE
                            .get();

            int roll =
                    level.random.nextInt(
                            100
                    );

            if (roll
                    >= chance) {

                PENDING_MORNING_NOTICE.put(
                        playerId,
                        MorningNotice.WINE_SURVIVED
                );

                continue;
            }

            EligibleMaid picked =
                    drinkable.get(
                            level.random.nextInt(
                                    drinkable.size()
                            )
                    );

            EntityMaid maid =
                    picked.maid();

            BarrelApproach approach =
                    pickBarrelApproach(
                            level,
                            maid,
                            picked.stations()
                    );

            if (approach == null) {
                /*
                 * Wine exists, but there is literally no safe floor tile
                 * beside any candidate barrel. Do not consume the night.
                 */
                PROCESSED_NIGHT.remove(
                        playerId,
                        currentNight
                );
                continue;
            }

            if (!MaidActionLock
                    .tryAcquire(
                            maid,
                            ACTION_ID
                    )) {

                /*
                 * No theft session actually started. Re-arm this night so
                 * a temporary competing action cannot consume the whole
                 * night's event.
                 */
                PROCESSED_NIGHT.remove(
                        playerId,
                        currentNight
                );

                continue;
            }

            BlockPos barrel =
                    approach.barrelPos();

            /*
             * An actual theft is beginning.
             */
            PENDING_MORNING_NOTICE.remove(
                    playerId
            );

            Session session =
                    new Session(
                            level,
                            player,
                            maid,
                            barrel,
                            approach.interactionPos(),
                            approach.standPos()
                    );

            ACTIVE_BY_PLAYER.put(
                    playerId,
                    session
            );

            session.begin();

            WgonNetwork.sendStart(
                    player,
                    maid.getId()
            );
        }
    }

    private static List<EligibleMaid>
    findEligibleMaids(
            ServerLevel level,
            ServerPlayer owner
    ) {
        AABB nearby =
                new AABB(
                        owner.blockPosition()
                ).inflate(
                        32.0D
                );

        int barrelRadius =
                WgonConfig
                        .SEARCH_RADIUS
                        .get();

        List<EntityMaid> maids =
                level.getEntitiesOfClass(
                        EntityMaid.class,
                        nearby,
                        maid ->
                                maid.isAlive()
                                        && MaidBehaviorSettings.isNightStealEnabled(maid)
                                        && owner
                                        .getUUID()
                                        .equals(
                                                maid.getOwnerUUID()
                                        )
                                        && maid.isSleeping()
                                        && maid.getTask()
                                        != null
                                        && TaskIdle.UID
                                        .equals(
                                                maid.getTask()
                                                        .getUid()
                                        )
                                        && !MaidActionLock
                                        .isLocked(
                                                maid
                                        )
                );

        List<EligibleMaid> result =
                new ArrayList<>();

        for (EntityMaid maid :
                maids) {

            List<TavernBarrelAccess.BarrelStation> stations =
                    TavernBarrelAccess
                            .findValidBarrelStations(
                                    level,
                                    maid,
                                    barrelRadius
                            );

            if (!stations.isEmpty()) {

                result.add(
                        new EligibleMaid(
                                maid,
                                stations
                        )
                );
            }
        }

        return result;
    }

    /**
     * Keep only wine mature enough for nighttime stealing.
     *
     * BrewLevel:
     *
     * 0 = no usable brew state
     * 1 = 难以下咽
     * 2+ = allowed
     */
    private static List<EligibleMaid>
    filterDrinkableBarrels(
            ServerLevel level,
            List<EligibleMaid> source
    ) {
        List<EligibleMaid> result =
                new ArrayList<>();

        for (EligibleMaid candidate :
                source) {

            List<TavernBarrelAccess.BarrelStation> drinkableStations =
                    new ArrayList<>();

            for (TavernBarrelAccess.BarrelStation station :
                    candidate.stations()) {

                int brewLevel =
                        TavernBarrelAccess
                                .getBrewLevel(
                                        level,
                                        station.barrelPos()
                                );

                if (brewLevel
                        <= IBarrel.BREWING_STARTED) {

                    continue;
                }

                drinkableStations.add(
                        station
                );
            }

            if (!drinkableStations.isEmpty()) {

                result.add(
                        new EligibleMaid(
                                candidate.maid(),
                                drinkableStations
                        )
                );
            }
        }

        return result;
    }

    private static void tickSessions(
            ServerLevel level
    ) {
        Iterator<Map.Entry<UUID, Session>>
                iterator =
                ACTIVE_BY_PLAYER
                        .entrySet()
                        .iterator();

        while (iterator.hasNext()) {

            Session session =
                    iterator
                            .next()
                            .getValue();

            if (session.level
                    != level) {

                continue;
            }

            session.tick();

            if (session.finished) {

                iterator.remove();
            }
        }
    }

    /**
     * Prefer a normally reachable barrel-side floor tile. If every valid
     * barrel is temporarily unreachable, keep a geometrically safe tile
     * as a recovery target; the session watchdog can correct the maid to
     * that tile after a short failed path attempt.
     */
    private static BarrelApproach pickBarrelApproach(
            ServerLevel level,
            EntityMaid maid,
            List<TavernBarrelAccess.BarrelStation> stations
    ) {
        List<BarrelApproach> reachable =
                new ArrayList<>();

        List<BarrelApproach> fallback =
                new ArrayList<>();

        for (TavernBarrelAccess.BarrelStation station : stations) {
            if (!TavernBarrelAccess.isValidStation(level, station)) {
                continue;
            }

            BlockPos tap = station.tapPos();

            BlockPos stand =
                    GroundApproachHelper
                            .findGroundSpotInHorizontalRange(
                                    level,
                                    maid,
                                    tap,
                                    1,
                                    2,
                                    true
                            );

            if (stand != null) {
                reachable.add(
                        new BarrelApproach(
                                station,
                                stand
                        )
                );
                continue;
            }

            /*
             * Keep a safe 1-2 block faucet-side tile as a recovery target.
             * The night watchdog may hop across a blocked piece of furniture,
             * but it still lands in the same legitimate interaction ring.
             */
            stand =
                    GroundApproachHelper
                            .findGroundSpotInHorizontalRange(
                                    level,
                                    maid,
                                    tap,
                                    1,
                                    2,
                                    false
                            );

            if (stand != null) {
                fallback.add(
                        new BarrelApproach(
                                station,
                                stand
                        )
                );
            }
        }

        List<BarrelApproach> pool =
                !reachable.isEmpty()
                        ? reachable
                        : fallback;

        if (pool.isEmpty()) {
            return null;
        }

        return pool.get(
                level.random.nextInt(
                        pool.size()
                )
        );
    }

    private record BarrelApproach(
            TavernBarrelAccess.BarrelStation station,
            BlockPos standPos
    ) {
        BlockPos barrelPos() {
            return station.barrelPos();
        }

        BlockPos interactionPos() {
            return station.tapPos();
        }
    }

    private record EligibleMaid(
            EntityMaid maid,
            List<TavernBarrelAccess.BarrelStation> stations
    ) {
    }

    private enum Stage {
        MOVE_TO_BARREL,
        PRAY,
        PLACE_CUP,
        FILL_CUP,
        RETRIEVE_CUP,
        DRINK,
        RETURN_TO_BED,
        GIVE_GIFT,
        WAIT_IN_BED,
        DONE
    }

    private static final class Session {

        private final ServerLevel level;

        private final UUID playerId;

        private final EntityMaid maid;

        private final BlockPos barrelPos;

        private final BlockPos interactionPos;

        private final BlockPos barrelStandPos;

        private final BlockPos bedPos;

        private final ItemStack oldMainHand;

        private final int prayTicks;

        private final int timeoutTicks;

        private Stage stage =
                Stage.MOVE_TO_BARREL;

        private int stageTicks =
                0;

        private int totalTicks =
                0;

        private int drinks =
                0;

        /**
         * Brew quality captured at the beginning of the event.
         */
        private int brewLevelAtStart =
                0;

        /**
         * Number of servings the maid plans to drink.
         */
        private int targetDrinks =
                1;

        private boolean cupShown =
                false;

        /**
         * Temporary ItemDisplay that represents the mug physically resting
         * under the Tavern faucet. It is always removed before the mug returns
         * to the maid's hand or the session ends.
         */
        private Display.ItemDisplay tapCupDisplay =
                null;

        private boolean tapPresentationActive =
                false;

        private boolean tapOpenedByWgon =
                false;

        private boolean qualityBubbleShown =
                false;

        private boolean emptyBubbleShown =
                false;

        private boolean emptyBubblePending =
                false;

        /**
         * True only when this maid personally consumed the serving that made
         * the selected barrel empty. This grants one extra Grand Pool gift.
         */
        private boolean emptiedBarrelByMaid =
                false;

        /**
         * Gifts are rolled exactly once per night-theft session.
         */
        private boolean giftsPrepared =
                false;

        private final List<ItemStack> pendingGifts =
                new ArrayList<>();

        private int nextGiftIndex =
                0;

        /**
         * Midnight theft favorability is granted once for the
         * entire event after the first successfully consumed
         * serving.
         *
         * Five servings still only give +20.
         */
        private boolean favorabilityGranted =
                false;

        /**
         * Post-theft drunk-sleep decision. The roll is made at most once and
         * only after at least one serving was actually consumed.
         */
        private boolean drunkSleepDecisionMade =
                false;

        private boolean drunkSleepPlanned =
                false;

        /**
         * Current TLM chat bubble ID.
         */
        private long currentBubbleId =
                -1L;

        private boolean finished =
                false;

        private Session(
                ServerLevel level,
                ServerPlayer player,
                EntityMaid maid,
                BlockPos barrelPos,
                BlockPos interactionPos,
                BlockPos barrelStandPos
        ) {
            this.level =
                    level;

            this.playerId =
                    player.getUUID();

            this.maid =
                    maid;

            this.barrelPos =
                    barrelPos.immutable();

            this.interactionPos =
                    interactionPos.immutable();

            this.barrelStandPos =
                    barrelStandPos.immutable();

            this.bedPos =
                    maid.getSleepingPos()
                            .orElse(
                                    maid.blockPosition()
                            )
                            .immutable();

            this.oldMainHand =
                    maid.getMainHandItem()
                            .copy();

            this.prayTicks =
                    60
                            + level.random.nextInt(
                            21
                    );

            this.timeoutTicks =
                    WgonConfig
                            .SESSION_TIMEOUT_SECONDS
                            .get()
                            * 20;
        }

        private void begin() {
            forceMaidOutOfBed();

            /*
             * MaidFollowOwnerTaskMixin suppresses TLM's follow-owner task
             * while night_barrel owns the WGON action lock. clearCombatIntent()
             * below also removes any stale WALK/LOOK target left from the
             * tick before this session began.
             */
            /*
             * Capture quality once.
             */
            brewLevelAtStart =
                    TavernBarrelAccess
                            .getBrewLevel(
                                    level,
                                    barrelPos
                            );

            targetDrinks =
                    Math.min(
                            WgonConfig
                                    .MAX_DRINKS
                                    .get(),
                            getDrinkCountForQuality(
                                    brewLevelAtStart
                            )
                    );

            /*
             * Level 1 has already been filtered out before
             * Session creation.
             */
            replaceBubble(
                    "chatbubble.feastwineallgone.night.thirsty"
            );

            clearCombatIntent();

            FavoriteBeggingController.stop(maid);

            maid.getNavigation()
                    .stop();
        }

        private void tick() {
            if (finished) {
                return;
            }

            totalTicks++;
            stageTicks++;

            if (!maid.isAlive()
                    || maid.isRemoved()
                    || totalTicks
                    > timeoutTicks) {

                forceFinish(
                        true
                );

                return;
            }

            ServerPlayer owner =
                    level.getServer()
                            .getPlayerList()
                            .getPlayer(
                                    playerId
                            );

            if (owner == null
                    || !owner.isSleeping()) {

                forceFinish(
                        true
                );

                return;
            }

            clearCombatIntent();

            if (stage != Stage.RETURN_TO_BED
                    && stage != Stage.WAIT_IN_BED
                    && maid.isSleeping()) {
                forceMaidOutOfBed();
            }

            switch (stage) {

                case MOVE_TO_BARREL ->
                        tickMoveToBarrel();

                case PRAY ->
                        tickPray();

                case PLACE_CUP ->
                        tickPlaceCup();

                case FILL_CUP ->
                        tickFillCup();

                case RETRIEVE_CUP ->
                        tickRetrieveCup();

                case DRINK ->
                        tickDrink();

                case RETURN_TO_BED ->
                        tickReturnToBed();

                case GIVE_GIFT ->
                        tickGiveGift();

                case WAIT_IN_BED ->
                        tickWaitInBed();

                case DONE ->
                        finishSession();
            }
        }

        private TavernBarrelAccess.BarrelStation currentStation() {
            return new TavernBarrelAccess.BarrelStation(
                    barrelPos,
                    interactionPos
            );
        }

        /**
         * Final anti-telekinesis gate. X/Z is the primary 1-2 block rule;
         * the vertical guard only prevents aligned floors from interacting.
         */
        private boolean isWithinBarrelInteractionRange() {
            double tapX = interactionPos.getX() + 0.5D;
            double tapZ = interactionPos.getZ() + 0.5D;

            double dx = maid.getX() - tapX;
            double dz = maid.getZ() - tapZ;
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            double tapY = interactionPos.getY() + 0.5D;
            double vertical = Math.abs(maid.getEyeY() - tapY);

            return horizontal >= NIGHT_INTERACTION_MIN_HORIZONTAL
                    && horizontal <= NIGHT_INTERACTION_MAX_HORIZONTAL
                    && vertical <= NIGHT_INTERACTION_MAX_VERTICAL;
        }

        private boolean stationStillValid() {
            return TavernBarrelAccess.isValidStation(
                    level,
                    currentStation()
            );
        }

        private void returnToBarrelApproach() {
            maid.stopUsingItem();
            cancelTapPresentation();
            removeTapCupDisplay();
            hideCup();
            FavoriteBeggingController.stop(maid);
            transition(Stage.MOVE_TO_BARREL);
        }

        private void tickMoveToBarrel() {
            if (!stationStillValid()) {

                transition(
                        Stage.RETURN_TO_BED
                );

                return;
            }

            Vec3 target =
                    GroundApproachHelper
                            .centerOfFeet(
                                    barrelStandPos
                            );

            maid.setSprinting(
                    false
            );

            faceInteractionHorizontally();

            if (GroundApproachHelper
                    .isStandingOnForbiddenSurface(
                            level,
                            maid
                    )
                    && GroundApproachHelper.isSafeGroundSpot(
                    level,
                    barrelStandPos,
                    interactionPos
            )) {
                GroundApproachHelper
                        .settleExactlyOnGroundSpot(
                                maid,
                                barrelStandPos
                        );
            }

            if (maid.distanceToSqr(
                    target
            ) <= NIGHT_APPROACH_REACH_DISTANCE_SQR) {

                GroundApproachHelper
                        .settleExactlyOnGroundSpot(
                                maid,
                                barrelStandPos
                        );

                if (!isWithinBarrelInteractionRange()) {
                    return;
                }

                FavoriteBeggingController.start(maid);

                transition(
                        Stage.PRAY
                );

                return;
            }

            /*
             * If the bed-to-cellar route is blocked by furniture, fences,
             * another entity or a bad path node, do not let a guaranteed
             * night event spend the whole night vibrating in place. After
             * a normal five-second path attempt, recover onto the already
             * validated floor tile beside the barrel.
             */
            if (stageTicks >= NIGHT_PATH_RECOVERY_TICKS
                    && GroundApproachHelper
                    .isSafeGroundSpot(
                            level,
                            barrelStandPos,
                            interactionPos
                    )) {

                GroundApproachHelper
                        .settleExactlyOnGroundSpot(
                                maid,
                                barrelStandPos
                        );

                if (!isWithinBarrelInteractionRange()) {
                    return;
                }

                FavoriteBeggingController.start(maid);

                transition(
                        Stage.PRAY
                );

                return;
            }

            if (stageTicks % 10 == 1
                    || maid.getNavigation()
                    .isDone()) {

                GroundApproachHelper
                        .startSafeNavigation(
                                level,
                                maid,
                                barrelStandPos,
                                NIGHT_APPROACH_SPEED
                        );
            }
        }

        private void tickPray() {
            if (!stationStillValid()) {
                transition(Stage.RETURN_TO_BED);
                return;
            }

            if (!isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            maid.getNavigation()
                    .stop();

            faceInteractionHorizontally();

            /*
             * Use the forced controller rather than a raw setBegging(true).
             * TLM's own temptation AI is allowed to write setBegging(false)
             * every tick; the controller/mixin pair protects WGON's prayer
             * until this stage explicitly releases it.
             */
            FavoriteBeggingController.start(maid);

            if (stageTicks
                    >= prayTicks) {

                FavoriteBeggingController.stop(maid);

                /*
                 * First let the maid visibly take the empty 3D mug out in her
                 * hand. PLACE_CUP then transfers the same visual mug to an
                 * ItemDisplay under the faucet before the tap is opened.
                 */
                showEmptyCup();

                transition(
                        Stage.PLACE_CUP
                );
            }
        }

        private void tickPlaceCup() {
            if (!stationStillValid()) {
                removeTapCupDisplay();
                hideCup();
                transition(Stage.RETURN_TO_BED);
                return;
            }

            if (!isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            maid.getNavigation()
                    .stop();

            faceInteractionHorizontally();

            if (stageTicks
                    < PLACE_CUP_TICKS) {
                return;
            }

            /*
             * A normal Tavern player puts a bottle below the faucet and then
             * opens it. WGON mirrors that staging with a visual-only mug.
             */
            removeTapCupDisplay();
            tapCupDisplay =
                    TapCupPresentation.spawnEmptyCup(
                            level,
                            interactionPos
                    );

            /*
             * The mug is no longer in the maid's hand while it is visibly
             * sitting below the tap. Only after the mug is in place does the
             * maid swing her hand to read as twisting/right-clicking the tap.
             */
            hideCup();

            maid.swing(
                    InteractionHand.MAIN_HAND
            );

            tapOpenedByWgon =
                    TavernBarrelAccess
                            .beginTapPourPresentation(
                                    level,
                                    interactionPos
                            );

            tapPresentationActive =
                    true;

            transition(
                    Stage.FILL_CUP
            );
        }

        private void tickFillCup() {
            if (!stationStillValid()) {
                cancelTapPresentation();
                removeTapCupDisplay();
                hideCup();
                transition(Stage.RETURN_TO_BED);
                return;
            }

            if (!isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            maid.getNavigation()
                    .stop();

            faceInteractionHorizontally();

            if (stageTicks
                    < TAP_FILL_TICKS) {
                return;
            }

            completeTapPresentation();

            /*
             * The liquid appears only after the Tavern-length pour has
             * completed. Keep the full purple mug below the faucet briefly so
             * the player can actually see the result before the maid picks it
             * back up.
             */
            TapCupPresentation.showWine(
                    tapCupDisplay
            );

            transition(
                    Stage.RETRIEVE_CUP
            );
        }

        private void tickRetrieveCup() {
            if (!stationStillValid()) {
                removeTapCupDisplay();
                transition(Stage.RETURN_TO_BED);
                return;
            }

            if (!isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            maid.getNavigation()
                    .stop();

            faceInteractionHorizontally();

            if (stageTicks
                    < RETRIEVE_CUP_TICKS) {
                return;
            }

            removeTapCupDisplay();
            showWineCup();

            transition(
                    Stage.DRINK
            );
        }

        private void tickDrink() {
            if (!stationStillValid()) {
                hideCup();
                transition(Stage.RETURN_TO_BED);
                return;
            }

            if (!isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            maid.getNavigation()
                    .stop();

            faceInteractionHorizontally();

            if (stageTicks
                    == 1) {

                maid.startUsingItem(
                        InteractionHand.MAIN_HAND
                );
            }

            if (stageTicks
                    < DRINK_ANIMATION_TICKS) {

                return;
            }

            maid.stopUsingItem();

            if (!stationStillValid()
                    || !isWithinBarrelInteractionRange()) {
                returnToBarrelApproach();
                return;
            }

            boolean drank =
                    TavernBarrelAccess
                            .stealOneServing(
                                    level,
                                    barrelPos,
                                    maid
                            );

            if (!drank) {

                hideCup();

                transition(
                        Stage.RETURN_TO_BED
                );

                return;
            }

            /*
             * The first successfully consumed serving makes
             * this a successful midnight theft event.
             *
             * Award +20 exactly once.
             */
            grantNightStealFavorabilityOnce();

            drinks++;

            /*
             * Quality reaction occurs once after the first
             * successful drink.
             */
            showQualityBubbleOnce();

            /*
             * TavernBarrelAccess already performs Tavern's
             * original final-serving cleanup.
             */
            if (!barrelHasDrinkRemaining()) {

                emptiedBarrelByMaid =
                        true;

                emptyBubblePending =
                        true;

                hideCup();
                decideDrunkSleepIfNeeded();

                /*
                 * A drunk-sleep night must branch here, before the normal
                 * RETURN_TO_BED stage ever starts. The first implementation
                 * waited until after bedside gift delivery, so the maid visibly
                 * walked all the way back to bed and only then vanished to her
                 * drunk-sleep location.
                 */
                if (tryFinishAsDrunkSleepBeforeBed()) {
                    return;
                }

                transition(
                        Stage.RETURN_TO_BED
                );

                return;
            }

            if (drinks
                    >= targetDrinks) {

                hideCup();
                decideDrunkSleepIfNeeded();

                /*
                 * A drunk-sleep night must branch here, before the normal
                 * RETURN_TO_BED stage ever starts. The first implementation
                 * waited until after bedside gift delivery, so the maid visibly
                 * walked all the way back to bed and only then vanished to her
                 * drunk-sleep location.
                 */
                if (tryFinishAsDrunkSleepBeforeBed()) {
                    return;
                }

                transition(
                        Stage.RETURN_TO_BED
                );

                return;
            }

            /*
             * Continue drinking with the same physical mug. After a serving
             * is swallowed the mug becomes empty in the maid's hand, then the
             * full place -> pour -> retrieve -> drink sequence repeats.
             * Begging, quality reaction and favorability are still one-shot.
             */
            showEmptyCup();

            transition(
                    Stage.PLACE_CUP
            );
        }

        /**
         * Night stealing quantities:
         *
         * Level 1:
         * 难以下咽
         * -> no Session
         *
         * Level 2:
         * 劣质
         * -> 1 serving
         *
         * Level 3:
         * 普通
         * -> 2 servings
         *
         * Level 4:
         * 优质
         * -> 3 servings
         *
         * Level 5:
         * 精酿
         * -> 4 servings
         *
         * Level 6:
         * 典藏
         * -> 5 servings
         */
        private int getDrinkCountForQuality(
                int brewLevel
        ) {
            return switch (brewLevel) {

                case 2 ->
                        1;

                case 3 ->
                        2;

                case 4 ->
                        3;

                case 5 ->
                        4;

                case 6 ->
                        5;

                default ->
                        1;
            };
        }

        /**
         * Use Touhou Little Maid's native favorability system.
         *
         * This function is deliberately idempotent.
         */
        private void grantNightStealFavorabilityOnce() {
            if (favorabilityGranted) {
                return;
            }

            maid.getFavorabilityManager()
                    .add(
                            NIGHT_STEAL_FAVORABILITY
                    );

            favorabilityGranted =
                    true;
        }

        private void showQualityBubbleOnce() {
            if (qualityBubbleShown) {
                return;
            }

            qualityBubbleShown =
                    true;

            String key =
                    switch (brewLevelAtStart) {

                        case 2 ->
                                "chatbubble.feastwineallgone.night.quality.bad";

                        case 3 ->
                                "chatbubble.feastwineallgone.night.quality.common";

                        case 4 ->
                                "chatbubble.feastwineallgone.night.quality.fine";

                        case 5 ->
                                "chatbubble.feastwineallgone.night.quality.crafted";

                        case 6 ->
                                "chatbubble.feastwineallgone.night.quality.vintage";

                        default ->
                                "chatbubble.feastwineallgone.night.quality.bad";
                    };

            replaceBubble(
                    key
            );
        }

        private void tickPendingEmptyBubble() {
            if (!emptyBubblePending
                    || emptyBubbleShown) {

                return;
            }

            if (stageTicks
                    < EMPTY_BUBBLE_DELAY_TICKS) {

                return;
            }

            showEmptyBubbleOnce();
        }

        private void showEmptyBubbleOnce() {
            if (emptyBubbleShown) {
                return;
            }

            emptyBubbleShown =
                    true;

            emptyBubblePending =
                    false;

            replaceBubble(
                    "chatbubble.feastwineallgone.night.empty"
            );
        }

        /**
         * Keep only one WGON chat bubble visible at a time.
         */
        private void replaceBubble(
                String langKey
        ) {
            if (currentBubbleId
                    >= 0L) {

                maid.getChatBubbleManager()
                        .removeChatBubble(
                                currentBubbleId
                        );
            }

            currentBubbleId =
                    maid.getChatBubbleManager()
                            .addTextChatBubble(
                                    langKey
                            );
        }

        private void clearCurrentBubble() {
            if (currentBubbleId
                    < 0L) {

                return;
            }

            maid.getChatBubbleManager()
                    .removeChatBubble(
                            currentBubbleId
                    );

            currentBubbleId =
                    -1L;
        }

        private boolean barrelHasDrinkRemaining() {
            BlockEntity blockEntity =
                    level.getBlockEntity(
                            barrelPos
                    );

            if (!(blockEntity
                    instanceof IBarrel barrel)) {

                return false;
            }

            return !barrel
                    .getOutput()
                    .getStackInSlot(0)
                    .isEmpty();
        }

        private void tickReturnToBed() {
            removeTapCupDisplay();
            hideCup();

            FavoriteBeggingController.stop(maid);

            tickPendingEmptyBubble();

            Vec3 bedCenter =
                    Vec3.atCenterOf(
                            bedPos
                    );

            if (maid.distanceToSqr(
                    bedCenter
            ) <= 3.0D) {

                prepareNightGiftsOnce();

                transition(
                        Stage.GIVE_GIFT
                );

                return;
            }

            /*
             * Navigation watchdog.
             */
            if (stageTicks
                    > 200) {

                moveMaidToBedCenterAwake();
                prepareNightGiftsOnce();

                transition(
                        Stage.GIVE_GIFT
                );

                return;
            }

            if (stageTicks % 20 == 1
                    || maid.getNavigation()
                    .isDone()) {

                maid.getNavigation()
                        .moveTo(
                                bedCenter.x,
                                bedCenter.y,
                                bedCenter.z,
                                1.05D
                        );
            }
        }

        private void tickGiveGift() {
            maid.getNavigation()
                    .stop();

            FavoriteBeggingController.stop(maid);

            removeTapCupDisplay();
            hideCup();

            tickPendingEmptyBubble();

            prepareNightGiftsOnce();

            ServerPlayer owner =
                    getOwner();

            if (pendingGifts.isEmpty()
                    || nextGiftIndex >= pendingGifts.size()) {

                finishGiftStage();
                return;
            }

            ItemStack gift =
                    pendingGifts.get(
                            nextGiftIndex
                    );

            if (stageTicks == 1) {
                faceOwnerHorizontally(owner);

                maid.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        gift.copy()
                );
            }

            if (stageTicks == GIFT_THROW_TICKS) {
                maid.swing(
                        InteractionHand.MAIN_HAND
                );

                dropGiftTowardOwner(
                        gift,
                        owner
                );

                restoreOriginalHand();

                nextGiftIndex++;

                if (nextGiftIndex
                        < pendingGifts.size()) {

                    transition(
                            Stage.GIVE_GIFT
                    );

                    return;
                }
            }

            if (nextGiftIndex >= pendingGifts.size()
                    && stageTicks >= GIFT_CYCLE_TICKS) {

                finishGiftStage();
            }
        }

        private void decideDrunkSleepIfNeeded() {
            if (drunkSleepDecisionMade
                    || drinks <= 0) {
                return;
            }

            drunkSleepDecisionMade = true;
            drunkSleepPlanned =
                    DrunkSleepManager.shouldDrunkSleep(level);
        }

        private void finishGiftStage() {
            restoreOriginalHand();

            /*
             * If the maid was going to pass out elsewhere, that branch already
             * happened immediately after her last drink. Reaching GIVE_GIFT now
             * means this is a normal return-home night.
             */
            putMaidBackToBed();
            transition(
                    Stage.WAIT_IN_BED
            );
        }

        /**
         * Commit the drunk-sleep route before RETURN_TO_BED.
         *
         * Gifts are still preserved, but on a drunk night they are placed by
         * the sleeping owner's bedside without making the maid physically walk
         * back home first.
         */
        private boolean tryFinishAsDrunkSleepBeforeBed() {
            if (!drunkSleepPlanned
                    || drinks <= 0
                    || !maid.isAlive()
                    || maid.isRemoved()) {
                return false;
            }

            restoreOriginalHand();
            prepareNightGiftsOnce();

            if (!DrunkSleepManager.tryStartAfterNightSteal(
                    level,
                    maid,
                    barrelPos,
                    interactionPos,
                    barrelStandPos
            )) {
                /*
                 * No safe drunk-sleep candidate survived validation. Commit to
                 * the ordinary bed route for this night instead of trying again
                 * after the maid has already returned home.
                 */
                drunkSleepPlanned = false;
                return false;
            }

            dropAllPendingGiftsAtBedside();
            clearCurrentBubble();
            finishSession();
            return true;
        }

        private void tickWaitInBed() {
            maid.getNavigation()
                    .stop();

            FavoriteBeggingController.stop(maid);

            removeTapCupDisplay();
            hideCup();

            tickPendingEmptyBubble();

            if (!maid.isSleeping()) {

                putMaidBackToBed();
            }

            if (stageTicks
                    >= POST_BED_WAIT_TICKS) {

                finishSession();
            }
        }

        /**
         * Player skips only the presentation.
         *
         * Remaining servings are still settled server-side.
         */
        private void skipPresentation() {
            if (finished) {
                return;
            }

            if (stage
                    == Stage.MOVE_TO_BARREL
                    || stage
                    == Stage.PRAY
                    || stage
                    == Stage.PLACE_CUP
                    || stage
                    == Stage.FILL_CUP
                    || stage
                    == Stage.RETRIEVE_CUP
                    || stage
                    == Stage.DRINK) {

                resolveRemainingDrinksImmediately();
            }

            cancelTapPresentation();
            removeTapCupDisplay();

            hideCup();

            FavoriteBeggingController.stop(maid);

            maid.getNavigation()
                    .stop();

            clearCurrentBubble();

            prepareNightGiftsOnce();

            if (tryFinishAsDrunkSleepBeforeBed()) {
                return;
            }

            dropAllPendingGiftsImmediately();

            if (maid.isAlive()
                    && !maid.isRemoved()) {

                restoreOriginalHand();
                putMaidBackToBed();
            }

            finishSession();
        }

        /**
         * Skip mode follows the same quality-based quantity.
         *
         * Favorability still increases because the theft itself
         * still physically happens on the server.
         *
         * Even if several servings are resolved here, the
         * idempotent favorability method grants only +20 total.
         */
        private void resolveRemainingDrinksImmediately() {
            while (drinks
                    < targetDrinks) {

                boolean drank =
                        TavernBarrelAccess
                                .stealOneServingSilently(
                                        level,
                                        barrelPos,
                                        maid
                                );

                if (!drank) {
                    break;
                }

                /*
                 * A successful invisible/server-side drink is
                 * still a successful midnight theft.
                 */
                grantNightStealFavorabilityOnce();

                drinks++;

                if (!barrelHasDrinkRemaining()) {
                    emptiedBarrelByMaid =
                            true;
                    break;
                }
            }

            decideDrunkSleepIfNeeded();
        }

        private void prepareNightGiftsOnce() {
            if (giftsPrepared) {
                return;
            }

            giftsPrepared =
                    true;

            if (drinks <= 0) {
                return;
            }

            pendingGifts.addAll(
                    NightGiftManager.prepareGifts(
                            brewLevelAtStart,
                            emptiedBarrelByMaid,
                            level.random
                    )
            );
        }

        private void dropAllPendingGiftsImmediately() {
            ServerPlayer owner =
                    getOwner();

            while (nextGiftIndex
                    < pendingGifts.size()) {

                ItemStack gift =
                        pendingGifts.get(
                                nextGiftIndex
                        );

                dropGiftTowardOwner(
                        gift,
                        owner
                );

                nextGiftIndex++;
            }

            restoreOriginalHand();
        }

        /**
         * Drunk nights no longer take the maid back to the owner's bed just to
         * perform the gift animation. Keep the reward semantics by placing the
         * already-rolled gifts beside the sleeping owner instead.
         */
        private void dropAllPendingGiftsAtBedside() {
            ServerPlayer owner = getOwner();

            BlockPos anchorPos =
                    owner != null
                            ? owner.getSleepingPos()
                                    .orElse(owner.blockPosition())
                            : bedPos;

            Vec3 base =
                    Vec3.atCenterOf(anchorPos)
                            .add(0.0D, 0.65D, 0.0D);

            while (nextGiftIndex
                    < pendingGifts.size()) {

                ItemStack gift =
                        pendingGifts.get(
                                nextGiftIndex
                        );

                if (gift != null
                        && !gift.isEmpty()) {

                    ItemEntity dropped =
                            new ItemEntity(
                                    level,
                                    base.x
                                            + (level.random.nextDouble() - 0.5D)
                                            * 0.35D,
                                    base.y,
                                    base.z
                                            + (level.random.nextDouble() - 0.5D)
                                            * 0.35D,
                                    gift.copy()
                            );

                    dropped.setDeltaMovement(
                            (level.random.nextDouble() - 0.5D) * 0.05D,
                            0.08D,
                            (level.random.nextDouble() - 0.5D) * 0.05D
                    );

                    level.addFreshEntity(dropped);
                }

                nextGiftIndex++;
            }

            restoreOriginalHand();
        }

        private void dropGiftTowardOwner(
                ItemStack gift,
                ServerPlayer owner
        ) {
            if (gift == null
                    || gift.isEmpty()
                    || !maid.isAlive()
                    || maid.isRemoved()) {

                return;
            }

            Vec3 spawn =
                    maid.position()
                            .add(
                                    0.0D,
                                    Math.max(
                                            0.55D,
                                            maid.getBbHeight()
                                                    * 0.55D
                                    ),
                                    0.0D
                            );

            ItemEntity dropped =
                    new ItemEntity(
                            level,
                            spawn.x,
                            spawn.y,
                            spawn.z,
                            gift.copy()
                    );

            Vec3 target =
                    owner != null
                            ? owner.position()
                                    .add(
                                            0.0D,
                                            0.45D,
                                            0.0D
                                    )
                            : Vec3.atCenterOf(
                                    bedPos
                            );

            Vec3 delta =
                    target.subtract(
                            spawn
                    );

            Vec3 horizontal =
                    new Vec3(
                            delta.x,
                            0.0D,
                            delta.z
                    );

            if (horizontal.lengthSqr()
                    > 1.0E-6D) {

                horizontal =
                        horizontal
                                .normalize()
                                .scale(
                                        0.22D
                                );
            }

            dropped.setDeltaMovement(
                    horizontal.x,
                    0.18D,
                    horizontal.z
            );

            level.addFreshEntity(
                    dropped
            );
        }

        private ServerPlayer getOwner() {
            return level.getServer()
                    .getPlayerList()
                    .getPlayer(
                            playerId
                    );
        }

        private void faceOwnerHorizontally(
                ServerPlayer owner
        ) {
            Vec3 target =
                    owner != null
                            ? owner.position()
                            : Vec3.atCenterOf(
                                    bedPos
                            );

            double dx =
                    target.x - maid.getX();

            double dz =
                    target.z - maid.getZ();

            if (dx * dx + dz * dz
                    < 1.0E-6D) {
                return;
            }

            float yaw =
                    (float) (Mth.atan2(
                            dz,
                            dx
                    ) * (180.0D / Math.PI))
                            - 90.0F;

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

        private void restoreOriginalHand() {
            maid.stopUsingItem();

            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    oldMainHand.copy()
            );

            cupShown =
                    false;
        }

        private void moveMaidToBedCenterAwake() {
            maid.getNavigation()
                    .stop();

            maid.stopSleeping();

            maid.moveTo(
                    bedPos.getX()
                            + 0.5D,
                    bedPos.getY()
                            + 0.8D,
                    bedPos.getZ()
                            + 0.5D,
                    maid.getYRot(),
                    maid.getXRot()
            );

            maid.setDeltaMovement(
                    Vec3.ZERO
            );

            maid.fallDistance =
                    0.0F;
        }

        private void putMaidBackToBed() {
            maid.getNavigation()
                    .stop();

            double x =
                    bedPos.getX()
                            + 0.5D;

            double y =
                    bedPos.getY()
                            + 0.8D;

            double z =
                    bedPos.getZ()
                            + 0.5D;

            maid.moveTo(
                    x,
                    y,
                    z,
                    maid.getYRot(),
                    maid.getXRot()
            );

            maid.setDeltaMovement(
                    Vec3.ZERO
            );

            maid.fallDistance =
                    0.0F;

            maid.startSleeping(
                    bedPos
            );
        }

        private void transition(
                Stage next
        ) {
            stage =
                    next;

            stageTicks =
                    0;
        }

        private void completeTapPresentation() {
            if (!tapPresentationActive) {
                return;
            }

            TavernBarrelAccess
                    .finishTapPourPresentation(
                            level,
                            interactionPos,
                            tapOpenedByWgon,
                            true
                    );

            tapPresentationActive =
                    false;

            tapOpenedByWgon =
                    false;
        }

        private void cancelTapPresentation() {
            if (!tapPresentationActive) {
                return;
            }

            TavernBarrelAccess
                    .finishTapPourPresentation(
                            level,
                            interactionPos,
                            tapOpenedByWgon,
                            false
                    );

            tapPresentationActive =
                    false;

            tapOpenedByWgon =
                    false;
        }

        private void showEmptyCup() {
            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(
                            ModItems
                                    .WOODEN_CUP
                                    .get()
                    )
            );

            cupShown =
                    true;
        }

        private void showWineCup() {
            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    new ItemStack(
                            ModItems
                                    .WOODEN_CUP_WINE
                                    .get()
                    )
            );

            cupShown =
                    true;
        }

        private void removeTapCupDisplay() {
            TapCupPresentation.remove(
                    tapCupDisplay
            );

            tapCupDisplay =
                    null;
        }

        private void hideCup() {
            if (!cupShown) {
                return;
            }

            maid.stopUsingItem();

            maid.setItemInHand(
                    InteractionHand.MAIN_HAND,
                    oldMainHand.copy()
            );

            cupShown =
                    false;
        }

        private void clearCombatIntent() {
            maid.setTarget(
                    null
            );

            maid.setAggressive(
                    false
            );

            /*
             * Remove any stale follow-owner destination. The follow task is
             * suppressed by mixin while night_barrel owns the action lock.
             */
            maid.getBrain()
                    .eraseMemory(
                            MemoryModuleType
                                    .ATTACK_TARGET
                    );

            maid.getBrain()
                    .eraseMemory(
                            MemoryModuleType
                                    .WALK_TARGET
                    );

            maid.getBrain()
                    .eraseMemory(
                            MemoryModuleType
                                    .LOOK_TARGET
                    );
        }

        private void faceInteractionHorizontally() {
            Vec3 center =
                    Vec3.atCenterOf(
                            interactionPos
                    );

            double dx =
                    center.x - maid.getX();

            double dz =
                    center.z - maid.getZ();

            float yaw =
                    (float) (Mth.atan2(
                            dz,
                            dx
                    ) * (180.0D / Math.PI))
                            - 90.0F;

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

        private void forceMaidOutOfBed() {
            maid.stopSleeping();
            maid.getNavigation().stop();

            BlockPos wakeSpot =
                    GroundApproachHelper
                            .findGroundSpot(
                                    level,
                                    maid,
                                    bedPos,
                                    false
                            );

            if (wakeSpot != null) {
                GroundApproachHelper
                        .settleExactlyOnGroundSpot(
                                maid,
                                wakeSpot
                        );
            }

            maid.setDeltaMovement(Vec3.ZERO);
            maid.fallDistance = 0.0F;
            maid.setXRot(0.0F);
        }

        private void forceFinish(
                boolean returnHome
        ) {
            cancelTapPresentation();
            removeTapCupDisplay();
            hideCup();

            FavoriteBeggingController.stop(maid);

            maid.getNavigation()
                    .stop();

            if (returnHome
                    && maid.isAlive()
                    && !maid.isRemoved()) {

                prepareNightGiftsOnce();
                dropAllPendingGiftsImmediately();
                restoreOriginalHand();
                putMaidBackToBed();
            }

            finishSession();
        }

        private void finishSession() {
            if (finished) {
                return;
            }

            cancelTapPresentation();
            removeTapCupDisplay();
            hideCup();

            FavoriteBeggingController.stop(maid);

            maid.getNavigation()
                    .stop();

            MaidActionLock.release(
                    maid,
                    ACTION_ID
            );

            stage =
                    Stage.DONE;

            finished =
                    true;

            ServerPlayer owner =
                    level.getServer()
                            .getPlayerList()
                            .getPlayer(
                                    playerId
                            );

            if (owner != null) {

                WgonNetwork.sendEnd(
                        owner
                );
            }
        }
    }
}
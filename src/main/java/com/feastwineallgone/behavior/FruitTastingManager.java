package com.feastwineallgone.behavior;

import com.mojang.logging.LogUtils;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.integration.compat.FruitHarvestAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Idle fruit tasting.
 *
 * Fruit tasting is an occasional idle CYCLE, not a permanently available
 * activity type. IdleActivityManager rolls the configurable trigger chance;
 * when it succeeds, this manager plans one, two, or every distinct fruit type
 * found at cycle start. Physical block count never increases a variety's weight.
 *
 * Each successfully eaten fruit still grants the fixed +10 favorability reward.
 * One WGON action lock is held for the whole cycle so TLM follow-owner movement
 * cannot tug the maid away between fruit varieties.
 */
public final class FruitTastingManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Temporary first-party diagnostics for the grape/melon compatibility
     * investigation. Logs are deliberately throttled per maid so a large
     * orchard does not turn latest.log into a fruit encyclopedia.
     */
    private static final Map<UUID, Long> NEXT_DIAGNOSTIC_LOG =
            new HashMap<>();

    public static final String ACTION_ID =
            "fruit_tasting";

    private static final double APPROACH_SPEED =
            0.65D;

    private static final int APPROACH_TIMEOUT_TICKS =
            240;

    private static final int SETTLE_TICKS =
            8;

    private static final int ADMIRE_FRUIT_TICKS =
            10;

    private static final int EAT_VISUAL_TICKS =
            24;

    private static final int SESSION_WATCHDOG_TICKS =
            20 * 30;

    private static final double MAX_HORIZONTAL_REACH =
            3.35D;

    private static final int FIXED_FAVORABILITY_PER_FRUIT =
            10;

    private static final int FAILED_TARGET_RETRY_TICKS =
            20 * 5;

    private static final double MAX_VERTICAL_REACH =
            4.5D;

    private static final Map<UUID, Session> ACTIVE =
            new HashMap<>();

    private static final Set<GlobalPos> RESERVED_FRUITS =
            new HashSet<>();

    /**
     * A favorite fruit receives one priority pick. After that successful snack,
     * the next fruit session deliberately uses the full random pool once. This
     * prevents an apple orchard from turning into an automatic apple vacuum.
     */
    private static final Set<UUID> FORCE_RANDOM_NEXT_TASTING =
            new HashSet<>();

    /**
     * Short per-maid quarantine for a fruit whose approach failed. Without this,
     * several fruits around an awkward trapdoor/high bush can repeatedly roll
     * the same bad target and look like indecision.
     */
    private static final Map<UUID, Map<GlobalPos, Long>> FAILED_TARGET_UNTIL =
            new HashMap<>();

    private FruitTastingManager() {
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
                ACTIVE.entrySet().iterator();

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

    public static void diagnosticEligibility(
            ServerLevel level,
            EntityMaid maid,
            long gameTime,
            boolean eligible
    ) {
        if (WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE.get() <= 0) {
            return;
        }

        long next = NEXT_DIAGNOSTIC_LOG.getOrDefault(maid.getUUID(), 0L);
        if (gameTime < next) {
            return;
        }

        NEXT_DIAGNOSTIC_LOG.put(maid.getUUID(), gameTime + 100L);

        String task = maid.getTask() == null
                ? "null"
                : String.valueOf(maid.getTask().getUid());

        LOGGER.info(
                "[WGON FruitDiag] eligibility maid={} eligible={} master={} fruitEnabled={} task={} sleeping={} sitting={} using={} target={} locked={} pos={}",
                maid.getDisplayName().getString(),
                eligible,
                MaidBehaviorSettings.isMasterEnabled(maid),
                MaidBehaviorSettings.isFruitTastingEnabled(maid),
                task,
                maid.isSleeping(),
                maid.isMaidInSittingPose(),
                maid.isUsingItem(),
                maid.getTarget() != null,
                MaidActionLock.isLocked(maid),
                maid.blockPosition()
        );
    }

    public static void diagnosticDecision(
            EntityMaid maid,
            long gameTime,
            boolean enabled,
            boolean rollPassed
    ) {
        LOGGER.info(
                "[WGON FruitDiag] idle-decision maid={} gameTime={} enabled={} triggerChance={} rollPassed={}",
                maid.getDisplayName().getString(),
                gameTime,
                enabled,
                WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE.get(),
                rollPassed
        );
    }

    public static boolean rollIdleTrigger(
            ServerLevel level
    ) {
        int chance = Mth.clamp(
                WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE.get(),
                0,
                100
        );

        return chance >= 100
                || (chance > 0 && level.random.nextInt(100) < chance);
    }

    public static List<BlockPos> findAvailableTargets(
            ServerLevel level,
            EntityMaid maid
    ) {
        List<BlockPos> targets = new ArrayList<>();

        if (!MaidBehaviorSettings.isFruitTastingEnabled(maid)
                || !isIdleEnough(maid)) {
            return targets;
        }

        int radius = WgonConfig.FRUIT_TASTING_SEARCH_RADIUS.get();
        int verticalRadius = Math.min(radius, 6);
        BlockPos origin = maid.blockPosition();
        int previewedTargets = 0;
        int groundFailures = 0;
        int interestingBlocksSeen = 0;

        /*
         * Roll once per fruit-discovery pass, not once per golden apple block.
         * A large golden orchard therefore does not turn a 25% setting into an
         * almost guaranteed theft simply because many apples are visible.
         */
        int goldenChance = WgonConfig.GOLDEN_APPLE_STEAL_CHANCE.get();
        boolean allowGoldenAppleThisScan = goldenChance >= 100
                || (goldenChance > 0 && level.random.nextInt(100) < goldenChance);

        BlockPos min = origin.offset(-radius, -verticalRadius, -radius);
        BlockPos max = origin.offset(radius, verticalRadius, radius);

        for (BlockPos mutable : BlockPos.betweenClosed(min, max)) {
            int dx = mutable.getX() - origin.getX();
            int dz = mutable.getZ() - origin.getZ();

            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }

            BlockPos pos = mutable.immutable();
            GlobalPos reservation = GlobalPos.of(level.dimension(), pos);

            if (RESERVED_FRUITS.contains(reservation)
                    || isTemporarilyFailed(level, maid, reservation)) {
                continue;
            }

            if (FruitHarvestAccess.isGoldenAppleTarget(level, pos)
                    && !allowGoldenAppleThisScan) {
                continue;
            }

            BlockStateProbe probe = describeInterestingBlock(level, pos);
            if (probe.interesting()) {
                interestingBlocksSeen++;
            }

            ItemStack preview = FruitHarvestAccess
                    .previewOne(level, maid, pos)
                    .orElse(ItemStack.EMPTY);

            if (probe.interesting()) {
                LOGGER.info(
                        "[WGON FruitDiag] probe maid={} block={} pos={} preview={} cutter={}",
                        maid.getDisplayName().getString(),
                        probe.blockId(),
                        pos,
                        itemId(preview),
                        itemId(FruitHarvestAccess.findCuttingTool(maid))
                );
            }

            if (preview.isEmpty()) {
                continue;
            }

            previewedTargets++;

            BlockPos groundSpot = GroundApproachHelper.findFruitGroundSpot(
                    level,
                    maid,
                    pos,
                    true
            );

            if (groundSpot == null) {
                groundFailures++;
                LOGGER.info(
                        "[WGON FruitDiag] no-ground maid={} targetBlock={} targetPos={} preview={}",
                        maid.getDisplayName().getString(),
                        probe.blockId(),
                        pos,
                        itemId(preview)
                );
                markTemporarilyFailed(level, maid, reservation);
                continue;
            }

            targets.add(pos);
        }

        LOGGER.info(
                "[WGON FruitDiag] scan-summary maid={} origin={} radius={} interestingSeen={} previewed={} groundFailures={} reachableTargets={}",
                maid.getDisplayName().getString(),
                origin,
                radius,
                interestingBlocksSeen,
                previewedTargets,
                groundFailures,
                targets.size()
        );

        return targets;
    }

    public static boolean tryStartForMaid(
            ServerLevel level,
            EntityMaid maid,
            List<BlockPos> candidates
    ) {
        if (candidates == null
                || candidates.isEmpty()
                || !MaidBehaviorSettings.isFruitTastingEnabled(maid)
                || !isIdleEnough(maid)
                || ACTIVE.containsKey(maid.getUUID())) {
            LOGGER.info(
                    "[WGON FruitDiag] start-rejected maid={} candidates={} fruitEnabled={} idleEnough={} alreadyActive={}",
                    maid.getDisplayName().getString(),
                    candidates == null ? -1 : candidates.size(),
                    MaidBehaviorSettings.isFruitTastingEnabled(maid),
                    isIdleEnough(maid),
                    ACTIVE.containsKey(maid.getUUID())
            );
            return false;
        }

        UUID maidId = maid.getUUID();
        List<TargetCandidate> viable = buildViableCandidates(
                level,
                maid,
                candidates
        );

        if (viable.isEmpty()) {
            LOGGER.info(
                    "[WGON FruitDiag] no-viable maid={} candidates={}",
                    maid.getDisplayName().getString(),
                    candidates.size()
            );
            return false;
        }

        boolean forceRandom = FORCE_RANDOM_NEXT_TASTING.contains(maidId);
        CyclePlan plan = buildCyclePlan(level, viable, forceRandom);

        if (plan.types().isEmpty()) {
            return false;
        }

        if (!MaidActionLock.tryAcquire(maid, ACTION_ID)) {
            LOGGER.info(
                    "[WGON FruitDiag] action-lock-failed maid={}",
                    maid.getDisplayName().getString()
            );
            return false;
        }

        Session session = new Session(
                level,
                maid,
                plan.types(),
                plan.positionsByType(),
                plan.favoritePriorityUsed()
        );

        if (!session.prepareFirstTarget()) {
            MaidActionLock.release(maid, ACTION_ID);
            return false;
        }

        ACTIVE.put(maidId, session);
        LOGGER.info(
                "[WGON FruitDiag] session-start maid={} plannedTypes={}",
                maid.getDisplayName().getString(),
                plan.types()
        );
        session.begin();
        return true;
    }

    private static BlockStateProbe describeInterestingBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(
                level.getBlockState(pos).getBlock()
        );

        if (id == null) {
            return new BlockStateProbe(false, "unknown");
        }

        String path = id.getPath();
        boolean grapeCrop = "kaleidoscope_tavern".equals(id.getNamespace())
                && ("grape_crop".equals(path)
                || "ice_grape_crop".equals(path)
                || "gold_grape_crop".equals(path));
        boolean melon = "minecraft".equals(id.getNamespace())
                && "melon".equals(path);
        boolean durian = "fruitsdelight".equals(id.getNamespace())
                && "durian".equals(path);

        return new BlockStateProbe(
                grapeCrop || melon || durian,
                id.toString()
        );
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "unregistered" : id.toString();
    }

    private record BlockStateProbe(
            boolean interesting,
            String blockId
    ) {
    }

    private static List<TargetCandidate> buildViableCandidates(
            ServerLevel level,
            EntityMaid maid,
            List<BlockPos> candidates
    ) {
        List<TargetCandidate> viable = new ArrayList<>();

        for (BlockPos fruitPos : candidates) {
            ItemStack preview = FruitHarvestAccess
                    .previewOne(level, maid, fruitPos)
                    .orElse(ItemStack.EMPTY);

            if (preview.isEmpty()) {
                continue;
            }

            GlobalPos reservation = GlobalPos.of(level.dimension(), fruitPos);

            if (RESERVED_FRUITS.contains(reservation)
                    || isTemporarilyFailed(level, maid, reservation)) {
                continue;
            }

            BlockPos groundSpot = GroundApproachHelper.findFruitGroundSpot(
                    level,
                    maid,
                    fruitPos,
                    true
            );

            if (groundSpot == null) {
                markTemporarilyFailed(level, maid, reservation);
                continue;
            }

            ResourceLocation fruitItemId = ForgeRegistries.ITEMS.getKey(preview.getItem());

            if (fruitItemId == null) {
                continue;
            }

            viable.add(new TargetCandidate(
                    fruitPos.immutable(),
                    groundSpot.immutable(),
                    reservation,
                    fruitItemId,
                    isFavoriteFruit(preview),
                    FruitHarvestAccess.isGoldenAppleTarget(level, fruitPos)
            ));
        }

        return viable;
    }

    /**
     * Build one cycle from DISTINCT fruit item IDs. Defaults are:
     * 40% one type, 35% exactly two types, 25% every type discovered.
     * The two configurable multi-type chances are kept disjoint; if a hand-
     * edited TOML makes their sum exceed 100, ALL keeps its configured share
     * and TWO is clamped to the remaining percentage.
     */
    private static CyclePlan buildCyclePlan(
            ServerLevel level,
            List<TargetCandidate> viable,
            boolean forceRandom
    ) {
        Map<ResourceLocation, List<TargetCandidate>> byType = new LinkedHashMap<>();

        for (TargetCandidate candidate : viable) {
            byType.computeIfAbsent(
                    candidate.fruitItemId(),
                    ignored -> new ArrayList<>()
            ).add(candidate);
        }

        List<ResourceLocation> types = new ArrayList<>(byType.keySet());
        shuffleWithLevelRandom(level, types);

        boolean favoritePriorityUsed = false;

        if (!forceRandom) {
            List<ResourceLocation> favoriteTypes = new ArrayList<>();

            for (Map.Entry<ResourceLocation, List<TargetCandidate>> entry : byType.entrySet()) {
                if (entry.getValue().stream().anyMatch(TargetCandidate::favorite)) {
                    favoriteTypes.add(entry.getKey());
                }
            }

            if (!favoriteTypes.isEmpty()) {
                ResourceLocation favoriteType = favoriteTypes.get(
                        level.random.nextInt(favoriteTypes.size())
                );
                types.remove(favoriteType);
                types.add(0, favoriteType);
                favoritePriorityUsed = true;
            }
        }

        CycleMode mode = rollCycleMode(level);
        int wanted = switch (mode) {
            case ONE -> 1;
            case TWO -> Math.min(2, types.size());
            case ALL -> types.size();
        };

        List<ResourceLocation> plannedTypes = new ArrayList<>(
                types.subList(0, Math.min(wanted, types.size()))
        );
        Map<ResourceLocation, List<BlockPos>> positionsByType = new LinkedHashMap<>();

        for (ResourceLocation type : plannedTypes) {
            List<BlockPos> positions = new ArrayList<>();
            for (TargetCandidate candidate : byType.getOrDefault(type, List.of())) {
                positions.add(candidate.fruitPos());
            }
            shuffleWithLevelRandom(level, positions);
            positionsByType.put(type, positions);
        }

        return new CyclePlan(
                plannedTypes,
                positionsByType,
                favoritePriorityUsed
        );
    }

    private static <T> void shuffleWithLevelRandom(
            ServerLevel level,
            List<T> values
    ) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = level.random.nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    private static CycleMode rollCycleMode(ServerLevel level) {
        int allChance = Mth.clamp(
                WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.get(),
                0,
                100
        );
        int twoChance = Mth.clamp(
                WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.get(),
                0,
                Math.max(0, 100 - allChance)
        );
        int roll = level.random.nextInt(100);

        if (roll < allChance) {
            return CycleMode.ALL;
        }

        if (roll < allChance + twoChance) {
            return CycleMode.TWO;
        }

        return CycleMode.ONE;
    }

    private enum CycleMode {
        ONE,
        TWO,
        ALL
    }

    private record CyclePlan(
            List<ResourceLocation> types,
            Map<ResourceLocation, List<BlockPos>> positionsByType,
            boolean favoritePriorityUsed
    ) {
    }

    /**
     * Fair random selection across fruit varieties rather than physical block
     * count. Removing every candidate of the selected type from the working
     * pool also means that, if its chosen instance goes stale before the
     * session starts, the same scan can fall through to another fruit variety
     * instead of repeatedly rolling another berry bush.
     */
    private static TargetCandidate removeRandomTypeCandidate(
            ServerLevel level,
            List<TargetCandidate> pool
    ) {
        if (pool.isEmpty()) {
            return null;
        }

        Map<ResourceLocation, List<TargetCandidate>> byType =
                new LinkedHashMap<>();

        for (TargetCandidate candidate : pool) {
            byType.computeIfAbsent(
                    candidate.fruitItemId(),
                    ignored -> new ArrayList<>()
            ).add(candidate);
        }

        List<ResourceLocation> types = new ArrayList<>(byType.keySet());
        ResourceLocation selectedType = types.get(
                level.random.nextInt(types.size())
        );
        List<TargetCandidate> instances = byType.get(selectedType);
        TargetCandidate selected = instances.get(
                level.random.nextInt(instances.size())
        );

        pool.removeIf(candidate ->
                candidate.fruitItemId().equals(selectedType)
        );

        return selected;
    }

    private static boolean isFavoriteFruit(ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());

        if (itemId == null) {
            return false;
        }

        String id = itemId.toString();
        for (String configured : WgonConfig.FAVORITE_FRUIT_WHITELIST.get()) {
            if (id.equals(configured)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isTemporarilyFailed(
            ServerLevel level,
            EntityMaid maid,
            GlobalPos target
    ) {
        Map<GlobalPos, Long> failed = FAILED_TARGET_UNTIL.get(maid.getUUID());

        if (failed == null) {
            return false;
        }

        long now = level.getGameTime();
        failed.entrySet().removeIf(entry -> entry.getValue() <= now);

        if (failed.isEmpty()) {
            FAILED_TARGET_UNTIL.remove(maid.getUUID());
            return false;
        }

        return failed.getOrDefault(target, 0L) > now;
    }

    private static void markTemporarilyFailed(
            ServerLevel level,
            EntityMaid maid,
            GlobalPos target
    ) {
        FAILED_TARGET_UNTIL
                .computeIfAbsent(maid.getUUID(), ignored -> new HashMap<>())
                .put(target, level.getGameTime() + FAILED_TARGET_RETRY_TICKS);
    }

    private record TargetCandidate(
            BlockPos fruitPos,
            BlockPos groundSpot,
            GlobalPos reservation,
            ResourceLocation fruitItemId,
            boolean favorite,
            boolean goldenAppleTheft
    ) {
    }

    public static boolean hasActiveSession(
            EntityMaid maid
    ) {
        return ACTIVE.containsKey(maid.getUUID())
                || GroundDurianTastingManager.hasActiveSession(maid);
    }

    public static void cancelForMaid(
            EntityMaid maid
    ) {
        /*
         * Ground-durian tasting deliberately shares ACTION_ID with ordinary
         * fruit tasting so the existing night-steal pre-emption and stale-lock
         * cleanup rules continue to work unchanged.
         */
        GroundDurianTastingManager.cancelForMaid(maid);

        Session session = ACTIVE.remove(maid.getUUID());

        if (session != null) {
            session.finish();
        } else {
            MaidActionLock.release(maid, ACTION_ID);
        }
    }

    private static void finishActiveSessionsInLevel(
            ServerLevel level
    ) {
        Iterator<Map.Entry<UUID, Session>> iterator =
                ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();

            if (session.level != level) {
                continue;
            }

            session.finish();
            iterator.remove();
        }
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

        return TaskIdle.UID.equals(maid.getTask().getUid());
    }

    /**
     * Once WGON owns a fruit session, using the fruit is expected behaviour.
     * The pre-session eligibility check rejects maids that are already using
     * an unrelated item, but the active session must not cancel itself one
     * tick after startUsingItem().
     */
    private static boolean isSessionStillValid(
            EntityMaid maid
    ) {
        if (!maid.isAlive()
                || maid.isRemoved()
                || maid.getOwnerUUID() == null
                || maid.isSleeping()
                || maid.isMaidInSittingPose()
                || maid.getTarget() != null
                || maid.getTask() == null) {
            return false;
        }

        return TaskIdle.UID.equals(maid.getTask().getUid());
    }

    private enum Stage {
        MOVE_TO_FRUIT,
        SETTLE,
        CUT_FRUIT,
        ADMIRE,
        EAT,
        DONE
    }

    private static final class Session {

        private final ServerLevel level;
        private final EntityMaid maid;
        private final UUID maidId;
        private final ItemStack oldMainHand;
        private final List<ResourceLocation> plannedTypes;
        private final Map<ResourceLocation, List<BlockPos>> positionsByType;
        private final boolean favoritePriorityUsed;

        private Stage stage = Stage.MOVE_TO_FRUIT;
        private int stageTicks = 0;
        private int fruitAttemptTicks = 0;
        private int nextTypeIndex = 0;
        private boolean finished = false;
        private boolean handOverridden = false;
        private boolean currentHarvested = false;
        private boolean anySuccessful = false;
        private ItemStack fruit = ItemStack.EMPTY;
        private long currentBubbleId = -1L;

        private ResourceLocation currentType;
        private BlockPos fruitPos;
        private BlockPos groundSpot;
        private GlobalPos reservation;
        private boolean goldenAppleTheft;
        private boolean cuttingFruitTarget;

        private Session(
                ServerLevel level,
                EntityMaid maid,
                List<ResourceLocation> plannedTypes,
                Map<ResourceLocation, List<BlockPos>> positionsByType,
                boolean favoritePriorityUsed
        ) {
            this.level = level;
            this.maid = maid;
            this.maidId = maid.getUUID();
            this.plannedTypes = new ArrayList<>(plannedTypes);
            this.positionsByType = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, List<BlockPos>> entry : positionsByType.entrySet()) {
                this.positionsByType.put(
                        entry.getKey(),
                        new ArrayList<>(entry.getValue())
                );
            }
            this.favoritePriorityUsed = favoritePriorityUsed;
            this.oldMainHand = maid.getMainHandItem().copy();
        }

        /**
         * Called before the session is published in ACTIVE. This reserves the
         * first physical fruit while the action lock is already owned.
         */
        private boolean prepareFirstTarget() {
            return prepareNextTarget();
        }

        private boolean prepareNextTarget() {
            clearCurrentTarget(false);

            while (nextTypeIndex < plannedTypes.size()) {
                ResourceLocation wantedType = plannedTypes.get(nextTypeIndex++);
                List<BlockPos> positions = positionsByType.getOrDefault(
                        wantedType,
                        List.of()
                );

                for (BlockPos pos : positions) {
                    ItemStack preview = FruitHarvestAccess
                            .previewOne(level, maid, pos)
                            .orElse(ItemStack.EMPTY);

                    if (preview.isEmpty()) {
                        continue;
                    }

                    ResourceLocation actualType = ForgeRegistries.ITEMS.getKey(preview.getItem());
                    if (!wantedType.equals(actualType)) {
                        continue;
                    }

                    GlobalPos candidateReservation = GlobalPos.of(
                            level.dimension(),
                            pos
                    );

                    if (RESERVED_FRUITS.contains(candidateReservation)
                            || isTemporarilyFailed(level, maid, candidateReservation)) {
                        continue;
                    }

                    BlockPos candidateGround = GroundApproachHelper.findFruitGroundSpot(
                            level,
                            maid,
                            pos,
                            true
                    );

                    if (candidateGround == null) {
                        markTemporarilyFailed(level, maid, candidateReservation);
                        continue;
                    }

                    if (!RESERVED_FRUITS.add(candidateReservation)) {
                        continue;
                    }

                    currentType = wantedType;
                    fruitPos = pos.immutable();
                    groundSpot = candidateGround.immutable();
                    reservation = candidateReservation;
                    goldenAppleTheft = FruitHarvestAccess.isGoldenAppleTarget(level, pos);
                    cuttingFruitTarget = FruitHarvestAccess.isCuttingFruitTarget(level, maid, pos);
                    currentHarvested = false;
                    fruitAttemptTicks = 0;
                    LOGGER.info(
                            "[WGON FruitDiag] target-selected maid={} type={} fruitPos={} groundSpot={} cutting={} golden={}",
                            maid.getDisplayName().getString(),
                            wantedType,
                            fruitPos,
                            groundSpot,
                            cuttingFruitTarget,
                            goldenAppleTheft
                    );
                    transition(Stage.MOVE_TO_FRUIT);
                    return true;
                }
            }

            return false;
        }

        private void begin() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);
        }

        private void tick() {
            if (finished) {
                return;
            }

            fruitAttemptTicks++;
            stageTicks++;

            /*
             * The watchdog is PER FRUIT, not per cycle. An ALL-varieties cycle
             * is intentionally uncapped, so a large orchard must not hit a
             * global 30-second timeout merely because it contains many types.
             */
            if (fruitAttemptTicks > SESSION_WATCHDOG_TICKS
                    || !MaidBehaviorSettings.isFruitTastingEnabled(maid)
                    || NightStealManager.hasNightPriority(level, maid)
                    || !isSessionStillValid(maid)) {
                finish();
                return;
            }

            switch (stage) {
                case MOVE_TO_FRUIT -> tickMove();
                case SETTLE -> tickSettle();
                case CUT_FRUIT -> tickCutFruit();
                case ADMIRE -> tickAdmire();
                case EAT -> tickEat();
                case DONE -> finish();
            }
        }

        private void tickMove() {
            if (FruitHarvestAccess.previewOne(level, maid, fruitPos).isEmpty()) {
                LOGGER.info(
                        "[WGON FruitDiag] target-became-invalid maid={} pos={}",
                        maid.getDisplayName().getString(),
                        fruitPos
                );
                failCurrentAndContinue();
                return;
            }

            faceFruit();
            maid.setSprinting(false);

            Vec3 target = GroundApproachHelper.centerOfFeet(groundSpot);
            double goalDistanceSqr = maid.position().distanceToSqr(target);

            boolean nearApproachPoint =
                    goalDistanceSqr <= 1.45D * 1.45D;
            boolean navigationNaturallyFinished =
                    stageTicks > 2 && maid.getNavigation().isDone();

            if (isWithinReach()
                    && (nearApproachPoint || navigationNaturallyFinished)) {
                if (!GroundApproachHelper.settleNaturallyIfGrounded(maid)) {
                    return;
                }

                transition(Stage.SETTLE);
                return;
            }

            if (stageTicks > APPROACH_TIMEOUT_TICKS) {
                LOGGER.info(
                        "[WGON FruitDiag] approach-timeout maid={} fruitPos={} groundSpot={} distanceSqr={}",
                        maid.getDisplayName().getString(),
                        fruitPos,
                        groundSpot,
                        goalDistanceSqr
                );
                failCurrentAndContinue();
                return;
            }

            if (stageTicks % 10 == 1 || maid.getNavigation().isDone()) {
                boolean started = GroundApproachHelper.startSafeNavigation(
                        level,
                        maid,
                        groundSpot,
                        APPROACH_SPEED
                );

                if (!started) {
                    LOGGER.info(
                            "[WGON FruitDiag] navigation-start-failed maid={} fruitPos={} groundSpot={}",
                            maid.getDisplayName().getString(),
                            fruitPos,
                            groundSpot
                    );
                    failCurrentAndContinue();
                }
            }
        }

        private void tickSettle() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);
            faceFruit();

            if (stageTicks < SETTLE_TICKS) {
                return;
            }

            if (!isWithinReach()) {
                LOGGER.info(
                        "[WGON FruitDiag] settle-out-of-reach maid={} fruitPos={} maidPos={}",
                        maid.getDisplayName().getString(),
                        fruitPos,
                        maid.position()
                );
                failCurrentAndContinue();
                return;
            }

            if (goldenAppleTheft && currentBubbleId < 0L) {
                currentBubbleId = maid.getChatBubbleManager()
                        .addTextChatBubble(
                                "chatbubble.feastwineallgone.fruit.golden_apple_sneak"
                        );
            }

            if (cuttingFruitTarget) {
                ItemStack cuttingTool = FruitHarvestAccess.findCuttingTool(maid);

                if (cuttingTool.isEmpty()) {
                    LOGGER.info(
                            "[WGON FruitDiag] cutting-tool-missing maid={} fruitPos={}",
                            maid.getDisplayName().getString(),
                            fruitPos
                    );
                    failCurrentAndContinue();
                    return;
                }

                /*
                 * Give the cut its own visible beat.  If we swapped the tool
                 * to a melon slice in the same tick, clients could skip the
                 * axe/sword pose entirely and the melon would appear to pop.
                 */
                showInMainHand(cuttingTool);
                maid.swing(InteractionHand.MAIN_HAND);
                transition(Stage.CUT_FRUIT);
                return;
            }

            harvestCurrentFruit();
        }

        private void tickCutFruit() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);
            faceFruit();

            /* A short wind-up makes the chop readable without feeling slow. */
            if (stageTicks < 8) {
                return;
            }

            if (!FruitHarvestAccess.isCuttingFruitTarget(level, maid, fruitPos)) {
                LOGGER.info(
                        "[WGON FruitDiag] cutting-target-invalid-after-windup maid={} fruitPos={} cutter={}",
                        maid.getDisplayName().getString(),
                        fruitPos,
                        itemId(FruitHarvestAccess.findCuttingTool(maid))
                );
                failCurrentAndContinue();
                return;
            }

            harvestCurrentFruit();
        }

        private void harvestCurrentFruit() {
            fruit = FruitHarvestAccess.harvestOne(level, maid, fruitPos);

            LOGGER.info(
                    "[WGON FruitDiag] harvest maid={} fruitPos={} result={}",
                    maid.getDisplayName().getString(),
                    fruitPos,
                    itemId(fruit)
            );

            if (fruit.isEmpty()) {
                failCurrentAndContinue();
                return;
            }

            currentHarvested = true;

            if (!cuttingFruitTarget) {
                maid.swing(InteractionHand.MAIN_HAND);
            }

            showInMainHand(fruit);
            transition(Stage.ADMIRE);
        }

        private void tickAdmire() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);
            faceFruit();

            if (stageTicks < ADMIRE_FRUIT_TICKS) {
                return;
            }

            maid.startUsingItem(InteractionHand.MAIN_HAND);
            transition(Stage.EAT);
        }

        private void tickEat() {
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);

            if (stageTicks < EAT_VISUAL_TICKS) {
                return;
            }

            maid.stopUsingItem();

            if (!fruit.isEmpty()) {
                /* Preserve the real food finish hook and the historical +10. */
                fruit.copy().finishUsingItem(level, maid);
                maid.getFavorabilityManager().add(
                        FIXED_FAVORABILITY_PER_FRUIT
                );
                LOGGER.info(
                        "[WGON FruitDiag] eaten maid={} item={} favorabilityPlus={}",
                        maid.getDisplayName().getString(),
                        itemId(fruit),
                        FIXED_FAVORABILITY_PER_FRUIT
                );
                anySuccessful = true;
            }

            fruit = ItemStack.EMPTY;
            restoreMainHand();

            if (prepareNextTarget()) {
                begin();
                return;
            }

            finish();
        }

        private void failCurrentAndContinue() {
            if (reservation != null && !currentHarvested) {
                markTemporarilyFailed(level, maid, reservation);
            }

            if (prepareNextTarget()) {
                begin();
                return;
            }

            finish();
        }

        private boolean isWithinReach() {
            if (fruitPos == null) {
                return false;
            }

            Vec3 center = Vec3.atCenterOf(fruitPos);
            double dx = center.x - maid.getX();
            double dz = center.z - maid.getZ();
            double horizontalSqr = dx * dx + dz * dz;
            double vertical = Math.abs(center.y - maid.getY());

            return horizontalSqr <= MAX_HORIZONTAL_REACH * MAX_HORIZONTAL_REACH
                    && vertical <= MAX_VERTICAL_REACH;
        }

        private void faceFruit() {
            if (fruitPos == null) {
                return;
            }

            Vec3 target = Vec3.atCenterOf(fruitPos);
            double dx = target.x - maid.getX();
            double dz = target.z - maid.getZ();

            float yaw = (float) (Mth.atan2(dz, dx)
                    * (180.0D / Math.PI)) - 90.0F;

            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setXRot(0.0F);
        }

        private void showInMainHand(ItemStack stack) {
            maid.stopUsingItem();
            maid.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            handOverridden = true;
        }

        private void restoreMainHand() {
            if (!handOverridden) {
                return;
            }

            maid.stopUsingItem();
            maid.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand.copy());
            handOverridden = false;
        }

        private void transition(Stage next) {
            stage = next;
            stageTicks = 0;
        }

        private void clearCurrentTarget(boolean markFailed) {
            restoreMainHand();
            fruit = ItemStack.EMPTY;

            if (currentBubbleId >= 0L) {
                maid.getChatBubbleManager().removeChatBubble(currentBubbleId);
                currentBubbleId = -1L;
            }

            if (reservation != null) {
                if (markFailed && !currentHarvested) {
                    markTemporarilyFailed(level, maid, reservation);
                }
                RESERVED_FRUITS.remove(reservation);
            }

            currentType = null;
            fruitPos = null;
            groundSpot = null;
            reservation = null;
            goldenAppleTheft = false;
            cuttingFruitTarget = false;
            currentHarvested = false;
        }

        private void finish() {
            if (finished) {
                return;
            }

            finished = true;
            GroundApproachHelper.clearCompetingWalkTarget(maid);
            maid.getNavigation().stop();
            maid.setSprinting(false);

            boolean failedCurrent = reservation != null && !currentHarvested;
            clearCurrentTarget(failedCurrent);

            /* Favorite priority is a per-CYCLE decision, not per fruit. */
            if (favoritePriorityUsed && anySuccessful) {
                FORCE_RANDOM_NEXT_TASTING.add(maidId);
            } else if (anySuccessful) {
                FORCE_RANDOM_NEXT_TASTING.remove(maidId);
            }

            MaidActionLock.release(maid, ACTION_ID);

            /*
             * No fruit cooldown exists anymore. The next ordinary idle pass may
             * immediately roll the trigger chance again, allowing a second cycle
             * to chain naturally if the random roll happens to succeed.
             */
            IdleActivityManager.requestImmediateDecision(maid);
        }
    }
}

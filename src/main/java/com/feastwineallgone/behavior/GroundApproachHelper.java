package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IBarrel;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.TapBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.deco.BarCounterBlock;
import com.feastwineallgone.food.DiningSurfaceRules;
import com.feastwineallgone.food.FoodBlockService;
import com.feastwineallgone.integration.tavern.TavernCounterDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared floor/path helper for WGON dining and drinking behaviours.
 *
 * WGON never intentionally asks a maid to stand on food, bottle,
 * counter, tap or barrel blocks. It also rejects navigation paths whose
 * nodes climb across those surfaces. This keeps the maid on the floor
 * instead of using the bar as a shortcut.
 */
final class GroundApproachHelper {

    private GroundApproachHelper() {
    }

    static BlockPos findGroundSpot(
            ServerLevel level,
            EntityMaid maid,
            BlockPos targetPos,
            boolean requireReachable
    ) {
        BlockPos maidFeet = maid.blockPosition();
        List<Candidate> candidates = new ArrayList<>();

        int[] yOffsets = targetPos.getY() >= maidFeet.getY() + 2
                ? new int[]{0, 1, -1}
                : new int[]{0, -1};

        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    for (int yOffset : yOffsets) {
                        BlockPos candidate = new BlockPos(
                                targetPos.getX() + dx,
                                maidFeet.getY() + yOffset,
                                targetPos.getZ() + dz
                        );

                        if (!isSafeGroundSpot(level, candidate, targetPos)) {
                            continue;
                        }

                        Vec3 target = centerOfFeet(candidate);

                        double score = maid.position().distanceToSqr(target)
                                + Math.abs(yOffset) * 5.0D
                                + radius * 0.25D;

                        candidates.add(
                                new Candidate(
                                        candidate.immutable(),
                                        score
                                )
                        );
                    }
                }
            }
        }

        candidates.sort(
                Comparator.comparingDouble(
                        Candidate::score
                )
        );

        for (Candidate candidate : candidates) {
            if (!requireReachable
                    || createSafePath(
                    level,
                    maid,
                    candidate.pos()
            ) != null) {
                return candidate.pos();
            }
        }

        return null;
    }


    /**
     * Fruit interaction uses a slightly different stand-point search from
     * table dining. Fruit can legitimately hang one or more blocks above the
     * maid and berry bushes are often decorated with trapdoors. Anchor the
     * candidate Y levels to both the maid and the fruit, and allow a real
     * player-like interaction ring out to roughly three blocks.
     */
    static BlockPos findFruitGroundSpot(
            ServerLevel level,
            EntityMaid maid,
            BlockPos fruitPos,
            boolean requireReachable
    ) {
        BlockPos maidFeet = maid.blockPosition();
        List<Candidate> candidates = new ArrayList<>();

        int[] yLevels = new int[]{
                maidFeet.getY(),
                maidFeet.getY() - 1,
                maidFeet.getY() + 1,
                fruitPos.getY() - 1,
                fruitPos.getY(),
                fruitPos.getY() + 1
        };

        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    double horizontal = Math.sqrt((double) dx * dx + (double) dz * dz);
                    if (horizontal > 3.25D) {
                        continue;
                    }

                    for (int y : yLevels) {
                        BlockPos candidate = new BlockPos(
                                fruitPos.getX() + dx,
                                y,
                                fruitPos.getZ() + dz
                        );

                        if (!isSafeFruitGroundSpot(level, candidate, fruitPos)) {
                            continue;
                        }

                        double vertical = Math.abs((fruitPos.getY() + 0.5D) - y);
                        if (vertical > 4.5D) {
                            continue;
                        }

                        Vec3 target = centerOfFeet(candidate);
                        double score = maid.position().distanceToSqr(target)
                                + Math.abs(y - maidFeet.getY()) * 3.0D
                                + horizontal * 0.35D;

                        candidates.add(new Candidate(candidate.immutable(), score));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::score));

        BlockPos previous = null;
        for (Candidate candidate : candidates) {
            if (candidate.pos().equals(previous)) {
                continue;
            }
            previous = candidate.pos();

            if (!requireReachable
                    || createSafePath(level, maid, candidate.pos()) != null) {
                return candidate.pos();
            }
        }

        return null;
    }

    /**
     * Find a floor tile whose X/Z distance from the interaction target is
     * inside a strict ring. Night stealing uses a 1-2 block ring around the
     * Tavern faucet so extraction cannot happen from the other side of a
     * room or from a vertically aligned floor above the cellar.
     *
     * Candidate Y values are anchored to the target itself, rather than the
     * maid's current floor. This is important in multi-storey buildings.
     */
    static BlockPos findGroundSpotInHorizontalRange(
            ServerLevel level,
            EntityMaid maid,
            BlockPos targetPos,
            int minRadius,
            int maxRadius,
            boolean requireReachable
    ) {
        int min = Math.max(1, minRadius);
        int max = Math.max(min, maxRadius);
        List<Candidate> candidates = new ArrayList<>();

        int[] candidateY = new int[]{
                targetPos.getY() - 1,
                targetPos.getY(),
                targetPos.getY() + 1
        };

        for (int radius = min; radius <= max; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    double horizontal = Math.sqrt(
                            (double) dx * dx + (double) dz * dz
                    );

                    if (horizontal < min
                            || horizontal > max + 0.25D) {
                        continue;
                    }

                    for (int y : candidateY) {
                        BlockPos candidate = new BlockPos(
                                targetPos.getX() + dx,
                                y,
                                targetPos.getZ() + dz
                        );

                        if (!isSafeGroundSpot(level, candidate, targetPos)) {
                            continue;
                        }

                        Vec3 target = centerOfFeet(candidate);
                        double score = maid.position().distanceToSqr(target)
                                + Math.abs(y - targetPos.getY()) * 2.0D
                                + radius * 0.25D;

                        candidates.add(new Candidate(candidate.immutable(), score));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::score));

        for (Candidate candidate : candidates) {
            if (!requireReachable
                    || createSafePath(level, maid, candidate.pos()) != null) {
                return candidate.pos();
            }
        }

        return null;
    }

    private static boolean isSafeFruitGroundSpot(
            ServerLevel level,
            BlockPos pos,
            BlockPos fruitPos
    ) {
        boolean directlyUnderFruit = pos.getX() == fruitPos.getX()
                && pos.getZ() == fruitPos.getZ()
                && fruitPos.getY() >= pos.getY() + 2;

        if (!directlyUnderFruit
                && pos.getX() == fruitPos.getX()
                && pos.getZ() == fruitPos.getZ()) {
            return false;
        }

        BlockPos groundPos = pos.below();
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(groundPos);

        if (!feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()) {
            return false;
        }

        if (!feet.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        /*
         * When standing directly under a hanging fruit, the fruit itself may
         * occupy the block above the maid. Hanging-fruit blocks have no useful
         * body collision for the maid, so accept an empty collision shape here.
         */
        if (!head.getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        return !isForbiddenSurface(level, pos)
                && !isForbiddenSurface(level, groundPos);
    }

    static boolean isSafeGroundSpot(
            ServerLevel level,
            BlockPos pos,
            BlockPos interactionTarget
    ) {
        if (pos.getX() == interactionTarget.getX()
                && pos.getZ() == interactionTarget.getZ()) {
            return false;
        }

        BlockPos groundPos = pos.below();

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(groundPos);

        if (!feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()) {
            return false;
        }

        if (!feet.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        if (!head.getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        return !isForbiddenSurface(level, pos)
                && !isForbiddenSurface(level, groundPos);
    }

    /**
     * Returns a complete path only when every path node stays off WGON's
     * interaction furniture. Minecraft otherwise likes to treat counters
     * and low display blocks as convenient stairs.
     */
    private static Path createSafePath(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target
    ) {
        Path path = maid.getNavigation().createPath(target, 0);

        if (path == null || !path.canReach()) {
            return null;
        }

        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            BlockPos feet = new BlockPos(node.x, node.y, node.z);

            if (isForbiddenSurface(level, feet)
                    || isForbiddenSurface(level, feet.below())) {
                return null;
            }
        }

        return path;
    }

    static boolean startSafeNavigation(
            ServerLevel level,
            EntityMaid maid,
            BlockPos target,
            double speed
    ) {
        clearCompetingWalkTarget(maid);

        Path path = createSafePath(level, maid, target);

        if (path == null) {
            return false;
        }

        return maid.getNavigation().moveTo(path, speed);
    }

    /**
     * Useful as a defensive recovery check if some other AI/path step has
     * already pushed the maid onto a counter/display surface.
     */
    static boolean isStandingOnForbiddenSurface(
            ServerLevel level,
            EntityMaid maid
    ) {
        BlockPos feet = maid.blockPosition();
        return isForbiddenSurface(level, feet)
                || isForbiddenSurface(level, feet.below());
    }

    private static boolean isForbiddenSurface(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);

        /*
         * Cookery tables are furniture, never a shortcut.  The old path
         * filter only rejected the FOOD block itself, so a path could step
         * onto an empty neighbouring table tile and then hop across the
         * serving line.  That is the visible one-second "bounce" from the
         * user video.
         */
        if (state.getBlock() instanceof TableBlock) {
            return true;
        }

        if (state.getBlock() instanceof BarCounterBlock
                || state.getBlock() instanceof TapBlock) {
            return true;
        }

        /*
         * Custom dining surfaces can be arbitrary registered blocks, so do
         * not make every configured block globally unwalkable.  It becomes
         * interaction furniture only while a supported serving is actually
         * sitting directly above it.
         */
        if (DiningSurfaceRules.isAllowedSurface(state)
                && FoodBlockService.isSupportedFood(level, pos.above())) {
            return true;
        }

        if (TavernCounterDrinkAccess.isSupportedDrinkAt(level, pos)) {
            return true;
        }

        /*
         * A custom configured counter can be any registered block. Treat it
         * as interaction furniture only while a supported Tavern drink is
         * actually displayed above it; otherwise a common floor block added
         * by a pack maker would become globally unwalkable.
         */
        if (TavernCounterDrinkAccess.isSupportedDrinkAt(level, pos.above())) {
            return true;
        }

        if (FoodBlockService.findHandler(state).isPresent()) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof IBarrel;
    }

    static Vec3 centerOfFeet(BlockPos pos) {
        return new Vec3(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D
        );
    }

    /**
     * WGON drives navigation directly while an eating/drinking action owns the
     * maid.  TLM may already have a WALK_TARGET stored from the previous brain
     * tick even though MaidFollowOwnerTaskMixin prevents a new follow task from
     * starting.  Erasing that stale memory prevents the two movement systems
     * from tugging the maid in different directions.
     */
    static void clearCompetingWalkTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /**
     * Stop a dining approach without teleporting the maid to the centre of a
     * block. Pathfinding can finish a step while the maid is still in the
     * small hop used to cross a block edge; snapping her Y position at that
     * instant creates the visible one-second dash/hover reported around
     * favorite food. Wait for a real grounded tick instead.
     */
    static boolean settleNaturallyIfGrounded(
            EntityMaid maid
    ) {
        clearCompetingWalkTarget(maid);
        maid.getNavigation().stop();
        maid.setSprinting(false);

        Vec3 motion = maid.getDeltaMovement();

        if (!maid.onGround()) {
            // Kill only horizontal carry; keep gravity/falling intact.
            maid.setDeltaMovement(
                    0.0D,
                    motion.y,
                    0.0D
            );
            return false;
        }

        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        maid.setXRot(0.0F);
        return true;
    }

    static void settleExactlyOnGroundSpot(
            EntityMaid maid,
            BlockPos pos
    ) {
        Vec3 target = centerOfFeet(pos);

        clearCompetingWalkTarget(maid);
        maid.getNavigation().stop();
        maid.moveTo(
                target.x,
                target.y,
                target.z,
                maid.getYRot(),
                0.0F
        );
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        maid.setXRot(0.0F);
    }
    private record Candidate(
            BlockPos pos,
            double score
    ) {
    }

}

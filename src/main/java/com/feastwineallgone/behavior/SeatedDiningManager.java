package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.ChairBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.TableBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.decoration.TableBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.entity.SitEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.item.ClayPotMilkTeaItem;
import com.github.ysbbbbbb.kaleidoscopecookery.item.TeacupItem;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.food.DiningSurfaceRules;
import com.feastwineallgone.food.FavoriteFoodRules;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodBlockService;
import com.feastwineallgone.food.FoodConsumeResult;
import com.feastwineallgone.food.FoodSafetyRules;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import com.feastwineallgone.integration.tavern.TavernDrinkAccess;
import com.feastwineallgone.integration.tavern.TavernPlacedDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cookery chair dining mode.
 *
 * Important rules:
 * 1. Chair snap only happens on the rising edge of the player's sit order.
 *    WGON never re-snaps every tick. This makes "stand up" reliable.
 * 2. Food physically placed on top of Cookery tables is the primary source.
 *    Those blocks reuse WGON's normal FoodBlockService, so cake, Cookery food,
 *    platters and Cookery tea all keep their existing compatibility logic.
 * 3. Vanilla CakeBlock gets a small seated compatibility exception: it may
 *    sit on any supporting block within reach while Cookery table placement is
 *    incompatible with cake placement.
 * 4. TableBlockEntity display items remain a fallback source.
 * 5. Seated dining deliberately has a wider reach than normal idle eating.
 *    While seated at a Cookery chair, the maid may reach food on the same
 *    dining setup up to roughly 4 horizontal blocks away. This supports large
 *    player-built tables without restoring long-range reach to normal eating.
 */
public final class SeatedDiningManager {

    public static final String ACTION_ID = "seated_dining";

    private static final int CHAIR_RADIUS = 4;
    private static final int TABLE_RADIUS = 5;
    private static final int FREE_CAKE_SCAN_RADIUS = 5;
    private static final int PICKUP_SETTLE_TICKS = 6;
    private static final int HANDHELD_VISUAL_TICKS = 32;
    private static final int BETWEEN_SERVINGS_MIN_TICKS = 30;
    private static final int BETWEEN_SERVINGS_MAX_TICKS = 60;
    private static final int FAVORITE_BEG_MIN_TICKS = 40;
    private static final int FAVORITE_BEG_MAX_TICKS = 60;

    private static final double MIN_REACH = 0.65D;
    private static final double MAX_REACH = 4.20D;
    private static final double MAX_VERTICAL_REACH = 2.25D;
    private static final double MIN_REACH_SQR = MIN_REACH * MIN_REACH;
    private static final double MAX_REACH_SQR = MAX_REACH * MAX_REACH;

    private static final Map<UUID, DiningSession> SESSIONS = new HashMap<>();

    /**
     * Last observed TLM ordered-sit state. We only snap to a Cookery chair on
     * false -> true, never continuously while the flag stays true.
     */
    private static final Map<UUID, Boolean> LAST_SIT_COMMAND = new HashMap<>();

    /**
     * Maids currently mounted by WGON's own one-shot chair snap.
     */
    private static final Set<UUID> AUTO_SEATED = new HashSet<>();

    private SeatedDiningManager() {
    }

    /**
     * Protect the maid's temporary hand item while a seated bite/drink is in
     * progress. TLM exposes the maid inventory through its interaction GUI,
     * so opening that GUI while WGON has temporarily replaced MAIN_HAND with
     * a detached serving lets the player pull the visual serving out of the
     * equipment slot. The later hand restore then re-syncs/recreates items.
     *
     * We only hard-block the dangerous pickup/use window. During the safe
     * between-serving/begging phases, interacting with the maid cancels the
     * dining session first and then lets TLM open the GUI normally. This keeps
     * the lock short: at worst the player waits for the current bite/drink.
     */
    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        EntityMaid maid = event.getMaid();
        DiningSession session = SESSIONS.get(maid.getUUID());
        if (session == null || session.finished) {
            return;
        }

        if (session.hasProtectedHandState()) {
            event.setCanceled(true);
            if (!event.getWorld().isClientSide) {
                event.getPlayer().displayClientMessage(
                        Component.translatable(
                                "message.feastwineallgone.seated_dining_busy"
                        ),
                        true
                );
            }
            return;
        }

        // Safe point: stop dining before the normal TLM GUI is allowed to open.
        SESSIONS.remove(maid.getUUID());
        session.finish();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)
                || event.phase != TickEvent.Phase.END) {
            return;
        }

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }

            UUID maidId = maid.getUUID();

            if (!maid.isAlive() || maid.isRemoved()) {
                cancelForMaid(maid);
                AUTO_SEATED.remove(maidId);
                LAST_SIT_COMMAND.remove(maidId);
                continue;
            }

            if (!MaidBehaviorSettings.isSeatedDiningEnabled(maid)) {
                cancelForMaid(maid);
                MealProgressManager.cancelForMaid(maid);
                if (AUTO_SEATED.remove(maidId)
                        && maid.getVehicle() instanceof SitEntity) {
                    maid.stopRiding();
                }
                LAST_SIT_COMMAND.put(maidId, false);
                continue;
            }

            if (NightStealManager.hasNightPriority(level, maid)) {
                prepareForNight(maid);
                continue;
            }

            /*
             * If a maid GUI is already open (for example another mod opened
             * it directly), never begin or continue a seated serving session.
             * This is a second safety net behind InteractMaidEvent and also
             * prevents a new temporary hand item from appearing underneath an
             * already-open inventory screen.
             */
            if (maid.guiOpening) {
                cancelForMaid(maid);
                continue;
            }

            boolean sitCommand = maid.isMaidInSittingPose();
            boolean previousSitCommand = LAST_SIT_COMMAND.getOrDefault(maidId, false);

            if (!sitCommand) {
                /*
                 * Player explicitly stood the maid up. Release the seat once
                 * and arm the next false -> true transition.
                 */
                cancelForMaid(maid);
                if (AUTO_SEATED.remove(maidId)
                        && maid.getVehicle() instanceof SitEntity) {
                    maid.stopRiding();
                }
                LAST_SIT_COMMAND.put(maidId, false);
                continue;
            }

            if (!previousSitCommand) {
                /*
                 * One-shot absorption. If no free Cookery chair exists, leave
                 * the normal TLM sitting order alone and do nothing else.
                 */
                LAST_SIT_COMMAND.put(maidId, true);
                if (!attachToNearestCookeryChair(level, maid)) {
                    cancelForMaid(maid);
                    AUTO_SEATED.remove(maidId);
                    continue;
                }
                AUTO_SEATED.add(maidId);
            } else if (!AUTO_SEATED.contains(maidId)) {
                /*
                 * The player is still technically in sit mode, but WGON has
                 * already used (or lost) its one-shot chair snap. Never pull
                 * the maid back again until sit is toggled off and on.
                 */
                cancelForMaid(maid);
                continue;
            }

            if (!(maid.getVehicle() instanceof SitEntity sitEntity)
                    || !(level.getBlockState(sitEntity.blockPosition()).getBlock() instanceof ChairBlock)) {
                /*
                 * If the seat disappears or the maid is manually dismounted,
                 * treat that as leaving the WGON chair. Do not re-absorb.
                 */
                cancelForMaid(maid);
                AUTO_SEATED.remove(maidId);
                continue;
            }

            if (MealProgressManager.isFoodCooldownActive(level, maid)) {
                cancelForMaid(maid);
                continue;
            }

            IdleFoodManager.cancelForMaid(maid);
            CounterDrinkManager.cancelForMaid(maid);

            DiningSession session = SESSIONS.get(maidId);
            if (session != null) {
                session.tick();
                if (session.finished) {
                    SESSIONS.remove(maidId);
                }
                continue;
            }

            if (level.getGameTime() % 5L != 0L) {
                continue;
            }

            if (!hasAnyReachableServing(level, maid)) {
                MaidActionLock.release(maid, ACTION_ID);
                continue;
            }

            if (!MaidActionLock.tryAcquire(maid, ACTION_ID)) {
                continue;
            }

            DiningSession created = new DiningSession(level, maid);
            SESSIONS.put(maidId, created);
            created.beginNextServing();
        }
    }

    public static void cancelForMaid(EntityMaid maid) {
        DiningSession session = SESSIONS.remove(maid.getUUID());
        if (session != null) {
            session.finish();
        }
        MaidActionLock.release(maid, ACTION_ID);
    }

    public static void prepareForNight(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        cancelForMaid(maid);
        AUTO_SEATED.remove(maidId);
        LAST_SIT_COMMAND.put(maidId, false);

        if (maid.getVehicle() instanceof SitEntity) {
            maid.stopRiding();
        }
        if (maid.isMaidInSittingPose()) {
            maid.setOrderedToSit(false);
        }
    }

    private static boolean attachToNearestCookeryChair(ServerLevel level, EntityMaid maid) {
        if (maid.getVehicle() instanceof SitEntity sitEntity
                && level.getBlockState(sitEntity.blockPosition()).getBlock() instanceof ChairBlock) {
            return true;
        }

        BlockPos chairPos = findNearestFreeChair(level, maid);
        if (chairPos == null) {
            return false;
        }

        if (maid.isPassenger()) {
            maid.stopRiding();
        }

        SitEntity seat = findReusableSeat(level, chairPos);
        if (seat == null) {
            seat = new SitEntity(level, chairPos, 0.5125D);
            BlockState state = level.getBlockState(chairPos);
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            seat.setYRot(facing.toYRot());
            level.addFreshEntity(seat);
        }

        maid.getNavigation().stop();
        maid.setDeltaMovement(Vec3.ZERO);
        return maid.startRiding(seat, true);
    }

    private static BlockPos findNearestFreeChair(ServerLevel level, EntityMaid maid) {
        BlockPos origin = maid.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-CHAIR_RADIUS, -2, -CHAIR_RADIUS),
                origin.offset(CHAIR_RADIUS, 2, CHAIR_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChairBlock)) {
                continue;
            }

            SitEntity seat = findReusableSeat(level, pos);
            if (seat == null && hasOccupiedSeat(level, pos)) {
                continue;
            }

            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }

        return best;
    }

    private static SitEntity findReusableSeat(ServerLevel level, BlockPos chairPos) {
        List<SitEntity> seats = level.getEntitiesOfClass(
                SitEntity.class,
                new AABB(chairPos).inflate(0.15D),
                seat -> !seat.isRemoved() && seat.getPassengers().isEmpty()
        );
        return seats.isEmpty() ? null : seats.get(0);
    }

    private static boolean hasOccupiedSeat(ServerLevel level, BlockPos chairPos) {
        return !level.getEntitiesOfClass(
                SitEntity.class,
                new AABB(chairPos).inflate(0.15D),
                seat -> !seat.isRemoved() && !seat.getPassengers().isEmpty()
        ).isEmpty();
    }

    /**
     * Returns true when at least one seated-dining serving exists within reach.
     *
     * A seated maid treats all nearby Cookery table blocks within her 4-block
     * dining reach as one dining setup. This lets large player-built tables
     * behave naturally without forcing the maid to exhaust one bowl first.
     */
    private static boolean hasAnyReachableServing(ServerLevel level, EntityMaid maid) {
        return !collectServingCandidates(level, maid).isEmpty();
    }

    /**
     * Collect all currently reachable servings around nearby dining surfaces.
     * Cookery tables are always supported; config-whitelisted surfaces may
     * additionally host real placed food/drink blocks. The final choice is
     * made randomly by DiningSession.
     */
    private static List<ServingTarget> collectServingCandidates(
            ServerLevel level,
            EntityMaid maid
    ) {
        BlockPos origin = maid.blockPosition();
        List<ServingTarget> candidates = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-TABLE_RADIUS, -2, -TABLE_RADIUS),
                origin.offset(TABLE_RADIUS, 2, TABLE_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!DiningSurfaceRules.isAllowedSeatedSurface(state)) {
                continue;
            }

            addServingCandidatesOnSurface(level, maid, pos.immutable(), candidates);
        }

        /*
         * Compatibility 2: vanilla cake cannot currently be placed normally
         * on some Cookery tables because the table intercepts the interaction.
         * While the maid is already seated, accept any real minecraft:cake
         * block within seated reach, regardless of the block underneath it.
         *
         * This is intentionally cake-only. Other placed foods keep using the
         * Cookery-table rule so seated dining does not start vacuuming food
         * from unrelated shelves/floors around the room.
         */
        addFreeStandingCakeCandidates(level, maid, origin, candidates);

        return candidates;
    }

    private static void addFreeStandingCakeCandidates(
            ServerLevel level,
            EntityMaid maid,
            BlockPos origin,
            List<ServingTarget> output
    ) {
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-FREE_CAKE_SCAN_RADIUS, -2, -FREE_CAKE_SCAN_RADIUS),
                origin.offset(FREE_CAKE_SCAN_RADIUS, 2, FREE_CAKE_SCAN_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CakeBlock)) {
                continue;
            }

            BlockPos cakePos = pos.immutable();
            if (!FoodBlockService.isSupportedFood(level, cakePos)) {
                continue;
            }

            Vec3 cakeCenter = Vec3.atCenterOf(cakePos);
            if (!isWithinServingReach(maid, cakeCenter)) {
                continue;
            }

            if (containsPlacedBlockCandidate(output, cakePos)) {
                continue;
            }

            output.add(ServingTarget.placedBlock(
                    cakePos.below(),
                    cakePos,
                    cakeCenter
            ));
        }
    }

    private static boolean containsPlacedBlockCandidate(
            List<ServingTarget> candidates,
            BlockPos blockPos
    ) {
        for (ServingTarget candidate : candidates) {
            if (candidate.kind() == ServingKind.PLACED_BLOCK
                    && blockPos.equals(candidate.blockPos())) {
                return true;
            }
        }
        return false;
    }

    private static void addServingCandidatesOnSurface(
            ServerLevel level,
            EntityMaid maid,
            BlockPos tablePos,
            List<ServingTarget> output
    ) {
        BlockPos placedFoodPos = tablePos.above();
        Vec3 placedCenter = Vec3.atCenterOf(placedFoodPos);

        if (FoodBlockService.isSupportedFood(level, placedFoodPos)
                && !FoodSafetyRules.isHardBlacklisted(level.getBlockState(placedFoodPos).getBlock().asItem())) {
            if (isWithinServingReach(maid, placedCenter)) {
                output.add(ServingTarget.placedBlock(
                        tablePos,
                        placedFoodPos.immutable(),
                        placedCenter
                ));
            }
        } else if (TavernPlacedDrinkAccess.isSupportedDrinkAt(level, placedFoodPos)) {
            /*
             * Tavern bottles/cocktails placed as real world blocks on top of
             * a physical dining surface are NOT stored in TableBlockEntity and
             * are not FoodBlockService foods. Treat them as another seated-
             * dining source when their supporting block is an allowed surface.
             */
            if (isWithinServingReach(maid, placedCenter)) {
                output.add(ServingTarget.placedTavernDrink(
                        tablePos,
                        placedFoodPos.immutable(),
                        placedCenter
                ));
            }
        }

        BlockEntity blockEntity = level.getBlockEntity(tablePos);
        if (!(blockEntity instanceof TableBlockEntity table)) {
            return;
        }

        ItemStackHandler items = table.getItems();
        int nonEmpty = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                nonEmpty++;
            }
        }

        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!isConsumable(stack)) {
                continue;
            }

            Vec3 displayPos = tableDisplayPosition(table, slot, nonEmpty);
            if (!isWithinServingReach(maid, displayPos)) {
                continue;
            }

            output.add(ServingTarget.tableItem(
                    tablePos,
                    slot,
                    displayPos
            ));
        }
    }

    private static boolean isWithinServingReach(EntityMaid maid, Vec3 target) {
        Vec3 maidPos = maid.position();
        double horizontalSqr = horizontalDistanceSqr(maidPos, target);
        double vertical = Math.abs(target.y - maidPos.y);

        return horizontalSqr >= MIN_REACH_SQR
                && horizontalSqr <= MAX_REACH_SQR
                && vertical <= MAX_VERTICAL_REACH;
    }

    private static double horizontalDistanceSqr(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return dx * dx + dz * dz;
    }

    private static Vec3 tableDisplayPosition(TableBlockEntity table, int slot, int count) {
        double[][] offsets = switch (Math.max(1, Math.min(4, count))) {
            case 1 -> new double[][]{{0.0D, 0.0D}};
            case 2 -> new double[][]{{-0.25D, 0.10D}, {0.25D, -0.10D}};
            case 3 -> new double[][]{{0.25D, -0.20D}, {-0.25D, 0.0D}, {0.24D, 0.20D}};
            default -> new double[][]{{0.25D, -0.30D}, {-0.24D, -0.10D}, {0.24D, 0.10D}, {-0.25D, 0.30D}};
        };

        int index = Math.max(0, Math.min(offsets.length - 1, slot));
        double localX = offsets[index][0];
        double localZ = offsets[index][1];

        BlockState state = table.getBlockState();
        Direction.Axis axis = state.getValue(TableBlock.AXIS);

        double rotatedX;
        double rotatedZ;
        if (axis == Direction.Axis.X) {
            rotatedX = -localX;
            rotatedZ = -localZ;
        } else {
            rotatedX = localZ;
            rotatedZ = -localX;
        }

        BlockPos pos = table.getBlockPos();
        return new Vec3(
                pos.getX() + 0.5D + rotatedX,
                pos.getY() + 1.30D,
                pos.getZ() + 0.5D + rotatedZ
        );
    }

    private static boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (FoodSafetyRules.isHardBlacklisted(stack)) {
            return false;
        }
        return stack.isEdible()
                || stack.is(Items.CAKE)
                || stack.getItem() instanceof TeacupItem
                || stack.getItem() instanceof ClayPotMilkTeaItem
                || TavernDrinkAccess.isDrink(stack);
    }

    private static void compactTableItems(ItemStackHandler items) {
        ItemStack[] compact = new ItemStack[items.getSlots()];
        int write = 0;

        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                compact[write++] = stack.copy();
            }
        }

        for (int slot = 0; slot < items.getSlots(); slot++) {
            items.setStackInSlot(
                    slot,
                    compact[slot] == null ? ItemStack.EMPTY : compact[slot]
            );
        }
    }

    private static boolean consumeInventoryServing(ServerLevel level, EntityMaid maid, ItemStack serving) {
        if (serving.getItem() instanceof TeacupItem
                || serving.getItem() instanceof ClayPotMilkTeaItem) {
            return CookeryDrinkAccess.consumeDetachedDrink(level, maid, serving);
        }

        if (TavernDrinkAccess.isDrink(serving)) {
            return TavernDrinkAccess.consumeDetachedDrink(level, maid, serving);
        }

        if (serving.is(Items.CAKE)) {
            // A cake stored by Cookery's TableBlockEntity is an ItemStack, not
            // a world CakeBlock, and ItemStack#isEdible() is false. Consume the
            // displayed cake as one table serving so seated maids do not ignore
            // their default favorite simply because the table stored the item.
            level.playSound(
                    null,
                    maid.blockPosition(),
                    SoundEvents.GENERIC_EAT,
                    SoundSource.NEUTRAL,
                    0.8F,
                    0.9F + level.random.nextFloat() * 0.2F
            );
            return true;
        }

        if (!serving.isEdible()) {
            return false;
        }

        ItemStack one = serving.copy();
        one.setCount(1);
        ItemStack remainder = one.finishUsingItem(level, maid);
        CookeryDrinkAccess.storeOrDrop(level, maid, remainder);
        return true;
    }

    private static boolean isFavoriteServing(
            ServerLevel level,
            ServingTarget target
    ) {
        if (target == null) {
            return false;
        }

        if (target.kind() == ServingKind.PLACED_BLOCK) {
            return target.blockPos() != null
                    && FavoriteFoodRules.isFavoriteBlock(
                    level.getBlockState(target.blockPos())
            );
        }

        if (target.kind() == ServingKind.PLACED_TAVERN_DRINK) {
            if (target.blockPos() == null) {
                return false;
            }

            ItemStack preview = TavernPlacedDrinkAccess.peekOne(
                    level,
                    target.blockPos()
            );

            return FavoriteFoodRules.isFavoriteDrink(
                    level,
                    target.blockPos(),
                    preview
            );
        }

        BlockEntity blockEntity = level.getBlockEntity(target.tablePos());
        if (!(blockEntity instanceof TableBlockEntity table)) {
            return false;
        }

        int slot = target.slot();
        if (slot < 0 || slot >= table.getItems().getSlots()) {
            return false;
        }

        return FavoriteFoodRules.isFavoriteItem(
                table.getItems().getStackInSlot(slot)
        );
    }

    private enum ServingKind {
        PLACED_BLOCK,
        PLACED_TAVERN_DRINK,
        TABLE_ITEM
    }

    private record ServingTarget(
            ServingKind kind,
            BlockPos tablePos,
            BlockPos blockPos,
            int slot,
            Vec3 interactionPos
    ) {
        static ServingTarget placedBlock(
                BlockPos tablePos,
                BlockPos pos,
                Vec3 interactionPos
        ) {
            return new ServingTarget(
                    ServingKind.PLACED_BLOCK,
                    tablePos.immutable(),
                    pos.immutable(),
                    -1,
                    interactionPos
            );
        }

        static ServingTarget placedTavernDrink(
                BlockPos tablePos,
                BlockPos pos,
                Vec3 interactionPos
        ) {
            return new ServingTarget(
                    ServingKind.PLACED_TAVERN_DRINK,
                    tablePos.immutable(),
                    pos.immutable(),
                    -1,
                    interactionPos
            );
        }

        static ServingTarget tableItem(
                BlockPos tablePos,
                int slot,
                Vec3 interactionPos
        ) {
            return new ServingTarget(
                    ServingKind.TABLE_ITEM,
                    tablePos.immutable(),
                    null,
                    slot,
                    interactionPos
            );
        }

        ServingSource source() {
            return new ServingSource(
                    kind,
                    kind == ServingKind.TABLE_ITEM ? tablePos : blockPos,
                    slot
            );
        }
    }

    private record ServingSource(
            ServingKind kind,
            BlockPos sourcePos,
            int slot
    ) {
    }

    /**
     * Stable identity for one physical dish during a formal meal.
     *
     * PLACED_BLOCK / PLACED_TAVERN_DRINK use their actual block position.
     * TABLE_ITEM uses the table position + slot. Therefore eating a cake or
     * platter five times still contributes exactly one dish to meal capacity.
     */
    private static String mealDishKey(ServingTarget target) {
        ServingSource source = target.source();
        BlockPos pos = source.sourcePos();
        long packedPos = pos == null ? 0L : pos.asLong();
        return source.kind().name() + ":" + packedPos + ":" + source.slot();
    }

    private enum Phase {
        WAIT,
        FAVORITE_BEG,
        PICKUP,
        VISUAL
    }

    private static final class DiningSession {
        private final ServerLevel level;
        private final EntityMaid maid;
        private final ItemStack oldMainHand;

        private ServingTarget target;
        private ServingSource lastSource;
        private final Set<ServingSource> favoriteBeggedSources = new HashSet<>();
        private HandheldFoodBlockHandler placedHandheldHandler;
        private ItemStack visualServing = ItemStack.EMPTY;
        private int ticks = 0;
        private int waitTicks = 0;
        private int favoriteBegTicks = 0;
        private Phase phase = Phase.WAIT;
        private boolean detachedServingPending = false;
        private boolean targetFavorite = false;
        private boolean finished = false;

        private DiningSession(ServerLevel level, EntityMaid maid) {
            this.level = level;
            this.maid = maid;
            this.oldMainHand = maid.getMainHandItem().copy();
        }

        private void beginNextServing() {
            if (!isSessionValid()) {
                finish();
                return;
            }

            target = chooseRandomServing();
            if (target == null) {
                finish();
                return;
            }

            if (!isWithinServingReach(maid, target.interactionPos())) {
                finish();
                return;
            }

            keepHorizontalPose();
            ticks = 0;
            waitTicks = 0;
            placedHandheldHandler = null;
            visualServing = ItemStack.EMPTY;

            if (MaidBehaviorSettings.isFavoriteReactionEnabled(maid)
                    && isFavoriteServing(level, target)
                    && !favoriteBeggedSources.contains(target.source())) {
                favoriteBegTicks = FAVORITE_BEG_MIN_TICKS
                        + level.random.nextInt(
                        FAVORITE_BEG_MAX_TICKS
                                - FAVORITE_BEG_MIN_TICKS
                                + 1
                );
                FavoriteBeggingController.start(maid);
                faceTargetHorizontally(target.interactionPos());
                phase = Phase.FAVORITE_BEG;
                return;
            }

            beginTargetServing();
        }

        private void beginTargetServing() {
            FavoriteBeggingController.stop(maid);
            targetFavorite = isFavoriteServing(level, target);

            if (target.kind() == ServingKind.PLACED_BLOCK) {
                beginPlacedBlockServing();
            } else if (target.kind() == ServingKind.PLACED_TAVERN_DRINK) {
                beginPlacedTavernDrinkServing();
            } else {
                beginTableItemServing();
            }
        }

        private ServingTarget chooseRandomServing() {
            List<ServingTarget> candidates = collectServingCandidates(level, maid);
            if (candidates.isEmpty()) {
                return null;
            }

            if (MaidBehaviorSettings.isFavoriteReactionEnabled(maid)) {
                List<ServingTarget> favorites = new ArrayList<>();
                for (ServingTarget candidate : candidates) {
                    if (isFavoriteServing(level, candidate)) {
                        favorites.add(candidate);
                    }
                }

                if (!favorites.isEmpty()) {
                    candidates = favorites;
                }
            }

            if (lastSource != null && candidates.size() > 1) {
                List<ServingTarget> alternatives = new ArrayList<>();
                for (ServingTarget candidate : candidates) {
                    if (!candidate.source().equals(lastSource)) {
                        alternatives.add(candidate);
                    }
                }
                if (!alternatives.isEmpty()) {
                    candidates = alternatives;
                }
            }

            return candidates.get(level.random.nextInt(candidates.size()));
        }

        private int nextServingPauseTicks() {
            return BETWEEN_SERVINGS_MIN_TICKS
                    + level.random.nextInt(
                    BETWEEN_SERVINGS_MAX_TICKS
                            - BETWEEN_SERVINGS_MIN_TICKS
                            + 1
            );
        }

        private void beginPlacedBlockServing() {
            BlockPos foodPos = target.blockPos();
            if (foodPos == null || !FoodBlockService.isSupportedFood(level, foodPos)) {
                finish();
                return;
            }

            BlockState state = level.getBlockState(foodPos);
            Optional<FoodBlockHandler> optionalHandler = FoodBlockService.findHandler(state);
            if (optionalHandler.isEmpty()) {
                finish();
                return;
            }

            FoodBlockHandler handler = optionalHandler.get();
            if (handler instanceof HandheldFoodBlockHandler handheld) {
                ItemStack serving = handheld.takeOneServing(maid, level, foodPos, state);
                if (serving.isEmpty()) {
                    finish();
                    return;
                }

                placedHandheldHandler = handheld;
                visualServing = serving.copy();

                /*
                 * Pickup-only bundles (notably Farming Tales' stacked
                 * drinks) must retain their full count. Older WGON code
                 * forced every visual serving to count=1 here, which erased
                 * the bundle identity before the handler could store it and
                 * made the seated path consume one bottle as food/drink.
                 */
                if (!handheld.isPickupOnlyDetachedServing(visualServing)) {
                    visualServing.setCount(1);
                }

                detachedServingPending = true;
                maid.setItemInHand(InteractionHand.MAIN_HAND, visualServing.copy());
                // Swing at the actual pickup moment, before the eating animation.
                maid.swing(InteractionHand.MAIN_HAND);
                phase = Phase.PICKUP;
                return;
            }

            // Non-handheld foods are consumed immediately, so remember their identity before the world state changes.
            ResourceLocation consumedFoodId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            maid.swing(InteractionHand.MAIN_HAND);
            FoodConsumeResult result = FoodBlockService.consumeOne(maid, level, foodPos);
            if (!result.consumed()) {
                finish();
                return;
            }

            if (consumedFoodId != null) {
                MealProgressManager.recordServing(level, maid, consumedFoodId,
                        MealProgressManager.ServingType.FOOD, targetFavorite,
                        mealDishKey(target));
            }
            lastSource = target.source();
            phase = Phase.WAIT;
            waitTicks = nextServingPauseTicks();
        }


        private void beginPlacedTavernDrinkServing() {
            BlockPos drinkPos = target.blockPos();
            if (drinkPos == null
                    || !TavernPlacedDrinkAccess.isSupportedDrinkAt(level, drinkPos)) {
                finish();
                return;
            }

            ItemStack serving = TavernPlacedDrinkAccess.takeOne(level, drinkPos);
            if (serving.isEmpty() || !TavernDrinkAccess.isDrink(serving)) {
                finish();
                return;
            }

            visualServing = serving.copy();
            visualServing.setCount(1);
            detachedServingPending = true;
            maid.setItemInHand(InteractionHand.MAIN_HAND, visualServing.copy());
            // Bottle was physically taken now; animate the reaching hand now too.
            maid.swing(InteractionHand.MAIN_HAND);
            phase = Phase.PICKUP;
        }

        private void beginTableItemServing() {
            BlockEntity blockEntity = level.getBlockEntity(target.tablePos());
            if (!(blockEntity instanceof TableBlockEntity table)) {
                finish();
                return;
            }

            int slot = target.slot();
            if (slot < 0 || slot >= table.getItems().getSlots()) {
                finish();
                return;
            }

            ItemStack current = table.getItems().getStackInSlot(slot);
            if (!isConsumable(current)) {
                finish();
                return;
            }

            ItemStack extracted = table.getItems().extractItem(slot, 1, false);
            if (extracted.isEmpty()) {
                finish();
                return;
            }

            compactTableItems(table.getItems());
            table.refresh();

            visualServing = extracted.copy();
            visualServing.setCount(1);
            detachedServingPending = true;
            maid.setItemInHand(InteractionHand.MAIN_HAND, visualServing.copy());
            // Table inventory was extracted now; swing before the use animation.
            maid.swing(InteractionHand.MAIN_HAND);
            phase = Phase.PICKUP;
        }

        private void tick() {
            if (finished) {
                return;
            }

            if (!isSessionValid()) {
                finish();
                return;
            }

            keepHorizontalPose();

            if (phase == Phase.FAVORITE_BEG) {
                if (target == null
                        || !isWithinServingReach(maid, target.interactionPos())) {
                    FavoriteBeggingController.stop(maid);
                    finish();
                    return;
                }

                maid.getNavigation().stop();
                faceTargetHorizontally(target.interactionPos());
                FavoriteBeggingController.start(maid);
                ticks++;

                if (ticks < favoriteBegTicks) {
                    return;
                }

                FavoriteBeggingController.stop(maid);
                favoriteBeggedSources.add(target.source());
                ticks = 0;
                beginTargetServing();
                return;
            }

            if (phase == Phase.WAIT) {
                if (waitTicks > 0) {
                    waitTicks--;
                }
                if (waitTicks <= 0) {
                    beginNextServing();
                }
                return;
            }

            if (phase == Phase.PICKUP) {
                if (target == null || !isWithinServingReach(maid, target.interactionPos())) {
                    finish();
                    return;
                }

                ticks++;
                if (ticks < PICKUP_SETTLE_TICKS) {
                    return;
                }

                /*
                 * A pickup-only bundle is luggage, not lunch. Finalize the
                 * pickup now without ever entering Minecraft's item-use
                 * pipeline. This prevents EAT/DRINK sounds, finishUsingItem()
                 * and accidental loss of the bundled drinks.
                 */
                if (target.kind() == ServingKind.PLACED_BLOCK
                        && placedHandheldHandler != null
                        && placedHandheldHandler
                        .isPickupOnlyDetachedServing(visualServing)) {

                    boolean pickedUp = finishPlacedHandheldServing();

                    if (pickedUp) {
                        detachedServingPending = false;
                    } else {
                        returnDetachedServingIfNeeded();
                    }

                    restoreHand();

                    if (!pickedUp) {
                        finish();
                        return;
                    }

                    lastSource = target.source();
                    phase = Phase.WAIT;
                    waitTicks = nextServingPauseTicks();
                    return;
                }

                maid.startUsingItem(InteractionHand.MAIN_HAND);
                ticks = 0;
                phase = Phase.VISUAL;
                return;
            }

            if (target == null || !isWithinServingReach(maid, target.interactionPos())) {
                finish();
                return;
            }

            ticks++;
            if (ticks < HANDHELD_VISUAL_TICKS) {
                return;
            }

            maid.stopUsingItem();

            boolean consumed;
            if (target.kind() == ServingKind.PLACED_BLOCK) {
                consumed = finishPlacedHandheldServing();
            } else if (target.kind() == ServingKind.PLACED_TAVERN_DRINK) {
                consumed = finishPlacedTavernDrinkServing();
            } else {
                consumed = finishTableItemServing();
            }

            if (consumed) {
                detachedServingPending = false;
                recordSuccessfulServing(target, visualServing, targetFavorite);
            } else {
                returnDetachedServingIfNeeded();
            }

            restoreHand();

            if (!consumed) {
                finish();
                return;
            }

            lastSource = target.source();
            phase = Phase.WAIT;
            waitTicks = nextServingPauseTicks();
        }

        private void recordSuccessfulServing(ServingTarget consumedTarget, ItemStack consumedStack, boolean favorite) {
            ResourceLocation id = null;
            MealProgressManager.ServingType type = MealProgressManager.ServingType.FOOD;

            if (consumedTarget.kind() == ServingKind.PLACED_TAVERN_DRINK) {
                id = consumedStack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(consumedStack.getItem());
                type = MealProgressManager.ServingType.DRINK;
            } else if (consumedTarget.kind() == ServingKind.TABLE_ITEM) {
                if (!consumedStack.isEmpty()) {
                    id = ForgeRegistries.ITEMS.getKey(consumedStack.getItem());
                    if (consumedStack.getItem() instanceof TeacupItem
                            || consumedStack.getItem() instanceof ClayPotMilkTeaItem
                            || TavernDrinkAccess.isDrink(consumedStack)) {
                        type = MealProgressManager.ServingType.DRINK;
                    }
                }
            } else if (!consumedStack.isEmpty()) {
                id = ForgeRegistries.ITEMS.getKey(consumedStack.getItem());
                if (consumedStack.getItem() instanceof TeacupItem
                        || consumedStack.getItem() instanceof ClayPotMilkTeaItem
                        || TavernDrinkAccess.isDrink(consumedStack)) {
                    type = MealProgressManager.ServingType.DRINK;
                }
            } else if (consumedTarget.blockPos() != null) {
                id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(consumedTarget.blockPos()).getBlock());
                /* The block may have become an empty remnant/air after the bite. Use source ID fallback. */
                if (id == null || "minecraft:air".equals(id.toString())) {
                    id = new ResourceLocation("feastwineallgone", "placed_food_" + consumedTarget.blockPos().asLong());
                }
            }

            if (id != null) {
                MealProgressManager.recordServing(level, maid, id, type, favorite,
                        mealDishKey(consumedTarget));
            }
        }

        private boolean finishPlacedHandheldServing() {
            if (placedHandheldHandler == null || visualServing.isEmpty()) {
                return false;
            }
            return placedHandheldHandler.consumeDetachedServing(maid, level, visualServing);
        }


        private boolean finishPlacedTavernDrinkServing() {
            if (visualServing.isEmpty()
                    || !TavernDrinkAccess.isDrink(visualServing)) {
                return false;
            }

            return TavernDrinkAccess.consumeDetachedDrink(
                    level,
                    maid,
                    visualServing.copy()
            );
        }

        private boolean finishTableItemServing() {
            if (visualServing.isEmpty()) {
                return false;
            }

            return consumeInventoryServing(
                    level,
                    maid,
                    visualServing.copy()
            );
        }

        private void returnDetachedServingIfNeeded() {
            if (!detachedServingPending || visualServing.isEmpty()) {
                detachedServingPending = false;
                return;
            }

            CookeryDrinkAccess.storeOrDrop(
                    level,
                    maid,
                    visualServing.copy()
            );
            detachedServingPending = false;
        }

        private boolean hasProtectedHandState() {
            return detachedServingPending
                    || phase == Phase.PICKUP
                    || phase == Phase.VISUAL;
        }

        private boolean isSessionValid() {
            return !NightStealManager.hasNightPriority(level, maid)
                    && !maid.guiOpening
                    && maid.isMaidInSittingPose()
                    && AUTO_SEATED.contains(maid.getUUID())
                    && maid.getVehicle() instanceof SitEntity;
        }

        private void faceTargetHorizontally(Vec3 targetPos) {
            double dx = targetPos.x - maid.getX();
            double dz = targetPos.z - maid.getZ();

            float yaw = (float) (net.minecraft.util.Mth.atan2(
                    dz,
                    dx
            ) * (180.0D / Math.PI)) - 90.0F;

            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setXRot(0.0F);
        }

        private void keepHorizontalPose() {
            maid.setYHeadRot(maid.getYRot());
            maid.setXRot(0.0F);
        }

        private void restoreHand() {
            maid.stopUsingItem();
            maid.setItemInHand(InteractionHand.MAIN_HAND, oldMainHand.copy());
            visualServing = ItemStack.EMPTY;
            placedHandheldHandler = null;
        }

        private void finish() {
            if (finished) {
                return;
            }
            FavoriteBeggingController.stop(maid);
            returnDetachedServingIfNeeded();
            restoreHand();
            finished = true;
            MaidActionLock.release(maid, ACTION_ID);
        }
    }
}

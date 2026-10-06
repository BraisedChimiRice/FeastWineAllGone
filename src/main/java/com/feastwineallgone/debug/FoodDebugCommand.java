package com.feastwineallgone.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.CommandDispatcher;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodBlockService;
import com.feastwineallgone.food.FoodConsumeResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Temporary development command used to test WGON's placed-food engine.
 *
 * Command:
 *
 * /wgon_food_test
 *
 * Behaviour:
 *
 * 1. Finds the nearest maid around the player.
 * 2. Searches around that maid for the nearest food block
 *    recognised by FoodBlockService.
 * 3. Makes the maid consume exactly one serving.
 * 4. Prints debugging information.
 *
 * This class can be removed once the food system is stable.
 */
@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class FoodDebugCommand {

    private static final double MAID_SEARCH_RADIUS = 12.0D;

    private static final int FOOD_SEARCH_RADIUS = 8;

    private FoodDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        register(event.getDispatcher());
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("wgon_food_test")
                        .executes(context ->
                                execute(
                                        context.getSource()
                                )
                        )
        );
    }

    private static int execute(
            CommandSourceStack source
    ) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(
                    Component.literal(
                            "[WGON] This command must be executed by a player."
                    )
            );

            return 0;
        }

        ServerLevel level =
                player.serverLevel();

        Optional<EntityMaid> maidOptional =
                findNearestMaid(
                        level,
                        player
                );

        if (maidOptional.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "[WGON] No maid found within "
                                    + (int) MAID_SEARCH_RADIUS
                                    + " blocks."
                    )
            );

            return 0;
        }

        EntityMaid maid =
                maidOptional.get();

        Optional<BlockPos> foodOptional =
                findNearestFood(
                        level,
                        maid
                );

        if (foodOptional.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "[WGON] Maid found, but no supported food block "
                                    + "was found within "
                                    + FOOD_SEARCH_RADIUS
                                    + " blocks of her."
                    )
            );

            return 0;
        }

        BlockPos foodPos =
                foodOptional.get();

        BlockState stateBefore =
                level.getBlockState(foodPos);

        Optional<FoodBlockHandler> handlerOptional =
                FoodBlockService.findHandler(
                        stateBefore
                );

        if (handlerOptional.isEmpty()) {
            source.sendFailure(
                    Component.literal(
                            "[WGON] Food disappeared before it could be tested."
                    )
            );

            return 0;
        }

        FoodBlockHandler handler =
                handlerOptional.get();

        int servingsBefore =
                handler.getRemainingServings(
                        level,
                        foodPos,
                        stateBefore
                );

        FoodConsumeResult result =
                FoodBlockService.consumeOne(
                        maid,
                        level,
                        foodPos
                );

        BlockState stateAfter =
                level.getBlockState(foodPos);

        int servingsAfter;

        Optional<FoodBlockHandler> afterHandler =
                FoodBlockService.findHandler(
                        stateAfter
                );

        if (afterHandler.isPresent()) {
            servingsAfter =
                    afterHandler
                            .get()
                            .getRemainingServings(
                                    level,
                                    foodPos,
                                    stateAfter
                            );
        } else {
            servingsAfter = 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "[WGON] Food test result:"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Maid: "
                                + maid.getName().getString()
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Handler: "
                                + handler.getId()
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Position: "
                                + foodPos.getX()
                                + ", "
                                + foodPos.getY()
                                + ", "
                                + foodPos.getZ()
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Before: "
                                + servingsBefore
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "After: "
                                + servingsAfter
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "Result: "
                                + result.status()
                ),
                false
        );

        return result.consumed() ? 1 : 0;
    }

    private static Optional<EntityMaid> findNearestMaid(
            ServerLevel level,
            ServerPlayer player
    ) {
        AABB searchBox =
                player.getBoundingBox()
                        .inflate(
                                MAID_SEARCH_RADIUS
                        );

        List<EntityMaid> maids =
                level.getEntitiesOfClass(
                        EntityMaid.class,
                        searchBox,
                        EntityMaid::isAlive
                );

        return maids.stream()
                .min(
                        Comparator.comparingDouble(
                                maid ->
                                        maid.distanceToSqr(
                                                player
                                        )
                        )
                );
    }

    private static Optional<BlockPos> findNearestFood(
            ServerLevel level,
            EntityMaid maid
    ) {
        BlockPos center =
                maid.blockPosition();

        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(
                        -FOOD_SEARCH_RADIUS,
                        -FOOD_SEARCH_RADIUS,
                        -FOOD_SEARCH_RADIUS
                ),
                center.offset(
                        FOOD_SEARCH_RADIUS,
                        FOOD_SEARCH_RADIUS,
                        FOOD_SEARCH_RADIUS
                )
        )) {
            if (!FoodBlockService.isSupportedFood(
                    level,
                    pos
            )) {
                continue;
            }

            Vec3 foodCenter =
                    Vec3.atCenterOf(pos);

            double distance =
                    maid.position()
                            .distanceToSqr(
                                    foodCenter
                            );

            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = pos.immutable();
            }
        }

        return Optional.ofNullable(
                bestPos
        );
    }
}
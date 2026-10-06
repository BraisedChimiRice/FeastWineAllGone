package com.feastwineallgone.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemNormalBauble;
import com.feastwineallgone.behavior.DrunkSleepData;
import com.feastwineallgone.behavior.DrunkSleepManager;
import com.feastwineallgone.network.WgonNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * FWAG maid bauble and per-maid configuration / drunk-sleep recording tool.
 */
public final class MaidToolboxItem extends ItemNormalBauble {

    private static final String BOUND_MAID_UUID =
            "FeastWineAllGoneBoundMaid";
    private static final String BOUND_MAID_NAME =
            "FeastWineAllGoneBoundMaidName";
    private static final String RECORD_MODE =
            "FeastWineAllGoneRecordDrunkSleep";

    public MaidToolboxItem() {
        super();
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity interactionTarget,
            InteractionHand usedHand
    ) {
        if (!(interactionTarget instanceof EntityMaid maid)) {
            return InteractionResult.PASS;
        }

        if (maid.getOwnerUUID() == null
                || !maid.getOwnerUUID().equals(player.getUUID())) {
            return InteractionResult.PASS;
        }

        if (!player.level().isClientSide
                && player instanceof ServerPlayer serverPlayer) {

            bindToMaid(
                    stack,
                    maid
            );

            if (player.isShiftKeyDown()) {
                setRecordMode(
                        stack,
                        true
                );

                serverPlayer.displayClientMessage(
                        Component.translatable(
                                "message.feastwineallgone.toolbox.record_mode_on",
                                maid.getDisplayName(),
                                DrunkSleepData.getRecordedLocationCount(maid),
                                DrunkSleepData.MAX_RECORDED_LOCATIONS
                        ),
                        false
                );
            } else {
                WgonNetwork.sendOpenMaidSettings(
                        serverPlayer,
                        maid
                );
            }
        }

        return InteractionResult.sidedSuccess(
                player.level().isClientSide
        );
    }

    /**
     * Sneak + right click in the air toggles coordinate-record mode for the
     * maid most recently bound by right-clicking her with this toolbox.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand usedHand
    ) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer) {

            UUID maidId = getBoundMaidUuid(stack);

            if (maidId == null) {
                serverPlayer.displayClientMessage(
                        Component.translatable(
                                "message.feastwineallgone.toolbox.record_no_target"
                        ),
                        false
                );

                return InteractionResultHolder.success(stack);
            }

            boolean next = !isRecordMode(stack);
            setRecordMode(stack, next);

            if (next) {
                EntityMaid maid = findBoundMaid(
                        serverPlayer,
                        maidId
                );

                int count = maid == null
                        ? 0
                        : DrunkSleepData.getRecordedLocationCount(maid);

                serverPlayer.displayClientMessage(
                        Component.translatable(
                                "message.feastwineallgone.toolbox.record_mode_on",
                                getBoundMaidName(stack),
                                count,
                                DrunkSleepData.MAX_RECORDED_LOCATIONS
                        ),
                        false
                );
            } else {
                serverPlayer.displayClientMessage(
                        Component.translatable(
                                "message.feastwineallgone.toolbox.record_mode_off"
                        ),
                        false
                );
            }
        }

        return InteractionResultHolder.sidedSuccess(
                stack,
                level.isClientSide
        );
    }

    /**
     * In record mode, right-click a floor / block face to save a safe feet
     * position for the bound maid. Up to three locations are stored on the
     * maid, not on the toolbox.
     */
    @Override
    public InteractionResult useOn(
            UseOnContext context
    ) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null
                || !isRecordMode(stack)) {
            return InteractionResult.PASS;
        }

        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getLevel() instanceof ServerLevel level)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        UUID maidId = getBoundMaidUuid(stack);
        EntityMaid maid = maidId == null
                ? null
                : findBoundMaid(serverPlayer, maidId);

        if (maid == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable(
                            "message.feastwineallgone.toolbox.record_target_unavailable",
                            getBoundMaidName(stack)
                    ),
                    false
            );
            return InteractionResult.SUCCESS;
        }

        BlockPos requested =
                context.getClickedPos()
                        .relative(
                                context.getClickedFace()
                        );

        BlockPos safe =
                DrunkSleepManager.resolveRecordedSleepSpot(
                        level,
                        requested
                );

        if (safe == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable(
                            "message.feastwineallgone.toolbox.record_unsafe"
                    ),
                    false
            );
            return InteractionResult.SUCCESS;
        }

        DrunkSleepData.AddLocationResult result =
                DrunkSleepData.addRecordedLocation(
                        maid,
                        level.dimension().location(),
                        safe,
                        player.getYRot()
                );

        if (result == DrunkSleepData.AddLocationResult.FULL) {
            serverPlayer.displayClientMessage(
                    Component.translatable(
                            "message.feastwineallgone.toolbox.record_full",
                            DrunkSleepData.MAX_RECORDED_LOCATIONS
                    ),
                    false
            );
            return InteractionResult.SUCCESS;
        }

        int count =
                DrunkSleepData.getRecordedLocationCount(maid);

        serverPlayer.displayClientMessage(
                Component.translatable(
                        result == DrunkSleepData.AddLocationResult.ADDED
                                ? "message.feastwineallgone.toolbox.record_saved"
                                : "message.feastwineallgone.toolbox.record_updated",
                        safe.getX(),
                        safe.getY(),
                        safe.getZ(),
                        count,
                        DrunkSleepData.MAX_RECORDED_LOCATIONS
                ),
                false
        );

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltipComponents,
            TooltipFlag isAdvanced
    ) {
        tooltipComponents.add(
                Component.translatable(
                                "item.feastwineallgone.maid_toolbox.tooltip"
                        )
                        .withStyle(ChatFormatting.GRAY)
        );

        tooltipComponents.add(
                Component.translatable(
                                "item.feastwineallgone.maid_toolbox.tooltip_record"
                        )
                        .withStyle(ChatFormatting.DARK_GRAY)
        );

        UUID maidId = getBoundMaidUuid(stack);
        if (maidId != null) {
            tooltipComponents.add(
                    Component.translatable(
                                    "item.feastwineallgone.maid_toolbox.bound",
                                    getBoundMaidName(stack)
                            )
                            .withStyle(ChatFormatting.AQUA)
            );
        }

        if (isRecordMode(stack)) {
            tooltipComponents.add(
                    Component.translatable(
                                    "item.feastwineallgone.maid_toolbox.recording"
                            )
                            .withStyle(ChatFormatting.GOLD)
            );
        }
    }

    private static void bindToMaid(
            ItemStack stack,
            EntityMaid maid
    ) {
        stack.getOrCreateTag().putUUID(
                BOUND_MAID_UUID,
                maid.getUUID()
        );

        stack.getOrCreateTag().putString(
                BOUND_MAID_NAME,
                maid.getDisplayName().getString()
        );
    }

    private static UUID getBoundMaidUuid(
            ItemStack stack
    ) {
        if (!stack.hasTag()
                || !stack.getTag().hasUUID(BOUND_MAID_UUID)) {
            return null;
        }

        return stack.getTag().getUUID(
                BOUND_MAID_UUID
        );
    }

    private static String getBoundMaidName(
            ItemStack stack
    ) {
        if (!stack.hasTag()) {
            return "?";
        }

        String name = stack.getTag().getString(
                BOUND_MAID_NAME
        );

        return name.isBlank() ? "?" : name;
    }

    private static boolean isRecordMode(
            ItemStack stack
    ) {
        return stack.hasTag()
                && stack.getTag().getBoolean(RECORD_MODE);
    }

    private static void setRecordMode(
            ItemStack stack,
            boolean enabled
    ) {
        stack.getOrCreateTag().putBoolean(
                RECORD_MODE,
                enabled
        );
    }

    private static EntityMaid findBoundMaid(
            ServerPlayer player,
            UUID maidId
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid)
                    || !maid.getUUID().equals(maidId)) {
                continue;
            }

            if (maid.getOwnerUUID() == null
                    || !maid.getOwnerUUID().equals(player.getUUID())) {
                return null;
            }

            return maid;
        }

        return null;
    }
}

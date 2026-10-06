package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.food.FoodBlockHandler;
import com.feastwineallgone.food.FoodConsumeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Direct-bite compatibility for Farming Tales' staged KubeJS dishes.
 *
 * The pack's ftbakRegisterEat(...) foods are not portion pickups.  A player
 * right-clicks the placed dish with an empty hand and the block advances:
 *
 *     xiaolongbao -> xiaolongbao-1 -> ... -> xiaolongbao-4
 *
 * The terminal stage is visually the empty container.  WGON deliberately
 * keeps that terminal stage in the world, matching its existing "incomplete
 * clear" policy for Cookery foods.  This means the maid never pulls an entire
 * steamer/hotpot/noodle ItemStack into her hand and never eats the container.
 *
 * Slice families are excluded: Farming Tales marks their final slice item as
 * stackable_placeable_consumables, so they continue through the detached-item
 * handler and can be picked up one slice at a time.
 */
public final class FarmingTalesDirectBiteHandler
        implements FoodBlockHandler {

    private static final String ID =
            "farmingtales_direct_bite";

    private static final TagKey<Item> PLACEABLE_CONSUMABLES =
            ItemTags.create(
                    new ResourceLocation(
                            "farmingtales",
                            "placeable_consumables"
                    )
            );

    private static final TagKey<Item> STACKABLE_CONSUMABLES =
            ItemTags.create(
                    new ResourceLocation(
                            "farmingtales",
                            "stackable_placeable_consumables"
                    )
            );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(BlockState state) {
        if (!WgonConfig.FARMING_TALES_PLACEABLE_COMPAT_ENABLED.get()) {
            return false;
        }

        DirectBiteInfo info = resolveDirectBiteInfo(state);

        /*
         * The last visual stage is the empty plate/bowl/pot/steamer remnant.
         * Keep it visible and stop selecting it as food.
         */
        return info != null
                && info.stage() < info.maxStage();
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        DirectBiteInfo info = resolveDirectBiteInfo(state);

        if (info == null) {
            return -1;
        }

        return Math.max(
                0,
                info.maxStage() - info.stage()
        );
    }

    @Override
    public FoodConsumeResult consumeOne(
            EntityMaid maid,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        if (level.isClientSide) {
            return FoodConsumeResult.clientSide();
        }

        DirectBiteInfo info = resolveDirectBiteInfo(state);

        if (info == null) {
            return FoodConsumeResult.unsupported();
        }

        int before = Math.max(
                0,
                info.maxStage() - info.stage()
        );

        if (before <= 0) {
            return FoodConsumeResult.empty(
                    ID,
                    0
            );
        }

        Block nextBlock = findBlock(
                info.namespace(),
                info.basePath()
                        + "-"
                        + (info.stage() + 1)
        );

        if (nextBlock == Blocks.AIR) {
            return FoodConsumeResult.failed(
                    ID,
                    before
            );
        }

        BlockState nextState = copySharedProperties(
                state,
                nextBlock.defaultBlockState()
        );

        level.setBlockAndUpdate(
                pos,
                nextState
        );

        /*
         * Presentation mirrors a right-click bite.  No ItemStack is detached
         * and no container item is generated here: the final staged block is
         * itself the visible remnant that WGON intentionally leaves behind.
         */
        if (info.useAnimation() == UseAnim.DRINK) {
            maid.playSound(
                    SoundEvents.GENERIC_DRINK,
                    0.75F,
                    1.0F
            );
        } else {
            maid.playSound(
                    SoundEvents.GENERIC_EAT,
                    0.75F,
                    1.0F
            );
        }

        return FoodConsumeResult.success(
                ID,
                before,
                Math.max(0, before - 1)
        );
    }

    /**
     * Shared guard used by FarmingTalesPlacedConsumableHandler so a direct
     * staged dish can never be claimed by the detached-item path.
     */
    static boolean isDirectBiteStage(BlockState state) {
        return resolveDirectBiteInfo(state) != null;
    }

    private static DirectBiteInfo resolveDirectBiteInfo(
            BlockState state
    ) {
        ResourceLocation blockId =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (blockId == null) {
            return null;
        }

        String namespace = blockId.getNamespace();
        String path = blockId.getPath();

        int stage = 0;
        String basePath = path;

        int dash = path.lastIndexOf('-');

        if (dash >= 0
                && dash + 1 < path.length()) {

            String suffix = path.substring(dash + 1);

            if (allDigits(suffix)) {
                try {
                    stage = Integer.parseInt(suffix);
                    basePath = path.substring(0, dash);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        /*
         * A real staged family must contain base-1 and every member must be
         * tagged as a Farming Tales placeable consumable.
         */
        if (!isPlaceableConsumableBlock(
                findBlock(namespace, basePath + "-1")
        )) {
            return null;
        }

        int maxStage = 0;

        for (int candidate = 1;
             candidate <= 32;
             candidate++) {

            Block candidateBlock = findBlock(
                    namespace,
                    basePath + "-" + candidate
            );

            if (!isPlaceableConsumableBlock(candidateBlock)) {
                break;
            }

            maxStage = candidate;
        }

        if (maxStage <= 0
                || stage > maxStage) {
            return null;
        }

        Block current = stage == 0
                ? findBlock(namespace, basePath)
                : findBlock(
                namespace,
                basePath + "-" + stage
        );

        if (!isPlaceableConsumableBlock(current)) {
            return null;
        }

        /*
         * ftbakRegisterSlice(...) marks its final slice as stackable because
         * that slice can be removed/reinserted.  ftbakRegisterEat(...) does
         * not.  This tag difference cleanly separates the two behaviours
         * without hard-coding a list of dish names.
         */
        ItemStack finalStage = itemStack(
                namespace,
                basePath + "-" + maxStage
        );

        if (finalStage.isEmpty()
                || finalStage.is(STACKABLE_CONSUMABLES)) {
            return null;
        }

        ItemStack currentItem = itemStack(
                namespace,
                stage == 0
                        ? basePath
                        : basePath + "-" + stage
        );

        UseAnim useAnimation = currentItem.isEmpty()
                ? UseAnim.EAT
                : currentItem.getUseAnimation();

        return new DirectBiteInfo(
                namespace,
                basePath,
                stage,
                maxStage,
                useAnimation
        );
    }

    private static boolean isPlaceableConsumableBlock(
            Block block
    ) {
        if (block == null
                || block == Blocks.AIR) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.BLOCKS.getKey(block);

        if (id == null) {
            return false;
        }

        ItemStack stack = itemStack(id);

        return !stack.isEmpty()
                && stack.is(PLACEABLE_CONSUMABLES);
    }

    private static ItemStack itemStack(
            String namespace,
            String path
    ) {
        return itemStack(
                new ResourceLocation(
                        namespace,
                        path
                )
        );
    }

    private static ItemStack itemStack(
            ResourceLocation id
    ) {
        Item item =
                ForgeRegistries.ITEMS.getValue(id);

        if (item == null
                || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private static Block findBlock(
            String namespace,
            String path
    ) {
        Block block =
                ForgeRegistries.BLOCKS.getValue(
                        new ResourceLocation(
                                namespace,
                                path
                        )
                );

        return block == null
                ? Blocks.AIR
                : block;
    }

    private static boolean allDigits(
            String value
    ) {
        if (value.isEmpty()) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copySharedProperties(
            BlockState source,
            BlockState target
    ) {
        BlockState result = target;

        for (Property sourceProperty : source.getProperties()) {
            Property targetProperty =
                    result.getBlock()
                            .getStateDefinition()
                            .getProperty(
                                    sourceProperty.getName()
                            );

            if (targetProperty == null) {
                continue;
            }

            Comparable value =
                    source.getValue(sourceProperty);

            if (targetProperty
                    .getPossibleValues()
                    .contains(value)) {

                result = result.setValue(
                        targetProperty,
                        value
                );
            }
        }

        return result;
    }

    private record DirectBiteInfo(
            String namespace,
            String basePath,
            int stage,
            int maxStage,
            UseAnim useAnimation
    ) {
    }
}

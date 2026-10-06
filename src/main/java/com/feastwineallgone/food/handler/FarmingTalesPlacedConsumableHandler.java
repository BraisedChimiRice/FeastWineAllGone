package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.compat.FarmingTalesDrinkSoundGuard;
import com.feastwineallgone.integration.compat.SpecialDrinkSoundAccess;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * Soft compatibility for Farming Tales' KubeJS placeable consumables.
 *
 * Farming Tales represents several different interactions with visual block
 * variants:
 *
 * - food piles: base, _x2, _x3 ...
 * - bottles/jars/cups: base/_1, _2, _3 ...
 * - multi-serving dishes/slices: base, -1, -2 ...
 *
 * Only the base serving is guaranteed to carry the real EAT/DRINK behaviour.
 * WGON therefore resolves the base serving before touching the world. This is
 * important for stacked drinks: the _2/_3/_4 blocks are display variants, not
 * actual drink items.
 */
public final class FarmingTalesPlacedConsumableHandler
        implements HandheldFoodBlockHandler {

    private static final String ID =
            "farmingtales_placeable_consumable";

    private static final String CONTAINER_NBT =
            "WGONFarmingTalesContainer";

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
    public boolean supports(
            BlockState state
    ) {
        if (!WgonConfig.FARMING_TALES_PLACEABLE_COMPAT_ENABLED.get()) {
            return false;
        }

        /*
         * Farming Tales' direct-eat staged dishes (xiaolongbao, hotpot,
         * noodles, plated meals, etc.) are handled by
         * FarmingTalesDirectBiteHandler.  They must never fall through to
         * this detached-item handler, otherwise WGON would pull an entire
         * dish ItemStack into the maid's hand and could consume the visible
         * empty pot/steamer stage as an item.
         */
        if (FarmingTalesDirectBiteHandler.isDirectBiteStage(state)) {
            return false;
        }

        return resolvePlacedInfo(state) != null;
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        PlacedInfo info =
                resolvePlacedInfo(state);

        return info == null
                ? -1
                : Math.max(1, info.servings());
    }

    @Override
    public ItemStack takeOneServing(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        PlacedInfo info =
                resolvePlacedInfo(state);

        if (info == null
                || info.serving().isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack detached =
                info.serving().copy();

        /*
         * Farming Tales has no one-bottle pickup action for a visual stack in
         * the pack rule requested by WGON. A stack of drinks is therefore
         * picked up as one bundle and stored in the maid backpack.
         */
        if (info.kind() == Kind.STACK
                && info.servings() > 1
                && isActualDrink(info.serving())) {

            level.destroyBlock(
                    pos,
                    false
            );

            detached.setCount(
                    info.servings()
            );

            return detached;
        }

        detached.setCount(1);

        if (!advanceOneServing(
                level,
                pos,
                state,
                info
        )) {
            return ItemStack.EMPTY;
        }

        ResourceLocation container =
                resolveContainerForServing(
                        info,
                        detached
                );

        if (container != null) {
            detached.getOrCreateTag()
                    .putString(
                            CONTAINER_NBT,
                            container.toString()
                    );
        }

        return detached;
    }

    @Override
    public boolean shouldAnimateDetachedUse(
            ItemStack serving
    ) {
        if (serving == null
                || serving.isEmpty()) {
            return true;
        }

        /*
         * Farming Tales currently gives braised_chicken the DRINK use
         * animation in KubeJS even though it is a solid meal.  Starting
         * Minecraft's normal item-use animation would therefore make the
         * maid raise the whole chicken like a bottle.  Skip that visual use
         * and let WGON present it as a normal bite instead.
         */
        if (isDrinkAnimationFood(serving)) {
            return false;
        }

        return !isPickupOnlyDetachedServing(serving);
    }

    @Override
    public boolean isPickupOnlyDetachedServing(
            ItemStack serving
    ) {
        return serving != null
                && !serving.isEmpty()
                && serving.getCount() > 1
                && isActualDrink(serving);
    }

    @Override
    public boolean consumeDetachedServing(
            EntityMaid maid,
            ServerLevel level,
            ItemStack serving
    ) {
        if (serving == null
                || serving.isEmpty()
                || !serving.is(PLACEABLE_CONSUMABLES)) {
            return false;
        }

        /*
         * Stacked drinks are pickup-only. Do not fake a drinking animation or
         * consume one bottle from the bundle: the whole visible stack goes to
         * the backpack exactly as requested.
         */
        if (isPickupOnlyDetachedServing(serving)) {

            CookeryDrinkAccess.storeOrDrop(
                    level,
                    maid,
                    serving
            );

            maid.playSound(
                    SoundEvents.ITEM_PICKUP,
                    0.6F,
                    1.0F
            );

            return true;
        }

        ItemStack one =
                serving.copy();

        one.setCount(1);

        ResourceLocation explicitContainer =
                readAndRemoveContainerMarker(one);

        ItemStack before =
                one.copy();

        boolean actualDrink =
                isActualDrink(one);

        ItemStack remainder =
                actualDrink
                        ? FarmingTalesDrinkSoundGuard
                        .finishUsingItemWithoutGenericEat(
                                one,
                                level,
                                maid
                        )
                        : one.finishUsingItem(
                                level,
                                maid
                        );

        /*
         * Some KubeJS placeables are interaction-defined rather than normal
         * edible items. If finishUsingItem returns the untouched stack, the
         * placed serving was still removed by WGON, so putting it back would
         * duplicate food. Genuine container remainders are kept.
         */
        boolean unchanged =
                !remainder.isEmpty()
                        && ItemStack.isSameItemSameTags(
                        before,
                        remainder
                )
                        && remainder.getCount()
                        >= before.getCount();

        if (!unchanged) {
            CookeryDrinkAccess.storeOrDrop(
                    level,
                    maid,
                    remainder
            );
        }

        if (explicitContainer != null) {
            Item containerItem =
                    ForgeRegistries.ITEMS.getValue(
                            explicitContainer
                    );

            if (containerItem != null
                    && containerItem != Items.AIR) {

                CookeryDrinkAccess.storeOrDrop(
                        level,
                        maid,
                        new ItemStack(containerItem)
                );
            }
        }

        if (actualDrink) {

            maid.playSound(
                    SoundEvents.GENERIC_DRINK,
                    0.75F,
                    1.0F
            );

            /*
             * Some optional drinks have a signature post-drink sound that
             * their original mod only emits on the player consumption path.
             * Re-emit that registered sound for maid consumption without
             * linking the third-party mod as a hard dependency.
             */
            SpecialDrinkSoundAccess.playAfterDrinkIfNeeded(
                    level,
                    maid,
                    one
            );
        } else {
            maid.playSound(
                    SoundEvents.GENERIC_EAT,
                    0.75F,
                    1.0F
            );
        }

        return true;
    }

    private static boolean advanceOneServing(
            ServerLevel level,
            BlockPos pos,
            BlockState currentState,
            PlacedInfo info
    ) {
        if (info.nextBlock() == null
                || info.nextBlock() == Blocks.AIR) {

            level.destroyBlock(
                    pos,
                    false
            );

            return true;
        }

        BlockState nextState =
                copySharedProperties(
                        currentState,
                        info.nextBlock()
                                .defaultBlockState()
                );

        level.setBlockAndUpdate(
                pos,
                nextState
        );

        return true;
    }

    private static PlacedInfo resolvePlacedInfo(
            BlockState state
    ) {
        ResourceLocation blockId =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (blockId == null) {
            return null;
        }

        String path =
                blockId.getPath();

        /*
         * Dash stages always move forward: dish -> dish-1 -> dish-2 -> ...
         * Both direct-eat dishes and slice-style bakery foods use this visual
         * convention in Farming Tales.
         */
        StagePath stagePath =
                resolveStagePath(
                        blockId.getNamespace(),
                        path
                );

        if (stagePath != null) {
            ItemStack serving =
                    resolveStageServing(
                            blockId.getNamespace(),
                            stagePath
                    );

            if (!serving.isEmpty()) {
                Block next =
                        stagePath.stage() < stagePath.maxStage()
                                ? findBlock(
                                blockId.getNamespace(),
                                stagePath.basePath()
                                        + "-"
                                        + (stagePath.stage() + 1)
                        )
                                : null;

                return new PlacedInfo(
                        Kind.STAGE,
                        serving,
                        stagePath.maxStage()
                                - stagePath.stage()
                                + 1,
                        next,
                        stagePath.basePath(),
                        stagePath.stage()
                                == stagePath.maxStage()
                );
            }
        }

        /*
         * Do not rely on Farming Tales' block stack-target tag here. The
         * pack only tags stack levels that can accept one more item, so the
         * maximum visual stack (normally x4) is deliberately absent from the
         * tag. That made x4 drinks fall through as SINGLE display items and
         * WGON tried to consume the display block item instead of collecting
         * four real drinks.
         *
         * resolveStackPath() is already self-validating: it only succeeds
         * when the resolved base serving is in the stackable-consumable item
         * tag. Therefore it is safe, and necessary, to probe every supported
         * placed consumable path.
         */
        StackPath stackPath =
                resolveStackPath(
                        blockId.getNamespace(),
                        path
                );

        if (stackPath != null) {
            Block next =
                    stackPath.amount() <= 1
                            ? null
                            : findStackBlock(
                            blockId.getNamespace(),
                            stackPath,
                            stackPath.amount() - 1
                    );

            return new PlacedInfo(
                    Kind.STACK,
                    stackPath.serving(),
                    stackPath.amount(),
                    next,
                    stackPath.basePath(),
                    stackPath.amount() == 1
            );
        }

        ItemStack exact =
                itemStack(
                        blockId
                );

        if (!exact.isEmpty()
                && exact.is(PLACEABLE_CONSUMABLES)) {

            return new PlacedInfo(
                    Kind.SINGLE,
                    exact,
                    1,
                    null,
                    path,
                    true
            );
        }

        return null;
    }

    private static StagePath resolveStagePath(
            String namespace,
            String path
    ) {
        int stage = 0;
        String base = path;

        int dash =
                path.lastIndexOf('-');

        if (dash >= 0
                && dash + 1 < path.length()) {

            String suffix =
                    path.substring(dash + 1);

            if (allDigits(suffix)) {
                try {
                    stage = Integer.parseInt(suffix);
                    base = path.substring(0, dash);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        /*
         * A real stage family must have at least base-1. This avoids treating
         * ordinary single blocks as a staged dish.
         */
        Block firstStage =
                findBlock(
                        namespace,
                        base + "-1"
                );

        if (!isPlaceableConsumableBlock(firstStage)) {
            return null;
        }

        int maxStage = 0;

        for (int candidate = 1;
             candidate <= 32;
             candidate++) {

            Block block =
                    findBlock(
                            namespace,
                            base + "-" + candidate
                    );

            if (!isPlaceableConsumableBlock(block)) {
                break;
            }

            maxStage = candidate;
        }

        if (stage > maxStage) {
            return null;
        }

        Block current =
                stage == 0
                        ? findBlock(namespace, base)
                        : findBlock(
                        namespace,
                        base + "-" + stage
                );

        if (!isPlaceableConsumableBlock(current)) {
            return null;
        }

        return new StagePath(
                base,
                stage,
                maxStage
        );
    }

    private static ItemStack resolveStageServing(
            String namespace,
            StagePath stagePath
    ) {
        /*
         * Slice-style foods mark their final slice item as the stack target.
         * Prefer that item for every bite so the maid holds one slice rather
         * than an entire pie/cake model. Direct-eat dishes do not have this tag
         * and therefore use their current stage item.
         */
        ItemStack finalStage =
                itemStack(
                        namespace,
                        stagePath.basePath()
                                + "-"
                                + stagePath.maxStage()
                );

        if (!finalStage.isEmpty()
                && finalStage.is(STACKABLE_CONSUMABLES)) {
            return finalStage;
        }

        String currentPath =
                stagePath.stage() == 0
                        ? stagePath.basePath()
                        : stagePath.basePath()
                        + "-"
                        + stagePath.stage();

        return itemStack(
                namespace,
                currentPath
        );
    }

    private static StackPath resolveStackPath(
            String namespace,
            String blockPath
    ) {
        String noX =
                stripNumericSuffix(
                        blockPath,
                        "_x"
                );

        if (noX != null) {
            Integer amount =
                    parseNumericSuffix(
                            blockPath,
                            noX + "_x"
                    );

            ItemStack source =
                    itemStack(
                            namespace,
                            noX
                    );

            if (amount != null
                    && isStackServing(source)) {

                return new StackPath(
                        StackStyle.X,
                        noX,
                        source,
                        Math.max(1, amount)
                );
            }
        }

        String noUnderscore =
                stripNumericSuffix(
                        blockPath,
                        "_"
                );

        if (noUnderscore != null) {
            Integer amount =
                    parseNumericSuffix(
                            blockPath,
                            noUnderscore + "_"
                    );

            if (amount != null) {
                ItemStack noOneSuffix =
                        itemStack(
                                namespace,
                                noUnderscore
                        );

                if (isStackServing(noOneSuffix)) {
                    return new StackPath(
                            StackStyle.UNDERSCORE_NO_ONE,
                            noUnderscore,
                            noOneSuffix,
                            Math.max(1, amount)
                    );
                }

                ItemStack withOneSuffix =
                        itemStack(
                                namespace,
                                noUnderscore + "_1"
                        );

                if (isStackServing(withOneSuffix)) {
                    return new StackPath(
                            StackStyle.UNDERSCORE_WITH_ONE,
                            noUnderscore,
                            withOneSuffix,
                            Math.max(1, amount)
                    );
                }
            }
        }

        /*
         * Amount-one variants can be a bare base item (bottles) or an explicit
         * _1 item (cups/juice/jam). They are themselves stack targets.
         */
        ItemStack exact =
                itemStack(
                        namespace,
                        blockPath
                );

        if (isStackServing(exact)) {
            if (blockPath.endsWith("_1")) {
                return new StackPath(
                        StackStyle.UNDERSCORE_WITH_ONE,
                        blockPath.substring(
                                0,
                                blockPath.length() - 2
                        ),
                        exact,
                        1
                );
            }

            return new StackPath(
                    blockPath.contains("_x")
                            ? StackStyle.X
                            : StackStyle.UNDERSCORE_NO_ONE,
                    blockPath,
                    exact,
                    1
            );
        }

        return null;
    }

    private static Block findStackBlock(
            String namespace,
            StackPath stack,
            int amount
    ) {
        String path;

        switch (stack.style()) {
            case X ->
                    path = amount <= 1
                            ? stack.basePath()
                            : stack.basePath()
                            + "_x"
                            + amount;

            case UNDERSCORE_WITH_ONE ->
                    path = stack.basePath()
                            + "_"
                            + amount;

            case UNDERSCORE_NO_ONE ->
                    path = amount <= 1
                            ? stack.basePath()
                            : stack.basePath()
                            + "_"
                            + amount;

            default -> {
                return null;
            }
        }

        Block block =
                findBlock(
                        namespace,
                        path
                );

        return block == Blocks.AIR
                ? null
                : block;
    }

    private static boolean isStackServing(
            ItemStack stack
    ) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(PLACEABLE_CONSUMABLES)
                && stack.is(STACKABLE_CONSUMABLES);
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

        ItemStack stack =
                itemStack(id);

        return !stack.isEmpty()
                && stack.is(PLACEABLE_CONSUMABLES);
    }

    private static boolean isActualDrink(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()
                || stack.getUseAnimation() != UseAnim.DRINK) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.ITEMS.getKey(
                        stack.getItem()
                );

        if (id == null) {
            return true;
        }

        String path =
                id.getPath();

        /*
         * Farming Tales uses DRINK as a presentation animation for several
         * things that are not beverages.  In particular braised_chicken is
         * explicitly configured with item.useAnimation("drink") in the pack's
         * KubeJS.  Jams/sauce do the same.  They must stay on the food path,
         * otherwise a stacked display is treated like bottles and a single
         * meal is visually gulped down like water.
         */
        return !isDrinkAnimationFoodPath(path);
    }

    private static boolean isDrinkAnimationFood(
            ItemStack stack
    ) {
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        ResourceLocation id =
                ForgeRegistries.ITEMS.getKey(
                        stack.getItem()
                );

        return id != null
                && isDrinkAnimationFoodPath(id.getPath());
    }

    private static boolean isDrinkAnimationFoodPath(
            String path
    ) {
        return path != null
                && (path.contains("_jam")
                || path.equals("chocolate_sauce")
                || path.equals("braised_chicken"));
    }

    private static ResourceLocation resolveContainerForServing(
            PlacedInfo info,
            ItemStack serving
    ) {
        String base =
                info.basePath();

        /*
         * KubeJS food.eaten callbacks only give these containers to Player.
         * A maid is a LivingEntity, not a Player, so WGON preserves the same
         * remnants explicitly instead of silently deleting the pot/bowl/jar.
         */
        if (info.kind() == Kind.STAGE
                && info.finalServing()) {

            return switch (base) {
                case "xiaolongbao" ->
                        ResourceLocation.tryParse(
                                "kaleidoscope_cookery:steamer"
                        );

                case "banmian",
                     "fruitwood_steak",
                     "genghis_chicken",
                     "stinky_fried_chicken",
                     "douzhi",
                     "amaranth_stem" ->
                        ResourceLocation.tryParse(
                                "minecraft:bowl"
                        );

                case "hotpot" ->
                        ResourceLocation.tryParse(
                                "youkaisfeasts:short_iron_pot"
                        );

                default -> null;
            };
        }

        ResourceLocation itemId =
                ForgeRegistries.ITEMS.getKey(
                        serving.getItem()
                );

        if (itemId != null) {
            String path = itemId.getPath();

            if (path.equals("braised_chicken")) {
                return ResourceLocation.tryParse(
                        "minecraft:flower_pot"
                );
            }

            if (path.contains("_jam")
                    || path.equals("chocolate_sauce")) {
                return ResourceLocation.tryParse(
                        "bakery:jar"
                );
            }
        }

        return null;
    }

    private static ResourceLocation readAndRemoveContainerMarker(
            ItemStack stack
    ) {
        if (!stack.hasTag()) {
            return null;
        }

        CompoundTag tag =
                stack.getTag();

        if (tag == null
                || !tag.contains(CONTAINER_NBT)) {
            return null;
        }

        String value =
                tag.getString(
                        CONTAINER_NBT
                );

        tag.remove(
                CONTAINER_NBT
        );

        return ResourceLocation.tryParse(
                value
        );
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
                ForgeRegistries.ITEMS.getValue(
                        id
                );

        if (item == null
                || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
                new ItemStack(item);

        stack.setCount(1);
        return stack;
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

    private static String stripNumericSuffix(
            String value,
            String marker
    ) {
        int index =
                value.lastIndexOf(marker);

        if (index < 0) {
            return null;
        }

        String suffix =
                value.substring(
                        index + marker.length()
                );

        if (suffix.isEmpty()
                || !allDigits(suffix)) {
            return null;
        }

        return value.substring(
                0,
                index
        );
    }

    private static Integer parseNumericSuffix(
            String value,
            String prefix
    ) {
        if (!value.startsWith(prefix)
                || value.length() == prefix.length()) {
            return null;
        }

        String suffix =
                value.substring(
                        prefix.length()
                );

        if (!allDigits(suffix)) {
            return null;
        }

        try {
            return Integer.parseInt(
                    suffix
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean allDigits(
            String value
    ) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(
                    value.charAt(i)
            )) {
                return false;
            }
        }

        return !value.isEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copySharedProperties(
            BlockState source,
            BlockState target
    ) {
        BlockState result =
                target;

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
                    source.getValue(
                            sourceProperty
                    );

            if (targetProperty
                    .getPossibleValues()
                    .contains(value)) {

                result =
                        result.setValue(
                                targetProperty,
                                value
                        );
            }
        }

        return result;
    }

    private enum Kind {
        SINGLE,
        STACK,
        STAGE
    }

    private enum StackStyle {
        X,
        UNDERSCORE_NO_ONE,
        UNDERSCORE_WITH_ONE
    }

    private record PlacedInfo(
            Kind kind,
            ItemStack serving,
            int servings,
            Block nextBlock,
            String basePath,
            boolean finalServing
    ) {
    }

    private record StagePath(
            String basePath,
            int stage,
            int maxStage
    ) {
    }

    private record StackPath(
            StackStyle style,
            String basePath,
            ItemStack serving,
            int amount
    ) {
    }
}

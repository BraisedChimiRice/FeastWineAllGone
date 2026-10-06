package com.feastwineallgone.integration.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopetavern.block.plant.GrapeCropBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopetavern.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

/**
 * Fruit harvesting used by idle fruit tasting.
 *
 * Most supported fruit plants are harvested non-destructively.  Kaleidoscope
 * Tavern grapes are fruit blocks grown by a persistent trellis, so tasting one
 * removes only the ripe grape block and leaves the actual vine/trellis intact.
 * Vanilla melon and Fruits Delight durian are deliberate cutting targets:
 * a maid must carry an axe or sword, visibly cuts the fruit open, eats one
 * piece and stores the remaining products.
 *
 * Optional-mod support is registry-id based, keeping these integrations soft.
 */
public final class FruitHarvestAccess {

    private static final ResourceLocation SWEET_BERRY_BUSH =
            new ResourceLocation("minecraft", "sweet_berry_bush");

    private static final ResourceLocation CAVE_VINES =
            new ResourceLocation("minecraft", "cave_vines");

    private static final ResourceLocation CAVE_VINES_PLANT =
            new ResourceLocation("minecraft", "cave_vines_plant");

    private static final ResourceLocation VANILLA_MELON =
            new ResourceLocation("minecraft", "melon");

    /*
     * Fruits Delight 1.1.3 soft-compat IDs.
     *
     * The supplied mod JAR defines the Farmer's Delight cutting result for one
     * durian as:
     *   6x durian_flesh
     *   1x durian_helmet
     *   1x durian_shell
     *   1x durian_sapling
     *
     * WGON keeps this integration registry-id based so Fruits Delight remains
     * optional at compile/runtime.
     */
    private static final ResourceLocation FD_DURIAN =
            new ResourceLocation("fruitsdelight", "durian");

    private static final ResourceLocation FD_DURIAN_FLESH =
            new ResourceLocation("fruitsdelight", "durian_flesh");

    private static final ResourceLocation FD_DURIAN_HELMET =
            new ResourceLocation("fruitsdelight", "durian_helmet");

    private static final ResourceLocation FD_DURIAN_SHELL =
            new ResourceLocation("fruitsdelight", "durian_shell");

    private static final ResourceLocation FD_DURIAN_SAPLING =
            new ResourceLocation("fruitsdelight", "durian_sapling");

    private static final ResourceLocation BF_PICK_SOUND =
            new ResourceLocation("bountifulfares", "hanging_fruit_pick");

    private static final ResourceLocation BF_HANGING_GOLDEN_APPLE =
            new ResourceLocation("bountifulfares", "hanging_golden_apple");

    /**
     * Safe first-wave Bountiful Fares fruit-tree support.
     *
     * Ordinary orchard fruits are always eligible. The ripe golden apple is
     * handled separately by FruitTastingManager because it has its own
     * configurable "sneaky snack" probability. Withered/hoary special apples
     * remain excluded from autonomous tasting.
     */
    private static final Map<ResourceLocation, ResourceLocation> BOUNTIFUL_FRUITS =
            Map.of(
                    new ResourceLocation("bountifulfares", "hanging_apple"),
                    new ResourceLocation("minecraft", "apple"),
                    new ResourceLocation("bountifulfares", "hanging_orange"),
                    new ResourceLocation("bountifulfares", "orange"),
                    new ResourceLocation("bountifulfares", "hanging_lemon"),
                    new ResourceLocation("bountifulfares", "lemon"),
                    new ResourceLocation("bountifulfares", "hanging_plum"),
                    new ResourceLocation("bountifulfares", "plum")
            );

    /**
     * Kaleidoscope Tavern is a required dependency of FWAG, so grape support
     * can use the real GrapeCropBlock/ModBlocks definitions instead of guessing
     * registry ids. This is intentionally stricter and more reliable than the
     * first compatibility pass.
     */

    private FruitHarvestAccess() {
    }

    /**
     * Preview fruit that does not depend on the maid's inventory.
     */
    public static Optional<ItemStack> previewOne(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId == null) {
            return Optional.empty();
        }

        if (SWEET_BERRY_BUSH.equals(blockId)) {
            if (!(state.getBlock() instanceof SweetBerryBushBlock)
                    || state.getValue(SweetBerryBushBlock.AGE) <= 1) {
                return Optional.empty();
            }

            return Optional.of(new ItemStack(Items.SWEET_BERRIES));
        }

        if (CAVE_VINES.equals(blockId)
                || CAVE_VINES_PLANT.equals(blockId)) {
            if (!state.hasProperty(BlockStateProperties.BERRIES)
                    || !state.getValue(BlockStateProperties.BERRIES)) {
                return Optional.empty();
            }

            return Optional.of(new ItemStack(Items.GLOW_BERRIES));
        }

        if (BF_HANGING_GOLDEN_APPLE.equals(blockId)) {
            IntegerProperty age = findIntegerProperty(state, "age");

            if (age == null || !isAtMaximum(state, age)) {
                return Optional.empty();
            }

            return Optional.of(new ItemStack(Items.GOLDEN_APPLE));
        }

        ResourceLocation fruitItemId = BOUNTIFUL_FRUITS.get(blockId);

        if (fruitItemId != null) {
            IntegerProperty age = findIntegerProperty(state, "age");

            if (age == null || !isAtMaximum(state, age)) {
                return Optional.empty();
            }

            return stackFor(fruitItemId);
        }

        Optional<ItemStack> tavernGrape = previewTavernGrape(state);
        if (tavernGrape.isPresent()) {
            return tavernGrape;
        }

        return Optional.empty();
    }

    /**
     * Maid-aware preview. Vanilla melon only becomes a fruit target while the
     * maid actually carries something capable of cutting it open.
     */
    public static Optional<ItemStack> previewOne(
            ServerLevel level,
            EntityMaid maid,
            BlockPos pos
    ) {
        if (isMelonBlock(level, pos)) {
            return hasCuttingTool(maid)
                    ? Optional.of(new ItemStack(Items.MELON_SLICE))
                    : Optional.empty();
        }

        /*
         * Fruits Delight's orchard durian is a FallingBlock. A naturally
         * fallen fruit normally becomes a fruitsdelight:durian BLOCK after it
         * lands, rather than remaining an ItemEntity.
         */
        if (isDurianBlock(level, pos)) {
            return hasCuttingTool(maid)
                    ? stackFor(FD_DURIAN_FLESH)
                    : Optional.empty();
        }

        return previewOne(level, pos);
    }

    public static boolean isGoldenAppleTarget(
            ServerLevel level,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (!BF_HANGING_GOLDEN_APPLE.equals(blockId)) {
            return false;
        }

        IntegerProperty age = findIntegerProperty(state, "age");
        return age != null && isAtMaximum(state, age);
    }

    public static boolean isMelonTarget(
            ServerLevel level,
            EntityMaid maid,
            BlockPos pos
    ) {
        return isMelonBlock(level, pos) && hasCuttingTool(maid);
    }

    public static boolean isDurianTarget(
            ServerLevel level,
            EntityMaid maid,
            BlockPos pos
    ) {
        return isDurianBlock(level, pos) && hasCuttingTool(maid);
    }

    public static boolean isCuttingFruitTarget(
            ServerLevel level,
            EntityMaid maid,
            BlockPos pos
    ) {
        return isMelonTarget(level, maid, pos)
                || isDurianTarget(level, maid, pos);
    }

    /**
     * Returns a one-count visual copy of the first axe/sword available to the
     * maid. getAvailableInv(false) covers the maid's usable backpack slots and
     * hands, matching the player's expectation of "carrying" the tool.
     */
    public static ItemStack findCuttingTool(
            EntityMaid maid
    ) {
        if (maid == null) {
            return ItemStack.EMPTY;
        }

        ItemStack found = findCuttingToolInHandler(maid.getAvailableInv(false));
        if (!found.isEmpty()) {
            return found;
        }

        /*
         * Touhou Little Maid keeps task equipment in a separate task inventory.
         * The first implementation only searched getAvailableInv(false), which
         * meant an axe/sword placed in a task/tool slot was invisible to WGON.
         */
        return findCuttingToolInHandler(maid.getTaskInv());
    }

    public static boolean hasCuttingTool(
            EntityMaid maid
    ) {
        return !findCuttingTool(maid).isEmpty();
    }

    public static ItemStack harvestOne(
            ServerLevel level,
            EntityMaid maid,
            BlockPos pos
    ) {
        Optional<ItemStack> preview = previewOne(level, maid, pos);

        if (preview.isEmpty()) {
            return ItemStack.EMPTY;
        }

        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId == null) {
            return ItemStack.EMPTY;
        }

        if (SWEET_BERRY_BUSH.equals(blockId)) {
            /*
             * Vanilla right-click harvesting resets a mature bush to age 1.
             * WGON deliberately takes exactly one berry and never breaks the
             * bush, even though vanilla can normally yield multiple berries.
             */
            level.setBlock(
                    pos,
                    state.setValue(SweetBerryBushBlock.AGE, 1),
                    2
            );

            level.playSound(
                    null,
                    pos,
                    SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F + level.random.nextFloat() * 0.4F
            );

            return preview.get();
        }

        if (CAVE_VINES.equals(blockId)
                || CAVE_VINES_PLANT.equals(blockId)) {
            level.setBlock(
                    pos,
                    state.setValue(BlockStateProperties.BERRIES, false),
                    2
            );

            level.playSound(
                    null,
                    pos,
                    SoundEvents.CAVE_VINES_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F + level.random.nextFloat() * 0.4F
            );

            return preview.get();
        }

        if (BOUNTIFUL_FRUITS.containsKey(blockId)
                || BF_HANGING_GOLDEN_APPLE.equals(blockId)) {
            IntegerProperty age = findIntegerProperty(state, "age");

            if (age == null || !isAtMaximum(state, age)) {
                return ItemStack.EMPTY;
            }

            int resetAge = age.getPossibleValues()
                    .stream()
                    .min(Integer::compareTo)
                    .orElse(0);

            /*
             * Do not call Bountiful Fares' player-use method here. Its own
             * fruitReplaceWhenPicked option may delete the hanging fruit block.
             * WGON keeps the plant and resets only AGE.
             */
            level.setBlock(
                    pos,
                    state.setValue(age, resetAge),
                    2
            );

            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(BF_PICK_SOUND);

            level.playSound(
                    null,
                    pos,
                    sound != null
                            ? sound
                            : SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    1.0F,
                    0.8F + level.random.nextFloat() * 0.4F
            );

            return preview.get();
        }

        if (isTavernGrapeState(state)) {
            if (!(state.getBlock() instanceof GrapeCropBlock grapeCrop)
                    || !grapeCrop.isMaxAge(state)) {
                return ItemStack.EMPTY;
            }

            /*
             * Tavern's ripe grape bunch is a separate block hanging under the
             * trellis. Empty-hand harvesting removes that bunch while the
             * trellis stays in place, so mirror exactly that world change and
             * reserve one grape for the maid's tasting animation.
             */
            level.destroyBlock(pos, false, maid);
            level.playSound(
                    null,
                    pos,
                    SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                    SoundSource.BLOCKS,
                    0.9F,
                    0.9F + level.random.nextFloat() * 0.2F
            );

            return preview.get();
        }

        if (FD_DURIAN.equals(blockId)) {
            if (!hasCuttingTool(maid)) {
                return ItemStack.EMPTY;
            }

            ItemStack snack =
                    createDurianCutProducts(
                            level,
                            maid
                    );

            if (snack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            /*
             * Remove the landed durian without letting the block drop another
             * intact durian item. The cutting outputs above are authoritative.
             */
            level.destroyBlock(
                    pos,
                    false,
                    maid
            );

            level.levelEvent(
                    2001,
                    pos,
                    Block.getId(state)
            );

            return snack;
        }

        if (VANILLA_MELON.equals(blockId)) {
            if (!hasCuttingTool(maid)) {
                return ItemStack.EMPTY;
            }

            /*
             * Vanilla melons normally yield 3-7 slices.
             *
             * V2 behaviour:
             *   - one slice goes into the visible tasting/eating animation;
             *   - every remaining slice is immediately picked up into the
             *     maid's backpack;
             *   - if the backpack is full, only the true overflow is dropped
             *     at her feet.
             */
            int totalSlices = 3 + level.random.nextInt(5);
            int remainingSlices = Math.max(0, totalSlices - 1);

            level.destroyBlock(pos, false, maid);

            if (remainingSlices > 0) {
                storeInBackpackOrDrop(
                        level,
                        maid,
                        new ItemStack(Items.MELON_SLICE, remainingSlices)
                );
                maid.tryPlayMaidPickupSound();
            }

            return new ItemStack(Items.MELON_SLICE);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Returns true only for a dropped whole Fruits Delight durian.
     */
    public static boolean isDroppedDurian(
            ItemEntity entity
    ) {
        if (entity == null
                || !entity.isAlive()
                || entity.getItem().isEmpty()) {
            return false;
        }

        ResourceLocation itemId =
                ForgeRegistries.ITEMS.getKey(
                        entity.getItem().getItem()
                );

        return FD_DURIAN.equals(itemId);
    }

    /**
     * Cuts exactly ONE durian from a ground ItemEntity.
     *
     * The supplied Fruits Delight 1.1.3 JAR's own cutting recipe produces:
     *   6 flesh + helmet + shell + sapling.
     *
     * WGON mirrors those outputs:
     *   - 1 flesh is returned for the maid to admire/eat;
     *   - 5 flesh are stored in the backpack;
     *   - the shell and sapling are stored too;
     *   - if the maid has no head item, she immediately equips the
     *     durian_helmet; otherwise the helmet is stored in the backpack;
     *   - inventory overflow is never deleted and instead drops at her feet.
     *
     * If a ground stack contains multiple whole durians, only one is cut per
     * snack session. The untouched durians remain in the ItemEntity.
     */
    public static ItemStack cutOneDroppedDurian(
            ServerLevel level,
            EntityMaid maid,
            ItemEntity entity
    ) {
        if (level == null
                || maid == null
                || !isDroppedDurian(entity)
                || !hasCuttingTool(maid)) {
            return ItemStack.EMPTY;
        }

        ItemStack groundStack =
                entity.getItem();

        groundStack.shrink(1);

        if (groundStack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(groundStack);
        }

        return createDurianCutProducts(
                level,
                maid
        );
    }

    /**
     * Creates the Fruits Delight 1.1.3 cutting outputs for ONE whole durian.
     *
     * One flesh is returned for the visible eating animation. The other five
     * flesh, shell and sapling go to the maid's backpack. If the maid has no
     * head item, the durian helmet is equipped immediately; otherwise it is
     * stored in the backpack.
     */
    private static ItemStack createDurianCutProducts(
            ServerLevel level,
            EntityMaid maid
    ) {
        ItemStack flesh =
                stackFor(FD_DURIAN_FLESH)
                        .orElse(ItemStack.EMPTY);

        if (flesh.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainingFlesh =
                flesh.copy();
        remainingFlesh.setCount(5);

        storeInBackpackOrDrop(
                level,
                maid,
                remainingFlesh
        );

        ItemStack shell =
                stackFor(FD_DURIAN_SHELL)
                        .orElse(ItemStack.EMPTY);

        if (!shell.isEmpty()) {
            storeInBackpackOrDrop(
                    level,
                    maid,
                    shell
            );
        }

        ItemStack sapling =
                stackFor(FD_DURIAN_SAPLING)
                        .orElse(ItemStack.EMPTY);

        if (!sapling.isEmpty()) {
            storeInBackpackOrDrop(
                    level,
                    maid,
                    sapling
            );
        }

        ItemStack helmet =
                stackFor(FD_DURIAN_HELMET)
                        .orElse(ItemStack.EMPTY);

        if (!helmet.isEmpty()) {
            if (maid.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                ItemStack equipped =
                        helmet.copy();
                equipped.setCount(1);

                maid.setItemSlot(
                        EquipmentSlot.HEAD,
                        equipped
                );
            } else {
                storeInBackpackOrDrop(
                        level,
                        maid,
                        helmet
                );
            }
        }

        maid.tryPlayMaidPickupSound();

        ItemStack snack =
                flesh.copy();
        snack.setCount(1);
        return snack;
    }

    /**
     * Insert into TLM's normal available maid inventory. The remainder is
     * dropped only when the backpack has no room.
     */
    public static ItemStack storeInBackpackOrDrop(
            ServerLevel level,
            EntityMaid maid,
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder =
                ItemHandlerHelper.insertItemStacked(
                        maid.getAvailableInv(false),
                        stack.copy(),
                        false
                );

        if (!remainder.isEmpty()) {
            Block.popResource(
                    level,
                    maid.blockPosition(),
                    remainder
            );
        }

        return remainder;
    }

    private static Optional<ItemStack> stackFor(
            ResourceLocation itemId
    ) {
        Item fruitItem = ForgeRegistries.ITEMS.getValue(itemId);

        if (fruitItem == null || fruitItem == Items.AIR) {
            return Optional.empty();
        }

        return Optional.of(new ItemStack(fruitItem));
    }

    private static Optional<ItemStack> previewTavernGrape(
            BlockState state
    ) {
        if (!(state.getBlock() instanceof GrapeCropBlock grapeCrop)
                || !grapeCrop.isMaxAge(state)) {
            return Optional.empty();
        }

        if (state.is(ModBlocks.GRAPE_CROP.get())) {
            return Optional.of(new ItemStack(ModItems.GRAPE.get()));
        }

        if (state.is(ModBlocks.ICE_GRAPE_CROP.get())) {
            return Optional.of(new ItemStack(ModItems.ICE_GRAPE.get()));
        }

        if (state.is(ModBlocks.GOLD_GRAPE_CROP.get())) {
            return Optional.of(new ItemStack(ModItems.GOLD_GRAPE.get()));
        }

        return Optional.empty();
    }

    private static boolean isTavernGrapeState(
            BlockState state
    ) {
        return state.is(ModBlocks.GRAPE_CROP.get())
                || state.is(ModBlocks.ICE_GRAPE_CROP.get())
                || state.is(ModBlocks.GOLD_GRAPE_CROP.get());
    }

    private static ItemStack findCuttingToolInHandler(
            IItemHandler inventory
    ) {
        if (inventory == null) {
            return ItemStack.EMPTY;
        }

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);

            if (isCuttingTool(stack)) {
                ItemStack visual = stack.copy();
                visual.setCount(1);
                return visual;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean isMelonBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        return level.getBlockState(pos).is(Blocks.MELON);
    }

    private static boolean isDurianBlock(
            ServerLevel level,
            BlockPos pos
    ) {
        if (level == null || pos == null) {
            return false;
        }

        ResourceLocation blockId =
                ForgeRegistries.BLOCKS.getKey(
                        level.getBlockState(pos).getBlock()
                );

        return FD_DURIAN.equals(blockId);
    }

    private static boolean isCuttingTool(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof SwordItem;
    }

    private static IntegerProperty findIntegerProperty(
            BlockState state,
            String name
    ) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty
                    && name.equals(integerProperty.getName())) {
                return integerProperty;
            }
        }

        return null;
    }

    private static boolean isAtMaximum(
            BlockState state,
            IntegerProperty property
    ) {
        int current = state.getValue(property);
        int maximum = property.getPossibleValues()
                .stream()
                .max(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);

        return current >= maximum;
    }
}

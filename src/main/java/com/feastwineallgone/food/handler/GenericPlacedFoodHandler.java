package com.feastwineallgone.food.handler;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.food.HandheldFoodBlockHandler;
import com.feastwineallgone.integration.cookery.CookeryDrinkAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Conservative generic compatibility for placed food blocks from mods that
 * WGON does not know directly.
 *
 * The handler deliberately runs after all dedicated handlers.  It recognises
 * common food-state conventions (bites / servings / cuts / stack), finds a
 * real edible serving ItemStack through public methods, supplier fields or
 * registry-name conventions, updates the source block, and then lets the
 * detached ItemStack perform its normal Minecraft consumption logic.
 *
 * No Let's Do classes are linked directly here.  That keeps the compatibility
 * optional and also lets the same fallback work for unrelated food mods that
 * follow the same conventions.
 */
public final class GenericPlacedFoodHandler
        implements HandheldFoodBlockHandler {

    private static final String ID =
            "generic_placed_food";

    private static final List<String> PORTION_PROPERTIES =
            List.of(
                    "bites",
                    "servings",
                    "cuts",
                    "stack"
            );

    private static final List<String> SERVING_METHODS =
            List.of(
                    "getPieSliceItem",
                    "getSliceItem",
                    "getServingItem",
                    "getServingStack",
                    "getFoodItem"
            );

    private static final List<String> SERVING_FIELDS =
            List.of(
                    "slice",
                    "Slice",
                    "serving",
                    "servingItem",
                    "foodItem"
            );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean supports(
            BlockState state
    ) {
        if (!WgonConfig.GENERIC_PLACED_FOOD_ENABLED.get()) {
            return false;
        }

        if (isDedicatedCompatibilityNamespace(state)) {
            return false;
        }

        if (isBlacklisted(state)) {
            return false;
        }

        Optional<IntegerProperty> propertyOptional =
                findPortionProperty(state);

        if (propertyOptional.isEmpty()) {
            return false;
        }

        if (getRemainingServings(
                null,
                BlockPos.ZERO,
                state
        ) <= 0) {
            return false;
        }

        /*
         * A matching integer property alone is not enough.  Some decorative
         * stackable blocks also use a property named "stack".  Require a real
         * edible serving ItemStack before WGON claims the block as food.
         */
        return !resolveServingStack(state).isEmpty();
    }

    @Override
    public int getRemainingServings(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        Optional<IntegerProperty> propertyOptional =
                findPortionProperty(state);

        if (propertyOptional.isEmpty()) {
            return -1;
        }

        IntegerProperty property =
                propertyOptional.get();

        int value =
                state.getValue(property);

        String name =
                property.getName();

        if ("bites".equals(name)
                || "cuts".equals(name)) {

            int terminal =
                    resolveIncreasingTerminal(
                            state.getBlock(),
                            property
                    );

            return Math.max(
                    0,
                    terminal - value + 1
            );
        }

        int minimum =
                minimumValue(property);

        if ("servings".equals(name)) {
            return Math.max(
                    0,
                    value - minimum
            );
        }

        if ("stack".equals(name)) {
            return Math.max(
                    0,
                    value - minimum + 1
            );
        }

        return -1;
    }

    @Override
    public ItemStack takeOneServing(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {
        Optional<IntegerProperty> propertyOptional =
                findPortionProperty(state);

        if (propertyOptional.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack serving =
                resolveServingStack(state);

        if (serving.isEmpty()
                || !serving.isEdible()) {
            return ItemStack.EMPTY;
        }

        IntegerProperty property =
                propertyOptional.get();

        int before =
                getRemainingServings(
                        level,
                        pos,
                        state
                );

        if (before <= 0) {
            return ItemStack.EMPTY;
        }

        if (!consumeSourceState(
                level,
                pos,
                state,
                property
        )) {
            return ItemStack.EMPTY;
        }

        ItemStack detached =
                serving.copy();

        detached.setCount(1);
        return detached;
    }

    @Override
    public boolean consumeDetachedServing(
            EntityMaid maid,
            ServerLevel level,
            ItemStack serving
    ) {
        if (serving == null
                || serving.isEmpty()
                || !serving.isEdible()) {
            return false;
        }

        ItemStack one =
                serving.copy();

        one.setCount(1);

        ItemStack remainder =
                one.finishUsingItem(
                        level,
                        maid
                );

        CookeryDrinkAccess.storeOrDrop(
                level,
                maid,
                remainder
        );

        return true;
    }

    private static boolean consumeSourceState(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            IntegerProperty property
    ) {
        int value =
                state.getValue(property);

        String name =
                property.getName();

        if ("bites".equals(name)
                || "cuts".equals(name)) {

            int terminal =
                    resolveIncreasingTerminal(
                            state.getBlock(),
                            property
                    );

            if (value >= terminal) {
                level.destroyBlock(
                        pos,
                        false
                );
            } else {
                level.setBlockAndUpdate(
                        pos,
                        state.setValue(
                                property,
                                value + 1
                        )
                );
            }

            return true;
        }

        int minimum =
                minimumValue(property);

        if ("servings".equals(name)) {
            if (value <= minimum) {
                return false;
            }

            level.setBlockAndUpdate(
                    pos,
                    state.setValue(
                            property,
                            Math.max(
                                    minimum,
                                    value - 1
                            )
                    )
            );

            return true;
        }

        if ("stack".equals(name)) {
            if (value <= minimum) {
                level.destroyBlock(
                        pos,
                        false
                );
            } else {
                level.setBlockAndUpdate(
                        pos,
                        state.setValue(
                                property,
                                value - 1
                        )
                );
            }

            return true;
        }

        return false;
    }

    private static Optional<IntegerProperty> findPortionProperty(
            BlockState state
    ) {
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof IntegerProperty integerProperty)) {
                continue;
            }

            if (PORTION_PROPERTIES.contains(
                    integerProperty.getName()
            )) {
                return Optional.of(integerProperty);
            }
        }

        return Optional.empty();
    }

    private static int resolveIncreasingTerminal(
            Block block,
            IntegerProperty property
    ) {
        int propertyMaximum =
                maximumValue(property);

        List<String> methodNames =
                List.of(
                        "getMaxBites",
                        "getMaxCuts",
                        "getMaxServings",
                        "getMaxCount",
                        "getMaxStack"
                );

        for (String methodName : methodNames) {
            Integer reflected =
                    invokeIntegerMethod(
                            block,
                            methodName
                    );

            if (reflected != null) {
                return Math.min(
                        propertyMaximum,
                        Math.max(
                                minimumValue(property),
                                reflected
                        )
                );
            }
        }

        List<String> fieldNames =
                List.of(
                        "maxBites",
                        "maxCuts",
                        "maxServings",
                        "maxCount",
                        "maxStack"
                );

        for (String fieldName : fieldNames) {
            Integer reflected =
                    readIntegerField(
                            block,
                            fieldName
                    );

            if (reflected != null) {
                return Math.min(
                        propertyMaximum,
                        Math.max(
                                minimumValue(property),
                                reflected
                        )
                );
            }
        }

        return propertyMaximum;
    }

    private static ItemStack resolveServingStack(
            BlockState state
    ) {
        Block block =
                state.getBlock();

        for (String methodName : SERVING_METHODS) {
            ItemStack stack =
                    invokeServingMethod(
                            block,
                            methodName,
                            state
                    );

            if (isUsableServing(stack)) {
                return one(stack);
            }
        }

        for (String fieldName : SERVING_FIELDS) {
            Object value =
                    readField(
                            block,
                            fieldName
                    );

            ItemStack stack =
                    objectToStack(value);

            if (isUsableServing(stack)) {
                return one(stack);
            }
        }

        ItemStack registryGuess =
                findServingByRegistryConvention(
                        block
                );

        if (isUsableServing(registryGuess)) {
            return one(registryGuess);
        }

        Item item =
                block.asItem();

        if (item != Items.AIR) {
            ItemStack stack =
                    new ItemStack(item);

            if (isUsableServing(stack)) {
                return one(stack);
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack invokeServingMethod(
            Block block,
            String methodName,
            BlockState state
    ) {
        Method noArgs =
                findMethod(
                        block.getClass(),
                        methodName
                );

        if (noArgs != null) {
            try {
                Object result =
                        noArgs.invoke(block);

                ItemStack stack =
                        objectToStack(result);

                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        Method stateMethod =
                findMethod(
                        block.getClass(),
                        methodName,
                        BlockState.class
                );

        if (stateMethod != null) {
            try {
                Object result =
                        stateMethod.invoke(
                                block,
                                state
                        );

                return objectToStack(result);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack findServingByRegistryConvention(
            Block block
    ) {
        ResourceLocation blockId =
                ForgeRegistries.BLOCKS.getKey(block);

        if (blockId == null) {
            return ItemStack.EMPTY;
        }

        String namespace =
                blockId.getNamespace();

        String path =
                blockId.getPath();

        List<String> candidates =
                new ArrayList<>();

        if (path.endsWith("_block")
                && path.length() > "_block".length()) {

            String base =
                    path.substring(
                            0,
                            path.length()
                                    - "_block".length()
                    );

            candidates.add(base);
            candidates.add(base + "_slice");
            candidates.add(base + "_salad");
            candidates.add(base + "_serving");
        }

        candidates.add(path + "_slice");
        candidates.add(path + "_salad");
        candidates.add(path + "_serving");
        candidates.add(path + "_portion");
        candidates.add(path + "_piece");

        for (String candidate : candidates) {
            ResourceLocation itemId =
                    new ResourceLocation(
                            namespace,
                            candidate
                    );

            Item item =
                    ForgeRegistries.ITEMS.getValue(
                            itemId
                    );

            if (item == null
                    || item == Items.AIR) {
                continue;
            }

            ItemStack stack =
                    new ItemStack(item);

            if (isUsableServing(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean isUsableServing(
            ItemStack stack
    ) {
        return stack != null
                && !stack.isEmpty()
                && stack.isEdible();
    }

    private static ItemStack one(
            ItemStack stack
    ) {
        ItemStack copy =
                stack.copy();

        copy.setCount(1);
        return copy;
    }

    private static ItemStack objectToStack(
            Object value
    ) {
        if (value == null) {
            return ItemStack.EMPTY;
        }

        if (value instanceof ItemStack stack) {
            return stack.copy();
        }

        if (value instanceof Item item) {
            return new ItemStack(item);
        }

        if (value instanceof ItemLike itemLike) {
            return new ItemStack(itemLike);
        }

        Object supplied =
                unwrapSupplier(value);

        if (supplied != value) {
            return objectToStack(supplied);
        }

        return ItemStack.EMPTY;
    }

    private static Object unwrapSupplier(
            Object value
    ) {
        try {
            if (value instanceof Supplier<?> supplier) {
                return supplier.get();
            }

            Method get =
                    value.getClass()
                            .getMethod("get");

            if (get.getParameterCount() == 0) {
                return get.invoke(value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        return value;
    }

    private static Integer invokeIntegerMethod(
            Object target,
            String methodName
    ) {
        Method method =
                findMethod(
                        target.getClass(),
                        methodName
                );

        if (method == null) {
            return null;
        }

        try {
            Object value =
                    method.invoke(target);

            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        return null;
    }

    private static Integer readIntegerField(
            Object target,
            String fieldName
    ) {
        Object value =
                readField(
                        target,
                        fieldName
                );

        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private static Object readField(
            Object target,
            String fieldName
    ) {
        Class<?> type =
                target.getClass();

        while (type != null
                && type != Object.class) {
            try {
                Field field =
                        type.getDeclaredField(
                                fieldName
                        );

                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }

        return null;
    }

    private static Method findMethod(
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) {
        Class<?> current =
                type;

        while (current != null
                && current != Object.class) {
            try {
                Method method =
                        current.getDeclaredMethod(
                                name,
                                parameterTypes
                        );

                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static int minimumValue(
            IntegerProperty property
    ) {
        Collection<Integer> values =
                property.getPossibleValues();

        int minimum =
                Integer.MAX_VALUE;

        for (Integer value : values) {
            if (value != null) {
                minimum = Math.min(
                        minimum,
                        value
                );
            }
        }

        return minimum == Integer.MAX_VALUE
                ? 0
                : minimum;
    }

    private static int maximumValue(
            IntegerProperty property
    ) {
        Collection<Integer> values =
                property.getPossibleValues();

        int maximum =
                Integer.MIN_VALUE;

        for (Integer value : values) {
            if (value != null) {
                maximum = Math.max(
                        maximum,
                        value
                );
            }
        }

        return maximum == Integer.MIN_VALUE
                ? 0
                : maximum;
    }

    private static boolean isDedicatedCompatibilityNamespace(
            BlockState state
    ) {
        ResourceLocation id =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (id == null) {
            return false;
        }

        return switch (id.getNamespace()) {
            case "minecraft",
                    "kaleidoscope_cookery",
                    "kaleidoscope_tavern",
                    "farmersdelight" -> true;
            default -> false;
        };
    }

    private static boolean isBlacklisted(
            BlockState state
    ) {
        ResourceLocation id =
                ForgeRegistries.BLOCKS.getKey(
                        state.getBlock()
                );

        if (id == null) {
            return false;
        }

        String value =
                id.toString()
                        .toLowerCase(Locale.ROOT);

        for (String blocked :
                WgonConfig.GENERIC_PLACED_FOOD_BLACKLIST.get()) {

            if (blocked != null
                    && value.equals(
                    blocked.toLowerCase(Locale.ROOT)
            )) {
                return true;
            }
        }

        return false;
    }
}

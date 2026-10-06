package com.feastwineallgone.integration.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.PlateBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.StackableFoodBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.init.registry.PlateRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Compatibility bridge for Kaleidoscope Cookery placed platter foods.
 *
 * Cookery has two different placed-food families that look like
 * "platters" to the player:
 *
 * 1. StackableFoodBlock
 *    Stores one protected Supplier<Item> named "item".
 *
 * 2. PlateBlock
 *    Uses PlateRegistry/PlateData and may contain one or more serving
 *    item suppliers for each logical serving.
 *
 * WGON keeps both details in this small integration class so the
 * behaviour layer does not need to know Cookery internals.
 */
public final class CookeryPlatterAccess {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final Field STACKABLE_ITEM_FIELD =
            findStackableItemField();

    private CookeryPlatterAccess() {
    }

    /**
     * Creates one detached serving from a StackableFoodBlock.
     *
     * The world is not modified here.
     */
    public static ItemStack createServing(
            StackableFoodBlock platter
    ) {
        if (platter == null
                || STACKABLE_ITEM_FIELD == null) {

            return ItemStack.EMPTY;
        }

        try {
            Object rawSupplier =
                    STACKABLE_ITEM_FIELD.get(
                            platter
                    );

            if (!(rawSupplier
                    instanceof Supplier<?> supplier)) {

                return ItemStack.EMPTY;
            }

            Object supplied =
                    supplier.get();

            if (!(supplied
                    instanceof Item item)) {

                return ItemStack.EMPTY;
            }

            ItemStack serving =
                    item.getDefaultInstance();

            if (serving.isEmpty()) {
                return ItemStack.EMPTY;
            }

            serving.setCount(
                    1
            );

            return serving;

        } catch (IllegalAccessException
                 | RuntimeException exception) {

            LOGGER.error(
                    "WGON could not read the food item from Kaleidoscope Cookery StackableFoodBlock",
                    exception
            );

            return ItemStack.EMPTY;
        }
    }

    /**
     * Creates the real item bundle represented by one PlateBlock
     * serving.
     *
     * Most plates contain one item per serving (for example the apple
     * platter). Some Cookery plates contain more than one item type;
     * Cookery itself gives all of them when a player takes one serving,
     * so WGON returns the complete bundle here as well.
     *
     * The world is not modified here.
     */
    public static List<ItemStack> createServingBundle(
            PlateBlock plate
    ) {
        if (plate == null) {
            return List.of();
        }

        ResourceLocation blockId =
                ForgeRegistries.BLOCKS
                        .getKey(
                                plate
                        );

        if (blockId == null) {
            return List.of();
        }

        PlateRegistry.PlateData plateData =
                PlateRegistry.PLATE_DATA_MAP
                        .get(
                                blockId
                        );

        if (plateData == null) {
            return List.of();
        }

        List<Supplier<Item>> suppliers =
                plateData.getServingItems();

        if (suppliers == null
                || suppliers.isEmpty()) {

            return List.of();
        }

        List<ItemStack> result =
                new ArrayList<>();

        for (Supplier<Item> supplier : suppliers) {
            if (supplier == null) {
                continue;
            }

            Item item;

            try {
                item = supplier.get();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "WGON could not resolve a Kaleidoscope Cookery PlateBlock serving item for {}",
                        blockId,
                        exception
                );
                continue;
            }

            if (item == null) {
                continue;
            }

            ItemStack stack =
                    item.getDefaultInstance();

            if (stack.isEmpty()) {
                continue;
            }

            stack.setCount(
                    1
            );

            result.add(
                    stack
            );
        }

        return result;
    }

    private static Field findStackableItemField() {
        try {
            Field field =
                    StackableFoodBlock.class
                            .getDeclaredField(
                                    "item"
                            );

            if (!field.trySetAccessible()) {
                field.setAccessible(
                        true
                );
            }

            return field;

        } catch (NoSuchFieldException
                 | SecurityException exception) {

            LOGGER.error(
                    "WGON could not access Kaleidoscope Cookery StackableFoodBlock.item",
                    exception
            );

            return null;
        }
    }
}

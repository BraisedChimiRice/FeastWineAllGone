package com.feastwineallgone.reward;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

/**
 * Fixed reward rules for gifts brought back after a successful night theft.
 *
 * These chances are intentionally not configurable. The only unstable part
 * is DynamicRewardPool's automatic interpretation of third-party item value.
 */
public final class NightGiftManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NightGiftManager() {
    }

    public static List<ItemStack> prepareGifts(
            int brewLevel,
            boolean emptiedBarrel,
            RandomSource random
    ) {
        List<ItemStack> gifts = new ArrayList<>(2);

        ItemStack qualityGift = rollQualityGift(
                brewLevel,
                random
        );

        if (!qualityGift.isEmpty()) {
            gifts.add(qualityGift);
        }

        if (emptiedBarrel) {
            ItemStack grandGift = DynamicRewardPool.randomGift(
                    DynamicRewardPool.Tier.GRAND,
                    random
            );

            if (!grandGift.isEmpty()) {
                gifts.add(grandGift);
            }
        }

        LOGGER.info(
                "[WGON NightGift] Gift roll finished: brewLevel={}, emptiedBarrel={}, gifts={}",
                brewLevel,
                emptiedBarrel,
                describeGifts(gifts)
        );

        return gifts;
    }

    private static String describeGifts(List<ItemStack> gifts) {
        if (gifts.isEmpty()) {
            return "[]";
        }

        List<String> ids = new ArrayList<>(gifts.size());
        for (ItemStack stack : gifts) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ids.add(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())));
        }
        return ids.toString();
    }

    private static ItemStack rollQualityGift(
            int brewLevel,
            RandomSource random
    ) {
        return switch (brewLevel) {
            /*
             * 1 = 难以下咽, 2 = 劣质:
             * 30% poor, otherwise no base gift. NightStealManager currently
             * rejects level 1 before a session starts, but the reward rule is
             * kept complete here for future compatibility.
             */
            case 1, 2 -> random.nextFloat() < 0.30F
                    ? DynamicRewardPool.randomGift(
                    DynamicRewardPool.Tier.POOR,
                    random
            )
                    : ItemStack.EMPTY;

            /*
             * 3 = 普通, 4 = 优质:
             * 75% normal / 25% good.
             */
            case 3, 4 -> DynamicRewardPool.randomGift(
                    random.nextFloat() < 0.25F
                            ? DynamicRewardPool.Tier.GOOD
                            : DynamicRewardPool.Tier.NORMAL,
                    random
            );

            /* 5 = 精酿: 70% good / 30% excellent. */
            case 5 -> DynamicRewardPool.randomGift(
                    random.nextFloat() < 0.30F
                            ? DynamicRewardPool.Tier.EXCELLENT
                            : DynamicRewardPool.Tier.GOOD,
                    random
            );

            /* 6 = 典藏: 50% good / 50% excellent. */
            case 6 -> DynamicRewardPool.randomGift(
                    random.nextBoolean()
                            ? DynamicRewardPool.Tier.EXCELLENT
                            : DynamicRewardPool.Tier.GOOD,
                    random
            );

            default -> ItemStack.EMPTY;
        };
    }
}

package com.feastwineallgone.reward;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

/**
 * Runtime-generated reward pools for the night gift system.
 *
 * There is intentionally no user-facing pool configuration. Minecraft's
 * modding ecosystem has no universal item-value API, so WGON makes a best
 * effort estimate from vanilla/Forge-visible metadata and accepts that some
 * modded items will be wildly misclassified. That uncertainty is part of the
 * feature rather than something hidden behind a giant manual value table.
 */
public final class DynamicRewardPool {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Tier {
        POOR,
        NORMAL,
        GOOD,
        EXCELLENT,
        GRAND
    }

    private static final Set<String> TECHNICAL_ITEM_PATHS = Set.of(
            "air",
            "barrier",
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "command_block_minecart",
            "structure_block",
            "structure_void",
            "jigsaw",
            "debug_stick",
            "light",
            "knowledge_book"
    );

    private static final TagKey<Item> FORGE_NETHERITE_INGOTS = forgeTag("ingots/netherite");
    private static final TagKey<Item> FORGE_DIAMONDS = forgeTag("gems/diamond");
    private static final TagKey<Item> FORGE_EMERALDS = forgeTag("gems/emerald");
    private static final TagKey<Item> FORGE_GOLD_INGOTS = forgeTag("ingots/gold");
    private static final TagKey<Item> FORGE_IRON_INGOTS = forgeTag("ingots/iron");
    private static final TagKey<Item> FORGE_COPPER_INGOTS = forgeTag("ingots/copper");

    private static volatile Snapshot snapshot;

    private DynamicRewardPool() {
    }

    public static ItemStack randomGift(
            Tier tier,
            RandomSource random
    ) {
        Snapshot pools = getSnapshot();
        List<Item> pool = pools.pool(tier);

        if (pool.isEmpty()) {
            pool = pools.grand();
        }

        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item item = pool.get(
                random.nextInt(pool.size())
        );

        ItemStack result = item.getDefaultInstance();
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        /*
         * A gift is always one concrete item, even when the source item is
         * normally stackable. This keeps the event readable and prevents a
         * lucky roll from silently becoming a stack-of-64 economy bomb.
         */
        result.setCount(1);
        return result;
    }

    private static Snapshot getSnapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }

        synchronized (DynamicRewardPool.class) {
            current = snapshot;
            if (current == null) {
                current = buildSnapshot();
                snapshot = current;
            }
        }

        return current;
    }

    private static Snapshot buildSnapshot() {
        List<Item> poor = new ArrayList<>();
        List<Item> normal = new ArrayList<>();
        List<Item> good = new ArrayList<>();
        List<Item> excellent = new ArrayList<>();
        List<Item> grand = new ArrayList<>();

        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (!isEligible(item)) {
                    continue;
                }

                grand.add(item);

                int score = estimateValue(item);
                if (score >= 75) {
                    excellent.add(item);
                } else if (score >= 45) {
                    good.add(item);
                } else if (score >= 20) {
                    normal.add(item);
                } else {
                    poor.add(item);
                }
            } catch (RuntimeException | LinkageError ignored) {
                /*
                 * A third-party item is allowed to have unusual runtime
                 * assumptions. One broken value probe must not disable the
                 * entire gift system, so that item is simply omitted.
                 */
            }
        }

        Snapshot built = new Snapshot(
                immutable(poor),
                immutable(normal),
                immutable(good),
                immutable(excellent),
                immutable(grand)
        );

        LOGGER.info(
                "[WGON NightGift] Dynamic reward pools built: poor={}, normal={}, good={}, excellent={}, grand={}",
                poor.size(),
                normal.size(),
                good.size(),
                excellent.size(),
                grand.size()
        );

        return built;
    }

    private static boolean isEligible(Item item) {
        if (item == null || item == Items.AIR) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || TECHNICAL_ITEM_PATHS.contains(id.getPath())) {
            return false;
        }

        ItemStack stack = item.getDefaultInstance();
        return !stack.isEmpty();
    }

    /**
     * Heuristic only. It is deliberately conservative and intentionally
     * imperfect for third-party content.
     */
    private static int estimateValue(Item item) {
        ItemStack stack = item.getDefaultInstance();
        int score = rarityScore(stack.getRarity());

        int maxStack = stack.getMaxStackSize();
        if (maxStack <= 1) {
            score += 12;
        } else if (maxStack <= 16) {
            score += 6;
        }

        if (stack.isDamageableItem()) {
            int durability = stack.getMaxDamage();
            if (durability >= 2000) {
                score += 12;
            } else if (durability >= 1000) {
                score += 8;
            } else if (durability >= 500) {
                score += 4;
            }
        }

        if (item instanceof TieredItem tiered) {
            score += Math.max(0, tiered.getTier().getLevel()) * 15;
        }

        if (item instanceof SwordItem) {
            score += 8;
        }

        if (item instanceof ArmorItem armor) {
            score += armor.getDefense() * 2;
            score += Math.round(armor.getToughness() * 2.0F);
        }

        if (item instanceof SpawnEggItem) {
            score += 80;
        }

        if (item instanceof EnchantedBookItem) {
            score += 55;
        }

        if (item instanceof PotionItem) {
            score += 25;
        }

        if (item instanceof RecordItem) {
            score += 25;
        }

        /*
         * Conventional Forge material tags provide a little more context for
         * common resources. Unknown mod materials simply fall back to the
         * generic heuristic above.
         */
        if (stack.is(FORGE_NETHERITE_INGOTS)) {
            score += 95;
        } else if (stack.is(FORGE_DIAMONDS)) {
            score += 70;
        } else if (stack.is(FORGE_EMERALDS)) {
            score += 55;
        } else if (stack.is(FORGE_GOLD_INGOTS)) {
            score += 35;
        } else if (stack.is(FORGE_IRON_INGOTS)) {
            score += 20;
        } else if (stack.is(FORGE_COPPER_INGOTS)) {
            score += 10;
        }

        return score;
    }

    private static int rarityScore(Rarity rarity) {
        if (rarity == Rarity.EPIC) {
            return 90;
        }
        if (rarity == Rarity.RARE) {
            return 55;
        }
        if (rarity == Rarity.UNCOMMON) {
            return 25;
        }
        return 0;
    }

    private static TagKey<Item> forgeTag(String path) {
        return TagKey.create(
                Registries.ITEM,
                new ResourceLocation("forge", path)
        );
    }

    private static List<Item> immutable(List<Item> source) {
        return Collections.unmodifiableList(
                new ArrayList<>(source)
        );
    }

    private record Snapshot(
            List<Item> poor,
            List<Item> normal,
            List<Item> good,
            List<Item> excellent,
            List<Item> grand
    ) {
        List<Item> pool(Tier tier) {
            return switch (tier) {
                case POOR -> poor;
                case NORMAL -> normal;
                case GOOD -> good;
                case EXCELLENT -> excellent;
                case GRAND -> grand;
            };
        }
    }
}

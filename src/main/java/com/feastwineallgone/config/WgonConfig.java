package com.feastwineallgone.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * WGON common configuration.
 *
 * Config keys remain English and stable for backwards compatibility, while
 * comments/translation keys are provided in Chinese and English through the
 * normal language files and the built-in config screen.
 */
public final class WgonConfig {

    public static final ForgeConfigSpec SPEC;

    /* Defaults are public so the in-game config screen can restore them. */
    public static final int DEFAULT_STEAL_CHANCE = 25;
    public static final int DEFAULT_DRUNK_SLEEP_CHANCE = 25;
    public static final int DEFAULT_DRUNK_SLEEP_AUTO_WAKE_SECONDS = 120;
    public static final int DEFAULT_IDLE_ACTIVITY_MIN_ATTEMPT_TICKS = 200;
    public static final int DEFAULT_IDLE_ACTIVITY_MAX_ATTEMPT_TICKS = 600;
    public static final int DEFAULT_IDLE_ACTIVITY_MIXED_HANDOFF_TICKS = 10;
    public static final int DEFAULT_INVENTORY_DRINK_MIN_ATTEMPT_TICKS = 1200;
    public static final int DEFAULT_INVENTORY_DRINK_MAX_ATTEMPT_TICKS = 3600;
    public static final int DEFAULT_COUNTER_DRINK_TAKE_TWO_CHANCE = 35;
    public static final int DEFAULT_FRUIT_TASTING_SEARCH_RADIUS = 8;
    public static final int DEFAULT_FRUIT_TASTING_TRIGGER_CHANCE = 25;
    public static final int DEFAULT_FRUIT_TASTING_TWO_TYPE_CHANCE = 35;
    public static final int DEFAULT_FRUIT_TASTING_ALL_TYPE_CHANCE = 25;
    public static final int DEFAULT_GOLDEN_APPLE_STEAL_CHANCE = 25;
    public static final String DEFAULT_COUNTER_DRINK_SURFACE =
            "kaleidoscope_tavern:bar_counter";

    /* =========================================================
     * Night barrel stealing
     * ========================================================= */

    public static final ForgeConfigSpec.IntValue STEAL_CHANCE;
    public static final ForgeConfigSpec.IntValue DRUNK_SLEEP_CHANCE;
    public static final ForgeConfigSpec.IntValue DRUNK_SLEEP_AUTO_WAKE_SECONDS;
    public static final ForgeConfigSpec.IntValue SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_DRINKS;
    public static final ForgeConfigSpec.IntValue SESSION_TIMEOUT_SECONDS;

    /* =========================================================
     * Shared idle activity selection
     * ========================================================= */

    public static final ForgeConfigSpec.IntValue
            IDLE_ACTIVITY_MIN_ATTEMPT_TICKS;

    public static final ForgeConfigSpec.IntValue
            IDLE_ACTIVITY_MAX_ATTEMPT_TICKS;

    public static final ForgeConfigSpec.IntValue
            IDLE_ACTIVITY_MIXED_HANDOFF_TICKS;

    public static final ForgeConfigSpec.IntValue
            IDLE_FOOD_SEARCH_RADIUS;

    public static final ForgeConfigSpec.IntValue
            PLATTER_FINISH_ALL_CHANCE;

    /* =========================================================
     * Generic placed-food compatibility
     * ========================================================= */

    public static final ForgeConfigSpec.BooleanValue
            GENERIC_PLACED_FOOD_ENABLED;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            GENERIC_PLACED_FOOD_BLACKLIST;

    /* =========================================================
     * External soft compatibility
     * ========================================================= */

    public static final ForgeConfigSpec.BooleanValue
            FARMING_TALES_PLACEABLE_COMPAT_ENABLED;

    public static final ForgeConfigSpec.BooleanValue
            BOUNTIFUL_FARES_PASTRY_COMPAT_ENABLED;

    /* =========================================================
     * Favorite placed foods / drinks
     * ========================================================= */

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            FAVORITE_FOOD_WHITELIST;

    /* =========================================================
     * Allowed dining surfaces
     * ========================================================= */

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            DINING_SURFACE_WHITELIST;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            SEATED_DINING_SURFACE_WHITELIST;

    /* =========================================================
     * Idle bar-counter drinking
     * ========================================================= */

    public static final ForgeConfigSpec.BooleanValue
            COUNTER_DRINK_ENABLED;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            COUNTER_DRINK_SURFACE_WHITELIST;

    public static final ForgeConfigSpec.IntValue
            COUNTER_DRINK_SEARCH_RADIUS;

    public static final ForgeConfigSpec.IntValue
            COUNTER_DRINK_TAKE_TWO_CHANCE;

    public static final ForgeConfigSpec.IntValue
            COUNTER_DRINK_APPROACH_TIMEOUT_TICKS;

    /* =========================================================
     * Idle fruit tasting
     * ========================================================= */

    public static final ForgeConfigSpec.IntValue
            FRUIT_TASTING_TRIGGER_CHANCE;

    public static final ForgeConfigSpec.IntValue
            FRUIT_TASTING_SEARCH_RADIUS;

    public static final ForgeConfigSpec.IntValue
            FRUIT_TASTING_TWO_TYPE_CHANCE;

    public static final ForgeConfigSpec.IntValue
            FRUIT_TASTING_ALL_TYPE_CHANCE;

    public static final ForgeConfigSpec.IntValue
            GOLDEN_APPLE_STEAL_CHANCE;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>>
            FAVORITE_FRUIT_WHITELIST;

    /* =========================================================
     * Seated meal feedback / growth
     * ========================================================= */

    public static final ForgeConfigSpec.IntValue MEAL_END_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.IntValue MEAL_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue MEAL_COOLDOWN_TIMER_VISIBLE;
    public static final ForgeConfigSpec.IntValue MEAL_MAX_PER_DAY;
    public static final ForgeConfigSpec.IntValue MEAL_MAX_REWARDED;
    public static final ForgeConfigSpec.DoubleValue NORMAL_MEAL_HEALTH;
    public static final ForgeConfigSpec.DoubleValue NORMAL_MEAL_ARMOR;
    public static final ForgeConfigSpec.DoubleValue FAVORITE_MEAL_HEALTH;
    public static final ForgeConfigSpec.DoubleValue FAVORITE_MEAL_ARMOR;

    /* =========================================================
     * Inventory drinking
     * ========================================================= */

    public static final ForgeConfigSpec.BooleanValue
            INVENTORY_DRINK_ENABLED;

    public static final ForgeConfigSpec.IntValue
            INVENTORY_DRINK_MIN_ATTEMPT_TICKS;

    public static final ForgeConfigSpec.IntValue
            INVENTORY_DRINK_MAX_ATTEMPT_TICKS;

    public static final ForgeConfigSpec.IntValue
            INVENTORY_DRINK_CRAVING_CHANCE;

    public static final ForgeConfigSpec.IntValue
            INVENTORY_DRINK_INJURED_HEALTH_PERCENT;

    static {
        ForgeConfigSpec.Builder builder =
                new ForgeConfigSpec.Builder();

        /* =====================================================
         * Night stealing
         * ===================================================== */

        builder.push(
                "night_stealing"
        );

        STEAL_CHANCE =
                builder
                        .comment(
                                "夜间偷酒触发概率（1-100）。玩家入睡时检查一次。",
                                "Night barrel-stealing chance checked when the player goes to sleep."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.steal_chance"
                        )
                        .defineInRange(
                                "stealChance",
                                DEFAULT_STEAL_CHANCE,
                                1,
                                100
                        );

        DRUNK_SLEEP_CHANCE =
                builder
                        .comment(
                                "成功完成夜间偷酒后，女仆不回床而在酒桶旁/记录地点醉倒的概率（0-100）。",
                                "Chance that a maid sleeps at a drunk-sleep location instead of returning to bed after a successful night theft."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.drunk_sleep_chance"
                        )
                        .defineInRange(
                                "drunkSleepChance",
                                DEFAULT_DRUNK_SLEEP_CHANCE,
                                0,
                                100
                        );

        DRUNK_SLEEP_AUTO_WAKE_SECONDS =
                builder
                        .comment(
                                "醉眠后若主人一直没有唤醒女仆，多少秒后女仆会自行醒来并回到主人身边。0 = 关闭自动醒来。",
                                "Seconds before a drunk-sleeping maid wakes herself and returns to her owner. 0 disables automatic wake-up."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.drunk_sleep_auto_wake"
                        )
                        .defineInRange(
                                "drunkSleepAutoWakeSeconds",
                                DEFAULT_DRUNK_SLEEP_AUTO_WAKE_SECONDS,
                                0,
                                3600
                        );

        SEARCH_RADIUS =
                builder
                        .comment(
                                "夜间偷酒时搜索森罗酒馆酒桶的半径。",
                                "Search radius used to find Kaleidoscope Tavern barrels."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.search_radius"
                        )
                        .defineInRange(
                                "searchRadius",
                                10,
                                1,
                                32
                        );

        MAX_DRINKS =
                builder
                        .comment(
                                "一次夜间偷酒事件最多喝掉多少份。",
                                "Maximum servings stolen during one nighttime barrel event."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.max_drinks"
                        )
                        .defineInRange(
                                "maxDrinks",
                                5,
                                1,
                                5
                        );

        SESSION_TIMEOUT_SECONDS =
                builder
                        .comment(
                                "夜间偷酒卡死保护时间（秒）。超过后强制结束本次行为。",
                                "Safety watchdog for a stuck nighttime event."
                        )
                        .translation(
                                "config.feastwineallgone.night_stealing.timeout"
                        )
                        .defineInRange(
                                "sessionTimeoutSeconds",
                                60,
                                10,
                                180
                        );

        builder.pop();

        /* =====================================================
         * Shared idle activity selection
         * ===================================================== */

        builder.push(
                "idle_activity"
        );

        IDLE_ACTIVITY_MIN_ATTEMPT_TICKS =
                builder
                        .comment(
                                "酒狐空闲时再次考虑‘吃饭/拿吧台酒’的最短随机间隔。20 tick = 1 秒。",
                                "Minimum random wait between ordinary idle FOOD/DRINK decisions."
                        )
                        .translation(
                                "config.feastwineallgone.idle_activity.min_interval"
                        )
                        .defineInRange(
                                "minAttemptTicks",
                                DEFAULT_IDLE_ACTIVITY_MIN_ATTEMPT_TICKS,
                                20,
                                72000
                        );

        IDLE_ACTIVITY_MAX_ATTEMPT_TICKS =
                builder
                        .comment(
                                "酒狐空闲时再次考虑‘吃饭/拿吧台酒’的最长随机间隔。20 tick = 1 秒。",
                                "Maximum random wait between ordinary idle FOOD/DRINK decisions."
                        )
                        .translation(
                                "config.feastwineallgone.idle_activity.max_interval"
                        )
                        .defineInRange(
                                "maxAttemptTicks",
                                DEFAULT_IDLE_ACTIVITY_MAX_ATTEMPT_TICKS,
                                20,
                                72000
                        );

        IDLE_ACTIVITY_MIXED_HANDOFF_TICKS =
                builder
                        .comment(
                                "附近同时存在食物和吧台酒时，完成一个行为后再次选择下一行为的间隔。",
                                "默认 10 tick = 0.5 秒。调高可避免酒狐在自助餐桌前连续狂吃狂喝。",
                                "Delay before the next FOOD/DRINK choice when both activity types are available."
                        )
                        .translation(
                                "config.feastwineallgone.idle_activity.mixed_handoff"
                        )
                        .defineInRange(
                                "mixedHandoffTicks",
                                DEFAULT_IDLE_ACTIVITY_MIXED_HANDOFF_TICKS,
                                1,
                                72000
                        );

        IDLE_FOOD_SEARCH_RADIUS =
                builder
                        .comment(
                                "空闲酒狐搜索可食用方块的半径。",
                                "Radius around an idle maid used to search for supported placed food."
                        )
                        .translation(
                                "config.feastwineallgone.idle_activity.food_search_radius"
                        )
                        .defineInRange(
                                "foodSearchRadius",
                                8,
                                1,
                                32
                        );

        PLATTER_FINISH_ALL_CHANCE =
                builder
                        .comment(
                                "酒狐已经吃掉森罗厨房拼盘前两份后，继续把剩余部分吃光的概率（0-100）。",
                                "Chance to finish the rest of a Kaleidoscope Cookery platter after the first two servings."
                        )
                        .translation(
                                "config.feastwineallgone.idle_activity.platter_finish_chance"
                        )
                        .defineInRange(
                                "platterFinishAllChance",
                                25,
                                0,
                                100
                        );

        builder.pop();

        /* =====================================================
         * Generic placed-food compatibility
         * ===================================================== */

        builder.push(
                "generic_placed_food"
        );

        GENERIC_PLACED_FOOD_ENABLED =
                builder
                        .comment(
                                "启用未知模组方块食物的保守通用兼容。",
                                "亿宴酊蒸会识别 bites、servings、cuts、stack 等常见份数属性，",
                                "但只有在能找到真实可食用份额 ItemStack 时才会接管。"
                        )
                        .translation(
                                "config.feastwineallgone.generic.enabled"
                        )
                        .define(
                                "enabled",
                                true
                        );

        GENERIC_PLACED_FOOD_BLACKLIST =
                builder
                        .comment(
                                "禁止通用食物兼容触碰的方块注册名列表。",
                                "如果某个非食物方块碰巧也使用 bites/servings/cuts/stack，可在此加入黑名单。"
                        )
                        .translation(
                                "config.feastwineallgone.generic.blacklist"
                        )
                        .defineList(
                                "blacklist",
                                List.of(),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        builder.pop();

        /* =====================================================
         * External soft compatibility
         * ===================================================== */

        builder.push(
                "external_compatibility"
        );

        FARMING_TALES_PLACEABLE_COMPAT_ENABLED =
                builder
                        .comment(
                                "兼容农场物语整合包的 KubeJS 可放置食物/饮料标签系统。",
                                "未安装对应内容时不会产生任何影响，也不会把它变成强制前置。"
                        )
                        .translation(
                                "config.feastwineallgone.compat.farmingtales"
                        )
                        .define(
                                "farmingTalesPlaceableConsumables",
                                true
                        );

        BOUNTIFUL_FARES_PASTRY_COMPAT_ENABLED =
                builder
                        .comment(
                                "兼容丰饶食记（Bountiful Fares）的派、挞和同类 bites 方块食物。",
                                "该兼容通过注册名/类层级字符串检测，不要求丰饶食记作为强制前置。"
                        )
                        .translation(
                                "config.feastwineallgone.compat.bountiful_fares"
                        )
                        .define(
                                "bountifulFaresPastries",
                                true
                        );

        builder.pop();

        /* =====================================================
         * Favorite placed foods / drinks
         * ===================================================== */

        builder.push(
                "favorite_foods"
        );

        FAVORITE_FOOD_WHITELIST =
                builder
                        .comment(
                                "酒狐最爱食物/饮料白名单（注册名）。",
                                "可以填写方块 ID，也可以填写物品 ID。最爱目标会优先于普通随机吃饭/拿酒。",
                                "默认：minecraft:cake"
                        )
                        .translation(
                                "config.feastwineallgone.favorite.whitelist"
                        )
                        .defineList(
                                "whitelist",
                                List.of(
                                        "minecraft:cake"
                                ),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        builder.pop();

        /* =====================================================
         * Allowed dining surfaces
         * ===================================================== */

        builder.push(
                "dining_surfaces"
        );

        DINING_SURFACE_WHITELIST =
                builder
                        .comment(
                                "额外允许空闲酒狐在其上方吃放置食物的方块注册名。",
                                "森罗厨房餐桌始终允许，无需重复填写。",
                                "Additional block IDs that may act as ordinary idle dining surfaces.",
                                "Kaleidoscope Cookery tables are always allowed."
                        )
                        .translation(
                                "config.feastwineallgone.dining_surfaces.whitelist"
                        )
                        .defineList(
                                "whitelist",
                                List.of(),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        SEATED_DINING_SURFACE_WHITELIST =
                builder
                        .comment(
                                "额外允许坐着吃饭时作为桌面的方块注册名。",
                                "普通用餐桌面白名单中的方块也会自动允许坐着吃，无需重复填写。",
                                "Additional block IDs that may act as seated-dining surfaces.",
                                "Ordinary dining-surface whitelist entries are inherited automatically."
                        )
                        .translation(
                                "config.feastwineallgone.dining_surfaces.seated_whitelist"
                        )
                        .defineList(
                                "seatedWhitelist",
                                List.of(),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        builder.pop();

        /* =====================================================
         * Bar-counter drinking
         * ===================================================== */

        builder.push(
                "counter_drinking"
        );

        COUNTER_DRINK_ENABLED =
                builder
                        .comment(
                                "允许空闲酒狐拿走直接摆在森罗酒馆吧台上的酒。"
                        )
                        .translation(
                                "config.feastwineallgone.counter.enabled"
                        )
                        .define(
                                "enabled",
                                true
                        );

        COUNTER_DRINK_SURFACE_WHITELIST =
                builder
                        .comment(
                                "空闲酒狐允许从哪些方块上方拿取森罗酒馆饮料。",
                                "默认仅包含森罗酒馆吧台；默认项本身也可以删除。",
                                "可填写任意已注册方块 ID。列表为空时，空闲状态不会从任何台面拿酒。",
                                "Allowed support-block IDs for idle counter drinking.",
                                "The Kaleidoscope Tavern bar counter is only a removable default entry, not hard-coded behavior."
                        )
                        .translation(
                                "config.feastwineallgone.counter.surface_whitelist"
                        )
                        .defineList(
                                "surfaceWhitelist",
                                List.of(DEFAULT_COUNTER_DRINK_SURFACE),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        COUNTER_DRINK_SEARCH_RADIUS =
                builder
                        .comment(
                                "空闲酒狐搜索吧台酒的半径。"
                        )
                        .translation(
                                "config.feastwineallgone.counter.search_radius"
                        )
                        .defineInRange(
                                "searchRadius",
                                8,
                                1,
                                32
                        );

        COUNTER_DRINK_TAKE_TWO_CHANCE =
                builder
                        .comment(
                                "酒狐喝第一瓶后，再顺手拿走第二瓶塞进背包的概率（0-100）。"
                        )
                        .translation(
                                "config.feastwineallgone.counter.take_two_chance"
                        )
                        .defineInRange(
                                "takeTwoChance",
                                DEFAULT_COUNTER_DRINK_TAKE_TWO_CHANCE,
                                0,
                                100
                        );

        COUNTER_DRINK_APPROACH_TIMEOUT_TICKS =
                builder
                        .comment(
                                "酒狐尝试走到吧台酒前的最大时间。20 tick = 1 秒。"
                        )
                        .translation(
                                "config.feastwineallgone.counter.approach_timeout"
                        )
                        .defineInRange(
                                "approachTimeoutTicks",
                                200,
                                20,
                                1200
                        );

        builder.pop();

        /* =====================================================
         * Idle fruit tasting
         * ===================================================== */

        builder.push(
                "fruit_tasting"
        );

        FRUIT_TASTING_TRIGGER_CHANCE =
                builder
                        .comment(
                                "酒狐处于普通空闲决策时，触发一次水果品尝循环的概率（百分比）。",
                                "默认 25%。未触发时水果不会作为一个强制活动类型吸引酒狐。",
                                "Chance (percent) for an ordinary idle decision to start one fruit-tasting cycle."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.trigger_chance"
                        )
                        .defineInRange(
                                "triggerChance",
                                DEFAULT_FRUIT_TASTING_TRIGGER_CHANCE,
                                0,
                                100
                        );

        FRUIT_TASTING_SEARCH_RADIUS =
                builder
                        .comment(
                                "水果品尝循环开始时搜索成熟水果的水平半径。",
                                "循环按水果种类而不是方块数量规划，每种水果最多品尝一次。",
                                "Horizontal search radius used when a fruit-tasting cycle starts."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.search_radius"
                        )
                        .defineInRange(
                                "searchRadius",
                                DEFAULT_FRUIT_TASTING_SEARCH_RADIUS,
                                1,
                                32
                        );

        FRUIT_TASTING_TWO_TYPE_CHANCE =
                builder
                        .comment(
                                "一次水果品尝循环恰好计划品尝两种不同水果的概率（百分比）。默认 35%。",
                                "若附近只有一种水果，则只会品尝一种。",
                                "Chance (percent) for a tasting cycle to sample exactly two distinct fruit types."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.two_type_chance"
                        )
                        .defineInRange(
                                "twoTypeChance",
                                DEFAULT_FRUIT_TASTING_TWO_TYPE_CHANCE,
                                0,
                                100
                        );

        FRUIT_TASTING_ALL_TYPE_CHANCE =
                builder
                        .comment(
                                "一次水果品尝循环进入“不封顶”模式的概率（百分比）。默认 25%。",
                                "触发后会按种类各品尝一次循环开始时发现的所有成熟水果；种了多少种就最多尝多少次。",
                                "Chance (percent) for an uncapped cycle that samples every distinct fruit type discovered at cycle start."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.all_type_chance"
                        )
                        .defineInRange(
                                "allTypeChance",
                                DEFAULT_FRUIT_TASTING_ALL_TYPE_CHANCE,
                                0,
                                100
                        );

        GOLDEN_APPLE_STEAL_CHANCE =
                builder
                        .comment(
                                "空闲品尝水果时，酒狐决定偷吃丰饶食记成熟金苹果的概率（百分比）。",
                                "0 = 永不偷吃，100 = 发现成熟金苹果时总会将其视为可品尝目标。",
                                "Chance (percent) for idle fruit tasting to consider a ripe Bountiful Fares golden apple as a sneaky snack."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.golden_apple_steal_chance"
                        )
                        .defineInRange(
                                "goldenAppleStealChance",
                                DEFAULT_GOLDEN_APPLE_STEAL_CHANCE,
                                0,
                                100
                        );

        FAVORITE_FRUIT_WHITELIST =
                builder
                        .comment(
                                "酒狐优先品尝的水果物品注册名。默认留空，不预设任何喜欢水果。",
                                "玩家仍可自行加入物品 ID；优先规则只使用一次，吃过喜欢水果后下一次强制回到全部候选随机选择。",
                                "Favorite fruit item IDs. Empty by default; no fruit receives built-in priority.",
                                "Players may add item IDs manually. Favorite priority remains one-shot; the next tasting returns to the full random pool."
                        )
                        .translation(
                                "config.feastwineallgone.fruit_tasting.favorite_fruits"
                        )
                        .defineList(
                                "preferredFruits",
                                List.of(),
                                entry -> entry instanceof String value
                                        && ResourceLocation.tryParse(value) != null
                        );

        builder.pop();

        /* =====================================================
         * Inventory drinking
         * ===================================================== */

        /* =====================================================
         * Seated meal feedback / growth
         * ===================================================== */

        builder.push("meal_feedback");

        MEAL_END_TIMEOUT_SECONDS = builder
                .comment("最后一次成功吃喝后多久结算本顿正餐（秒）。")
                .translation("config.feastwineallgone.meal.timeout")
                .defineInRange("mealEndTimeoutSeconds", 60, 5, 600);

        MEAL_COOLDOWN_MINUTES = builder
                .comment("合格正餐后的进食冷却（现实分钟）。水果品尝与饮酒不受此冷却影响。")
                .translation("config.feastwineallgone.meal.cooldown")
                .defineInRange("mealCooldownMinutes", 5, 0, 120);

        MEAL_COOLDOWN_TIMER_VISIBLE = builder
                .comment("是否在女仆头顶显示距离再次饥饿的倒计时。")
                .translation("config.feastwineallgone.meal.show_cooldown_timer")
                .define("showCooldownTimer", true);

        MEAL_MAX_PER_DAY = builder
                .comment("每个 Minecraft 日最多登记多少顿正式正餐。达到上限后仍可进食，但不再登记正餐。")
                .translation("config.feastwineallgone.meal.max_per_day")
                .defineInRange("maxMealsPerDay", 3, 0, 64);

        MEAL_MAX_REWARDED = builder
                .comment("一只女仆一生最多获得多少次正餐成长。设为 0 = 保留正反馈但关闭属性/好感成长。",
                         "Increasing this may create an extremely powerful maid. This is intentional.")
                .translation("config.feastwineallgone.meal.max_rewarded")
                .defineInRange("maxRewardedMeals", 10, 0, Integer.MAX_VALUE);

        NORMAL_MEAL_HEALTH = builder
                .comment("丰盛正餐每次增加的最大生命。")
                .translation("config.feastwineallgone.meal.normal_health")
                .defineInRange("normalMealHealth", 6.0D, 0.0D, 1000000.0D);
        NORMAL_MEAL_ARMOR = builder
                .comment("丰盛正餐每次增加的护甲。")
                .translation("config.feastwineallgone.meal.normal_armor")
                .defineInRange("normalMealArmor", 5.0D, 0.0D, 1000000.0D);
        FAVORITE_MEAL_HEALTH = builder
                .comment("喜爱正餐每次增加的最大生命。")
                .translation("config.feastwineallgone.meal.favorite_health")
                .defineInRange("favoriteMealHealth", 10.0D, 0.0D, 1000000.0D);
        FAVORITE_MEAL_ARMOR = builder
                .comment("喜爱正餐每次增加的护甲。")
                .translation("config.feastwineallgone.meal.favorite_armor")
                .defineInRange("favoriteMealArmor", 10.0D, 0.0D, 1000000.0D);

        builder.pop();

        builder.push(
                "inventory_drinking"
        );

        INVENTORY_DRINK_ENABLED =
                builder
                        .comment(
                                "允许空闲酒狐饮用自己背包中的森罗酒馆酒水。"
                        )
                        .translation(
                                "config.feastwineallgone.inventory.enabled"
                        )
                        .define(
                                "enabled",
                                true
                        );

        INVENTORY_DRINK_MIN_ATTEMPT_TICKS =
                builder
                        .comment(
                                "酒狐考虑从背包喝酒的最短随机间隔。20 tick = 1 秒。"
                        )
                        .translation(
                                "config.feastwineallgone.inventory.min_interval"
                        )
                        .defineInRange(
                                "minAttemptTicks",
                                DEFAULT_INVENTORY_DRINK_MIN_ATTEMPT_TICKS,
                                20,
                                72000
                        );

        INVENTORY_DRINK_MAX_ATTEMPT_TICKS =
                builder
                        .comment(
                                "酒狐考虑从背包喝酒的最长随机间隔。20 tick = 1 秒。"
                        )
                        .translation(
                                "config.feastwineallgone.inventory.max_interval"
                        )
                        .defineInRange(
                                "maxAttemptTicks",
                                DEFAULT_INVENTORY_DRINK_MAX_ATTEMPT_TICKS,
                                20,
                                72000
                        );

        INVENTORY_DRINK_CRAVING_CHANCE =
                builder
                        .comment(
                                "健康状态下，酒狐仅仅因为嘴馋而从背包喝酒的概率（0-100）。"
                        )
                        .translation(
                                "config.feastwineallgone.inventory.craving_chance"
                        )
                        .defineInRange(
                                "cravingChance",
                                20,
                                0,
                                100
                        );

        INVENTORY_DRINK_INJURED_HEALTH_PERCENT =
                builder
                        .comment(
                                "生命值低于该百分比时，酒狐跳过普通嘴馋概率并尝试喝背包中的酒。",
                                "酒本身是否治疗仍由森罗酒馆的酒水效果决定。"
                        )
                        .translation(
                                "config.feastwineallgone.inventory.injured_health_percent"
                        )
                        .defineInRange(
                                "injuredHealthPercent",
                                70,
                                1,
                                100
                        );

        builder.pop();

        SPEC =
                builder.build();
    }

    private WgonConfig() {
    }
}

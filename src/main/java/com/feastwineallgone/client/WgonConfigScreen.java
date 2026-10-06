package com.feastwineallgone.client;

import com.feastwineallgone.config.WgonConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Built-in translated WGON config screen.
 *
 * The old screen only exposed seven frequently-used values while the common
 * TOML already contained many more settings. This version is paged so every
 * scalar WGON option plus all three registry-ID lists can be edited in game.
 */
public final class WgonConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_SIZE = 20;
    private static final int VALUE_WIDTH = 84;
    private static final int PAGE_COUNT = 10;

    private final Screen parent;
    private final int page;

    private EditBox diningSurfaceList;
    private EditBox favoriteFoodList;
    private EditBox genericBlacklist;
    private EditBox counterSurfaceList;
    private EditBox favoriteFruitList;

    public WgonConfigScreen(
            Screen parent
    ) {
        this(parent, 0);
    }

    private WgonConfigScreen(
            Screen parent,
            int page
    ) {
        super(Component.translatable(
                "config.feastwineallgone.screen.title"
        ));

        this.parent = parent;
        this.page = Mth.clamp(page, 0, PAGE_COUNT - 1);
    }

    @Override
    protected void init() {
        int firstRowY = 56;

        switch (page) {
            case 0 -> initIdlePage(firstRowY);
            case 1 -> initCounterInventoryPage(firstRowY);
            case 2 -> initNightPage(firstRowY);
            case 3 -> initCompatibilityPage(firstRowY);
            case 4 -> initListsPage(firstRowY);
            case 5 -> initCounterSurfacePage(firstRowY);
            case 6 -> initFruitTastingPage(firstRowY);
            case 7 -> initMealRulesPage(firstRowY);
            case 8 -> initMealLimitsPage(firstRowY);
            case 9 -> initMealGrowthPage(firstRowY);
            default -> {
            }
        }

        int bottomY = this.height - 28;

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("<"),
                                button -> openPage(
                                        (page - 1 + PAGE_COUNT)
                                                % PAGE_COUNT
                                )
                        )
                        .bounds(
                                this.width / 2 - 154,
                                bottomY,
                                32,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "config.feastwineallgone.screen.defaults"
                                ),
                                button -> {
                                    resetDefaults();
                                    openPage(page);
                                }
                        )
                        .bounds(
                                this.width / 2 - 116,
                                bottomY,
                                104,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "gui.done"
                                ),
                                button -> saveAndReturn()
                        )
                        .bounds(
                                this.width / 2 - 6,
                                bottomY,
                                104,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal(">"),
                                button -> openPage(
                                        (page + 1)
                                                % PAGE_COUNT
                                )
                        )
                        .bounds(
                                this.width / 2 + 104,
                                bottomY,
                                32,
                                20
                        )
                        .build()
        );
    }

    private void initIdlePage(
            int y
    ) {
        addIntRow(
                y,
                WgonConfig.IDLE_ACTIVITY_MIN_ATTEMPT_TICKS,
                20,
                20,
                72000,
                true,
                false
        );

        addIntRow(
                y + ROW_HEIGHT,
                WgonConfig.IDLE_ACTIVITY_MAX_ATTEMPT_TICKS,
                20,
                20,
                72000,
                false,
                true
        );

        addIntRow(
                y + ROW_HEIGHT * 2,
                WgonConfig.IDLE_ACTIVITY_MIXED_HANDOFF_TICKS,
                10,
                1,
                72000,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 3,
                WgonConfig.IDLE_FOOD_SEARCH_RADIUS,
                1,
                1,
                32,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 4,
                WgonConfig.PLATTER_FINISH_ALL_CHANCE,
                5,
                0,
                100,
                false,
                false
        );
    }

    private void initCounterInventoryPage(
            int y
    ) {
        addBooleanRow(
                y,
                WgonConfig.COUNTER_DRINK_ENABLED
        );

        addIntRow(
                y + ROW_HEIGHT,
                WgonConfig.COUNTER_DRINK_SEARCH_RADIUS,
                1,
                1,
                32,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 2,
                WgonConfig.COUNTER_DRINK_TAKE_TWO_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 3,
                WgonConfig.COUNTER_DRINK_APPROACH_TIMEOUT_TICKS,
                20,
                20,
                1200,
                false,
                false
        );

        addBooleanRow(
                y + ROW_HEIGHT * 4,
                WgonConfig.INVENTORY_DRINK_ENABLED
        );

        addIntRow(
                y + ROW_HEIGHT * 5,
                WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS,
                100,
                20,
                72000,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 6,
                WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS,
                100,
                20,
                72000,
                false,
                false
        );
    }

    private void initNightPage(
            int y
    ) {
        addIntRow(
                y,
                WgonConfig.INVENTORY_DRINK_CRAVING_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT,
                WgonConfig.INVENTORY_DRINK_INJURED_HEALTH_PERCENT,
                5,
                1,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 2,
                WgonConfig.STEAL_CHANCE,
                5,
                1,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 3,
                WgonConfig.DRUNK_SLEEP_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 4,
                WgonConfig.DRUNK_SLEEP_AUTO_WAKE_SECONDS,
                10,
                0,
                3600,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 5,
                WgonConfig.SEARCH_RADIUS,
                1,
                1,
                32,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 6,
                WgonConfig.MAX_DRINKS,
                1,
                1,
                5,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 7,
                WgonConfig.SESSION_TIMEOUT_SECONDS,
                5,
                10,
                180,
                false,
                false
        );
    }

    private void initCompatibilityPage(
            int y
    ) {
        addBooleanRow(
                y,
                WgonConfig.GENERIC_PLACED_FOOD_ENABLED
        );

        addBooleanRow(
                y + ROW_HEIGHT,
                WgonConfig.FARMING_TALES_PLACEABLE_COMPAT_ENABLED
        );

        addBooleanRow(
                y + ROW_HEIGHT * 2,
                WgonConfig.BOUNTIFUL_FARES_PASTRY_COMPAT_ENABLED
        );
    }

    private void initListsPage(
            int y
    ) {
        diningSurfaceList =
                addListField(
                        y + 14,
                        WgonConfig.DINING_SURFACE_WHITELIST.get()
                );

        favoriteFoodList =
                addListField(
                        y + 58,
                        WgonConfig.FAVORITE_FOOD_WHITELIST.get()
                );

        genericBlacklist =
                addListField(
                        y + 102,
                        WgonConfig.GENERIC_PLACED_FOOD_BLACKLIST.get()
                );
    }

    private void initCounterSurfacePage(
            int y
    ) {
        counterSurfaceList =
                addListField(
                        y + 14,
                        WgonConfig.COUNTER_DRINK_SURFACE_WHITELIST.get()
                );
    }

    private void initFruitTastingPage(
            int y
    ) {
        addIntRow(
                y,
                WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT,
                WgonConfig.FRUIT_TASTING_SEARCH_RADIUS,
                1,
                1,
                32,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 2,
                WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 3,
                WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE,
                5,
                0,
                100,
                false,
                false
        );

        addIntRow(
                y + ROW_HEIGHT * 4,
                WgonConfig.GOLDEN_APPLE_STEAL_CHANCE,
                1,
                0,
                100,
                false,
                false
        );

        favoriteFruitList =
                addListField(
                        y + ROW_HEIGHT * 5 + 10,
                        WgonConfig.FAVORITE_FRUIT_WHITELIST.get()
                );
    }

    private void initMealRulesPage(
            int y
    ) {
        addIntRow(y, WgonConfig.MEAL_END_TIMEOUT_SECONDS, 5, 5, 600, false, false);
    }

    private void initMealLimitsPage(
            int y
    ) {
        addIntRow(y, WgonConfig.MEAL_COOLDOWN_MINUTES, 1, 0, 120, false, false);
        addIntRow(y + ROW_HEIGHT, WgonConfig.MEAL_MAX_PER_DAY, 1, 0, 64, false, false);
        addIntRow(y + ROW_HEIGHT * 2, WgonConfig.MEAL_MAX_REWARDED, 1, 0, Integer.MAX_VALUE, false, false);
        addBooleanRow(y + ROW_HEIGHT * 3, WgonConfig.MEAL_COOLDOWN_TIMER_VISIBLE);
    }

    private void initMealGrowthPage(
            int y
    ) {
        addDoubleRow(y, WgonConfig.NORMAL_MEAL_HEALTH, 1.0D, 0.0D, 1000000.0D);
        addDoubleRow(y + ROW_HEIGHT, WgonConfig.NORMAL_MEAL_ARMOR, 1.0D, 0.0D, 1000000.0D);
        addDoubleRow(y + ROW_HEIGHT * 2, WgonConfig.FAVORITE_MEAL_HEALTH, 1.0D, 0.0D, 1000000.0D);
        addDoubleRow(y + ROW_HEIGHT * 3, WgonConfig.FAVORITE_MEAL_ARMOR, 1.0D, 0.0D, 1000000.0D);
    }

    private EditBox addListField(
            int y,
            List<? extends String> values
    ) {
        EditBox box =
                new EditBox(
                        this.font,
                        this.width / 2 - 150,
                        y,
                        300,
                        20,
                        Component.empty()
                );

        box.setMaxLength(4096);
        box.setValue(
                String.join(
                        ", ",
                        values
                )
        );

        this.addRenderableWidget(box);
        return box;
    }

    private void addIntRow(
            int y,
            ForgeConfigSpec.IntValue value,
            int step,
            int min,
            int max,
            boolean idleMinimum,
            boolean idleMaximum
    ) {
        int valueCenter =
                this.width / 2 + 92;

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("−"),
                                button -> {
                                    adjustInt(
                                            value,
                                            -step,
                                            min,
                                            max,
                                            idleMinimum,
                                            idleMaximum
                                    );
                                }
                        )
                        .bounds(
                                valueCenter - VALUE_WIDTH / 2 - BUTTON_SIZE - 4,
                                y,
                                BUTTON_SIZE,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("+"),
                                button -> {
                                    adjustInt(
                                            value,
                                            step,
                                            min,
                                            max,
                                            idleMinimum,
                                            idleMaximum
                                    );
                                }
                        )
                        .bounds(
                                valueCenter + VALUE_WIDTH / 2 + 4,
                                y,
                                BUTTON_SIZE,
                                20
                        )
                        .build()
        );
    }

    private void addDoubleRow(
            int y,
            ForgeConfigSpec.DoubleValue value,
            double step,
            double min,
            double max
    ) {
        int valueCenter = this.width / 2 + 92;

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("−"),
                                button -> adjustDouble(value, -step, min, max)
                        )
                        .bounds(
                                valueCenter - VALUE_WIDTH / 2 - BUTTON_SIZE - 4,
                                y,
                                BUTTON_SIZE,
                                20
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.literal("+"),
                                button -> adjustDouble(value, step, min, max)
                        )
                        .bounds(
                                valueCenter + VALUE_WIDTH / 2 + 4,
                                y,
                                BUTTON_SIZE,
                                20
                        )
                        .build()
        );
    }

    private void addBooleanRow(
            int y,
            ForgeConfigSpec.BooleanValue value
    ) {
        Button[] holder =
                new Button[1];

        holder[0] =
                Button.builder(
                                booleanText(value.get()),
                                button -> {
                                    value.set(!value.get());
                                    button.setMessage(
                                            booleanText(value.get())
                                    );
                                }
                        )
                        .bounds(
                                this.width / 2 + 48,
                                y,
                                88,
                                20
                        )
                        .build();

        this.addRenderableWidget(holder[0]);
    }

    private static Component booleanText(
            boolean value
    ) {
        return Component.translatable(
                value
                        ? "options.on"
                        : "options.off"
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                16,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        pageTitleKey()
                ),
                this.width / 2,
                34,
                0xE0E0E0
        );

        graphics.drawCenteredString(
                this.font,
                Component.literal(
                        (page + 1)
                                + " / "
                                + PAGE_COUNT
                ),
                this.width / 2,
                48,
                0x909090
        );

        int y = 56;

        switch (page) {
            case 0 -> {
                drawIntRow(graphics, y,
                        "config.feastwineallgone.idle_activity.min_interval",
                        WgonConfig.IDLE_ACTIVITY_MIN_ATTEMPT_TICKS.get(),
                        ValueKind.TICKS);
                drawIntRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.idle_activity.max_interval",
                        WgonConfig.IDLE_ACTIVITY_MAX_ATTEMPT_TICKS.get(),
                        ValueKind.TICKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.idle_activity.mixed_handoff",
                        WgonConfig.IDLE_ACTIVITY_MIXED_HANDOFF_TICKS.get(),
                        ValueKind.TICKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.idle_activity.food_search_radius",
                        WgonConfig.IDLE_FOOD_SEARCH_RADIUS.get(),
                        ValueKind.BLOCKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 4,
                        "config.feastwineallgone.idle_activity.platter_finish_chance",
                        WgonConfig.PLATTER_FINISH_ALL_CHANCE.get(),
                        ValueKind.PERCENT);
            }

            case 1 -> {
                drawBooleanLabel(graphics, y,
                        "config.feastwineallgone.counter.enabled");
                drawIntRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.counter.search_radius",
                        WgonConfig.COUNTER_DRINK_SEARCH_RADIUS.get(),
                        ValueKind.BLOCKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.counter.take_two_chance",
                        WgonConfig.COUNTER_DRINK_TAKE_TWO_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.counter.approach_timeout",
                        WgonConfig.COUNTER_DRINK_APPROACH_TIMEOUT_TICKS.get(),
                        ValueKind.TICKS);
                drawBooleanLabel(graphics, y + ROW_HEIGHT * 4,
                        "config.feastwineallgone.inventory.enabled");
                drawIntRow(graphics, y + ROW_HEIGHT * 5,
                        "config.feastwineallgone.inventory.min_interval",
                        WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS.get(),
                        ValueKind.TICKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 6,
                        "config.feastwineallgone.inventory.max_interval",
                        WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS.get(),
                        ValueKind.TICKS);
            }

            case 2 -> {
                drawIntRow(graphics, y,
                        "config.feastwineallgone.inventory.craving_chance",
                        WgonConfig.INVENTORY_DRINK_CRAVING_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.inventory.injured_health_percent",
                        WgonConfig.INVENTORY_DRINK_INJURED_HEALTH_PERCENT.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.night_stealing.steal_chance",
                        WgonConfig.STEAL_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.night_stealing.drunk_sleep_chance",
                        WgonConfig.DRUNK_SLEEP_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 4,
                        "config.feastwineallgone.night_stealing.drunk_sleep_auto_wake",
                        WgonConfig.DRUNK_SLEEP_AUTO_WAKE_SECONDS.get(),
                        ValueKind.SECONDS);
                drawIntRow(graphics, y + ROW_HEIGHT * 5,
                        "config.feastwineallgone.night_stealing.search_radius",
                        WgonConfig.SEARCH_RADIUS.get(),
                        ValueKind.BLOCKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 6,
                        "config.feastwineallgone.night_stealing.max_drinks",
                        WgonConfig.MAX_DRINKS.get(),
                        ValueKind.NUMBER);
                drawIntRow(graphics, y + ROW_HEIGHT * 7,
                        "config.feastwineallgone.night_stealing.timeout",
                        WgonConfig.SESSION_TIMEOUT_SECONDS.get(),
                        ValueKind.SECONDS);
            }

            case 3 -> {
                drawBooleanLabel(graphics, y,
                        "config.feastwineallgone.generic.enabled");
                drawBooleanLabel(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.compat.farmingtales");
                drawBooleanLabel(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.compat.bountiful_fares");

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable(
                                "config.feastwineallgone.screen.compat_note"
                        ),
                        this.width / 2,
                        y + ROW_HEIGHT * 4,
                        0x909090
                );
            }

            case 4 -> {
                drawListLabel(graphics, y,
                        "config.feastwineallgone.dining_surfaces.whitelist");
                drawListLabel(graphics, y + 44,
                        "config.feastwineallgone.favorite.whitelist");
                drawListLabel(graphics, y + 88,
                        "config.feastwineallgone.generic.blacklist");

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable(
                                "config.feastwineallgone.screen.list_hint"
                        ),
                        this.width / 2,
                        y + 132,
                        0x909090
                );
            }

            case 5 -> {
                drawListLabel(graphics, y,
                        "config.feastwineallgone.counter.surface_whitelist");

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable(
                                "config.feastwineallgone.screen.counter_surface_hint"
                        ),
                        this.width / 2,
                        y + 52,
                        0x909090
                );

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable(
                                "config.feastwineallgone.screen.counter_surface_default_hint"
                        ),
                        this.width / 2,
                        y + 66,
                        0x909090
                );
            }


            case 6 -> {
                drawIntRow(graphics, y,
                        "config.feastwineallgone.fruit_tasting.trigger_chance",
                        WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.fruit_tasting.search_radius",
                        WgonConfig.FRUIT_TASTING_SEARCH_RADIUS.get(),
                        ValueKind.BLOCKS);
                drawIntRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.fruit_tasting.two_type_chance",
                        WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.fruit_tasting.all_type_chance",
                        WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.get(),
                        ValueKind.PERCENT);
                drawIntRow(graphics, y + ROW_HEIGHT * 4,
                        "config.feastwineallgone.fruit_tasting.golden_apple_steal_chance",
                        WgonConfig.GOLDEN_APPLE_STEAL_CHANCE.get(),
                        ValueKind.PERCENT);
                drawListLabel(graphics, y + ROW_HEIGHT * 5,
                        "config.feastwineallgone.fruit_tasting.favorite_fruits");

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable(
                                "config.feastwineallgone.screen.fruit_tasting_hint"
                        ),
                        this.width / 2,
                        y + ROW_HEIGHT * 7 - 4,
                        0x909090
                );
            }

            case 7 -> {
                drawIntRow(graphics, y,
                        "config.feastwineallgone.meal.timeout",
                        WgonConfig.MEAL_END_TIMEOUT_SECONDS.get(),
                        ValueKind.SECONDS);

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable("config.feastwineallgone.screen.meal_rules_hint"),
                        this.width / 2,
                        y + ROW_HEIGHT * 2,
                        0x909090
                );
            }

            case 8 -> {
                drawIntRow(graphics, y,
                        "config.feastwineallgone.meal.cooldown",
                        WgonConfig.MEAL_COOLDOWN_MINUTES.get(),
                        ValueKind.MINUTES);
                drawIntRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.meal.max_per_day",
                        WgonConfig.MEAL_MAX_PER_DAY.get(),
                        ValueKind.NUMBER);
                drawIntRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.meal.max_rewarded",
                        WgonConfig.MEAL_MAX_REWARDED.get(),
                        ValueKind.NUMBER);
                drawBooleanLabel(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.meal.show_cooldown_timer");

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable("config.feastwineallgone.screen.meal_limits_hint"),
                        this.width / 2,
                        y + ROW_HEIGHT * 4,
                        0x909090
                );
            }

            case 9 -> {
                drawDoubleRow(graphics, y,
                        "config.feastwineallgone.meal.normal_health",
                        WgonConfig.NORMAL_MEAL_HEALTH.get());
                drawDoubleRow(graphics, y + ROW_HEIGHT,
                        "config.feastwineallgone.meal.normal_armor",
                        WgonConfig.NORMAL_MEAL_ARMOR.get());
                drawDoubleRow(graphics, y + ROW_HEIGHT * 2,
                        "config.feastwineallgone.meal.favorite_health",
                        WgonConfig.FAVORITE_MEAL_HEALTH.get());
                drawDoubleRow(graphics, y + ROW_HEIGHT * 3,
                        "config.feastwineallgone.meal.favorite_armor",
                        WgonConfig.FAVORITE_MEAL_ARMOR.get());

                graphics.drawCenteredString(
                        this.font,
                        Component.translatable("config.feastwineallgone.screen.meal_growth_hint"),
                        this.width / 2,
                        y + ROW_HEIGHT * 5,
                        0x909090
                );
            }

            default -> {
            }
        }

        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        "config.feastwineallgone.screen.server_note"
                ),
                this.width / 2,
                this.height - 42,
                0x707070
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private void drawIntRow(
            GuiGraphics graphics,
            int y,
            String labelKey,
            int value,
            ValueKind kind
    ) {
        drawLabel(graphics, y, labelKey);

        graphics.drawCenteredString(
                this.font,
                formatValue(value, kind),
                this.width / 2 + 92,
                y + 6,
                0xFFFFFF
        );
    }

    private void drawDoubleRow(
            GuiGraphics graphics,
            int y,
            String labelKey,
            double value
    ) {
        drawLabel(graphics, y, labelKey);

        graphics.drawCenteredString(
                this.font,
                String.format(Locale.ROOT, "%.1f", value),
                this.width / 2 + 92,
                y + 6,
                0xFFFFFF
        );
    }

    private void drawBooleanLabel(
            GuiGraphics graphics,
            int y,
            String labelKey
    ) {
        drawLabel(graphics, y, labelKey);
    }

    private void drawListLabel(
            GuiGraphics graphics,
            int y,
            String labelKey
    ) {
        graphics.drawString(
                this.font,
                Component.translatable(labelKey),
                this.width / 2 - 150,
                y,
                0xE0E0E0,
                false
        );
    }

    private void drawLabel(
            GuiGraphics graphics,
            int y,
            String labelKey
    ) {
        graphics.drawString(
                this.font,
                Component.translatable(labelKey),
                this.width / 2 - 160,
                y + 6,
                0xE0E0E0,
                false
        );
    }

    private static String formatValue(
            int value,
            ValueKind kind
    ) {
        return switch (kind) {
            case TICKS -> formatTicks(value);
            case PERCENT -> value + "%";
            case BLOCKS -> Integer.toString(value);
            case SECONDS -> value + " s";
            case MINUTES -> value + " min";
            case NUMBER -> Integer.toString(value);
        };
    }

    private static String formatTicks(
            int ticks
    ) {
        double seconds =
                ticks / 20.0D;

        if (ticks % 20 == 0) {
            return String.format(
                    Locale.ROOT,
                    "%.0f s",
                    seconds
            );
        }

        return String.format(
                Locale.ROOT,
                "%.1f s",
                seconds
        );
    }

    private static void adjustInt(
            ForgeConfigSpec.IntValue value,
            int delta,
            int min,
            int max,
            boolean idleMinimum,
            boolean idleMaximum
    ) {
        int next =
                Mth.clamp(
                        value.get() + delta,
                        min,
                        max
                );

        value.set(next);

        /*
         * Exactly-two and all-varieties are disjoint branches of one cycle
         * roll, so their configured shares may not exceed 100% together.
         */
        if (value == WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE) {
            int maxAll = Math.max(
                    0,
                    100 - WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.get()
            );
            if (WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.get() > maxAll) {
                WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.set(maxAll);
            }
        }

        if (value == WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE) {
            int maxTwo = Math.max(
                    0,
                    100 - WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.get()
            );
            if (WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.get() > maxTwo) {
                WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.set(maxTwo);
            }
        }

        if (idleMinimum
                && WgonConfig.IDLE_ACTIVITY_MAX_ATTEMPT_TICKS.get() < next) {
            WgonConfig.IDLE_ACTIVITY_MAX_ATTEMPT_TICKS.set(next);
        }

        if (idleMaximum
                && WgonConfig.IDLE_ACTIVITY_MIN_ATTEMPT_TICKS.get() > next) {
            WgonConfig.IDLE_ACTIVITY_MIN_ATTEMPT_TICKS.set(next);
        }

        if (value == WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS
                && WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS.get() < next) {
            WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS.set(next);
        }

        if (value == WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS
                && WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS.get() > next) {
            WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS.set(next);
        }
    }

    private static void adjustDouble(
            ForgeConfigSpec.DoubleValue value,
            double delta,
            double min,
            double max
    ) {
        double next = Math.max(min, Math.min(max, value.get() + delta));
        value.set(next);
    }

    private void openPage(
            int targetPage
    ) {
        saveListFields();
        WgonConfig.SPEC.save();

        if (this.minecraft != null) {
            this.minecraft.setScreen(
                    new WgonConfigScreen(
                            parent,
                            targetPage
                    )
            );
        }
    }

    private void saveListFields() {
        if (diningSurfaceList != null) {
            WgonConfig.DINING_SURFACE_WHITELIST.set(
                    parseRegistryList(
                            diningSurfaceList.getValue()
                    )
            );
        }

        if (favoriteFoodList != null) {
            WgonConfig.FAVORITE_FOOD_WHITELIST.set(
                    parseRegistryList(
                            favoriteFoodList.getValue()
                    )
            );
        }

        if (genericBlacklist != null) {
            WgonConfig.GENERIC_PLACED_FOOD_BLACKLIST.set(
                    parseRegistryList(
                            genericBlacklist.getValue()
                    )
            );
        }

        if (counterSurfaceList != null) {
            WgonConfig.COUNTER_DRINK_SURFACE_WHITELIST.set(
                    parseRegistryList(
                            counterSurfaceList.getValue()
                    )
            );
        }

        if (favoriteFruitList != null) {
            WgonConfig.FAVORITE_FRUIT_WHITELIST.set(
                    parseRegistryList(
                            favoriteFruitList.getValue()
                    )
            );
        }
    }

    private static List<String> parseRegistryList(
            String raw
    ) {
        List<String> result =
                new ArrayList<>();

        if (raw == null
                || raw.isBlank()) {
            return result;
        }

        for (String token : raw.split("[,;]")) {
            String value = token.trim();

            if (value.isEmpty()
                    || ResourceLocation.tryParse(value) == null
                    || result.contains(value)) {
                continue;
            }

            result.add(value);
        }

        return result;
    }

    private void resetDefaults() {
        WgonConfig.STEAL_CHANCE.set(WgonConfig.DEFAULT_STEAL_CHANCE);
        WgonConfig.DRUNK_SLEEP_CHANCE.set(WgonConfig.DEFAULT_DRUNK_SLEEP_CHANCE);
        WgonConfig.DRUNK_SLEEP_AUTO_WAKE_SECONDS.set(WgonConfig.DEFAULT_DRUNK_SLEEP_AUTO_WAKE_SECONDS);
        WgonConfig.SEARCH_RADIUS.set(10);
        WgonConfig.MAX_DRINKS.set(5);
        WgonConfig.SESSION_TIMEOUT_SECONDS.set(60);

        WgonConfig.IDLE_ACTIVITY_MIN_ATTEMPT_TICKS.set(200);
        WgonConfig.IDLE_ACTIVITY_MAX_ATTEMPT_TICKS.set(600);
        WgonConfig.IDLE_ACTIVITY_MIXED_HANDOFF_TICKS.set(10);
        WgonConfig.IDLE_FOOD_SEARCH_RADIUS.set(8);
        WgonConfig.PLATTER_FINISH_ALL_CHANCE.set(25);

        WgonConfig.GENERIC_PLACED_FOOD_ENABLED.set(true);
        WgonConfig.GENERIC_PLACED_FOOD_BLACKLIST.set(List.of());
        WgonConfig.FARMING_TALES_PLACEABLE_COMPAT_ENABLED.set(true);
        WgonConfig.BOUNTIFUL_FARES_PASTRY_COMPAT_ENABLED.set(true);

        WgonConfig.FAVORITE_FOOD_WHITELIST.set(
                List.of("minecraft:cake")
        );
        WgonConfig.DINING_SURFACE_WHITELIST.set(List.of());

        WgonConfig.COUNTER_DRINK_ENABLED.set(true);
        WgonConfig.COUNTER_DRINK_SURFACE_WHITELIST.set(
                List.of(WgonConfig.DEFAULT_COUNTER_DRINK_SURFACE)
        );
        WgonConfig.COUNTER_DRINK_SEARCH_RADIUS.set(8);
        WgonConfig.COUNTER_DRINK_TAKE_TWO_CHANCE.set(35);
        WgonConfig.COUNTER_DRINK_APPROACH_TIMEOUT_TICKS.set(200);

        WgonConfig.FRUIT_TASTING_TRIGGER_CHANCE.set(
                WgonConfig.DEFAULT_FRUIT_TASTING_TRIGGER_CHANCE
        );
        WgonConfig.FRUIT_TASTING_SEARCH_RADIUS.set(
                WgonConfig.DEFAULT_FRUIT_TASTING_SEARCH_RADIUS
        );
        WgonConfig.FRUIT_TASTING_TWO_TYPE_CHANCE.set(
                WgonConfig.DEFAULT_FRUIT_TASTING_TWO_TYPE_CHANCE
        );
        WgonConfig.FRUIT_TASTING_ALL_TYPE_CHANCE.set(
                WgonConfig.DEFAULT_FRUIT_TASTING_ALL_TYPE_CHANCE
        );
        WgonConfig.GOLDEN_APPLE_STEAL_CHANCE.set(
                WgonConfig.DEFAULT_GOLDEN_APPLE_STEAL_CHANCE
        );
        WgonConfig.FAVORITE_FRUIT_WHITELIST.set(
                List.of()
        );

        WgonConfig.MEAL_END_TIMEOUT_SECONDS.set(60);
        WgonConfig.MEAL_COOLDOWN_MINUTES.set(5);
        WgonConfig.MEAL_COOLDOWN_TIMER_VISIBLE.set(true);
        WgonConfig.MEAL_MAX_PER_DAY.set(3);
        WgonConfig.MEAL_MAX_REWARDED.set(10);
        WgonConfig.NORMAL_MEAL_HEALTH.set(6.0D);
        WgonConfig.NORMAL_MEAL_ARMOR.set(5.0D);
        WgonConfig.FAVORITE_MEAL_HEALTH.set(10.0D);
        WgonConfig.FAVORITE_MEAL_ARMOR.set(10.0D);

        WgonConfig.INVENTORY_DRINK_ENABLED.set(true);
        WgonConfig.INVENTORY_DRINK_MIN_ATTEMPT_TICKS.set(1200);
        WgonConfig.INVENTORY_DRINK_MAX_ATTEMPT_TICKS.set(3600);
        WgonConfig.INVENTORY_DRINK_CRAVING_CHANCE.set(20);
        WgonConfig.INVENTORY_DRINK_INJURED_HEALTH_PERCENT.set(70);
    }

    private void saveAndReturn() {
        saveListFields();
        WgonConfig.SPEC.save();

        if (this.minecraft != null) {
            this.minecraft.setScreen(
                    this.parent
            );
        }
    }

    @Override
    public void onClose() {
        saveAndReturn();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String pageTitleKey() {
        return "config.feastwineallgone.screen.page."
                + page;
    }

    private enum ValueKind {
        TICKS,
        PERCENT,
        BLOCKS,
        SECONDS,
        MINUTES,
        NUMBER
    }
}

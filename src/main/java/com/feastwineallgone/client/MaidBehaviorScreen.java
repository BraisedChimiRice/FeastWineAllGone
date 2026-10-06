package com.feastwineallgone.client;

import com.feastwineallgone.config.MaidBehaviorSettings;
import com.feastwineallgone.network.WgonNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Per-maid behaviour screen opened by the Maid Toolbox.
 *
 * The values shown here are local editing state until the player presses Save.
 * Global probabilities/radii/reward values remain in the normal FWAG config.
 */
public final class MaidBehaviorScreen extends Screen {

    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 20;
    private static final int GAP = 6;

    private final int maidEntityId;
    private int drunkSleepLocationCount;
    private final List<ToggleBinding> toggleBindings = new ArrayList<>();

    private boolean masterEnabled;
    private boolean idleFoodEnabled;
    private boolean counterDrinkEnabled;
    private boolean fruitTastingEnabled;
    private boolean seatedDiningEnabled;
    private boolean nightStealEnabled;
    private boolean favoriteReactionEnabled;
    private boolean mealRewardsEnabled;
    private boolean cooldownTimerEnabled;

    public MaidBehaviorScreen(
            int maidEntityId,
            MaidBehaviorSettings.Snapshot snapshot,
            int drunkSleepLocationCount
    ) {
        super(Component.translatable(
                "screen.feastwineallgone.maid_settings.title"
        ));

        this.maidEntityId = maidEntityId;
        this.drunkSleepLocationCount = drunkSleepLocationCount;
        applyLocal(snapshot);
    }

    public static void open(
            int maidEntityId,
            MaidBehaviorSettings.Snapshot snapshot,
            int drunkSleepLocationCount
    ) {
        Minecraft.getInstance().setScreen(
                new MaidBehaviorScreen(
                        maidEntityId,
                        snapshot,
                        drunkSleepLocationCount
                )
        );
    }

    @Override
    protected void init() {
        toggleBindings.clear();

        int center = this.width / 2;
        int top = 48;

        addToggle(
                center - BUTTON_W / 2,
                top,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.master",
                () -> masterEnabled,
                value -> masterEnabled = value
        );

        int leftX = center - BUTTON_W - GAP / 2;
        int rightX = center + GAP / 2;
        int row1 = top + 30;
        int step = 25;

        addToggle(
                leftX,
                row1,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.idle_food",
                () -> idleFoodEnabled,
                value -> idleFoodEnabled = value
        );
        addToggle(
                rightX,
                row1,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.counter_drink",
                () -> counterDrinkEnabled,
                value -> counterDrinkEnabled = value
        );

        addToggle(
                leftX,
                row1 + step,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.fruit_tasting",
                () -> fruitTastingEnabled,
                value -> fruitTastingEnabled = value
        );
        addToggle(
                rightX,
                row1 + step,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.seated_dining",
                () -> seatedDiningEnabled,
                value -> seatedDiningEnabled = value
        );

        addToggle(
                leftX,
                row1 + step * 2,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.night_steal",
                () -> nightStealEnabled,
                value -> nightStealEnabled = value
        );
        addToggle(
                rightX,
                row1 + step * 2,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.favorite_reaction",
                () -> favoriteReactionEnabled,
                value -> favoriteReactionEnabled = value
        );

        addToggle(
                leftX,
                row1 + step * 3,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.meal_rewards",
                () -> mealRewardsEnabled,
                value -> mealRewardsEnabled = value
        );
        addToggle(
                rightX,
                row1 + step * 3,
                BUTTON_W,
                "screen.feastwineallgone.maid_settings.cooldown_timer",
                () -> cooldownTimerEnabled,
                value -> cooldownTimerEnabled = value
        );

        this.addRenderableWidget(
                Button.builder(
                                drunkSleepLocationsButtonMessage(),
                                button -> {
                                    WgonNetwork.sendClearDrunkSleepLocationsToServer(
                                            maidEntityId
                                    );
                                    drunkSleepLocationCount = 0;
                                    button.setMessage(
                                            drunkSleepLocationsButtonMessage()
                                    );
                                }
                        )
                        .bounds(
                                center - BUTTON_W,
                                row1 + step * 4,
                                BUTTON_W * 2,
                                BUTTON_H
                        )
                        .build()
        );

        int bottom = this.height - 28;

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "screen.feastwineallgone.maid_settings.defaults"
                                ),
                                button -> {
                                    applyLocal(
                                            MaidBehaviorSettings.Snapshot.defaults()
                                    );
                                    refreshToggles();
                                }
                        )
                        .bounds(
                                center - 145,
                                bottom,
                                90,
                                BUTTON_H
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable("gui.cancel"),
                                button -> onClose()
                        )
                        .bounds(
                                center - 45,
                                bottom,
                                90,
                                BUTTON_H
                        )
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "screen.feastwineallgone.maid_settings.save"
                                ),
                                button -> saveAndClose()
                        )
                        .bounds(
                                center + 55,
                                bottom,
                                90,
                                BUTTON_H
                        )
                        .build()
        );
    }

    private void addToggle(
            int x,
            int y,
            int width,
            String labelKey,
            BooleanSupplier getter,
            Consumer<Boolean> setter
    ) {
        Button button = Button.builder(
                        toggleMessage(
                                labelKey,
                                getter.getAsBoolean()
                        ),
                        clicked -> {
                            setter.accept(
                                    !getter.getAsBoolean()
                            );
                            clicked.setMessage(
                                    toggleMessage(
                                            labelKey,
                                            getter.getAsBoolean()
                                    )
                            );
                        }
                )
                .bounds(
                        x,
                        y,
                        width,
                        BUTTON_H
                )
                .build();

        this.addRenderableWidget(button);
        toggleBindings.add(
                new ToggleBinding(
                        button,
                        labelKey,
                        getter
                )
        );
    }

    private Component toggleMessage(
            String labelKey,
            boolean enabled
    ) {
        return Component.translatable(labelKey)
                .append(": ")
                .append(
                        Component.translatable(
                                enabled
                                        ? "screen.feastwineallgone.maid_settings.on"
                                        : "screen.feastwineallgone.maid_settings.off"
                        )
                );
    }

    private Component drunkSleepLocationsButtonMessage() {
        return Component.translatable(
                "screen.feastwineallgone.maid_settings.drunk_sleep_locations",
                drunkSleepLocationCount,
                3
        );
    }

    private void refreshToggles() {
        for (ToggleBinding binding : toggleBindings) {
            binding.button().setMessage(
                    toggleMessage(
                            binding.labelKey(),
                            binding.getter().getAsBoolean()
                    )
            );
        }
    }

    private void applyLocal(
            MaidBehaviorSettings.Snapshot snapshot
    ) {
        masterEnabled = snapshot.masterEnabled();
        idleFoodEnabled = snapshot.idleFoodEnabled();
        counterDrinkEnabled = snapshot.counterDrinkEnabled();
        fruitTastingEnabled = snapshot.fruitTastingEnabled();
        seatedDiningEnabled = snapshot.seatedDiningEnabled();
        nightStealEnabled = snapshot.nightStealEnabled();
        favoriteReactionEnabled = snapshot.favoriteReactionEnabled();
        mealRewardsEnabled = snapshot.mealRewardsEnabled();
        cooldownTimerEnabled = snapshot.cooldownTimerEnabled();
    }

    private MaidBehaviorSettings.Snapshot currentSnapshot() {
        return new MaidBehaviorSettings.Snapshot(
                masterEnabled,
                idleFoodEnabled,
                counterDrinkEnabled,
                fruitTastingEnabled,
                seatedDiningEnabled,
                nightStealEnabled,
                favoriteReactionEnabled,
                mealRewardsEnabled,
                cooldownTimerEnabled
        );
    }

    private void saveAndClose() {
        WgonNetwork.sendMaidSettingsToServer(
                maidEntityId,
                currentSnapshot()
        );

        onClose();
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
                14,
                0xFFFFFF
        );

        graphics.drawCenteredString(
                this.font,
                maidNameLine(),
                this.width / 2,
                28,
                0xE0E0E0
        );

        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        "screen.feastwineallgone.maid_settings.subtitle"
                ),
                this.width / 2,
                38,
                0x909090
        );

        super.render(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
    }

    private Component maidNameLine() {
        if (this.minecraft == null
                || this.minecraft.level == null) {
            return Component.empty();
        }

        Entity entity =
                this.minecraft.level.getEntity(
                        maidEntityId
                );

        if (entity == null) {
            return Component.translatable(
                    "screen.feastwineallgone.maid_settings.maid_unknown"
            );
        }

        return Component.translatable(
                "screen.feastwineallgone.maid_settings.maid_name",
                entity.getDisplayName()
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ToggleBinding(
            Button button,
            String labelKey,
            BooleanSupplier getter
    ) {
    }
}

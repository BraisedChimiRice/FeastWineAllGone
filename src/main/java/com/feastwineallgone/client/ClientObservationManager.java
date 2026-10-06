package com.feastwineallgone.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.network.WgonNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = FeastWineAllGone.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class ClientObservationManager {

    private enum Mode {
        NONE,
        NIGHT_OBSERVATION,
        DRUNK_WAKE_CLOSEUP
    }

    private static final double DEFAULT_MOVE_SPEED = 6.0D;
    private static final double DRUNK_WAKE_DEFAULT_MOVE_SPEED = 3.0D;

    private static final double DRUNK_WAKE_SEARCH_DISTANCE = 8.0D;
    private static final double DRUNK_WAKE_SEARCH_DISTANCE_SQR =
            DRUNK_WAKE_SEARCH_DISTANCE * DRUNK_WAKE_SEARCH_DISTANCE;

    private static final double DRUNK_WAKE_CAMERA_DISTANCE = 1.85D;
    private static final double DRUNK_WAKE_TARGET_Y_OFFSET = 0.08D;
    private static final double DRUNK_WAKE_CAMERA_HEIGHT = 0.28D;
    private static final double MIN_MOVE_SPEED = 1.0D;
    private static final double MAX_MOVE_SPEED = 30.0D;

    private static final double BOOST_MULTIPLIER = 3.0D;

    private static final double FOCUS_DISTANCE = 6.0D;
    private static final double FOCUS_HEIGHT = 1.4D;

    private static boolean active = false;
    private static Mode mode = Mode.NONE;

    private static int maidEntityId = -1;

    private static Vec3 cameraPosition = Vec3.ZERO;

    private static float cameraYaw = 0.0F;
    private static float cameraPitch = 0.0F;

    private static double moveSpeed = DEFAULT_MOVE_SPEED;

    private static CameraType previousCameraType = null;

    private static boolean waitingForInitialFocus = false;

    private static long previousRenderNanos = 0L;

    private static int suppressSleepFadeTicks = 0;

    /* Prevent repeated G presses from spamming skip packets. */
    private static boolean skipRequested = false;

    /* Prevent repeated right-click packets during the close-up. */
    private static boolean pokeRequested = false;

    private ClientObservationManager() {
    }

    public static void start(int maidId) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!active) {
            previousCameraType = minecraft.options.getCameraType();
        }

        active = true;
        mode = Mode.NIGHT_OBSERVATION;
        maidEntityId = maidId;

        moveSpeed = DEFAULT_MOVE_SPEED;
        previousRenderNanos = 0L;

        suppressSleepFadeTicks = 20;
        skipRequested = false;
        pokeRequested = false;

        minecraft.options.setCameraType(
                CameraType.FIRST_PERSON
        );

        if (minecraft.player != null) {
            cameraPosition =
                    minecraft.player.getEyePosition(1.0F);

            cameraYaw =
                    minecraft.player.getYRot();

            cameraPitch =
                    minecraft.player.getXRot();
        }

        waitingForInitialFocus = true;

        focusOnMaid();

        /*
         * Observation is a true grabbed-mouse free camera.  The vanilla
         * InBedChatScreen must be removed explicitly before grabbing the
         * cursor.  Minecraft may try to recreate that screen on a later tick
         * while the player is still sleeping; onScreenOpening() below blocks
         * only that sleep screen for the lifetime of observation mode.
         */
        if (minecraft.screen instanceof InBedChatScreen) {
            minecraft.setScreen(null);
        }

        minecraft.mouseHandler.grabMouse();
    }

    /**
     * Morning drunk-wake observation entered after the owner finds a passed-out
     * maid.
     *
     * This is now a true free camera, matching the night observation controls.
     * It starts from the player's current eye position and simply turns toward
     * the maid, so model size / sleep pose can never place the camera inside
     * her body. The player can then move to any comfortable viewing angle.
     */
    public static void startDrunkWake(
            int maidId
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (!active) {
            previousCameraType =
                    minecraft.options.getCameraType();
        }

        active = true;
        mode = Mode.DRUNK_WAKE_CLOSEUP;
        maidEntityId = maidId;
        waitingForInitialFocus = true;
        previousRenderNanos = 0L;
        skipRequested = false;
        pokeRequested = false;
        moveSpeed = DRUNK_WAKE_DEFAULT_MOVE_SPEED;

        minecraft.options.setCameraType(
                CameraType.FIRST_PERSON
        );

        if (minecraft.player != null) {
            cameraPosition =
                    minecraft.player.getEyePosition(1.0F);

            cameraYaw =
                    minecraft.player.getYRot();

            cameraPitch =
                    minecraft.player.getXRot();
        }

        /*
         * For drunk-wake mode focusOnMaid() only aims the camera at the maid;
         * it no longer teleports the camera to a hard-coded close-up point.
         */
        focusOnMaid();

        if (minecraft.screen != null
                && !(minecraft.screen instanceof PauseScreen)
                && !(minecraft.screen instanceof ChatScreen)) {
            minecraft.setScreen(null);
        }

        minecraft.mouseHandler.grabMouse();
    }

    public static void end() {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (!active) {
            suppressSleepFadeTicks = 20;
            return;
        }

        active = false;
        mode = Mode.NONE;

        maidEntityId = -1;

        waitingForInitialFocus = false;

        previousRenderNanos = 0L;

        suppressSleepFadeTicks = 20;

        if (previousCameraType != null) {
            minecraft.options.setCameraType(
                    previousCameraType
            );
        }

        previousCameraType = null;
        skipRequested = false;
        pokeRequested = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isDrunkWakeCloseup() {
        return active && mode == Mode.DRUNK_WAKE_CLOSEUP;
    }


    public static boolean isCameraOverrideActive() {
        return active;
    }

    public static Vec3 getCameraPosition() {
        return cameraPosition;
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static double getMoveSpeed() {
        return moveSpeed;
    }

    /**
     * Move the free camera close to the maid.
     *
     * This is only called when observation begins
     * or the player manually presses the locate button.
     */
    public static void focusOnMaid() {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (!active
                || minecraft.level == null) {

            return;
        }

        Entity maid =
                minecraft.level.getEntity(
                        maidEntityId
                );

        if (maid == null
                || maid.isRemoved()) {

            waitingForInitialFocus = true;
            return;
        }

        waitingForInitialFocus = false;

        Vec3 target =
                maid.getEyePosition(1.0F)
                        .add(
                                0.0D,
                                mode == Mode.DRUNK_WAKE_CLOSEUP
                                        ? DRUNK_WAKE_TARGET_Y_OFFSET
                                        : -0.10D,
                                0.0D
                        );

        if (mode == Mode.DRUNK_WAKE_CLOSEUP) {
            /*
             * Free-camera drunk wake:
             * "locate maid" only turns the camera toward her. It never moves
             * the camera, which avoids all model-size / sleeping-pose clipping.
             */
            lookAt(cameraPosition, target);
            return;
        }

        Vec3 forward =
                Vec3.directionFromRotation(
                        0.0F,
                        maid.getYRot()
                );

        cameraPosition =
                target
                        .subtract(
                                forward.scale(
                                        FOCUS_DISTANCE
                                )
                        )
                        .add(
                                0.0D,
                                FOCUS_HEIGHT,
                                0.0D
                        );

        lookAt(
                cameraPosition,
                target
        );
    }

    private static void lookAt(
            Vec3 from,
            Vec3 target
    ) {
        Vec3 direction =
                target.subtract(from);

        double horizontal =
                Math.sqrt(
                        direction.x * direction.x
                                + direction.z * direction.z
                );

        cameraYaw =
                (float) Math.toDegrees(
                        Math.atan2(
                                -direction.x,
                                direction.z
                        )
                );

        cameraPitch =
                (float) -Math.toDegrees(
                        Math.atan2(
                                direction.y,
                                horizontal
                        )
                );

        cameraPitch =
                Mth.clamp(
                        cameraPitch,
                        -89.0F,
                        89.0F
                );
    }

    /**
     * Receives relative cursor movement captured from MouseHandler.onMove().
     * The mixin derives the delta from consecutive raw callback coordinates
     * but never cancels vanilla onMove(), so Minecraft keeps its own mouse
     * bookkeeping intact.
     */
    public static void onMouseDelta(
            double deltaX,
            double deltaY
    ) {
        if (!active) {
            return;
        }

        /* Ignore impossible one-frame focus/warp spikes. */
        if (Math.abs(deltaX) > 1000.0D
                || Math.abs(deltaY) > 1000.0D) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        /*
         * Match vanilla's 1.20.1 sensitivity curve.  Entity.turn() applies
         * the final 0.15 factor, so we apply it here to the free camera.
         */
        double sensitivity = minecraft.options.sensitivity().get();
        double base = sensitivity * 0.6D + 0.2D;
        double scaled = base * base * base * 8.0D;
        double degreesPerDelta = scaled * 0.15D;

        cameraYaw += (float) (deltaX * degreesPerDelta);
        cameraPitch += (float) (deltaY * degreesPerDelta);
        cameraPitch = Mth.clamp(cameraPitch, -89.0F, 89.0F);
    }

    public static void adjustMoveSpeed(
            double scrollDelta
    ) {
        if (!active) {
            return;
        }

        moveSpeed =
                Mth.clamp(
                        moveSpeed
                                + scrollDelta,
                        MIN_MOVE_SPEED,
                        MAX_MOVE_SPEED
                );
    }

    /**
     * Vanilla keeps trying to expose InBedChatScreen while the local player
     * remains in bed.  That screen contains the normal "Leave Bed" button
     * and also releases the cursor, both of which conflict with WGON's night
     * observation camera.  Cancel only this one vanilla screen; pause/chat/
     * options screens are still allowed normally.
     */
    /**
     * Observation deliberately runs without the vanilla bed screen.  While
     * the free camera owns the client, gameplay screens opened directly from
     * the world (inventory, creative inventory, etc.) are also blocked so a
     * sleeping player cannot accidentally resume normal gameplay underneath
     * the camera.  Esc/chat are still allowed; once a real screen is already
     * open, its normal screen-to-screen navigation is left alone.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(
            ScreenEvent.Opening event
    ) {
        if (!active) {
            return;
        }

        if (event.getNewScreen() instanceof InBedChatScreen) {
            event.setCanceled(true);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen == null
                && event.getNewScreen() != null
                && !(event.getNewScreen() instanceof PauseScreen)
                && !(event.getNewScreen() instanceof ChatScreen)) {
            event.setCanceled(true);
        }
    }

    /**
     * The observation camera is not the sleeping player's hands.  Swallow
     * raw mouse buttons before vanilla turns them into attack/use/pick key
     * mappings.  This prevents left/right click from waking the player,
     * mining blocks, placing blocks, or using items while the camera is free.
     * Menus remain clickable because this only runs when no Screen is open.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onObservationMouseButton(
            InputEvent.MouseButton.Pre event
    ) {
        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null) {
            return;
        }

        if (mode == Mode.DRUNK_WAKE_CLOSEUP
                && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && event.getAction() == GLFW.GLFW_PRESS
                && !pokeRequested) {

            requestFacePoke();
        }

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(
            TickEvent.ClientTickEvent event
    ) {
        if (event.phase
                != TickEvent.Phase.END) {

            return;
        }

        if (suppressSleepFadeTicks > 0) {
            suppressSleepFadeTicks--;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null
                || minecraft.player == null) {

            return;
        }

        /*
         * The dedicated key deliberately does not require the player to hit the
         * maid's tiny / pose-shifted interaction box. Outside observation mode
         * it finds the nearest owned drunk maid within 8 blocks.
         */
        if (!active) {
            if (minecraft.screen == null) {
                while (WgonKeyMappings.DRUNK_WAKE_INTERACT.consumeClick()) {
                    requestNearestDrunkWakeCloseup();
                }
            }
            return;
        }

        if (minecraft.options.getCameraType()
                != CameraType.FIRST_PERSON) {

            minecraft.options.setCameraType(
                    CameraType.FIRST_PERSON
            );
        }

        if (waitingForInitialFocus) {
            focusOnMaid();
        }

        if (mode == Mode.DRUNK_WAKE_CLOSEUP) {
            Entity maid =
                    minecraft.level.getEntity(
                            maidEntityId
                    );

            if (!(maid instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid entityMaid)
                    || maid.isRemoved()
                    || !ClientDrunkSleepState.isActive(entityMaid)) {
                end();
                return;
            }

            if (minecraft.screen == null) {
                while (WgonKeyMappings.DRUNK_WAKE_INTERACT.consumeClick()) {
                    requestFacePoke();
                }

                while (WgonKeyMappings.LOCATE_MAID.consumeClick()) {
                    focusOnMaid();
                }

                while (WgonKeyMappings.SKIP_AND_SLEEP.consumeClick()) {
                    end();
                }
            }

            if (minecraft.screen == null
                    && !minecraft.mouseHandler.isMouseGrabbed()) {
                minecraft.mouseHandler.grabMouse();
            }

            return;
        }

        if (minecraft.screen == null) {
            while (WgonKeyMappings.LOCATE_MAID.consumeClick()) {
                focusOnMaid();
            }

            while (WgonKeyMappings.SKIP_AND_SLEEP.consumeClick()) {
                requestSkip();
            }
        }

        /*
         * Keep normal gameplay mouse capture while no other screen is open.
         * If the player deliberately opens a menu with Esc, do not fight it.
         */
        if (minecraft.screen == null
                && !minecraft.mouseHandler.isMouseGrabbed()) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(
            TickEvent.RenderTickEvent event
    ) {
        if (event.phase
                != TickEvent.Phase.START) {

            return;
        }

        if (!active) {
            previousRenderNanos = 0L;
            return;
        }

        long now =
                System.nanoTime();

        if (previousRenderNanos == 0L) {
            previousRenderNanos = now;
            return;
        }

        double deltaSeconds =
                (now - previousRenderNanos)
                        / 1_000_000_000.0D;

        previousRenderNanos = now;

        deltaSeconds =
                Mth.clamp(
                        deltaSeconds,
                        0.0D,
                        0.05D
                );

        updateFreeCameraMovement(
                deltaSeconds
        );
    }

    /**
     * True free-camera movement.
     *
     * Camera movement is calculated every rendered frame,
     * independently from the maid.
     */
    private static void updateFreeCameraMovement(
            double deltaSeconds
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        long window =
                minecraft
                        .getWindow()
                        .getWindow();

        boolean forwardPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_W
                );

        boolean backwardPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_S
                );

        boolean leftPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_A
                );

        boolean rightPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_D
                );

        boolean upPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_SPACE
                );

        boolean downPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_LEFT_SHIFT
                )
                        || InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_RIGHT_SHIFT
                );

        boolean boostPressed =
                InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_LEFT_CONTROL
                )
                        || InputConstants.isKeyDown(
                        window,
                        GLFW.GLFW_KEY_RIGHT_CONTROL
                );

        Vec3 movement =
                Vec3.ZERO;

        Vec3 forward =
                Vec3.directionFromRotation(
                        cameraPitch,
                        cameraYaw
                );

        Vec3 horizontalForward =
                new Vec3(
                        forward.x,
                        0.0D,
                        forward.z
                );

        if (horizontalForward.lengthSqr()
                > 0.000001D) {

            horizontalForward =
                    horizontalForward.normalize();
        }

        /*
         * IMPORTANT:
         *
         * Previous version:
         *
         *     UP.cross(FORWARD)
         *
         * produced a mirrored horizontal axis.
         *
         * Correct right vector:
         *
         *     FORWARD.cross(UP)
         */
        Vec3 right =
                horizontalForward.cross(
                        new Vec3(
                                0.0D,
                                1.0D,
                                0.0D
                        )
                );

        if (forwardPressed) {
            movement =
                    movement.add(
                            forward
                    );
        }

        if (backwardPressed) {
            movement =
                    movement.subtract(
                            forward
                    );
        }

        /*
         * A = left
         */
        if (leftPressed) {
            movement =
                    movement.subtract(
                            right
                    );
        }

        /*
         * D = right
         */
        if (rightPressed) {
            movement =
                    movement.add(
                            right
                    );
        }

        if (upPressed) {
            movement =
                    movement.add(
                            0.0D,
                            1.0D,
                            0.0D
                    );
        }

        if (downPressed) {
            movement =
                    movement.add(
                            0.0D,
                            -1.0D,
                            0.0D
                    );
        }

        if (movement.lengthSqr()
                < 0.000001D) {

            return;
        }

        movement =
                movement.normalize();

        double speed =
                moveSpeed;

        if (boostPressed) {
            speed *=
                    BOOST_MULTIPLIER;
        }

        cameraPosition =
                cameraPosition.add(
                        movement.scale(
                                speed
                                        * deltaSeconds
                        )
                );
    }

    private static void requestNearestDrunkWakeCloseup() {
        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.level == null
                || minecraft.player == null) {
            return;
        }

        EntityMaid nearest = null;
        double nearestDistanceSqr =
                DRUNK_WAKE_SEARCH_DISTANCE_SQR;

        for (Entity entity :
                minecraft.level.entitiesForRendering()) {

            if (!(entity instanceof EntityMaid maid)) {
                continue;
            }

            if (!ClientDrunkSleepState.isActive(maid)) {
                continue;
            }

            if (maid.getOwnerUUID() == null
                    || !maid.getOwnerUUID().equals(
                            minecraft.player.getUUID()
                    )) {
                continue;
            }

            double distanceSqr =
                    minecraft.player.distanceToSqr(maid);

            if (distanceSqr <= nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearest = maid;
            }
        }

        if (nearest == null) {
            minecraft.player.displayClientMessage(
                    Component.translatable(
                            "message.feastwineallgone.drunk_wake.none_nearby"
                    ),
                    true
            );
            return;
        }

        WgonNetwork.requestDrunkWakeCloseupFromServer(
                nearest.getId()
        );
    }

    private static void requestFacePoke() {
        if (!isDrunkWakeCloseup()
                || pokeRequested
                || maidEntityId < 0) {
            return;
        }

        pokeRequested = true;

        WgonNetwork.sendPokeDrunkMaidToServer(
                maidEntityId
        );
    }

    private static void requestSkip() {
        if (!active || skipRequested) {
            return;
        }

        skipRequested = true;
        WgonNetwork.sendSkipToServer();
    }

    @SubscribeEvent
    public static void onMouseScroll(
            InputEvent.MouseScrollingEvent event
    ) {
        if (!active) {
            return;
        }

        adjustMoveSpeed(event.getScrollDelta());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderObservationHud(
            RenderGuiEvent.Post event
    ) {
        if (!active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        if (mode == Mode.DRUNK_WAKE_CLOSEUP) {
            String interactKey =
                    WgonKeyMappings.DRUNK_WAKE_INTERACT
                            .getTranslatedKeyMessage()
                            .getString();

            String wakeText =
                    pokeRequested
                            ? "正在叫醒……"
                            : "再次按 " + interactKey + " 戳脸唤醒";

            String exitKey =
                    WgonKeyMappings.SKIP_AND_SLEEP
                            .getTranslatedKeyMessage()
                            .getString();

            String exitText =
                    "按 " + exitKey + " 退出观察";

            String locateKey =
                    WgonKeyMappings.LOCATE_MAID
                            .getTranslatedKeyMessage()
                            .getString();

            int boxWidth =
                    Math.max(
                            190,
                            minecraft.font.width(wakeText) + 24
                    );

            int x = 14;
            int y = height / 2 - 10;

            drawPromptBox(
                    guiGraphics,
                    minecraft,
                    x,
                    y,
                    boxWidth,
                    20,
                    wakeText,
                    !pokeRequested
            );

            guiGraphics.drawString(
                    minecraft.font,
                    Component.literal(exitText),
                    x + 3,
                    y + 27,
                    0xDDDDDD,
                    true
            );

            guiGraphics.drawCenteredString(
                    minecraft.font,
                    Component.literal("自由镜头：WASD移动 · 空格上升 · Shift下降 · Ctrl加速"),
                    width / 2,
                    12,
                    0xFFFFFF
            );

            guiGraphics.drawCenteredString(
                    minecraft.font,
                    Component.literal("鼠标转向 · 滚轮调速 · " + locateKey + " 重新看向酒狐"),
                    width / 2,
                    25,
                    0xDDDDDD
            );

            return;
        }

        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.literal("酒狐正在偷偷行动……"),
                width / 2,
                12,
                0xFFFFFF
        );

        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.literal("WASD移动 · 空格上升 · Shift下降 · Ctrl加速"),
                width / 2,
                25,
                0xDDDDDD
        );

        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.literal("移动鼠标转动视角 · 滚轮调整速度"),
                width / 2,
                38,
                0xDDDDDD
        );

        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.literal(
                        String.format(
                                "自由镜头速度：%.1f 格/秒",
                                moveSpeed
                        )
                ),
                width / 2,
                51,
                0xBBBBBB
        );

        String locateKey = WgonKeyMappings.LOCATE_MAID
                .getTranslatedKeyMessage()
                .getString();

        String skipKey = WgonKeyMappings.SKIP_AND_SLEEP
                .getTranslatedKeyMessage()
                .getString();

        String locateText = "定位酒狐（" + locateKey + "）";
        String skipText = skipRequested
                ? "正在继续睡觉……"
                : "跳过并继续睡觉（酒依然会被偷喝）（" + skipKey + "）";

        int gap = 8;
        int locateWidth = Math.max(150, minecraft.font.width(locateText) + 24);
        int skipWidth = Math.max(260, minecraft.font.width(skipText) + 24);
        int totalWidth = locateWidth + gap + skipWidth;
        int startX = width / 2 - totalWidth / 2;
        int y = height - 35;

        drawPromptBox(
                guiGraphics,
                minecraft,
                startX,
                y,
                locateWidth,
                20,
                locateText,
                true
        );

        drawPromptBox(
                guiGraphics,
                minecraft,
                startX + locateWidth + gap,
                y,
                skipWidth,
                20,
                skipText,
                !skipRequested
        );
    }

    private static void drawPromptBox(
            GuiGraphics guiGraphics,
            Minecraft minecraft,
            int x,
            int y,
            int width,
            int height,
            String text,
            boolean enabled
    ) {
        int border = enabled ? 0xFFB8B8B8 : 0xFF666666;
        int fill = enabled ? 0xB0404040 : 0xB0202020;
        int textColor = enabled ? 0xFFFFFF : 0x999999;

        guiGraphics.fill(x, y, x + width, y + height, border);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        guiGraphics.drawCenteredString(
                minecraft.font,
                Component.literal(text),
                x + width / 2,
                y + 6,
                textColor
        );
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(
            RenderGuiOverlayEvent.Pre event
    ) {
        if (!(active
                || suppressSleepFadeTicks > 0)) {

            return;
        }

        if (event.getOverlay()
                .id()
                .equals(
                        VanillaGuiOverlay
                                .SLEEP_FADE
                                .id()
                )) {

            event.setCanceled(true);
        }
    }

}

package com.feastwineallgone.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.behavior.DrunkSleepData;
import com.feastwineallgone.behavior.DrunkSleepManager;
import com.feastwineallgone.behavior.DrunkWakeInteractionManager;
import com.feastwineallgone.behavior.NightStealManager;
import com.feastwineallgone.behavior.WakeStretchLockManager;
import com.feastwineallgone.client.ClientObservationManager;
import com.feastwineallgone.client.ClientDrunkSleepState;
import com.feastwineallgone.client.ClientWakeStretchState;
import com.feastwineallgone.client.MaidBehaviorScreen;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import org.slf4j.Logger;

import java.util.UUID;
import java.util.function.Supplier;

public final class WgonNetwork {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    /*
     * Version 9 packet format is unchanged in V6.
     * V6 moves the settle/ground decision to the server wake gate.
     */
    private static final String PROTOCOL_VERSION = "9";

    private static SimpleChannel channel;
    private static boolean registered = false;
    private static int nextId = 0;

    private WgonNetwork() {
    }

    /** Must be called during the mod constructor. */
    public static void register() {
        if (registered) {
            return;
        }

        channel =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(
                                FeastWineAllGone.MOD_ID,
                                "main"
                        ),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals
                );

        channel.registerMessage(
                nextId++,
                StartObservationS2C.class,
                StartObservationS2C::encode,
                StartObservationS2C::decode,
                StartObservationS2C::handle
        );

        channel.registerMessage(
                nextId++,
                EndObservationS2C.class,
                EndObservationS2C::encode,
                EndObservationS2C::decode,
                EndObservationS2C::handle
        );

        channel.registerMessage(
                nextId++,
                SkipObservationC2S.class,
                SkipObservationC2S::encode,
                SkipObservationC2S::decode,
                SkipObservationC2S::handle
        );

        channel.registerMessage(
                nextId++,
                OpenMaidSettingsS2C.class,
                OpenMaidSettingsS2C::encode,
                OpenMaidSettingsS2C::decode,
                OpenMaidSettingsS2C::handle
        );

        channel.registerMessage(
                nextId++,
                UpdateMaidSettingsC2S.class,
                UpdateMaidSettingsC2S::encode,
                UpdateMaidSettingsC2S::decode,
                UpdateMaidSettingsC2S::handle
        );

        channel.registerMessage(
                nextId++,
                ClearDrunkSleepLocationsC2S.class,
                ClearDrunkSleepLocationsC2S::encode,
                ClearDrunkSleepLocationsC2S::decode,
                ClearDrunkSleepLocationsC2S::handle
        );

        channel.registerMessage(
                nextId++,
                DrunkSleepStateS2C.class,
                DrunkSleepStateS2C::encode,
                DrunkSleepStateS2C::decode,
                DrunkSleepStateS2C::handle
        );

        channel.registerMessage(
                nextId++,
                StartDrunkWakeCloseupS2C.class,
                StartDrunkWakeCloseupS2C::encode,
                StartDrunkWakeCloseupS2C::decode,
                StartDrunkWakeCloseupS2C::handle
        );

        channel.registerMessage(
                nextId++,
                PokeDrunkMaidC2S.class,
                PokeDrunkMaidC2S::encode,
                PokeDrunkMaidC2S::decode,
                PokeDrunkMaidC2S::handle
        );

        channel.registerMessage(
                nextId++,
                RequestDrunkWakeCloseupC2S.class,
                RequestDrunkWakeCloseupC2S::encode,
                RequestDrunkWakeCloseupC2S::decode,
                RequestDrunkWakeCloseupC2S::handle
        );

        channel.registerMessage(
                nextId++,
                WakeStretchS2C.class,
                WakeStretchS2C::encode,
                WakeStretchS2C::decode,
                WakeStretchS2C::handle
        );

        registered = true;
    }

    public static void sendStart(
            ServerPlayer player,
            int maidEntityId
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new StartObservationS2C(
                        maidEntityId
                )
        );
    }

    public static void sendEnd(
            ServerPlayer player
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new EndObservationS2C()
        );
    }

    public static void sendSkipToServer() {
        if (!registered || channel == null) {
            return;
        }

        channel.sendToServer(
                new SkipObservationC2S()
        );
    }

    public static void sendOpenMaidSettings(
            ServerPlayer player,
            EntityMaid maid
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new OpenMaidSettingsS2C(
                        maid.getId(),
                        MaidBehaviorSettings.snapshot(maid),
                        DrunkSleepData.getRecordedLocationCount(maid)
                )
        );
    }

    public static void sendMaidSettingsToServer(
            int maidEntityId,
            MaidBehaviorSettings.Snapshot snapshot
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.sendToServer(
                new UpdateMaidSettingsC2S(
                        maidEntityId,
                        snapshot
                )
        );
    }

    public static void sendClearDrunkSleepLocationsToServer(
            int maidEntityId
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.sendToServer(
                new ClearDrunkSleepLocationsC2S(
                        maidEntityId
                )
        );
    }

    public static void sendStartDrunkWake(
            ServerPlayer player,
            int maidEntityId
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new StartDrunkWakeCloseupS2C(
                        maidEntityId
                )
        );
    }

    public static void requestDrunkWakeCloseupFromServer(
            int maidEntityId
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.sendToServer(
                new RequestDrunkWakeCloseupC2S(
                        maidEntityId
                )
        );
    }

    public static void sendPokeDrunkMaidToServer(
            int maidEntityId
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.sendToServer(
                new PokeDrunkMaidC2S(
                        maidEntityId
                )
        );
    }

    /**
     * Tell every client in this dimension to play the maid model's authored
     * wake/stretch sequence. The packet is tiny and happens only after a
     * successful owner face-poke.
     */
    public static void broadcastWakeStretch(
            EntityMaid maid,
            int settleTicks,
            int durationTicks
    ) {
        if (!registered
                || channel == null
                || !(maid.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        int safeSettle =
                Math.max(0, settleTicks);

        int safeDuration =
                Math.max(1, durationTicks);

        LOGGER.info(
                "[WGON WakeStretch] server BROADCAST entityId={} uuid={} modelId={} isYsm={} ysmId={} settleTicks={} durationTicks={} players={}",
                maid.getId(),
                maid.getUUID(),
                maid.getModelId(),
                maid.isYsmModel(),
                maid.getYsmModelId(),
                safeSettle,
                safeDuration,
                level.players().size()
        );

        WakeStretchS2C message =
                new WakeStretchS2C(
                        maid.getId(),
                        maid.getUUID(),
                        safeSettle,
                        safeDuration
                );

        for (ServerPlayer player : level.players()) {
            channel.send(
                    PacketDistributor.PLAYER.with(
                            () -> player
                    ),
                    message
            );
        }
    }

    /**
     * Broadcast the authoritative drunk-sleep state to every player currently
     * in the maid's dimension. This event is rare (sleep start / wake only), so
     * a tiny dimension-wide packet is both simple and robust.
     */
    public static void broadcastDrunkSleepState(
            EntityMaid maid,
            boolean active,
            BlockPos pos,
            float yaw
    ) {
        if (!registered
                || channel == null
                || !(maid.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        DrunkSleepStateS2C message =
                new DrunkSleepStateS2C(
                        maid.getId(),
                        maid.getUUID(),
                        active,
                        level.dimension().location(),
                        pos,
                        yaw
                );

        for (ServerPlayer player : level.players()) {
            channel.send(
                    PacketDistributor.PLAYER.with(
                            () -> player
                    ),
                    message
            );
        }
    }

    /**
     * Send one exact state snapshot to a player that has just started tracking
     * this maid. Sending inactive snapshots too is intentional: it clears a
     * stale numeric entity id on the client if one ever survived a reconnect.
     */
    public static void sendDrunkSleepState(
            ServerPlayer player,
            EntityMaid maid
    ) {
        if (!registered || channel == null) {
            return;
        }

        DrunkSleepData.ActiveSleep active =
                DrunkSleepData.getActiveSleep(maid)
                        .orElse(null);

        ResourceLocation dimension =
                player.level().dimension().location();

        BlockPos pos =
                active != null
                        ? active.pos()
                        : maid.blockPosition();

        float yaw =
                active != null
                        ? active.yaw()
                        : maid.getYRot();

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new DrunkSleepStateS2C(
                        maid.getId(),
                        maid.getUUID(),
                        active != null,
                        dimension,
                        pos,
                        yaw
                )
        );
    }

    public static void sendDrunkSleepClear(
            ServerPlayer player,
            EntityMaid maid
    ) {
        if (!registered || channel == null) {
            return;
        }

        channel.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new DrunkSleepStateS2C(
                        maid.getId(),
                        maid.getUUID(),
                        false,
                        player.level().dimension().location(),
                        maid.blockPosition(),
                        maid.getYRot()
                )
        );
    }

    private static void writeSnapshot(
            FriendlyByteBuf buffer,
            MaidBehaviorSettings.Snapshot snapshot
    ) {
        buffer.writeBoolean(snapshot.masterEnabled());
        buffer.writeBoolean(snapshot.idleFoodEnabled());
        buffer.writeBoolean(snapshot.counterDrinkEnabled());
        buffer.writeBoolean(snapshot.fruitTastingEnabled());
        buffer.writeBoolean(snapshot.seatedDiningEnabled());
        buffer.writeBoolean(snapshot.nightStealEnabled());
        buffer.writeBoolean(snapshot.favoriteReactionEnabled());
        buffer.writeBoolean(snapshot.mealRewardsEnabled());
        buffer.writeBoolean(snapshot.cooldownTimerEnabled());
    }

    private static MaidBehaviorSettings.Snapshot readSnapshot(
            FriendlyByteBuf buffer
    ) {
        return new MaidBehaviorSettings.Snapshot(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    public record StartObservationS2C(
            int maidEntityId
    ) {

        private static void encode(
                StartObservationS2C message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(
                    message.maidEntityId()
            );
        }

        private static StartObservationS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new StartObservationS2C(
                    buffer.readVarInt()
            );
        }

        private static void handle(
                StartObservationS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> ClientObservationManager.start(
                                    message.maidEntityId()
                            )
                    )
            );

            context.setPacketHandled(true);
        }
    }

    public record EndObservationS2C() {

        private static void encode(
                EndObservationS2C message,
                FriendlyByteBuf buffer
        ) {
        }

        private static EndObservationS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new EndObservationS2C();
        }

        private static void handle(
                EndObservationS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> ClientObservationManager::end
                    )
            );

            context.setPacketHandled(true);
        }
    }

    public record SkipObservationC2S() {

        private static void encode(
                SkipObservationC2S message,
                FriendlyByteBuf buffer
        ) {
        }

        private static SkipObservationC2S decode(
                FriendlyByteBuf buffer
        ) {
            return new SkipObservationC2S();
        }

        private static void handle(
                SkipObservationC2S message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> {
                        ServerPlayer sender = context.getSender();

                        if (sender != null) {
                            NightStealManager.requestSkip(sender);
                        }
                    }
            );

            context.setPacketHandled(true);
        }
    }

    public record OpenMaidSettingsS2C(
            int maidEntityId,
            MaidBehaviorSettings.Snapshot snapshot,
            int drunkSleepLocationCount
    ) {
        private static void encode(
                OpenMaidSettingsS2C message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(message.maidEntityId());
            writeSnapshot(buffer, message.snapshot());
            buffer.writeVarInt(message.drunkSleepLocationCount());
        }

        private static OpenMaidSettingsS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new OpenMaidSettingsS2C(
                    buffer.readVarInt(),
                    readSnapshot(buffer),
                    buffer.readVarInt()
            );
        }

        private static void handle(
                OpenMaidSettingsS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> MaidBehaviorScreen.open(
                                    message.maidEntityId(),
                                    message.snapshot(),
                                    message.drunkSleepLocationCount()
                            )
                    )
            );

            context.setPacketHandled(true);
        }
    }

    public record UpdateMaidSettingsC2S(
            int maidEntityId,
            MaidBehaviorSettings.Snapshot snapshot
    ) {
        private static void encode(
                UpdateMaidSettingsC2S message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(message.maidEntityId());
            writeSnapshot(buffer, message.snapshot());
        }

        private static UpdateMaidSettingsC2S decode(
                FriendlyByteBuf buffer
        ) {
            return new UpdateMaidSettingsC2S(
                    buffer.readVarInt(),
                    readSnapshot(buffer)
            );
        }

        private static void handle(
                UpdateMaidSettingsC2S message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> {
                        ServerPlayer sender = context.getSender();
                        if (sender == null) {
                            return;
                        }

                        Entity entity = sender.level().getEntity(
                                message.maidEntityId()
                        );

                        if (!(entity instanceof EntityMaid maid)) {
                            return;
                        }

                        if (maid.getOwnerUUID() == null
                                || !maid.getOwnerUUID().equals(
                                        sender.getUUID()
                                )) {
                            return;
                        }

                        /* Do not allow remote arbitrary entity editing. */
                        if (sender.distanceToSqr(maid) > 100.0D) {
                            return;
                        }

                        MaidBehaviorSettings.apply(
                                maid,
                                message.snapshot()
                        );
                    }
            );

            context.setPacketHandled(true);
        }
    }
    public record ClearDrunkSleepLocationsC2S(
            int maidEntityId
    ) {
        private static void encode(
                ClearDrunkSleepLocationsC2S message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(message.maidEntityId());
        }

        private static ClearDrunkSleepLocationsC2S decode(
                FriendlyByteBuf buffer
        ) {
            return new ClearDrunkSleepLocationsC2S(
                    buffer.readVarInt()
            );
        }

        private static void handle(
                ClearDrunkSleepLocationsC2S message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context = contextSupplier.get();

            context.enqueueWork(
                    () -> {
                        ServerPlayer sender = context.getSender();
                        if (sender == null) {
                            return;
                        }

                        Entity entity = sender.level().getEntity(
                                message.maidEntityId()
                        );

                        if (!(entity instanceof EntityMaid maid)) {
                            return;
                        }

                        if (maid.getOwnerUUID() == null
                                || !maid.getOwnerUUID().equals(
                                        sender.getUUID()
                                )) {
                            return;
                        }

                        if (sender.distanceToSqr(maid) > 100.0D) {
                            return;
                        }

                        DrunkSleepData.clearRecordedLocations(maid);
                    }
            );

            context.setPacketHandled(true);
        }
    }


    public record DrunkSleepStateS2C(
            int maidEntityId,
            UUID maidUuid,
            boolean active,
            ResourceLocation dimension,
            BlockPos pos,
            float yaw
    ) {
        private static void encode(
                DrunkSleepStateS2C message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(message.maidEntityId());
            buffer.writeUUID(message.maidUuid());
            buffer.writeBoolean(message.active());
            buffer.writeResourceLocation(message.dimension());
            buffer.writeBlockPos(message.pos());
            buffer.writeFloat(message.yaw());
        }

        private static DrunkSleepStateS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new DrunkSleepStateS2C(
                    buffer.readVarInt(),
                    buffer.readUUID(),
                    buffer.readBoolean(),
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readFloat()
            );
        }

        private static void handle(
                DrunkSleepStateS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () -> ClientDrunkSleepState.apply(
                                    message.maidEntityId(),
                                    message.maidUuid(),
                                    message.active(),
                                    message.dimension(),
                                    message.pos(),
                                    message.yaw()
                            )
                    )
            );

            context.setPacketHandled(true);
        }
    }



    public record StartDrunkWakeCloseupS2C(
            int maidEntityId
    ) {
        private static void encode(
                StartDrunkWakeCloseupS2C message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(
                    message.maidEntityId()
            );
        }

        private static StartDrunkWakeCloseupS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new StartDrunkWakeCloseupS2C(
                    buffer.readVarInt()
            );
        }

        private static void handle(
                StartDrunkWakeCloseupS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () ->
                                    ClientObservationManager.startDrunkWake(
                                            message.maidEntityId()
                                    )
                    )
            );

            context.setPacketHandled(true);
        }
    }

    public record PokeDrunkMaidC2S(
            int maidEntityId
    ) {
        private static void encode(
                PokeDrunkMaidC2S message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(
                    message.maidEntityId()
            );
        }

        private static PokeDrunkMaidC2S decode(
                FriendlyByteBuf buffer
        ) {
            return new PokeDrunkMaidC2S(
                    buffer.readVarInt()
            );
        }

        private static void handle(
                PokeDrunkMaidC2S message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(
                    () -> {
                        ServerPlayer player =
                                context.getSender();

                        if (player == null) {
                            return;
                        }

                        Entity entity =
                                player.serverLevel()
                                        .getEntity(
                                                message.maidEntityId()
                                        );

                        if (!(entity instanceof EntityMaid maid)) {
                            sendEnd(player);
                            return;
                        }

                        boolean woke =
                                DrunkSleepManager.wakeByOwnerFacePoke(
                                        player,
                                        maid
                                );

                        LOGGER.info(
                                "[WGON WakeStretch] face poke result entityId={} woke={}",
                                maid.getId(),
                                woke
                        );

                        if (woke) {
                            /*
                             * V6:
                             * Do not start the stretch on a fixed one-second
                             * timer. WakeStretchLockManager first allows the
                             * maid to finish vertical collision/gravity, waits
                             * for a stable onGround state, and only then
                             * broadcasts the animation packet.
                             */
                            WakeStretchLockManager.start(
                                    maid,
                                    ClientWakeStretchState.DEFAULT_DURATION_TICKS
                            );
                        }

                        /*
                         * Keep the animation layer independent from the
                         * observation camera. The free camera closes normally;
                         * the maid continues her 5.5 second authored stretch
                         * animation in the world.
                         */
                        sendEnd(player);
                    }
            );

            context.setPacketHandled(true);
        }
    }



    public record RequestDrunkWakeCloseupC2S(
            int maidEntityId
    ) {
        private static void encode(
                RequestDrunkWakeCloseupC2S message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(
                    message.maidEntityId()
            );
        }

        private static RequestDrunkWakeCloseupC2S decode(
                FriendlyByteBuf buffer
        ) {
            return new RequestDrunkWakeCloseupC2S(
                    buffer.readVarInt()
            );
        }

        private static void handle(
                RequestDrunkWakeCloseupC2S message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(
                    () -> {
                        ServerPlayer player =
                                context.getSender();

                        if (player == null) {
                            return;
                        }

                        Entity entity =
                                player.serverLevel()
                                        .getEntity(
                                                message.maidEntityId()
                                        );

                        if (entity instanceof EntityMaid maid) {
                            DrunkWakeInteractionManager.tryStartCloseup(
                                    player,
                                    maid
                            );
                        }
                    }
            );

            context.setPacketHandled(true);
        }
    }



    public record WakeStretchS2C(
            int maidEntityId,
            UUID maidUuid,
            int settleTicks,
            int durationTicks
    ) {
        private static void encode(
                WakeStretchS2C message,
                FriendlyByteBuf buffer
        ) {
            buffer.writeVarInt(
                    message.maidEntityId()
            );
            buffer.writeUUID(
                    message.maidUuid()
            );
            buffer.writeVarInt(
                    message.settleTicks()
            );
            buffer.writeVarInt(
                    message.durationTicks()
            );
        }

        private static WakeStretchS2C decode(
                FriendlyByteBuf buffer
        ) {
            return new WakeStretchS2C(
                    buffer.readVarInt(),
                    buffer.readUUID(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
        }

        private static void handle(
                WakeStretchS2C message,
                Supplier<NetworkEvent.Context> contextSupplier
        ) {
            NetworkEvent.Context context =
                    contextSupplier.get();

            context.enqueueWork(
                    () -> DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () -> () ->
                                    ClientWakeStretchState.start(
                                            message.maidEntityId(),
                                            message.maidUuid(),
                                            message.settleTicks(),
                                            message.durationTicks()
                                    )
                    )
            );

            context.setPacketHandled(true);
        }
    }


}

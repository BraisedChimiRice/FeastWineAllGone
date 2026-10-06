package com.feastwineallgone.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Persistent per-maid data for FWAG drunk sleeping.
 *
 * The three player-recorded fallback locations and the active drunk-sleep
 * state live on the maid herself, so replacing / losing the toolbox does not
 * erase the setup.
 */
public final class DrunkSleepData {

    public static final int MAX_RECORDED_LOCATIONS = 3;

    private static final String ROOT_KEY =
            "FeastWineAllGoneDrunkSleep";

    private static final String LOCATIONS = "locations";
    private static final String ACTIVE = "active";

    private static final String DIMENSION = "dimension";
    private static final String POS = "pos";
    private static final String YAW = "yaw";
    private static final String START_GAME_TIME = "startGameTime";

    private DrunkSleepData() {
    }

    public static List<RecordedLocation> getRecordedLocations(
            EntityMaid maid
    ) {
        CompoundTag root = readRoot(maid);
        if (!root.contains(LOCATIONS, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = root.getList(
                LOCATIONS,
                Tag.TAG_COMPOUND
        );

        List<RecordedLocation> result = new ArrayList<>();

        for (int i = 0;
             i < list.size()
                     && result.size() < MAX_RECORDED_LOCATIONS;
             i++) {

            CompoundTag tag = list.getCompound(i);
            RecordedLocation location = readRecordedLocation(tag);

            if (location != null) {
                result.add(location);
            }
        }

        return Collections.unmodifiableList(result);
    }

    public static int getRecordedLocationCount(
            EntityMaid maid
    ) {
        return getRecordedLocations(maid).size();
    }

    public static AddLocationResult addRecordedLocation(
            EntityMaid maid,
            ResourceLocation dimension,
            BlockPos pos,
            float yaw
    ) {
        List<RecordedLocation> current =
                new ArrayList<>(getRecordedLocations(maid));

        for (int i = 0; i < current.size(); i++) {
            RecordedLocation old = current.get(i);

            if (old.dimension().equals(dimension)
                    && old.pos().equals(pos)) {

                /* Re-recording the same point updates its facing. */
                current.set(
                        i,
                        new RecordedLocation(
                                dimension,
                                pos.immutable(),
                                yaw
                        )
                );
                writeRecordedLocations(maid, current);
                return AddLocationResult.UPDATED_EXISTING;
            }
        }

        if (current.size() >= MAX_RECORDED_LOCATIONS) {
            return AddLocationResult.FULL;
        }

        current.add(
                new RecordedLocation(
                        dimension,
                        pos.immutable(),
                        yaw
                )
        );

        writeRecordedLocations(maid, current);
        return AddLocationResult.ADDED;
    }

    public static void clearRecordedLocations(
            EntityMaid maid
    ) {
        CompoundTag root = getOrCreateRoot(maid);
        root.remove(LOCATIONS);
        pruneRootIfEmpty(maid, root);
    }

    public static boolean isActive(
            EntityMaid maid
    ) {
        return getActiveSleep(maid).isPresent();
    }

    public static Optional<ActiveSleep> getActiveSleep(
            EntityMaid maid
    ) {
        CompoundTag root = readRoot(maid);

        if (!root.contains(ACTIVE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag active = root.getCompound(ACTIVE);

        ResourceLocation dimension =
                ResourceLocation.tryParse(
                        active.getString(DIMENSION)
                );

        if (dimension == null
                || !active.contains(POS, Tag.TAG_LONG)) {
            return Optional.empty();
        }

        return Optional.of(
                new ActiveSleep(
                        dimension,
                        BlockPos.of(active.getLong(POS)),
                        active.getFloat(YAW),
                        active.getLong(START_GAME_TIME)
                )
        );
    }

    public static void setActiveSleep(
            EntityMaid maid,
            ResourceLocation dimension,
            BlockPos pos,
            float yaw,
            long startGameTime
    ) {
        CompoundTag root = getOrCreateRoot(maid);
        CompoundTag active = new CompoundTag();

        active.putString(
                DIMENSION,
                dimension.toString()
        );
        active.putLong(
                POS,
                pos.asLong()
        );
        active.putFloat(
                YAW,
                yaw
        );
        active.putLong(
                START_GAME_TIME,
                startGameTime
        );

        root.put(ACTIVE, active);
    }

    public static void clearActiveSleep(
            EntityMaid maid
    ) {
        CompoundTag root = getOrCreateRoot(maid);
        root.remove(ACTIVE);
        pruneRootIfEmpty(maid, root);
    }

    private static RecordedLocation readRecordedLocation(
            CompoundTag tag
    ) {
        ResourceLocation dimension =
                ResourceLocation.tryParse(
                        tag.getString(DIMENSION)
                );

        if (dimension == null
                || !tag.contains(POS, Tag.TAG_LONG)) {
            return null;
        }

        return new RecordedLocation(
                dimension,
                BlockPos.of(tag.getLong(POS)),
                tag.getFloat(YAW)
        );
    }

    private static void writeRecordedLocations(
            EntityMaid maid,
            List<RecordedLocation> locations
    ) {
        CompoundTag root = getOrCreateRoot(maid);
        ListTag list = new ListTag();

        int limit = Math.min(
                locations.size(),
                MAX_RECORDED_LOCATIONS
        );

        for (int i = 0; i < limit; i++) {
            RecordedLocation location = locations.get(i);
            CompoundTag tag = new CompoundTag();

            tag.putString(
                    DIMENSION,
                    location.dimension().toString()
            );
            tag.putLong(
                    POS,
                    location.pos().asLong()
            );
            tag.putFloat(
                    YAW,
                    location.yaw()
            );

            list.add(tag);
        }

        if (list.isEmpty()) {
            root.remove(LOCATIONS);
        } else {
            root.put(LOCATIONS, list);
        }

        pruneRootIfEmpty(maid, root);
    }

    private static CompoundTag readRoot(
            EntityMaid maid
    ) {
        CompoundTag persistent = maid.getPersistentData();

        if (!persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return persistent.getCompound(ROOT_KEY);
    }

    private static CompoundTag getOrCreateRoot(
            EntityMaid maid
    ) {
        CompoundTag persistent = maid.getPersistentData();

        if (persistent.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
            return persistent.getCompound(ROOT_KEY);
        }

        CompoundTag root = new CompoundTag();
        persistent.put(ROOT_KEY, root);
        return root;
    }

    private static void pruneRootIfEmpty(
            EntityMaid maid,
            CompoundTag root
    ) {
        if (root.isEmpty()) {
            maid.getPersistentData().remove(ROOT_KEY);
        }
    }

    public enum AddLocationResult {
        ADDED,
        UPDATED_EXISTING,
        FULL
    }

    public record RecordedLocation(
            ResourceLocation dimension,
            BlockPos pos,
            float yaw
    ) {
    }

    public record ActiveSleep(
            ResourceLocation dimension,
            BlockPos pos,
            float yaw,
            long startGameTime
    ) {
    }
}

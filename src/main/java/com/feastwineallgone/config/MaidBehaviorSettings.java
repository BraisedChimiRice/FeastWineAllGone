package com.feastwineallgone.config;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.feastwineallgone.behavior.DrunkSleepData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-maid FWAG behaviour switches.
 *
 * Global numeric rules still live in {@link WgonConfig}.  This class only
 * answers whether one particular maid participates in each behaviour.
 * Missing data always means "enabled" so existing worlds keep their current
 * behaviour after updating.
 */
public final class MaidBehaviorSettings {

    private static final String ROOT_KEY =
            "FeastWineAllGoneBehaviorSettings";

    private static final String MASTER = "master";
    private static final String IDLE_FOOD = "idleFood";
    private static final String COUNTER_DRINK = "counterDrink";
    private static final String FRUIT_TASTING = "fruitTasting";
    private static final String SEATED_DINING = "seatedDining";
    private static final String NIGHT_STEAL = "nightSteal";
    private static final String FAVORITE_REACTION = "favoriteReaction";
    private static final String MEAL_REWARDS = "mealRewards";
    private static final String COOLDOWN_TIMER = "cooldownTimer";

    /*
     * Maid Restaurant owns these two TLM tasks completely while they are
     * active.  Treat them as externally managed workers rather than ordinary
     * lifestyle maids, otherwise FWAG can steal control from waiter/cook AI
     * (for example chair absorption during serving).
     *
     * Use raw task UIDs instead of importing Maid Restaurant classes so FWAG
     * keeps working when maid_restaurant is not installed.
     */
    private static final ResourceLocation MAID_RESTAURANT_COOK =
            new ResourceLocation("maid_restaurant", "cook");
    private static final ResourceLocation MAID_RESTAURANT_WAITER =
            new ResourceLocation("maid_restaurant", "waiter");

    private MaidBehaviorSettings() {
    }

    public static Snapshot snapshot(EntityMaid maid) {
        CompoundTag root = maid.getPersistentData();

        if (!root.contains(ROOT_KEY)) {
            return Snapshot.defaults();
        }

        CompoundTag tag = root.getCompound(ROOT_KEY);

        return new Snapshot(
                readEnabled(tag, MASTER),
                readEnabled(tag, IDLE_FOOD),
                readEnabled(tag, COUNTER_DRINK),
                readEnabled(tag, FRUIT_TASTING),
                readEnabled(tag, SEATED_DINING),
                readEnabled(tag, NIGHT_STEAL),
                readEnabled(tag, FAVORITE_REACTION),
                readEnabled(tag, MEAL_REWARDS),
                readEnabled(tag, COOLDOWN_TIMER)
        );
    }

    public static void apply(
            EntityMaid maid,
            Snapshot snapshot
    ) {
        CompoundTag root = maid.getPersistentData();

        /* Keep old worlds/data compact: an all-default profile needs no tag. */
        if (snapshot.isDefault()) {
            root.remove(ROOT_KEY);
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MASTER, snapshot.masterEnabled());
        tag.putBoolean(IDLE_FOOD, snapshot.idleFoodEnabled());
        tag.putBoolean(COUNTER_DRINK, snapshot.counterDrinkEnabled());
        tag.putBoolean(FRUIT_TASTING, snapshot.fruitTastingEnabled());
        tag.putBoolean(SEATED_DINING, snapshot.seatedDiningEnabled());
        tag.putBoolean(NIGHT_STEAL, snapshot.nightStealEnabled());
        tag.putBoolean(FAVORITE_REACTION, snapshot.favoriteReactionEnabled());
        tag.putBoolean(MEAL_REWARDS, snapshot.mealRewardsEnabled());
        tag.putBoolean(COOLDOWN_TIMER, snapshot.cooldownTimerEnabled());
        root.put(ROOT_KEY, tag);
    }

    public static void reset(EntityMaid maid) {
        maid.getPersistentData().remove(ROOT_KEY);
    }

    public static boolean isMasterEnabled(EntityMaid maid) {
        return !isExternallyManagedWorker(maid)
                && !DrunkSleepData.isActive(maid)
                && readSetting(maid, MASTER);
    }

    /**
     * Returns true when another mod currently owns this maid as a dedicated
     * worker role that must not be interrupted by FWAG lifestyle behaviours.
     *
     * This is intentionally task-based rather than UUID-based: if the player
     * later changes the same maid back to a normal/idle task, her saved FWAG
     * profile immediately becomes active again.
     */
    public static boolean isExternallyManagedWorker(EntityMaid maid) {
        if (maid == null || maid.getTask() == null) {
            return false;
        }

        ResourceLocation uid = maid.getTask().getUid();
        return MAID_RESTAURANT_COOK.equals(uid)
                || MAID_RESTAURANT_WAITER.equals(uid);
    }

    public static boolean isIdleFoodEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, IDLE_FOOD);
    }

    public static boolean isCounterDrinkEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, COUNTER_DRINK);
    }

    public static boolean isFruitTastingEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, FRUIT_TASTING);
    }

    public static boolean isSeatedDiningEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, SEATED_DINING);
    }

    public static boolean isNightStealEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, NIGHT_STEAL);
    }

    public static boolean isFavoriteReactionEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, FAVORITE_REACTION);
    }

    public static boolean isMealRewardsEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, MEAL_REWARDS);
    }

    public static boolean isCooldownTimerEnabled(EntityMaid maid) {
        return isBehaviorEnabled(maid, COOLDOWN_TIMER);
    }

    private static boolean isBehaviorEnabled(
            EntityMaid maid,
            String key
    ) {
        return isMasterEnabled(maid) && readSetting(maid, key);
    }

    private static boolean readSetting(
            EntityMaid maid,
            String key
    ) {
        CompoundTag root = maid.getPersistentData();
        if (!root.contains(ROOT_KEY)) {
            return true;
        }
        return readEnabled(root.getCompound(ROOT_KEY), key);
    }

    private static boolean readEnabled(
            CompoundTag tag,
            String key
    ) {
        return !tag.contains(key) || tag.getBoolean(key);
    }

    public record Snapshot(
            boolean masterEnabled,
            boolean idleFoodEnabled,
            boolean counterDrinkEnabled,
            boolean fruitTastingEnabled,
            boolean seatedDiningEnabled,
            boolean nightStealEnabled,
            boolean favoriteReactionEnabled,
            boolean mealRewardsEnabled,
            boolean cooldownTimerEnabled
    ) {
        public static Snapshot defaults() {
            return new Snapshot(
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        public boolean isDefault() {
            return equals(defaults());
        }
    }
}

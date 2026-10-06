package com.feastwineallgone.behavior;

import com.feastwineallgone.config.WgonConfig;
import com.feastwineallgone.config.MaidBehaviorSettings;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * FWAG formal seated-meal tracker.
 *
 * A meal records successful servings, never mere targeting/pickup attempts.
 * Runtime MealSession data is intentionally transient; lifetime/day/cooldown
 * progress is stored on the maid's persistent Forge data.
 */
public final class MealProgressManager {
    private static final int NORMAL_FAVORABILITY = 30;
    private static final int FAVORITE_FAVORABILITY = 50;

    /*
     * Formal-meal qualification is intentionally fixed. These values are
     * gameplay rules, not player-facing configuration:
     *   - at least 4 physical dishes/drinks
     *   - at least 2 physical food dishes
     *   - at most 10 physical dishes per meal
     *
     * A multi-bite cake/platter still counts as ONE dish. Repeated bites from
     * the same placed block/table slot never consume extra meal-capacity.
     */
    private static final int MEAL_MIN_DISHES = 4;
    private static final int MEAL_MIN_FOOD_DISHES = 2;
    private static final int MEAL_MAX_DISHES = 10;

    private static final String ROOT = "FeastWineAllGoneMeal";
    private static final String MEALS_TODAY = "MealsToday";
    private static final String LAST_DAY = "LastMealDay";
    private static final String REWARDED = "RewardedMealCount";
    private static final String COOLDOWN_UNTIL = "FoodCooldownUntil";
    private static final String BONUS_HEALTH = "BonusMaxHealth";
    private static final String BONUS_ARMOR = "BonusArmor";

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("e4a94e92-69a1-4bbf-8df4-7d598f591701");
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("7fbf30cb-3d65-4a52-9d8d-b4b929c0f2f7");

    private static final Map<UUID, MealSession> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> KNOWN_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_TIMER_BUBBLES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_TIMER_SECONDS = new HashMap<>();

    private MealProgressManager() {}

    public enum ServingType { FOOD, DRINK }

    /**
     * Called only after one serving/bite has actually been consumed.
     *
     * dishKey identifies the physical dish source (placed block position or
     * table slot). Multiple bites from the same source count as one dish.
     */
    public static void recordServing(ServerLevel level, EntityMaid maid, ResourceLocation foodId,
                                     ServingType type, boolean favorite, String dishKey) {
        if (foodId == null || maid == null || !maid.isAlive()) return;
        if (!MaidBehaviorSettings.isSeatedDiningEnabled(maid)) return;
        refreshDay(level, maid);
        if (isFoodCooldownActive(level, maid)) return;
        if (getData(maid).getInt(MEALS_TODAY) >= WgonConfig.MEAL_MAX_PER_DAY.get()) return;

        MealSession session = ACTIVE.computeIfAbsent(maid.getUUID(), id -> new MealSession());

        String effectiveDishKey = (dishKey == null || dishKey.isBlank())
                ? type.name() + ":" + foodId
                : dishKey;

        session.dishes.add(effectiveDishKey);
        if (type == ServingType.FOOD) {
            session.foodDishes.add(effectiveDishKey);
        }

        session.favorite |= favorite;
        session.lastEatGameTime = level.getGameTime();

        if (session.dishes.size() >= MEAL_MAX_DISHES) {
            finishMeal(level, maid, session);
            ACTIVE.remove(maid.getUUID());
        }
    }

    /**
     * Compatibility overload for any future caller that does not have a
     * physical source key. Current seated dining always uses the keyed form.
     */
    public static void recordServing(ServerLevel level, EntityMaid maid, ResourceLocation foodId,
                                     ServingType type, boolean favorite) {
        recordServing(level, maid, foodId, type, favorite, null);
    }

    public static void cancelForMaid(EntityMaid maid) {
        if (maid == null) return;
        ACTIVE.remove(maid.getUUID());
    }

    public static boolean isFoodCooldownActive(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return false;
        return isFoodCooldownActive(level, maid);
    }

    public static boolean isFoodCooldownActive(ServerLevel level, EntityMaid maid) {
        return getData(maid).getLong(COOLDOWN_UNTIL) > level.getGameTime();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level) || event.phase != TickEvent.Phase.END) return;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof EntityMaid maid) || !maid.isAlive()) continue;
            if (!MaidBehaviorSettings.isSeatedDiningEnabled(maid)) {
                cancelForMaid(maid);
            }
            refreshDay(level, maid);
            ensureGrowthModifiers(maid);
            tickCooldownState(level, maid);

            MealSession session = ACTIVE.get(maid.getUUID());
            if (session == null) continue;
            long timeout = WgonConfig.MEAL_END_TIMEOUT_SECONDS.get() * 20L;
            if (level.getGameTime() - session.lastEatGameTime >= timeout) {
                finishMeal(level, maid, session);
                ACTIVE.remove(maid.getUUID());
            }
        }
    }

    private static void finishMeal(ServerLevel level, EntityMaid maid, MealSession session) {
        boolean qualified = session.dishes.size() >= MEAL_MIN_DISHES
                && session.foodDishes.size() >= MEAL_MIN_FOOD_DISHES;
        if (!qualified) return;

        CompoundTag data = getData(maid);
        data.putInt(MEALS_TODAY, data.getInt(MEALS_TODAY) + 1);

        boolean rewarded = MaidBehaviorSettings.isMealRewardsEnabled(maid)
                && data.getInt(REWARDED) < WgonConfig.MEAL_MAX_REWARDED.get();
        int favorability = session.favorite ? FAVORITE_FAVORABILITY : NORMAL_FAVORABILITY;
        double health = session.favorite ? WgonConfig.FAVORITE_MEAL_HEALTH.get() : WgonConfig.NORMAL_MEAL_HEALTH.get();
        double armor = session.favorite ? WgonConfig.FAVORITE_MEAL_ARMOR.get() : WgonConfig.NORMAL_MEAL_ARMOR.get();

        if (rewarded) {
            applyGrowth(maid, health, armor);
            maid.getFavorabilityManager().add(favorability);
            data.putInt(REWARDED, data.getInt(REWARDED) + 1);
            sendOwnerRewardMessage(level, maid, favorability, armor, health);
        } else {
            sendOwnerMessage(level, maid, "message.feastwineallgone.meal.enjoyed_no_growth");
        }

        playPositiveFeedback(level, maid, session.favorite);

        long cooldownTicks = WgonConfig.MEAL_COOLDOWN_MINUTES.get() * 60L * 20L;
        if (cooldownTicks > 0L) {
            long until = level.getGameTime() + cooldownTicks;
            data.putLong(COOLDOWN_UNTIL, until);
            KNOWN_COOLDOWNS.put(maid.getUUID(), until);
            sendOwnerMessage(level, maid, "message.feastwineallgone.meal.full");
            updateCooldownTimerBubble(level, maid, until);
        } else {
            clearCooldownTimerBubble(maid);
        }
    }

    private static void applyGrowth(EntityMaid maid, double health, double armor) {
        CompoundTag data = getData(maid);
        data.putDouble(BONUS_HEALTH, data.getDouble(BONUS_HEALTH) + health);
        data.putDouble(BONUS_ARMOR, data.getDouble(BONUS_ARMOR) + armor);
        ensureGrowthModifiers(maid);
        if (health > 0.0D) maid.heal((float) health);
    }

    private static void ensureGrowthModifiers(EntityMaid maid) {
        CompoundTag data = getData(maid);
        syncModifier(maid.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID,
                "FWAG meal max health", data.getDouble(BONUS_HEALTH));
        syncModifier(maid.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID,
                "FWAG meal armor", data.getDouble(BONUS_ARMOR));
    }

    private static void syncModifier(AttributeInstance instance, UUID id, String name, double amount) {
        if (instance == null) return;
        AttributeModifier old = instance.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - amount) < 0.000001D) return;
        if (old != null) instance.removeModifier(id);
        if (amount != 0.0D) {
            instance.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void playPositiveFeedback(ServerLevel level, EntityMaid maid, boolean favorite) {
        /*
         * Formal-meal feedback deliberately does NOT use the begging/tail-wag
         * flag. Tail wagging belongs to FavoriteAttentionManager when the maid
         * actually notices a favorite food, so meal settlement cannot collide
         * with that reaction anymore.
         *
         * The remaining two feedback branches are equally likely and both use
         * server-synchronised vanilla/TLM paths:
         *   - ServerLevel#sendParticles -> tracking clients
         *   - TLM ChatBubbleManager -> SynchedEntityData
         */
        if (level.random.nextBoolean()) {
            level.sendParticles(ParticleTypes.HEART, maid.getX(), maid.getY() + 1.0D, maid.getZ(),
                    18, 0.45D, 0.55D, 0.45D, 0.08D);
        } else {
            maid.getChatBubbleManager().addTextChatBubble(favorite
                    ? "chatbubble.feastwineallgone.meal.favorite"
                    : "chatbubble.feastwineallgone.meal.rich");
        }
    }

    private static void tickCooldownState(ServerLevel level, EntityMaid maid) {
        CompoundTag data = getData(maid);
        long until = data.getLong(COOLDOWN_UNTIL);
        UUID id = maid.getUUID();

        if (until > level.getGameTime()) {
            KNOWN_COOLDOWNS.putIfAbsent(id, until);
            updateCooldownTimerBubble(level, maid, until);
            return;
        }

        clearCooldownTimerBubble(maid);

        Long known = KNOWN_COOLDOWNS.remove(id);
        if (known != null && known > 0L) {
            data.putLong(COOLDOWN_UNTIL, 0L);
            sendOwnerMessage(level, maid, "message.feastwineallgone.meal.hungry_again");
        }
    }

    private static void updateCooldownTimerBubble(ServerLevel level, EntityMaid maid, long until) {
        if (!WgonConfig.MEAL_COOLDOWN_TIMER_VISIBLE.get()
                || !MaidBehaviorSettings.isCooldownTimerEnabled(maid)) {
            clearCooldownTimerBubble(maid);
            return;
        }

        long remainingTicks = Math.max(0L, until - level.getGameTime());
        long remainingSeconds = (remainingTicks + 19L) / 20L;
        UUID id = maid.getUUID();

        Long previousSeconds = COOLDOWN_TIMER_SECONDS.get(id);
        Long previousBubble = COOLDOWN_TIMER_BUBBLES.get(id);
        if (previousSeconds != null && previousSeconds == remainingSeconds) {
            if (previousBubble == null
                    || maid.getChatBubbleManager().getChatBubble(previousBubble) != null) {
                return;
            }
        }

        if (previousBubble != null) {
            maid.getChatBubbleManager().removeChatBubble(previousBubble);
        }

        String time = formatCooldownTime(remainingSeconds);
        TextChatBubbleData timer = TextChatBubbleData.create(
                30,
                Component.translatable("chatbubble.feastwineallgone.meal.cooldown_timer", time),
                IChatBubbleData.TYPE_1,
                -100
        );
        long bubbleId = maid.getChatBubbleManager().addChatBubble(timer);
        COOLDOWN_TIMER_SECONDS.put(id, remainingSeconds);
        if (bubbleId >= 0L) {
            COOLDOWN_TIMER_BUBBLES.put(id, bubbleId);
        } else {
            /* TLM keeps at most five bubbles. If full, retry next second. */
            COOLDOWN_TIMER_BUBBLES.remove(id);
        }
    }

    private static void clearCooldownTimerBubble(EntityMaid maid) {
        UUID id = maid.getUUID();
        Long bubble = COOLDOWN_TIMER_BUBBLES.remove(id);
        COOLDOWN_TIMER_SECONDS.remove(id);
        if (bubble != null && maid.getChatBubbleManager().getChatBubble(bubble) != null) {
            maid.getChatBubbleManager().removeChatBubble(bubble);
        }
    }

    private static String formatCooldownTime(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static void refreshDay(ServerLevel level, EntityMaid maid) {
        CompoundTag data = getData(maid);
        long day = level.getDayTime() / 24000L;
        if (!data.contains(LAST_DAY)) {
            data.putLong(LAST_DAY, day);
        } else if (data.getLong(LAST_DAY) != day) {
            data.putLong(LAST_DAY, day);
            data.putInt(MEALS_TODAY, 0);
        }
    }

    private static void sendOwnerMessage(ServerLevel level, EntityMaid maid, String key) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) owner.sendSystemMessage(Component.translatable(key));
    }

    private static void sendOwnerRewardMessage(ServerLevel level, EntityMaid maid,
                                               int favorability, double armor, double health) {
        UUID ownerId = maid.getOwnerUUID();
        if (ownerId == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;

        owner.sendSystemMessage(Component.translatable(
                "message.feastwineallgone.meal.reward",
                favorability,
                formatRewardNumber(armor),
                formatRewardNumber(health)
        ));
    }

    private static String formatRewardNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static CompoundTag getData(EntityMaid maid) {
        CompoundTag forge = maid.getPersistentData();
        if (!forge.contains(ROOT)) forge.put(ROOT, new CompoundTag());
        return forge.getCompound(ROOT);
    }

    private static final class MealSession {
        /** Every physical source counts once, even when it has many bites. */
        final Set<String> dishes = new HashSet<>();
        /** Food-only subset used by the formal-meal nutrition requirement. */
        final Set<String> foodDishes = new HashSet<>();
        boolean favorite;
        long lastEatGameTime;
    }
}

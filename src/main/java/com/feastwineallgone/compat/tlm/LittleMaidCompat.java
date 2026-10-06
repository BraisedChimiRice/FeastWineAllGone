package com.feastwineallgone.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.task.meal.MaidMealType;
import com.github.tartaricacid.touhoulittlemaid.entity.task.meal.MaidMealManager;

@LittleMaidExtension
public final class LittleMaidCompat
        implements ILittleMaid {

    public LittleMaidCompat() {
    }

    @Override
    public void addMaidMeal(
            MaidMealManager manager
    ) {
        manager.addMaidMeal(
                MaidMealType.WORK_MEAL,
                new TavernMaidMeal(
                        MaidMealType.WORK_MEAL
                )
        );

        manager.addMaidMeal(
                MaidMealType.HOME_MEAL,
                new TavernMaidMeal(
                        MaidMealType.HOME_MEAL
                )
        );

        manager.addMaidMeal(
                MaidMealType.HEAL_MEAL,
                new TavernMaidMeal(
                        MaidMealType.HEAL_MEAL
                )
        );
    }
}
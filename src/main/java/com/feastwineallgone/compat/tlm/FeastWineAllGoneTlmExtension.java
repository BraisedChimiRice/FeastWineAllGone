package com.feastwineallgone.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.feastwineallgone.client.DrunkSleepFloorAnimation;
import com.feastwineallgone.client.WineFoxWakeStretchAnimation;
import com.feastwineallgone.registry.ModItems;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Touhou Little Maid extension entrypoint.
 */
@LittleMaidExtension
public final class FeastWineAllGoneTlmExtension implements ILittleMaid {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(
                ModItems.MAID_TOOLBOX,
                new MaidToolboxBauble()
        );
    }

    @Override
    public void addHardcodeAnimation(
            HardcodedAnimationManger manager
    ) {
        /*
         * TLM invokes this extension hook from its client-side hardcoded
         * animation initialization. The implementation itself only touches
         * rendering/model state and therefore has no server-side runtime work.
         */
        manager.addMaidAnimation(
                new DrunkSleepFloorAnimation()
        );

        /*
         * Family-wide Wine Fox wake stretch.
         *
         * ICustomAnimation is invoked by TLM for both its classic model path
         * and Gecko/YSM-compatible model path, so one adapter covers the Wine
         * Fox family instead of hard-coding individual skins.
         */
        manager.addMaidAnimation(
                new WineFoxWakeStretchAnimation()
        );

        LOGGER.info(
                "[WGON WakeStretch] TLM hardcoded Wine Fox animation adapter registered"
        );
    }
}

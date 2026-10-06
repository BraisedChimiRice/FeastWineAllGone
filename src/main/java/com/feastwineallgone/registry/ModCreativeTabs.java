package com.feastwineallgone.registry;

import com.feastwineallgone.FeastWineAllGone;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** FWAG's own creative-mode item tab. */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    FeastWineAllGone.MOD_ID
            );

    public static final RegistryObject<CreativeModeTab> MAIN =
            REGISTER.register(
                    "main",
                    () -> CreativeModeTab.builder()
                            .title(
                                    Component.translatable(
                                            "itemGroup.feastwineallgone"
                                    )
                            )
                            .icon(
                                    () -> new ItemStack(
                                            ModItems.CREATIVE_TAB_ICON.get()
                                    )
                            )
                            .displayItems(
                                    (parameters, output) -> {
                                        /*
                                         * Player-facing FWAG items live here.
                                         * Presentation-only cups and the hidden
                                         * tab-icon item are deliberately omitted.
                                         */
                                        output.accept(
                                                ModItems.MAID_TOOLBOX.get()
                                        );
                                    }
                            )
                            .build()
            );

    private ModCreativeTabs() {
    }
}

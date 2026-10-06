package com.feastwineallgone.registry;

import com.feastwineallgone.FeastWineAllGone;
import com.feastwineallgone.item.MaidToolboxItem;
import com.feastwineallgone.item.WoodenCupItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, FeastWineAllGone.MOD_ID);

    /**
     * Empty visual mug used by the night-barrel presentation.
     *
     * Keep the historical registry id "wooden_cup" so worlds created by
     * earlier WGON builds do not gain an unnecessary missing mapping.
     */
    public static final RegistryObject<Item> WOODEN_CUP = REGISTER.register(
            "wooden_cup",
            () -> new WoodenCupItem(new Item.Properties().stacksTo(1))
    );

    /**
     * The same mug with the user's purple-wine liquid layer visible.
     * This is presentation-only and is never inserted into normal gameplay
     * inventories by WGON.
     */
    public static final RegistryObject<Item> WOODEN_CUP_WINE = REGISTER.register(
            "wooden_cup_wine",
            () -> new WoodenCupItem(new Item.Properties().stacksTo(1))
    );

    /**
     * Player-facing maid bauble used to open one maid's FWAG behaviour panel.
     */
    public static final RegistryObject<Item> MAID_TOOLBOX = REGISTER.register(
            "maid_toolbox",
            MaidToolboxItem::new
    );

    /**
     * Internal icon item used only by FWAG's creative-mode tab.
     *
     * It intentionally is NOT added to the tab contents. Minecraft creative
     * tabs require their icon to be an ItemStack, so a tiny hidden item lets
     * us use the dedicated Wine Fox + barrel artwork without changing the
     * Maid Toolbox's own texture/model.
     */
    public static final RegistryObject<Item> CREATIVE_TAB_ICON = REGISTER.register(
            "creative_tab_icon",
            () -> new Item(new Item.Properties().stacksTo(1))
    );

    private ModItems() {}
}

package dev.autotrader.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Gives us access to the trade offer the player currently selected in the villager
 * trade screen, plus the vanilla "an offer was picked" routine
 * (setSelectionHint + tryMoveItems + ServerboundSelectTradePacket).
 */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {

    @Accessor("shopItem")
    int autotrader$getShopItem();

    @Accessor("shopItem")
    void autotrader$setShopItem(int index);

    @Invoker("postButtonClick")
    void autotrader$postButtonClick();
}

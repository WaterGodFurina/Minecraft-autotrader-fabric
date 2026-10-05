package dev.autotrader.mixin;

import dev.autotrader.VillagerTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes which merchant the player right-clicks, so the price refresh can reopen that same villager
 * later. See {@link VillagerTracker}. This only observes - the interaction itself is untouched.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    private void autotrader$rememberMerchant(Player player, Entity entity, EntityHitResult hit,
                                             InteractionHand hand,
                                             CallbackInfoReturnable<InteractionResult> cir) {
        if (entity instanceof Merchant) {
            VillagerTracker.remember(entity);
        }
    }
}

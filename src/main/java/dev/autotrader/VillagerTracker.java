package dev.autotrader;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Remembers the last merchant the player interacted with.
 *
 * <p>The client-side merchant menu is backed by a {@code ClientSideMerchant} that has no link back
 * to the villager entity, so when the price refresh wants to close and reopen the trade screen it
 * has no other way to find the villager again. This is pure bookkeeping - it never changes an
 * interaction, it only notes one that already happened.
 */
public final class VillagerTracker {
    private static Entity lastMerchant;

    private VillagerTracker() {
    }

    public static void remember(Entity entity) {
        if (entity != null) {
            lastMerchant = entity;
        }
    }

    /** The remembered merchant, or null if it is gone / in another level / no longer alive. */
    public static Entity get(Minecraft client) {
        if (lastMerchant == null || client == null || client.level == null) {
            return null;
        }
        if (lastMerchant.level() != client.level || !lastMerchant.isAlive()) {
            return null;
        }
        return lastMerchant;
    }
}

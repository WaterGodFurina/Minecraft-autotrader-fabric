package dev.autotrader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.EntityHitResult;

/**
 * "Auto-open the nearest villager" - a small convenience for the first seconds after joining.
 *
 * <p>For ten seconds after the player really gets into the world, the mod looks for the
 * <b>closest</b> villager it can actually reach and right-clicks it, so the trade screen comes
 * up on its own. That is handy on a phone, where walking up to a villager and tapping it
 * precisely is fiddly: join the server, wait a moment, and the trade screen is already there.</p>
 *
 * <p>Rules it sticks to:</p>
 * <ul>
 *   <li><b>Nearest</b>, not "all of them": the villager with the smallest distance wins.</li>
 *   <li><b>Reachable</b>: only a villager inside the player's entity interaction range is
 *       considered, because that is the same test vanilla uses - anything further would just
 *       be an interact packet the server drops. If only unreachable villagers are around, the
 *       search keeps waiting (one may walk closer) until the ten seconds are up.</li>
 *   <li><b>The ten seconds are "feet on the ground" seconds.</b> The window does not run down
 *       while a screen is open. That matters on a multiplayer server: right after joining, the
 *       client shows the level-loading screen while {@code player} and {@code level} are
 *       already set, and on a slow server that screen alone can easily outlast ten seconds.
 *       Counting it would waste the whole window before the player ever sees the world.</li>
 *   <li><b>Ten seconds, then done.</b> If no villager shows up in that window the search ends
 *       for this join - it never keeps poking around afterwards.</li>
 *   <li><b>Never fights the player.</b> If the trade screen is already open (auto-opened or by
 *       hand) the search stops straight away.</li>
 * </ul>
 *
 * <p>The switch is {@code autoOpenVillagerGui} in the config file, off by default.</p>
 */
public final class VillagerAutoOpen {

    /** how long the search lasts once the player is really in the world: 200 ticks = 10 seconds */
    private static final int WINDOW_TICKS = 200;
    /** between two interact attempts, so a villager that will not open is not spammed */
    private static final int RETRY_COOLDOWN_TICKS = 10;

    private static boolean searching;
    private static int elapsedTicks;
    private static int cooldown;
    /** so a villager that will not open is only announced once, not on every retry */
    private static boolean announced;

    private VillagerAutoOpen() {
    }

    /** Arms the search for one join. With the switch off, nothing at all happens. */
    public static void onJoin() {
        searching = AutoTraderClient.config().autoOpenVillagerGui;
        elapsedTicks = 0;
        cooldown = 0;
        announced = false;
    }

    /** Called once per client tick. */
    public static void tick(Minecraft client) {
        if (!searching) {
            return;
        }
        // Until the player, the level and the game mode are all there, there is nothing
        // sensible to click and the window has not started yet.
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        Screen screen = client.gui.screen();
        if (screen != null) {
            // the trade screen being open means the job is done
            if (screen instanceof MerchantScreen) {
                searching = false;
                AutoTraderClient.LOGGER.info("[autotrader] auto-open: villager trade screen is up");
            }
            // Any other screen means the world is not really in play yet (the join loading
            // screen) or the player is busy, so the window does not run down. This is what
            // keeps a slow multiplayer join from burning the whole window before the player
            // can even see the world.
            return;
        }
        if (elapsedTicks == 0) {
            AutoTraderClient.LOGGER.info(
                    "[autotrader] auto-open: world is up, the {}-tick window starts now", WINDOW_TICKS);
        }
        if (elapsedTicks++ >= WINDOW_TICKS) {
            searching = false; // ten seconds are up
            AutoTraderClient.LOGGER.info(announced
                    ? "[autotrader] auto-open: 10s up and the villager would not open - giving up"
                    : "[autotrader] auto-open: no reachable villager in 10s after joining - giving up");
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Entity nearest = nearestReachableVillager(client);
        if (nearest == null) {
            return;
        }
        if (!announced) {
            announced = true;
            AutoTraderClient.LOGGER.info(
                    "[autotrader] auto-open: nearest villager {} is {} block(s) away, opening its trade screen",
                    BuiltInRegistries.ENTITY_TYPE.getKey(nearest.getType()),
                    String.format("%.2f", Math.sqrt(client.player.distanceToSqr(nearest))));
        }
        client.gameMode.interact(client.player, nearest, new EntityHitResult(nearest),
                InteractionHand.MAIN_HAND);
        // the screen normally arrives within a tick or two; if it does not, look again shortly
        cooldown = RETRY_COOLDOWN_TICKS;
    }

    /**
     * The closest villager / wandering trader the player can reach right now, or {@code null}
     * when there is none. Reachability is the same test vanilla runs before it lets you
     * right-click an entity, so the mod never fires an interact the server will ignore.
     */
    private static Entity nearestReachableVillager(Minecraft client) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractVillager) || !entity.isAlive()) {
                continue;
            }
            if (!client.player.isWithinEntityInteractionRange(entity, 0.0)) {
                continue;
            }
            double distance = client.player.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }
}

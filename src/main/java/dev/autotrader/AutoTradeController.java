package dev.autotrader;

import dev.autotrader.config.AutoTraderConfig;
import dev.autotrader.config.TradeEntry;
import dev.autotrader.mixin.MerchantScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the trades.
 *
 * <p>An earlier version traded exactly one offer per cycle and slept {@code tradeIntervalMs}
 * in between; that is fine for "keep a trickle of trades going" but makes a bulk job crawl.
 * This now works the way the classic Easy Villager Trading / Villager Trading Plus family
 * does it: one pass keeps topping the two payment slots back up and taking the result slot
 * until the villager has nothing more to give, so a whole stack - or a whole inventory -
 * is traded in a single go.</p>
 *
 * <p>None of that is optional and none of it hangs off a key bind: bulk trading <i>is</i>
 * how this mod trades. The vanilla entry points used per trade are
 * {@code ServerboundSelectTradePacket} (server side this runs
 * {@code MerchantMenu.setSelectionHint + tryMoveItems}, i.e. the server refills the payment
 * slots from the authoritative inventory) and taking slot 2, whose
 * {@code MerchantResultSlot.onTake} performs the actual transaction.</p>
 *
 * <h2>Getting rid of the products - and why the cursor is never part of it</h2>
 * <p>The one thing this mod must never do is touch the mouse. Neither mode below uses
 * {@code PICKUP}: nothing is ever lifted onto the carried stack.</p>
 *
 * <p>Dropping is a setting again ({@code discardProducts}, default on):</p>
 * <ul>
 *   <li><b>on</b> - the result slot is {@code THROW}n out whole-stack. The {@code THROW}
 *       branch of {@code AbstractContainerMenu.doClick} needs an empty carried stack,
 *       removes the stack from the slot and lands it on the ground <i>directly</i>
 *       ({@code Slot.safeTake} → {@code Slot.remove} → {@code Slot.onTake} →
 *       {@code Player.drop}). The bag is also swept every tick: every stack whose item is
 *       the product of a configured trade is {@code THROW}n out too - exactly like the
 *       autodrop mod does it - so leftovers from earlier sessions do not pile up. An item
 *       that is still needed as <i>payment</i> by some configured trade is kept.</li>
 *   <li><b>off</b> - the result slot is shift-clicked ({@code QUICK_MOVE}). That would look
 *       like it only moves the goods, but {@code MerchantMenu.quickMoveStack} hands the moved
 *       stack to {@code Slot.onTake} on the way out, and {@code MerchantResultSlot.onTake} is
 *       the method that charges the payment and pays out - so the trade really happens and
 *       the product lands in the bag. Still no cursor.</li>
 * </ul>
 *
 * <p>The old "keep" mode took the product with {@code PICKUP} and put it down in a bag slot.
 * That is literally a mouse operation, and on a phone or tablet (where the cursor is a
 * virtual one the player also has to use by hand) it turned the trade loop into "the cursor
 * is busy grabbing things again" and made manual dropping next to impossible. It is gone.</p>
 *
 * <p>While the cursor is not empty, or while a mouse button is held down, the mod does
 * nothing at all - it will not race the player for the container.</p>
 */
public final class AutoTradeController {

    /** payment slots of {@link MerchantMenu} */
    private static final int SLOT_PAYMENT_1 = 0;
    private static final int SLOT_PAYMENT_2 = 1;
    private static final int SLOT_RESULT = 2;
    /** {@code THROW} with this button throws the whole stack, not a single item */
    private static final int THROW_WHOLE_STACK = 1;
    /**
     * Ceiling per client tick. A full inventory is not traded in one frame, but the rest
     * follows on the next tick - it still feels instant, and it keeps a single tick from
     * shoving thousands of packets down the connection at once.
     */
    private static final int MAX_TRADES_PER_TICK = 64;
    /** offers can unblock each other (freed inventory room), so sweep the list a few times */
    private static final int MAX_SWEEPS = 4;
    /**
     * How long the mod is willing to sit out a held mouse button. Long enough to cover any
     * drag a human does in an inventory, short enough that a touch client which leaves the
     * button "pressed" can never stall trading forever.
     */
    private static final int MOUSE_HOLD_GRACE_TICKS = 60;
    /** ticks with a non-empty cursor before the player is told why nothing is happening */
    private static final int CURSOR_BUSY_TICKS = 60;
    // --- price refresh: close + reopen the villager to shake off an inflated price ---
    /** how many times in a row we are willing to close/reopen to clear the same blockage */
    private static final int MAX_REFRESH_ATTEMPTS = 3;
    /** do not refresh again this soon after the previous refresh */
    private static final int REFRESH_COOLDOWN_TICKS = 40;
    /** give up waiting for the villager screen to come back after this many ticks */
    private static final int REFRESH_TIMEOUT_TICKS = 80;
    /**
     * A trade that would still go through is only worth interrupting once its visible price has
     * ballooned to at least this multiple of the base amount (13 -&gt; 26+). A trade that cannot
     * be assembled at all is refreshed regardless of how far the price has moved.
     */
    private static final int REFRESH_PRICE_FACTOR = 2;

    private static int cooldown;
    private static int mouseHoldTicks;
    private static int cursorBusyTicks;
    private static boolean cursorHintShown;
    private static String lastMissing = "";

    // --- price-refresh state (must survive the screen being closed for the reopen) ---
    private static boolean refreshPending;
    private static int refreshStartTick;
    private static int refreshAttempts;
    private static int lastRefreshTick;
    private static boolean refreshHintShown;
    /** price signature we already reopened the villager for, so a stationary peak does not reopen forever */
    private static int lastRefreshedPrice = Integer.MIN_VALUE;

    private AutoTradeController() {
    }

    public static void reset() {
        cooldown = 0;
        mouseHoldTicks = 0;
        cursorBusyTicks = 0;
        cursorHintShown = false;
        refreshAttempts = 0;
        refreshHintShown = false;
        lastRefreshedPrice = Integer.MIN_VALUE;
    }

    /**
     * Called once per client tick. Drains every wanted offer as far as it will go, with no
     * artificial pacing - the only thing that stops it is the villager or the inventory.
     */
    public static void tick(Minecraft client) {
        AutoTraderConfig config = AutoTraderClient.config();
        if (client.player == null || client.gameMode == null) {
            reset();
            refreshPending = false;
            return;
        }
        // A price refresh closes the screen and asks the server to reopen the same villager.
        // While we wait for that screen to come back there is no menu to act on, so this has
        // to run before the "not a merchant screen" early-out below.
        if (refreshPending) {
            tickRefresh(client);
            return;
        }
        if (!(client.gui.screen() instanceof MerchantScreen screen)) {
            reset();
            return;
        }
        if (!config.enabled || config.trades.isEmpty()) {
            reset();
            return;
        }

        MerchantMenu menu = screen.getMenu();

        // --- hands off the cursor, always ---
        // THROW needs an empty carried stack; and while the player has something on the
        // cursor they are the one using the "mouse", so the mod waits.
        if (!menu.getCarried().isEmpty()) {
            cursorBusyTicks++;
            if (cursorBusyTicks == CURSOR_BUSY_TICKS && !cursorHintShown) {
                cursorHintShown = true;
                AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.cursor_busy"));
            }
            return;
        }
        cursorBusyTicks = 0;
        cursorHintShown = false;

        // the player is clicking / drag-dropping right now: let them finish first
        if (playerIsClicking(client)) {
            return;
        }

        // the slots of this menu that are the player's own bag, hotbar included
        List<Integer> bag = playerInventorySlots(menu, client.player);

        // Discard mode: clean the bag before trading, and also while idling (the cooldown
        // below skips the trade pass but should not skip this - that is how leftovers get
        // cleared). In keep mode the products are the whole point, so nothing is swept.
        if (config.discardProducts) {
            sweepInventory(client, screen, menu, bag, config);
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int total = 0;
        for (int sweep = 0; sweep < MAX_SWEEPS && total < MAX_TRADES_PER_TICK; sweep++) {
            boolean traded = false;
            MerchantOffers offers = menu.getOffers();
            for (int i = 0; i < offers.size() && total < MAX_TRADES_PER_TICK; i++) {
                MerchantOffer offer = offers.get(i);
                if (!isWanted(offer, config)) {
                    continue;
                }
                int done = drain(client, screen, menu, bag, i, MAX_TRADES_PER_TICK - total);
                if (refreshPending) {
                    // drain deliberately closed the screen to re-read the price; bail out now
                    return;
                }
                if (done > 0) {
                    total += done;
                    traded = true;
                }
            }
            if (!traded) {
                break;
            }
        }

        if (!anyWantedPriceRaised(menu.getOffers(), config)) {
            // every wanted offer is back at its base price, so a later spike may refresh again.
            // Deliberately NOT reset on a successful trade: since a reopen does not lower a
            // demand-inflated price, resetting per trade would make the mod close/reopen the
            // villager over and over while the price stays high.
            refreshAttempts = 0;
            lastRefreshedPrice = Integer.MIN_VALUE;
        }

        if (total == 0) {
            // nothing this tick: back off a bit instead of hammering the villager every tick
            if (!anyWanted(menu.getOffers(), config)) {
                // the menu holds none of the trades on the list at all - worth saying out loud.
                // "matched but dry" (out of stock, no materials) stays quiet.
                warnMissing(config);
            }
            cooldown = config.intervalTicks();
        }
    }

    /**
     * Trades one offer over and over until it is exhausted.
     *
     * <p>Each round is: make sure the result slot is filled (which also tops the payment
     * slots up), then take the product out of it. Discard mode takes it with {@code THROW},
     * keep mode with {@code QUICK_MOVE} - either way {@code MerchantResultSlot.onTake} runs
     * and the trade is charged, and either way the cursor is untouched.</p>
     *
     * <p>Whether the round actually traded is decided by looking at the <i>payment slots</i>:
     * only {@code onTake} can shrink them, so a payment slot that did not change means
     * nothing was traded (out of stock, unaffordable, bag full in keep mode) and the offer is
     * done. Checking the result slot instead would be wrong: after every trade
     * {@code MerchantContainer.updateSellItem} refills it locally, so it is non-empty again
     * right away whenever the payment slots still hold enough for the next one.</p>
     *
     * @return how many transactions actually went through
     */
    private static int drain(Minecraft client, MerchantScreen screen, MerchantMenu menu,
                             List<Integer> bag, int index, int cap) {
        AutoTraderConfig config = AutoTraderClient.config();
        int done = 0;
        while (done < cap) {
            // the offers list is replaced whenever the villager resends it, so look it up every round
            MerchantOffers offers = menu.getOffers();
            if (index >= offers.size()) {
                break;
            }
            MerchantOffer offer = offers.get(index);
            if (offer.isOutOfStock() || !menu.getCarried().isEmpty()) {
                break;
            }
            if (!hasEnoughItems(menu, bag, offer)) {
                // Out of materials for this offer. Put any leftover payment back where it
                // belongs instead of letting it strand the input slot (see unstickPayment).
                unstickPayment(client, menu);
                break;
            }
            // never try to buy more than the villager still has in stock for this offer
            if (offer.getMaxUses() - offer.getUses() <= 0) {
                break;
            }

            // The price has climbed well past base. Close and reopen the villager: server side
            // Villager#stopTrading -> resetSpecialPrices drops the special-price adjustment, and
            // the reopen re-reads the authoritative offer list. Bounded by MAX_REFRESH_ATTEMPTS
            // and the "one attempt per price level" guard in shouldRefreshForPrice.
            if (shouldRefreshForPrice(offer, false)
                    && startPriceRefresh(client, offer)) {
                return done;
            }

            if (menu.getSlot(SLOT_RESULT).getItem().isEmpty()) {
                // the payment slots need topping up. postButtonClick sends the select-trade
                // packet, which makes the server run setSelectionHint + tryMoveItems on its own,
                // authoritative, container - while the client mirrors it locally.
                selectOffer(screen, index);
                if (menu.getSlot(SLOT_RESULT).getItem().isEmpty()) {
                    // The villager still will not offer this trade. The input slots may be held up
                    // by the leftover payment of a *previous* offer that tryMoveItems could not
                    // return to the bag (full bag), which stops it topping this one up. Items there
                    // that cannot pay for this offer are unusable here, so free them and retry.
                    if (clearForeignPayment(client, menu, offer)) {
                        selectOffer(screen, index);
                    }
                }
                if (menu.getSlot(SLOT_RESULT).getItem().isEmpty()) {
                    // Still nothing. Either genuinely unaffordable, or the client and server
                    // disagree about the price (the client offer list only refreshes when the
                    // server resends it). If the price is above base, close and reopen the
                    // villager to re-read the authoritative list.
                    if (shouldRefreshForPrice(offer, true)
                            && startPriceRefresh(client, offer)) {
                        return done;
                    }
                    // Genuinely cannot run this offer right now. Do not leave a partial payment
                    // stranded in the input slots - that is what jams the window.
                    unstickPayment(client, menu);
                    break;
                }
            }

            ItemStack[] before = paymentSnapshot(menu);
            if (config.discardProducts) {
                // throw the whole product out of the result slot: safeTake -> remove -> onTake
                // runs (the trade is paid for and the villager notified) and the stack lands on
                // the ground - the cursor is never touched
                click(client, menu, SLOT_RESULT, THROW_WHOLE_STACK, ContainerInput.THROW);
            } else {
                // shift-click it: quickMoveStack moves the product into the bag and then calls
                // onTake on the source slot, which runs the trade. Also cursor-free.
                click(client, menu, SLOT_RESULT, 0, ContainerInput.QUICK_MOVE);
            }
            if (!paymentConsumed(menu, before)) {
                // nothing was charged, so nothing was traded
                break;
            }
            done++;
        }
        return done;
    }

    /** The two payment slots, copied, so the trade can be spotted afterwards. */
    private static ItemStack[] paymentSnapshot(MerchantMenu menu) {
        return new ItemStack[]{
                menu.getSlot(SLOT_PAYMENT_1).getItem().copy(),
                menu.getSlot(SLOT_PAYMENT_2).getItem().copy()};
    }

    /** True when a trade went through: {@code MerchantResultSlot.onTake} shrinks the payment slots. */
    private static boolean paymentConsumed(MerchantMenu menu, ItemStack[] before) {
        return !ItemStack.matches(menu.getSlot(SLOT_PAYMENT_1).getItem(), before[0])
                || !ItemStack.matches(menu.getSlot(SLOT_PAYMENT_2).getItem(), before[1]);
    }

    /** Picks the offer in the villager screen and posts it, making the server top the payment slots up. */
    private static void selectOffer(MerchantScreen screen, int index) {
        ((MerchantScreenAccessor) screen).autotrader$setShopItem(index);
        ((MerchantScreenAccessor) screen).autotrader$postButtonClick();
    }

    /**
     * Throws every bag stack whose item is a configured trade product out of the window.
     *
     * <p>Uses {@code THROW} so it works from the slot itself - no cursor, no fake "clicked
     * outside" slot. Items that another configured trade still consumes as payment are left
     * alone (an emerald can be both a product to discard and the money for the next purchase).</p>
     *
     * @return how many stacks were thrown out (mostly informational / for logging)
     */
    private static int sweepInventory(Minecraft client, MerchantScreen screen, MerchantMenu menu,
                                      List<Integer> bag, AutoTraderConfig config) {
        Set<String> keep = protectedPaymentIds(screen, menu, config);
        int dropped = 0;
        for (int i : bag) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty() || !isDiscardableProduct(stack, menu, config, keep)) {
                continue;
            }
            AutoTraderClient.LOGGER.info("[autotrader] sweeping {}x {} out of slot {}",
                    stack.getCount(), itemId(stack), i);
            click(client, menu, i, THROW_WHOLE_STACK, ContainerInput.THROW);
            if (menu.getSlot(i).getItem().isEmpty()) {
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Item ids the sweep is never allowed to touch.
     *
     * <p>Two sources: every payment of a configured trade (an emerald can be a product to
     * discard <i>and</i> the money for the next purchase at the same time), and - as a belt
     * and braces - the payment of the offer that is selected in the villager screen right
     * now. That second source is what keeps the item the player is literally trading away
     * (say, the string in "20 string -&gt; 1 emerald") safe even if the trade list on disk is
     * stale, hand edited, or was added while the wrong offer was highlighted.</p>
     */
    private static Set<String> protectedPaymentIds(MerchantScreen screen, MerchantMenu menu,
                                                   AutoTraderConfig config) {
        Set<String> ids = new HashSet<>();
        for (TradeEntry entry : config.trades) {
            if (entry.costA != null && !entry.costA.isEmpty()) {
                ids.add(entry.costA);
            }
            if (entry.costB != null && !entry.costB.isEmpty()) {
                ids.add(entry.costB);
            }
        }
        MerchantOffers offers = menu.getOffers();
        // Live, authoritative source: every trade we are actually going to run in this menu.
        // Deriving the "do not touch" set from the offers in front of us - rather than only from
        // the on-disk list - is what stops the sweep from throwing away the very item the player
        // is trading with (e.g. the string in "20 string -> 1 emerald").
        for (int i = 0; i < offers.size(); i++) {
            if (isWanted(offers.get(i), config)) {
                addCosts(ids, offers.get(i));
            }
        }
        int selected = ((MerchantScreenAccessor) screen).autotrader$getShopItem();
        if (selected >= 0 && selected < offers.size()) {
            addCosts(ids, offers.get(selected));
        }
        return ids;
    }

    private static void addCosts(Set<String> ids, MerchantOffer offer) {
        String a = itemId(offer.getItemCostA().itemStack());
        if (!a.isEmpty()) {
            ids.add(a);
        }
        String b = offer.getItemCostB().map(cost -> itemId(cost.itemStack())).orElse("");
        if (!b.isEmpty()) {
            ids.add(b);
        }
    }

    /**
     * True for a bag stack that is the product of a trade we are actually about to run, and that
     * no live trade consumes as payment. The product item is read from the live offer list, so it
     * covers any recipe (paper-&gt;emerald, emerald-&gt;book, ...) without a single item id being
     * written into this class.
     */
    private static boolean isDiscardableProduct(ItemStack stack, MerchantMenu menu,
                                                AutoTraderConfig config, Set<String> keep) {
        String id = itemId(stack);
        if (id.isEmpty() || keep.contains(id)) {
            return false;
        }
        MerchantOffers offers = menu.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (isWanted(offer, config) && id.equals(itemId(offer.getResult()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The slots of this menu that hold the player's own bag, hotbar included.
     *
     * <p>Found by container identity instead of hard-coded indices - the same trick the
     * autodrop mod uses - so the sweep keeps hitting the right slots even if the villager
     * menu (payment / result / 27 bag / 9 hotbar) is ever laid out differently.</p>
     */
    private static List<Integer> playerInventorySlots(MerchantMenu menu, Player player) {
        Inventory inventory = player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).container == inventory) {
                slots.add(i);
            }
        }
        return slots;
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Can the two payment slots be satisfied from what is in the inventory right now? */
    private static boolean hasEnoughItems(MerchantMenu menu, List<Integer> bag, MerchantOffer offer) {
        if (!hasEnough(menu, bag, offer.getItemCostA())) {
            return false;
        }
        return offer.getItemCostB().map(cost -> hasEnough(menu, bag, cost)).orElse(true);
    }

    private static boolean hasEnough(MerchantMenu menu, List<Integer> bag, ItemCost cost) {
        int remaining = cost.count();
        // whatever is already sitting in the payment slots counts too
        remaining -= matching(menu.getSlot(SLOT_PAYMENT_1).getItem(), cost);
        remaining -= matching(menu.getSlot(SLOT_PAYMENT_2).getItem(), cost);
        if (remaining <= 0) {
            return true;
        }
        for (int i : bag) {
            remaining -= matching(menu.getSlot(i).getItem(), cost);
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static int matching(ItemStack stack, ItemCost cost) {
        return !stack.isEmpty() && cost.test(stack) ? stack.getCount() : 0;
    }

    /** True while the player is holding a mouse button, i.e. mid click / drag. */
    private static boolean playerIsClicking(Minecraft client) {
        if (client.mouseHandler != null && client.mouseHandler.isLeftPressed()) {
            if (mouseHoldTicks < MOUSE_HOLD_GRACE_TICKS) {
                mouseHoldTicks++;
                return true;
            }
            // the button looks stuck (some touch clients do that): do not stall the mod
            return false;
        }
        mouseHoldTicks = 0;
        return false;
    }

    private static boolean isWanted(MerchantOffer offer, AutoTraderConfig config) {
        for (TradeEntry entry : config.trades) {
            if (entry.matches(offer)) {
                return true;
            }
        }
        return false;
    }

    /** True when at least one offer in this menu is on the auto trade list. */
    private static boolean anyWanted(MerchantOffers offers, AutoTraderConfig config) {
        for (int i = 0; i < offers.size(); i++) {
            if (isWanted(offers.get(i), config)) {
                return true;
            }
        }
        return false;
    }

    private static void click(Minecraft client, MerchantMenu menu, int slot, int button, ContainerInput input) {
        if (client.gameMode == null || client.player == null) {
            return;
        }
        client.gameMode.handleContainerInput(menu.containerId, slot, button, input, client.player);
    }

    private static void warnMissing(AutoTraderConfig config) {
        StringBuilder sb = new StringBuilder();
        for (TradeEntry entry : config.trades) {
            sb.append(entry.describe().getString()).append("; ");
        }
        String signature = sb.toString();
        if (signature.equals(lastMissing)) {
            return;
        }
        lastMissing = signature;
        AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.no_offer_matched"));
    }

    /** Called from the "add trade" keybind while the villager screen is open. */
    public static void addSelectedTrade(MerchantScreen screen, Minecraft client) {
        AutoTraderConfig config = AutoTraderClient.config();
        MerchantMenu menu = screen.getMenu();
        MerchantOffers offers = menu.getOffers();
        int index = ((MerchantScreenAccessor) screen).autotrader$getShopItem();

        if (index < 0 || index >= offers.size()) {
            AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.no_selection"));
            return;
        }

        TradeEntry entry = TradeEntry.of(offers.get(index));
        if (entry.result.isEmpty()) {
            AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.no_selection"));
            return;
        }

        if (config.trades.removeIf(existing -> existing.sameAs(entry))) {
            config.save();
            AutoTraderClient.notifyPlayer(Component.translatable("autotrader.msg.removed", entry.describe()));
            return;
        }
        if (config.trades.size() >= 32) {
            AutoTraderClient.notifyPlayer(Component.translatable("autotrader.msg.list_full"));
            return;
        }
        config.trades.add(entry);
        config.save();
        AutoTraderClient.notifyPlayer(Component.translatable("autotrader.msg.added", entry.describe()));
    }

    // ------------------------------------------------------------------
    // input-slot unstick (trade not going through, input slot still holds goods)
    // ------------------------------------------------------------------

    /**
     * The input slots can be held up by the leftover payment of a <em>previous</em> offer that
     * {@code tryMoveItems} could not return to the bag (full bag), which stops it topping this
     * offer up. Items there that cannot pay for {@code offer} are unusable here, so throwing them
     * out frees the slot without giving up anything we could have spent on this trade.
     *
     * @return true if anything was thrown out (so the caller can retry the top-up)
     */
    private static boolean clearForeignPayment(Minecraft client, MerchantMenu menu, MerchantOffer offer) {
        boolean cleared = false;
        for (int slot : new int[]{SLOT_PAYMENT_1, SLOT_PAYMENT_2}) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty() || paysFor(stack, offer)) {
                continue;
            }
            click(client, menu, slot, THROW_WHOLE_STACK, ContainerInput.THROW);
            cleared = true;
        }
        return cleared;
    }

    /** True if {@code stack} is one of the items {@code offer} accepts as payment. */
    private static boolean paysFor(ItemStack stack, MerchantOffer offer) {
        if (offer.getItemCostA().test(stack)) {
            return true;
        }
        return offer.getItemCostB().map(cost -> cost.test(stack)).orElse(false);
    }

    /**
     * Empties the input slots once an offer really cannot be run.
     *
     * <p>Why this is needed: vanilla {@code MerchantMenu#tryMoveItems} first moves <b>both</b>
     * input slots back into the bag and only refills them if that worked. A leftover payment
     * (say 12 string for a 13-string trade) therefore produces a deadlock - if the bag cannot
     * take it (full inventory) {@code tryMoveItems} bails out before topping anything up, so the
     * trade never assembles and the leftover can neither be spent nor, by the mod, cleared. The
     * result is the "trade blocked, item stuck in the slot" the player sees.</p>
     *
     * <p>The leftover is real goods, so it is returned to the bag the ordinary way first
     * (shift-click). Only if the bag will not take it is it dropped - a few stranded items are
     * preferable to a jammed merchant window.</p>
     */
    private static void unstickPayment(Minecraft client, MerchantMenu menu) {
        for (int slot : new int[]{SLOT_PAYMENT_1, SLOT_PAYMENT_2}) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                continue;
            }
            click(client, menu, slot, 0, ContainerInput.QUICK_MOVE);
            ItemStack left = menu.getSlot(slot).getItem();
            if (!left.isEmpty()) {
                AutoTraderClient.LOGGER.info(
                        "[autotrader] input slot {} would not go back in the bag - dropping {}x {}",
                        slot, left.getCount(), itemId(left));
                click(client, menu, slot, THROW_WHOLE_STACK, ContainerInput.THROW);
            }
        }
    }

    // ------------------------------------------------------------------
    // price refresh (close + reopen the villager so the server resends its price list)
    // ------------------------------------------------------------------

    /**
     * Is the price the client is looking at above the villager's base price? {@code getCostA()}
     * carries the demand / special-price adjustment while {@code getBaseCostA()} is the untouched
     * amount, so a positive difference means the trade is dearer than normal right now. This is
     * item-agnostic and covers the optional second input as well.
     */
    private static boolean isPriceRaised(MerchantOffer offer) {
        if (offer.getCostA().getCount() > offer.getBaseCostA().getCount()) {
            return true;
        }
        ItemCost baseB = offer.getItemCostB().orElse(null);
        return baseB != null && offer.getCostB().getCount() > baseB.itemStack().getCount();
    }

    /** True when the visible price is at least {@link #REFRESH_PRICE_FACTOR}x the base amount. */
    private static boolean isPriceHeavilyRaised(MerchantOffer offer) {
        if (heavilyRaised(offer.getCostA().getCount(), offer.getBaseCostA().getCount())) {
            return true;
        }
        ItemCost baseB = offer.getItemCostB().orElse(null);
        return baseB != null && heavilyRaised(offer.getCostB().getCount(), baseB.itemStack().getCount());
    }

    private static boolean heavilyRaised(int current, int base) {
        return current >= Math.max(REFRESH_PRICE_FACTOR, base * REFRESH_PRICE_FACTOR);
    }

    /** True while at least one wanted offer in the open menu is priced above its base amount. */
    private static boolean anyWantedPriceRaised(MerchantOffers offers, AutoTraderConfig config) {
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            if (isWanted(offer, config) && isPriceRaised(offer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is a close/reopen worth it?
     *
     * <p>{@code blocked} says this offer could not be assembled <i>at all</i> (the result slot
     * stayed empty). That is exactly the case a reopen fixes on a server which drops the price
     * when the screen is closed, so it is refreshed whenever the visible price sits above base -
     * <i>regardless</i> of whether the bag could have paid the inflated amount (the whole point
     * is that it could not; requiring affordability here is what used to keep the villager stuck
     * at an unpayable price). Otherwise the trade would still go through, so it is only
     * interrupted once the price has really ballooned ({@link #REFRESH_PRICE_FACTOR}x base) and
     * only once per distinct price - a reopen that changes nothing must not become a
     * close/reopen loop.</p>
     *
     * <p>Gated by {@link AutoTraderConfig#refreshPriceOnRaise}: turning that option off
     * disables every reopen. On a server that does not drop the price when the screen closes
     * this feature simply does nothing (it stays bounded by {@link #MAX_REFRESH_ATTEMPTS}).</p>
     */
    private static boolean shouldRefreshForPrice(MerchantOffer offer, boolean blocked) {
        if (!AutoTraderClient.config().refreshPriceOnRaise) {
            return false;
        }
        if (refreshAttempts >= MAX_REFRESH_ATTEMPTS) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null
                && client.player.tickCount - lastRefreshTick < REFRESH_COOLDOWN_TICKS) {
            return false;
        }
        if (!isPriceRaised(offer)) {
            return false;
        }
        if (blocked) {
            return true;
        }
        return isPriceHeavilyRaised(offer) && priceSignature(offer) != lastRefreshedPrice;
    }

    /** Both input amounts, packed: "the same price" is recognised for the second input too. */
    private static int priceSignature(MerchantOffer offer) {
        return offer.getCostA().getCount() * 4096 + offer.getCostB().getCount();
    }

    /**
     * Close the villager screen and immediately right-click the same villager again, so the server
     * resends its authoritative offer list - that resend is what "refreshes" the price. The
     * reopened screen arrives a tick or two later; {@link #tickRefresh} waits for it.
     */
    private static boolean startPriceRefresh(Minecraft client, MerchantOffer offer) {
        Entity villager = VillagerTracker.get(client);
        if (villager == null || client.player == null
                || !(client.gui.screen() instanceof MerchantScreen)) {
            return false;
        }
        refreshPending = true;
        refreshStartTick = client.player.tickCount;
        lastRefreshTick = client.player.tickCount;
        refreshAttempts++;
        lastRefreshedPrice = priceSignature(offer);
        AutoTraderClient.LOGGER.info(
                "[autotrader] price refresh #{}: visible {} / base {} (demand {}, stock {}/{}), reopening villager",
                refreshAttempts, offer.getCostA().getCount(), offer.getBaseCostA().getCount(),
                offer.getDemand(), offer.getUses(), offer.getMaxUses());
        // onClose does exactly what pressing Escape does: it closes the container (sending the
        // close packet) and clears the screen. The interact packet that follows makes the server
        // run Villager#startTrading / openMenu again, which resends the authoritative offers.
        Screen open = client.gui.screen();
        if (open != null) {
            open.onClose();
        }
        client.gameMode.interact(client.player, villager,
                new EntityHitResult(villager), InteractionHand.MAIN_HAND);
        if (!refreshHintShown) {
            refreshHintShown = true;
            AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.price_refresh"));
        }
        return true;
    }

    /** Wait for the screen to come back after a price refresh, or give up without hanging. */
    private static void tickRefresh(Minecraft client) {
        if (client.player == null || client.level == null) {
            refreshPending = false;
            return;
        }
        if (client.gui.screen() instanceof MerchantScreen) {
            refreshPending = false; // reopened - the normal tick takes over from here
            return;
        }
        if (client.player.tickCount - refreshStartTick > REFRESH_TIMEOUT_TICKS) {
            refreshPending = false; // could not reopen (moved out of range / villager gone)
            AutoTraderClient.notifyPlayerOverlay(Component.translatable("autotrader.msg.price_refresh_failed"));
        }
    }
}

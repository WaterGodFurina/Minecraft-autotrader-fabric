package dev.autotrader.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * One entry of the auto-trade list: "what I give" -> "what I want".
 * Item ids are stored as strings so the json stays readable / hand editable.
 */
public final class TradeEntry {

    public String costA = "";
    public int countA = 1;
    /** second payment item, empty when the offer only costs one item */
    public String costB = "";
    public int countB;
    public String result = "";
    public int resultCount = 1;

    public TradeEntry() {
    }

    public static TradeEntry of(MerchantOffer offer) {
        ItemStack a = offer.getItemCostA().itemStack();
        ItemStack b = offer.getItemCostB().map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
        ItemStack r = offer.getResult();

        TradeEntry entry = new TradeEntry();
        entry.costA = idOf(a);
        entry.countA = a.getCount();
        entry.costB = idOf(b);
        entry.countB = b.getCount();
        entry.result = idOf(r);
        entry.resultCount = r.getCount();
        return entry;
    }

    /** Matches on item types only - villager prices fluctuate, we still want to trade. */
    public boolean matches(MerchantOffer offer) {
        ItemStack a = offer.getItemCostA().itemStack();
        ItemStack b = offer.getItemCostB().map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
        ItemStack r = offer.getResult();

        if (!costA.equals(idOf(a)) || !result.equals(idOf(r))) {
            return false;
        }
        if (costB == null || costB.isEmpty()) {
            return b.isEmpty();
        }
        return costB.equals(idOf(b));
    }

    public boolean sameAs(TradeEntry other) {
        if (other == null) {
            return false;
        }
        return costA.equals(other.costA)
                && result.equals(other.result)
                && nz(costB).equals(nz(other.costB));
    }

    public Component describe() {
        MutableComponent text = Component.empty().append(label(costA, countA));
        if (costB != null && !costB.isEmpty()) {
            text.append(" + ").append(label(costB, countB));
        }
        return text.append(" → ").append(label(result, resultCount));
    }

    private static Component label(String itemId, int count) {
        Item item = itemId == null || itemId.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId)).orElse(null);
        Component name = item == null ? Component.literal(itemId == null ? "?" : itemId) : new ItemStack(item).getItemName();
        return Component.literal(count + "× ").append(name);
    }

    private static String idOf(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}

package dev.autotrader.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.autotrader.KeyBindings;
import dev.autotrader.ViewAngles;
import dev.autotrader.config.AutoTraderConfig;
import dev.autotrader.config.TradeEntry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-game config. Vanilla widgets only, so it keeps working across Minecraft releases.
 * Every option of the mod is editable here, hotkeys included.
 *
 * <p>There are more rows than fit on a normal screen, so the options live in a scrolling
 * viewport with a scrollbar on the right (mouse wheel and dragging both work) and the
 * title / Done button stay put.</p>
 */
public final class AutoTraderConfigScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 24;
    private static final int BTN_H = 20;
    private static final int TEXT_OFF = 8;
    private static final int MAX_TRADE_ROWS = 6;
    /** fixed chrome: title strip on top, Done button at the bottom */
    private static final int VIEW_TOP = 36;
    private static final int BOTTOM_BAR = 28;
    private static final int SCROLLBAR_W = 4;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    private static final int WARN = 0xFFFFA0A0;
    private static final int DISABLED = 0xFF808080;

    private final Screen parent;
    private final AutoTraderConfig config;
    /** always-on-screen text: the repair warning and the "press a key" hint */
    private final List<Label> labels = new ArrayList<>();
    /** text inside the scrolling viewport, in content coordinates */
    private final List<Label> contentLabels = new ArrayList<>();
    /** widgets inside the scrolling viewport, with the y they were laid out at */
    private final List<ScrollRow> content = new ArrayList<>();

    private record Label(Component text, int x, int y, int color) {
    }

    private record ScrollRow(AbstractWidget widget, int baseY) {
    }

    private EditBox intervalBox;
    private EditBox degreesBox;
    private EditBox driftYawBox;
    private EditBox driftPitchBox;
    private EditBox driftTicksBox;
    private KeyMapping capturing;
    /** row where the live "facing" readout is painted (content coordinates); -1 when not laid out */
    private int facingRowY = -1;

    private int scroll;
    private int maxScroll;
    private boolean draggingScrollbar;

    public AutoTraderConfigScreen(Screen parent, AutoTraderConfig config) {
        super(Component.translatable("autotrader.screen.title"));
        this.parent = parent;
        this.config = config;
    }

    private int left() {
        return this.width / 2 - PANEL_W / 2;
    }

    /** last pixel row that belongs to the scrolling viewport */
    private int viewBottom() {
        return this.height - BOTTOM_BAR;
    }

    @Override
    protected void init() {
        this.labels.clear();
        this.contentLabels.clear();
        this.content.clear();

        int left = left();
        int right = left + 190;
        int half = 185;
        int y = 4; // content coordinates: 0 is the top of the viewport

        // --- master switches ---
        content(Button.builder(
                        toggleText("autotrader.option.enabled", config.enabled),
                        button -> {
                            config.enabled = !config.enabled;
                            button.setMessage(toggleText("autotrader.option.enabled", config.enabled));
                            save();
                        })
                .bounds(left, VIEW_TOP + y, half, BTN_H).build(), y);
        content(Button.builder(
                        toggleText("autotrader.option.discard", config.discardProducts),
                        button -> {
                            config.discardProducts = !config.discardProducts;
                            button.setMessage(toggleText("autotrader.option.discard", config.discardProducts));
                            save();
                        })
                .bounds(right, VIEW_TOP + y, half, BTN_H).build(), y);
        y += ROW_H;

        // explains what the discard switch does now: both sides are cursor-free
        contentText(Component.translatable("autotrader.option.discard_hint"), left + 4, y, HINT);
        y += ROW_H;

        // --- close + reopen the villager when its price has been raised (server dependent) ---
        content(Button.builder(
                        toggleText("autotrader.option.price_refresh", config.refreshPriceOnRaise),
                        button -> {
                            config.refreshPriceOnRaise = !config.refreshPriceOnRaise;
                            button.setMessage(toggleText("autotrader.option.price_refresh", config.refreshPriceOnRaise));
                            save();
                        })
                .bounds(left, VIEW_TOP + y, half, BTN_H).build(), y);
        y += ROW_H;

        contentText(Component.translatable("autotrader.option.price_refresh_hint"), left + 4, y, HINT);
        y += ROW_H;

        // --- auto-open the nearest villager's trade screen right after joining ---
        content(Button.builder(
                        toggleText("autotrader.option.auto_open_gui", config.autoOpenVillagerGui),
                        button -> {
                            config.autoOpenVillagerGui = !config.autoOpenVillagerGui;
                            button.setMessage(toggleText("autotrader.option.auto_open_gui", config.autoOpenVillagerGui));
                            save();
                        })
                .bounds(left, VIEW_TOP + y, half, BTN_H).build(), y);
        y += ROW_H;

        contentText(Component.translatable("autotrader.option.auto_open_gui_hint"), left + 4, y, HINT);
        y += ROW_H;

        // --- how often the auto trader looks for work; a bulk pass itself is not paced ---
        contentText(Component.translatable("autotrader.option.interval"), left + 4, y, TEXT);
        intervalBox = content(new EditBox(this.font, right, VIEW_TOP + y, 90, BTN_H,
                Component.translatable("autotrader.option.interval")), y);
        intervalBox.setMaxLength(6);
        intervalBox.setValue(Integer.toString(config.tradeIntervalMs));
        intervalBox.setResponder(value -> {
            try {
                config.tradeIntervalMs = Math.clamp(Integer.parseInt(value.trim()), 0, 60_000);
            } catch (NumberFormatException ignored) {
                // keep the previous value while the box is mid-edit
            }
        });
        contentText(Component.translatable("autotrader.unit.ms"), right + 96, y, HINT);
        y += ROW_H;

        // --- rotate the view when the trade screen opens (relative nudge, applied instantly) ---
        content(Button.builder(
                        toggleText("autotrader.option.rotate", config.rotateViewOnOpen),
                        button -> {
                            config.rotateViewOnOpen = !config.rotateViewOnOpen;
                            button.setMessage(toggleText("autotrader.option.rotate", config.rotateViewOnOpen));
                            save();
                        })
                .bounds(left, VIEW_TOP + y, half, BTN_H).build(), y);
        degreesBox = content(new EditBox(this.font, right, VIEW_TOP + y, 90, BTN_H,
                Component.translatable("autotrader.option.degrees")), y);
        degreesBox.setMaxLength(7);
        degreesBox.setValue(ViewAngles.trim(config.viewYawDegrees));
        degreesBox.setResponder(value -> {
            try {
                config.viewYawDegrees = Math.clamp(Double.parseDouble(value.trim()), -180.0, 180.0);
            } catch (NumberFormatException ignored) {
                // mid-edit
            }
        });
        contentText(Component.translatable("autotrader.unit.degrees"), right + 96, y, HINT);
        y += ROW_H;

        // --- drift the view to a fixed angle when the trade screen opens ---
        content(Button.builder(
                        toggleText("autotrader.option.drift", config.driftViewOnOpen),
                        button -> {
                            config.driftViewOnOpen = !config.driftViewOnOpen;
                            button.setMessage(toggleText("autotrader.option.drift", config.driftViewOnOpen));
                            save();
                        })
                .bounds(left, VIEW_TOP + y, half, BTN_H).build(), y);
        driftYawBox = content(new EditBox(this.font, right, VIEW_TOP + y, 90, BTN_H,
                Component.translatable("autotrader.option.drift_yaw")), y);
        driftYawBox.setMaxLength(7);
        driftYawBox.setValue(ViewAngles.trim(config.driftViewYaw));
        driftYawBox.setResponder(value -> {
            try {
                config.driftViewYaw = Math.clamp(Double.parseDouble(value.trim()), -180.0, 180.0);
            } catch (NumberFormatException ignored) {
                // mid-edit
            }
        });
        contentText(Component.translatable("autotrader.unit.degrees"), right + 96, y, HINT);
        y += ROW_H;

        contentText(Component.translatable("autotrader.option.drift_pitch"), left + 4, y, TEXT);
        driftPitchBox = content(new EditBox(this.font, right, VIEW_TOP + y, 90, BTN_H,
                Component.translatable("autotrader.option.drift_pitch")), y);
        driftPitchBox.setMaxLength(7);
        driftPitchBox.setValue(ViewAngles.trim(config.driftViewPitch));
        driftPitchBox.setResponder(value -> {
            try {
                config.driftViewPitch = Math.clamp(Double.parseDouble(value.trim()), -90.0, 90.0);
            } catch (NumberFormatException ignored) {
                // mid-edit
            }
        });
        contentText(Component.translatable("autotrader.unit.degrees"), right + 96, y, HINT);
        y += ROW_H;

        contentText(Component.translatable("autotrader.option.drift_ticks"), left + 4, y, TEXT);
        driftTicksBox = content(new EditBox(this.font, right, VIEW_TOP + y, 90, BTN_H,
                Component.translatable("autotrader.option.drift_ticks")), y);
        driftTicksBox.setMaxLength(3);
        driftTicksBox.setValue(Integer.toString(config.driftViewTicks));
        driftTicksBox.setResponder(value -> {
            try {
                config.driftViewTicks = Math.clamp(Integer.parseInt(value.trim()), 0, 200);
            } catch (NumberFormatException ignored) {
                // mid-edit
            }
        });
        contentText(Component.translatable("autotrader.unit.ticks"), right + 96, y, HINT);
        y += ROW_H;

        // where the live facing readout goes: painted in extractRenderState so it follows the
        // boxes while they are being typed in
        this.facingRowY = y + TEXT_OFF;
        y += ROW_H;

        // --- hotkeys ---
        for (Map.Entry<String, KeyMapping> entry : KeyBindings.ALL.entrySet()) {
            KeyMapping mapping = entry.getValue();
            String labelKey = "autotrader.hotkey." + entry.getKey();
            contentText(Component.translatable(labelKey), left + 4, y, TEXT);
            content(Button.builder(boundKeyText(mapping), button -> {
                        capturing = mapping;
                        rebuildWidgets();
                    })
                    .bounds(right, VIEW_TOP + y, half, BTN_H).build(), y);
            y += ROW_H;
        }

        // --- auto trade list ---
        contentText(Component.translatable("autotrader.list.header", config.trades.size()), left + 4, y, TEXT);
        content(Button.builder(Component.translatable("autotrader.list.clear"), button -> {
                    config.trades.clear();
                    save();
                    rebuildWidgets();
                })
                .bounds(right, VIEW_TOP + y, 90, BTN_H).build(), y);
        y += ROW_H;

        int tradeRows = Math.min(config.trades.size(), MAX_TRADE_ROWS);
        for (int i = 0; i < tradeRows; i++) {
            TradeEntry trade = config.trades.get(i);
            contentText(trade.describe(), left + 12, y, TEXT);
            final int index = i;
            content(Button.builder(Component.translatable("autotrader.list.remove"), button -> {
                        config.trades.remove(index);
                        save();
                        rebuildWidgets();
                    })
                    .bounds(right, VIEW_TOP + y, 90, BTN_H).build(), y);
            y += ROW_H;
        }
        if (config.trades.size() > tradeRows) {
            contentText(Component.translatable("autotrader.list.more", config.trades.size() - tradeRows),
                    left + 12, y, HINT);
            y += ROW_H;
        }

        // --- the one fixed control: leave the screen ---
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - half / 2, this.height - 22, half, BTN_H).build());

        // --- scroll geometry ---
        this.maxScroll = Math.max(0, y + 8 - (viewBottom() - VIEW_TOP));
        this.scroll = Math.clamp(this.scroll, 0, maxScroll);
        applyScroll();

        if (capturing != null) {
            Component hint = Component.translatable("autotrader.hotkey.capturing",
                    capturing.getTranslatedKeyMessage());
            labels.add(new Label(hint, (this.width - this.font.width(hint)) / 2, this.height - 40, WARN));
        }
        if (AutoTraderConfig.lastLoadWasBroken()) {
            Component warn = Component.translatable("autotrader.msg.config_repaired");
            labels.add(new Label(warn, (this.width - this.font.width(warn)) / 2, 24, WARN));
        }
    }

    private <T extends AbstractWidget> T content(T widget, int baseY) {
        content.add(new ScrollRow(widget, baseY));
        return addRenderableWidget(widget);
    }

    private void contentText(Component text, int x, int baseY, int color) {
        contentLabels.add(new Label(text, x, baseY + TEXT_OFF, color));
    }

    /** Pushes the stored layout through the scroll offset onto the live widgets. */
    private void applyScroll() {
        int bottom = viewBottom();
        for (ScrollRow row : content) {
            AbstractWidget widget = row.widget();
            int screenY = VIEW_TOP + row.baseY() - scroll;
            widget.setY(screenY);
            // hide whatever is fully outside the viewport so it cannot be clicked through
            widget.visible = screenY + widget.getHeight() > VIEW_TOP && screenY < bottom;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.text(this.font, this.getTitle(), this.width / 2 - this.font.width(this.getTitle()) / 2, 12, TEXT);

        int left = left();
        int bottom = viewBottom();
        extractor.enableScissor(left, VIEW_TOP, left + PANEL_W, bottom);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        for (Label label : contentLabels) {
            int screenY = VIEW_TOP + label.y() - scroll;
            if (screenY + 9 >= VIEW_TOP && screenY <= bottom) {
                extractor.text(this.font, label.text(), label.x(), screenY, label.color());
            }
        }
        if (facingRowY >= 0) {
            Component facing = Component.translatable("autotrader.option.drift_facing")
                    .append(": ")
                    .append(ViewAngles.describe(config.driftViewYaw, config.driftViewPitch));
            int screenY = VIEW_TOP + facingRowY - scroll;
            if (screenY + 9 >= VIEW_TOP && screenY <= bottom) {
                extractor.text(this.font, facing, left + 12, screenY,
                        config.driftViewOnOpen ? TEXT : DISABLED);
            }
        }
        extractor.disableScissor();

        for (Label label : labels) {
            extractor.text(this.font, label.text(), label.x(), label.y(), label.color());
        }
        drawScrollbar(extractor);
    }

    // --- scrolling ---

    private int scrollbarX() {
        return left() + PANEL_W - SCROLLBAR_W - 2;
    }

    private int thumbHeight(int track) {
        return Math.max(16, (int) ((long) track * track / (track + maxScroll)));
    }

    private void drawScrollbar(GuiGraphicsExtractor extractor) {
        if (maxScroll <= 0) {
            return;
        }
        int x = scrollbarX();
        int top = VIEW_TOP;
        int track = viewBottom() - top;
        extractor.fill(x, top, x + SCROLLBAR_W, top + track, 0x40FFFFFF);
        int thumbH = thumbHeight(track);
        int thumbY = top + (int) ((long) (track - thumbH) * scroll / maxScroll);
        extractor.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, draggingScrollbar ? 0xFFFFFFFF : 0xFFC0C0C0);
    }

    private void setScroll(int value) {
        this.scroll = Math.clamp(value, 0, maxScroll);
        applyScroll();
    }

    private void scrollToMouse(double mouseY) {
        int top = VIEW_TOP;
        int track = viewBottom() - top;
        int thumbH = thumbHeight(track);
        int span = Math.max(1, track - thumbH);
        double ratio = (mouseY - top - thumbH / 2.0) / span;
        setScroll((int) Math.round(ratio * maxScroll));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            setScroll(this.scroll - (int) Math.round(scrollY * ROW_H));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (maxScroll > 0 && event.button() == 0 && event.x() >= scrollbarX() - 2 && event.x() <= scrollbarX() + SCROLLBAR_W + 2
                && event.y() >= VIEW_TOP && event.y() <= viewBottom()) {
            draggingScrollbar = true;
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                capturing = null;
                rebuildWidgets();
                return true;
            }
            InputConstants.Key key = InputConstants.getKey(event);
            if (key != null && key != InputConstants.UNKNOWN) {
                capturing.setKey(key);
                if (this.minecraft != null && this.minecraft.options != null) {
                    this.minecraft.options.save();
                }
                capturing = null;
                config.captureHotkeysFromKeyMappings();
                save();
                rebuildWidgets();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        // runs whatever path the screen leaves through (Done, Esc, resize, a screen swap):
        // an edit can never be lost
        save();
        super.removed();
    }

    private void save() {
        config.captureHotkeysFromKeyMappings();
        config.save();
    }

    private static Component toggleText(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }

    private static Component boundKeyText(KeyMapping mapping) {
        return Component.literal(mapping.getTranslatedKeyMessage().getString());
    }
}

package com.pureblue.woad.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pureblue.woad.config.ConfigStore;
import com.pureblue.woad.core.Woad;
import com.pureblue.woad.core.Feature;
import com.pureblue.woad.gui.ButtonEditScreen;
import com.pureblue.woad.gui.HexPromptScreen;
import com.pureblue.woad.gui.InvButtonRenderer;
import com.pureblue.woad.gui.InventoryButtonsScreen;
import com.pureblue.woad.gui.InventoryGrid;
import com.pureblue.woad.mixin.HandledScreenAccessor;
import com.pureblue.woad.ui.Theme;
import com.pureblue.woad.ui.UiButton;
import com.pureblue.woad.ui.UiTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds clickable command buttons to the player inventory. Left-click runs the button's command,
 * right-click edits it. Buttons are placed on the inventory grid (anywhere in or around it, but not
 * over real slots) from this feature's editor. Each button can show an item or a label, and the
 * global style is either the vanilla button texture or a flat fill with a chosen border colour.
 */
public class InventoryButtonsFeature extends Feature {

    /** Global button look. */
    public enum Style { CUSTOM, VANILLA }

    /** One placed button: a grid cell, the command it runs, and its look (item / label). */
    public static final class CmdButton {
        public int col;
        public int row;
        public String command;
        public String item = "";   // item id shown inside, or ""
        public String label = "";  // text/number shown inside (if no item), or ""

        private transient ItemStack cachedStack;
        private transient String cachedItem;

        public CmdButton(int col, int row, String command) {
            this.col = col;
            this.row = row;
            this.command = command == null ? "" : command;
        }

        /** The resolved item stack for {@link #item} (cached), or EMPTY. */
        public ItemStack getStack() {
            if (item == null || item.isBlank()) return ItemStack.EMPTY;
            if (!item.equals(cachedItem)) {
                cachedItem = item;
                cachedStack = InvButtonRenderer.resolveItem(item);
            }
            return cachedStack == null ? ItemStack.EMPTY : cachedStack;
        }
    }

    private final List<CmdButton> buttons = new ArrayList<>();
    private Style style = Style.CUSTOM;
    private int borderColor = Theme.ACCENT & 0xFFFFFF;

    public InventoryButtonsFeature() {
        super("inv_buttons", "Inventory Buttons", "",
                true);
    }

    public List<CmdButton> getButtons() {
        return buttons;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
        ConfigStore.save();
    }

    public int getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(int rgb) {
        this.borderColor = rgb & 0xFFFFFF;
        ConfigStore.save();
    }

    public CmdButton buttonAt(int col, int row) {
        for (CmdButton b : buttons) {
            if (b.col == col && b.row == row) return b;
        }
        return null;
    }

    public void addButton(int col, int row) {
        if (buttonAt(col, row) == null) {
            buttons.add(new CmdButton(col, row, ""));
            ConfigStore.save();
        }
    }

    public void removeButton(CmdButton b) {
        buttons.remove(b);
        ConfigStore.save();
    }

    /** Opens the per-button editor (command + item + label). Used by the editor and the inventory. */
    public void editButton(Screen parent, CmdButton b) {
        Minecraft.getInstance().setScreen(new ButtonEditScreen(parent, this, b));
    }

    @Override
    public boolean hasConfigScreen() {
        return true;
    }

    @Override
    public Screen createConfigScreen(Screen parent) {
        return new InventoryButtonsScreen(parent, this);
    }

    // ---- Extra menu controls (style + border colour), next to the Open button ----------------

    private final UiButton styleButton = new UiButton("", UiButton.Variant.SECONDARY,
            () -> setStyle(style == Style.VANILLA ? Style.CUSTOM : Style.VANILLA));
    private final UiButton borderButton = new UiButton("Border color", UiButton.Variant.SECONDARY, () ->
            Minecraft.getInstance().setScreen(new HexPromptScreen(Minecraft.getInstance().screen,
                    String.format("#%06X", borderColor), this::setBorderColor)));

    @Override
    public void renderExtra(GuiGraphicsExtractor ctx, Screen parent, int left, int y, int right, int mouseX, int mouseY) {
        styleButton.label("Style: " + (style == Style.VANILLA ? "Vanilla" : "Custom"));
        styleButton.bounds(left, y, styleButton.preferredWidth(), Theme.BUTTON_H_SMALL).render(ctx, mouseX, mouseY);
        borderButton.swatch(borderColor & 0xFFFFFF);
        borderButton.bounds(left + styleButton.width() + 4, y, borderButton.preferredWidth(), Theme.BUTTON_H_SMALL)
                .render(ctx, mouseX, mouseY);
    }

    @Override
    public int extraHeight() {
        return Theme.BUTTON_H_SMALL;
    }

    @Override
    public boolean extraMouseClicked(Screen parent, double mx, double my, int button) {
        return styleButton.mouseClicked(mx, my, button) || borderButton.mouseClicked(mx, my, button);
    }

    // ---- Live inventory rendering + input -------------------------------------------------

    /** Draws the buttons over an open container GUI. */
    public void renderInInventory(AbstractContainerScreen<?> screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        int guiX = ((HandledScreenAccessor) screen).woad$getX();
        int guiY = ((HandledScreenAccessor) screen).woad$getY();
        Font tr = Minecraft.getInstance().font;

        String tooltip = null;
        for (CmdButton b : buttons) {
            int cx = InventoryGrid.cellX(guiX, b.col);
            int cy = InventoryGrid.cellY(guiY, b.row);
            boolean hover = mouseX >= cx && mouseX < cx + InvButtonRenderer.SIZE && mouseY >= cy && mouseY < cy + InvButtonRenderer.SIZE;
            InvButtonRenderer.draw(ctx, tr, cx, cy, hover, style, borderColor, b);
            if (hover) {
                tooltip = !b.command.isBlank() ? b.command : "Right-click to set a command";
            }
        }
        if (style == Style.VANILLA) {
            // The vanilla look was the player's choice, so its tooltip stays vanilla too.
            if (tooltip != null) {
                ctx.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(tooltip), mouseX, mouseY);
            }
        } else {
            if (tooltip != null) inventoryTooltip.offer(tooltip, List.of(tooltip));
            inventoryTooltip.render(ctx, mouseX, mouseY, screen.width, screen.height);
        }
    }

    /** Tooltip for the Woad-style buttons in the live inventory. */
    private final UiTooltip inventoryTooltip = new UiTooltip();

    /** Handles a click on an open container GUI; returns true if it hit a button (cancel vanilla). */
    public boolean onInventoryClick(AbstractContainerScreen<?> screen, MouseButtonEvent click) {
        if (!isEnabled()) return false;
        int guiX = ((HandledScreenAccessor) screen).woad$getX();
        int guiY = ((HandledScreenAccessor) screen).woad$getY();
        int mx = (int) click.x();
        int my = (int) click.y();

        for (CmdButton b : buttons) {
            int cx = InventoryGrid.cellX(guiX, b.col);
            int cy = InventoryGrid.cellY(guiY, b.row);
            if (mx >= cx && mx < cx + InvButtonRenderer.SIZE && my >= cy && my < cy + InvButtonRenderer.SIZE) {
                if (click.button() == 1 || b.command == null || b.command.isBlank()) {
                    editButton(screen, b);
                } else {
                    execute(b.command);
                }
                return true;
            }
        }
        return false;
    }

    private static void execute(String command) {
        ClientPacketListener nh = Minecraft.getInstance().getConnection();
        if (nh == null) return;
        String cmd = command.trim();
        if (cmd.startsWith("/")) {
            nh.sendCommand(cmd.substring(1));
        } else {
            nh.sendChat(cmd);
        }
    }

    // ---- Persistence ----------------------------------------------------------------------

    @Override
    public void writeConfig(JsonObject node) {
        node.addProperty("style", style.name());
        node.addProperty("borderColor", String.format("%06X", borderColor));
        JsonArray arr = new JsonArray();
        for (CmdButton b : buttons) {
            JsonObject o = new JsonObject();
            o.addProperty("col", b.col);
            o.addProperty("row", b.row);
            o.addProperty("command", b.command);
            o.addProperty("item", b.item);
            o.addProperty("label", b.label);
            arr.add(o);
        }
        node.add("buttons", arr);
    }

    @Override
    public void readConfig(JsonObject node) {
        if (node.has("style")) {
            try {
                style = Style.valueOf(node.get("style").getAsString());
            } catch (IllegalArgumentException ignored) {
                style = Style.CUSTOM;
            }
        }
        if (node.has("borderColor")) {
            try {
                borderColor = (int) (Long.parseLong(node.get("borderColor").getAsString(), 16) & 0xFFFFFF);
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (node.has("buttons") && node.get("buttons").isJsonArray()) {
            buttons.clear();
            for (var el : node.getAsJsonArray("buttons")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                CmdButton b = new CmdButton(
                        o.has("col") ? o.get("col").getAsInt() : 0,
                        o.has("row") ? o.get("row").getAsInt() : 0,
                        o.has("command") ? o.get("command").getAsString() : "");
                b.item = o.has("item") ? o.get("item").getAsString() : "";
                b.label = o.has("label") ? o.get("label").getAsString() : "";
                buttons.add(b);
            }
        }
    }
}

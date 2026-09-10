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
    private int borderColor = Woad.ACCENT & 0xFFFFFF;

    public InventoryButtonsFeature() {
        super("inv_buttons", "Inventory Buttons",
                "Clickable command buttons in your inventory. Left-click runs the command, "
                        + "right-click edits it. Place and style them with Open.",
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

    // ---- Extra menu controls (style + border colour), shown below the Open button ----------

    private final int[] styleRect = new int[4];
    private final int[] colorRect = new int[4];

    @Override
    public void renderExtra(GuiGraphicsExtractor ctx, Screen parent, int left, int y, int right, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        int w = right - left;

        String styleLabel = "Style: " + (style == Style.VANILLA ? "Vanilla" : "Custom");
        drawControl(ctx, tr, styleRect, left, y, w, styleLabel, mouseX, mouseY);

        int y2 = y + 20;
        int swatch = 16;
        drawControl(ctx, tr, colorRect, left, y2, w - swatch - 4, "Border color", mouseX, mouseY);
        ctx.fill(right - swatch, y2, right, y2 + 16, 0xFF000000 | borderColor);
        ctx.fill(right - swatch, y2, right, y2 + 1, 0xFF45454F);
        ctx.fill(right - swatch, y2 + 15, right, y2 + 16, 0xFF45454F);
        ctx.fill(right - swatch, y2, right - swatch + 1, y2 + 16, 0xFF45454F);
        ctx.fill(right - 1, y2, right, y2 + 16, 0xFF45454F);
    }

    @Override
    public int extraHeight() {
        return 40;
    }

    @Override
    public boolean extraMouseClicked(Screen parent, double mx, double my, int button) {
        if (button != 0) return false;
        if (inRect(styleRect, mx, my)) {
            setStyle(style == Style.VANILLA ? Style.CUSTOM : Style.VANILLA);
            return true;
        }
        if (inRect(colorRect, mx, my)) {
            Minecraft.getInstance().setScreen(new HexPromptScreen(parent,
                String.format("#%06X", borderColor), this::setBorderColor));
            return true;
        }
        return false;
    }

    private void drawControl(GuiGraphicsExtractor ctx, Font tr, int[] rect, int x, int y, int w, String label, int mouseX, int mouseY) {
        int h = 16;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ctx.fill(x, y, x + w, y + h, hovered ? 0xFF3A3A44 : 0xFF26262C);
        ctx.fill(x, y, x + w, y + 1, 0xFF45454F);
        ctx.fill(x, y + h - 1, x + w, y + h, 0xFF45454F);
        ctx.fill(x, y, x + 1, y + h, 0xFF45454F);
        ctx.fill(x + w - 1, y, x + w, y + h, 0xFF45454F);
        ctx.text(tr, label, x + 6, y + 4, 0xFFE8E8EC, false);
        rect[0] = x; rect[1] = y; rect[2] = x + w; rect[3] = y + h;
    }

    private static boolean inRect(int[] r, double mx, double my) {
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
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
        if (tooltip != null) {
            ctx.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(tooltip), mouseX, mouseY);
        }
    }

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

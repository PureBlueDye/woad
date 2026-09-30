package com.pureblue.woad.ui;

/**
 * Every visual constant of Woad's interface, in one place.
 *
 * <p>Colours are ARGB ints as Minecraft expects them. Sizes are in GUI pixels, so they scale with
 * the player's GUI scale like everything else in the game. Durations are in milliseconds and are
 * applied against real elapsed time, never against a frame count.
 *
 * <p>The palette was checked for contrast: body text stays above 4.5:1 on every surface it sits
 * on. {@link #ACCENT} is for edges, glows and indicators only — as text it falls to 4.3:1 on a
 * card, which is why buttons are filled with {@link #FILL} instead.
 */
public final class Theme {

    private Theme() {}

    // ---- Surfaces ------------------------------------------------------------------------------

    /** Screen background gradient, top-left to bottom-right. */
    public static final int BG_TOP = 0xFF0B1220;
    public static final int BG_BOTTOM = 0xFF111A2E;
    /** Dims the blurred world behind a screen, from the centre outwards. */
    public static final int SCRIM_CENTER = 0x4D0B1220;
    public static final int SCRIM_EDGE = 0xC70B1220;

    /** Glass panels: translucent so the blurred world shows through. */
    public static final int PANEL_TOP = 0xDB182440;
    public static final int PANEL_BOTTOM = 0xE60D1527;
    /** The faint light catching the top edge of a panel. */
    public static final int PANEL_HIGHLIGHT = 0x10FFFFFF;

    public static final int CARD = 0xFF16213A;
    /** Recessed surfaces: text fields, value buttons, slots. */
    public static final int INSET = 0xFF0E172B;
    public static final int INSET_HOVER = 0xFF111C33;
    public static final int HOVER = 0xFF1E2C4A;
    public static final int RAISE = 0xFF25365A;
    /** Sidebar strip behind the feature list. */
    public static final int SIDEBAR = 0x470D1426;

    public static final int LINE = 0x2194B0DC;
    public static final int LINE_STRONG = 0x3D94B0DC;
    public static final int LINE_HOVER = 0x5C94B0DC;

    // ---- Text ----------------------------------------------------------------------------------

    public static final int TEXT = 0xFFE6EDF7;
    public static final int TEXT_2 = 0xFF8A9BB8;
    public static final int TEXT_3 = 0xFF5D6E8C;
    public static final int TEXT_ON_FILL = 0xFFFFFFFF;

    // ---- Blues ---------------------------------------------------------------------------------

    /** Button fill: white text on it is 5.2:1. */
    public static final int FILL = 0xFF2563EB;
    public static final int FILL_HOVER = 0xFF2B6BEF;
    public static final int FILL_PRESS = 0xFF1D4ED8;
    public static final int FILL_DEEP = 0xFF1E40AF;
    /** Edges, glows, indicators. Never text. */
    public static final int ACCENT = 0xFF3B82F6;
    /** Accent text and the far end of gradients (7.5:1 on a card). */
    public static final int CYAN = 0xFF38BDF8;
    public static final int GLOW = 0x6B3B82F6;
    public static final int GLOW_SOFT = 0x293B82F6;
    /** Tint of a selected row. */
    public static final int SELECTED_FROM = 0x4D2563EB;
    public static final int SELECTED_TO = 0x142563EB;

    // ---- States --------------------------------------------------------------------------------

    public static final int OK = 0xFF22C55E;
    public static final int ERR = 0xFFEF4444;
    public static final int WARN = 0xFFF59E0B;
    public static final int OK_TINT = 0x1A22C55E;
    public static final int NEUTRAL_TINT = 0x1A8A9BB8;

    // ---- Controls ------------------------------------------------------------------------------

    public static final int SWITCH_OFF = 0xFF2A3752;
    public static final int SWITCH_DISABLED = 0xFF1D2740;
    public static final int KNOB = 0xFFF2F6FC;
    public static final int DISABLED_FILL = 0xFF172036;
    public static final int TRACK = 0xFF26324C;
    public static final int SHADOW = 0x8C030710;
    public static final int TOOLTIP_BG = 0xF7090F1C;
    public static final int TOOLTIP_EDGE = 0x733B82F6;
    /** In-game HUD pills: see-through enough not to hide the game. */
    public static final int HUD_BG = 0xBD090F1C;

    // ---- Shape (GUI pixels) --------------------------------------------------------------------

    public static final float RADIUS_PANEL = 7f;
    public static final float RADIUS_CARD = 5f;
    public static final float RADIUS_CONTROL = 4f;
    public static final float RADIUS_SLOT = 3f;

    public static final int PAD = 12;
    public static final int GAP = 6;
    public static final int BUTTON_H = 18;
    public static final int BUTTON_H_SMALL = 15;
    public static final int FIELD_H = 18;
    public static final int SWITCH_W = 21;
    public static final int SWITCH_H = 12;
    public static final int SCROLLBAR_W = 3;

    // ---- Motion (milliseconds) -----------------------------------------------------------------

    public static final int MS_PRESS = 80;
    public static final int MS_HOVER = 120;
    public static final int MS_FOCUS = 160;
    public static final int MS_TOGGLE = 180;
    public static final int MS_SLIDE = 180;
    public static final int MS_OPEN = 260;
    public static final int MS_TOOLTIP = 140;
    public static final int MS_TOOLTIP_DELAY = 350;
    /** Scroll catch-up half-life: how long the view takes to cover half the remaining distance. */
    public static final int MS_SCROLL_HALF_LIFE = 40;
    /** How far a screen slides up while it opens, in GUI pixels. */
    public static final float OPEN_SLIDE = 6f;
}

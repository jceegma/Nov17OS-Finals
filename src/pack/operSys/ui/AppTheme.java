package pack.operSys.ui;

import java.awt.Color;
import java.awt.Font;

/** All colors and fonts live here. Restyle the whole app by editing this one file. */
public final class AppTheme {
    private AppTheme() { }

    // ---- surfaces (dark, tinted toward the teal highlight) ----
    public static final Color BG           = new Color(0x0D1416);
    public static final Color NAV_BG       = new Color(0x111C1F);
    public static final Color CARD         = new Color(0x152024);
    public static final Color CARD_ALT     = new Color(0x1E2D32);
    public static final Color BORDER       = new Color(0x28393E);
    public static final Color SCROLL_THUMB = new Color(0x3A5156);

    // ---- text ----
    public static final Color TEXT  = new Color(0xE7EFEF);
    public static final Color MUTED = new Color(0x8DA3A6);

    // ---- highlight + buttons ----
    public static final Color ACCENT      = new Color(0x1C968C);   // main highlight (navbar, Start)
    public static final Color ACCENT_DARK = new Color(0x278F5A);   // secondary teal (Add Process)
    public static final Color DANGER      = new Color(0xB9434C);   // Remove

    // ---- metric tile colors (solid, no gradients) ----
    public static final Color TILE_WAIT = new Color(0x176F68);
    public static final Color TILE_TAT  = new Color(0x176F68);
    public static final Color TILE_CT   = new Color(0x176F68);
    public static final Color TILE_RT   = new Color(0x176F68);
    /** One color per process row (cycles if there are more than 8). */
    private static final Color[] PROCESS = {
            new Color(0xF2B84B), // P4: Sunbeam Gold

        new Color(0xFF7B6B), // P2: Coral Salmon
        new Color(0xB28DFF), // P3: Soft Lavender
        new Color(0x5CA4FF), // P1: Bright Sky Blue

        new Color(0xF472B6), // P6: Flamingo Pink
        new Color(0x67D4E8), // P7: Ice Cyan
        new Color(0x4DD4AC), // P5: Bright Mint

        new Color(0xA3D96C)  // P8: Fresh Lime
    };
    public static Color processColor(int index) {
        return PROCESS[index % PROCESS.length];
    }

    public static final Font TITLE = font(Font.BOLD, 17);

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }
}

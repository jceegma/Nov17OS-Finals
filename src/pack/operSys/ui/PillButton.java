package pack.operSys.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JButton;

/**
 * A rounded flat button (no gradients).
 *  - default look:            dark fill, muted text
 *  - setActive(true):         teal highlight (used for the selected algorithm / nav tab)
 *  - setSolidColor(color):    always that color with white text (Start, Add, Remove)
 *  - setTransparentWhenIdle:  no fill until hovered or active (used in the nav bar)
 */
public class PillButton extends JButton {

    private boolean active;
    private Color solid;
    private boolean transparentIdle;

    public PillButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(AppTheme.font(Font.BOLD, 13));
        setForeground(AppTheme.MUTED);
        setMargin(new Insets(8, 16, 8, 16));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setActive(boolean active) {
        this.active = active;
        setForeground(active || solid != null ? Color.WHITE : AppTheme.MUTED);
        repaint();
    }

    public boolean isActive() {
        return active;
    }

    public void setSolidColor(Color color) {
        this.solid = color;
        setForeground(color != null ? Color.WHITE : AppTheme.MUTED);
        repaint();
    }

    public void setTransparentWhenIdle(boolean transparentIdle) {
        this.transparentIdle = transparentIdle;
        repaint();
    }

    private static Color lighten(Color c, float amount) {
        int r = c.getRed() + Math.round((255 - c.getRed()) * amount);
        int g = c.getGreen() + Math.round((255 - c.getGreen()) * amount);
        int b = c.getBlue() + Math.round((255 - c.getBlue()) * amount);
        return new Color(r, g, b);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color fill;
        if (solid != null) {
            fill = solid;
        } else if (active) {
            fill = AppTheme.ACCENT;
        } else if (transparentIdle) {
            fill = null;
        } else {
            fill = AppTheme.CARD_ALT;
        }

        boolean hover = getModel().isRollover();
        if (fill == null) {
            if (hover) fill = AppTheme.CARD_ALT;
        } else if (hover) {
            fill = lighten(fill, 0.10f);
        }
        if (fill != null && getModel().isPressed()) {
            fill = fill.darker();
        }

        if (fill != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g2.dispose();
        }
        super.paintComponent(g);   // draws the text
    }
}

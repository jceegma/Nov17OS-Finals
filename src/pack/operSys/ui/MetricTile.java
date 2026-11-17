package pack.operSys.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

import javax.swing.JComponent;

/**
 * A dark rounded tile: small caption on top, big number below (same look as the
 * Banker's resource tiles, without the progress bar).
 * The 'color' argument is kept so existing code still compiles; it is not used for painting.
 */
public class MetricTile extends JComponent {

    private final String caption;
    private String value = "--";

    public MetricTile(String caption, Color color) {
        this.caption = caption;
        setPreferredSize(new Dimension(150, 82));
    }

    public void setValue(double v) {
        value = String.format(Locale.US, "%.2f", v);
        repaint();
    }

    /** Shows "--" (nothing calculated yet). */
    public void clear() {
        value = "--";
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);

        Font captionFont = new Font(Font.MONOSPACED, Font.BOLD, 11);
        Font valueFont = AppTheme.font(Font.BOLD, 28);
        FontMetrics cf = g2.getFontMetrics(captionFont);
        FontMetrics vf = g2.getFontMetrics(valueFont);

        final int gap = 8;
        int contentHeight = cf.getAscent() + gap + vf.getAscent();
        int top = (h - contentHeight) / 2;

        g2.setFont(captionFont);
        g2.setColor(AppTheme.MUTED);
        g2.drawString(caption, 14, top + cf.getAscent());

        g2.setFont(valueFont);
        g2.setColor(AppTheme.TEXT);
        g2.drawString(value, 14, top + cf.getAscent() + gap + vf.getAscent());
        g2.dispose();
    }
}
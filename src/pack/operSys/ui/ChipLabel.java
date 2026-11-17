package pack.operSys.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** A small rounded "chip" label: "work = [10, 5, 7]", "R0 10", the SAFE / UNSAFE badge... */
public class ChipLabel extends JLabel {

    private Color fill = AppTheme.CARD_ALT;
    private Color line = AppTheme.BORDER;

    public ChipLabel(String text) {
        super(text);
        setOpaque(false);
        setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        setForeground(AppTheme.MUTED);
        setBorder(BorderFactory.createEmptyBorder(5, 11, 5, 11));
    }

    /** Changes the chip colors (fill, text, outline). */
    public void setTone(Color fill, Color text, Color line) {
        this.fill = fill;
        this.line = line;
        setForeground(text);
        repaint();
    }

    /** The same color with a see-through alpha (0-255), for soft tinted backgrounds. */
    public static Color tint(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(line);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.dispose();
        super.paintComponent(g);
    }
}

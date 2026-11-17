package pack.operSys.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Thin rounded scroll bar with no arrow buttons. Use ModernScrollBarUI.style(scrollPane). */
public class ModernScrollBarUI extends BasicScrollBarUI {

    /** Applies the modern look to both scroll bars of a scroll pane (a missing bar is skipped). */
    public static void style(JScrollPane pane) {
        JScrollBar vertical = pane.getVerticalScrollBar();
        if (vertical != null) {
            vertical.setUI(new ModernScrollBarUI());
            vertical.setPreferredSize(new Dimension(10, 0));
            vertical.setBackground(AppTheme.CARD);
            vertical.setUnitIncrement(16);
        }

        JScrollBar horizontal = pane.getHorizontalScrollBar();
        if (horizontal != null) {
            horizontal.setUI(new ModernScrollBarUI());
            horizontal.setPreferredSize(new Dimension(0, 10));
            horizontal.setBackground(AppTheme.CARD);
            horizontal.setUnitIncrement(16);
        }
    }

    private static JButton zeroButton() {
        JButton b = new JButton();
        Dimension zero = new Dimension(0, 0);
        b.setPreferredSize(zero);
        b.setMinimumSize(zero);
        b.setMaximumSize(zero);
        return b;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zeroButton();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return scrollbar.getOrientation() == JScrollBar.VERTICAL
                ? new Dimension(10, 48) : new Dimension(48, 10);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(AppTheme.CARD);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color color = AppTheme.SCROLL_THUMB;
        if (isDragging) {
            color = AppTheme.ACCENT;
        } else if (isThumbRollover()) {
            color = AppTheme.MUTED;
        }
        g2.setColor(color);

        final int pad = 2;
        int w = r.width - 2 * pad;
        int h = r.height - 2 * pad;
        int arc = Math.min(w, h);
        g2.fillRoundRect(r.x + pad, r.y + pad, w, h, arc, arc);
        g2.dispose();
    }
}

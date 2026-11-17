package pack.operSys.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/** A dark, rounded drop-down (replaces the grey default JComboBox). */
public class ModernComboBox<E> extends JComboBox<E> {

    public ModernComboBox() {
        setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                return new ArrowButton();
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // the rounded background is painted by the combo box itself
            }

            @Override
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
                super.paintCurrentValue(g, bounds, false);
            }

            @Override
            protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox) {
                    @Override
                    protected JScrollPane createScroller() {
                        JScrollPane sp = super.createScroller();
                        ModernScrollBarUI.style(sp);
                        sp.setBorder(BorderFactory.createEmptyBorder());
                        return sp;
                    }
                };
                popup.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
                return popup;
            }
        });

        setOpaque(false);
        setFont(AppTheme.font(Font.BOLD, 13));
        setForeground(AppTheme.TEXT);
        setBorder(BorderFactory.createEmptyBorder());
        setPreferredSize(new Dimension(160, 34));
        setFocusable(false);

        setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, false);
                setFont(AppTheme.font(Font.BOLD, 13));
                setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
                if (index < 0) {                       // the closed box showing the current value
                    setOpaque(false);
                    setForeground(AppTheme.TEXT);
                } else {                               // a row in the pop-up list
                    setOpaque(true);
                    setBackground(selected ? AppTheme.ACCENT_DARK : AppTheme.CARD);
                    setForeground(selected ? Color.WHITE : AppTheme.TEXT);
                }
                return this;
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }

    /** The little down-pointing triangle. */
    private static class ArrowButton extends JButton {
        ArrowButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppTheme.MUTED);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            Polygon tri = new Polygon();
            tri.addPoint(cx - 5, cy - 2);
            tri.addPoint(cx + 5, cy - 2);
            tri.addPoint(cx, cy + 3);
            g2.fillPolygon(tri);
            g2.dispose();
        }
    }
}

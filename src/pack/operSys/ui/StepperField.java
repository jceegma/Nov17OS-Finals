package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * A modern replacement for JSpinner: rounded box, value in the middle, small up/down arrows.
 * Click an arrow (hold to repeat) or use the mouse wheel.
 * Use getValue() / setValue() / addChangeListener() just like a spinner.
 */
public class StepperField extends JPanel {

    private final int min;
    private final int max;
    private int value;
    private final JLabel valueLabel = new JLabel("", SwingConstants.CENTER);

    public StepperField(int value, int min, int max) {
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(max, value));

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 1));
        setPreferredSize(new Dimension(76, 30));

        valueLabel.setFont(AppTheme.font(Font.BOLD, 13));
        valueLabel.setForeground(AppTheme.TEXT);
        valueLabel.setText(String.valueOf(this.value));

        JPanel arrows = new JPanel(new GridLayout(2, 1, 0, 0));
        arrows.setOpaque(false);
        arrows.add(new ArrowButton(+1));
        arrows.add(new ArrowButton(-1));

        add(valueLabel, BorderLayout.CENTER);
        add(arrows, BorderLayout.EAST);

        addMouseWheelListener(e -> setValue(getValue() - e.getWheelRotation()));
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        int clamped = Math.max(min, Math.min(max, newValue));
        if (clamped == value) return;
        value = clamped;
        valueLabel.setText(String.valueOf(value));
        for (ChangeListener l : listenerList.getListeners(ChangeListener.class)) {
            l.stateChanged(new ChangeEvent(this));
        }
    }

    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
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

    /** One small triangle button. direction +1 = up (increase), -1 = down (decrease). */
    private class ArrowButton extends JButton {

        private final int direction;
        private boolean hover;

        ArrowButton(final int direction) {
            this.direction = direction;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setPreferredSize(new Dimension(22, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            final Timer repeat = new Timer(90, e -> setValue(getValue() + direction));
            repeat.setInitialDelay(400);

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    setValue(getValue() + direction);
                    repeat.start();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    repeat.stop();
                }
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    repeat.stop();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? AppTheme.ACCENT : AppTheme.MUTED);
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            Polygon tri = new Polygon();
            if (direction > 0) {
                tri.addPoint(cx - 4, cy + 2);
                tri.addPoint(cx + 4, cy + 2);
                tri.addPoint(cx, cy - 3);
            } else {
                tri.addPoint(cx - 4, cy - 2);
                tri.addPoint(cx + 4, cy - 2);
                tri.addPoint(cx, cy + 3);
            }
            g2.fillPolygon(tri);
            g2.dispose();
        }
    }
}

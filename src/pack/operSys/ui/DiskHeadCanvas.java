package pack.operSys.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import pack.operSys.model.DiskSeekStep;

/**
 * Interactive Disk Head Movement Chart:
 * - Top cylinder ruler (0 .. Max Track) with vertical guide lines
 * - Step-by-step zigzag path showing initial head, boundary reversals, and serviced tracks
 * - Hover/click highlighting synced with the Seek Sequence table
 */
public class DiskHeadCanvas extends JPanel {

    public interface StepListener {
        void onStepSelected(DiskSeekStep step, int index);
    }

    private static final int LEFT_MARGIN = 52;
    private static final int RIGHT_MARGIN = 28;
    private static final int TOP_RULER_H = 34;
    private static final int ROW_SPACING = 28;

    private List<DiskSeekStep> steps = new ArrayList<>();
    private int maxTrack = 199;
    private int selectedStepIndex = -1;
    private StepListener listener;

    public DiskHeadCanvas() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = nearestStepAt(e.getX(), e.getY());
                if (idx != selectedStepIndex) {
                    selectedStepIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = nearestStepAt(e.getX(), e.getY());
                if (idx >= 0) {
                    selectedStepIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });
    }

    public void setStepListener(StepListener listener) {
        this.listener = listener;
    }

    public void setData(List<DiskSeekStep> steps, int maxTrack) {
        this.steps = steps != null ? steps : new ArrayList<>();
        this.maxTrack = Math.max(1, maxTrack);
        this.selectedStepIndex = this.steps.size() > 1 ? this.steps.size() - 1 : -1;
        revalidate();
        repaint();
        notifyListener();
    }

    public void highlightStep(int index) {
        if (index >= 0 && index < steps.size()) {
            this.selectedStepIndex = index;
            repaint();
        }
    }

    private void notifyListener() {
        if (listener == null) return;
        if (selectedStepIndex >= 0 && selectedStepIndex < steps.size()) {
            listener.onStepSelected(steps.get(selectedStepIndex), selectedStepIndex);
        } else {
            listener.onStepSelected(null, -1);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = Math.max(6, steps.size());
        int h = TOP_RULER_H + rows * ROW_SPACING + 16;
        return new Dimension(580, Math.max(225, h));
    }

    private int trackToX(int track, int plotW) {
        double ratio = Math.max(0.0, Math.min(1.0, (double) track / maxTrack));
        return LEFT_MARGIN + (int) Math.round(ratio * plotW);
    }

    private int rowToY(int idx) {
        return TOP_RULER_H + 14 + idx * ROW_SPACING;
    }

    private int nearestStepAt(int mx, int my) {
        if (steps.isEmpty()) return -1;
        int plotW = Math.max(100, getWidth() - LEFT_MARGIN - RIGHT_MARGIN);
        int bestIdx = -1;
        double bestDist = 26.0;

        for (int i = 0; i < steps.size(); i++) {
            int nx = trackToX(steps.get(i).toTrack, plotW);
            int ny = rowToY(i);
            double dist = Point.distance(mx, my, nx, ny);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int plotW = Math.max(120, w - LEFT_MARGIN - RIGHT_MARGIN);

        if (steps.size() <= 1) {
            g2.setFont(AppTheme.font(Font.PLAIN, 13));
            g2.setColor(AppTheme.MUTED);
            String msg = "Add track requests to visualize the disk arm movement.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        // ---- 1. Top Cylinder Ruler (0 .. Max Track) ----
        int rulerY = 22;
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(LEFT_MARGIN - 8, 4, plotW + 16, 22, 8, 8);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(LEFT_MARGIN - 8, 4, plotW + 16, 22, 8, 8);

        int ticks = 5;
        g2.setFont(AppTheme.font(Font.BOLD, 10));
        for (int t = 0; t <= ticks; t++) {
            int trackVal = (int) Math.round((maxTrack * t) / (double) ticks);
            int tx = trackToX(trackVal, plotW);

            // Subtle vertical grid line down the chart
            g2.setColor(new Color(0x1B2B2E));
            g2.drawLine(tx, rulerY + 6, tx, h - 8);

            // Tick label on ruler
            g2.setColor(AppTheme.MUTED);
            String lbl = String.valueOf(trackVal);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(lbl, tx - fm.stringWidth(lbl) / 2, 19);
        }

        // ---- 2. Horizontal Step Swimlanes & Left Step Labels ----
        for (int i = 0; i < steps.size(); i++) {
            int y = rowToY(i);
            boolean isSelected = (i == selectedStepIndex);

            if (isSelected) {
                g2.setColor(new Color(0x16292D));
                g2.fillRoundRect(4, y - 12, w - 8, 24, 6, 6);
            }

            g2.setColor(new Color(0x1A292C));
            g2.drawLine(LEFT_MARGIN, y, LEFT_MARGIN + plotW, y);

            String seqTag = (i == 0) ? "Start" : "#" + i;
            g2.setFont(AppTheme.font(isSelected ? Font.BOLD : Font.PLAIN, 10));
            g2.setColor(isSelected ? Color.WHITE : AppTheme.MUTED);
            g2.drawString(seqTag, 10, y + 4);
        }

        // ---- 3. Connecting Seek Segments ----
        for (int i = 1; i < steps.size(); i++) {
            DiskSeekStep step = steps.get(i);
            int x1 = trackToX(step.fromTrack, plotW);
            int y1 = rowToY(i - 1);
            int x2 = trackToX(step.toTrack, plotW);
            int y2 = rowToY(i);

            boolean isSelected = (i == selectedStepIndex);
            Color segColor = isSelected ? Color.WHITE
                    : (step.isBoundary ? new Color(0xF2B84B) : AppTheme.ACCENT);

            if (step.isBoundary && !isSelected) {
                Stroke old = g2.getStroke();
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[] { 5f, 4f }, 0));
                g2.setColor(segColor);
                g2.drawLine(x1, y1, x2, y2);
                g2.setStroke(old);
            } else {
                g2.setStroke(new BasicStroke(isSelected ? 2.8f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(segColor);
                g2.drawLine(x1, y1, x2, y2);
                g2.setStroke(new BasicStroke(1f));
            }

            // Show seek distance pill on the active/selected segment
            if (isSelected && step.distance > 0) {
                int midX = (x1 + x2) / 2;
                int midY = (y1 + y2) / 2;
                String distText = "+" + step.distance;
                g2.setFont(AppTheme.font(Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int badgeW = fm.stringWidth(distText) + 12;
                int badgeH = 17;

                g2.setColor(AppTheme.ACCENT_DARK);
                g2.fillRoundRect(midX - badgeW / 2, midY - badgeH / 2, badgeW, badgeH, 8, 8);
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(midX - badgeW / 2, midY - badgeH / 2, badgeW - 1, badgeH - 1, 8, 8);
                g2.drawString(distText, midX - fm.stringWidth(distText) / 2, midY + 4);
            }
        }

        // ---- 4. Track Nodes & Numeric Labels ----
        for (int i = 0; i < steps.size(); i++) {
            DiskSeekStep step = steps.get(i);
            int nx = trackToX(step.toTrack, plotW);
            int ny = rowToY(i);
            boolean isSelected = (i == selectedStepIndex);

            Color nodeColor;
            if (i == 0) {
                nodeColor = new Color(0x34D399); // Initial Head (Emerald)
            } else if (step.isBoundary) {
                nodeColor = new Color(0xF2B84B); // Boundary turn/wrap (Amber)
            } else {
                nodeColor = new Color(0x5EEAD4); // Serviced request (Bright Teal)
            }

            if (isSelected) {
                g2.setColor(new Color(nodeColor.getRed(), nodeColor.getGreen(), nodeColor.getBlue(), 75));
                g2.fillOval(nx - 10, ny - 10, 20, 20);
            }

            int r = isSelected ? 6 : 4;
            g2.setColor(nodeColor);
            g2.fillOval(nx - r, ny - r, r * 2, r * 2);

            if (isSelected) {
                g2.setColor(Color.WHITE);
                g2.drawOval(nx - r, ny - r, r * 2, r * 2);
            }

            // Track number label positioned neatly above/beside the dot
            String label = String.valueOf(step.toTrack);
            g2.setFont(AppTheme.font(Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);

            int lx = nx - lw / 2;
            if (lx < LEFT_MARGIN - 4) lx = nx + 7;
            if (lx + lw > w - 6) lx = nx - lw - 7;
            int ly = ny - 7;

            g2.setColor(isSelected ? Color.WHITE : AppTheme.TEXT);
            g2.drawString(label, lx, ly);
        }

        g2.dispose();
    }
}
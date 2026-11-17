package pack.operSys.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import pack.operSys.logic.MultiprogrammingEngine;
import pack.operSys.model.MultiprogrammingEvent;

/**
 * Interactive visual timeline for Processor-Sharing Multiprogramming:
 * - Plots event nodes (Arrived/Started vs Terminated) along a horizontal time axis
 * - Draws stacked colored execution ribbons between events showing concurrent jobs
 *   and their shared CPU percentage, plus dashed IDLE intervals.
 */
public class MultiprogrammingTimelineCanvas extends JPanel {

    public interface EventInspectListener {
        void onEventInspected(MultiprogrammingEvent event);
    }

    private static final int PAD_X = 48;
    private static final int MIN_SEG_W = 94;
    private static final int MAX_SEG_W = 168;

    private List<MultiprogrammingEvent> events = new ArrayList<>();
    private Map<String, Color> colorByJob = new HashMap<>();
    private int[] nodeX = new int[0];
    private int selectedIndex = -1;
    private EventInspectListener listener;

    public MultiprogrammingTimelineCanvas() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = nearestNodeIndex(e.getX());
                if (idx != selectedIndex) {
                    selectedIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = nearestNodeIndex(e.getX());
                if (idx >= 0) {
                    selectedIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });
    }

    public void setEventInspectListener(EventInspectListener listener) {
        this.listener = listener;
    }

    public void setTimelineData(List<MultiprogrammingEvent> events, Map<String, Color> colorByJob) {
        this.events = events != null ? events : new ArrayList<>();
        this.colorByJob = colorByJob != null ? colorByJob : new HashMap<>();
        this.selectedIndex = this.events.isEmpty() ? -1 : this.events.size() - 1;
        computeNodePositions();
        revalidate();
        repaint();
        notifyListener();
    }

    private void notifyListener() {
        if (listener == null) return;
        if (selectedIndex >= 0 && selectedIndex < events.size()) {
            listener.onEventInspected(events.get(selectedIndex));
        } else {
            listener.onEventInspected(null);
        }
    }

    private void computeNodePositions() {
        int n = events.size();
        nodeX = new int[n];
        if (n == 0) return;

        nodeX[0] = PAD_X;
        for (int i = 0; i < n - 1; i++) {
            double dt = Math.max(0.0, events.get(i + 1).time - events.get(i).time);
            int segW = (int) Math.round(MIN_SEG_W + Math.min(dt, 6.0) * 12.0);
            segW = Math.max(MIN_SEG_W, Math.min(MAX_SEG_W, segW));
            nodeX[i + 1] = nodeX[i] + segW;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (nodeX.length == 0) {
            return new Dimension(520, 118);
        }
        int totalW = nodeX[nodeX.length - 1] + PAD_X;
        return new Dimension(Math.max(520, totalW), 118);
    }

    private int nearestNodeIndex(int mx) {
        if (nodeX.length == 0) return -1;
        int bestIdx = 0;
        int bestDist = Math.abs(mx - nodeX[0]);
        for (int i = 1; i < nodeX.length; i++) {
            int dist = Math.abs(mx - nodeX[i]);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestDist <= 65 ? bestIdx : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (events.isEmpty()) {
            g2.setFont(AppTheme.font(Font.PLAIN, 12));
            g2.setColor(AppTheme.MUTED);
            String msg = "Press Run to visualize the multiprogramming timeline.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2 + 4);
            g2.dispose();
            return;
        }

        int badgeY = 8;
        int badgeH = 20;
        int trackTop = 38;
        int trackH = 44;
        int axisY = trackTop + trackH + 10;
        int timeLabelY = axisY + 16;

        // 1. Draw concurrent execution ribbons between event[i] and event[i+1]
        for (int i = 0; i < events.size() - 1; i++) {
            int x1 = nodeX[i];
            int x2 = nodeX[i + 1];
            int segW = x2 - x1;
            MultiprogrammingEvent nextEv = events.get(i + 1);

            int boxX = x1 + 4;
            int boxW = segW - 8;
            boolean isFocusedSeg = (selectedIndex == i || selectedIndex == i + 1);

            if (nextEv.isIdleBefore || nextEv.jobNames.isEmpty()) {
                // Dashed IDLE block
                Stroke oldStroke = g2.getStroke();
                g2.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[] { 4f, 4f }, 0));
                g2.setColor(new Color(0x162427));
                g2.fillRoundRect(boxX, trackTop, boxW, trackH, 8, 8);
                g2.setColor(AppTheme.BORDER);
                g2.drawRoundRect(boxX, trackTop, boxW, trackH, 8, 8);
                g2.setStroke(oldStroke);

                g2.setFont(AppTheme.font(Font.BOLD, 10));
                g2.setColor(new Color(0x5B7175));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("IDLE", boxX + (boxW - fm.stringWidth("IDLE")) / 2,
                        trackTop + (trackH + fm.getAscent()) / 2 - 2);
            } else {
                // Active concurrent jobs stacked inside the interval
                String[] jobs = nextEv.jobNames.split(",\\s*");
                int count = jobs.length;
                int gap = 2;
                int barH = Math.max(8, (trackH - gap * (count - 1)) / count);
                String pctText = MultiprogrammingEngine.fmt(nextEv.cpuPercentPerJob) + "%";

                for (int j = 0; j < count; j++) {
                    String jid = jobs[j].trim();
                    Color jobCol = colorByJob.getOrDefault(jid, AppTheme.ACCENT);
                    int by = trackTop + j * (barH + gap);

                    g2.setColor(new Color(jobCol.getRed(), jobCol.getGreen(), jobCol.getBlue(),
                            isFocusedSeg ? 85 : 52));
                    g2.fillRoundRect(boxX, by, boxW, barH, 6, 6);

                    g2.setColor(isFocusedSeg ? Color.WHITE : jobCol);
                    g2.drawRoundRect(boxX, by, boxW - 1, barH - 1, 6, 6);

                    if (barH >= 13) {
                        g2.setFont(AppTheme.font(Font.BOLD, count == 1 ? 11 : 10));
                        g2.setColor(Color.WHITE);
                        String barText = (count <= 2) ? (jid + " · " + pctText) : jid;
                        FontMetrics fm = g2.getFontMetrics();
                        if (fm.stringWidth(barText) < boxW - 6) {
                            g2.drawString(barText,
                                    boxX + (boxW - fm.stringWidth(barText)) / 2,
                                    by + ((barH - fm.getHeight()) / 2) + fm.getAscent());
                        }
                    }
                }
            }
        }

        // 2. Draw horizontal time axis line
        int startAxisX = nodeX[0];
        int endAxisX = (nodeX.length > 1) ? nodeX[nodeX.length - 1] : nodeX[0] + 60;
        g2.setColor(AppTheme.BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(startAxisX, axisY, endAxisX, axisY);
        g2.setStroke(new BasicStroke(1f));

        // 3. Draw event badges, connector stems, nodes, and timestamps
        for (int i = 0; i < events.size(); i++) {
            MultiprogrammingEvent ev = events.get(i);
            int nx = nodeX[i];
            boolean isSelected = (i == selectedIndex);
            boolean isTerm = ev.isTermination;

            Color accent = isTerm ? new Color(0x5EEAD4) : new Color(0xF2B84B);
            String jobId = ev.event.split("\\s+")[0];
            String shortTag = jobId + (isTerm ? " Done" : " Start");

            // Vertical stem from badge to axis
            g2.setColor(isSelected ? Color.WHITE : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 130));
            g2.drawLine(nx, badgeY + badgeH, nx, axisY);

            // Top event pill badge
            g2.setFont(AppTheme.font(Font.BOLD, 10));
            FontMetrics fmTag = g2.getFontMetrics();
            int tagW = fmTag.stringWidth(shortTag) + 14;
            int tagX = nx - tagW / 2;

            g2.setColor(isSelected ? accent : AppTheme.CARD_ALT);
            g2.fillRoundRect(tagX, badgeY, tagW, badgeH, 10, 10);
            g2.setColor(isSelected ? Color.WHITE : accent);
            g2.drawRoundRect(tagX, badgeY, tagW - 1, badgeH - 1, 10, 10);

            g2.setColor(isSelected ? new Color(0x0D1416) : accent);
            g2.drawString(shortTag, tagX + 7, badgeY + 14);

            // Axis circle marker
            int r = isSelected ? 6 : 4;
            if (isSelected) {
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                g2.fillOval(nx - 9, axisY - 9, 18, 18);
            }
            g2.setColor(accent);
            g2.fillOval(nx - r, axisY - r, r * 2, r * 2);
            if (isSelected) {
                g2.setColor(Color.WHITE);
                g2.drawOval(nx - r, axisY - r, r * 2, r * 2);
            }

            // Timestamp label below axis
            String timeStr = MultiprogrammingEngine.fmt(ev.time);
            g2.setFont(AppTheme.font(isSelected ? Font.BOLD : Font.PLAIN, 11));
            g2.setColor(isSelected ? Color.WHITE : AppTheme.MUTED);
            FontMetrics fmTime = g2.getFontMetrics();
            g2.drawString(timeStr, nx - fmTime.stringWidth(timeStr) / 2, timeLabelY);
        }

        g2.dispose();
    }
}
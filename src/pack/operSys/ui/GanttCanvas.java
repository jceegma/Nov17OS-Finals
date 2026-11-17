package pack.operSys.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import pack.operSys.model.GanttBlock;

/** Custom-painted Gantt chart. No drag-and-drop component can do this, so we paint it ourselves. */
public class GanttCanvas extends JComponent {

    private List<GanttBlock> blocks = new ArrayList<>();
    private Map<String, Color> colors = new HashMap<>();
    private String emptyMessage = "Press Start to run the simulation.";

    public GanttCanvas() {
        setPreferredSize(new Dimension(400, 120));
        setMinimumSize(new Dimension(200, 120));
    }

    public void setBlocks(List<GanttBlock> blocks, Map<String, Color> colors) {
        this.blocks = blocks;
        this.colors = colors;
        repaint();
    }

    /** Text shown while there is nothing to draw. */
    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (blocks.isEmpty()) {
            g2.setColor(AppTheme.MUTED);
            g2.setFont(AppTheme.font(Font.PLAIN, 13));
            g2.drawString(emptyMessage, 4, 40);
            g2.dispose();
            return;
        }

        int total = 0;
        for (GanttBlock b : blocks) total = Math.max(total, b.end);
        double unit = (double) (getWidth() - 2) / total;

        final int barY = 4;
        final int barH = 72;
        Font nameFont = AppTheme.font(Font.BOLD, 13);
        Font smallFont = AppTheme.font(Font.PLAIN, 11);
        FontMetrics nameFm = g2.getFontMetrics(nameFont);
        FontMetrics smallFm = g2.getFontMetrics(smallFont);
        int lastLabelEnd = -100;

        for (GanttBlock b : blocks) {
            int x1 = (int) Math.round(b.start * unit);
            int x2 = (int) Math.round(b.end * unit);
            int w = Math.max(2, x2 - x1 - 3);

            g2.setColor(colors.getOrDefault(b.pid, AppTheme.ACCENT));
            g2.fillRoundRect(x1, barY, w, barH, 14, 14);

            g2.setColor(Color.WHITE);
            if (w > 26) {
                g2.setFont(nameFont);
                g2.drawString(b.pid, x1 + (w - nameFm.stringWidth(b.pid)) / 2, barY + barH / 2 - 2);
                String dur = (b.end - b.start) + "u";
                g2.setFont(smallFont);
                g2.drawString(dur, x1 + (w - smallFm.stringWidth(dur)) / 2, barY + barH / 2 + 14);
            }

            // start-time tick under the bar (skipped if it would overlap the previous label)
            String label = String.valueOf(b.start);
            if (x1 >= lastLabelEnd + 6) {
                g2.setColor(AppTheme.MUTED);
                g2.setFont(smallFont);
                g2.drawString(label, x1, barY + barH + 18);
                lastLabelEnd = x1 + smallFm.stringWidth(label);
            }
        }

        String endLabel = String.valueOf(total);
        int endX = getWidth() - smallFm.stringWidth(endLabel) - 2;
        if (endX >= lastLabelEnd + 6) {
            g2.setColor(AppTheme.MUTED);
            g2.setFont(smallFont);
            g2.drawString(endLabel, endX, barY + barH + 18);
        }
        g2.dispose();
    }
}

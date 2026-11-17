package pack.operSys.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;

import pack.operSys.model.BankersState;

/**
 * The big table: PROCESS | ALLOCATION | MAXIMUM | NEED, painted by hand.
 * A process that has "finished" in the safety run gets a teal edge and a numbered badge
 * showing its place in the safe sequence.
 */
public class BankersGrid extends JComponent implements Scrollable {

    private static final int HEADER_H = 66;
    private static final int ROW_H = 38;
    private static final int PROC_W = 124;

    private static final Color AMBER = new Color(0xE0B04F);
    private static final Color ALLOC = new Color(0x7FC4BC);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.BOLD, 13);

    private BankersState state;
    private int[] orderPos = new int[0];     // orderPos[p] = place in the safe sequence (0 = not yet)
    private int highlight = -1;              // row being "processed" right now

    public void setData(BankersState state, int[] orderPos, int highlight) {
        this.state = state;
        this.orderPos = orderPos;
        this.highlight = highlight;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int n = state == null ? 0 : state.processCount;
        return new Dimension(420, HEADER_H + n * ROW_H + 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (state == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        final int w = getWidth();
        final int n = state.processCount;
        final int m = state.resourceCount;
        final double colW = (w - PROC_W) / (double) (3 * m);

        Font headFont = AppTheme.font(Font.BOLD, 11);
        Font monoSmall = new Font(Font.MONOSPACED, Font.BOLD, 11);
        String[] groups = { "ALLOCATION", "MAXIMUM", "NEED" };

        // ---- header ----
        g2.setFont(headFont);
        g2.setColor(AppTheme.MUTED);
        g2.drawString("PROCESS", 16, HEADER_H / 2 + 4);

        for (int grp = 0; grp < 3; grp++) {
            int x0 = PROC_W + (int) Math.round(grp * m * colW);
            int x1 = PROC_W + (int) Math.round((grp + 1) * m * colW);

            g2.setFont(headFont);
            g2.setColor(grp == 2 ? AMBER : AppTheme.MUTED);
            drawCentered(g2, groups[grp], (x0 + x1) / 2, 20);

            g2.setFont(monoSmall);
            g2.setColor(AppTheme.MUTED);
            for (int r = 0; r < m; r++) {
                int cx = PROC_W + (int) Math.round((grp * m + r + 0.5) * colW);
                drawCentered(g2, "R" + r, cx, 49);
            }
            if (grp > 0) {                                   // vertical group separators
                g2.setColor(AppTheme.BORDER);
                g2.drawLine(x0, 0, x0, HEADER_H + n * ROW_H);
            }
        }
        g2.setColor(AppTheme.BORDER);
        g2.drawLine(PROC_W, 31, w, 31);                       // under the group titles
        g2.drawLine(0, HEADER_H - 1, w, HEADER_H - 1);        // under the header

        // ---- rows ----
        for (int p = 0; p < n; p++) {
            int y0 = HEADER_H + p * ROW_H;
            boolean done = p < orderPos.length && orderPos[p] > 0;

            if (p == highlight) {
                g2.setColor(ChipLabel.tint(AppTheme.ACCENT, 45));
                g2.fillRect(0, y0, w, ROW_H);
            }
            if (done) {
                g2.setColor(AppTheme.ACCENT);
                g2.fillRect(0, y0, 3, ROW_H - 1);
            }
            g2.setColor(AppTheme.BORDER);
            g2.drawLine(0, y0 + ROW_H - 1, w, y0 + ROW_H - 1);

            // process name + order badge
            g2.setFont(AppTheme.font(Font.BOLD, 13));
            g2.setColor(AppTheme.TEXT);
            String name = "P" + p;
            g2.drawString(name, 16, y0 + ROW_H / 2 + 5);
            if (done) {
                FontMetrics fm = g2.getFontMetrics();
                int bx = 16 + fm.stringWidth(name) + 8;
                int by = y0 + (ROW_H - 18) / 2 - 1;
                g2.setColor(AppTheme.ACCENT);
                g2.fillOval(bx, by, 18, 18);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
                drawCentered(g2, String.valueOf(orderPos[p]), bx + 9, by + 13);
            }

            // numbers
            g2.setFont(MONO);
            for (int r = 0; r < m; r++) {
                drawValue(g2, state.allocation[p][r], 0, r, m, colW, y0, ALLOC);
                drawValue(g2, state.max[p][r], 1, r, m, colW, y0, AppTheme.TEXT);
                drawValue(g2, state.need(p, r), 2, r, m, colW, y0, AMBER);
            }
        }
        g2.dispose();
    }

    private void drawValue(Graphics2D g2, int value, int group, int r, int m, double colW, int y0, Color color) {
        int cx = PROC_W + (int) Math.round((group * m + r + 0.5) * colW);
        g2.setColor(value == 0 ? ChipLabel.tint(AppTheme.MUTED, 150) : color);
        drawCentered(g2, String.valueOf(value), cx, y0 + ROW_H / 2 + 5);
    }

    private static void drawCentered(Graphics2D g2, String text, int cx, int baseline) {
        g2.drawString(text, cx - g2.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    // ---- Scrollable: follow the viewport width, scroll vertically ----
    @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(420, 230); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return ROW_H; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return ROW_H * 3; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }
}

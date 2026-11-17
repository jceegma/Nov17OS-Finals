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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import pack.operSys.model.PageReplacementStep;

/**
 * Renders the step-by-step Page Replacement grid:
 *   Top row:    Step (requested page numbers)
 *   Middle:     F0 .. Fn-1 physical frame contents per step
 *   Bottom row: Result (FAULT vs HIT badges)
 */
public class PageReplacementCanvas extends JPanel {

    public interface StepSelectionListener {
        void onStepInspected(PageReplacementStep step);
    }

    private static final int LABEL_COL_W = 74;
    private static final int MIN_STEP_COL_W = 54;
    private static final int HEADER_H = 38;
    private static final int RESULT_H = 38;

    private List<PageReplacementStep> steps = new ArrayList<>();
    private int frameCount = 4;
    private int activeStepIndex = -1;
    private StepSelectionListener listener;

    public PageReplacementCanvas() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = stepAtX(e.getX());
                if (idx != activeStepIndex) {
                    activeStepIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = stepAtX(e.getX());
                if (idx >= 0) {
                    activeStepIndex = idx;
                    notifyListener();
                    repaint();
                }
            }
        });
    }

    public void setStepSelectionListener(StepSelectionListener listener) {
        this.listener = listener;
    }

    public void setSimulationData(List<PageReplacementStep> steps, int frameCount) {
        this.steps = steps != null ? steps : new ArrayList<>();
        this.frameCount = Math.max(1, frameCount);
        this.activeStepIndex = this.steps.isEmpty() ? -1 : this.steps.size() - 1;
        revalidate();
        repaint();
        notifyListener();
    }

    private void notifyListener() {
        if (listener == null) return;
        if (activeStepIndex >= 0 && activeStepIndex < steps.size()) {
            listener.onStepInspected(steps.get(activeStepIndex));
        } else {
            listener.onStepInspected(null);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int cols = Math.max(8, steps.size());
        int w = LABEL_COL_W + (cols * MIN_STEP_COL_W) + 8;
        int h = HEADER_H + (frameCount * 42) + RESULT_H + 8;
        return new Dimension(w, Math.max(240, h));
    }

    private int getStepColumnWidth() {
        if (steps.isEmpty()) return MIN_STEP_COL_W;
        int avail = getWidth() - LABEL_COL_W - 6;
        return Math.max(MIN_STEP_COL_W, avail / steps.size());
    }

    private int stepAtX(int x) {
        if (steps.isEmpty() || x < LABEL_COL_W) return -1;
        int colW = getStepColumnWidth();
        int idx = (x - LABEL_COL_W) / colW;
        return (idx >= 0 && idx < steps.size()) ? idx : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (steps.isEmpty()) {
            g2.setFont(AppTheme.font(Font.PLAIN, 13));
            g2.setColor(AppTheme.MUTED);
            String emptyMsg = "Add pages to the Reference String to simulate.";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        int colW = getStepColumnWidth();
        int gridW = LABEL_COL_W + steps.size() * colW;

        int bodyH = Math.max(frameCount * 32, h - HEADER_H - RESULT_H - 6);
        int rowH = bodyH / frameCount;
        int totalH = HEADER_H + (rowH * frameCount) + RESULT_H;

        // Outer subtle table border
        g2.setColor(AppTheme.CARD);
        g2.fillRoundRect(0, 0, gridW, totalH, 10, 10);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(0, 0, gridW - 1, totalH - 1, 10, 10);

        // ---- 1. Header Row ("Step" + Reference String digits) ----
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(1, 1, gridW - 2, HEADER_H - 1, 9, 9);
        g2.fillRect(1, HEADER_H - 8, gridW - 2, 8);

        drawCenteredText(g2, "Step", 0, 0, LABEL_COL_W, HEADER_H,
                AppTheme.font(Font.BOLD, 12), AppTheme.MUTED);

        for (int c = 0; c < steps.size(); c++) {
            int cx = LABEL_COL_W + c * colW;
            PageReplacementStep step = steps.get(c);
            boolean isSelected = (c == activeStepIndex);

            if (isSelected) {
                g2.setColor(new Color(0x1C968C));
                g2.fillRect(cx + 2, 3, colW - 4, HEADER_H - 6);
            }

            drawCenteredText(g2, String.valueOf(step.page), cx, 0, colW, HEADER_H,
                    AppTheme.font(Font.BOLD, 14), isSelected ? Color.WHITE : AppTheme.TEXT);
        }

        // ---- 2. Physical Frame Rows (F0 .. Fn-1) ----
        for (int r = 0; r < frameCount; r++) {
            int ry = HEADER_H + r * rowH;

            // Horizontal divider
            g2.setColor(AppTheme.BORDER);
            g2.drawLine(0, ry, gridW, ry);

            // Frame label ("F0", "F1", ...)
            drawCenteredText(g2, "F" + r, 0, ry, LABEL_COL_W, rowH,
                    AppTheme.font(Font.BOLD, 12), AppTheme.MUTED);

            for (int c = 0; c < steps.size(); c++) {
                int cx = LABEL_COL_W + c * colW;
                PageReplacementStep step = steps.get(c);
                int val = (r < step.frames.length) ? step.frames[r] : -1;
                boolean isTarget = (step.targetFrame == r);
                boolean isSelectedCol = (c == activeStepIndex);

                int padX = 3;
                int padY = 3;
                int cellW = colW - padX * 2;
                int cellH = rowH - padY * 2;

                if (val >= 0) {
                    Color pageColor = AppTheme.processColor(val);
                    int alpha = isTarget ? (step.hit ? 55 : 85) : 34;
                    g2.setColor(new Color(pageColor.getRed(), pageColor.getGreen(), pageColor.getBlue(), alpha));
                    g2.fillRoundRect(cx + padX, ry + padY, cellW, cellH, 7, 7);

                    if (isTarget) {
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.setColor(step.hit ? AppTheme.ACCENT : pageColor);
                        g2.drawRoundRect(cx + padX, ry + padY, cellW - 1, cellH - 1, 7, 7);
                        g2.setStroke(new BasicStroke(1f));
                    } else if (isSelectedCol) {
                        g2.setColor(new Color(255, 255, 255, 65));
                        g2.drawRoundRect(cx + padX, ry + padY, cellW - 1, cellH - 1, 7, 7);
                    }

                    drawCenteredText(g2, String.valueOf(val), cx, ry, colW, rowH,
                            AppTheme.font(Font.BOLD, 13), isTarget ? Color.WHITE : pageColor);
                } else {
                    drawCenteredText(g2, "-", cx, ry, colW, rowH,
                            AppTheme.font(Font.PLAIN, 13), new Color(0x5B7175));
                }
            }
        }

        // ---- 3. Result Row ("Result" + FAULT / HIT badges) ----
        int resY = HEADER_H + frameCount * rowH;
        g2.setColor(AppTheme.BORDER);
        g2.drawLine(0, resY, gridW, resY);

        drawCenteredText(g2, "Result", 0, resY, LABEL_COL_W, RESULT_H,
                AppTheme.font(Font.BOLD, 12), AppTheme.MUTED);

        for (int c = 0; c < steps.size(); c++) {
            int cx = LABEL_COL_W + c * colW;
            PageReplacementStep step = steps.get(c);

            int padX = 3;
            int padY = 5;
            int badgeW = colW - padX * 2;
            int badgeH = RESULT_H - padY * 2;

            Color baseCol = step.hit ? AppTheme.ACCENT_DARK : AppTheme.DANGER;
            g2.setColor(new Color(baseCol.getRed(), baseCol.getGreen(), baseCol.getBlue(), 65));
            g2.fillRoundRect(cx + padX, resY + padY, badgeW, badgeH, 6, 6);

            g2.setColor(baseCol);
            g2.drawRoundRect(cx + padX, resY + padY, badgeW - 1, badgeH - 1, 6, 6);

            String label = step.hit ? "HIT" : "FAULT";
            Color textCol = step.hit ? new Color(0x5EEAD4) : new Color(0xFDA4AF);
            drawCenteredText(g2, label, cx, resY, colW, RESULT_H,
                    AppTheme.font(Font.BOLD, 10), textCol);
        }

        // Vertical separator after the left label column
        g2.setColor(AppTheme.BORDER);
        g2.drawLine(LABEL_COL_W, 0, LABEL_COL_W, totalH);

        g2.dispose();
    }

    private void drawCenteredText(Graphics2D g2, String text, int x, int y, int w, int h, Font font, Color color) {
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (w - fm.stringWidth(text)) / 2;
        int ty = y + ((h - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(text, tx, ty);
    }
}
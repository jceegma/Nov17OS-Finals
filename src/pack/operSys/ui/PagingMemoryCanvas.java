package pack.operSys.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import pack.operSys.logic.PagingEngine;
import pack.operSys.model.AddressTranslationResult;

/**
 * Visualizes physical memory frames (colored by owning process, highlighting
 * the active MMU-translated frame) alongside the 4-step MMU address pipeline.
 */
public class PagingMemoryCanvas extends JPanel {

    private PagingEngine engine;
    private Map<String, Color> colorByPid = new HashMap<>();
    private AddressTranslationResult translation;
    private int hoveredFrame = -1;

    public PagingMemoryCanvas() {
        setOpaque(false);
        setPreferredSize(new Dimension(600, 275));
        setMinimumSize(new Dimension(400, 245));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int prev = hoveredFrame;
                hoveredFrame = frameAt(e.getX(), e.getY());
                if (hoveredFrame != prev) {
                    updateTooltip();
                    repaint();
                }
            }
        });
    }

    public void setState(PagingEngine engine, Map<String, Color> colorByPid, AddressTranslationResult translation) {
        this.engine = engine;
        this.colorByPid = colorByPid != null ? colorByPid : new HashMap<>();
        this.translation = translation;
        repaint();
    }

    private void updateTooltip() {
        if (engine == null || hoveredFrame < 0 || hoveredFrame >= engine.getTotalFrames()) {
            setToolTipText(null);
            return;
        }
        int pageBytes = engine.getPageSizeBytes();
        int base = hoveredFrame * pageBytes;
        int end = base + pageBytes - 1;
        String owner = engine.getFrameOwner(hoveredFrame);
        String who = (owner == null)
                ? "FREE"
                : owner + " (Page " + engine.getFramePage(hoveredFrame) + ")";
        setToolTipText(String.format("Frame %d | %s | Physical Range: %d - %d (0x%X - 0x%X)",
                hoveredFrame, who, base, end, base, end));
    }

    private int frameAt(int mx, int my) {
        if (engine == null) return -1;
        int total = engine.getTotalFrames();
        int cols = total <= 8 ? 4 : 8;
        int rows = (int) Math.ceil(total / (double) cols);

        int padX = 4;
        int topY = 22;
        int gridH = Math.max(96, getHeight() - 122);
        int gap = 8;

        int cellW = (getWidth() - padX * 2 - gap * (cols - 1)) / cols;
        int cellH = (gridH - gap * (rows - 1)) / rows;
        if (cellW <= 0 || cellH <= 0) return -1;

        for (int i = 0; i < total; i++) {
            int r = i / cols;
            int c = i % cols;
            int x = padX + c * (cellW + gap);
            int y = topY + r * (cellH + gap);
            if (mx >= x && mx <= x + cellW && my >= y && my <= y + cellH) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (engine == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int padX = 4;

        // ---- 1. Section Header: Physical Frames ----
        g2.setFont(AppTheme.font(Font.BOLD, 11));
        g2.setColor(AppTheme.MUTED);
        g2.drawString("PHYSICAL MEMORY FRAME MAP (HOVER FOR BYTE RANGE)", padX, 12);

        int highlightedFrame = (translation != null && translation.valid) ? translation.frameNumber : -1;

        int total = engine.getTotalFrames();
        int cols = total <= 8 ? 4 : 8;
        int rows = (int) Math.ceil(total / (double) cols);
        int topY = 22;
        int gridH = Math.max(96, h - 126);
        int gap = 8;

        int cellW = (w - padX * 2 - gap * (cols - 1)) / cols;
        int cellH = (gridH - gap * (rows - 1)) / rows;

        for (int i = 0; i < total; i++) {
            int r = i / cols;
            int c = i % cols;
            int x = padX + c * (cellW + gap);
            int y = topY + r * (cellH + gap);

            String owner = engine.getFrameOwner(i);
            int pageNum = engine.getFramePage(i);
            boolean isTarget = (i == highlightedFrame);
            boolean isHovered = (i == hoveredFrame);

            Color pidColor = (owner != null) ? colorByPid.getOrDefault(owner, AppTheme.ACCENT) : null;

            // Cell fill
            if (pidColor != null) {
                g2.setColor(new Color(pidColor.getRed(), pidColor.getGreen(), pidColor.getBlue(), isTarget ? 78 : 42));
            } else {
                g2.setColor(AppTheme.CARD_ALT);
            }
            g2.fillRoundRect(x, y, cellW, cellH, 10, 10);

            // Cell border
            if (isTarget) {
                g2.setStroke(new BasicStroke(2.2f));
                g2.setColor(Color.WHITE);
            } else if (isHovered) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(AppTheme.ACCENT);
            } else if (pidColor != null) {
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(pidColor.getRed(), pidColor.getGreen(), pidColor.getBlue(), 160));
            } else {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(AppTheme.BORDER);
            }
            g2.drawRoundRect(x, y, cellW - 1, cellH - 1, 10, 10);
            g2.setStroke(new BasicStroke(1f));

            // Frame index label (top-left)
            g2.setFont(AppTheme.font(Font.BOLD, 10));
            g2.setColor(isTarget ? Color.WHITE : AppTheme.MUTED);
            g2.drawString("F" + i, x + 8, y + 14);

            // Center owner / page label
            if (owner != null) {
                g2.setFont(AppTheme.font(Font.BOLD, cellH >= 46 ? 13 : 11));
                g2.setColor(pidColor);
                String mainText = owner + " · Pg" + pageNum;
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (cellW - fm.stringWidth(mainText)) / 2;
                int ty = y + (cellH + fm.getAscent()) / 2 + 3;
                g2.drawString(mainText, Math.max(x + 6, tx), ty);
            } else {
                g2.setFont(AppTheme.font(Font.PLAIN, 11));
                g2.setColor(new Color(0x5B7175));
                FontMetrics fm = g2.getFontMetrics();
                String freeTxt = "FREE";
                g2.drawString(freeTxt, x + (cellW - fm.stringWidth(freeTxt)) / 2, y + (cellH + fm.getAscent()) / 2 + 3);
            }
        }

        // ---- 2. MMU Address Translation Strip ----
        int stripY = topY + gridH + 14;
        int stripH = h - stripY - 2;
        if (stripH >= 64) {
            drawTranslationStrip(g2, padX, stripY, w - padX * 2, stripH);
        }

        g2.dispose();
    }

    private void drawTranslationStrip(Graphics2D g2, int x, int y, int w, int h) {
        g2.setFont(AppTheme.font(Font.BOLD, 11));
        g2.setColor(AppTheme.MUTED);
        String header = "MMU ADDRESS TRANSLATION PIPELINE";
        if (translation != null) {
            header += "  —  " + translation.statusMessage;
        }
        g2.drawString(header, x, y + 10);

        int boxY = y + 18;
        int boxH = h - 18;
        int arrowW = 24;
        int boxW = (w - arrowW * 3) / 4;

        String[] titles = {
            "1. LOGICAL ADDRESS",
            "2. PAGE & OFFSET",
            "3. FRAME LOOKUP",
            "4. PHYSICAL ADDRESS"
        };

        String[] primary = { "--", "--", "--", "--" };
        String[] secondary = { "Select process & address", "p = addr / size, d = addr % size", "Page Table[p]", "(f × size) + d" };
        Color[] accents = { AppTheme.ACCENT, AppTheme.ACCENT, AppTheme.ACCENT, AppTheme.ACCENT };

        if (translation != null) {
            primary[0] = translation.logicalAddress + " (" + translation.pid + ")";
            secondary[0] = String.format("Hex: 0x%04X", Math.max(0, translation.logicalAddress));

            primary[1] = "Pg " + translation.pageNumber + " | Off " + translation.offset;
            secondary[1] = String.format("0x%X | 0x%03X", translation.pageNumber, translation.offset);

            if (translation.valid) {
                Color pCol = colorByPid.getOrDefault(translation.pid, AppTheme.ACCENT);
                primary[2] = "Frame " + translation.frameNumber;
                secondary[2] = "Valid = 1 (Hit)";
                accents[2] = pCol;

                primary[3] = String.valueOf(translation.physicalAddress);
                secondary[3] = String.format("Hex: 0x%04X", translation.physicalAddress);
                accents[3] = AppTheme.ACCENT_DARK;
            } else {
                primary[2] = "PAGE FAULT";
                secondary[2] = "Valid = 0 (Trap)";
                accents[2] = AppTheme.DANGER;

                primary[3] = "UNMAPPED";
                secondary[3] = "Translation aborted";
                accents[3] = AppTheme.DANGER;
            }
        }

        for (int i = 0; i < 4; i++) {
            int bx = x + i * (boxW + arrowW);

            g2.setColor(AppTheme.CARD_ALT);
            g2.fillRoundRect(bx, boxY, boxW, boxH, 10, 10);

            g2.setColor(i >= 2 && translation != null ? accents[i] : AppTheme.BORDER);
            g2.drawRoundRect(bx, boxY, boxW - 1, boxH - 1, 10, 10);

            g2.setFont(AppTheme.font(Font.BOLD, 10));
            g2.setColor(AppTheme.MUTED);
            g2.drawString(titles[i], bx + 10, boxY + 16);

            g2.setFont(AppTheme.font(Font.BOLD, 14));
            g2.setColor(i >= 2 && translation != null && !translation.valid ? AppTheme.DANGER : AppTheme.TEXT);
            g2.drawString(primary[i], bx + 10, boxY + 36);

            if (boxH >= 56) {
                g2.setFont(AppTheme.font(Font.PLAIN, 11));
                g2.setColor(AppTheme.MUTED);
                g2.drawString(secondary[i], bx + 10, boxY + 52);
            }

            if (i < 3) {
                int ax = bx + boxW + (arrowW / 2) - 4;
                int ay = boxY + (boxH / 2) + 4;
                g2.setFont(AppTheme.font(Font.BOLD, 14));
                g2.setColor(AppTheme.ACCENT);
                g2.drawString("→", ax, ay);
            }
        }
    }
}
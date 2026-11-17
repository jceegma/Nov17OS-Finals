package pack.operSys.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import pack.operSys.logic.PagingEngine;
import pack.operSys.logic.PagingEngine.TranslationStep;
import pack.operSys.model.PagedProcess;

/**
 * Compact 3-column Paging Model diagram (Silberschatz Fig. 8.4 / 8.5 / 8.13):
 *   Column 1: Logical Memory vertical bar (pages 0..n-1 of selected process)
 *   Column 2: Page Table (p -> f) + Free-Frame List queue
 *   Column 3: Physical Memory vertical bar (frames 0..m-1)
 *   Bottom:   MMU Address Translation [ p | d ] -> [ f | d ]
 */
public class PagingDiagramCanvas extends JPanel {

    public interface InteractionListener {
        void onLogicalPageClicked(int pageNumber);
        void onPhysicalFrameClicked(String ownerPid, int pageNumber);
    }

    private PagingEngine engine;
    private String selectedPid;
    private Map<String, Color> colorByPid = new HashMap<>();
    private TranslationStep step;
    private InteractionListener listener;

    private final Map<Integer, Rectangle> logicalPageRects = new HashMap<>();
    private final Map<Integer, Rectangle> physicalFrameRects = new HashMap<>();

    public PagingDiagramCanvas() {
        setOpaque(false);
        setPreferredSize(new Dimension(640, 235));
        setMinimumSize(new Dimension(480, 215));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (listener == null || engine == null) return;
                for (Map.Entry<Integer, Rectangle> entry : logicalPageRects.entrySet()) {
                    if (entry.getValue().contains(e.getPoint())) {
                        listener.onLogicalPageClicked(entry.getKey());
                        return;
                    }
                }
                for (Map.Entry<Integer, Rectangle> entry : physicalFrameRects.entrySet()) {
                    if (entry.getValue().contains(e.getPoint())) {
                        int frame = entry.getKey();
                        String owner = engine.getFrameOwner(frame);
                        if (owner != null) {
                            listener.onPhysicalFrameClicked(owner, engine.getFramePage(frame));
                        }
                        return;
                    }
                }
            }
        });
    }

    public void setInteractionListener(InteractionListener listener) {
        this.listener = listener;
    }

    public void setState(PagingEngine engine, String selectedPid,
            Map<String, Color> colorByPid, TranslationStep step) {
        this.engine = engine;
        this.selectedPid = selectedPid;
        this.colorByPid = colorByPid != null ? colorByPid : new HashMap<>();
        this.step = step;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        logicalPageRects.clear();
        physicalFrameRects.clear();
        if (engine == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        int stripH = 50;
        int topY = 18;
        int diagramH = h - stripH - 8;
        int barAreaH = Math.max(115, diagramH - topY - 4);

        // Divide width into 3 visual columns: Logical Memory | Page Table | Physical Memory
        int col1X = 14;
        int col1W = Math.min(165, (int) (w * 0.24));

        int col2X = col1X + col1W + Math.max(44, (int) (w * 0.09));
        int col2W = Math.min(130, (int) (w * 0.19));

        int col3X = col2X + col2W + Math.max(50, (int) (w * 0.10));
        int col3W = Math.max(170, w - col3X - 46);

        PagedProcess proc = (selectedPid != null) ? engine.getProcess(selectedPid) : null;
        Color procColor = (proc != null) ? colorByPid.getOrDefault(proc.pid, AppTheme.ACCENT) : AppTheme.ACCENT;

        int activePage = (step != null && step.valid) ? step.pageNumber : -1;
        int activeFrame = (step != null && step.valid) ? step.frameNumber : -1;

        // =================================================================
        // 1. COLUMN 1: LOGICAL MEMORY BAR
        // =================================================================
        drawCenteredHeader(g2, proc != null ? "LOGICAL MEMORY (" + proc.pid + ")" : "LOGICAL MEMORY",
                col1X, col1W, 12);

        Map<Integer, Integer> pageMidY = new HashMap<>();
        if (proc != null && proc.numPages > 0) {
            int slotH = Math.min(34, Math.max(18, barAreaH / Math.max(4, proc.numPages)));
            int stackH = slotH * proc.numPages;
            int startY = topY + Math.max(0, (barAreaH - stackH) / 2);

            for (int p = 0; p < proc.numPages; p++) {
                int y = startY + p * slotH;
                Rectangle rect = new Rectangle(col1X, y, col1W, slotH);
                logicalPageRects.put(p, rect);
                pageMidY.put(p, y + slotH / 2);

                boolean isHighlighted = (p == activePage);

                g2.setColor(new Color(procColor.getRed(), procColor.getGreen(), procColor.getBlue(),
                        isHighlighted ? 90 : 42));
                g2.fillRect(rect.x, rect.y, rect.width, rect.height);

                // Show internal fragmentation slack on the last page
                if (p == proc.numPages - 1 && proc.internalFrag > 0 && proc.allocated) {
                    double fragRatio = (double) proc.internalFrag / proc.pageSizeUnits;
                    int fragH = Math.max(4, (int) (slotH * fragRatio));
                    g2.setColor(new Color(0x0D1416));
                    g2.fillRect(rect.x + 1, rect.y + slotH - fragH, rect.width - 2, fragH - 1);
                }

                g2.setStroke(new BasicStroke(isHighlighted ? 2.0f : 1.1f));
                g2.setColor(isHighlighted ? Color.WHITE : procColor);
                g2.drawRect(rect.x, rect.y, rect.width, rect.height);
                g2.setStroke(new BasicStroke(1f));

                int byteStart = p * engine.getPageSizeBytes();
                int byteEnd = byteStart + engine.getPageSizeBytes() - 1;
                g2.setFont(AppTheme.font(Font.BOLD, 11));
                g2.setColor(AppTheme.TEXT);
                g2.drawString("page " + p, rect.x + 8, rect.y + (slotH / 2) + 4);

                g2.setFont(AppTheme.font(Font.PLAIN, 10));
                g2.setColor(AppTheme.MUTED);
                String range = byteStart + "–" + byteEnd;
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(range, rect.x + rect.width - fm.stringWidth(range) - 8, rect.y + (slotH / 2) + 4);
            }
        } else {
            g2.setFont(AppTheme.font(Font.PLAIN, 12));
            g2.setColor(AppTheme.MUTED);
            g2.drawString("No process", col1X + 24, topY + 40);
        }

        // =================================================================
        // 2. COLUMN 2: PAGE TABLE + FREE-FRAME LIST
        // =================================================================
        drawCenteredHeader(g2, "PAGE TABLE", col2X, col2W, 12);

        Map<Integer, Integer> tableRowMidY = new HashMap<>();
        int freeQueueAreaH = 44;

        if (proc != null && proc.numPages > 0) {
            int availTableH = Math.max(60, barAreaH - freeQueueAreaH - 4);
            int rowH = Math.min(26, Math.max(15, availTableH / Math.max(4, proc.numPages)));
            int boxX = col2X + 24;
            int boxW = col2W - 30;
            int startY = topY + 2;

            for (int p = 0; p < proc.numPages; p++) {
                int y = startY + p * rowH;
                int midY = y + rowH / 2;
                tableRowMidY.put(p, midY);

                boolean isHighlighted = (p == activePage);

                g2.setFont(AppTheme.font(Font.BOLD, 11));
                g2.setColor(isHighlighted ? Color.WHITE : AppTheme.MUTED);
                g2.drawString(String.valueOf(p), boxX - 16, midY + 4);

                g2.setColor(isHighlighted ? AppTheme.ACCENT : AppTheme.CARD_ALT);
                g2.fillRect(boxX, y, boxW, rowH);

                g2.setStroke(new BasicStroke(isHighlighted ? 2.0f : 1.1f));
                g2.setColor(isHighlighted ? Color.WHITE : AppTheme.BORDER);
                g2.drawRect(boxX, y, boxW, rowH);
                g2.setStroke(new BasicStroke(1f));

                int frame = proc.pageTable[p];
                String fText = (proc.allocated && frame >= 0) ? String.valueOf(frame) : "-";
                g2.setFont(AppTheme.font(Font.BOLD, 11));
                g2.setColor(isHighlighted ? Color.WHITE : AppTheme.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(fText, boxX + (boxW - fm.stringWidth(fText)) / 2, midY + 4);
            }
        }

        drawFreeFrameQueue(g2, col2X, topY + barAreaH - freeQueueAreaH + 2, col2W);

        // =================================================================
        // 3. COLUMN 3: PHYSICAL MEMORY BAR
        // =================================================================
        drawCenteredHeader(g2, "PHYSICAL MEMORY", col3X, col3W, 12);

        int totalFrames = engine.getTotalFrames();
        int stackCols = totalFrames > 16 ? 2 : 1;
        int framesPerCol = (int) Math.ceil(totalFrames / (double) stackCols);
        int frameH = Math.max(14, barAreaH / framesPerCol);
        int physStartY = topY + Math.max(0, (barAreaH - frameH * framesPerCol) / 2);

        Map<Integer, Integer> frameMidY = new HashMap<>();
        int subColGap = 34;
        int subColW = (col3W - (stackCols - 1) * subColGap) / stackCols;

        for (int f = 0; f < totalFrames; f++) {
            int sc = f / framesPerCol;
            int sr = f % framesPerCol;
            int fx = col3X + sc * (subColW + subColGap);
            int fy = physStartY + sr * frameH;

            Rectangle rect = new Rectangle(fx, fy, subColW, frameH);
            physicalFrameRects.put(f, rect);
            frameMidY.put(f, fy + frameH / 2);

            String owner = engine.getFrameOwner(f);
            int pNum = engine.getFramePage(f);
            boolean isHighlighted = (f == activeFrame);
            Color ownerColor = (owner != null) ? colorByPid.getOrDefault(owner, AppTheme.ACCENT) : null;

            g2.setFont(AppTheme.font(Font.BOLD, 10));
            g2.setColor(isHighlighted ? Color.WHITE : AppTheme.MUTED);
            String fNumStr = String.valueOf(f);
            FontMetrics fmNum = g2.getFontMetrics();
            g2.drawString(fNumStr, fx - fmNum.stringWidth(fNumStr) - 6, fy + (frameH / 2) + 4);

            if (ownerColor != null) {
                g2.setColor(new Color(ownerColor.getRed(), ownerColor.getGreen(), ownerColor.getBlue(),
                        isHighlighted ? 95 : 46));
            } else {
                g2.setColor(new Color(0x16292D));
            }
            g2.fillRect(rect.x, rect.y, rect.width, rect.height);

            g2.setStroke(new BasicStroke(isHighlighted ? 2.0f : 1.1f));
            if (isHighlighted) {
                g2.setColor(Color.WHITE);
            } else if (ownerColor != null) {
                g2.setColor(ownerColor);
            } else {
                g2.setColor(AppTheme.BORDER);
            }
            g2.drawRect(rect.x, rect.y, rect.width, rect.height);
            g2.setStroke(new BasicStroke(1f));

            if (owner != null) {
                g2.setFont(AppTheme.font(Font.BOLD, frameH >= 19 ? 11 : 10));
                g2.setColor(isHighlighted ? Color.WHITE : ownerColor);
                String label = owner + " · page " + pNum;
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, rect.x + (rect.width - fm.stringWidth(label)) / 2,
                        rect.y + (frameH / 2) + 4);
            } else {
                g2.setFont(AppTheme.font(Font.PLAIN, 10));
                g2.setColor(new Color(0x5B7175));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("free", rect.x + (rect.width - fm.stringWidth("free")) / 2,
                        rect.y + (frameH / 2) + 4);
            }

            if (stackCols == 1) {
                int physBase = f * engine.getPageSizeBytes();
                g2.setFont(AppTheme.font(Font.PLAIN, 10));
                g2.setColor(AppTheme.MUTED);
                g2.drawString(String.valueOf(physBase), rect.x + rect.width + 6, fy + (frameH / 2) + 4);
            }
        }

        // =================================================================
        // 4. CONNECTING ARROWS: Logical Page -> Page Table -> Physical Frame
        // =================================================================
        if (proc != null && proc.allocated) {
            int boxLeftX = col2X + 24;
            int boxRightX = boxLeftX + (col2W - 30);

            for (int p = 0; p < proc.numPages; p++) {
                int f = proc.pageTable[p];
                if (f < 0 || !pageMidY.containsKey(p) || !tableRowMidY.containsKey(p) || !frameMidY.containsKey(f)) {
                    continue;
                }
                boolean isHighlighted = (p == activePage);
                int y1 = pageMidY.get(p);
                int y2 = tableRowMidY.get(p);
                int y3 = frameMidY.get(f);

                g2.setStroke(new BasicStroke(isHighlighted ? 2.0f : 1.1f));
                g2.setColor(isHighlighted ? Color.WHITE
                        : new Color(procColor.getRed(), procColor.getGreen(), procColor.getBlue(), 130));

                drawCurveArrow(g2, col1X + col1W, y1, boxLeftX - 18, y2);

                int targetFrameX = physicalFrameRects.get(f).x - 20;
                drawCurveArrow(g2, boxRightX, y2, targetFrameX, y3);
            }
            g2.setStroke(new BasicStroke(1f));
        }

        // =================================================================
        // 5. BOTTOM HARDWARE TRANSLATION STRIP
        // =================================================================
        drawTranslationFormulaStrip(g2, 4, h - stripH, w - 8, stripH - 2);

        g2.dispose();
    }

    private void drawFreeFrameQueue(Graphics2D g2, int x, int y, int w) {
        List<Integer> freeList = engine.getFreeFrameList();
        drawCenteredHeader(g2, "FREE-FRAME LIST", x, w, y + 9);

        int boxY = y + 14;
        int boxH = 26;
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(x, boxY, w, boxH, 8, 8);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(x, boxY, w, boxH, 8, 8);

        String content;
        if (freeList.isEmpty()) {
            content = "Empty (0 free)";
            g2.setColor(AppTheme.DANGER);
        } else {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(freeList.size(), 8);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                sb.append(freeList.get(i));
            }
            if (freeList.size() > limit) sb.append("...");
            content = sb.toString();
            g2.setColor(AppTheme.ACCENT);
        }

        g2.setFont(AppTheme.font(Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(content, x + (w - fm.stringWidth(content)) / 2, boxY + 17);
    }

    private void drawTranslationFormulaStrip(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(step != null && !step.valid ? AppTheme.DANGER : AppTheme.BORDER);
        g2.drawRoundRect(x, y, w, h, 8, 8);

        g2.setFont(AppTheme.font(Font.BOLD, 10));
        g2.setColor(AppTheme.MUTED);
        g2.drawString("ADDRESS TRANSLATION  (Logical [ p | d ]  →  Page Table  →  Physical [ f | d ])",
                x + 10, y + 13);

        if (step == null) return;

        if (!step.valid) {
            g2.setFont(AppTheme.font(Font.BOLD, 11));
            g2.setColor(AppTheme.DANGER);
            g2.drawString(step.message, x + 10, y + 34);
            return;
        }

        int curX = x + 10;
        int boxY = y + 19;
        int boxH = 22;

        curX = drawTupleBox(g2, curX, boxY, boxH, "p = " + step.pageNumber, "d = " + step.offset, AppTheme.ACCENT);

        g2.setFont(AppTheme.font(Font.BOLD, 13));
        g2.setColor(AppTheme.ACCENT);
        g2.drawString("→", curX + 7, boxY + 15);
        curX += 24;

        curX = drawTupleBox(g2, curX, boxY, boxH, "f = " + step.frameNumber, "d = " + step.offset, AppTheme.ACCENT_DARK);

        g2.setFont(AppTheme.font(Font.BOLD, 11));
        g2.setColor(AppTheme.TEXT);
        String formula = String.format("  =  (%d × %d) + %d  =  Physical Address %d",
                step.frameNumber, step.pageSizeBytes, step.offset, step.physicalAddress);
        g2.drawString(formula, curX + 4, boxY + 15);
    }

    private int drawTupleBox(Graphics2D g2, int x, int y, int h, String leftText, String rightText, Color borderCol) {
        g2.setFont(AppTheme.font(Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int w1 = fm.stringWidth(leftText) + 14;
        int w2 = fm.stringWidth(rightText) + 14;

        g2.setColor(AppTheme.CARD);
        g2.fillRect(x, y, w1 + w2, h);
        g2.setColor(borderCol);
        g2.drawRect(x, y, w1, h);
        g2.drawRect(x + w1, y, w2, h);

        g2.setColor(AppTheme.TEXT);
        g2.drawString(leftText, x + 7, y + 15);
        g2.drawString(rightText, x + w1 + 7, y + 15);

        return x + w1 + w2;
    }

    private void drawCurveArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
        int ctrlOffset = Math.max(12, (x2 - x1) / 2);
        CubicCurve2D curve = new CubicCurve2D.Float(
                x1, y1,
                x1 + ctrlOffset, y1,
                x2 - ctrlOffset, y2,
                x2, y2);
        g2.draw(curve);
        g2.fillPolygon(
                new int[] { x2, x2 - 5, x2 - 5 },
                new int[] { y2, y2 - 3, y2 + 3 },
                3);
    }

    private void drawCenteredHeader(Graphics2D g2, String text, int x, int w, int y) {
        g2.setFont(AppTheme.font(Font.BOLD, 10));
        g2.setColor(AppTheme.MUTED);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, y);
    }
}
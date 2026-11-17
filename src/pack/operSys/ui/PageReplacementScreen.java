package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import pack.operSys.logic.PageReplacementEngine;
import pack.operSys.logic.PageReplacementEngine.Algorithm;
import pack.operSys.model.PageReplacementStep;

/**
 * The Page Replacement screen:
 * - Left column: Interactive Reference String chip builder (Default / Clear,
 *   clickable chips, Add, Random) + Algorithm & Frame count selector.
 * - Right column: 4 Performance Metric tiles (Page Faults, Page Hits, Fault Rate,
 *   Hit Rate) + Step-by-step Frame Allocation Table with live trace bar.
 */
public class PageReplacementScreen extends JPanel {

    private static final int LEFT_WIDTH = 380;
    private static final String[] ALGO_NAMES = { "FIFO", "LRU", "OPT", "LFU" };
    private static final String[] ALGO_TITLES = {
        "First-In, First-Out",
        "Least Recently Used",
        "Optimal Page Replacement",
        "Least Frequently Used"
    };
    private static final String[] ALGO_INFO = {
        "Evicts the oldest page that has been in memory the longest.\n+ Simple queue logic, low overhead\n- Suffers from Belady's Anomaly (more frames can cause more faults)",
        "Evicts the page that has not been accessed for the longest time.\n+ Strong performance, immune to Belady's Anomaly\n- Requires tracking exact access timestamps",
        "Evicts the page that will not be used for the longest period in the future.\n+ Lowest possible page fault rate (theoretical benchmark)\n- Impossible to implement in real OS without future knowledge",
        "Evicts the page with the fewest total memory references since loading.\n+ Keeps heavily accessed pages resident\n- Old pages with high early counts can linger unnecessarily"
    };

    // ---- state ----
    private final List<Integer> referenceString = new ArrayList<>();
    private final PageReplacementEngine engine = new PageReplacementEngine();
    private final Random random = new Random();
    private int selectedAlgo = 1; // LRU default

    // ---- left controls ----
    private final JPanel chipFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final JTextField pageInputField = new JTextField();
    private final List<PillButton> algoButtons = new ArrayList<>();
    private final JLabel algoTitle = new JLabel();
    private final JTextArea algoInfo = new JTextArea(4, 20);
    private final StepperField frameCountField = new StepperField(4, 1, 8);

    // ---- right display ----
    private final StatTile tileFaults = new StatTile("PAGE FAULTS");
    private final StatTile tileHits = new StatTile("PAGE HITS");
    private final StatTile tileFaultRate = new StatTile("FAULT RATE");
    private final StatTile tileHitRate = new StatTile("HIT RATE");

    private final JLabel tableSummaryLabel = new JLabel("LRU · 4 Frames · 11 Steps");
    private final PageReplacementCanvas canvas = new PageReplacementCanvas();
    private final JLabel stepDetailLabel = new JLabel("Hover or click any step column to inspect frame replacement.");

    public PageReplacementScreen() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        canvas.setStepSelectionListener(step -> {
            if (step == null) {
                stepDetailLabel.setText("Add pages to the Reference String to run the simulation.");
                stepDetailLabel.setForeground(AppTheme.MUTED);
            } else {
                stepDetailLabel.setText(step.explanation);
                stepDetailLabel.setForeground(step.hit ? new Color(0x5EEAD4) : new Color(0xFDA4AF));
            }
        });

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;

        gc.gridx = 0;
        gc.weightx = 0;
        gc.insets = new Insets(0, 0, 0, 16);
        add(buildLeftColumn(), gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        add(buildRightColumn(), gc);

        loadDefaultReference();
    }

    // =====================================================================
    //  LAYOUT
    // =====================================================================

    private JComponent buildLeftColumn() {
        JPanel col = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(LEFT_WIDTH, super.getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(LEFT_WIDTH, super.getMinimumSize().height);
            }
        };
        col.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- Top Card: Reference String ---
        RoundedCard refCard = new RoundedCard("Reference String");

        PillButton defaultBtn = new PillButton("Default");
        defaultBtn.setSolidColor(AppTheme.ACCENT);
        defaultBtn.addActionListener(e -> loadDefaultReference());

        PillButton clearBtn = new PillButton("Clear");
        clearBtn.addActionListener(e -> clearReference());

        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
        topRow.setOpaque(false);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        topRow.add(defaultBtn);
        topRow.add(clearBtn);

        chipFlowPanel.setOpaque(true);
        chipFlowPanel.setBackground(AppTheme.CARD_ALT);
        chipFlowPanel.setPreferredSize(new Dimension(320, 120));

        JScrollPane chipScroll = new JScrollPane(chipFlowPanel);
        chipScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        chipScroll.getViewport().setBackground(AppTheme.CARD_ALT);
        chipScroll.setBackground(AppTheme.CARD_ALT);
        ModernScrollBarUI.style(chipScroll);

        JPanel inputRow = new JPanel(new GridBagLayout());
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        GridBagConstraints ig = new GridBagConstraints();
        ig.gridy = 0;
        ig.fill = GridBagConstraints.BOTH;

        pageInputField.setFont(AppTheme.font(Font.BOLD, 13));
        pageInputField.setHorizontalAlignment(SwingConstants.CENTER);
        pageInputField.setBackground(AppTheme.CARD_ALT);
        pageInputField.setForeground(AppTheme.TEXT);
        pageInputField.setCaretColor(Color.WHITE);
        pageInputField.setToolTipText("Enter page number (0-9) or space-separated list");
        pageInputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        pageInputField.addActionListener(e -> addFromInput());

        PillButton addBtn = new PillButton("+ Add");
        addBtn.setSolidColor(AppTheme.ACCENT_DARK);
        addBtn.addActionListener(e -> addFromInput());

        PillButton randomBtn = new PillButton("Random");
        randomBtn.addActionListener(e -> generateRandomReference());

        ig.gridx = 0;
        ig.weightx = 1.0;
        ig.insets = new Insets(0, 0, 0, 6);
        inputRow.add(pageInputField, ig);

        ig.gridx = 1;
        ig.weightx = 0;
        ig.insets = new Insets(0, 0, 0, 6);
        inputRow.add(addBtn, ig);

        ig.gridx = 2;
        ig.weightx = 0;
        ig.insets = new Insets(0, 0, 0, 0);
        inputRow.add(randomBtn, ig);

        refCard.getBody().add(topRow, BorderLayout.NORTH);
        refCard.getBody().add(chipScroll, BorderLayout.CENTER);
        refCard.getBody().add(inputRow, BorderLayout.SOUTH);

        gc.gridy = 0;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(refCard, gc);

        // --- Bottom Card: Algorithm & Frames ---
        RoundedCard algoCard = new RoundedCard("Algorithm & Frames");

        JPanel algoBtnRow = new JPanel(new GridLayout(1, 4, 6, 0));
        algoBtnRow.setOpaque(false);
        for (int i = 0; i < ALGO_NAMES.length; i++) {
            final int idx = i;
            PillButton b = new PillButton(ALGO_NAMES[i]);
            b.addActionListener(e -> selectAlgo(idx));
            algoButtons.add(b);
            algoBtnRow.add(b);
        }

        algoTitle.setFont(AppTheme.font(Font.BOLD, 15));
        algoTitle.setForeground(AppTheme.TEXT);

        algoInfo.setEditable(false);
        algoInfo.setOpaque(false);
        algoInfo.setLineWrap(true);
        algoInfo.setWrapStyleWord(true);
        algoInfo.setFont(AppTheme.font(Font.PLAIN, 12));
        algoInfo.setForeground(AppTheme.MUTED);

        JPanel framesRow = new JPanel(new BorderLayout());
        framesRow.setOpaque(false);
        framesRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));
        JLabel framesLbl = new JLabel("Page Frames");
        framesLbl.setFont(AppTheme.font(Font.BOLD, 13));
        framesLbl.setForeground(AppTheme.TEXT);
        framesRow.add(framesLbl, BorderLayout.WEST);
        framesRow.add(frameCountField, BorderLayout.EAST);
        frameCountField.addChangeListener(e -> recalculate());

        PillButton recalcBtn = new PillButton("Recalculate");
        recalcBtn.setSolidColor(AppTheme.ACCENT);
        recalcBtn.addActionListener(e -> recalculate());

        JPanel centerInfo = new JPanel(new BorderLayout(0, 6));
        centerInfo.setOpaque(false);
        centerInfo.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        centerInfo.add(algoTitle, BorderLayout.NORTH);
        centerInfo.add(algoInfo, BorderLayout.CENTER);

        JPanel southControls = new JPanel(new BorderLayout());
        southControls.setOpaque(false);
        southControls.add(framesRow, BorderLayout.NORTH);
        southControls.add(recalcBtn, BorderLayout.SOUTH);

        algoCard.getBody().add(algoBtnRow, BorderLayout.NORTH);
        algoCard.getBody().add(centerInfo, BorderLayout.CENTER);
        algoCard.getBody().add(southControls, BorderLayout.SOUTH);

        gc.gridy = 1;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(algoCard, gc);
        return col;
    }

    private JComponent buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- Top Card: Performance Metrics ---
        RoundedCard metricsCard = new RoundedCard("Performance Metrics");
        JPanel tiles = new JPanel(new GridLayout(1, 4, 12, 0));
        tiles.setOpaque(false);
        tiles.add(tileFaults);
        tiles.add(tileHits);
        tiles.add(tileFaultRate);
        tiles.add(tileHitRate);
        metricsCard.getBody().add(tiles, BorderLayout.CENTER);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(metricsCard, gc);

        // --- Bottom Card: Frame Allocation Table ---
        RoundedCard tableCard = new RoundedCard("Frame Allocation Table");
        tableSummaryLabel.setFont(AppTheme.font(Font.PLAIN, 12));
        tableSummaryLabel.setForeground(AppTheme.MUTED);
        tableCard.setHeaderRight(tableSummaryLabel);

        JScrollPane tableScroll = new JScrollPane(canvas);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(AppTheme.CARD);
        tableScroll.setBackground(AppTheme.CARD);
        ModernScrollBarUI.style(tableScroll);

        JPanel footerBar = new JPanel(new BorderLayout());
        footerBar.setOpaque(true);
        footerBar.setBackground(AppTheme.CARD_ALT);
        footerBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        stepDetailLabel.setFont(AppTheme.font(Font.BOLD, 12));
        stepDetailLabel.setForeground(AppTheme.MUTED);
        footerBar.add(stepDetailLabel, BorderLayout.CENTER);

        tableCard.getBody().add(tableScroll, BorderLayout.CENTER);
        tableCard.getBody().add(footerBar, BorderLayout.SOUTH);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(tableCard, gc);
        return col;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    private void loadDefaultReference() {
        referenceString.clear();
        referenceString.addAll(Arrays.asList(7, 0, 3, 0, 4, 2, 3, 0, 3, 2, 0));
        frameCountField.setValue(4);
        selectAlgo(1); // LRU
    }

    private void clearReference() {
        referenceString.clear();
        rebuildChips();
        recalculate();
    }

    private void generateRandomReference() {
        referenceString.clear();
        int len = 12;
        for (int i = 0; i < len; i++) {
            referenceString.add(random.nextInt(8));
        }
        rebuildChips();
        recalculate();
    }

    private void addFromInput() {
        String raw = pageInputField.getText().trim();
        if (raw.isEmpty()) return;

        String[] tokens = raw.split("[,\\s]+");
        boolean added = false;
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            try {
                int val = Integer.parseInt(tok);
                if (val >= 0 && val <= 99 && referenceString.size() < 30) {
                    referenceString.add(val);
                    added = true;
                }
            } catch (NumberFormatException ignored) {
                // Skip non-numeric token
            }
        }
        pageInputField.setText("");
        if (added) {
            rebuildChips();
            recalculate();
        }
    }

    private void selectAlgo(int index) {
        selectedAlgo = index;
        for (int i = 0; i < algoButtons.size(); i++) {
            algoButtons.get(i).setActive(i == index);
        }
        algoTitle.setText(ALGO_TITLES[index]);
        algoInfo.setText(ALGO_INFO[index]);
        recalculate();
    }

    private Algorithm currentAlgorithm() {
        switch (selectedAlgo) {
            case 0:  return Algorithm.FIFO;
            case 2:  return Algorithm.OPT;
            case 3:  return Algorithm.LFU;
            case 1:
            default: return Algorithm.LRU;
        }
    }

    private void rebuildChips() {
        chipFlowPanel.removeAll();
        if (referenceString.isEmpty()) {
            JLabel empty = new JLabel("No pages in sequence. Type below or click Random.");
            empty.setFont(AppTheme.font(Font.PLAIN, 12));
            empty.setForeground(AppTheme.MUTED);
            chipFlowPanel.add(empty);
        } else {
            for (int i = 0; i < referenceString.size(); i++) {
                final int idx = i;
                int page = referenceString.get(i);
                PageChip chip = new PageChip(page);
                chip.setToolTipText("Step " + (i + 1) + ": Page " + page + " (click to remove)");
                chip.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        referenceString.remove(idx);
                        rebuildChips();
                        recalculate();
                    }
                });
                chipFlowPanel.add(chip);
            }
        }

        int rows = Math.max(1, (int) Math.ceil(referenceString.size() / 7.0));
        chipFlowPanel.setPreferredSize(new Dimension(310, Math.max(96, rows * 38 + 12)));
        chipFlowPanel.revalidate();
        chipFlowPanel.repaint();
    }

    private void recalculate() {
        rebuildChips();

        int frames = frameCountField.getValue();
        Algorithm algo = currentAlgorithm();
        List<PageReplacementStep> steps = engine.simulate(algo, frames, referenceString);

        int total = steps.size();
        int faults = 0;
        int hits = 0;
        for (PageReplacementStep s : steps) {
            if (s.hit) hits++;
            else faults++;
        }

        if (total == 0) {
            tileFaults.setTextValue("--", 0.0);
            tileHits.setTextValue("--", 0.0);
            tileFaultRate.setTextValue("--", 0.0);
            tileHitRate.setTextValue("--", 0.0);
        } else {
            double faultRatio = (double) faults / total;
            double hitRatio = (double) hits / total;
            tileFaults.setTextValue(String.valueOf(faults), faultRatio);
            tileHits.setTextValue(String.valueOf(hits), hitRatio);
            tileFaultRate.setTextValue(String.format("%.0f%%", faultRatio * 100.0), faultRatio);
            tileHitRate.setTextValue(String.format("%.0f%%", hitRatio * 100.0), hitRatio);
        }

        tableSummaryLabel.setText(algo.name() + " · " + frames + " Frames · " + total + " Steps");
        canvas.setSimulationData(steps, frames);
    }

    // =====================================================================
    //  CUSTOM CHIPS & SYSTEM-STATE STYLE METRIC TILES
    // =====================================================================

    /** Small circular badge representing one page request in the reference string. */
    private static class PageChip extends JLabel {
        private final Color color;
        private boolean hover;

        PageChip(int page) {
            super(String.valueOf(page), SwingConstants.CENTER);
            this.color = AppTheme.processColor(page);
            setFont(AppTheme.font(Font.BOLD, 12));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(32, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), hover ? 120 : 65));
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);

            g2.setColor(hover ? Color.WHITE : color);
            g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Dark slate metric card with subtle border and bottom teal accent bar (matching Banker's System State). */
    private static class StatTile extends JPanel {
        private final JLabel valueLabel = new JLabel("--");
        private double ratio = 1.0;

        StatTile(String title) {
            setOpaque(false);
            setLayout(new GridLayout(2, 1, 0, 2));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 14, 16));
            setPreferredSize(new Dimension(120, 62));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AppTheme.font(Font.BOLD, 11));
            titleLabel.setForeground(AppTheme.MUTED);

            valueLabel.setFont(AppTheme.font(Font.BOLD, 20));
            valueLabel.setForeground(AppTheme.TEXT);

            add(titleLabel);
            add(valueLabel);
        }

        void setTextValue(String text, double ratio) {
            valueLabel.setText(text);
            this.ratio = Math.max(0.0, Math.min(1.0, ratio));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(AppTheme.CARD_ALT);
            g2.fillRoundRect(0, 0, w, h, 12, 12);

            g2.setColor(AppTheme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

            int barX = 16;
            int barY = h - 10;
            int barW = Math.max(10, w - 32);
            int barH = 4;
            g2.setColor(new Color(0x22353A));
            g2.fillRoundRect(barX, barY, barW, barH, 4, 4);

            int fillW = (int) Math.round(barW * ratio);
            if (fillW > 0) {
                g2.setColor(AppTheme.ACCENT);
                g2.fillRoundRect(barX, barY, fillW, barH, 4, 4);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
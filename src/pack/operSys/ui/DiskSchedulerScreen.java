package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import pack.operSys.logic.DiskSchedulerEngine;
import pack.operSys.logic.DiskSchedulerEngine.Algorithm;
import pack.operSys.logic.DiskSchedulerEngine.SeekResult;
import pack.operSys.model.DiskSeekStep;

/**
 * The Disk Scheduling screen:
 * - Left column: Interactive Request Queue chips (Default / Clear, Add, Random),
 *   Initial Head / Max Track / Count steppers, and Algorithm + Direction selector.
 * - Right column: 4 Summary Metric tiles, Interactive Head Movement Path chart,
 *   Seek Sequence table, and live Algorithm Comparison table.
 */
public class DiskSchedulerScreen extends JPanel {

    private static final int LEFT_WIDTH = 380;
    private static final Algorithm[] ALGOS = Algorithm.values();
    private static final String[] ALGO_INFO = {
        "First-In, First-Out: Services disk requests strictly in arrival order. Fair, but causes wide random arm swings.",
        "Shortest Seek Time First: Picks the pending request closest to the current head position. Low seek time, but can starve distant tracks.",
        "SCAN (Elevator): Sweeps toward one end of the disk, servicing requests along the way, then reverses at the boundary.",
        "Circular SCAN: Sweeps in one direction to the disk end, jumps back to track 0, and continues scanning uniformly.",
        "LOOK: Like SCAN, but reverses immediately after reaching the furthest request instead of going to the physical edge.",
        "Circular LOOK: Like C-SCAN, but jumps directly from the highest request to the lowest request without touching boundaries."
    };

    // ---- state ----
    private final List<Integer> requestQueue = new ArrayList<>();
    private final DiskSchedulerEngine engine = new DiskSchedulerEngine();
    private final Random random = new Random();
    private int selectedAlgoIdx = 0; // FIFO default
    private boolean moveRight = true;
    private Algorithm bestAlgorithm = Algorithm.SSTF;

    // ---- left controls ----
    private final JPanel chipFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final JTextField trackInputField = new JTextField();
    private final StepperField headField = new StepperField(53, 0, 999);
    private final StepperField maxTrackField = new StepperField(199, 10, 999);
    private final StepperField randomCountField = new StepperField(8, 1, 20);

    private final List<PillButton> algoButtons = new ArrayList<>();
    private final PillButton dirRightBtn = new PillButton("Right →");
    private final PillButton dirLeftBtn = new PillButton("← Left");
    private final JTextArea algoInfo = new JTextArea(2, 20);

    // ---- right display ----
    private final StatTile tileTotalSeek = new StatTile("TOTAL SEEK");
    private final StatTile tileAvgSeek = new StatTile("AVG SEEK");
    private final StatTile tileRequests = new StatTile("REQUESTS");
    private final StatTile tileBestAlgo = new StatTile("BEST ALGO");

    private final DiskHeadCanvas headCanvas = new DiskHeadCanvas();
    private final JLabel pathStepLabel = new JLabel("Hover or click any node to inspect seek distance");

    private final DefaultTableModel seqModel = new DefaultTableModel(
            new Object[] { "SEQ", "TRACK", "TRAVERSED" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };

    private final DefaultTableModel compModel = new DefaultTableModel(
            new Object[] { "ALGORITHM", "TOTAL", "AVG / SEEK" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };

    private final JTable seqTable = new JTable(seqModel);
    private final JTable compTable = new JTable(compModel);
    private boolean syncingSelection = false;

    public DiskSchedulerScreen() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        styleTable(seqTable);
        styleTable(compTable);
        seqTable.getColumnModel().getColumn(0).setPreferredWidth(54);
        seqTable.getColumnModel().getColumn(1).setPreferredWidth(68);
        seqTable.getColumnModel().getColumn(2).setPreferredWidth(140);

        compTable.getColumnModel().getColumn(0).setCellRenderer(new ComparisonAlgoRenderer());

        seqTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        seqTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || syncingSelection) return;
            int row = seqTable.getSelectedRow();
            if (row >= 0) {
                headCanvas.highlightStep(row);
            }
        });

        compTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        compTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || syncingSelection) return;
            int row = compTable.getSelectedRow();
            if (row >= 0 && row < ALGOS.length && row != selectedAlgoIdx) {
                selectAlgorithm(row);
            }
        });

        headCanvas.setStepListener((step, index) -> {
            if (step == null) {
                pathStepLabel.setText("Hover or click any node to inspect seek distance");
                pathStepLabel.setForeground(AppTheme.MUTED);
            } else if (step.stepNumber == 0) {
                pathStepLabel.setText("Start at Cylinder " + step.toTrack);
                pathStepLabel.setForeground(new Color(0x34D399));
            } else {
                String tag = step.isBoundary ? " [Boundary]" : "";
                pathStepLabel.setText("Step " + step.stepNumber + ": " + step.fromTrack
                        + " → " + step.toTrack + " (" + step.getFormula() + ")" + tag);
                pathStepLabel.setForeground(step.isBoundary ? new Color(0xF2B84B) : new Color(0x5EEAD4));
            }
            if (index >= 0 && index < seqTable.getRowCount()) {
                syncingSelection = true;
                seqTable.setRowSelectionInterval(index, index);
                syncingSelection = false;
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

        loadDefaultSample();
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

        // --- Top Card: Request Queue & Disk Parameters ---
        RoundedCard queueCard = new RoundedCard("Request Queue");

        PillButton defaultBtn = new PillButton("Default");
        defaultBtn.setSolidColor(AppTheme.ACCENT);
        defaultBtn.addActionListener(e -> loadDefaultSample());

        PillButton clearBtn = new PillButton("Clear");
        clearBtn.addActionListener(e -> clearQueue());

        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
        topRow.setOpaque(false);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        topRow.add(defaultBtn);
        topRow.add(clearBtn);

        chipFlowPanel.setOpaque(true);
        chipFlowPanel.setBackground(AppTheme.CARD_ALT);

        JScrollPane chipScroll = new JScrollPane(chipFlowPanel);
        chipScroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        chipScroll.getViewport().setBackground(AppTheme.CARD_ALT);
        chipScroll.setBackground(AppTheme.CARD_ALT);
        ModernScrollBarUI.style(chipScroll);

        JPanel controlsFooter = new JPanel(new GridBagLayout());
        controlsFooter.setOpaque(false);
        controlsFooter.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        GridBagConstraints cg = new GridBagConstraints();
        cg.fill = GridBagConstraints.BOTH;

        trackInputField.setFont(AppTheme.font(Font.BOLD, 13));
        trackInputField.setHorizontalAlignment(SwingConstants.CENTER);
        trackInputField.setBackground(AppTheme.CARD_ALT);
        trackInputField.setForeground(AppTheme.TEXT);
        trackInputField.setCaretColor(Color.WHITE);
        trackInputField.setToolTipText("Enter track number(s) separated by spaces or commas");
        trackInputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        trackInputField.addActionListener(e -> addTracksFromInput());

        PillButton addBtn = new PillButton("+ Add");
        addBtn.setSolidColor(AppTheme.ACCENT_DARK);
        addBtn.addActionListener(e -> addTracksFromInput());

        PillButton randomBtn = new PillButton("Random");
        randomBtn.addActionListener(e -> generateRandomQueue());

        JPanel addRow = new JPanel(new GridBagLayout());
        addRow.setOpaque(false);
        GridBagConstraints ag = new GridBagConstraints();
        ag.gridy = 0;
        ag.fill = GridBagConstraints.BOTH;

        ag.gridx = 0;
        ag.weightx = 1.0;
        ag.insets = new Insets(0, 0, 0, 6);
        addRow.add(trackInputField, ag);

        ag.gridx = 1;
        ag.weightx = 0;
        ag.insets = new Insets(0, 0, 0, 6);
        addRow.add(addBtn, ag);

        ag.gridx = 2;
        ag.weightx = 0;
        ag.insets = new Insets(0, 0, 0, 0);
        addRow.add(randomBtn, ag);

        cg.gridx = 0;
        cg.gridy = 0;
        cg.weightx = 1;
        cg.insets = new Insets(0, 0, 10, 0);
        controlsFooter.add(addRow, cg);

        cg.gridy = 1;
        cg.insets = new Insets(0, 0, 6, 0);
        controlsFooter.add(buildStepperRow("Initial Head", headField), cg);
        headField.addChangeListener(e -> recalculate());

        cg.gridy = 2;
        cg.insets = new Insets(0, 0, 6, 0);
        controlsFooter.add(buildStepperRow("Max Track", maxTrackField), cg);
        maxTrackField.addChangeListener(e -> {
            clampQueueAndHeadToMax();
            recalculate();
        });

        cg.gridy = 3;
        cg.insets = new Insets(0, 0, 0, 0);
        controlsFooter.add(buildStepperRow("Random Count", randomCountField), cg);

        queueCard.getBody().add(topRow, BorderLayout.NORTH);
        queueCard.getBody().add(chipScroll, BorderLayout.CENTER);
        queueCard.getBody().add(controlsFooter, BorderLayout.SOUTH);

        gc.gridy = 0;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(queueCard, gc);

        // --- Bottom Card: Algorithm & Head Direction ---
        RoundedCard algoCard = new RoundedCard("Scheduling Algorithm");
        JPanel algoBody = new JPanel(new BorderLayout(0, 10));
        algoBody.setOpaque(false);

        JPanel algoGrid = new JPanel(new GridLayout(2, 3, 6, 6));
        algoGrid.setOpaque(false);
        for (int i = 0; i < ALGOS.length; i++) {
            final int idx = i;
            PillButton b = new PillButton(ALGOS[i].label);
            b.addActionListener(e -> selectAlgorithm(idx));
            algoButtons.add(b);
            algoGrid.add(b);
        }

        JPanel dirRow = new JPanel(new BorderLayout(8, 0));
        dirRow.setOpaque(false);
        JLabel dirLbl = new JLabel("Head Direction");
        dirLbl.setFont(AppTheme.font(Font.BOLD, 12));
        dirLbl.setForeground(AppTheme.MUTED);

        JPanel dirBtns = new JPanel(new GridLayout(1, 2, 6, 0));
        dirBtns.setOpaque(false);
        dirRightBtn.addActionListener(e -> setDirection(true));
        dirLeftBtn.addActionListener(e -> setDirection(false));
        dirBtns.add(dirRightBtn);
        dirBtns.add(dirLeftBtn);

        dirRow.add(dirLbl, BorderLayout.WEST);
        dirRow.add(dirBtns, BorderLayout.EAST);

        algoInfo.setEditable(false);
        algoInfo.setOpaque(false);
        algoInfo.setLineWrap(true);
        algoInfo.setWrapStyleWord(true);
        algoInfo.setFont(AppTheme.font(Font.PLAIN, 12));
        algoInfo.setForeground(AppTheme.MUTED);

        algoBody.add(algoGrid, BorderLayout.NORTH);
        algoBody.add(dirRow, BorderLayout.CENTER);
        algoBody.add(algoInfo, BorderLayout.SOUTH);

        algoCard.getBody().add(algoBody, BorderLayout.CENTER);

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

        // --- Top Row: 4 Metric Tiles ---
        JPanel tiles = new JPanel(new GridLayout(1, 4, 12, 0));
        tiles.setOpaque(false);
        tiles.add(tileTotalSeek);
        tiles.add(tileAvgSeek);
        tiles.add(tileRequests);
        tiles.add(tileBestAlgo);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(tiles, gc);

        // --- Middle Card: Head Path Chart ---
        RoundedCard pathCard = new RoundedCard("Head Path");
        pathStepLabel.setFont(AppTheme.font(Font.BOLD, 12));
        pathStepLabel.setForeground(AppTheme.MUTED);
        pathCard.setHeaderRight(pathStepLabel);

        JScrollPane chartScroll = new JScrollPane(headCanvas);
        chartScroll.setBorder(BorderFactory.createEmptyBorder());
        chartScroll.getViewport().setBackground(AppTheme.CARD);
        chartScroll.setBackground(AppTheme.CARD);
        chartScroll.setPreferredSize(new Dimension(560, 235));
        chartScroll.setMinimumSize(new Dimension(420, 210));
        ModernScrollBarUI.style(chartScroll);

        pathCard.getBody().add(chartScroll, BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(pathCard, gc);

        // --- Bottom Split: Seek Sequence (left) + Algorithm Comparison (right) ---
        JPanel bottomSplit = new JPanel(new GridBagLayout());
        bottomSplit.setOpaque(false);
        GridBagConstraints bg = new GridBagConstraints();
        bg.gridy = 0;
        bg.fill = GridBagConstraints.BOTH;
        bg.weighty = 1;

        RoundedCard seqCard = new RoundedCard("Seek Sequence");
        seqCard.getBody().add(wrapScroll(seqTable), BorderLayout.CENTER);

        bg.gridx = 0;
        bg.weightx = 0.55;
        bg.insets = new Insets(0, 0, 0, 14);
        bottomSplit.add(seqCard, bg);

        RoundedCard compCard = new RoundedCard("Algorithm Comparison");
        JLabel clickHint = new JLabel("Click row to switch");
        clickHint.setFont(AppTheme.font(Font.PLAIN, 11));
        clickHint.setForeground(AppTheme.MUTED);
        compCard.setHeaderRight(clickHint);
        compCard.getBody().add(wrapScroll(compTable), BorderLayout.CENTER);

        bg.gridx = 1;
        bg.weightx = 0.45;
        bg.insets = new Insets(0, 0, 0, 0);
        bottomSplit.add(compCard, bg);

        gc.gridy = 2;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(bottomSplit, gc);

        return col;
    }

    private JPanel buildStepperRow(String labelText, StepperField field) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(AppTheme.font(Font.BOLD, 12));
        lbl.setForeground(AppTheme.TEXT);
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.EAST);
        return row;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    private void loadDefaultSample() {
        requestQueue.clear();
        requestQueue.addAll(Arrays.asList(98, 183, 37, 122, 14, 124, 65, 67));
        maxTrackField.setValue(199);
        headField.setValue(53);
        moveRight = true;
        updateDirectionButtons();
        selectAlgorithm(0); // FIFO
    }

    private void clearQueue() {
        requestQueue.clear();
        recalculate();
    }

    private void generateRandomQueue() {
        int count = randomCountField.getValue();
        int max = maxTrackField.getValue();
        requestQueue.clear();
        for (int i = 0; i < count; i++) {
            requestQueue.add(random.nextInt(max + 1));
        }
        recalculate();
    }

    private void addTracksFromInput() {
        String raw = trackInputField.getText().trim();
        if (raw.isEmpty()) return;

        int max = maxTrackField.getValue();
        String[] tokens = raw.split("[,\\s]+");
        boolean added = false;

        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            try {
                int val = Integer.parseInt(tok);
                if (val >= 0 && val <= max && requestQueue.size() < 25) {
                    requestQueue.add(val);
                    added = true;
                }
            } catch (NumberFormatException ignored) {
                // Skip invalid token
            }
        }
        trackInputField.setText("");
        if (added) {
            recalculate();
        }
    }

    private void clampQueueAndHeadToMax() {
        int max = maxTrackField.getValue();
        if (headField.getValue() > max) {
            headField.setValue(max);
        }
        for (int i = 0; i < requestQueue.size(); i++) {
            if (requestQueue.get(i) > max) {
                requestQueue.set(i, max);
            }
        }
    }

    private void selectAlgorithm(int idx) {
        selectedAlgoIdx = idx;
        for (int i = 0; i < algoButtons.size(); i++) {
            algoButtons.get(i).setActive(i == idx);
        }
        algoInfo.setText(ALGO_INFO[idx]);
        recalculate();
    }

    private void setDirection(boolean right) {
        this.moveRight = right;
        updateDirectionButtons();
        recalculate();
    }

    private void updateDirectionButtons() {
        dirRightBtn.setActive(moveRight);
        dirLeftBtn.setActive(!moveRight);
    }

    private void rebuildChips() {
        chipFlowPanel.removeAll();
        if (requestQueue.isEmpty()) {
            JLabel empty = new JLabel("Queue is empty. Enter track numbers below or click Random.");
            empty.setFont(AppTheme.font(Font.PLAIN, 12));
            empty.setForeground(AppTheme.MUTED);
            chipFlowPanel.add(empty);
        } else {
            for (int i = 0; i < requestQueue.size(); i++) {
                final int idx = i;
                int track = requestQueue.get(i);
                TrackChip chip = new TrackChip(track, i);
                chip.setToolTipText("Request #" + (i + 1) + ": Track " + track + " (click to remove)");
                chip.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        requestQueue.remove(idx);
                        recalculate();
                    }
                });
                chipFlowPanel.add(chip);
            }
        }

        int rows = Math.max(1, (int) Math.ceil(requestQueue.size() / 6.0));
        chipFlowPanel.setPreferredSize(new Dimension(310, Math.max(76, rows * 36 + 10)));
        chipFlowPanel.revalidate();
        chipFlowPanel.repaint();
    }

    private void recalculate() {
        rebuildChips();

        int max = maxTrackField.getValue();
        int head = Math.min(max, headField.getValue());
        if (head != headField.getValue()) {
            headField.setValue(head);
        }

        Algorithm activeAlgo = ALGOS[selectedAlgoIdx];
        SeekResult activeRes = engine.simulate(activeAlgo, requestQueue, head, max, moveRight);
        List<SeekResult> allResults = engine.compareAll(requestQueue, head, max, moveRight);

        SeekResult best = allResults.get(0);
        for (SeekResult r : allResults) {
            if (r.totalSeek < best.totalSeek) {
                best = r;
            }
        }
        bestAlgorithm = best.algorithm;

        syncingSelection = true;
        seqModel.setRowCount(0);
        for (DiskSeekStep step : activeRes.steps) {
            String seqLabel = (step.stepNumber == 0) ? "Start" : String.valueOf(step.stepNumber);
            String trackLabel = step.isBoundary ? (step.toTrack + " (End)") : String.valueOf(step.toTrack);
            seqModel.addRow(new Object[] { seqLabel, trackLabel, step.getFormula() });
        }

        compModel.setRowCount(0);
        for (SeekResult r : allResults) {
            compModel.addRow(new Object[] {
                r.algorithm.label,
                requestQueue.isEmpty() ? "--" : r.totalSeek,
                requestQueue.isEmpty() ? "--" : DiskSchedulerEngine.fmt(r.avgSeek)
            });
        }
        if (selectedAlgoIdx >= 0 && selectedAlgoIdx < compTable.getRowCount()) {
            compTable.setRowSelectionInterval(selectedAlgoIdx, selectedAlgoIdx);
        }
        syncingSelection = false;

        if (requestQueue.isEmpty()) {
            tileTotalSeek.setTextValue("--", 0.0);
            tileAvgSeek.setTextValue("--", 0.0);
            tileRequests.setTextValue("0", 0.0);
            tileBestAlgo.setTextValue("--", 0.0);
        } else {
            double efficiencyRatio = activeRes.totalSeek > 0
                    ? Math.min(1.0, (double) best.totalSeek / activeRes.totalSeek)
                    : 1.0;
            tileTotalSeek.setTextValue(String.valueOf(activeRes.totalSeek), 1.0);
            tileAvgSeek.setTextValue(DiskSchedulerEngine.fmt(activeRes.avgSeek), 1.0);
            tileRequests.setTextValue(String.valueOf(activeRes.requestCount), 1.0);
            tileBestAlgo.setTextValue(best.algorithm.label + " (" + best.totalSeek + ")", efficiencyRatio);
        }

        headCanvas.setData(activeRes.steps, max);
        seqTable.repaint();
        compTable.repaint();
    }

    // =====================================================================
    //  STYLING HELPERS, CHIPS & SYSTEM-STATE STYLE METRIC TILES
    // =====================================================================

    private JScrollPane wrapScroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(AppTheme.CARD);
        sp.setBackground(AppTheme.CARD);
        ModernScrollBarUI.style(sp);
        return sp;
    }

    private void styleTable(JTable t) {
        t.setBackground(AppTheme.CARD);
        t.setForeground(AppTheme.TEXT);
        t.setFont(AppTheme.font(Font.PLAIN, 12));
        t.setRowHeight(27);
        t.setGridColor(AppTheme.BORDER);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(AppTheme.CARD_ALT);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setPreferredScrollableViewportSize(new Dimension(240, 160));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, center);

        JTableHeader header = t.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(10, 28));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, false, false, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(AppTheme.CARD);
                setForeground(AppTheme.MUTED);
                setFont(AppTheme.font(Font.BOLD, 11));
                setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER));
                return this;
            }
        });
    }

    private class ComparisonAlgoRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, false, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(AppTheme.font(Font.BOLD, 12));
            String name = String.valueOf(value);
            if (!requestQueue.isEmpty() && bestAlgorithm.label.equals(name)) {
                setForeground(new Color(0x5EEAD4));
            } else {
                setForeground(AppTheme.TEXT);
            }
            return this;
        }
    }

    /** Pill badge representing a cylinder track request in the queue. */
    private static class TrackChip extends JLabel {
        private final Color color;
        private boolean hover;

        TrackChip(int track, int index) {
            super(String.valueOf(track), SwingConstants.CENTER);
            this.color = AppTheme.processColor(index);
            setFont(AppTheme.font(Font.BOLD, 12));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(42, 28));
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

            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), hover ? 115 : 60));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            g2.setColor(hover ? Color.WHITE : color);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
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
            setBorder(BorderFactory.createEmptyBorder(8, 14, 14, 14));
            setPreferredSize(new Dimension(110, 62));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AppTheme.font(Font.BOLD, 10));
            titleLabel.setForeground(AppTheme.MUTED);

            valueLabel.setFont(AppTheme.font(Font.BOLD, 18));
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

            int barX = 14;
            int barY = h - 10;
            int barW = Math.max(10, w - 28);
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
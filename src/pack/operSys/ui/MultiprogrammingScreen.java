package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import pack.operSys.logic.MultiprogrammingEngine;
import pack.operSys.logic.MultiprogrammingEngine.SimulationResult;
import pack.operSys.model.MultiprogrammingEvent;
import pack.operSys.model.MultiprogrammingJob;

/**
 * The Multiprogramming screen:
 * - Left column: Jobs table (Run / Clear, + Add Job / Remove) + Job Generator (Jobs, Max Burst, Randomize, Default).
 * - Right column: 6 Summary Metric tiles, the step-by-step Simulation event table, and the Visual Timeline.
 */
public class MultiprogrammingScreen extends JPanel {

    private static final int LEFT_WIDTH = 380;

    // ---- data models behind the two tables ----
    private final DefaultTableModel jobModel = new DefaultTableModel(
            new Object[] { "JOB", "ARRIVAL", "BURST" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return col != 0;
        }
    };

    private final DefaultTableModel simModel = new DefaultTableModel(
            new Object[] { "TIME", "EVENT", "JOBS", "%CPU/JOB", "ELAPSED", "HEADWAY/JOB", "JOB#", "LEFT" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };

    private final JTable jobTable = new JTable(jobModel);
    private final JTable simTable = new JTable(simModel);

    // ---- controls & display ----
    private final StepperField randomCountField = new StepperField(4, 1, 15);
    private final StepperField maxBurstField = new StepperField(10, 1, 50);

    private final StatTile tileJobs = new StatTile("JOBS");
    private final StatTile tileFinish = new StatTile("FINISH TIME");
    private final StatTile tileAvgTat = new StatTile("AVG TAT");
    private final StatTile tileAvgWait = new StatTile("AVG WAIT");
    private final StatTile tileCpuBusy = new StatTile("CPU BUSY");
    private final StatTile tileCpuUtil = new StatTile("CPU UTIL.");

    private final JLabel eventCountLabel = new JLabel("0 events");
    private final JLabel timelineDetailLabel = new JLabel("Hover or click any event node");
    private final MultiprogrammingTimelineCanvas timelineCanvas = new MultiprogrammingTimelineCanvas();

    // ---- state ----
    private final MultiprogrammingEngine engine = new MultiprogrammingEngine();
    private final Random random = new Random();
    private int jobCounter = 0;
    private boolean started = false;
    private boolean updatingModel = false;
    private Map<String, Color> colorByJob = new HashMap<>();

    public MultiprogrammingScreen() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        styleTable(jobTable);
        styleTable(simTable);
        jobTable.getColumnModel().getColumn(0).setCellRenderer(new JobIdRenderer());
        simTable.getColumnModel().getColumn(1).setCellRenderer(new EventCellRenderer());

        simTable.getColumnModel().getColumn(0).setPreferredWidth(62);
        simTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        simTable.getColumnModel().getColumn(2).setPreferredWidth(46);
        simTable.getColumnModel().getColumn(3).setPreferredWidth(78);
        simTable.getColumnModel().getColumn(4).setPreferredWidth(68);
        simTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        simTable.getColumnModel().getColumn(6).setPreferredWidth(95);
        simTable.getColumnModel().getColumn(7).setPreferredWidth(115);

        timelineCanvas.setEventInspectListener(ev -> {
            if (ev == null) {
                timelineDetailLabel.setText("Hover or click any event node");
                timelineDetailLabel.setForeground(AppTheme.MUTED);
            } else {
                timelineDetailLabel.setText(ev.timelineEntry);
                timelineDetailLabel.setForeground(ev.isTermination ? new Color(0x5EEAD4) : new Color(0xF2B84B));
            }
        });

        jobModel.addTableModelListener(e -> {
            if (!updatingModel) {
                recalculate();
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

        loadReferenceSample();
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

        // --- Top Card: Jobs Table ---
        RoundedCard jobsCard = new RoundedCard("Jobs");

        PillButton runBtn = new PillButton("Run");
        runBtn.setSolidColor(AppTheme.ACCENT);
        runBtn.addActionListener(e -> runSimulation());

        PillButton clearBtn = new PillButton("Clear");
        clearBtn.addActionListener(e -> clearAll());

        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
        topRow.setOpaque(false);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        topRow.add(runBtn);
        topRow.add(clearBtn);

        PillButton addBtn = new PillButton("+ Add Job");
        addBtn.setSolidColor(AppTheme.ACCENT_DARK);
        addBtn.addActionListener(e -> addJob());

        PillButton removeBtn = new PillButton("Remove");
        removeBtn.setSolidColor(AppTheme.DANGER);
        removeBtn.addActionListener(e -> removeSelected());

        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 8, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        bottomRow.add(addBtn);
        bottomRow.add(removeBtn);

        jobsCard.getBody().add(topRow, BorderLayout.NORTH);
        jobsCard.getBody().add(wrapScroll(jobTable), BorderLayout.CENTER);
        jobsCard.getBody().add(bottomRow, BorderLayout.SOUTH);

        gc.gridy = 0;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(jobsCard, gc);

        // --- Bottom Card: Job Generator ---
        RoundedCard genCard = new RoundedCard("Job Generator");
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints bg = new GridBagConstraints();
        bg.gridx = 0;
        bg.fill = GridBagConstraints.HORIZONTAL;
        bg.weightx = 1;

        JPanel jobsRow = new JPanel(new BorderLayout());
        jobsRow.setOpaque(false);
        JLabel jobsLbl = new JLabel("Jobs");
        jobsLbl.setFont(AppTheme.font(Font.BOLD, 13));
        jobsLbl.setForeground(AppTheme.TEXT);
        jobsRow.add(jobsLbl, BorderLayout.WEST);
        jobsRow.add(randomCountField, BorderLayout.EAST);

        bg.gridy = 0;
        bg.insets = new Insets(0, 0, 10, 0);
        body.add(jobsRow, bg);

        JPanel burstRow = new JPanel(new BorderLayout());
        burstRow.setOpaque(false);
        JLabel burstLbl = new JLabel("Max Burst");
        burstLbl.setFont(AppTheme.font(Font.BOLD, 13));
        burstLbl.setForeground(AppTheme.TEXT);
        burstRow.add(burstLbl, BorderLayout.WEST);
        burstRow.add(maxBurstField, BorderLayout.EAST);

        bg.gridy = 1;
        bg.insets = new Insets(0, 0, 12, 0);
        body.add(burstRow, bg);

        PillButton randomBtn = new PillButton("Randomize");
        randomBtn.setSolidColor(AppTheme.ACCENT);
        randomBtn.addActionListener(e -> randomizeJobs());

        PillButton sampleBtn = new PillButton("Default");
        sampleBtn.addActionListener(e -> loadReferenceSample());

        JPanel genBtnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        genBtnRow.setOpaque(false);
        genBtnRow.add(randomBtn);
        genBtnRow.add(sampleBtn);

        bg.gridy = 2;
        bg.insets = new Insets(0, 0, 0, 0);
        body.add(genBtnRow, bg);

        genCard.getBody().add(body, BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(genCard, gc);
        return col;
    }

    private JComponent buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- Top Card: Summary Metrics ---
        RoundedCard summaryCard = new RoundedCard("Summary");
        JPanel tiles = new JPanel(new GridLayout(1, 6, 8, 0));
        tiles.setOpaque(false);
        tiles.add(tileJobs);
        tiles.add(tileFinish);
        tiles.add(tileAvgTat);
        tiles.add(tileAvgWait);
        tiles.add(tileCpuBusy);
        tiles.add(tileCpuUtil);
        summaryCard.getBody().add(tiles, BorderLayout.CENTER);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(summaryCard, gc);

        // --- Middle Card: Simulation Table ---
        RoundedCard simCard = new RoundedCard("Simulation");
        eventCountLabel.setFont(AppTheme.font(Font.PLAIN, 12));
        eventCountLabel.setForeground(AppTheme.MUTED);
        simCard.setHeaderRight(eventCountLabel);
        simCard.getBody().add(wrapScroll(simTable), BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 14, 0);
        col.add(simCard, gc);

        // --- Bottom Card: Visual Concurrency & Event Timeline ---
        RoundedCard timelineCard = new RoundedCard("Timeline");
        timelineDetailLabel.setFont(AppTheme.font(Font.BOLD, 12));
        timelineDetailLabel.setForeground(AppTheme.MUTED);
        timelineCard.setHeaderRight(timelineDetailLabel);

        JScrollPane timelineScroll = new JScrollPane(timelineCanvas);
        timelineScroll.setBorder(BorderFactory.createEmptyBorder());
        timelineScroll.getViewport().setBackground(AppTheme.CARD);
        timelineScroll.setBackground(AppTheme.CARD);
        timelineScroll.setPreferredSize(new Dimension(400, 134));
        timelineScroll.setMinimumSize(new Dimension(400, 124));
        ModernScrollBarUI.style(timelineScroll);

        timelineCard.getBody().add(timelineScroll, BorderLayout.CENTER);

        gc.gridy = 2;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(timelineCard, gc);

        return col;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    private void loadReferenceSample() {
        stopEditing();
        updatingModel = true;
        jobCounter = 0;
        jobModel.setRowCount(0);

        double[][] sample = {
            { 7.5, 0.5 },
            { 8.1, 0.6 },
            { 18.0, 1.98 },
            { 13.0, 8.88 },
            { 12.0, 5.69 },
            { 10.0, 0.69 }
        };
        for (double[] row : sample) {
            jobModel.addRow(new Object[] {
                "J" + (++jobCounter),
                MultiprogrammingEngine.fmt(row[0]),
                MultiprogrammingEngine.fmt(row[1])
            });
        }
        updatingModel = false;
        started = true;
        recalculate();
    }

    private void randomizeJobs() {
        stopEditing();
        updatingModel = true;
        jobCounter = 0;
        jobModel.setRowCount(0);

        int count = randomCountField.getValue();
        int maxBurst = maxBurstField.getValue();

        for (int i = 0; i < count; i++) {
            double arrival = Math.round(random.nextDouble() * 15.0 * 10.0) / 10.0;
            double burst = Math.round((0.5 + random.nextDouble() * Math.max(0.5, maxBurst - 0.5)) * 10.0) / 10.0;
            jobModel.addRow(new Object[] {
                "J" + (++jobCounter),
                MultiprogrammingEngine.fmt(arrival),
                MultiprogrammingEngine.fmt(burst)
            });
        }
        updatingModel = false;
        started = true;
        recalculate();
    }

    private void runSimulation() {
        stopEditing();
        if (jobModel.getRowCount() == 0) {
            started = false;
            showEmpty();
            return;
        }
        started = true;
        recalculate();
    }

    private void clearAll() {
        stopEditing();
        updatingModel = true;
        jobCounter = 0;
        jobModel.setRowCount(0);
        updatingModel = false;
        started = false;
        recalculate();
    }

    private void addJob() {
        stopEditing();
        updatingModel = true;
        jobModel.addRow(new Object[] { "J" + (++jobCounter), "0", "2" });
        updatingModel = false;
        recalculate();
    }

    private void removeSelected() {
        stopEditing();
        int[] rows = jobTable.getSelectedRows();
        if (rows.length == 0) return;

        updatingModel = true;
        for (int i = rows.length - 1; i >= 0; i--) {
            jobModel.removeRow(rows[i]);
        }
        updatingModel = false;
        recalculate();
    }

    private void stopEditing() {
        if (jobTable.isEditing()) {
            jobTable.getCellEditor().stopCellEditing();
        }
    }

    private void recalculate() {
        List<MultiprogrammingJob> jobs = readJobs();

        colorByJob = new HashMap<>();
        for (int i = 0; i < jobs.size(); i++) {
            colorByJob.put(jobs.get(i).jobId, AppTheme.processColor(i));
        }

        simModel.setRowCount(0);
        if (!started || jobs.isEmpty()) {
            showEmpty();
            return;
        }

        SimulationResult res = engine.run(jobs);

        for (MultiprogrammingEvent ev : res.events) {
            String timeStr = MultiprogrammingEngine.fmt(ev.time);
            String jobsStr = String.valueOf(ev.activeJobsBefore);
            String cpuStr = ev.isIdleBefore ? "" : MultiprogrammingEngine.fmt(ev.cpuPercentPerJob) + "%";
            String elapStr = ev.isIdleBefore ? "" : MultiprogrammingEngine.fmt(ev.elapsedTime);
            String headStr = ev.isIdleBefore ? "" : MultiprogrammingEngine.fmt(ev.headwayPerJob);

            simModel.addRow(new Object[] {
                timeStr,
                ev.event,
                jobsStr,
                cpuStr,
                elapStr,
                headStr,
                ev.jobNames,
                ev.remainingTimes
            });
        }

        tileJobs.setTextValue(String.valueOf(res.totalJobs), 1.0);
        tileFinish.setTextValue(MultiprogrammingEngine.fmt(res.finishTime), 1.0);
        tileAvgTat.setTextValue(MultiprogrammingEngine.fmt(res.avgTurnaround), 1.0);
        tileAvgWait.setTextValue(MultiprogrammingEngine.fmt(res.avgWaiting), 1.0);
        tileCpuBusy.setTextValue(MultiprogrammingEngine.fmt(res.cpuBusy), 1.0);
        tileCpuUtil.setTextValue(MultiprogrammingEngine.fmt(res.cpuUtilization) + "%", res.cpuUtilization / 100.0);

        eventCountLabel.setText(res.events.size() + " events");
        timelineCanvas.setTimelineData(res.events, colorByJob);
        jobTable.repaint();
    }

    private void showEmpty() {
        simModel.setRowCount(0);
        tileJobs.setTextValue("--", 0.0);
        tileFinish.setTextValue("--", 0.0);
        tileAvgTat.setTextValue("--", 0.0);
        tileAvgWait.setTextValue("--", 0.0);
        tileCpuBusy.setTextValue("--", 0.0);
        tileCpuUtil.setTextValue("--", 0.0);
        eventCountLabel.setText("0 events");
        timelineCanvas.setTimelineData(new ArrayList<>(), colorByJob);
        jobTable.repaint();
    }

    private List<MultiprogrammingJob> readJobs() {
        List<MultiprogrammingJob> list = new ArrayList<>();
        for (int r = 0; r < jobModel.getRowCount(); r++) {
            String id = String.valueOf(jobModel.getValueAt(r, 0));
            double arrival = Math.max(0.0, toDouble(jobModel.getValueAt(r, 1), 0.0));
            double burst = Math.max(0.01, toDouble(jobModel.getValueAt(r, 2), 1.0));
            list.add(new MultiprogrammingJob(id, arrival, burst));
        }
        return list;
    }

    private static double toDouble(Object value, double fallback) {
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // =====================================================================
    //  STYLING HELPERS & RENDERERS
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
        t.setRowHeight(30);
        t.setGridColor(AppTheme.BORDER);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(AppTheme.CARD_ALT);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setPreferredScrollableViewportSize(new Dimension(300, 160));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, selected, false, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                String text = value == null ? "" : String.valueOf(value);
                setToolTipText(text.isEmpty() ? null : text);
                return this;
            }
        };
        t.setDefaultRenderer(Object.class, center);

        JTextField editor = new JTextField();
        editor.setHorizontalAlignment(SwingConstants.CENTER);
        editor.setBackground(AppTheme.CARD_ALT);
        editor.setForeground(Color.WHITE);
        editor.setCaretColor(Color.WHITE);
        editor.setBorder(BorderFactory.createLineBorder(AppTheme.ACCENT));
        t.setDefaultEditor(Object.class, new DefaultCellEditor(editor));

        JTableHeader header = t.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(10, 30));
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

    private class JobIdRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, false, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(AppTheme.font(Font.BOLD, 12));
            setForeground(colorByJob.getOrDefault(String.valueOf(value), AppTheme.TEXT));
            return this;
        }
    }

    private static class EventCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, false, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(AppTheme.font(Font.BOLD, 12));
            String text = String.valueOf(value);
            if (text.contains("Terminated")) {
                setForeground(new Color(0x5EEAD4));
            } else {
                setForeground(new Color(0xF2B84B));
            }
            return this;
        }
    }

    /** Dark slate metric card with subtle border and bottom teal accent bar (matching Banker's System State). */
    private static class StatTile extends JPanel {
        private final JLabel valueLabel = new JLabel("--");
        private double ratio = 1.0;

        StatTile(String title) {
            setOpaque(false);
            setLayout(new GridLayout(2, 1, 0, 1));
            setBorder(BorderFactory.createEmptyBorder(7, 10, 13, 10));
            setPreferredSize(new Dimension(95, 58));
            setMinimumSize(new Dimension(80, 56));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AppTheme.font(Font.BOLD, 10));
            titleLabel.setForeground(AppTheme.MUTED);

            valueLabel.setFont(AppTheme.font(Font.BOLD, 15));
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

            int barX = 10;
            int barY = h - 9;
            int barW = Math.max(10, w - 20);
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
	package pack.operSys.ui;
	
	import java.awt.BorderLayout;
	import java.awt.Color;
	import java.awt.Component;
	import java.awt.Dimension;
	import java.awt.FlowLayout;
	import java.awt.Font;
	import java.awt.GridBagConstraints;
	import java.awt.GridBagLayout;
	import java.awt.GridLayout;
	import java.awt.Insets;
	import java.util.ArrayList;
	import java.util.HashMap;
	import java.util.List;
	import java.util.Map;
	
	import javax.swing.BorderFactory;
	import javax.swing.DefaultCellEditor;
	import javax.swing.JComponent;
	import javax.swing.JLabel;
	import javax.swing.JPanel;
	import javax.swing.JScrollPane;
	import javax.swing.JTable;
	import javax.swing.JTextArea;
	import javax.swing.JTextField;
	import javax.swing.SwingConstants;
	import javax.swing.table.DefaultTableCellRenderer;
	import javax.swing.table.DefaultTableModel;
	import javax.swing.table.JTableHeader;
	
	import pack.operSys.logic.ArrivalOrderEngine;
	import pack.operSys.logic.PriorityRankEngine;
	import pack.operSys.logic.SchedulingAlgorithm;
	import pack.operSys.logic.ShortestBurstEngine;
	import pack.operSys.logic.TimeSliceEngine;
	import pack.operSys.model.GanttBlock;
	import pack.operSys.model.ProcessEntry;
	
	/**
	 * The whole CPU Scheduling screen: process table, algorithm picker,
	 * Gantt chart, metric tiles and the result table.
	 *
	 * Flow: press Start -> recalculate() -> engine.run() -> update the display.
	 * After Start, editing the table or switching algorithm updates the results live.
	 * Clear wipes the table and the results and goes back to the idle state.
	 */
	public class SchedulerScreen extends JPanel {
	
	    /** Width of the left column (Process Table + Algorithm). Change this number to resize it. */
	    private static final int LEFT_WIDTH = 380;
	
	    private static final String[] ALGO_NAMES = { "FCFS", "SJF", "RR", "PRI" };
	    private static final String[] ALGO_TITLES = {
	        "First Come First Serve", "Shortest Job First", "Round Robin", "Priority Scheduling" };
	    private static final String[] ALGO_INFO = {
	        "Processes run in arrival order. Simplest algorithm, no preemption.\n+ Simple, fair, no starvation\n- Convoy effect: short jobs wait behind long ones",
	        "Runs the ready process with the smallest burst time (non-preemptive).\n+ Lowest average waiting time\n- Long jobs can starve",
	        "Each process runs for one time quantum, then goes to the back of the queue.\n+ Fair, good response time\n- Many switches if the quantum is too small",
	        "Runs the ready process with the highest priority (lower number = higher priority), non-preemptive.\n+ Important jobs go first\n- Low priority jobs can starve" };
	
	    // ---- data models behind the two tables ----
	    private final DefaultTableModel processModel = new DefaultTableModel(
	            new Object[] { "PID", "ARRIVAL", "BURST", "PRIORITY" }, 0) {
	        @Override public boolean isCellEditable(int row, int col) { return col != 0; }
	    };
	    private final DefaultTableModel resultModel = new DefaultTableModel(
	            new Object[] { "PROCESS", "AT", "BT", "CT", "TAT", "WT", "RT" }, 0) {
	        @Override public boolean isCellEditable(int row, int col) { return false; }
	    };
	    private final JTable processTable = new JTable(processModel);
	    private final JTable resultTable = new JTable(resultModel);
	
	    // ---- display pieces ----
	    private final GanttCanvas gantt = new GanttCanvas();
	    private final JLabel totalLabel = new JLabel("Total: 0 units");
	    private final MetricTile tileWait = new MetricTile("AVG WAITING TIME", AppTheme.TILE_WAIT);
	    private final MetricTile tileTat = new MetricTile("AVG TURNAROUND", AppTheme.TILE_TAT);
	    private final MetricTile tileCt = new MetricTile("AVG COMPLETION", AppTheme.TILE_CT);
	    private final MetricTile tileRt = new MetricTile("AVG RESPONSE", AppTheme.TILE_RT);
	
	    private final List<PillButton> algoButtons = new ArrayList<>();
	    private final JLabel algoTitle = new JLabel();
	    private final JTextArea algoInfo = new JTextArea(5, 20);
	    private final StepperField quantumField = new StepperField(2, 1, 50);
	    private final JPanel quantumRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
	
	    // ---- state ----
	    private int selectedAlgo = 0;
	    private int pidCounter = 0;
	    private boolean started = false;          // true after Start, false after Clear
	    private Map<String, Color> colorByPid = new HashMap<>();
	
	    public SchedulerScreen() {
	        setOpaque(false);
	        setLayout(new GridBagLayout());
	        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
	
	        styleTable(processTable);
	        styleTable(resultTable);
	        processTable.getColumnModel().getColumn(0).setCellRenderer(new PidRenderer());
	        resultTable.getColumnModel().getColumn(0).setCellRenderer(new PidRenderer());
	        processModel.addTableModelListener(e -> recalculate());
	
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
	
	        // starting data (same as the reference screenshot)
	        int[][] sample = { { 0, 5, 2 }, { 1, 3, 1 }, { 2, 8, 3 }, { 3, 2, 4 } };
	        for (int[] s : sample) {
	            processModel.addRow(new Object[] { "P" + (++pidCounter), s[0], s[1], s[2] });
	        }
	        selectAlgo(0);
	    }
	
	    // =====================================================================
	    //  LAYOUT
	    // =====================================================================
	
	    private JComponent buildLeftColumn() {
	        JPanel col = new JPanel(new GridBagLayout()) {
	            @Override public Dimension getPreferredSize() {
	                return new Dimension(LEFT_WIDTH, super.getPreferredSize().height);
	            }
	            @Override public Dimension getMinimumSize() {
	                return new Dimension(LEFT_WIDTH, super.getMinimumSize().height);
	            }
	        };
	        col.setOpaque(false);
	        GridBagConstraints gc = new GridBagConstraints();
	        gc.gridx = 0;
	        gc.fill = GridBagConstraints.BOTH;
	        gc.weightx = 1;
	
	        // --- Process Table card: title alone, Start/Clear on top, Add/Remove at the bottom ---
	        RoundedCard processCard = new RoundedCard("Process Table");
	
	        PillButton startBtn = new PillButton("Start");
	        startBtn.setSolidColor(AppTheme.ACCENT);
	        startBtn.addActionListener(e -> startSimulation());
	        PillButton clearBtn = new PillButton("Clear");
	        clearBtn.addActionListener(e -> clearAll());
	
	        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
	        topRow.setOpaque(false);
	        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
	        topRow.add(startBtn);
	        topRow.add(clearBtn);
	
	        PillButton addBtn = new PillButton("+ Add Process");
	        addBtn.setSolidColor(AppTheme.ACCENT_DARK);
	        addBtn.addActionListener(e -> addProcess());
	        PillButton removeBtn = new PillButton("Remove");
	        removeBtn.setSolidColor(AppTheme.DANGER);
	        removeBtn.addActionListener(e -> removeSelected());
	
	        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 8, 0));
	        bottomRow.setOpaque(false);
	        bottomRow.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
	        bottomRow.add(addBtn);
	        bottomRow.add(removeBtn);
	
	        processCard.getBody().add(topRow, BorderLayout.NORTH);
	        processCard.getBody().add(wrapScroll(processTable), BorderLayout.CENTER);
	        processCard.getBody().add(bottomRow, BorderLayout.SOUTH);
	
	        gc.gridy = 0;
	        gc.weighty = 1;
	        gc.insets = new Insets(0, 0, 16, 0);
	        col.add(processCard, gc);
	
	        // --- Algorithm card ---
	        RoundedCard algoCard = new RoundedCard("Algorithm");
	
	        JPanel buttonRow = new JPanel(new GridLayout(1, 4, 6, 0));
	        buttonRow.setOpaque(false);
	        for (int i = 0; i < ALGO_NAMES.length; i++) {
	            final int idx = i;
	            PillButton b = new PillButton(ALGO_NAMES[i]);
	            b.addActionListener(e -> selectAlgo(idx));
	            algoButtons.add(b);
	            buttonRow.add(b);
	        }
	
	        algoTitle.setFont(AppTheme.font(Font.BOLD, 15));
	        algoTitle.setForeground(AppTheme.TEXT);
	
	        JLabel quantumLabel = new JLabel("Quantum");
	        quantumLabel.setForeground(AppTheme.MUTED);
	        quantumRow.setOpaque(false);
	        quantumRow.add(quantumLabel);
	        quantumRow.add(quantumField);
	        quantumField.addChangeListener(e -> recalculate());
	
	        algoInfo.setEditable(false);
	        algoInfo.setOpaque(false);
	        algoInfo.setLineWrap(true);
	        algoInfo.setWrapStyleWord(true);
	        algoInfo.setFont(AppTheme.font(Font.PLAIN, 12));
	        algoInfo.setForeground(AppTheme.MUTED);
	
	        JPanel infoTop = new JPanel(new BorderLayout());
	        infoTop.setOpaque(false);
	        infoTop.add(algoTitle, BorderLayout.WEST);
	        infoTop.add(quantumRow, BorderLayout.EAST);
	
	        JPanel info = new JPanel(new BorderLayout(0, 8));
	        info.setOpaque(false);
	        info.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
	        info.add(infoTop, BorderLayout.NORTH);
	        info.add(algoInfo, BorderLayout.CENTER);
	
	        algoCard.getBody().add(buttonRow, BorderLayout.NORTH);
	        algoCard.getBody().add(info, BorderLayout.CENTER);
	
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
	
	        // --- Gantt card ---
	        RoundedCard ganttCard = new RoundedCard("Gantt Chart");
	        totalLabel.setForeground(AppTheme.MUTED);
	        totalLabel.setFont(AppTheme.font(Font.PLAIN, 12));
	        ganttCard.setHeaderRight(totalLabel);
	        ganttCard.getBody().add(gantt, BorderLayout.CENTER);
	
	        gc.gridy = 0;
	        gc.weighty = 0;
	        gc.insets = new Insets(0, 0, 16, 0);
	        col.add(ganttCard, gc);
	
	        // --- Metrics card ---
	        RoundedCard metricsCard = new RoundedCard("Performance Metrics");
	        JPanel tiles = new JPanel(new GridLayout(1, 4, 12, 0));
	        tiles.setOpaque(false);
	        tiles.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
	        tiles.add(tileWait);
	        tiles.add(tileTat);
	        tiles.add(tileCt);
	        tiles.add(tileRt);
	
	        metricsCard.getBody().add(tiles, BorderLayout.NORTH);
	        metricsCard.getBody().add(wrapScroll(resultTable), BorderLayout.CENTER);
	
	        gc.gridy = 1;
	        gc.weighty = 1;
	        gc.insets = new Insets(0, 0, 0, 0);
	        col.add(metricsCard, gc);
	        return col;
	    }
	
	    // =====================================================================
	    //  ACTIONS
	    // =====================================================================
	
	    /** Start: run the selected algorithm on the current table. */
	    private void startSimulation() {
	        stopEditing();
	        if (processModel.getRowCount() == 0) {
	            started = false;
	            showEmpty("Add at least one process first.");
	            return;
	        }
	        started = true;
	        recalculate();
	    }
	
	    /** Clear: remove every process, wipe the results, and go back to the idle state. */
	    private void clearAll() {
	        stopEditing();
	        started = false;
	        pidCounter = 0;
	        processModel.setRowCount(0);
	        recalculate();
	    }
	
	    private void addProcess() {
	        stopEditing();
	        processModel.addRow(new Object[] { "P" + (++pidCounter), 0, 3, 1 });
	    }
	
	    private void removeSelected() {
	        stopEditing();
	        int[] rows = processTable.getSelectedRows();
	        for (int i = rows.length - 1; i >= 0; i--) {
	            processModel.removeRow(rows[i]);
	        }
	    }
	
	    private void stopEditing() {
	        if (processTable.isEditing()) {
	            processTable.getCellEditor().stopCellEditing();
	        }
	    }
	
	    private void selectAlgo(int index) {
	        selectedAlgo = index;
	        for (int i = 0; i < algoButtons.size(); i++) {
	            algoButtons.get(i).setActive(i == index);
	        }
	        algoTitle.setText(ALGO_TITLES[index]);
	        algoInfo.setText(ALGO_INFO[index]);
	        quantumRow.setVisible(index == 2);
	        recalculate();
	    }
	
	    private SchedulingAlgorithm currentAlgorithm() {
	        switch (selectedAlgo) {
	            case 1:  return new ShortestBurstEngine();
	            case 2:  return new TimeSliceEngine(quantumField.getValue());
	            case 3:  return new PriorityRankEngine();
	            default: return new ArrivalOrderEngine();
	        }
	    }
	
	    /** Refreshes colors, and (only after Start) runs the algorithm and updates the whole display. */
	    private void recalculate() {
	        List<ProcessEntry> list = readProcesses();
	
	        colorByPid = new HashMap<>();
	        for (int i = 0; i < list.size(); i++) {
	            colorByPid.put(list.get(i).pid, AppTheme.processColor(i));
	        }
	
	        resultModel.setRowCount(0);
	        if (!started) {
	            showEmpty("Press Start to run the simulation.");
	            return;
	        }
	        if (list.isEmpty()) {
	            showEmpty("Add at least one process.");
	            return;
	        }
	
	        List<GanttBlock> blocks = currentAlgorithm().run(list);
	        gantt.setBlocks(blocks, colorByPid);
	
	        int total = 0;
	        double wt = 0, tat = 0, ct = 0, rt = 0;
	        for (GanttBlock b : blocks) total = Math.max(total, b.end);
	        for (ProcessEntry p : list) {
	            wt += p.waiting;
	            tat += p.turnaround;
	            ct += p.completion;
	            rt += p.response;
	            resultModel.addRow(new Object[] { p.pid, p.arrival, p.burst,
	                    p.completion, p.turnaround, p.waiting, p.response });
	        }
	        int n = list.size();
	        totalLabel.setText("Total: " + total + " units");
	        tileWait.setValue(wt / n);
	        tileTat.setValue(tat / n);
	        tileCt.setValue(ct / n);
	        tileRt.setValue(rt / n);
	        processTable.repaint();
	    }
	
	    /** Blank Gantt chart with a message, empty tiles, empty result table. */
	    private void showEmpty(String message) {
	        gantt.setEmptyMessage(message);
	        gantt.setBlocks(new ArrayList<GanttBlock>(), colorByPid);
	        totalLabel.setText("Total: 0 units");
	        tileWait.clear();
	        tileTat.clear();
	        tileCt.clear();
	        tileRt.clear();
	        resultModel.setRowCount(0);
	        processTable.repaint();
	    }
	
	    private List<ProcessEntry> readProcesses() {
	        List<ProcessEntry> list = new ArrayList<>();
	        for (int r = 0; r < processModel.getRowCount(); r++) {
	            String pid = String.valueOf(processModel.getValueAt(r, 0));
	            int arrival = Math.max(0, toInt(processModel.getValueAt(r, 1), 0));
	            int burst = Math.max(1, toInt(processModel.getValueAt(r, 2), 1));
	            int priority = toInt(processModel.getValueAt(r, 3), 0);
	            list.add(new ProcessEntry(pid, arrival, burst, priority));
	        }
	        return list;
	    }
	
	    private static int toInt(Object value, int fallback) {
	        try {
	            return Integer.parseInt(String.valueOf(value).trim());
	        } catch (NumberFormatException ex) {
	            return fallback;
	        }
	    }
	
	    // =====================================================================
	    //  STYLING HELPERS
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
	        t.setFont(AppTheme.font(Font.PLAIN, 13));
	        t.setRowHeight(34);
	        t.setGridColor(AppTheme.BORDER);
	        t.setShowVerticalLines(false);
	        t.setIntercellSpacing(new java.awt.Dimension(0, 1));
	        t.setSelectionBackground(AppTheme.CARD_ALT);
	        t.setSelectionForeground(Color.WHITE);
	        t.setFillsViewportHeight(true);
	        t.setPreferredScrollableViewportSize(new java.awt.Dimension(300, 140));
	
	        // numbers centered
	        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
	        center.setHorizontalAlignment(SwingConstants.CENTER);
	        t.setDefaultRenderer(Object.class, center);
	
	        // dark text box when editing a cell
	        JTextField editor = new JTextField();
	        editor.setHorizontalAlignment(SwingConstants.CENTER);
	        editor.setBackground(AppTheme.CARD_ALT);
	        editor.setForeground(Color.WHITE);
	        editor.setCaretColor(Color.WHITE);
	        editor.setBorder(BorderFactory.createLineBorder(AppTheme.ACCENT));
	        t.setDefaultEditor(Object.class, new DefaultCellEditor(editor));
	
	        // header
	        JTableHeader header = t.getTableHeader();
	        header.setReorderingAllowed(false);
	        header.setPreferredSize(new java.awt.Dimension(10, 34));
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
	
	    /** Draws the PID text in that process' own color (matches its Gantt bar). */
	    private class PidRenderer extends DefaultTableCellRenderer {
	        @Override
	        public Component getTableCellRendererComponent(JTable table, Object value,
	                boolean selected, boolean focus, int row, int col) {
	            super.getTableCellRendererComponent(table, value, selected, false, row, col);
	            setHorizontalAlignment(SwingConstants.CENTER);
	            setFont(AppTheme.font(Font.BOLD, 13));
	            setForeground(colorByPid.getOrDefault(String.valueOf(value), AppTheme.TEXT));
	            return this;
	        }
	    }
	}

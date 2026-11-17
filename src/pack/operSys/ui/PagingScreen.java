package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import pack.operSys.logic.PagingEngine;
import pack.operSys.logic.PagingEngine.ProcessSpec;
import pack.operSys.logic.PagingEngine.TranslationStep;
import pack.operSys.model.PagedProcess;

/**
 * The Paging screen:
 * - Left column: Process Table (Reset / Clear, + Add / Remove), Page/Frame size,
 *   Frame count, and Logical Address translator.
 * - Right column: Compact 3-column Paging Diagram (Logical -> Page Table -> Physical)
 *   plus Memory Metrics & full Page Table breakdown.
 */
public class PagingScreen extends JPanel {

    private static final int LEFT_WIDTH = 380;
    private static final String[] PAGE_SIZE_LABELS = { "4 B", "1 KB", "2 KB", "4 KB" };
    private static final int[] PAGE_SIZE_VALUES = { 4, 1, 2, 4 };
    private static final boolean[] PAGE_SIZE_IS_BYTES = { true, false, false, false };

    // ---- data models behind the two tables ----
    private final DefaultTableModel processModel = new DefaultTableModel(
            new Object[] { "PID", "SIZE", "PAGES", "FRAG" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 1;
        }
    };

    private final DefaultTableModel pageTableModel = new DefaultTableModel(
            new Object[] { "PROCESS", "PAGE (p)", "FRAME (f)", "PHYS. BASE", "STATUS" }, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }
    };

    private final JTable processTable = new JTable(processModel);
    private final JTable pageTable = new JTable(pageTableModel);

    // ---- display pieces ----
    private final PagingEngine engine = new PagingEngine();
    private final PagingDiagramCanvas diagramCanvas = new PagingDiagramCanvas();
    private final JLabel capacityLabel = new JLabel("8 Frames · 32 KB Total");
    private final JLabel warningLabel = new JLabel(" ");

    private final StatTile tileFreeFrames = new StatTile("FREE FRAMES");
    private final StatTile tileUsedFrames = new StatTile("FRAMES USED");
    private final StatTile tileFrag = new StatTile("INTERNAL FRAG.");
    private final StatTile tilePageSize = new StatTile("PAGE SIZE");

    // ---- controls ----
    private final List<PillButton> pageSizeButtons = new ArrayList<>();
    private final StepperField frameCountField = new StepperField(8, 4, 32);
    private final JPanel pidPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JTextField logicalAddrField = new JTextField("5000");

    // ---- state ----
    private int selectedSizeIdx = 3; // 4 KB default
    private int pidCounter = 0;
    private String selectedPid = "P1";
    private boolean updatingModels = false;
    private Map<String, Color> colorByPid = new HashMap<>();

    public PagingScreen() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));

        styleTable(processTable);
        styleTable(pageTable);
        processTable.getColumnModel().getColumn(0).setCellRenderer(new PidRenderer());
        pageTable.getColumnModel().getColumn(0).setCellRenderer(new PidRenderer());

        processTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingModels) return;
            int row = processTable.getSelectedRow();
            if (row >= 0) {
                selectedPid = String.valueOf(processModel.getValueAt(row, 0));
                refreshPidPicker();
                runTranslation();
            }
        });

        processModel.addTableModelListener(e -> {
            if (!updatingModels) {
                recalculate(false);
            }
        });

        diagramCanvas.setInteractionListener(new PagingDiagramCanvas.InteractionListener() {
            @Override
            public void onLogicalPageClicked(int pageNumber) {
                int addr = pageNumber * engine.getPageSizeBytes();
                logicalAddrField.setText(String.valueOf(addr));
                runTranslation();
            }

            @Override
            public void onPhysicalFrameClicked(String ownerPid, int pageNumber) {
                selectedPid = ownerPid;
                int addr = pageNumber * engine.getPageSizeBytes();
                logicalAddrField.setText(String.valueOf(addr));
                refreshPidPicker();
                runTranslation();
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

        loadDefaultProcesses();
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

        // --- Top Card: Process Table ---
        RoundedCard processCard = new RoundedCard("Process Table");

        PillButton resetBtn = new PillButton("Reset");
        resetBtn.setSolidColor(AppTheme.ACCENT);
        resetBtn.addActionListener(e -> loadDefaultProcesses());

        PillButton clearBtn = new PillButton("Clear");
        clearBtn.addActionListener(e -> clearAll());

        JPanel topRow = new JPanel(new GridLayout(1, 2, 8, 0));
        topRow.setOpaque(false);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        topRow.add(resetBtn);
        topRow.add(clearBtn);

        PillButton addBtn = new PillButton("+ Add Process");
        addBtn.setSolidColor(AppTheme.ACCENT_DARK);
        addBtn.addActionListener(e -> addProcess());

        PillButton removeBtn = new PillButton("Remove");
        removeBtn.setSolidColor(AppTheme.DANGER);
        removeBtn.addActionListener(e -> removeSelected());

        JPanel bottomRow = new JPanel(new GridLayout(1, 2, 8, 0));
        bottomRow.setOpaque(false);
        bottomRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        bottomRow.add(addBtn);
        bottomRow.add(removeBtn);

        processCard.getBody().add(topRow, BorderLayout.NORTH);
        processCard.getBody().add(wrapScroll(processTable), BorderLayout.CENTER);
        processCard.getBody().add(bottomRow, BorderLayout.SOUTH);

        gc.gridy = 0;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 12, 0);
        col.add(processCard, gc);

        // --- Bottom Card: Paging Configuration & Address Translation ---
        RoundedCard configCard = new RoundedCard("Paging & Address Translation");
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints bg = new GridBagConstraints();
        bg.gridx = 0;
        bg.fill = GridBagConstraints.HORIZONTAL;
        bg.weightx = 1;

        JLabel sizeLbl = new JLabel("Page / Frame Size");
        sizeLbl.setFont(AppTheme.font(Font.BOLD, 12));
        sizeLbl.setForeground(AppTheme.MUTED);
        bg.gridy = 0;
        bg.insets = new Insets(0, 0, 6, 0);
        body.add(sizeLbl, bg);

        JPanel sizeRow = new JPanel(new GridLayout(1, 4, 6, 0));
        sizeRow.setOpaque(false);
        for (int i = 0; i < PAGE_SIZE_LABELS.length; i++) {
            final int idx = i;
            PillButton b = new PillButton(PAGE_SIZE_LABELS[i]);
            b.addActionListener(e -> selectPageSize(idx));
            pageSizeButtons.add(b);
            sizeRow.add(b);
        }
        bg.gridy = 1;
        bg.insets = new Insets(0, 0, 10, 0);
        body.add(sizeRow, bg);

        JPanel framesRow = new JPanel(new BorderLayout());
        framesRow.setOpaque(false);
        JLabel framesLbl = new JLabel("Physical Frames");
        framesLbl.setFont(AppTheme.font(Font.BOLD, 12));
        framesLbl.setForeground(AppTheme.MUTED);
        framesRow.add(framesLbl, BorderLayout.WEST);
        framesRow.add(frameCountField, BorderLayout.EAST);
        frameCountField.addChangeListener(e -> recalculate(true));

        bg.gridy = 2;
        bg.insets = new Insets(0, 0, 10, 0);
        body.add(framesRow, bg);

        JLabel inspectLbl = new JLabel("Inspect Process & Logical Address");
        inspectLbl.setFont(AppTheme.font(Font.BOLD, 12));
        inspectLbl.setForeground(AppTheme.MUTED);
        inspectLbl.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        bg.gridy = 3;
        bg.insets = new Insets(0, 0, 6, 0);
        body.add(inspectLbl, bg);

        pidPickerPanel.setOpaque(false);
        bg.gridy = 4;
        bg.insets = new Insets(0, 0, 8, 0);
        body.add(pidPickerPanel, bg);

        JPanel addrRow = new JPanel(new BorderLayout(8, 0));
        addrRow.setOpaque(false);

        logicalAddrField.setFont(AppTheme.font(Font.BOLD, 13));
        logicalAddrField.setHorizontalAlignment(SwingConstants.CENTER);
        logicalAddrField.setBackground(AppTheme.CARD_ALT);
        logicalAddrField.setForeground(AppTheme.TEXT);
        logicalAddrField.setCaretColor(Color.WHITE);
        logicalAddrField.setPreferredSize(new Dimension(140, 32));
        logicalAddrField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        logicalAddrField.addActionListener(e -> runTranslation());

        PillButton translateBtn = new PillButton("Translate");
        translateBtn.setSolidColor(AppTheme.ACCENT);
        translateBtn.addActionListener(e -> runTranslation());

        addrRow.add(logicalAddrField, BorderLayout.CENTER);
        addrRow.add(translateBtn, BorderLayout.EAST);

        bg.gridy = 5;
        bg.insets = new Insets(0, 0, 0, 0);
        body.add(addrRow, bg);

        configCard.getBody().add(body, BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(configCard, gc);
        return col;
    }

    private JComponent buildRightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- Top Card: Compact 3-Column Paging Diagram ---
        RoundedCard diagramCard = new RoundedCard("Paging Model of Logical and Physical Memory");
        capacityLabel.setForeground(AppTheme.MUTED);
        capacityLabel.setFont(AppTheme.font(Font.PLAIN, 12));
        diagramCard.setHeaderRight(capacityLabel);
        diagramCard.getBody().add(diagramCanvas, BorderLayout.CENTER);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 12, 0);
        col.add(diagramCard, gc);

        // --- Bottom Card: Compact Metrics & Full Page Table ---
        RoundedCard bottomCard = new RoundedCard("Page Table & Memory Metrics");
        warningLabel.setForeground(AppTheme.DANGER);
        warningLabel.setFont(AppTheme.font(Font.BOLD, 12));
        bottomCard.setHeaderRight(warningLabel);

        JPanel tiles = new JPanel(new GridLayout(1, 4, 10, 0));
        tiles.setOpaque(false);
        tiles.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        tiles.add(tileFreeFrames);
        tiles.add(tileUsedFrames);
        tiles.add(tileFrag);
        tiles.add(tilePageSize);

        bottomCard.getBody().add(tiles, BorderLayout.NORTH);
        bottomCard.getBody().add(wrapScroll(pageTable), BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(bottomCard, gc);
        return col;
    }

    // =====================================================================
    //  ACTIONS
    // =====================================================================

    private void loadDefaultProcesses() {
        stopEditing();
        updatingModels = true;
        pidCounter = 0;
        processModel.setRowCount(0);
        int[] sizes = { 10, 6, 8 }; // KB
        for (int s : sizes) {
            processModel.addRow(new Object[] { "P" + (++pidCounter), s, 0, 0 });
        }
        frameCountField.setValue(8);
        selectedSizeIdx = 3; // 4 KB
        updateSizeButtons();
        updatingModels = false;

        selectedPid = "P1";
        logicalAddrField.setText("5000");
        recalculate(true);
    }

    private void clearAll() {
        stopEditing();
        updatingModels = true;
        pidCounter = 0;
        processModel.setRowCount(0);
        updatingModels = false;
        selectedPid = null;
        recalculate(true);
    }

    private void addProcess() {
        stopEditing();
        updatingModels = true;
        String pid = "P" + (++pidCounter);
        int defaultSize = PAGE_SIZE_VALUES[selectedSizeIdx] * 2;
        processModel.addRow(new Object[] { pid, defaultSize, 0, 0 });
        updatingModels = false;
        if (selectedPid == null) selectedPid = pid;
        recalculate(false);
    }

    private void removeSelected() {
        stopEditing();
        int[] rows = processTable.getSelectedRows();
        if (rows.length == 0) return;

        updatingModels = true;
        for (int i = rows.length - 1; i >= 0; i--) {
            processModel.removeRow(rows[i]);
        }
        updatingModels = false;
        recalculate(false);
    }

    private void selectPageSize(int idx) {
        selectedSizeIdx = idx;
        updateSizeButtons();
        recalculate(true);
    }

    private void updateSizeButtons() {
        for (int i = 0; i < pageSizeButtons.size(); i++) {
            pageSizeButtons.get(i).setActive(i == selectedSizeIdx);
        }
    }

    private void stopEditing() {
        if (processTable.isEditing()) {
            processTable.getCellEditor().stopCellEditing();
        }
    }

    private void recalculate(boolean rebuildFrames) {
        List<ProcessSpec> specs = readProcessSpecs();
        int pageSizeVal = PAGE_SIZE_VALUES[selectedSizeIdx];
        boolean isBytes = PAGE_SIZE_IS_BYTES[selectedSizeIdx];
        int totalFrames = frameCountField.getValue();

        if (rebuildFrames) {
            engine.resetMemory(pageSizeVal, isBytes, totalFrames);
        }
        engine.syncProcesses(specs);
        refreshDisplayFromEngine();
    }

    private void refreshDisplayFromEngine() {
        List<PagedProcess> processes = engine.getProcesses();
        colorByPid = new HashMap<>();
        for (int i = 0; i < processes.size(); i++) {
            colorByPid.put(processes.get(i).pid, AppTheme.processColor(i));
        }

        String unit = engine.getUnitLabel();
        int pageBytes = engine.getPageSizeBytes();

        updatingModels = true;
        for (int r = 0; r < processModel.getRowCount(); r++) {
            String pid = String.valueOf(processModel.getValueAt(r, 0));
            PagedProcess proc = engine.getProcess(pid);
            if (proc != null) {
                processModel.setValueAt(proc.sizeUnits, r, 1);
                processModel.setValueAt(proc.numPages, r, 2);
                processModel.setValueAt(proc.allocated ? proc.internalFrag + " " + unit : "N/A", r, 3);
            }
        }

        pageTableModel.setRowCount(0);
        for (PagedProcess proc : processes) {
            for (int p = 0; p < proc.numPages; p++) {
                int f = proc.pageTable[p];
                if (proc.allocated && f >= 0) {
                    pageTableModel.addRow(new Object[] {
                        proc.pid, p, f, (f * pageBytes) + " B", "Valid (1)"
                    });
                } else {
                    pageTableModel.addRow(new Object[] {
                        proc.pid, p, "--", "--", "Invalid (0)"
                    });
                }
            }
        }
        updatingModels = false;

        capacityLabel.setText(engine.getTotalFrames() + " Frames · "
                + engine.getTotalMemoryUnits() + " " + unit + " Physical Memory");
        warningLabel.setText(engine.getWarningMessage().isEmpty() ? " " : engine.getWarningMessage());

        int free = engine.getFreeFramesCount();
        int used = engine.getUsedFramesCount();
        int total = engine.getTotalFrames();

        tileFreeFrames.setTextValue(free + " / " + total, total > 0 ? (double) free / total : 0.0);
        tileUsedFrames.setTextValue(used + " / " + total, total > 0 ? (double) used / total : 0.0);
        tileFrag.setTextValue(engine.getTotalInternalFrag() + " " + unit, 1.0);
        tilePageSize.setTextValue(engine.getPageSizeUnits() + " " + unit, 1.0);

        if (selectedPid == null || engine.getProcess(selectedPid) == null) {
            selectedPid = processes.isEmpty() ? null : processes.get(0).pid;
        }

        refreshPidPicker();
        runTranslation();
        processTable.repaint();
        pageTable.repaint();
    }

    private void refreshPidPicker() {
        pidPickerPanel.removeAll();
        List<PagedProcess> list = engine.getProcesses();
        if (list.isEmpty()) {
            JLabel none = new JLabel("No active processes");
            none.setFont(AppTheme.font(Font.PLAIN, 12));
            none.setForeground(AppTheme.MUTED);
            pidPickerPanel.add(none);
        } else {
            for (PagedProcess p : list) {
                PillButton b = new PillButton(p.pid);
                b.setActive(p.pid.equals(selectedPid));
                b.addActionListener(e -> {
                    selectedPid = p.pid;
                    refreshPidPicker();
                    runTranslation();
                });
                pidPickerPanel.add(b);
            }
        }
        pidPickerPanel.revalidate();
        pidPickerPanel.repaint();
    }

    private void runTranslation() {
        if (selectedPid == null) {
            diagramCanvas.setState(engine, null, colorByPid, null);
            return;
        }
        int logicalAddr = Math.max(0, toInt(logicalAddrField.getText(), 0));
        TranslationStep step = engine.translate(selectedPid, logicalAddr);
        diagramCanvas.setState(engine, selectedPid, colorByPid, step);
    }

    private List<ProcessSpec> readProcessSpecs() {
        List<ProcessSpec> list = new ArrayList<>();
        for (int r = 0; r < processModel.getRowCount(); r++) {
            String pid = String.valueOf(processModel.getValueAt(r, 0));
            int size = Math.max(1, Math.min(256, toInt(processModel.getValueAt(r, 1), 4)));
            list.add(new ProcessSpec(pid, size));
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
    //  STYLING HELPERS & SYSTEM-STATE STYLE METRIC TILE
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
        t.setRowHeight(28);
        t.setGridColor(AppTheme.BORDER);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(AppTheme.CARD_ALT);
        t.setSelectionForeground(Color.WHITE);
        t.setFillsViewportHeight(true);
        t.setPreferredScrollableViewportSize(new Dimension(300, 180));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
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

    private class PidRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, false, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(AppTheme.font(Font.BOLD, 12));
            setForeground(colorByPid.getOrDefault(String.valueOf(value), AppTheme.TEXT));
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
            setBorder(BorderFactory.createEmptyBorder(7, 14, 13, 14));
            setPreferredSize(new Dimension(110, 56));
            setMinimumSize(new Dimension(90, 54));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AppTheme.font(Font.BOLD, 10));
            titleLabel.setForeground(AppTheme.MUTED);

            valueLabel.setFont(AppTheme.font(Font.BOLD, 16));
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

            // Bottom teal accent bar
            int barX = 14;
            int barY = h - 9;
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
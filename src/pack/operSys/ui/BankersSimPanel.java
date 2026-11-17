package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import pack.operSys.logic.BankersEngine;
import pack.operSys.model.BankersState;

/**
 * The Banker's simulation view.
 *   left : Resource Request (request / release) + Safety Algorithm (run + step log)
 *   right: System State (available resources) + the Allocation / Maximum / Need table
 */
public class BankersSimPanel extends JPanel {

    private final Runnable onChangeSystem;
    private BankersState state;

    // ---- Resource Request card ----
    private final PillButton requestTab = new PillButton("Request");
    private final PillButton releaseTab = new PillButton("Release");
    private final ModernComboBox<String> processBox = new ModernComboBox<>();
    private final JPanel inputsPanel = new JPanel();
    private NumberBox[] inputBoxes = new NumberBox[0];
    private final PillButton actionBtn = new PillButton("Request resources");
    private final JTextArea messageArea = new JTextArea(2, 20);
    private boolean releaseMode = false;

    // ---- Safety Algorithm card ----
    private final PillButton runBtn = new PillButton("\u25B6  Run Safety Algorithm");
    private final JLabel verdictLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();

    // ---- System State / table ----
    private final JLabel subtitle = new JLabel();
    private final ChipLabel workChip = new ChipLabel("work = []");
    private final ChipLabel safeBadge = new ChipLabel("SAFE");
    private final JPanel tilesPanel = new JPanel();
    private final BankersGrid grid = new BankersGrid();

    // ---- safety run (revealed one step at a time) ----
    private javax.swing.Timer runTimer;
    private BankersEngine.SafetyResult run;
    private int revealed;
    private int[] orderPos = new int[0];
    private int highlight = -1;

    public BankersSimPanel(Runnable onChangeSystem) {
        this.onChangeSystem = onChangeSystem;
        BankersScreen.twoColumns(this, buildLeftColumn(), buildRightColumn());
    }

    // =====================================================================
    //  LAYOUT
    // =====================================================================

    private JComponent buildLeftColumn() {
        JPanel col = BankersScreen.leftColumn();
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- Resource Request card ---
        RoundedCard requestCard = new RoundedCard("Resource Request");

        JPanel tabs = new JPanel(new GridLayout(1, 2, 6, 0));
        tabs.setOpaque(false);
        tabs.add(requestTab);
        tabs.add(releaseTab);
        requestTab.addActionListener(e -> setMode(false));
        releaseTab.addActionListener(e -> setMode(true));

        inputsPanel.setOpaque(false);
        actionBtn.setSolidColor(AppTheme.ACCENT);
        actionBtn.addActionListener(e -> onAction());

        messageArea.setEditable(false);
        messageArea.setOpaque(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(AppTheme.font(Font.PLAIN, 12));
        messageArea.setForeground(AppTheme.MUTED);

        JPanel form = requestCard.getBody();
        form.setLayout(new GridBagLayout());
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 0;
        f.weightx = 1;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.anchor = GridBagConstraints.NORTHWEST;
        int row = 0;
        f.gridy = row++; f.insets = new Insets(0, 0, 12, 0); form.add(tabs, f);
        f.gridy = row++; f.insets = new Insets(0, 0, 4, 0);  form.add(caption("Process"), f);
        f.gridy = row++; f.insets = new Insets(0, 0, 12, 0); form.add(processBox, f);
        f.gridy = row++; f.insets = new Insets(0, 0, 4, 0);  form.add(caption("Resources"), f);
        f.gridy = row++; f.insets = new Insets(0, 0, 14, 0); form.add(inputsPanel, f);
        f.gridy = row++; f.insets = new Insets(0, 0, 10, 0); form.add(actionBtn, f);
        f.gridy = row++; f.insets = new Insets(0, 0, 0, 0);  f.weighty = 1; f.fill = GridBagConstraints.BOTH;
        form.add(messageArea, f);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 16, 0);
        col.add(requestCard, gc);

        // --- Safety Algorithm card ---
        RoundedCard safetyCard = new RoundedCard("Safety Algorithm");
        runBtn.setSolidColor(AppTheme.ACCENT);
        runBtn.addActionListener(e -> runSafety());

        verdictLabel.setFont(AppTheme.font(Font.BOLD, 13));
        verdictLabel.setForeground(AppTheme.MUTED);
        verdictLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));

        logArea.setEditable(false);
        logArea.setOpaque(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setForeground(AppTheme.MUTED);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.setOpaque(false);
        logScroll.getViewport().setOpaque(false);
        ModernScrollBarUI.style(logScroll);
        logScroll.setPreferredSize(new Dimension(100, 90));

        JPanel safetyTop = new JPanel(new BorderLayout());
        safetyTop.setOpaque(false);
        safetyTop.add(runBtn, BorderLayout.NORTH);
        safetyTop.add(verdictLabel, BorderLayout.CENTER);
        safetyCard.getBody().add(safetyTop, BorderLayout.NORTH);
        safetyCard.getBody().add(logScroll, BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 16, 0);
        col.add(safetyCard, gc);

        // --- back to setup ---
        PillButton changeBtn = new PillButton("Change or create a system  \u2192");
        changeBtn.addActionListener(e -> {
            cancelRun();
            onChangeSystem.run();
        });
        gc.gridy = 2;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(changeBtn, gc);
        return col;
    }

    private JComponent buildRightColumn() {
        JPanel col = BankersScreen.rightColumn();
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;

        // --- System State card ---
        RoundedCard stateCard = new RoundedCard("System State");
        stateCard.setHeaderRight(workChip);

        subtitle.setFont(AppTheme.font(Font.PLAIN, 12));
        subtitle.setForeground(AppTheme.MUTED);

        JLabel availCaption = new JLabel("AVAILABLE RESOURCES");
        availCaption.setFont(AppTheme.font(Font.BOLD, 11));
        availCaption.setForeground(AppTheme.MUTED);
        JPanel badgeBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeBox.setOpaque(false);
        badgeBox.add(safeBadge);

        JPanel availRow = new JPanel(new BorderLayout());
        availRow.setOpaque(false);
        availRow.setBorder(BorderFactory.createEmptyBorder(14, 0, 10, 0));
        availRow.add(availCaption, BorderLayout.WEST);
        availRow.add(badgeBox, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(subtitle, BorderLayout.NORTH);
        north.add(availRow, BorderLayout.CENTER);

        tilesPanel.setOpaque(false);
        stateCard.getBody().add(north, BorderLayout.NORTH);
        stateCard.getBody().add(tilesPanel, BorderLayout.CENTER);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 16, 0);
        col.add(stateCard, gc);

        // --- table card ---
        RoundedCard tableCard = new RoundedCard("Allocation Table");
        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(AppTheme.CARD);
        sp.setBackground(AppTheme.CARD);
        ModernScrollBarUI.style(sp);
        sp.setPreferredSize(new Dimension(420, 200));
        tableCard.getBody().add(sp, BorderLayout.CENTER);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(tableCard, gc);
        return col;
    }

    private static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.font(Font.BOLD, 12));
        l.setForeground(AppTheme.MUTED);
        return l;
    }

    // =====================================================================
    //  LOADING A SYSTEM
    // =====================================================================

    /** Shows a (new) system: rebuilds everything that depends on its size. */
    public void setSystem(BankersState newState) {
        cancelRun();
        state = newState;
        orderPos = new int[state.processCount];

        processBox.removeAllItems();
        for (int p = 0; p < state.processCount; p++) processBox.addItem("P" + p);

        rebuildInputs();
        rebuildTiles();
        setMode(false);
        refresh();
    }

    private void rebuildInputs() {
        int m = state.resourceCount;
        inputsPanel.removeAll();
        inputsPanel.setLayout(new GridLayout(1, m, 8, 0));
        inputBoxes = new NumberBox[m];
        for (int r = 0; r < m; r++) {
            inputBoxes[r] = new NumberBox(0);
            JLabel l = new JLabel("R" + r, SwingConstants.CENTER);
            l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            l.setForeground(AppTheme.MUTED);
            JPanel cell = new JPanel(new BorderLayout(0, 4));
            cell.setOpaque(false);
            cell.add(l, BorderLayout.NORTH);
            cell.add(inputBoxes[r], BorderLayout.CENTER);
            inputsPanel.add(cell);
        }
        inputsPanel.revalidate();
        inputsPanel.repaint();
    }

    private ResourceTile[] tiles = new ResourceTile[0];

    private void rebuildTiles() {
        int m = state.resourceCount;
        tilesPanel.removeAll();
        tilesPanel.setLayout(new GridLayout(1, m, 12, 0));
        tiles = new ResourceTile[m];
        for (int r = 0; r < m; r++) {
            tiles[r] = new ResourceTile();
            tilesPanel.add(tiles[r]);
        }
        tilesPanel.revalidate();
        tilesPanel.repaint();
    }

    // =====================================================================
    //  REQUEST / RELEASE
    // =====================================================================

    private void setMode(boolean release) {
        releaseMode = release;
        requestTab.setActive(!release);
        releaseTab.setActive(release);
        actionBtn.setText(release ? "Release resources" : "Request resources");
        showHint();
    }

    private void showHint() {
        messageArea.setForeground(AppTheme.MUTED);
        messageArea.setText(releaseMode
                ? "Choose a process and how much of each resource it gives back."
                : "Choose a process and how much of each resource it is asking for.");
    }

    private void onAction() {
        cancelRun();
        int p = processBox.getSelectedIndex();
        if (p < 0) return;

        int[] amounts = new int[state.resourceCount];
        for (int r = 0; r < amounts.length; r++) amounts[r] = inputBoxes[r].getIntValue();

        BankersEngine.RequestResult result = releaseMode
                ? BankersEngine.release(state, p, amounts)
                : BankersEngine.request(state, p, amounts);

        switch (result.outcome) {
            case GRANTED:    messageArea.setForeground(AppTheme.ACCENT); break;
            case MUST_WAIT:  messageArea.setForeground(BankersScreen.AMBER); break;
            default:         messageArea.setForeground(AppTheme.DANGER); break;
        }
        messageArea.setText(result.message);

        if (result.granted()) {
            for (NumberBox b : inputBoxes) b.setIntValue(0);
        }
        refresh();
    }

    // =====================================================================
    //  SAFETY ALGORITHM (revealed one step at a time)
    // =====================================================================

    private void runSafety() {
        cancelRun();
        run = BankersEngine.checkSafety(state);
        revealed = 0;
        logArea.setText("Work = Available = " + BankersEngine.compact(state.available) + "\n");
        verdictLabel.setForeground(AppTheme.MUTED);
        verdictLabel.setText("Running...");

        if (run.steps.isEmpty()) {
            finishRun();
            return;
        }
        runTimer = new javax.swing.Timer(700, e -> advanceRun());
        runTimer.setInitialDelay(250);
        runTimer.start();
    }

    private void advanceRun() {
        BankersEngine.Step step = run.steps.get(revealed);
        revealed++;
        orderPos[step.process] = revealed;
        highlight = step.process;

        logArea.append(revealed + ". P" + step.process + ": need " + BankersEngine.compact(step.need)
                + " <= work " + BankersEngine.compact(step.workBefore)
                + " -> " + BankersEngine.compact(step.workAfter) + "\n");
        workChip.setText("work = " + BankersEngine.format(step.workAfter));
        grid.setData(state, orderPos, highlight);

        if (revealed >= run.steps.size()) {
            runTimer.stop();
            finishRun();
        }
    }

    private void finishRun() {
        highlight = -1;
        grid.setData(state, orderPos, highlight);
        workChip.setText("work = " + BankersEngine.format(run.finalWork));

        if (run.safe) {
            verdictLabel.setForeground(AppTheme.ACCENT);
            verdictLabel.setText("SAFE  " + run.sequenceText());
            logArea.append("All processes can finish: safe sequence.\n");
        } else {
            StringBuilder stuck = new StringBuilder();
            for (int p : run.unfinished) {
                if (stuck.length() > 0) stuck.append(", ");
                stuck.append('P').append(p);
            }
            verdictLabel.setForeground(AppTheme.DANGER);
            verdictLabel.setText("UNSAFE  no safe sequence exists");
            logArea.append("Stuck: " + stuck + " need more than work "
                    + BankersEngine.compact(run.finalWork) + ".\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** Stops a running safety check and clears its badges, log and verdict. */
    private void cancelRun() {
        if (runTimer != null) {
            runTimer.stop();
            runTimer = null;
        }
        run = null;
        revealed = 0;
        highlight = -1;
        if (state != null) orderPos = new int[state.processCount];
        verdictLabel.setForeground(AppTheme.MUTED);
        verdictLabel.setText("Press Run to check the current state.");
        logArea.setText("");
    }

    // =====================================================================
    //  REFRESH
    // =====================================================================

    private void refresh() {
        int[] total = state.totalInstances();
        subtitle.setText(state.processCount + " processes \u00B7 " + state.resourceCount + " resource types");
        for (int r = 0; r < tiles.length; r++) tiles[r].setValues("R" + r, state.available[r], total[r]);

        boolean safe = BankersEngine.checkSafety(state).safe;
        Color tone = safe ? AppTheme.ACCENT : AppTheme.DANGER;
        safeBadge.setText(safe ? "SAFE" : "UNSAFE");
        safeBadge.setTone(ChipLabel.tint(tone, 40), tone, ChipLabel.tint(tone, 130));

        if (run == null) workChip.setText("work = " + BankersEngine.format(state.available));
        grid.setData(state, orderPos, highlight);
    }

    // =====================================================================
    //  One "R0  3  of 10" tile with a progress bar
    // =====================================================================

    private static class ResourceTile extends JComponent {
        private String name = "R0";
        private int available;
        private int total;

        ResourceTile() {
            setPreferredSize(new Dimension(120, 92));
        }

        void setValues(String name, int available, int total) {
            this.name = name;
            this.available = available;
            this.total = total;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(AppTheme.CARD_ALT);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
            g2.setColor(AppTheme.BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);

            g2.setColor(AppTheme.MUTED);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            g2.drawString(name, 14, 22);
            String of = "of " + total;
            g2.drawString(of, w - 14 - g2.getFontMetrics().stringWidth(of), 22);

            g2.setColor(AppTheme.TEXT);
            g2.setFont(AppTheme.font(Font.BOLD, 28));
            g2.drawString(String.valueOf(available), 14, 56);

            int barX = 14;
            int barY = h - 20;
            int barW = w - 28;
            g2.setColor(AppTheme.BORDER);
            g2.fillRoundRect(barX, barY, barW, 6, 6, 6);
            if (total > 0 && available > 0) {
                int fillW = Math.max(6, (int) Math.round(barW * Math.min(1.0, available / (double) total)));
                g2.setColor(AppTheme.ACCENT);
                g2.fillRoundRect(barX, barY, fillW, 6, 6, 6);
            }
            g2.dispose();
        }
    }
}

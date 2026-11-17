package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

import pack.operSys.model.BankersState;

/**
 * The Banker's setup view: create or change the system.
 *   left : Current System summary + "How it works"
 *   right: Define System form (counts, Available, Maximum claim, Current allocation)
 */
public class BankersSetupPanel extends JPanel {

    private final Consumer<BankersState> onCreate;
    private BankersState current;
    private boolean loading = false;          // true while the form is being filled from code

    // ---- Current System card ----
    private final JLabel processesBig = bigNumber();
    private final JLabel resourcesBig = bigNumber();
    private final JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    // ---- Define System form ----
    private final StepperField processCount = new StepperField(5, 1, 10);
    private final StepperField resourceCount = new StepperField(3, 1, 5);
    private final MatrixEditor availableEditor = new MatrixEditor(false);
    private final MatrixEditor maxEditor = new MatrixEditor(true);
    private final MatrixEditor allocEditor = new MatrixEditor(true);
    private final JTextArea errorArea = new JTextArea(2, 20);

    public BankersSetupPanel(Consumer<BankersState> onCreate) {
        this.onCreate = onCreate;
        BankersScreen.twoColumns(this, buildLeftColumn(), buildRightColumn());

        processCount.addChangeListener(e -> { if (!loading) resizeForm(); });
        resourceCount.addChangeListener(e -> { if (!loading) resizeForm(); });
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

        // --- Current System ---
        RoundedCard currentCard = new RoundedCard("Current System");
        JPanel stats = new JPanel(new GridLayout(1, 2, 12, 0));
        stats.setOpaque(false);
        stats.add(statBlock(processesBig, "Processes"));
        stats.add(statBlock(resourcesBig, "Resource types"));

        JLabel totalCaption = new JLabel("Total instances");
        totalCaption.setFont(AppTheme.font(Font.BOLD, 12));
        totalCaption.setForeground(AppTheme.MUTED);
        chipsPanel.setOpaque(false);
        JPanel totals = new JPanel(new BorderLayout(0, 8));
        totals.setOpaque(false);
        totals.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        totals.add(totalCaption, BorderLayout.NORTH);
        totals.add(chipsPanel, BorderLayout.CENTER);

        currentCard.getBody().add(stats, BorderLayout.NORTH);
        currentCard.getBody().add(totals, BorderLayout.CENTER);

        gc.gridy = 0;
        gc.weighty = 0;
        gc.insets = new Insets(0, 0, 16, 0);
        col.add(currentCard, gc);

        // --- How it works ---
        RoundedCard howCard = new RoundedCard("How It Works");
        String[] steps = {
            "<b>Need</b> = Maximum - Allocation, for every process.",
            "Start with <b>Work = Available</b>. Find an unfinished process whose Need &lt;= Work.",
            "Pretend it finishes: <b>Work += its Allocation</b>. Repeat until nothing more can finish.",
            "If every process finished, the state is <b>safe</b> and the finish order is a <b>safe sequence</b>.",
            "A request is granted only if it fits the <b>Need</b>, fits <b>Available</b>, and leaves the system safe."
        };
        JPanel list = new JPanel(new GridBagLayout());
        list.setOpaque(false);
        GridBagConstraints s = new GridBagConstraints();
        s.gridx = 0;
        s.weightx = 1;
        s.fill = GridBagConstraints.HORIZONTAL;
        s.insets = new Insets(0, 0, 14, 0);
        for (int i = 0; i < steps.length; i++) {
            JPanel dotBox = new JPanel(new BorderLayout());     // keeps the circle at the top-left
            dotBox.setOpaque(false);
            dotBox.add(new NumberDot(i + 1), BorderLayout.NORTH);

            JPanel stepRow = new JPanel(new BorderLayout(12, 0));
            stepRow.setOpaque(false);
            stepRow.add(dotBox, BorderLayout.WEST);              // WEST always keeps its full 24px
            stepRow.add(stepText(steps[i]), BorderLayout.CENTER);

            s.gridy = i;
            list.add(stepRow, s);
        }
        howCard.getBody().add(list, BorderLayout.NORTH);

        gc.gridy = 1;
        gc.weighty = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        col.add(howCard, gc);
        return col;
    }

    private JComponent buildRightColumn() {
        JPanel col = BankersScreen.rightColumn();
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 1;

        RoundedCard card = new RoundedCard("Define System");

        // --- counts row ---
        JPanel counts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        counts.setOpaque(false);
        counts.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        counts.add(caption("Processes"));
        counts.add(processCount);
        JLabel gap = new JLabel("   ");
        counts.add(gap);
        counts.add(caption("Resource types"));
        counts.add(resourceCount);

        // --- form (Available + two matrices) ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints f = new GridBagConstraints();
        f.anchor = GridBagConstraints.NORTHWEST;
        f.gridx = 0;
        f.gridwidth = 2;
        f.gridy = 0;
        f.insets = new Insets(0, 0, 2, 0);
        formPanel.add(caption("Available"), f);
        f.gridy = 1;
        f.insets = new Insets(0, 0, 18, 0);
        formPanel.add(leftAligned(availableEditor), f);

        f.gridwidth = 1;
        f.gridy = 2;
        f.weightx = 0.5;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.insets = new Insets(0, 0, 0, 24);
        formPanel.add(matrixBlock("Maximum claim", maxEditor), f);
        f.gridx = 1;
        f.insets = new Insets(0, 0, 0, 0);
        formPanel.add(matrixBlock("Current allocation", allocEditor), f);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(formPanel, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrapper);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        ModernScrollBarUI.style(sp);

        // --- footer: message + buttons ---
        errorArea.setEditable(false);
        errorArea.setOpaque(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setFont(AppTheme.font(Font.PLAIN, 12));
        errorArea.setForeground(AppTheme.DANGER);

        PillButton resetBtn = new PillButton("Reset to current");
        resetBtn.addActionListener(e -> {
            clearError();
            loadForm(current);
        });
        PillButton createBtn = new PillButton("Create system");
        createBtn.setSolidColor(AppTheme.ACCENT);
        createBtn.addActionListener(e -> createSystem());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(resetBtn);
        buttons.add(createBtn);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        footer.add(errorArea, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);

        card.getBody().add(counts, BorderLayout.NORTH);
        card.getBody().add(sp, BorderLayout.CENTER);
        card.getBody().add(footer, BorderLayout.SOUTH);

        col.add(card, gc);
        return col;
    }

    // =====================================================================
    //  FORM LOGIC
    // =====================================================================

    /** Called by BankersScreen: remembers the live system, updates the summary, fills the form. */
    public void setCurrent(BankersState state) {
        current = state;
        processesBig.setText(String.valueOf(state.processCount));
        resourcesBig.setText(String.valueOf(state.resourceCount));

        chipsPanel.removeAll();
        int[] total = state.totalInstances();
        for (int r = 0; r < total.length; r++) chipsPanel.add(new ChipLabel("R" + r + "  " + total[r]));
        chipsPanel.revalidate();
        chipsPanel.repaint();

        clearError();
        loadForm(state);
    }

    private void loadForm(BankersState s) {
        loading = true;
        processCount.setValue(s.processCount);
        resourceCount.setValue(s.resourceCount);
        loading = false;

        resizeForm();
        availableEditor.setValues(new int[][] { s.available });
        maxEditor.setValues(s.max);
        allocEditor.setValues(s.allocation);
    }

    /** Rebuilds the three grids to match the two counts (existing numbers are kept). */
    private void resizeForm() {
        int n = processCount.getValue();
        int m = resourceCount.getValue();
        availableEditor.setGridSize(1, m);
        maxEditor.setGridSize(n, m);
        allocEditor.setGridSize(n, m);
    }

    private void createSystem() {
        int n = processCount.getValue();
        int m = resourceCount.getValue();
        int[] avail = availableEditor.getValues()[0];
        int[][] mx = maxEditor.getValues();
        int[][] al = allocEditor.getValues();

        for (int p = 0; p < n; p++) {
            for (int r = 0; r < m; r++) {
                if (al[p][r] > mx[p][r]) {
                    showError("P" + p + ", R" + r + ": the allocation (" + al[p][r]
                            + ") is bigger than the maximum claim (" + mx[p][r]
                            + "). A process cannot hold more than it may ever ask for.");
                    return;
                }
            }
        }

        BankersState created = new BankersState(n, m);
        System.arraycopy(avail, 0, created.available, 0, m);
        for (int p = 0; p < n; p++) {
            System.arraycopy(mx[p], 0, created.max[p], 0, m);
            System.arraycopy(al[p], 0, created.allocation[p], 0, m);
        }
        clearError();
        onCreate.accept(created);
    }

    private void showError(String message) {
        errorArea.setText(message);
    }

    private void clearError() {
        errorArea.setText("");
    }

    // =====================================================================
    //  SMALL BUILDING BLOCKS
    // =====================================================================

    private static JLabel bigNumber() {
        JLabel l = new JLabel("0");
        l.setFont(AppTheme.font(Font.BOLD, 30));
        l.setForeground(AppTheme.TEXT);
        return l;
    }

    private static JPanel statBlock(JLabel big, String captionText) {
        JLabel c = new JLabel(captionText);
        c.setFont(AppTheme.font(Font.PLAIN, 12));
        c.setForeground(AppTheme.MUTED);
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.add(big, BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppTheme.font(Font.BOLD, 12));
        l.setForeground(AppTheme.MUTED);
        return l;
    }

    private static JPanel leftAligned(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private static JPanel matrixBlock(String title, MatrixEditor editor) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(caption(title), BorderLayout.NORTH);
        p.add(leftAligned(editor), BorderLayout.CENTER);
        return p;
    }

    /** One "How it works" sentence. Bold parts are highlighted. */
    private static JLabel stepText(String html) {
        String strong = String.format("#%06X", AppTheme.TEXT.getRGB() & 0xFFFFFF);
        html = html.replace("<b>", "<b><font color='" + strong + "'>").replace("</b>", "</font></b>");
        JLabel l = new JLabel("<html><body style='width: 230px'>" + html + "</body></html>");
        l.setFont(AppTheme.font(Font.PLAIN, 12));
        l.setForeground(AppTheme.MUTED);
        l.setVerticalAlignment(SwingConstants.TOP);
        l.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        return l;
    }

    /** The numbered circle next to each step. */
    private static class NumberDot extends JComponent {
        private final int number;

        NumberDot(int number) {
            this.number = number;
            setPreferredSize(new Dimension(24, 24));
            setMinimumSize(new Dimension(24, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(AppTheme.ACCENT_DARK);
            g2.fillOval(0, 0, 23, 23);
            g2.setColor(Color.WHITE);
            g2.setFont(AppTheme.font(Font.BOLD, 12));
            String t = String.valueOf(number);
            g2.drawString(t, (24 - g2.getFontMetrics().stringWidth(t)) / 2, 16);
            g2.dispose();
        }
    }
}

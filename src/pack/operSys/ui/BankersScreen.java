package pack.operSys.ui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import pack.operSys.model.BankersState;

/**
 * The whole Banker's Algorithm module. Two views share this panel:
 *   "sim"   - request / release resources and run the safety algorithm
 *   "setup" - create or change the system (processes, resources, max claims, allocation)
 * It starts on the simulation with the classic textbook system already loaded.
 */
public class BankersScreen extends JPanel {

    /** Same width as the left column of the CPU Scheduling screen. */
    static final int LEFT_WIDTH = 380;
    static final Color AMBER = new Color(0xE0B04F);

    private final CardLayout cards = new CardLayout();
    private final BankersSimPanel sim;
    private final BankersSetupPanel setup;
    private BankersState current = BankersState.sample();

    public BankersScreen() {
        setOpaque(false);
        setLayout(cards);

        sim = new BankersSimPanel(this::showSetup);
        setup = new BankersSetupPanel(this::systemCreated);
        sim.setSystem(current);
        setup.setCurrent(current);

        add(sim, "sim");
        add(setup, "setup");
        cards.show(this, "sim");
    }

    private void showSetup() {
        setup.setCurrent(current);      // the form starts out filled with the live system
        cards.show(this, "setup");
    }

    private void systemCreated(BankersState created) {
        current = created;
        sim.setSystem(created);
        cards.show(this, "sim");
    }

    // ------------------------------------------------------------------
    //  Layout helpers shared by both views (same look as SchedulerScreen)
    // ------------------------------------------------------------------

    /** A column that is always LEFT_WIDTH wide. */
    static JPanel leftColumn() {
        JPanel col = new JPanel(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() {
                return new Dimension(LEFT_WIDTH, super.getPreferredSize().height);
            }
            @Override public Dimension getMinimumSize() {
                return new Dimension(LEFT_WIDTH, super.getMinimumSize().height);
            }
        };
        col.setOpaque(false);
        return col;
    }

    static JPanel rightColumn() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setOpaque(false);
        return col;
    }

    /** Puts the fixed-width left column and the stretchy right column side by side. */
    static void twoColumns(JPanel host, JComponent left, JComponent right) {
        host.setOpaque(false);
        host.setLayout(new GridBagLayout());
        host.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;

        gc.gridx = 0;
        gc.weightx = 0;
        gc.insets = new Insets(0, 0, 0, 16);
        host.add(left, gc);

        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        host.add(right, gc);
    }
}

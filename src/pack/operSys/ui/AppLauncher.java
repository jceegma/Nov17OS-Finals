package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * RUN THIS FILE.  (Right-click -> Run As -> Java Application)
 *
 * Main window: a top navigation bar (teal highlight on the active tab)
 * + one screen per OS module.
 */
public class AppLauncher extends JFrame {

    private static final String[] MODULE_NAMES = {
        "CPU Scheduling",
        "Banker's",
        "Paging",
        "Page Replacement",
        "Multiprogramming",
        "Disk Scheduling"
    };

    public AppLauncher() {
        super("OS Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 780);
        setMinimumSize(new java.awt.Dimension(1100, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(AppTheme.BG);
        setLayout(new BorderLayout());

        CardLayout cards = new CardLayout();
        JPanel stage = new JPanel(cards);
        stage.setOpaque(false);
        stage.add(new SchedulerScreen(), MODULE_NAMES[0]);
        stage.add(new BankersScreen(), MODULE_NAMES[1]);
        stage.add(new PagingScreen(), MODULE_NAMES[2]);
        stage.add(new PageReplacementScreen(), MODULE_NAMES[3]);
        stage.add(new MultiprogrammingScreen(), MODULE_NAMES[4]);
        stage.add(new DiskSchedulerScreen(), MODULE_NAMES[5]);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nav.setOpaque(true);
        nav.setBackground(AppTheme.NAV_BG);
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));

        JLabel logo = new JLabel("OS Simulator");
        logo.setFont(AppTheme.font(Font.BOLD, 20));
        logo.setForeground(AppTheme.TEXT);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 18));
        nav.add(logo);

        List<PillButton> navButtons = new ArrayList<>();
        for (String name : MODULE_NAMES) {
            PillButton b = new PillButton(name);
            b.setTransparentWhenIdle(true);
            b.addActionListener(e -> {
                cards.show(stage, name);
                for (PillButton other : navButtons) other.setActive(other == b);
            });
            navButtons.add(b);
            nav.add(b);
        }
        navButtons.get(0).setActive(true);

        add(nav, BorderLayout.NORTH);
        add(stage, BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppLauncher().setVisible(true));
    }
}
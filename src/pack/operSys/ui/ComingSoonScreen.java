package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Placeholder for modules you have not built yet (Banker's, Paging, ...). Replace later. */
public class ComingSoonScreen extends JPanel {

    public ComingSoonScreen(String moduleName) {
        setOpaque(false);
        setLayout(new BorderLayout());

        RoundedCard card = new RoundedCard(moduleName);
        JLabel msg = new JLabel("This module is not built yet.", SwingConstants.CENTER);
        msg.setFont(AppTheme.font(Font.PLAIN, 15));
        msg.setForeground(AppTheme.MUTED);
        card.getBody().add(msg, BorderLayout.CENTER);

        setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(card, BorderLayout.CENTER);
    }
}

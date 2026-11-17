package pack.operSys.ui;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A rounded dark "card" with a title on top. Put your content inside getBody(). */
public class RoundedCard extends JPanel {

    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel body = new JPanel(new BorderLayout());

    public RoundedCard(String title) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppTheme.TITLE);
        titleLabel.setForeground(AppTheme.TEXT);

        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        body.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    public JPanel getBody() {
        return body;
    }

    /** Puts a component (button, label...) on the right side of the title row. */
    public void setHeaderRight(JComponent c) {
        header.add(c, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppTheme.CARD);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
        g2.setColor(AppTheme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
        g2.dispose();
        super.paintComponent(g);
    }
}

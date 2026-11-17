package pack.operSys.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Rounded input box that only accepts whole numbers (up to 3 digits). */
public class NumberBox extends JTextField {

    public NumberBox(int value) {
        super(String.valueOf(value), 3);
        setHorizontalAlignment(SwingConstants.CENTER);
        setOpaque(false);
        setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        setForeground(AppTheme.TEXT);
        setCaretColor(AppTheme.TEXT);
        setSelectionColor(AppTheme.ACCENT_DARK);
        setSelectedTextColor(AppTheme.TEXT);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setPreferredSize(new Dimension(46, 32));
        ((AbstractDocument) getDocument()).setDocumentFilter(new DigitsOnly(3));

        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(() -> selectAll());
                repaint();
            }
            @Override public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    /** The number in the box (an empty box counts as 0). */
    public int getIntValue() {
        try {
            return Integer.parseInt(getText().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public void setIntValue(int value) {
        setText(String.valueOf(value));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppTheme.CARD_ALT);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(hasFocus() ? AppTheme.ACCENT : AppTheme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
    }

    /** Throws away anything that is not 0-9 and caps the length. */
    private static class DigitsOnly extends DocumentFilter {
        private final int maxLength;

        DigitsOnly(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            StringBuilder digits = new StringBuilder();
            if (text != null) {
                for (char c : text.toCharArray()) {
                    if (c >= '0' && c <= '9') digits.append(c);
                }
            }
            int room = maxLength - (fb.getDocument().getLength() - length);
            if (room < digits.length()) digits.setLength(Math.max(0, room));
            super.replace(fb, offset, length, digits.toString(), attrs);
        }
    }
}

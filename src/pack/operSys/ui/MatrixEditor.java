package pack.operSys.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * A grid of NumberBoxes with "R0 R1 ..." across the top and "P0 P1 ..." down the side.
 * setGridSize() keeps the numbers that are still inside the new size.
 */
public class MatrixEditor extends JPanel {

    private final boolean showRowLabels;
    private NumberBox[][] cells = new NumberBox[0][0];

    public MatrixEditor(boolean showRowLabels) {
        this.showRowLabels = showRowLabels;
        setOpaque(false);
        setLayout(new GridBagLayout());
    }

    public void setGridSize(int rows, int cols) {
        int[][] old = getValues();
        removeAll();
        cells = new NumberBox[rows][cols];

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 3, 3, 3);
        int offset = showRowLabels ? 1 : 0;

        for (int c = 0; c < cols; c++) {
            gc.gridx = c + offset;
            gc.gridy = 0;
            add(smallLabel("R" + c, SwingConstants.CENTER), gc);
        }
        for (int r = 0; r < rows; r++) {
            if (showRowLabels) {
                gc.gridx = 0;
                gc.gridy = r + 1;
                gc.anchor = GridBagConstraints.EAST;
                add(smallLabel("P" + r, SwingConstants.RIGHT), gc);
                gc.anchor = GridBagConstraints.CENTER;
            }
            for (int c = 0; c < cols; c++) {
                int value = (r < old.length && c < old[r].length) ? old[r][c] : 0;
                NumberBox box = new NumberBox(value);
                cells[r][c] = box;
                gc.gridx = c + offset;
                gc.gridy = r + 1;
                add(box, gc);
            }
        }
        revalidate();
        repaint();
    }

    /** All numbers as [row][column] (empty boxes count as 0). */
    public int[][] getValues() {
        int[][] v = new int[cells.length][];
        for (int r = 0; r < cells.length; r++) {
            v[r] = new int[cells[r].length];
            for (int c = 0; c < cells[r].length; c++) v[r][c] = cells[r][c].getIntValue();
        }
        return v;
    }

    /** Fills the boxes from v (anything outside the current size is ignored). */
    public void setValues(int[][] v) {
        for (int r = 0; r < cells.length && r < v.length; r++) {
            for (int c = 0; c < cells[r].length && c < v[r].length; c++) cells[r][c].setIntValue(v[r][c]);
        }
    }

    private static JLabel smallLabel(String text, int align) {
        JLabel l = new JLabel(text, align);
        l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        l.setForeground(AppTheme.MUTED);
        return l;
    }
}

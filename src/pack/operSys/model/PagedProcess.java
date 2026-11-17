package pack.operSys.model;

import java.util.Arrays;

/**
 * Represents a process divided into fixed-size logical pages
 * along with its Page Table (page number -> physical frame number).
 */
public class PagedProcess {

    public final String pid;
    public final int sizeUnits;       // Process size in current unit (B or KB)
    public final int pageSizeUnits;   // Page/frame size in current unit (B or KB)
    public final int numPages;
    public final int internalFrag;    // Unused space in the last page
    public final int[] pageTable;     // index = page #, value = frame # (-1 if unmapped)
    public final boolean allocated;

    public PagedProcess(String pid, int sizeUnits, int pageSizeUnits, int[] pageTable, boolean allocated) {
        this.pid = pid;
        this.sizeUnits = sizeUnits;
        this.pageSizeUnits = pageSizeUnits;
        this.numPages = pageTable.length;
        int totalAllocated = this.numPages * pageSizeUnits;
        this.internalFrag = allocated ? Math.max(0, totalAllocated - sizeUnits) : 0;
        this.pageTable = Arrays.copyOf(pageTable, pageTable.length);
        this.allocated = allocated;
    }
}
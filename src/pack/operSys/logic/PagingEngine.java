package pack.operSys.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import pack.operSys.model.PagedProcess;

/**
 * Implements Paging memory allocation based on Silberschatz Ch. 8:
 * - Divides physical memory into fixed-size frames and tracks the Free-Frame List
 * - Allocates N free frames to a process of N pages and populates its Page Table
 * - Computes internal fragmentation and translates logical addresses [p | d] -> [f | d]
 */
public class PagingEngine {

    public static class ProcessSpec {
        public final String pid;
        public final int sizeUnits;

        public ProcessSpec(String pid, int sizeUnits) {
            this.pid = pid;
            this.sizeUnits = Math.max(1, sizeUnits);
        }
    }

    public static class TranslationStep {
        public final boolean valid;
        public final String message;
        public final String pid;
        public final int logicalAddress;
        public final int pageSizeBytes;
        public final int pageNumber;   // p
        public final int offset;       // d
        public final int frameNumber;  // f
        public final int physicalAddress;

        public TranslationStep(boolean valid, String message, String pid,
                int logicalAddress, int pageSizeBytes, int pageNumber,
                int offset, int frameNumber, int physicalAddress) {
            this.valid = valid;
            this.message = message;
            this.pid = pid;
            this.logicalAddress = logicalAddress;
            this.pageSizeBytes = pageSizeBytes;
            this.pageNumber = pageNumber;
            this.offset = offset;
            this.frameNumber = frameNumber;
            this.physicalAddress = physicalAddress;
        }
    }

    private int pageSizeUnits = 4;      // e.g., 4 KB (or 4 B in byte mode)
    private boolean byteMode = false;   // false = KB, true = Bytes (for Slide 8.5)
    private int totalFrames = 8;

    private String[] frameOwner = new String[totalFrames];
    private int[] framePage = new int[totalFrames];
    private final LinkedList<Integer> freeFrameList = new LinkedList<>();
    private final Map<String, PagedProcess> processMap = new LinkedHashMap<>();
    private String warningMessage = "";

    public PagingEngine() {
        resetMemory(4, false, 8);
    }

    public int getPageSizeUnits() {
        return pageSizeUnits;
    }

    public boolean isByteMode() {
        return byteMode;
    }

    public String getUnitLabel() {
        return byteMode ? "B" : "KB";
    }

    public int getPageSizeBytes() {
        return byteMode ? pageSizeUnits : pageSizeUnits * 1024;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public int getTotalMemoryUnits() {
        return totalFrames * pageSizeUnits;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public String getFrameOwner(int frame) {
        if (frame < 0 || frame >= frameOwner.length) return null;
        return frameOwner[frame];
    }

    public int getFramePage(int frame) {
        if (frame < 0 || frame >= framePage.length) return -1;
        return framePage[frame];
    }

    public List<Integer> getFreeFrameList() {
        return new ArrayList<>(freeFrameList);
    }

    public List<PagedProcess> getProcesses() {
        return new ArrayList<>(processMap.values());
    }

    public PagedProcess getProcess(String pid) {
        return processMap.get(pid);
    }

    /** Resets physical memory and fills the free-frame list in ascending order (0..totalFrames-1). */
    public void resetMemory(int newPageSizeUnits, boolean isByteMode, int newTotalFrames) {
        this.pageSizeUnits = Math.max(1, newPageSizeUnits);
        this.byteMode = isByteMode;
        this.totalFrames = Math.max(4, Math.min(32, newTotalFrames));

        frameOwner = new String[totalFrames];
        framePage = new int[totalFrames];
        Arrays.fill(framePage, -1);

        freeFrameList.clear();
        for (int f = 0; f < totalFrames; f++) {
            freeFrameList.add(f);
        }
        processMap.clear();
        warningMessage = "";
    }

    /**
     * Loads a specific frame mapping (used for the Slide 8.4 & Slide 8.5 textbook presets),
     * placing all remaining unallocated frames into the free-frame list.
     */
    public void loadPresetMapping(int newPageSizeUnits, boolean isByteMode, int newTotalFrames,
            String pid, int sizeUnits, int[] explicitFrames) {
        resetMemory(newPageSizeUnits, isByteMode, newTotalFrames);
        int[] table = Arrays.copyOf(explicitFrames, explicitFrames.length);
        for (int p = 0; p < table.length; p++) {
            int f = table[p];
            if (f >= 0 && f < totalFrames && frameOwner[f] == null) {
                frameOwner[f] = pid;
                framePage[f] = p;
                freeFrameList.remove(Integer.valueOf(f));
            }
        }
        processMap.put(pid, new PagedProcess(pid, sizeUnits, pageSizeUnits, table, true));
    }

    /**
     * Synchronizes processes with physical frames using the Free-Frame List:
     * - Freed frames from deleted/resized processes return to the free-frame list
     * - New processes pop the first N frames from the free-frame list
     */
    public void syncProcesses(List<ProcessSpec> specs) {
        warningMessage = "";

        Map<String, ProcessSpec> wanted = new LinkedHashMap<>();
        for (ProcessSpec spec : specs) {
            wanted.put(spec.pid, spec);
        }

        // 1. Reclaim frames from removed or resized processes
        for (int f = 0; f < totalFrames; f++) {
            String owner = frameOwner[f];
            if (owner == null) continue;

            ProcessSpec spec = wanted.get(owner);
            if (spec == null) {
                frameOwner[f] = null;
                framePage[f] = -1;
                if (!freeFrameList.contains(f)) freeFrameList.add(f);
            } else {
                int neededPages = (spec.sizeUnits + pageSizeUnits - 1) / pageSizeUnits;
                PagedProcess prev = processMap.get(owner);
                if (prev == null || !prev.allocated || prev.numPages != neededPages) {
                    frameOwner[f] = null;
                    framePage[f] = -1;
                    if (!freeFrameList.contains(f)) freeFrameList.add(f);
                }
            }
        }

        Map<String, PagedProcess> updated = new LinkedHashMap<>();

        // 2. Keep valid allocations; pop N free frames for new processes
        for (ProcessSpec spec : specs) {
            int neededPages = (spec.sizeUnits + pageSizeUnits - 1) / pageSizeUnits;
            PagedProcess existing = processMap.get(spec.pid);

            if (existing != null && existing.allocated && existing.numPages == neededPages && verifyFrames(existing)) {
                updated.put(spec.pid, new PagedProcess(
                        spec.pid, spec.sizeUnits, pageSizeUnits, existing.pageTable, true));
                continue;
            }

            if (freeFrameList.size() < neededPages) {
                int[] unmapped = new int[neededPages];
                Arrays.fill(unmapped, -1);
                updated.put(spec.pid, new PagedProcess(
                        spec.pid, spec.sizeUnits, pageSizeUnits, unmapped, false));
                warningMessage = "Insufficient free frames for " + spec.pid
                        + " (needs " + neededPages + ", free: " + freeFrameList.size() + ").";
            } else {
                int[] table = new int[neededPages];
                for (int p = 0; p < neededPages; p++) {
                    int frame = freeFrameList.removeFirst();
                    frameOwner[frame] = spec.pid;
                    framePage[frame] = p;
                    table[p] = frame;
                }
                updated.put(spec.pid, new PagedProcess(
                        spec.pid, spec.sizeUnits, pageSizeUnits, table, true));
            }
        }

        processMap.clear();
        processMap.putAll(updated);
    }

    private boolean verifyFrames(PagedProcess proc) {
        for (int p = 0; p < proc.pageTable.length; p++) {
            int f = proc.pageTable[p];
            if (f < 0 || f >= totalFrames) return false;
            if (!proc.pid.equals(frameOwner[f]) || framePage[f] != p) return false;
        }
        return true;
    }

    public int getUsedFramesCount() {
        return totalFrames - freeFrameList.size();
    }

    public int getFreeFramesCount() {
        return freeFrameList.size();
    }

    public int getTotalInternalFrag() {
        int sum = 0;
        for (PagedProcess p : processMap.values()) {
            if (p.allocated) sum += p.internalFrag;
        }
        return sum;
    }

    /**
     * Translates Logical Address -> (Page p, Offset d) -> Page Table[p] = Frame f -> Physical Address.
     */
    public TranslationStep translate(String pid, int logicalAddress) {
        int pageBytes = getPageSizeBytes();
        if (pid == null || !processMap.containsKey(pid)) {
            return new TranslationStep(false, "Select a process to view its Page Table.",
                    "-", logicalAddress, pageBytes, 0, 0, -1, -1);
        }

        PagedProcess proc = processMap.get(pid);
        if (logicalAddress < 0) {
            return new TranslationStep(false, "Logical address must be >= 0.",
                    pid, logicalAddress, pageBytes, 0, 0, -1, -1);
        }

        int p = logicalAddress / pageBytes;
        int d = logicalAddress % pageBytes;

        if (!proc.allocated) {
            return new TranslationStep(false, pid + " is unallocated (not enough free frames).",
                    pid, logicalAddress, pageBytes, p, d, -1, -1);
        }

        if (p >= proc.numPages) {
            return new TranslationStep(false,
                    "Out of bounds: Page " + p + " does not exist (" + pid + " has " + proc.numPages + " pages).",
                    pid, logicalAddress, pageBytes, p, d, -1, -1);
        }

        int f = proc.pageTable[p];
        int physicalAddress = (f * pageBytes) + d;
        String msg = String.format("Logical %d -> Page %d, Offset %d -> Frame %d -> Physical (%d × %d) + %d = %d",
                logicalAddress, p, d, f, f, pageBytes, d, physicalAddress);

        return new TranslationStep(true, msg, pid, logicalAddress, pageBytes, p, d, f, physicalAddress);
    }
}
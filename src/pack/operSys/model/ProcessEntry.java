package pack.operSys.model;

/** One process row (named ProcessEntry so it never clashes with java.lang.Process). */
public class ProcessEntry {
    public final String pid;
    public final int arrival;
    public final int burst;
    public final int priority;

    // filled in by the scheduling algorithms
    public int completion;
    public int turnaround;
    public int waiting;
    public int response;

    public ProcessEntry(String pid, int arrival, int burst, int priority) {
        this.pid = pid;
        this.arrival = arrival;
        this.burst = burst;
        this.priority = priority;
    }
}

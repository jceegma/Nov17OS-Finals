package pack.operSys.model;

/** One colored bar on the Gantt chart: process pid ran from start to end. */
public class GanttBlock {
    public final String pid;
    public final int start;
    public final int end;

    public GanttBlock(String pid, int start, int end) {
        this.pid = pid;
        this.start = start;
        this.end = end;
    }
}

package pack.operSys.logic;

import java.util.List;

import pack.operSys.model.GanttBlock;
import pack.operSys.model.ProcessEntry;

/** Every CPU scheduling algorithm plugs into this, so the UI never cares which one runs. */
public interface SchedulingAlgorithm {

    /** Runs the algorithm, fills each process' results, and returns the Gantt blocks. */
    List<GanttBlock> run(List<ProcessEntry> processes);

    /** Works out CT, TAT, WT and RT for every process from the finished Gantt blocks. */
    static void computeMetrics(List<ProcessEntry> processes, List<GanttBlock> blocks) {
        for (ProcessEntry p : processes) {
            int first = -1;
            int last = -1;
            for (GanttBlock b : blocks) {
                if (b.pid.equals(p.pid)) {
                    if (first < 0) first = b.start;
                    last = b.end;
                }
            }
            p.completion = last;
            p.response = first - p.arrival;
            p.turnaround = last - p.arrival;
            p.waiting = p.turnaround - p.burst;
        }
    }
}

package pack.operSys.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pack.operSys.model.GanttBlock;
import pack.operSys.model.ProcessEntry;

/** Round Robin: every process gets at most 'quantum' time units, then goes to the back of the queue. */
public class TimeSliceEngine implements SchedulingAlgorithm {

    private final int quantum;

    public TimeSliceEngine(int quantum) {
        this.quantum = Math.max(1, quantum);
    }

    @Override
    public List<GanttBlock> run(List<ProcessEntry> input) {
        List<GanttBlock> blocks = new ArrayList<>();

        List<ProcessEntry> byArrival = new ArrayList<>(input);
        byArrival.sort(Comparator.comparingInt((ProcessEntry p) -> p.arrival));

        Map<String, Integer> left = new HashMap<>();
        for (ProcessEntry p : input) left.put(p.pid, p.burst);

        Deque<ProcessEntry> queue = new ArrayDeque<>();
        int n = byArrival.size();
        int next = 0;      // index of the next process that has not entered the queue yet
        int time = 0;

        while (next < n || !queue.isEmpty()) {
            if (queue.isEmpty() && time < byArrival.get(next).arrival) {
                time = byArrival.get(next).arrival;            // CPU idle
            }
            while (next < n && byArrival.get(next).arrival <= time) {
                queue.add(byArrival.get(next++));
            }

            ProcessEntry cur = queue.poll();
            int slice = Math.min(quantum, left.get(cur.pid));
            blocks.add(new GanttBlock(cur.pid, time, time + slice));
            time += slice;
            left.put(cur.pid, left.get(cur.pid) - slice);

            // newcomers who arrived during this slice line up BEFORE the preempted process
            while (next < n && byArrival.get(next).arrival <= time) {
                queue.add(byArrival.get(next++));
            }
            if (left.get(cur.pid) > 0) queue.add(cur);
        }

        SchedulingAlgorithm.computeMetrics(input, blocks);
        return blocks;
    }
}

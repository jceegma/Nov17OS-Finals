package pack.operSys.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import pack.operSys.model.GanttBlock;
import pack.operSys.model.ProcessEntry;

/**
 * Shared loop for every algorithm where a process runs to completion once started.
 * Subclasses only decide HOW the next process is picked from the ready list.
 */
public abstract class NonPreemptiveBase implements SchedulingAlgorithm {

    /** Ready processes are sorted with this; the first one runs next. */
    protected abstract Comparator<ProcessEntry> pickOrder();

    @Override
    public List<GanttBlock> run(List<ProcessEntry> input) {
        List<GanttBlock> blocks = new ArrayList<>();
        List<ProcessEntry> pending = new ArrayList<>(input);
        int time = 0;

        while (!pending.isEmpty()) {
            List<ProcessEntry> ready = new ArrayList<>();
            for (ProcessEntry p : pending) {
                if (p.arrival <= time) ready.add(p);
            }

            if (ready.isEmpty()) {            // CPU idle: jump to the next arrival
                int next = Integer.MAX_VALUE;
                for (ProcessEntry p : pending) next = Math.min(next, p.arrival);
                time = next;
                continue;
            }

            ready.sort(pickOrder());
            ProcessEntry chosen = ready.get(0);
            blocks.add(new GanttBlock(chosen.pid, time, time + chosen.burst));
            time += chosen.burst;
            pending.remove(chosen);
        }

        SchedulingAlgorithm.computeMetrics(input, blocks);
        return blocks;
    }
}

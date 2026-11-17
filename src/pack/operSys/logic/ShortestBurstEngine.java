package pack.operSys.logic;

import java.util.Comparator;

import pack.operSys.model.ProcessEntry;

/** SJF (non-preemptive): smallest burst time among the ready processes goes first. */
public class ShortestBurstEngine extends NonPreemptiveBase {
    @Override
    protected Comparator<ProcessEntry> pickOrder() {
        return Comparator.comparingInt((ProcessEntry p) -> p.burst)
                         .thenComparingInt(p -> p.arrival);
    }
}

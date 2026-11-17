package pack.operSys.logic;

import java.util.Comparator;

import pack.operSys.model.ProcessEntry;

/** Priority (non-preemptive): LOWER number = HIGHER priority. */
public class PriorityRankEngine extends NonPreemptiveBase {
    @Override
    protected Comparator<ProcessEntry> pickOrder() {
        return Comparator.comparingInt((ProcessEntry p) -> p.priority)
                         .thenComparingInt(p -> p.arrival);
    }
}

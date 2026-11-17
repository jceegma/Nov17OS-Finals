package pack.operSys.logic;

import java.util.Comparator;

import pack.operSys.model.ProcessEntry;

/** FCFS: earliest arrival goes first. */
public class ArrivalOrderEngine extends NonPreemptiveBase {
    @Override
    protected Comparator<ProcessEntry> pickOrder() {
        return Comparator.comparingInt((ProcessEntry p) -> p.arrival);
    }
}

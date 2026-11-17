package pack.operSys.model;

/**
 * One event snapshot in the Multiprogramming simulation and visual timeline.
 */
public class MultiprogrammingEvent {

    public final double time;
    public final String event;
    public final int activeJobsBefore;
    public final boolean isIdleBefore;
    public final boolean isTermination;

    public final double cpuPercentPerJob;
    public final double elapsedTime;
    public final double headwayPerJob;
    public final String jobNames;
    public final String remainingTimes;
    public final String timelineEntry;

    public MultiprogrammingEvent(
            double time,
            String event,
            int activeJobsBefore,
            boolean isIdleBefore,
            boolean isTermination,
            double cpuPercentPerJob,
            double elapsedTime,
            double headwayPerJob,
            String jobNames,
            String remainingTimes,
            String timelineEntry) {
        this.time = time;
        this.event = event;
        this.activeJobsBefore = activeJobsBefore;
        this.isIdleBefore = isIdleBefore;
        this.isTermination = isTermination;
        this.cpuPercentPerJob = cpuPercentPerJob;
        this.elapsedTime = elapsedTime;
        this.headwayPerJob = headwayPerJob;
        this.jobNames = jobNames;
        this.remainingTimes = remainingTimes;
        this.timelineEntry = timelineEntry;
    }
}
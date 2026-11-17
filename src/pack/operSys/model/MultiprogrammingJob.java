package pack.operSys.model;

/**
 * Represents a job in the Multiprogramming simulator along with its
 * computed completion, turnaround, and waiting times.
 */
public class MultiprogrammingJob {

    public final String jobId;
    public final double arrival;
    public final double burst;

    public double completion;
    public double turnaround;
    public double waiting;

    public MultiprogrammingJob(String jobId, double arrival, double burst) {
        this.jobId = jobId;
        this.arrival = arrival;
        this.burst = burst;
    }
}
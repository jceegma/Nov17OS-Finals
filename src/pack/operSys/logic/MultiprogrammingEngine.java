package pack.operSys.logic;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pack.operSys.model.MultiprogrammingEvent;
import pack.operSys.model.MultiprogrammingJob;

/**
 * Simulates ideal Processor-Sharing Multiprogramming using double precision:
 * When N jobs are in memory simultaneously, each receives (100 / N)% of the CPU
 * and progresses at rate (1 / N) per unit of elapsed time.
 */
public class MultiprogrammingEngine {

    private static final double EPS = 1e-9;

    /** Formats a double with up to 2 decimals and no trailing zeros (e.g., 8, 7.5, 1.98). */
    public static String fmt(double value) {
        if (Math.abs(value) < 1e-9) {
            value = 0.0;
        }
        DecimalFormat df = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(value);
    }

    public static class SimulationResult {
        public final List<MultiprogrammingEvent> events;
        public final int totalJobs;
        public final double finishTime;
        public final double avgTurnaround;
        public final double avgWaiting;
        public final double cpuBusy;
        public final double cpuUtilization;

        public SimulationResult(
                List<MultiprogrammingEvent> events,
                int totalJobs,
                double finishTime,
                double avgTurnaround,
                double avgWaiting,
                double cpuBusy,
                double cpuUtilization) {
            this.events = events;
            this.totalJobs = totalJobs;
            this.finishTime = finishTime;
            this.avgTurnaround = avgTurnaround;
            this.avgWaiting = avgWaiting;
            this.cpuBusy = cpuBusy;
            this.cpuUtilization = cpuUtilization;
        }
    }

    private static class ActiveJob {
        final MultiprogrammingJob job;
        double remaining;

        ActiveJob(MultiprogrammingJob job) {
            this.job = job;
            this.remaining = job.burst;
        }
    }

    public SimulationResult run(List<MultiprogrammingJob> inputJobs) {
        List<MultiprogrammingEvent> events = new ArrayList<>();
        if (inputJobs == null || inputJobs.isEmpty()) {
            return new SimulationResult(events, 0, 0, 0, 0, 0, 0);
        }

        List<MultiprogrammingJob> pending = new ArrayList<>(inputJobs);
        pending.sort(Comparator.comparingDouble(j -> j.arrival));

        List<ActiveJob> active = new ArrayList<>();
        int nextArrivalIdx = 0;
        double currentTime = pending.get(0).arrival;
        double firstArrival = currentTime;

        while (nextArrivalIdx < pending.size() || !active.isEmpty()) {
            double nextArrivalTime = (nextArrivalIdx < pending.size())
                    ? pending.get(nextArrivalIdx).arrival
                    : Double.POSITIVE_INFINITY;

            ActiveJob earliestFinishing = null;
            double nextCompletionTime = Double.POSITIVE_INFINITY;
            if (!active.isEmpty()) {
                int n = active.size();
                for (ActiveJob aj : active) {
                    double timeToFinish = aj.remaining * n;
                    double finishAt = currentTime + timeToFinish;
                    if (finishAt < nextCompletionTime - EPS) {
                        nextCompletionTime = finishAt;
                        earliestFinishing = aj;
                    }
                }
            }

            if (nextCompletionTime <= nextArrivalTime + EPS && earliestFinishing != null) {
                // ---- TERMINATION EVENT ----
                double elapsed = Math.max(0.0, nextCompletionTime - currentTime);
                int n = active.size();
                double cpuPct = 100.0 / n;
                double headway = elapsed / n;

                StringBuilder namesSb = new StringBuilder();
                StringBuilder leftSb = new StringBuilder();

                for (int i = 0; i < active.size(); i++) {
                    ActiveJob aj = active.get(i);
                    aj.remaining = Math.max(0.0, aj.remaining - headway);
                    if (aj == earliestFinishing) {
                        aj.remaining = 0.0;
                    }
                    if (i > 0) {
                        namesSb.append(", ");
                        leftSb.append(", ");
                    }
                    namesSb.append(aj.job.jobId);
                    leftSb.append(fmt(aj.remaining));
                }

                currentTime = nextCompletionTime;
                earliestFinishing.job.completion = currentTime;
                earliestFinishing.job.turnaround = currentTime - earliestFinishing.job.arrival;
                earliestFinishing.job.waiting = Math.max(0.0, earliestFinishing.job.turnaround - earliestFinishing.job.burst);

                String eventLabel = earliestFinishing.job.jobId + " Terminated";
                String logLine = earliestFinishing.job.jobId + " terminated at " + fmt(currentTime)
                        + " (TAT: " + fmt(earliestFinishing.job.turnaround) + ")";

                events.add(new MultiprogrammingEvent(
                        currentTime, eventLabel, n, false, true,
                        cpuPct, elapsed, headway,
                        namesSb.toString(), leftSb.toString(), logLine));

                active.remove(earliestFinishing);

            } else {
                // ---- ARRIVAL / START EVENT ----
                MultiprogrammingJob arriving = pending.get(nextArrivalIdx++);
                double elapsed = Math.max(0.0, nextArrivalTime - currentTime);
                int n = active.size();

                if (n == 0) {
                    currentTime = nextArrivalTime;
                    String eventLabel = arriving.jobId + " Arrived/Started";
                    String logLine = arriving.jobId + " arrived/started at " + fmt(currentTime)
                            + " (CPU idle prior)";

                    events.add(new MultiprogrammingEvent(
                            currentTime, eventLabel, 0, true, false,
                            0.0, 0.0, 0.0, "", "", logLine));
                } else {
                    double cpuPct = 100.0 / n;
                    double headway = elapsed / n;

                    StringBuilder namesSb = new StringBuilder();
                    StringBuilder leftSb = new StringBuilder();

                    for (int i = 0; i < active.size(); i++) {
                        ActiveJob aj = active.get(i);
                        aj.remaining = Math.max(0.0, aj.remaining - headway);
                        if (i > 0) {
                            namesSb.append(", ");
                            leftSb.append(", ");
                        }
                        namesSb.append(aj.job.jobId);
                        leftSb.append(fmt(aj.remaining));
                    }

                    currentTime = nextArrivalTime;
                    String eventLabel = arriving.jobId + " Arrived/Started";
                    String logLine = arriving.jobId + " arrived/started at " + fmt(currentTime)
                            + " (" + (n + 1) + " jobs now sharing " + fmt(100.0 / (n + 1)) + "% CPU each)";

                    events.add(new MultiprogrammingEvent(
                            currentTime, eventLabel, n, false, false,
                            cpuPct, elapsed, headway,
                            namesSb.toString(), leftSb.toString(), logLine));
                }

                active.add(new ActiveJob(arriving));
            }
        }

        int totalJobs = inputJobs.size();
        double finishTime = currentTime;
        double sumTat = 0.0;
        double sumWait = 0.0;
        double sumBusy = 0.0;

        for (MultiprogrammingJob j : inputJobs) {
            sumTat += j.turnaround;
            sumWait += j.waiting;
            sumBusy += j.burst;
        }

        double totalSpan = finishTime - firstArrival;
        double cpuUtil = (totalSpan > EPS) ? (sumBusy / totalSpan) * 100.0 : 100.0;

        return new SimulationResult(
                events,
                totalJobs,
                finishTime,
                sumTat / totalJobs,
                sumWait / totalJobs,
                sumBusy,
                cpuUtil);
    }
}
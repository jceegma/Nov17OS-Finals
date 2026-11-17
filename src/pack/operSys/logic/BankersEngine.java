package pack.operSys.logic;

import java.util.ArrayList;
import java.util.List;

import pack.operSys.model.BankersState;

/** Banker's Algorithm: the safety check, resource requests and releases. No Swing code in here. */
public final class BankersEngine {

    private BankersEngine() { }

    // ------------------------------------------------------------------
    //  Result types
    // ------------------------------------------------------------------

    /** One line of the safety algorithm: this process could finish, so its allocation came back. */
    public static class Step {
        public final int process;
        public final int[] workBefore;
        public final int[] need;
        public final int[] workAfter;

        Step(int process, int[] workBefore, int[] need, int[] workAfter) {
            this.process = process;
            this.workBefore = workBefore;
            this.need = need;
            this.workAfter = workAfter;
        }
    }

    public static class SafetyResult {
        public final boolean safe;
        /** In the order the processes finished (this IS the safe sequence when safe == true). */
        public final List<Step> steps;
        public final int[] finalWork;
        /** Processes that could never finish (empty when the state is safe). */
        public final List<Integer> unfinished;

        SafetyResult(boolean safe, List<Step> steps, int[] finalWork, List<Integer> unfinished) {
            this.safe = safe;
            this.steps = steps;
            this.finalWork = finalWork;
            this.unfinished = unfinished;
        }

        /** "<P1, P3, P4, P0, P2>" */
        public String sequenceText() {
            StringBuilder sb = new StringBuilder("<");
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append('P').append(steps.get(i).process);
            }
            return sb.append('>').toString();
        }
    }

    public enum Outcome { GRANTED, INVALID, EXCEEDS_CLAIM, MUST_WAIT, UNSAFE }

    public static class RequestResult {
        public final Outcome outcome;
        public final String message;

        RequestResult(Outcome outcome, String message) {
            this.outcome = outcome;
            this.message = message;
        }

        public boolean granted() {
            return outcome == Outcome.GRANTED;
        }
    }

    // ------------------------------------------------------------------
    //  Safety algorithm
    // ------------------------------------------------------------------

    /**
     * Work = Available. Keep scanning the processes in order; any unfinished process whose
     * Need <= Work "finishes" and gives its Allocation back (Work += Allocation).
     * Stops when everyone finished (safe) or a whole pass finds nobody (unsafe).
     */
    public static SafetyResult checkSafety(BankersState s) {
        int n = s.processCount;
        int m = s.resourceCount;
        int[] work = s.available.clone();
        boolean[] finished = new boolean[n];
        List<Step> steps = new ArrayList<>();

        boolean progress = true;
        while (steps.size() < n && progress) {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (finished[i]) continue;
                int[] need = s.needRow(i);
                if (fits(need, work)) {
                    int[] before = work.clone();
                    for (int r = 0; r < m; r++) work[r] += s.allocation[i][r];
                    finished[i] = true;
                    steps.add(new Step(i, before, need, work.clone()));
                    progress = true;
                }
            }
        }

        List<Integer> unfinished = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!finished[i]) unfinished.add(i);
        }
        return new SafetyResult(unfinished.isEmpty(), steps, work, unfinished);
    }

    // ------------------------------------------------------------------
    //  Request / Release
    // ------------------------------------------------------------------

    /**
     * Resource-request algorithm. The request is granted (and the state CHANGED) only if
     * it fits the process' Need, fits Available, and leaves the system in a safe state.
     * Otherwise the state is left exactly as it was.
     */
    public static RequestResult request(BankersState s, int p, int[] req) {
        if (isZero(req)) {
            return new RequestResult(Outcome.INVALID, "Enter at least one resource to request.");
        }
        for (int r = 0; r < s.resourceCount; r++) {
            if (req[r] > s.need(p, r)) {
                return new RequestResult(Outcome.EXCEEDS_CLAIM, "Denied: P" + p
                        + " asked for " + req[r] + " of R" + r + " but its remaining Need is only "
                        + s.need(p, r) + ". A process cannot go beyond its maximum claim.");
            }
        }
        for (int r = 0; r < s.resourceCount; r++) {
            if (req[r] > s.available[r]) {
                return new RequestResult(Outcome.MUST_WAIT, "P" + p + " must wait: it asked for "
                        + req[r] + " of R" + r + " but only " + s.available[r] + " is available.");
            }
        }

        // pretend to grant it, then test whether the new state is still safe
        for (int r = 0; r < s.resourceCount; r++) {
            s.available[r] -= req[r];
            s.allocation[p][r] += req[r];
        }
        SafetyResult result = checkSafety(s);
        if (!result.safe) {
            for (int r = 0; r < s.resourceCount; r++) {      // roll back
                s.available[r] += req[r];
                s.allocation[p][r] -= req[r];
            }
            return new RequestResult(Outcome.UNSAFE, "Denied: granting " + format(req) + " to P" + p
                    + " would leave the system in an UNSAFE state, so P" + p + " must wait.");
        }
        return new RequestResult(Outcome.GRANTED, "Granted " + format(req) + " to P" + p
                + ". The system is still safe: " + result.sequenceText());
    }

    /** A process gives some of its allocation back to Available. */
    public static RequestResult release(BankersState s, int p, int[] rel) {
        if (isZero(rel)) {
            return new RequestResult(Outcome.INVALID, "Enter at least one resource to release.");
        }
        for (int r = 0; r < s.resourceCount; r++) {
            if (rel[r] > s.allocation[p][r]) {
                return new RequestResult(Outcome.INVALID, "P" + p + " only holds "
                        + s.allocation[p][r] + " of R" + r + ", so it cannot release " + rel[r] + ".");
            }
        }
        for (int r = 0; r < s.resourceCount; r++) {
            s.available[r] += rel[r];
            s.allocation[p][r] -= rel[r];
        }
        return new RequestResult(Outcome.GRANTED, "P" + p + " released " + format(rel)
                + ". The resources are back in Available.");
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static boolean fits(int[] need, int[] work) {
        for (int r = 0; r < need.length; r++) {
            if (need[r] > work[r]) return false;
        }
        return true;
    }

    private static boolean isZero(int[] v) {
        for (int x : v) {
            if (x != 0) return false;
        }
        return true;
    }

    /** "[3,3,2]" - the tight version used in the step log. */
    public static String compact(int[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    /** "[3, 3, 2]" */
    public static String format(int[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}

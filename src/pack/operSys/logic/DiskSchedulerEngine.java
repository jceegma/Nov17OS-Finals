package pack.operSys.logic;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pack.operSys.model.DiskSeekStep;

/**
 * Simulates standard Disk Scheduling algorithms (FIFO, SSTF, SCAN, C-SCAN, LOOK, C-LOOK)
 * and computes seek sequences, total head movement, and average seek length.
 */
public class DiskSchedulerEngine {

    public enum Algorithm {
        FIFO("FIFO"),
        SSTF("SSTF"),
        SCAN("SCAN"),
        C_SCAN("C-SCAN"),
        LOOK("LOOK"),
        C_LOOK("C-LOOK");

        public final String label;

        Algorithm(String label) {
            this.label = label;
        }
    }

    public static class SeekResult {
        public final Algorithm algorithm;
        public final List<DiskSeekStep> steps;
        public final int requestCount;
        public final int totalSeek;
        public final double avgSeek;

        public SeekResult(Algorithm algorithm, List<DiskSeekStep> steps, int requestCount, int totalSeek) {
            this.algorithm = algorithm;
            this.steps = steps;
            this.requestCount = requestCount;
            this.totalSeek = totalSeek;
            this.avgSeek = requestCount > 0 ? (double) totalSeek / requestCount : 0.0;
        }
    }

    /** Formats decimals up to 2 places without unnecessary trailing zeros (e.g., 80, 29.5, 37.38). */
    public static String fmt(double value) {
        if (Math.abs(value) < 1e-9) value = 0.0;
        DecimalFormat df = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(value);
    }

    public SeekResult simulate(
            Algorithm algo,
            List<Integer> rawQueue,
            int initialHead,
            int maxTrack,
            boolean moveRight) {

        int safeMax = Math.max(1, maxTrack);
        int head = Math.max(0, Math.min(safeMax, initialHead));

        List<Integer> queue = new ArrayList<>();
        if (rawQueue != null) {
            for (int track : rawQueue) {
                queue.add(Math.max(0, Math.min(safeMax, track)));
            }
        }

        List<DiskSeekStep> steps = new ArrayList<>();
        steps.add(new DiskSeekStep(0, head, head, false));

        if (queue.isEmpty()) {
            return new SeekResult(algo, steps, 0, 0);
        }

        int current = head;
        int seq = 1;

        switch (algo) {
            case FIFO: {
                for (int target : queue) {
                    steps.add(new DiskSeekStep(seq++, current, target, false));
                    current = target;
                }
                break;
            }

            case SSTF: {
                List<Integer> remaining = new ArrayList<>(queue);
                while (!remaining.isEmpty()) {
                    int bestIdx = 0;
                    int bestDist = Math.abs(remaining.get(0) - current);
                    for (int i = 1; i < remaining.size(); i++) {
                        int dist = Math.abs(remaining.get(i) - current);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestIdx = i;
                        }
                    }
                    int next = remaining.remove(bestIdx);
                    steps.add(new DiskSeekStep(seq++, current, next, false));
                    current = next;
                }
                break;
            }

            case SCAN: {
                List<Integer> left = new ArrayList<>();
                List<Integer> right = new ArrayList<>();
                for (int t : queue) {
                    if (t < head) left.add(t);
                    else right.add(t);
                }
                Collections.sort(left);
                Collections.sort(right);

                if (moveRight) {
                    for (int t : right) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    if (!left.isEmpty()) {
                        if (current != safeMax) {
                            steps.add(new DiskSeekStep(seq++, current, safeMax, true));
                            current = safeMax;
                        }
                        for (int i = left.size() - 1; i >= 0; i--) {
                            int t = left.get(i);
                            steps.add(new DiskSeekStep(seq++, current, t, false));
                            current = t;
                        }
                    }
                } else {
                    // When moving left, tracks equal to head are serviced immediately
                    List<Integer> leftEq = new ArrayList<>();
                    List<Integer> strictlyRight = new ArrayList<>();
                    for (int t : queue) {
                        if (t <= head) leftEq.add(t);
                        else strictlyRight.add(t);
                    }
                    Collections.sort(leftEq);
                    Collections.sort(strictlyRight);

                    for (int i = leftEq.size() - 1; i >= 0; i--) {
                        int t = leftEq.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    if (!strictlyRight.isEmpty()) {
                        if (current != 0) {
                            steps.add(new DiskSeekStep(seq++, current, 0, true));
                            current = 0;
                        }
                        for (int t : strictlyRight) {
                            steps.add(new DiskSeekStep(seq++, current, t, false));
                            current = t;
                        }
                    }
                }
                break;
            }

            case C_SCAN: {
                if (moveRight) {
                    List<Integer> left = new ArrayList<>();
                    List<Integer> right = new ArrayList<>();
                    for (int t : queue) {
                        if (t < head) left.add(t);
                        else right.add(t);
                    }
                    Collections.sort(left);
                    Collections.sort(right);

                    for (int t : right) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    if (!left.isEmpty()) {
                        if (current != safeMax) {
                            steps.add(new DiskSeekStep(seq++, current, safeMax, true));
                            current = safeMax;
                        }
                        steps.add(new DiskSeekStep(seq++, current, 0, true));
                        current = 0;
                        for (int t : left) {
                            steps.add(new DiskSeekStep(seq++, current, t, false));
                            current = t;
                        }
                    }
                } else {
                    List<Integer> leftEq = new ArrayList<>();
                    List<Integer> strictlyRight = new ArrayList<>();
                    for (int t : queue) {
                        if (t <= head) leftEq.add(t);
                        else strictlyRight.add(t);
                    }
                    Collections.sort(leftEq);
                    Collections.sort(strictlyRight);

                    for (int i = leftEq.size() - 1; i >= 0; i--) {
                        int t = leftEq.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    if (!strictlyRight.isEmpty()) {
                        if (current != 0) {
                            steps.add(new DiskSeekStep(seq++, current, 0, true));
                            current = 0;
                        }
                        steps.add(new DiskSeekStep(seq++, current, safeMax, true));
                        current = safeMax;
                        for (int i = strictlyRight.size() - 1; i >= 0; i--) {
                            int t = strictlyRight.get(i);
                            steps.add(new DiskSeekStep(seq++, current, t, false));
                            current = t;
                        }
                    }
                }
                break;
            }

            case LOOK: {
                if (moveRight) {
                    List<Integer> left = new ArrayList<>();
                    List<Integer> right = new ArrayList<>();
                    for (int t : queue) {
                        if (t < head) left.add(t);
                        else right.add(t);
                    }
                    Collections.sort(left);
                    Collections.sort(right);

                    for (int t : right) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    for (int i = left.size() - 1; i >= 0; i--) {
                        int t = left.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                } else {
                    List<Integer> leftEq = new ArrayList<>();
                    List<Integer> strictlyRight = new ArrayList<>();
                    for (int t : queue) {
                        if (t <= head) leftEq.add(t);
                        else strictlyRight.add(t);
                    }
                    Collections.sort(leftEq);
                    Collections.sort(strictlyRight);

                    for (int i = leftEq.size() - 1; i >= 0; i--) {
                        int t = leftEq.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    for (int t : strictlyRight) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                }
                break;
            }

            case C_LOOK:
            default: {
                if (moveRight) {
                    List<Integer> left = new ArrayList<>();
                    List<Integer> right = new ArrayList<>();
                    for (int t : queue) {
                        if (t < head) left.add(t);
                        else right.add(t);
                    }
                    Collections.sort(left);
                    Collections.sort(right);

                    for (int t : right) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    for (int t : left) {
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                } else {
                    List<Integer> leftEq = new ArrayList<>();
                    List<Integer> strictlyRight = new ArrayList<>();
                    for (int t : queue) {
                        if (t <= head) leftEq.add(t);
                        else strictlyRight.add(t);
                    }
                    Collections.sort(leftEq);
                    Collections.sort(strictlyRight);

                    for (int i = leftEq.size() - 1; i >= 0; i--) {
                        int t = leftEq.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                    for (int i = strictlyRight.size() - 1; i >= 0; i--) {
                        int t = strictlyRight.get(i);
                        steps.add(new DiskSeekStep(seq++, current, t, false));
                        current = t;
                    }
                }
                break;
            }
        }

        int totalSeek = 0;
        for (int i = 1; i < steps.size(); i++) {
            totalSeek += steps.get(i).distance;
        }
        return new SeekResult(algo, steps, queue.size(), totalSeek);
    }

    public List<SeekResult> compareAll(List<Integer> queue, int initialHead, int maxTrack, boolean moveRight) {
        List<SeekResult> results = new ArrayList<>();
        for (Algorithm algo : Algorithm.values()) {
            results.add(simulate(algo, queue, initialHead, maxTrack, moveRight));
        }
        return results;
    }
}
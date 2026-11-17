package pack.operSys.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pack.operSys.model.PageReplacementStep;

/**
 * Simulates FIFO, LRU, OPT, and LFU page replacement algorithms
 * and produces a step-by-step frame allocation history.
 */
public class PageReplacementEngine {

    public enum Algorithm {
        FIFO, LRU, OPT, LFU
    }

    public List<PageReplacementStep> simulate(Algorithm algo, int frameCount, List<Integer> referenceString) {
        List<PageReplacementStep> steps = new ArrayList<>();
        if (referenceString == null || referenceString.isEmpty() || frameCount <= 0) {
            return steps;
        }

        int[] frames = new int[frameCount];
        int[] loadedAt = new int[frameCount];
        int[] lastUsedAt = new int[frameCount];
        int[] useCount = new int[frameCount];

        Arrays.fill(frames, -1);
        Arrays.fill(loadedAt, -1);
        Arrays.fill(lastUsedAt, -1);
        Arrays.fill(useCount, 0);

        for (int t = 0; t < referenceString.size(); t++) {
            int page = referenceString.get(t);

            // 1. Check if page is already resident in a frame (Page Hit)
            int hitFrame = -1;
            for (int f = 0; f < frameCount; f++) {
                if (frames[f] == page) {
                    hitFrame = f;
                    break;
                }
            }

            if (hitFrame != -1) {
                lastUsedAt[hitFrame] = t;
                useCount[hitFrame]++;
                String msg = String.format("Step %d (Page %d): PAGE HIT — already loaded in Frame F%d.",
                        t + 1, page, hitFrame);
                steps.add(new PageReplacementStep(t, page, frames, true, hitFrame, -1, msg));
                continue;
            }

            // 2. Page Fault: look for an empty frame first
            int emptyFrame = -1;
            for (int f = 0; f < frameCount; f++) {
                if (frames[f] == -1) {
                    emptyFrame = f;
                    break;
                }
            }

            if (emptyFrame != -1) {
                frames[emptyFrame] = page;
                loadedAt[emptyFrame] = t;
                lastUsedAt[emptyFrame] = t;
                useCount[emptyFrame] = 1;
                String msg = String.format("Step %d (Page %d): PAGE FAULT — loaded into free Frame F%d.",
                        t + 1, page, emptyFrame);
                steps.add(new PageReplacementStep(t, page, frames, false, emptyFrame, -1, msg));
                continue;
            }

            // 3. RAM is full: select a victim frame according to the active algorithm
            int victimFrame = chooseVictim(algo, frames, loadedAt, lastUsedAt, useCount, referenceString, t);
            int evicted = frames[victimFrame];

            frames[victimFrame] = page;
            loadedAt[victimFrame] = t;
            lastUsedAt[victimFrame] = t;
            useCount[victimFrame] = 1;

            String msg = String.format("Step %d (Page %d): PAGE FAULT — RAM full, evicted Page %d from Frame F%d (%s).",
                    t + 1, page, evicted, victimFrame, algo.name());
            steps.add(new PageReplacementStep(t, page, frames, false, victimFrame, evicted, msg));
        }

        return steps;
    }

    private int chooseVictim(
            Algorithm algo,
            int[] frames,
            int[] loadedAt,
            int[] lastUsedAt,
            int[] useCount,
            List<Integer> refs,
            int currentStep) {

        int frameCount = frames.length;
        int victim = 0;

        switch (algo) {
            case FIFO:
                for (int f = 1; f < frameCount; f++) {
                    if (loadedAt[f] < loadedAt[victim]) {
                        victim = f;
                    }
                }
                return victim;

            case LRU:
                for (int f = 1; f < frameCount; f++) {
                    if (lastUsedAt[f] < lastUsedAt[victim]) {
                        victim = f;
                    }
                }
                return victim;

            case LFU:
                for (int f = 1; f < frameCount; f++) {
                    if (useCount[f] < useCount[victim]
                            || (useCount[f] == useCount[victim] && loadedAt[f] < loadedAt[victim])) {
                        victim = f;
                    }
                }
                return victim;

            case OPT:
            default:
                int farthestNextUse = -1;
                for (int f = 0; f < frameCount; f++) {
                    int nextUse = Integer.MAX_VALUE;
                    for (int k = currentStep + 1; k < refs.size(); k++) {
                        if (refs.get(k) == frames[f]) {
                            nextUse = k;
                            break;
                        }
                    }
                    if (nextUse > farthestNextUse
                            || (nextUse == farthestNextUse && loadedAt[f] < loadedAt[victim])) {
                        farthestNextUse = nextUse;
                        victim = f;
                    }
                }
                return victim;
        }
    }
}
package pack.operSys.model;

import java.util.Arrays;

/**
 * Snapshot of physical memory frames at a single step of the reference string.
 */
public class PageReplacementStep {

    public final int stepIndex;       // 0-based index in the reference string
    public final int page;            // Requested page number
    public final int[] frames;        // Frame contents after this step (-1 = empty)
    public final boolean hit;         // true = Page Hit, false = Page Fault
    public final int targetFrame;     // Frame index that hit or received the new page
    public final int evictedPage;     // Page evicted on fault (-1 if free slot or hit)
    public final String explanation;  // Human-readable trace of what happened

    public PageReplacementStep(
            int stepIndex,
            int page,
            int[] frames,
            boolean hit,
            int targetFrame,
            int evictedPage,
            String explanation) {
        this.stepIndex = stepIndex;
        this.page = page;
        this.frames = Arrays.copyOf(frames, frames.length);
        this.hit = hit;
        this.targetFrame = targetFrame;
        this.evictedPage = evictedPage;
        this.explanation = explanation;
    }
}
package pack.operSys.model;

/**
 * Represents a single head movement in a Disk Scheduling simulation.
 */
public class DiskSeekStep {

    public final int stepNumber;      // 0 = Initial Head, 1..N = movements
    public final int fromTrack;
    public final int toTrack;
    public final int distance;
    public final boolean isBoundary;  // true when SCAN/C-SCAN hits 0 or Max Track

    public DiskSeekStep(int stepNumber, int fromTrack, int toTrack, boolean isBoundary) {
        this.stepNumber = stepNumber;
        this.fromTrack = fromTrack;
        this.toTrack = toTrack;
        this.distance = Math.abs(toTrack - fromTrack);
        this.isBoundary = isBoundary;
    }

    public String getFormula() {
        if (stepNumber == 0) {
            return "Initial Head";
        }
        int high = Math.max(fromTrack, toTrack);
        int low = Math.min(fromTrack, toTrack);
        return high + " - " + low + " = " + distance;
    }
}
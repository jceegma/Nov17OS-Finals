package pack.operSys.model;

/**
 * The whole Banker's Algorithm system: how many of each resource are free (available),
 * the most each process may ever ask for (max), and what each process holds now (allocation).
 * Need is never stored; it is always max - allocation.
 */
public class BankersState {

    public final int processCount;
    public final int resourceCount;
    public final int[] available;
    public final int[][] max;
    public final int[][] allocation;

    public BankersState(int processCount, int resourceCount) {
        this.processCount = processCount;
        this.resourceCount = resourceCount;
        this.available = new int[resourceCount];
        this.max = new int[processCount][resourceCount];
        this.allocation = new int[processCount][resourceCount];
    }

    /** Need = Maximum - Allocation. */
    public int need(int process, int resource) {
        return max[process][resource] - allocation[process][resource];
    }

    public int[] needRow(int process) {
        int[] row = new int[resourceCount];
        for (int r = 0; r < resourceCount; r++) row[r] = need(process, r);
        return row;
    }

    /** Total instances of each resource = available + everything currently allocated. */
    public int[] totalInstances() {
        int[] total = available.clone();
        for (int p = 0; p < processCount; p++) {
            for (int r = 0; r < resourceCount; r++) total[r] += allocation[p][r];
        }
        return total;
    }

    public BankersState copy() {
        BankersState c = new BankersState(processCount, resourceCount);
        System.arraycopy(available, 0, c.available, 0, resourceCount);
        for (int p = 0; p < processCount; p++) {
            System.arraycopy(max[p], 0, c.max[p], 0, resourceCount);
            System.arraycopy(allocation[p], 0, c.allocation[p], 0, resourceCount);
        }
        return c;
    }

    /** The classic textbook example: 5 processes, 3 resource types, 10 / 5 / 7 instances. */
    public static BankersState sample() {
        BankersState s = new BankersState(5, 3);
        int[] av = { 3, 3, 2 };
        int[][] mx = { { 7, 5, 3 }, { 3, 2, 2 }, { 9, 0, 2 }, { 2, 2, 2 }, { 4, 3, 3 } };
        int[][] al = { { 0, 1, 0 }, { 2, 0, 0 }, { 3, 0, 2 }, { 2, 1, 1 }, { 0, 0, 2 } };
        System.arraycopy(av, 0, s.available, 0, 3);
        for (int p = 0; p < 5; p++) {
            System.arraycopy(mx[p], 0, s.max[p], 0, 3);
            System.arraycopy(al[p], 0, s.allocation[p], 0, 3);
        }
        return s;
    }
}

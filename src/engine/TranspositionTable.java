package engine;

public class TranspositionTable {
    public static final int EXACT = 0;
    public static final int ALPHA = 1; // upper bound (failed low)
    public static final int BETA = 2;  // lower bound (failed high)

    private final long[] keys;
    private final int[] depths;
    private final int[] scores;
    private final int[] flags;
    private final int size;

    public TranspositionTable(int sizeMB) {
        // Each entry: 8 (key) + 4 (depth) + 4 (score) + 4 (flag) = 20 bytes
        this.size = (sizeMB * 1024 * 1024) / 20;
        this.keys = new long[size];
        this.depths = new int[size];
        this.scores = new int[size];
        this.flags = new int[size];
    }

    private int index(long hash) {
        return (int) (Math.abs(hash) % size);
    }

    public void store(long hash, int depth, int score, int flag) {
        int i = index(hash);
        // Replace if deeper or different position
        if (keys[i] == 0 || depth >= depths[i] || keys[i] != hash) {
            keys[i] = hash;
            depths[i] = depth;
            scores[i] = score;
            flags[i] = flag;
        }
    }

    public int probe(long hash, int depth, int alpha, int beta) {
        int i = index(hash);
        if (keys[i] != hash) return Integer.MIN_VALUE;
        if (depths[i] < depth) return Integer.MIN_VALUE;

        int score = scores[i];
        int flag = flags[i];

        if (flag == EXACT) return score;
        if (flag == ALPHA && score <= alpha) return alpha;
        if (flag == BETA && score >= beta) return beta;

        return Integer.MIN_VALUE;
    }

    public void clear() {
        java.util.Arrays.fill(keys, 0);
    }
}

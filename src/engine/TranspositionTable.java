package engine;

import main.Move;

public class TranspositionTable {
    public static final int NO_MOVE = 0;
    public static final int EXACT = 0;
    public static final int ALPHA = 1; // upper bound (failed low)
    public static final int BETA = 2;  // lower bound (failed high)
    public static final int MOVE_ONLY = 3;

    private final long[] keys;
    private final int[] depths;
    private final int[] scores;
    private final int[] flags;
    private final int[] bestMoves;
    private final int size;

    public TranspositionTable(int sizeMB) {
        // Each entry: 8 (key) + 4 each (depth, score, flag, packed move) = 24 bytes
        this.size = (sizeMB * 1024 * 1024) / 24;
        this.keys = new long[size];
        this.depths = new int[size];
        this.scores = new int[size];
        this.flags = new int[size];
        this.bestMoves = new int[size];
        java.util.Arrays.fill(depths, -1);
    }

    private int index(long hash) {
        return (int) ((hash & Long.MAX_VALUE) % size);
    }

    // Coordinates and promotion only: never retain mutable pieces from a board.
    public static int encodeMove(Move move) {
        if (move == null) return NO_MOVE;
        int from = move.row * 8 + move.col;
        int to = move.newRow * 8 + move.newCol;
        int promotion = move.promotionPiece == null ? 0 : move.promotionPiece.ordinal() + 1;
        return 1 + (from | (to << 6) | (promotion << 12));
    }

    public void store(long hash, int depth, int score, int flag, int bestMove) {
        int i = index(hash);
        // Replace if deeper or different position
        if (depths[i] < 0 || depth >= depths[i] || keys[i] != hash) {
            keys[i] = hash;
            depths[i] = depth;
            scores[i] = score;
            flags[i] = flag;
            bestMoves[i] = bestMove;
        }
    }

    // A shallow entry can still provide useful ordering for a deeper search.
    public int getBestMove(long hash) {
        int i = index(hash);
        return depths[i] >= 0 && keys[i] == hash ? bestMoves[i] : NO_MOVE;
    }

    public int probe(long hash, int depth, int alpha, int beta) {
        int i = index(hash);
        if (depths[i] < 0 || keys[i] != hash) return Integer.MIN_VALUE;
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
        java.util.Arrays.fill(depths, -1);
        java.util.Arrays.fill(bestMoves, NO_MOVE);
    }
}

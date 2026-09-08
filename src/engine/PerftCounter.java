package engine;

import Pieces.Piece;
import main.Board;
import main.Move;

import java.util.concurrent.CancellationException;

/** Exact leaf counting. Neither game draws nor search pruning terminate perft. */
public final class PerftCounter {
    private final boolean bulk;
    private final Cache cache;
    private long deadline;
    private long visits;
    public long cacheHits;

    public PerftCounter(boolean bulk, int cacheMB) {
        if (!bulk && cacheMB != 0) throw new IllegalArgumentException("Cache requires bulk mode");
        if (cacheMB < 0 || cacheMB > 128) throw new IllegalArgumentException("Cache must be 0..128 MiB");
        this.bulk = bulk;
        cache = cacheMB == 0 ? null : new Cache(cacheMB);
    }

    public long count(Board board, int depth, long timeoutMillis) {
        if (depth < 0 || depth > 8 || timeoutMillis <= 0 || timeoutMillis > 3_600_000)
            throw new IllegalArgumentException("Depth must be 0..8 and timeout 1..3600000 ms");
        deadline = System.nanoTime() + timeoutMillis * 1_000_000;
        visits = 0;
        cacheHits = 0;
        if (cache != null) cache.clear();
        return visit(board, new MoveGen(board), depth);
    }

    private long visit(Board board, MoveGen generator, int depth) {
        if ((visits++ & 16383) == 0 && (System.nanoTime() - deadline >= 0 || Thread.currentThread().isInterrupted()))
            throw new CancellationException("Perft time limit reached");
        if (depth == 0) return 1;
        if (cache != null && depth >= 2) {
            long found = cache.probe(board, depth);
            if (found >= 0) { cacheHits++; return found; }
        }
        var moves = generator.getAllValidMoves();
        if (bulk && depth == 1) return moves.size();
        long nodes = 0;
        for (Move move : moves) {
            Move undo = board.makeMove(move, true);
            try {
                nodes = Math.addExact(nodes, visit(board, generator, depth - 1));
            } finally {
                board.undoMove(undo);
            }
        }
        if (cache != null && depth >= 2) cache.store(board, depth, nodes);
        return nodes;
    }

    public long cacheBytes() { return cache == null ? 0 : cache.bytes(); }

    // A hash selects the bucket; all 64 squares, first-move flags, side, EP state
    // and depth must match before accepting a cached count. Hash collisions miss.
    private static final class Cache {
        final long[] a, b, c, d, firstMoves, counts;
        final int[] states;
        final int mask;
        long ka, kb, kc, kd, kFirst;
        int state;

        Cache(int mb) {
            int size = Integer.highestOneBit((mb * 1024 * 1024) / 52);
            mask = size - 1;
            a = new long[size]; b = new long[size]; c = new long[size]; d = new long[size];
            firstMoves = new long[size]; counts = new long[size]; states = new int[size];
        }
        void clear() { java.util.Arrays.fill(states, 0); }
        long bytes() { return (mask + 1L) * 52; }
        int key(Board board, int depth) {
            ka = kb = kc = kd = kFirst = 0;
            for (Piece piece : board.pieceList) {
                int square = piece.row * 8 + piece.col;
                long code = (long) (1 + piece.type.ordinal() + piece.color * 6) << ((square & 15) * 4);
                switch (square >>> 4) {
                    case 0 -> ka |= code;
                    case 1 -> kb |= code;
                    case 2 -> kc |= code;
                    case 3 -> kd |= code;
                }
                if (piece.isFirstMove) kFirst |= 1L << square;
            }
            state = (depth << 9) | board.colorToMove;
            if (board.scanner.enPassantEnable)
                state |= 2 | (board.scanner.enPassantCol << 2) | (board.scanner.enPassantRow << 5);
            return (int) (board.zobristHash ^ (0x9e3779b97f4a7c15L * depth)) & mask;
        }
        long probe(Board board, int depth) {
            int i = key(board, depth);
            return states[i] == state && a[i] == ka && b[i] == kb && c[i] == kc && d[i] == kd
                    && firstMoves[i] == kFirst ? counts[i] : -1;
        }
        void store(Board board, int depth, long nodes) {
            int i = key(board, depth);
            a[i] = ka; b[i] = kb; c[i] = kc; d[i] = kd;
            firstMoves[i] = kFirst; counts[i] = nodes; states[i] = state;
        }
    }
}

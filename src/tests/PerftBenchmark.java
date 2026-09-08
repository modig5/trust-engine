package tests;

import engine.PerftCounter;
import main.Board;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/** Example: java -Xms256m -Xmx512m -cp build/classes tests.PerftBenchmark --depth 7 --cache-mb 64 */
public final class PerftBenchmark {
    public static final String[] FENS = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10"
    };
    // Published reference counts: https://www.chessprogramming.org/Perft_Results
    public static final long[][] EXPECTED = {
            {1,20,400,8902,197281,4865609,119060324,3195901860L,84998978956L},
            {1,48,2039,97862,4085603,193690690,8031647685L},
            {1,14,191,2812,43238,674624,11030083,178633661,3009794393L},
            {1,6,264,9467,422333,15833292,706045033},
            {1,44,1486,62379,2103487,89941194},
            {1,46,2079,89890,3894594,164075551,6923051137L}
    };

    public static void main(String[] args) {
        int depth = 6, position = 1, runs = 3, warmups = 3, cacheMB = 0;
        long timeout = 120_000;
        boolean full = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--depth" -> depth = Integer.parseInt(args[++i]);
                case "--position" -> position = Integer.parseInt(args[++i]);
                case "--runs" -> runs = Integer.parseInt(args[++i]);
                case "--warmups" -> warmups = Integer.parseInt(args[++i]);
                case "--cache-mb" -> cacheMB = Integer.parseInt(args[++i]);
                case "--timeout-ms" -> timeout = Long.parseLong(args[++i]);
                case "--full" -> full = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (position < 1 || position > FENS.length || depth < 0 || depth > 8
                || depth >= EXPECTED[position - 1].length || runs < 1 || warmups < 0)
            throw new IllegalArgumentException("Invalid depth, position or run count");
        Board board = new Board(FENS[position - 1]);
        var counter = new PerftCounter(!full, cacheMB);
        double[] times = new double[runs];
        String fen = board.getFEN();
        long hash = board.zobristHash;
        var repetitions = new HashMap<>(board.repetitionMap);
        String mode = full ? "full" : cacheMB == 0 ? "bulk" : "cached";
        System.out.printf("position=%d depth=%d mode=%s warmups=%d runs=%d timeout_ms=%d heap_limit=%d cache_bytes=%d%n",
                position, depth, mode, warmups, runs, timeout, Runtime.getRuntime().maxMemory(), counter.cacheBytes());
        for (int run = -warmups; run < runs; run++) {
            int measuredDepth = run < 0 ? Math.min(depth, 5) : depth;
            long start = System.nanoTime();
            long nodes;
            try {
                nodes = counter.count(board, measuredDepth, timeout);
            } catch (CancellationException e) {
                checkRestored(board, fen, hash, repetitions);
                System.err.println("INCOMPLETE: " + e.getMessage() + "; board restored; no node total accepted");
                System.exit(2);
                return;
            }
            double seconds = (System.nanoTime() - start) / 1e9;
            checkRestored(board, fen, hash, repetitions);
            if (nodes != EXPECTED[position - 1][measuredDepth])
                throw new AssertionError("Perft mismatch: " + nodes + " expected " + EXPECTED[position - 1][measuredDepth]);
            if (run >= 0) {
                times[run] = seconds;
                System.out.printf(Locale.ROOT, "PASS run=%d nodes=%d seconds=%.6f nps=%.0f cache_hits=%d%n",
                        run + 1, nodes, seconds, nodes / seconds, counter.cacheHits);
            }
        }
        Arrays.sort(times);
        double median = runs % 2 == 1 ? times[runs / 2] : (times[runs / 2 - 1] + times[runs / 2]) / 2;
        long peakHeap = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        System.out.printf(Locale.ROOT, "median_seconds=%.6f median_nps=%.0f heap_pool_peak_sum_bytes=%d%n",
                median, EXPECTED[position - 1][depth] / median, peakHeap);
    }

    private static void checkRestored(Board board, String fen, long hash, java.util.Map<Long,Integer> repetitions) {
        if (!board.getFEN().equals(fen) || board.zobristHash != hash || !board.repetitionMap.equals(repetitions))
            throw new AssertionError("Perft did not restore board state");
    }
}

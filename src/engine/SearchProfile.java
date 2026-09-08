package engine;

import main.Board;
import main.Move;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Profiles the GUI's iterative search headlessly, without book lookup or pondering. */
public final class SearchProfile {
    private static final String[] NAMES = {"opening", "middlegame", "endgame"};
    private static final String[] FENS = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"
    };

    public static void main(String[] args) throws Exception {
        if (args.length > 3) throw new IllegalArgumentException("Usage: SearchProfile [depth] [runs] [warmups]");
        int depth = args.length > 0 ? Integer.parseInt(args[0]) : 7;
        int runs = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int warmups = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        if (depth < 1 || depth > 8 || runs < 1 || runs > 20 || warmups < 0 || warmups > 20)
            throw new IllegalArgumentException("Depth must be 1..8, runs 1..20, warmups 0..20");
        Path root = Path.of("build", "profiles");
        Files.createDirectories(root);
        Path output = Files.createTempDirectory(root, "search-");
        StringBuilder report = new StringBuilder();
        append(report, "Java %s; depth=%d runs=%d warmups=%d; max_heap=%d MiB%n",
                System.getProperty("java.version"), depth, runs, warmups,
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        System.out.println("Warming up search; output: " + output);
        for (int round = 0; round < warmups; round++) {
            for (String fen : FENS) {
                Board board = new Board(fen);
                AI ai = new AI(board, true);
                ai.maxDepth = Math.min(depth, 5);
                ai.search(board);
            }
        }
        Configuration settings = Configuration.getConfiguration("profile");
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocationBean = bean instanceof com.sun.management.ThreadMXBean extended
                && extended.isThreadAllocatedMemorySupported() ? extended : null;
        if (allocationBean != null) allocationBean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().threadId();
        for (int run = 1; run <= runs; run++) {
            for (int position = 0; position < FENS.length; position++) {
                // Fresh tables prevent immediate hits from an earlier run.
                // Construction and table allocation happen outside the recording.
                Board board = new Board(FENS[position]);
                AI ai = new AI(board, true);
                ai.maxDepth = depth;
                String fen = board.getFEN();
                long hash = board.zobristHash, repetitionHash = board.repetitionHash;
                var repetitions = new HashMap<>(board.repetitionMap);
                Path file = output.resolve(NAMES[position] + "-" + run + ".jfr");
                System.out.printf("Profiling %s, run %d...%n", NAMES[position], run);
                Move best;
                double seconds;
                long allocatedBytes;
                try (Recording recording = new Recording(settings)) {
                    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
                    recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                    recording.start();
                    long allocatedBefore = allocationBean == null ? 0 : allocationBean.getThreadAllocatedBytes(threadId);
                    long start = System.nanoTime();
                    best = ai.search(board);
                    seconds = (System.nanoTime() - start) / 1e9;
                    allocatedBytes = allocationBean == null ? -1
                            : allocationBean.getThreadAllocatedBytes(threadId) - allocatedBefore;
                    recording.stop();
                    recording.dump(file);
                }
                if (best == null || !board.isValidMove(best) || !board.getFEN().equals(fen)
                        || hash != board.zobristHash || repetitionHash != board.repetitionHash
                        || !repetitions.equals(board.repetitionMap))
                    throw new AssertionError("Search returned an illegal move or changed the position");
                append(report, "%n%s run=%d seconds=%.3f best=%s file=%s%n",
                        NAMES[position], run, seconds, uci(best), file.getFileName());
                if (allocatedBytes >= 0) append(report, "Search thread allocated MiB=%.2f%n", allocatedBytes / (1024.0 * 1024));
                else append(report, "Search thread allocation counter unavailable on this JVM.%n");
                summarize(file, report);
            }
        }
        append(report, "%nSamples are estimates, not exact call counts or allocation totals.%n"
                + "Allocation sample percentages are sample shares, not byte or object-count shares.%n"
                + "CPU rows show the top sampled frame in search stacks, not inclusive time.%n"
                + "GC pauses cover the recording and may include garbage from setup/warmup.%n"
                + "Short recordings may need a higher depth to collect useful samples.%n");
        Files.writeString(output.resolve("summary.txt"), report);
        System.out.println("Saved recordings and summary: " + output.toAbsolutePath());
    }

    private static void summarize(Path file, StringBuilder report) throws Exception {
        Map<String, Long> cpu = new HashMap<>(), allocations = new HashMap<>();
        long samples = 0, allocationSamples = 0, collections = 0, pauseNanos = 0;
        try (RecordingFile recording = new RecordingFile(file)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String name = event.getEventType().getName();
                if (name.equals("jdk.GarbageCollection")) {
                    collections++;
                    pauseNanos += event.getDuration("sumOfPauses").toNanos();
                } else if (isSearchSample(event)) {
                    if (name.equals("jdk.ExecutionSample")) {
                        cpu.merge(method(event.getStackTrace().getFrames().get(0)), 1L, Long::sum);
                        samples++;
                    } else if (name.equals("jdk.ObjectAllocationSample")) {
                        allocations.merge(event.getClass("objectClass").getName(), 1L, Long::sum);
                        allocationSamples++;
                    }
                }
            }
        }
        append(report, "CPU samples=%d; allocation samples=%d; GC collections=%d pause_ms=%.3f%n",
                samples, allocationSamples, collections, pauseNanos / 1e6);
        append(report, "Top CPU sample locations (self):%n");
        printTop(cpu, report);
        append(report, "Top classes by allocation sample count (not byte shares):%n");
        printTop(allocations, report);
        if (samples < 100) append(report, "Few CPU samples: treat this ranking as preliminary.%n");
    }

    private static boolean isSearchSample(RecordedEvent event) {
        String type = event.getEventType().getName();
        if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.ObjectAllocationSample")) return false;
        if (event.getStackTrace() == null) return false;
        return event.getStackTrace().getFrames().stream().anyMatch(frame -> {
            String name = method(frame);
            return name.equals("engine.AI.search") || name.equals("engine.AI.negaMax");
        });
    }

    private static String method(RecordedFrame frame) {
        return frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
    }

    private static void printTop(Map<String, Long> values, StringBuilder report) {
        long total = values.values().stream().mapToLong(Long::longValue).sum();
        values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey())).limit(10).forEach(entry ->
                append(report, "  %6.1f%% %8d %s%n", 100.0 * entry.getValue() / total,
                        entry.getValue(), entry.getKey()));
        if (values.isEmpty()) append(report, "  No samples captured.%n");
    }

    private static String uci(Move move) {
        String result = "" + (char) ('a' + move.col) + (8 - move.row)
                + (char) ('a' + move.newCol) + (8 - move.newRow);
        if (move.promotionPiece != null) result += switch (move.promotionPiece) {
            case QUEEN -> "q";
            case ROOK -> "r";
            case BISHOP -> "b";
            case KNIGHT -> "n";
            default -> throw new IllegalArgumentException("Invalid promotion");
        };
        return result;
    }

    private static void append(StringBuilder report, String format, Object... args) {
        String line = String.format(Locale.ROOT, format, args);
        report.append(line);
        System.out.print(line);
    }
}

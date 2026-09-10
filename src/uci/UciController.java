package uci;

import engine.AI;
import engine.SearchRequest;
import main.Board;
import main.Move;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.concurrent.CountDownLatch;

/** The input thread owns commands; one worker owns the board during search. */
public final class UciController implements AutoCloseable {
    private final BufferedReader input;
    private final PrintWriter output;
    private final PrintWriter errors;
    private Board board = new Board();
    private final AI ai = AI.forSearch(board);
    private SearchJob job;

    public UciController(Reader input, PrintWriter output, PrintWriter errors) {
        this.input = new BufferedReader(input);
        this.output = output;
        this.errors = errors;
    }

    public void run() throws IOException {
        try {
            String line;
            while ((line = input.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split("\\s+");
                try {
                    switch (tokens[0]) {
                        case "uci" -> {
                            send("id name Trust Engine");
                            send("id author Trust Engine contributors");
                            send("uciok");
                        }
                        case "isready" -> send("readyok");
                        case "position" -> {
                            stopAndJoin();
                            board = UciPosition.parse(tokens);
                        }
                        case "ucinewgame" -> {
                            stopAndJoin();
                            ai.resetSearchState();
                            board = new Board();
                        }
                        case "go" -> {
                            stopAndJoin();
                            Go limits = parseGo(tokens, board.colorToMove);
                            job = new SearchJob(board, limits);
                            job.worker.start();
                        }
                        case "stop" -> stopAndJoin();
                        case "quit" -> { return; }
                        // No options or UCI pondering advertised in this version.
                        default -> { }
                    }
                } catch (IllegalArgumentException | IllegalStateException e) {
                    error("Ignoring " + tokens[0] + ": " + e.getMessage());
                }
            }
        } finally {
            close();
        }
    }

    private record Go(SearchRequest request, boolean waitForStop) { }

    private static Go parseGo(String[] tokens, int color) {
        int depth = 128, movesToGo = 0;
        long whiteTime = -1, blackTime = -1, whiteIncrement = 0, blackIncrement = 0, moveTime = -1;
        boolean hasDepth = false, infinite = false;
        for (int i = 1; i < tokens.length; i++) {
            String key = tokens[i];
            switch (key) {
                case "depth", "movestogo", "wtime", "btime", "winc", "binc", "movetime" -> {
                    if (++i == tokens.length) throw new IllegalArgumentException("Missing value for " + key);
                    long value = Long.parseLong(tokens[i]);
                    if (value < 0) throw new IllegalArgumentException("Negative " + key);
                    if ((key.equals("depth") || key.equals("movestogo")) && value > Integer.MAX_VALUE)
                        throw new IllegalArgumentException("Value too large for " + key);
                    switch (key) {
                        case "depth" -> { depth = Math.toIntExact(value); hasDepth = true; }
                        case "movestogo" -> movesToGo = Math.toIntExact(value);
                        case "wtime" -> whiteTime = value;
                        case "btime" -> blackTime = value;
                        case "winc" -> whiteIncrement = value;
                        case "binc" -> blackIncrement = value;
                        case "movetime" -> moveTime = value;
                    }
                }
                case "infinite" -> infinite = true;
                case "ponder", "searchmoves", "nodes", "mate" ->
                        throw new IllegalArgumentException("Unsupported go limit: " + key);
                default -> { } // Ignore unknown tokens as specified by UCI.
            }
        }
        if (infinite) return new Go(SearchRequest.depth(depth), true);
        if (moveTime >= 0) return new Go(SearchRequest.fixedTime(depth, moveTime), false);
        long remaining = color == 0 ? whiteTime : blackTime;
        long increment = color == 0 ? whiteIncrement : blackIncrement;
        if (remaining >= 0) return new Go(SearchRequest.forClock(depth, remaining, increment, movesToGo), false);
        if (whiteTime >= 0 || blackTime >= 0) throw new IllegalArgumentException("Missing side-to-move clock");
        return new Go(SearchRequest.depth(depth), !hasDepth);
    }

    private final class SearchJob {
        final SearchRequest request;
        final CountDownLatch stopped = new CountDownLatch(1);
        final Thread worker;
        SearchJob(Board position, Go limits) {
            request = limits.request();
            worker = new Thread(() -> {
                Move best = null;
                long start = System.nanoTime();
                try {
                    best = ai.search(position, request);
                    // Infinite search must not emit bestmove early, even in terminal positions.
                    if (limits.waitForStop()) stopped.await();
                } catch (InterruptedException e) {
                    request.stop();
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    error("Search failed: " + e.getMessage());
                } finally {
                    send("info depth " + ai.getCompletedDepth() + " time " + (System.nanoTime() - start) / 1_000_000);
                    send("bestmove " + UciPosition.format(best));
                }
            }, "uci-search");
        }
        void stop() {
            request.stop();
            stopped.countDown();
        }
    }

    private void stopAndJoin() {
        if (job == null) return;
        job.stop();
        boolean interrupted = false;
        while (job.worker.isAlive()) {
            try { job.worker.join(); }
            catch (InterruptedException e) { interrupted = true; }
        }
        job = null;
        if (interrupted) Thread.currentThread().interrupt();
    }

    private synchronized void send(String line) {
        output.println(line);
        output.flush();
    }

    private synchronized void error(String line) {
        errors.println(line);
        errors.flush();
    }

    @Override public void close() {
        stopAndJoin();
    }
}

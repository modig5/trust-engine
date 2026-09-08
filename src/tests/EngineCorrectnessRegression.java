package tests;

import Pieces.Piece;
import engine.AI;
import engine.AttackTables;
import engine.MoveGen;
import engine.PerftCounter;
import engine.TranspositionTable;
import engine.Zobrist;
import main.Board;
import main.Move;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;

public final class EngineCorrectnessRegression {
    public static void main(String[] args) throws Exception {
        attacks();
        table();
        specialRules();
        drawCacheContext();
        perftModes();
        perftDrawState();
        cacheCollisions();
        System.out.println("Engine correctness regression checks passed");
    }

    private static void attacks() {
        for (int source = 0; source < 64; source++) {
            long knight = 0, king = 0, white = 0, black = 0;
            for (int target = 0; target < 64; target++) {
                int dx = target % 8 - source % 8, dy = target / 8 - source / 8;
                if (Math.abs(dx) * Math.abs(dy) == 2) knight |= 1L << target;
                if (Math.max(Math.abs(dx), Math.abs(dy)) == 1) king |= 1L << target;
                if (Math.abs(dx) == 1 && dy == -1) white |= 1L << target;
                if (Math.abs(dx) == 1 && dy == 1) black |= 1L << target;
            }
            require(AttackTables.knightAttacks[source] == knight && AttackTables.kingAttacks[source] == king
                    && AttackTables.whitePawnAttacks[source] == white && AttackTables.blackPawnAttacks[source] == black,
                    "Attack mask wraps an edge at " + source);
        }
    }

    private static void table() {
        TranspositionTable table = new TranspositionTable(1);
        for (long key : new long[]{0, Long.MIN_VALUE, Long.MAX_VALUE, -1}) {
            table.clear();
            require(table.probe(key, 0, -100, 100) == Integer.MIN_VALUE, "Empty table returned a score");
            table.store(key, 3, 99, TranspositionTable.EXACT, 42);
            require(table.probe(key, 3, -100, 100) == 99, "Extreme key not retrievable");
            table.clear();
            require(table.probe(key, 3, -100, 100) == Integer.MIN_VALUE
                    && table.getBestMove(key) == TranspositionTable.NO_MOVE, "Clear left a stale entry");
        }
    }

    private static void specialRules() {
        Board phantom = new Board("4k3/8/8/4P3/8/8/8/4K3 w - d6 0 1");
        require(!phantom.isValidMove(new Move(phantom, phantom.getPiece(4, 3), 3, 2)), "Phantom EP accepted");
        require(new MoveGen(phantom).getAllValidMoves().stream().noneMatch(m -> m.newCol == 3 && m.newRow == 2),
                "Generator emitted phantom EP");
        Board absent = new Board("4k3/8/8/4P3/8/8/8/4K3 w - - 0 1");
        require(phantom.repetitionHash == absent.repetitionHash, "Ineffective EP changed repetition identity");
        Board pinned = new Board("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        Board pinnedAbsent = new Board("4k3/8/8/r4pPK/8/8/8/8 w - - 0 1");
        require(pinned.repetitionHash == pinnedAbsent.repetitionHash, "Pinned EP changed repetition identity");
        Board legal = new Board("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        Board legalAbsent = new Board("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 1");
        require(legal.repetitionHash != legalAbsent.repetitionHash, "Legal EP was omitted from repetition identity");
        ArrayList<Piece> order = new ArrayList<>(legal.pieceList);
        long hash = legal.zobristHash;
        new MoveGen(legal).getAllValidMoves();
        require(legal.pieceList.equals(order) && legal.zobristHash == hash, "Legality checks mutated pieces/hash");

        Board mate = new Board("7k/6Q1/5K2/8/8/8/8/8 b - - 100 1");
        AI mateAI = new AI(mate);
        require(mateAI.negaMax(0, -Integer.MAX_VALUE, Integer.MAX_VALUE) == -mateAI.kingVal,
                "Leaf checkmate lost to evaluation or clock draw");
        Board stale = new Board("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
        require(new AI(stale).negaMax(0, -Integer.MAX_VALUE, Integer.MAX_VALUE) == 0, "Leaf stalemate not drawn");
        Board clock = new Board("4k3/8/8/8/8/8/P7/4K2R w - - 100 1");
        require(clock.drawByHalfMoveClock, "Loaded clock flag not set");
        require(new AI(clock).negaMax(1, -Integer.MAX_VALUE, Integer.MAX_VALUE) == 0, "Clock draw ignored");
        Board bare = new Board("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        require(new AI(bare).negaMax(0, -Integer.MAX_VALUE, Integer.MAX_VALUE) == 0, "Leaf material draw ignored");
    }

    private static void perftModes() {
        for (int p = 0; p < PerftBenchmark.FENS.length; p++) {
            Board board = new Board(PerftBenchmark.FENS[p]);
            for (PerftCounter counter : new PerftCounter[]{new PerftCounter(false, 0),
                    new PerftCounter(true, 0), new PerftCounter(true, 1)}) {
                long count = counter.count(board, 4, 120_000);
                require(count == PerftBenchmark.EXPECTED[p][4], "Position " + (p + 1) + " mode disagrees: " + count);
                require(board.zobristHash == Zobrist.computeHash(board), "Perft corrupted hash");
            }
            System.out.println("All perft modes position " + (p + 1) + " d4=" + PerftBenchmark.EXPECTED[p][4]);
        }
        Board mirrored = new Board("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1");
        require(new PerftCounter(true, 1).count(mirrored, 4, 120_000) == 422333, "Mirrored promotion perft mismatch");
        Board board = new Board();
        String fen = board.getFEN();
        long hash = board.zobristHash;
        var repetitions = new java.util.HashMap<>(board.repetitionMap);
        try {
            new PerftCounter(false, 0).count(board, 7, 1);
            throw new AssertionError("Expected perft timeout");
        } catch (CancellationException expected) {
            require(board.getFEN().equals(fen) && board.zobristHash == hash && board.repetitionMap.equals(repetitions),
                    "Timeout did not unwind board");
        }
    }

    private static int searchScore(AI ai) {
        return ai.negaMax(3, -Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private static void drawCacheContext() {
        Board clock = new Board("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        AI clockAI = new AI(clock);
        int ordinary = searchScore(clockAI);
        clock.halfMoveCounter = 99;
        require(ordinary != 0 && searchScore(clockAI) == 0
                        && searchScore(new AI(new Board(clock))) == 0,
                "Cached score leaked across the 50-move horizon");

        Board repeated = new Board("7k/8/5K2/6Q1/8/8/8/8 b - - 0 1");
        AI repeatedAI = new AI(repeated);
        int withoutHistory = searchScore(repeatedAI);
        var moves = new MoveGen(repeated).getAllValidMoves();
        require(moves.size() == 1, "Repetition fixture must have one forced move");
        Move undo = repeated.makeMove(moves.get(0), true);
        long child = repeated.repetitionHash;
        int expected = -repeatedAI.repetitionScore();
        repeated.undoMove(undo);
        repeated.repetitionMap.put(child, 2);
        require(withoutHistory != expected && searchScore(repeatedAI) == expected
                        && searchScore(new AI(new Board(repeated))) == expected,
                "Cached score leaked across repetition history");
    }

    private static void perftDrawState() {
        for (PerftCounter counter : new PerftCounter[]{new PerftCounter(false, 0),
                new PerftCounter(true, 0), new PerftCounter(true, 1)}) {
            Board board = new Board(PerftBenchmark.FENS[0].replace("0 1", "100 51"));
            board.threefold = true;
            board.repetitionMap.put(board.repetitionHash, 3);
            board.selectedPiece = board.getPiece(0, 6);
            Piece selected = board.selectedPiece;
            String fen = board.getFEN();
            long hash = board.zobristHash, repetitionHash = board.repetitionHash;
            var history = new java.util.HashMap<>(board.repetitionMap);
            require(counter.count(board, 4, 120_000) == 197281, "Game draws terminated perft");
            require(board.threefold && board.drawByHalfMoveClock && board.halfMoveCounter == 100
                            && board.fullMoveNumber == 51 && board.zobristHash == hash
                            && board.repetitionHash == repetitionHash && board.selectedPiece == selected
                            && board.getFEN().equals(fen) && board.repetitionMap.equals(history),
                    "Perft did not restore draw or selection state");
        }
    }

    private static void cacheCollisions() throws Exception {
        PerftCounter counter = new PerftCounter(true, 1);
        var field = PerftCounter.class.getDeclaredField("cache");
        field.setAccessible(true);
        Object cache = field.get(counter);
        var store = cache.getClass().getDeclaredMethod("store", Board.class, int.class, long.class);
        var probe = cache.getClass().getDeclaredMethod("probe", Board.class, int.class);
        store.setAccessible(true); probe.setAccessible(true);
        Board a = new Board();
        Board b = new Board("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
        store.invoke(cache, a, 2, 400L);
        b.zobristHash = a.zobristHash; // Force a complete hash collision, not only an index collision.
        require((long) probe.invoke(cache, b, 2) == -1, "Cache trusted hash collision");
        require((long) probe.invoke(cache, a, 3) == -1, "Cache ignored depth");
        a.getPiece(4, 7).isFirstMove = false;
        require((long) probe.invoke(cache, a, 2) == -1, "Cache ignored first-move rights");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

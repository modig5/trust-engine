package tests;

import engine.AI;
import engine.MoveGen;
import main.Board;
import main.Move;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/** Run with: java -Djava.awt.headless=true -cp build/classes tests.SearchRegression */
public class SearchRegression {
    private record RootCall(int depth, int beta) {}

    private static class RecordingAI extends AI {
        final ArrayList<RootCall> rootCalls = new ArrayList<>();
        int nesting;

        RecordingAI(Board board) {
            super(board);
        }

        @Override
        public int negaMax(int depth, int alpha, int beta) {
            if (nesting == 0) rootCalls.add(new RootCall(depth, beta));
            nesting++;
            try {
                return super.negaMax(depth, alpha, beta);
            } finally {
                nesting--;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        check("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        check("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1");
        System.out.println("Search regression checks passed");
    }

    private static void check(String fen) throws Exception {
        Board board = new Board(fen);
        RecordingAI ai = new RecordingAI(board);
        ai.maxDepth = 3;
        MoveGen generator = new MoveGen(board);
        var legalMoves = generator.getAllValidMoves();
        long originalHash = board.zobristHash;
        String originalFen = board.getFEN();
        var originalRepetitions = new HashMap<>(board.repetitionMap);

        Method search = AI.class.getDeclaredMethod("search", Board.class);
        search.setAccessible(true);
        Move chosen = (Move) search.invoke(ai, board);
        require(chosen != null, "Search returned no move");
        require(ai.rootCalls.size() == legalMoves.size() * ai.maxDepth,
                "Unexpected number of root searches");
        for (int depth = 1; depth <= ai.maxDepth; depth++) {
            int offset = (depth - 1) * legalMoves.size();
            require(ai.rootCalls.get(offset).beta() == Integer.MAX_VALUE,
                    "Root alpha must reset for each iteration");
            for (int i = 0; i < legalMoves.size(); i++) {
                RootCall call = ai.rootCalls.get(offset + i);
                require(call.depth() == depth - 1, "Search skipped an iterative depth");
                if (i > 0) require(call.beta() < Integer.MAX_VALUE,
                        "Later root moves still use a full window");
            }
        }

        // Compare against exhaustive minimax, independent of pruning and the TT.
        int bestScore = Integer.MIN_VALUE;
        int chosenScore = Integer.MIN_VALUE;
        for (Move move : legalMoves) {
            Move undo = board.makeMove(move, true);
            int score = -minimax(board, ai, generator, ai.maxDepth - 1);
            board.undoMove(undo);
            bestScore = Math.max(bestScore, score);
            if (move.col == chosen.col && move.row == chosen.row
                    && move.newCol == chosen.newCol && move.newRow == chosen.newRow
                    && move.promotionPiece == chosen.promotionPiece) chosenScore = score;
        }
        require(chosenScore == bestScore, "Root pruning changed the best score");
        require(board.getFEN().equals(originalFen) && board.zobristHash == originalHash
                        && board.repetitionMap.equals(originalRepetitions),
                "Search did not restore the board");
    }

    private static int minimax(Board board, AI ai, MoveGen generator, int depth) {
        if (depth == 0) return ai.evaluate();
        if (board.scanner.insufficientMaterial()) return 0;
        var moves = generator.getAllValidMoves();
        if (moves.isEmpty()) {
            var king = board.scanner.findKing(board.colorToMove);
            return board.scanner.isInCheck(king.col, king.row, king.color)
                    ? -ai.kingVal - depth : 0;
        }
        int best = Integer.MIN_VALUE;
        for (Move move : moves) {
            Move undo = board.makeMove(move, true);
            best = Math.max(best, -minimax(board, ai, generator, depth - 1));
            board.undoMove(undo);
        }
        return best;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

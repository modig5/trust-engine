package tests;

import java.util.ArrayList;

import engine.MoveGen;
import main.Board;
import main.Move;

/*

PERFT results are compared against the results on:
https://www.chessprogramming.org/Perft_Results

Currently only supports the starting FEN position.

*/

public class Perft {

    // Expected perft values for starting position (from chessprogramming.org)
    private static long[] EXPECTED = {
        1L,
        20L,
        400L,
        8902L,
        197281L,
        4865609L,
        119060324L
    };

    public static void main(String args[]) {
        System.out.println("┌─────────────────────────────────────────────────────────┐");
        System.out.println("│                    PERFT TEST SUITE                     │");
        System.out.println("├───────┬──────────────┬──────────────┬─────────┬─────────┤");
        System.out.println("│ Depth │     Result   │   Expected   │ Status  │  Time   │");
        System.out.println("├───────┼──────────────┼──────────────┼─────────┼─────────┤");

        Board board = new Board();
        int passed = 0;
        int maxDepth = Math.min(6, EXPECTED.length - 1);

        for (int depth = 0; depth <= maxDepth; depth++) {
            long startTime = System.currentTimeMillis();
            long result = perft(depth, board, new MoveGen(board));
            long elapsed = System.currentTimeMillis() - startTime;

            long expected = EXPECTED[depth];
            boolean correct = result == expected;
            if (correct) passed++;

            String status = correct ? "PASS" : "FAIL";
            String timeStr = elapsed < 1000 ? elapsed + "ms" : String.format("%.2fs", elapsed / 1000.0);

            System.out.printf("│ %5d │ %12d │ %12d │  %s   │ %7s │%n",
                depth, result, expected, status, timeStr);
        }

        System.out.println("└───────┴──────────────┴──────────────┴─────────┴─────────┘");
        System.out.printf("%nResults: %d/%d tests passed%n", passed, maxDepth + 1);
    }

    private static long perft(int depth, Board board, MoveGen moveGen) {
        long nodes = 0;

        if (depth == 0) return 1L;

        ArrayList<Move> validMoves = moveGen.getAllValidMoves();
        for (Move move : validMoves) {
            Move undoInfo = board.makeMove(move, true);
            nodes += perft(depth - 1, board, moveGen);
            board.undoMove(undoInfo);
        }

        return nodes;
    }
}

package engine;

import Pieces.Piece;
import main.Board;
import main.Move;

import java.util.ArrayList;

import static main.Board.pieceList;

public class AI {
    public int depth = 4;
    Board board;
    private MoveGen moveGenerator;

    public final int pawnVal = 100;
    public final int knightVal = 320;
    public final int bishopVal = 330;
    public final int rookVal = 500;
    public final int queenVal = 900;
    public final int kingVal = 100000;

    private static final int[][] PAWN_TABLE = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {50, 50, 50, 50, 50, 50, 50, 50},
            {10, 10, 20, 30, 30, 20, 10, 10},
            {5, 5, 10, 25, 25, 10, 5, 5},
            {0, 0, 0, 20, 20, 0, 0, 0},
            {5, -5, -10, 0, 0, -10, -5, 5},
            {5, 10, 10, -20, -20, 10, 10, 5},
            {0, 0, 0, 0, 0, 0, 0, 0}
    };

    // Knight position values
    private static final int[][] KNIGHT_TABLE = {
            {-50, -40, -30, -30, -30, -30, -40, -50},
            {-40, -20, 0, 0, 0, 0, -20, -40},
            {-30, 0, 10, 15, 15, 10, 0, -30},
            {-30, 5, 15, 20, 20, 15, 5, -30},
            {-30, 0, 15, 20, 20, 15, 0, -30},
            {-30, 5, 10, 15, 15, 10, 5, -30},
            {-40, -20, 0, 5, 5, 0, -20, -40},
            {-50, -40, -30, -30, -30, -30, -40, -50}
    };

    // Bishop position values
    private static final int[][] BISHOP_TABLE = {
            {-20, -10, -10, -10, -10, -10, -10, -20},
            {-10, 0, 0, 0, 0, 0, 0, -10},
            {-10, 0, 5, 10, 10, 5, 0, -10},
            {-10, 5, 5, 10, 10, 5, 5, -10},
            {-10, 0, 10, 10, 10, 10, 0, -10},
            {-10, 10, 10, 10, 10, 10, 10, -10},
            {-10, 5, 0, 0, 0, 0, 5, -10},
            {-20, -10, -10, -10, -10, -10, -10, -20}
    };

    // Rook position values
    private static final int[][] ROOK_TABLE = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {5, 10, 10, 10, 10, 10, 10, 5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {0, 0, 0, 5, 5, 0, 0, 0}
    };

    // Queen position values
    private static final int[][] QUEEN_TABLE = {
            {-20, -10, -10, -5, -5, -10, -10, -20},
            {-10, 0, 0, 0, 0, 0, 0, -10},
            {-10, 0, 5, 5, 5, 5, 0, -10},
            {-5, 0, 5, 5, 5, 5, 0, -5},
            {0, 0, 5, 5, 5, 5, 0, -5},
            {-10, 5, 5, 5, 5, 5, 0, -10},
            {-10, 0, 5, 0, 0, 0, 0, -10},
            {-20, -10, -10, -5, -5, -10, -10, -20}
    };

    // King position values (middle game)
    private static final int[][] KING_TABLE = {
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-20, -30, -30, -40, -40, -30, -30, -20},
            {-10, -20, -20, -20, -20, -20, -20, -10},
            {20, 20, 0, 0, 0, 0, 20, 20},
            {20, 50, 0, 0, 0, 0, 50, 20}
    };

    public AI(Board board) {
        this.board = board;
        this.moveGenerator = new MoveGen(board);
    }

    public int miniMax(int depth, int alpha, int beta) {
        if (depth == 0) {
            return evaluate();
        }

        ArrayList<Move> moves = getAllValidMoves();

        if (moves.isEmpty()) {
            Piece king = board.scanner.findKing(board.colorToMove);
            if (board.scanner.isInCheck(king.col, king.row, board.colorToMove)) {
                return -kingVal - depth;
            } else {
                return 0; // Stalemate
            }
        }

        System.out.println(board.repetitionMap.get(board.FEN));

        if (board.repetitionMap.get(board.FEN) >= 3) {
            System.out.println("threefold repetition trust");
            return 0; // Draw by repetition
        }

        for (Move move : moves) {
            Move undoInfo = board.makeMove(move, true);
            int eval = -miniMax(depth - 1, -beta, -alpha);
            board.undoMove(undoInfo);

            if (eval >= beta)
                return beta;

            alpha = Math.max(alpha, eval);
        }
        return alpha;
    }

    public int evaluate() {
        int whiteScore = countMaterial(0);
        int blackScore = countMaterial(1);

        for (Piece piece : pieceList) {
            int posValue = countPositionalValue(piece);
            if (piece.color == 0)
                whiteScore += posValue;
            else
                blackScore += posValue;
        }

        // for endgames
        int endGameWeight = 32 - pieceList.size();
        int endgameScore = forceKingCorner(board.colorToMove, endGameWeight);

        int eval = whiteScore - blackScore + endgameScore;

        if (board.colorToMove == 1)
            return -eval;
        return eval;
    }

    public int countMaterial(int color) {
        int materialScore = 0;

        materialScore += board.countPieces(color, "Pawn") * pawnVal;
        materialScore += board.countPieces(color, "Knight") * knightVal;
        materialScore += board.countPieces(color, "Bishop") * bishopVal;
        materialScore += board.countPieces(color, "Queen") * queenVal;
        materialScore += board.countPieces(color, "Rook") * rookVal;
        materialScore += board.countPieces(color, "King") * kingVal;

        return materialScore;
    }

    public int convertPieceToMaterial(Piece piece) {
        return switch (piece.name) {
            case "Pawn" -> pawnVal;
            case "Knight" -> knightVal;
            case "Bishop" -> bishopVal;
            case "Rook" -> rookVal;
            case "Queen" -> queenVal;
            case "King" -> kingVal;
            default -> {
                System.err.println("Warning: unknown piece type: " + piece.name);
                yield 0;
            }
        };
    }

    public int countPositionalValue(Piece piece) {
        int row = piece.row;
        int col = piece.col;

        if (piece.color == 1)
            row = 7 - row;

        if (row < 0 || row > 7 || col < 0 || col > 7) {
            System.err.println("Warning: piece has invalid coordinates: " + piece.name + " color=" + piece.color + " row=" + piece.row + " col=" + piece.col);
            return 0;
        }

        return switch (piece.name) {
            case "Pawn" -> PAWN_TABLE[row][col];
            case "Knight" -> KNIGHT_TABLE[row][col];
            case "Bishop" -> BISHOP_TABLE[row][col];
            case "Rook" -> ROOK_TABLE[row][col];
            case "Queen" -> QUEEN_TABLE[row][col];
            case "King" -> KING_TABLE[row][col];
            default -> 0;
        };
    }

    public int forceKingCorner(int color, int endgameWeight) {
        int evaluation = 0;
        Piece opponentKing = board.scanner.findKing(color^1);
        Piece ownKing = board.scanner.findKing(color);

        int opponentKingCol = opponentKing.col;
        int opponentKingRow = opponentKing.row;

        int opponentKingDstToCentreCol = Math.max(3 - opponentKingCol, opponentKingCol - 4);
        int opponentKingDstToCentreRow = Math.max(3 - opponentKingRow, opponentKingRow - 4);
        int opponentKingDstFromCentre = opponentKingDstToCentreCol + opponentKingDstToCentreRow;
        evaluation += (int) (opponentKingDstFromCentre * 3.5);

        int friendlyKingCol = ownKing.col;
        int friendlyKingRow = ownKing.row;

        int dstBetweenKingCol = Math.abs(friendlyKingCol - opponentKingCol);
        int dstBetweenKingRow = Math.abs(friendlyKingRow - opponentKingRow);
        int dstBetweenKings = dstBetweenKingCol + dstBetweenKingRow;
        evaluation += 14 - dstBetweenKings;

        int endgameEval = evaluation * endgameWeight;

        if (color == 1)
            return -(endgameEval);

        return endgameEval;
    }

    public void makeAIMove() {
        //System.out.println("AI makeAIMove() called - starting to think...");

        if (board.repetitionMap.get(board.FEN) >= 3) {
            return;
        }

        ArrayList<Move> validMoves = getAllValidMoves();

        if (validMoves.isEmpty()) {
            return;
        }

        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : validMoves) {
            Move undoInfo = board.makeMove(move, true);
            int score = -miniMax(depth - 1, -Integer.MAX_VALUE, Integer.MAX_VALUE);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            board.undoMove(undoInfo);
        }
        System.out.println(bestScore);
        //System.out.println("AI finished thinking");

        if (bestMove != null) {
            board.makeMove(bestMove, false);
        } else {
            System.out.println("ERROR: No best move found!");
        }
    }


    private ArrayList<Move> getAllValidMoves() {
        ArrayList<Move> moves = moveGenerator.getAllValidMoves();

        moves.sort((a, b) -> Integer.compare(getMoveScore(b), getMoveScore(a)));

        return moves;
    }

    private int getMoveScore(Move move) {
        int score = 0;
        if (move.capture != null) score += convertPieceToMaterial(move.capture) * 1000;
        if (move.wasPromotion) score += 9000;
        return score;
    }
}

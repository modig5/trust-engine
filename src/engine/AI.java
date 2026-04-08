package engine;

import Pieces.Piece;
import Pieces.PieceType;
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

    int CONTEMPT_FACTOR = 15; // value for adjusting when to prefer repetition
    int WINNING_MARGIN = 70; // value that determines winning positions

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

    // Rook position values (rewards the 7th rank from the piece's own perspective)
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

    // King position values, middle game (from the piece's own perspective)
    private static final int[][] KING_TABLE = {
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-20, -30, -30, -40, -40, -30, -30, -20},
            {-10, -20, -20, -20, -20, -20, -20, -10},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {20, 50, 30, 0, 0, 0, 50, 20}
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

        if (board.scanner.insufficientMaterial()) {
            return 0;
        }

        if (board.repetitionMap.getOrDefault(board.FEN, 0) >= 3) {
            return repetitionScore();
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

    public int repetitionScore() {
        int currentEval = evaluate();

        // Punish draws in winning positions and reward them in losing positions
        if (currentEval > WINNING_MARGIN) {
            return -CONTEMPT_FACTOR;
        } else if (currentEval < -WINNING_MARGIN) {
            return CONTEMPT_FACTOR;
        }
        return 0;
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

        materialScore += board.countPieces(color, PieceType.PAWN) * pawnVal;
        materialScore += board.countPieces(color, PieceType.KNIGHT) * knightVal;
        materialScore += board.countPieces(color, PieceType.BISHOP) * bishopVal;
        materialScore += board.countPieces(color, PieceType.QUEEN) * queenVal;
        materialScore += board.countPieces(color, PieceType.ROOK) * rookVal;
        materialScore += board.countPieces(color, PieceType.KING) * kingVal;

        return materialScore;
    }

    public int convertPieceToMaterial(Piece piece) {
        return switch (piece.type) {
            case PAWN -> pawnVal;
            case KNIGHT -> knightVal;
            case BISHOP -> bishopVal;
            case ROOK -> rookVal;
            case QUEEN -> queenVal;
            case KING -> kingVal;
        };
    }

    public int countPositionalValue(Piece piece) {
        int row = piece.row;
        int col = piece.col;

        if (piece.color == 1)
            row = 7 - row;

        if (row < 0 || row > 7 || col < 0 || col > 7) {
            System.err.println("Warning: piece has invalid coordinates: " + piece.type + " color=" + piece.color + " row=" + piece.row + " col=" + piece.col);
            return 0;
        }

        return switch (piece.type) {
            case PAWN -> PAWN_TABLE[row][col];
            case KNIGHT -> KNIGHT_TABLE[row][col];
            case BISHOP -> BISHOP_TABLE[row][col];
            case ROOK -> ROOK_TABLE[row][col];
            case QUEEN -> QUEEN_TABLE[row][col];
            case KING -> KING_TABLE[row][col];
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
        if (board.repetitionMap.getOrDefault(board.FEN, 0) >= 3) {
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

        //System.out.println(bestScore);
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

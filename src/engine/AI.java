package engine;

import Pieces.Piece;
import Pieces.PieceType;
import main.Board;
import main.BoardFenHelper;
import main.Move;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


public class AI {
    public int maxDepth = 4;
    Board board;
    private MoveGen moveGenerator;
    private final OpeningBook book;

    public final int pawnVal = 100;
    public final int knightVal = 320;
    public final int bishopVal = 330;
    public final int rookVal = 500;
    public final int queenVal = 900;
    public final int kingVal = 100000;

    int CONTEMPT_FACTOR = 15; // value for adjusting when to prefer repetition
    int WINNING_MARGIN = 70; // value that determines winning positions

    // Pondering state
    private Thread ponderThread;
    private AI ponderAIRef;
    public Move ponderMove;
    private Move ponderBestMove;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean pondering = new AtomicBoolean(false);

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
            {-10, 10, 0, 0, 0, 0, 10, -10},
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
        this.book = new OpeningBook("src/resources/Opening.bin", board);
    }

    // Lightweight constructor for pondering (no opening book)
    private AI(Board board, boolean searchOnly) {
        this.board = board;
        this.moveGenerator = new MoveGen(board);
        this.book = null;
    }

    public int negaMax(int maxDepth, int alpha, int beta) {
        if (stopRequested.get()) {
            return 0;
        }

        if (maxDepth == 0) {
            return evaluate();
        }

        ArrayList<Move> moves = getAllValidMoves();

        if (moves.isEmpty()) {
            Piece king = board.scanner.findKing(board.colorToMove);
            if (board.scanner.isInCheck(king.col, king.row, board.colorToMove)) {
                return -kingVal - maxDepth;
            } else {
                return 0; // Stalemate
            }
        }

        if (board.scanner.insufficientMaterial()) {
            return 0;
        }

        if (board.repetitionMap.getOrDefault(BoardFenHelper.repetitionKey(board.FEN), 0) >= 3) {
            return repetitionScore();
        }

        for (Move move : moves) {
            Move undoInfo = board.makeMove(move, true);
            int eval = -negaMax(maxDepth - 1, -beta, -alpha);
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

        for (Piece piece : board.pieceList) {
            int posValue = countPositionalValue(piece);
            if (piece.color == 0)
                whiteScore += posValue;
            else
                blackScore += posValue;
        }

        // for endgames
        int endGameWeight = 32 - board.pieceList.size();
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
        if (board.repetitionMap.getOrDefault(BoardFenHelper.repetitionKey(board.FEN), 0) >= 3) {
            return;
        }

        ArrayList<Move> validMoves = getAllValidMoves();

        if (validMoves.isEmpty()) {
            return;
        }

        // Check for an opening move, if null then continue normally
        OpeningBook.BookEntry bookEntry = book.pickMove();
        if (bookEntry != null) {
            Move bookMove = book.decodeMove(bookEntry.polyMove);
            if (bookMove != null) {
                board.makeMove(bookMove, false);
                return;
            }
        }

        Move bestMove = search(board);

        if (bestMove != null) {
            board.makeMove(bestMove, false);

            ponderMove = findPonderMove();
            if (ponderMove != null) {
                System.out.print("Pondering: ");
                Move.printMove(ponderMove);
                startPonder();
            }
        } else {
            System.out.println("ERROR: No best move found!");
        }
    }

    // Core search: iterative deepening negamax on the given board
    private Move search(Board searchBoard) {
        Board originalBoard = this.board;
        MoveGen originalMoveGen = this.moveGenerator;

        this.board = searchBoard;
        this.moveGenerator = new MoveGen(searchBoard);

        stopRequested.set(false);
        Move bestMove = null;

        ArrayList<Move> validMoves = getAllValidMoves();

        for (int d = 0; d < maxDepth; d++) {
            Move iterationBestMove = null;
            int iterationScore = Integer.MIN_VALUE;

            bestMoveToFront(bestMove, validMoves);

            for (Move move : validMoves) {
                Move undoInfo = searchBoard.makeMove(move, true);
                int score = -negaMax(maxDepth - 1, -Integer.MAX_VALUE, Integer.MAX_VALUE);
                if (score > iterationScore) {
                    iterationScore = score;
                    iterationBestMove = move;
                }
                searchBoard.undoMove(undoInfo);

                if (stopRequested.get()) break;
            }

            if (stopRequested.get()) break;
            bestMove = iterationBestMove;
        }

        this.board = originalBoard;
        this.moveGenerator = originalMoveGen;

        return bestMove;
    }

    // Find the opponent's best reply to the AI's last move
    private Move findPonderMove() {
        Board copy = new Board(board);
        AI tempAI = new AI(copy, true);
        tempAI.maxDepth = this.maxDepth;

        MoveGen tempMoveGen = new MoveGen(copy);
        ArrayList<Move> opponentMoves = tempMoveGen.getAllValidMoves();
        if (opponentMoves.isEmpty()) return null;

        Move bestReply = null;
        int bestScore = Integer.MIN_VALUE;

        for (Move move : opponentMoves) {
            Move undoInfo = copy.makeMove(move, true);
            int score = -tempAI.negaMax(maxDepth - 1, -Integer.MAX_VALUE, Integer.MAX_VALUE);
            if (score > bestScore) {
                bestScore = score;
                bestReply = move;
            }
            copy.undoMove(undoInfo);
        }

        return bestReply;
    }

    // Start pondering on a background thread using a board copy and separate AI
    private void startPonder() {
        // Copy the board and apply the predicted opponent reply
        Board ponderBoard = new Board(board);
        Piece ponderPiece = ponderBoard.getPiece(ponderMove.col, ponderMove.row);
        if (ponderPiece == null) return;

        Move ponderMoveOnCopy = new Move(ponderBoard, ponderPiece, ponderMove.newCol, ponderMove.newRow);
        ponderMoveOnCopy.promotionPiece = ponderMove.promotionPiece;
        ponderBoard.makeMove(ponderMoveOnCopy, true);

        // Create a separate AI for the ponder thread to avoid shared state
        AI ponderAI = new AI(ponderBoard, true);
        ponderAI.maxDepth = this.maxDepth;
        this.ponderAIRef = ponderAI;

        pondering.set(true);
        ponderBestMove = null;

        ponderThread = new Thread(() -> {
            Move result = ponderAI.search(ponderBoard);
            if (!ponderAI.stopRequested.get()) {
                ponderBestMove = result;
            }
            pondering.set(false);
        });
        ponderThread.setDaemon(true);
        ponderThread.start();
    }

    // Stop the ponder search and wait for the thread to finish
    public void stopPonder() {
        if (ponderThread != null && ponderThread.isAlive()) {
            if (ponderAIRef != null) {
                ponderAIRef.stopRequested.set(true);
            }
            try {
                ponderThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pondering.set(false);
        ponderAIRef = null;
    }

    // Check if the opponent played the move we predicted
    public boolean isPonderHit(Move opponentMove) {
        if (ponderMove == null || opponentMove == null) return false;
        return ponderMove.col == opponentMove.col
                && ponderMove.row == opponentMove.row
                && ponderMove.newCol == opponentMove.newCol
                && ponderMove.newRow == opponentMove.newRow;
    }

    // Get the best move found during pondering (null if ponder miss or not ready)
    public Move getPonderBestMove() {
        return ponderBestMove;
    }

    public void clearPonderState() {
        ponderMove = null;
        ponderBestMove = null;
    }

    // Find the next ponder move and start pondering (used after ponder hits)
    public void startNewPonder() {
        new Thread(() -> {
            ponderMove = findPonderMove();
            if (ponderMove != null) {
                System.out.print("Pondering: ");
                Move.printMove(ponderMove);
                startPonder();
            }
        }).start();
    }

    // Used to move the best move to the front of the list in makeAIMove
    private void bestMoveToFront(Move bestMove, ArrayList<Move> validMoves) {
        if (bestMove != null) {
            for (int i = 1; i < validMoves.size(); i++) {
                if (validMoves.get(i) == bestMove) {
                    validMoves.remove(i);
                    validMoves.add(0, bestMove);
                    break;
                }
            }
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

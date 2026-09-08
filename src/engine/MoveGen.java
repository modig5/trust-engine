package engine;

import Pieces.Piece;
import Pieces.PieceType;
import main.Board;
import main.Move;

import java.util.ArrayList;


public class MoveGen {
    private final Board board;

    public MoveGen(Board board) {
        this.board = board;
    }

    public ArrayList<Move> getAllValidMoves() {
        ArrayList<Move> validMoves = new ArrayList<>();
        long safeOrigins = board.scanner.safeNonKingOrigins(board.colorToMove);
        for (Piece piece : board.pieceList) {
            if (piece == null || piece.color != board.colorToMove)
                continue;
            generateMovesForPiece(piece, validMoves, safeOrigins);

        }
        return validMoves;
    }

    public boolean hasLegalMove() {
        ArrayList<Move> moves = new ArrayList<>();
        long safeOrigins = board.scanner.safeNonKingOrigins(board.colorToMove);
        for (Piece piece : board.pieceList) {
            if (piece.color != board.colorToMove) continue;
            generateMovesForPiece(piece, moves, safeOrigins);
            if (!moves.isEmpty()) return true;
        }
        return false;
    }

    public void generateMovesForPiece(Piece piece, ArrayList<Move> validMoves) {
        generateMovesForPiece(piece, validMoves, 0L);
    }

    private void generateMovesForPiece(Piece piece, ArrayList<Move> validMoves, long safeOrigins) {
        int square = BitBoard.SquareToIndex(piece.row, piece.col);

        switch (piece.type) {
            case PAWN -> generatePawnMoves(piece, square, validMoves, safeOrigins);
            case KNIGHT -> generateKnightMoves(piece, square, validMoves, safeOrigins);
            case KING -> generateKingMoves(piece, square, validMoves, safeOrigins);
            case BISHOP -> generateSlidingMoves(piece, AttackTables.BISHOP_DIRECTIONS, validMoves, safeOrigins);
            case ROOK -> generateSlidingMoves(piece, AttackTables.ROOK_DIRECTIONS, validMoves, safeOrigins);
            case QUEEN -> generateSlidingMoves(piece, AttackTables.QUEEN_DIRECTIONS, validMoves, safeOrigins);
        }
    }

    public void generatePawnMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        generatePawnMoves(piece, index, validMoves, 0L);
    }

    private void generatePawnMoves(Piece piece, int index, ArrayList<Move> validMoves, long safeOrigins) {
        long attacks = piece.color == 0 ? AttackTables.whitePawnAttacks[index]
                                        : AttackTables.blackPawnAttacks[index];
        generateMovesFromBitBoard(piece, attacks, validMoves, true, safeOrigins);

        // Forward push
        generatePawnPushes(piece, validMoves, safeOrigins);
    }

    public void generatePawnPushes(Piece piece, ArrayList<Move> validMoves) {
        generatePawnPushes(piece, validMoves, 0L);
    }

    private void generatePawnPushes(Piece piece, ArrayList<Move> validMoves, long safeOrigins) {
        int forward = (piece.color == 0) ? -1 : 1;
        int nr = piece.row + forward;

        if (nr >= 0 && nr < 8 && board.getPiece(piece.col, nr) == null) {
            addMoveIfValid(piece, piece.col, nr, validMoves, safeOrigins);

            if (piece.isFirstMove) {
                int nr2 = nr + forward;
                if (nr2 >= 0 && nr2 < 8 && board.getPiece(piece.col, nr2) == null)
                    addMoveIfValid(piece, piece.col, nr2, validMoves, safeOrigins);
            }
        }
    }

    public void generateKnightMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        generateKnightMoves(piece, index, validMoves, 0L);
    }

    private void generateKnightMoves(Piece piece, int index, ArrayList<Move> validMoves, long safeOrigins) {
        long attacks = AttackTables.knightAttacks[index];
        generateMovesFromBitBoard(piece, attacks, validMoves, false, safeOrigins);
    }

    public void generateKingMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        generateKingMoves(piece, index, validMoves, 0L);
    }

    private void generateKingMoves(Piece piece, int index, ArrayList<Move> validMoves, long safeOrigins) {
        long attacks = AttackTables.kingAttacks[index];
        generateMovesFromBitBoard(piece, attacks, validMoves, false, safeOrigins);
        
        // Add castling moves
        if (board.scanner.canCastleKingSide(piece.color)) {
            addMoveIfValid(piece, 6, piece.row, validMoves, safeOrigins);
        }
        if (board.scanner.canCastleQueenSide(piece.color)) {
            addMoveIfValid(piece, 2, piece.row, validMoves, safeOrigins);
        }
    }

    public void generateMovesFromBitBoard(Piece piece, long attacks, ArrayList<Move> validMoves, boolean capture) {
        generateMovesFromBitBoard(piece, attacks, validMoves, capture, 0L);
    }

    private void generateMovesFromBitBoard(Piece piece, long attacks, ArrayList<Move> validMoves, boolean capture, long safeOrigins) {
        long mask = attacks;
        while (mask != 0) {
            int index = Long.numberOfTrailingZeros(mask);
            mask &= (mask - 1);
            int nr = index / 8;
            int nc = index % 8;
            Piece target = board.getPiece(nc, nr);

            if (capture) {
                if (target != null && target.color != piece.color && target.type != PieceType.KING)
                    addMoveIfValid(piece, nc, nr, validMoves, safeOrigins);
                // En passant: check if target square is the capture square (one row past the enemy pawn)
                else if (board.canEnPassant(piece, nc, nr))
                    addMoveIfValid(piece, nc, nr, validMoves, safeOrigins);
            } else {
                // For knights/kings - empty squares OR captures
                if (target == null || (target.color != piece.color && target.type != PieceType.KING))
                    addMoveIfValid(piece, nc, nr, validMoves, safeOrigins);
            }
        }
    }

    public void generateSlidingMoves(Piece piece, int[] directions, ArrayList<Move> validMoves) {
        generateSlidingMoves(piece, directions, validMoves, 0L);
    }

    private void generateSlidingMoves(Piece piece, int[] directions, ArrayList<Move> validMoves, long safeOrigins) {
        int index = BitBoard.SquareToIndex(piece.row, piece.col);

        for (int dir : directions) {
            int prev = index;
            int current = index + dir;
            while (current >= 0 && current < 64) {
                if (!BitBoard.isValidDirection(prev, current, dir))
                    break;

                int nr = current / 8;
                int nc = current % 8;
                Piece target = board.getPiece(nc, nr);

                if (target == null)
                    addMoveIfValid(piece, nc, nr, validMoves, safeOrigins);
                else {
                    // Break after finding blocking piece, only add if capture
                    if (target.color != piece.color && target.type != PieceType.KING)
                        addMoveIfValid(piece, nc, nr, validMoves, safeOrigins);
                    break;
                }
                prev = current;
                current += dir;
            }
        }
    }

    private void addMoveIfValid(Piece piece, int col, int row, ArrayList<Move> validMoves, long safeOrigins) {
        // Check if this is a pawn promotion
        if (piece.type == PieceType.PAWN) {
            boolean isPromotion = (piece.color == 0 && row == 0) || (piece.color == 1 && row == 7);
            if (isPromotion) {
                addPromotionMoves(piece, col, row, validMoves, safeOrigins);
                return;
            }
        }
        
        // Normal move (non-promotion)
        Move move = new Move(board, piece, col, row);

        // Movement geometry and occupancy were established by the generators.
        if (isKingSafe(move, safeOrigins)) {
            validMoves.add(move);
        }
    }

    // Generate 4 moves for each promotion piece
    private void addPromotionMoves(Piece piece, int col, int row, ArrayList<Move> validMoves, long safeOrigins) {
        Move queenMove = new Move(board, piece, col, row, PieceType.QUEEN);
        if (!isKingSafe(queenMove, safeOrigins)) return;
        validMoves.add(queenMove);
        PieceType[] promotionPieces = {PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
        for (PieceType promotionPiece : promotionPieces) {
            Move move = new Move(board, piece, col, row, promotionPiece);
            validMoves.add(move);
        }
        return;
    }

    private boolean isKingSafe(Move move, long safeOrigins) {
        // Geometry and occupancy are already checked. Only an unpinned piece
        // can move freely when out of check; en passant removes a second blocker.
        if (move.piece.type != PieceType.KING
                && (safeOrigins & (1L << (move.row * 8 + move.col))) != 0
                && !board.isEnPassant(move)) return true;
        return !board.scanner.wouldBeInCheck(move);
    }

    private String debugWhyMoveInvalid(Move move) {
        // Check each validation step from Scanner.isValidMove() in order:

        // 1. Check team collision
        if (board.checkTeam(move.piece, move.capture)) {
            return "Cannot capture own piece";
        }

        // 2. Check if it's the right player's turn
        if (!(move.piece.color == board.colorToMove)) {
            return "Not this piece's turn to move (colorToMove=" + board.colorToMove +
                    ", piece.color=" + move.piece.color + ")";
        }

        // 3. Check if the piece can legally move to that square
        if (!(move.piece.isValidPieceMove(move.newCol, move.newRow))) {
            return "Piece cannot move to that square (violates piece movement rules)";
        }

        // 4. Check for collision/blocking
        if (move.piece.checkForCollision(move.newCol, move.newRow)) {
            return "Path is blocked by another piece";
        }

        // 5. Check if move would leave king in check
        if (board.scanner.wouldBeInCheck(move)) {
            return "Move would leave own king in check";
        }

        return "Unknown reason (shouldn't reach here)";
    }
}

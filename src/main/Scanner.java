package main;

import Pieces.Piece;
import Pieces.PieceType;
import engine.AttackTables;
import engine.BitBoard;

import java.util.ArrayList;


public class Scanner {
    Board board;
    public boolean enPassantEnable = false;
    public int enPassantCol;
    public int enPassantRow;

    public Scanner(Board board) {
        this.board = board;
    }

    public boolean scanCheckMate(int color) {
        Piece king = findKing(color);

        // is king in check
        if (!isInCheck(king.col, king.row, color)) {
            return false;
        }
        // copy
        ArrayList<Piece> piecesCopy = new ArrayList<>(board.pieceList);

        for (Piece piece : piecesCopy) {
            if (piece.color == color) {
                for (int i = 0; i < board.MAX_ROWS; i++) {
                    for (int j = 0; j < board.MAX_COLS; j++) {
                        Move move = new Move(board, piece, j, i);
                        if (isValidMove(move)) {
                            return false;  // is legal move = not checkmate
                        }
                    }
                }
            }
        }
        return true;  // only true if no legal moves were found
    }

    public boolean insufficientMaterial() {
        int minors = 0;
        int knights = 0;
        int bishopColors = 0;
        for (Piece piece : board.pieceList) {
            switch (piece.type) {
                case PAWN, ROOK, QUEEN -> { return false; }
                case KNIGHT -> { knights++; minors++; }
                case BISHOP -> {
                    minors++;
                    bishopColors |= 1 << ((piece.col + piece.row) & 1);
                }
                default -> { }
            }
        }
        // Any number of bishops confined to one square color cannot mate.
        return minors <= 1 || (knights == 0 && bishopColors != 3);
    }

    public void enPassantPossible(Move move) {
        enPassantEnable = true;
        enPassantCol = move.piece.col;
        enPassantRow = move.newRow;
    }

    public boolean canCastleQueenSide(int color) {
        Piece king = findKing(color);
        if (king == null || !king.isFirstMove || king.col != 4 || king.row != (color == 0 ? 7 : 0)) return false;
        if (isInCheck(king.col, king.row, color)) return false;

        Piece rook = board.getPiece(0,king.row);
        if (rook == null || rook.type != PieceType.ROOK || !(rook.isFirstMove) || rook.color != color) return false;

        for (int col = 1; col < king.col; col++) {
            if (board.getPiece(col, king.row) != null) return false;
        }

        if (isInCheck(2,king.row,color) || isInCheck(3,king.row,color)) return false;

        return true;
    }

    public boolean canCastleKingSide(int color) {
        Piece king = findKing(color);
        if (king == null || !king.isFirstMove || king.col != 4 || king.row != (color == 0 ? 7 : 0)) return false;
        if (isInCheck(king.col, king.row, color)) return false;

        Piece rook = board.getPiece(7,king.row);
        if (rook == null || rook.type != PieceType.ROOK || !(rook.isFirstMove) || rook.color != color) return false;

        for (int col = 5; col < 7; col++) {
            if (board.getPiece(col, king.row) != null) return false;
        }

        if (isInCheck(6,king.row,color) || isInCheck(5,king.row,color)) return false;

        return true;
    }

    public boolean isValidMove(Move move) {
        if (move.newCol < 0 || move.newCol >= 8 || move.newRow < 0 || move.newRow >= 8
                || (move.capture != null && move.capture.type == PieceType.KING)) return false;
        if (board.checkTeam(move.piece, move.capture))
            return false;
        else if (!(move.piece.color == board.colorToMove))
            return false;
        else if  (!(move.piece.isValidPieceMove(move.newCol, move.newRow)))
            return false;
        else if (move.piece.checkForCollision(move.newCol, move.newRow))
            return false;
        else if (wouldBeInCheck(move))
            return false;
        return true;
    }

    public Piece findPieceByType(ArrayList<Piece> pieces, PieceType typeToFind) {
        for (Piece piece : pieces) {
            if (piece.type == typeToFind)
                return piece;
        }
        return null;
    }

    // Helper to find first non KING piece
    public Piece findNonKingPiece(ArrayList<Piece> pieces) {
        for (Piece piece: pieces) {
            if (!(piece.type == PieceType.KING))
                return piece;
        }
        return null;
    }

    public Piece findKing(int color) {
        return board.king(color);
    }

    // Just checks if the king of given color is currently attacked
    public boolean isInCheck(int col, int row, int color) {
        int square = row * 8 + col;
        // Reverse pawn attacks identify the possible origins of enemy pawns.
        long pawns = color == 0 ? AttackTables.whitePawnAttacks[square] : AttackTables.blackPawnAttacks[square];
        if (hasAttacker(pawns, color, PieceType.PAWN)
                || hasAttacker(AttackTables.knightAttacks[square], color, PieceType.KNIGHT)
                || hasAttacker(AttackTables.kingAttacks[square], color, PieceType.KING)) return true;
        for (int direction : AttackTables.QUEEN_DIRECTIONS) {
            int previous = square;
            for (int next = square + direction; next >= 0 && next < 64; next += direction) {
                if (!BitBoard.isValidDirection(previous, next, direction)) break;
                Piece piece = board.getPiece(next % 8, next / 8);
                if (piece != null) {
                    if (piece.color != color) {
                        boolean straight = direction == 8 || direction == -8 || direction == 1 || direction == -1;
                        if (piece.type == PieceType.QUEEN
                                || piece.type == (straight ? PieceType.ROOK : PieceType.BISHOP)) return true;
                    }
                    break;
                }
                previous = next;
            }
        }
        return false;
    }

    private boolean hasAttacker(long mask, int color, PieceType type) {
        while (mask != 0) {
            int square = Long.numberOfTrailingZeros(mask);
            mask &= mask - 1;
            Piece piece = board.getPiece(square % 8, square / 8);
            if (piece != null && piece.color != color && piece.type == type) return true;
        }
        return false;
    }

    /**
     * Origins that need no king-safety probe for ordinary non-king moves.
     * Returns zero while in check. Compute once per generation call, before
     * any occupancy probes; the mask must never survive a position change.
     */
    public long safeNonKingOrigins(int color) {
        Piece king = findKing(color);
        int square = king.row * 8 + king.col;
        long pawns = color == 0 ? AttackTables.whitePawnAttacks[square] : AttackTables.blackPawnAttacks[square];
        if (hasAttacker(pawns, color, PieceType.PAWN)
                || hasAttacker(AttackTables.knightAttacks[square], color, PieceType.KNIGHT)
                || hasAttacker(AttackTables.kingAttacks[square], color, PieceType.KING)) return 0;

        long pinned = 0;
        for (int direction : AttackTables.QUEEN_DIRECTIONS) {
            int previous = square;
            int blocker = -1;
            for (int next = square + direction; next >= 0 && next < 64; next += direction) {
                if (!BitBoard.isValidDirection(previous, next, direction)) break;
                Piece piece = board.getPiece(next % 8, next / 8);
                if (piece != null) {
                    if (piece.color == color) {
                        if (blocker >= 0) break;
                        blocker = next;
                    } else {
                        boolean straight = Math.abs(direction) == 1 || Math.abs(direction) == 8;
                        if (piece.type == PieceType.QUEEN
                                || piece.type == (straight ? PieceType.ROOK : PieceType.BISHOP)) {
                            if (blocker < 0) return 0; // Direct sliding check.
                            pinned |= 1L << blocker;
                        }
                        break;
                    }
                }
                previous = next;
            }
        }
        return ~pinned & ~(1L << square);
    }

    public boolean wouldBeInCheck(Move move) {
        Piece capturedPiece = board.getPiece(move.newCol, move.newRow);
        boolean enPassant = board.isEnPassant(move);
        Piece epCaptured = enPassant ? board.getPiece(move.newCol, move.row) : null;
        board.setProbeSquare(move.col, move.row, null);
        board.setProbeSquare(move.newCol, move.newRow, move.piece);
        if (enPassant) board.setProbeSquare(move.newCol, move.row, null);
        try {
            Piece king = findKing(move.piece.color);
            int col = move.piece.type == PieceType.KING ? move.newCol : king.col;
            int row = move.piece.type == PieceType.KING ? move.newRow : king.row;
            return isInCheck(col, row, move.piece.color);
        } finally {
            board.setProbeSquare(move.col, move.row, move.piece);
            board.setProbeSquare(move.newCol, move.newRow, capturedPiece);
            if (enPassant) board.setProbeSquare(move.newCol, move.row, epCaptured);
        }
    }
}

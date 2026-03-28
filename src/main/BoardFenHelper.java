package main;

import Pieces.Piece;
import Pieces.PieceType;

import static main.Board.pieceList;

public final class BoardFenHelper {

    private BoardFenHelper() {
    }

    public static void applyCastlingRights(Board board, String castling) {
        // Revoke all castling rights first
        for (Piece piece : pieceList) {
            if (piece.type == PieceType.KING || piece.type == PieceType.ROOK) {
                piece.isFirstMove = false;
            }
        }
        if (castling.equals("-")) return;

        Piece whiteKing = board.getPiece(4, 7);
        if (castling.contains("K")) {
            Piece rook = board.getPiece(7, 7);
            if (whiteKing != null) whiteKing.isFirstMove = true;
            if (rook != null) rook.isFirstMove = true;
        }
        if (castling.contains("Q")) {
            Piece rook = board.getPiece(0, 7);
            if (whiteKing != null) whiteKing.isFirstMove = true;
            if (rook != null) rook.isFirstMove = true;
        }

        Piece blackKing = board.getPiece(4, 0);
        if (castling.contains("k")) {
            Piece rook = board.getPiece(7, 0);
            if (blackKing != null) blackKing.isFirstMove = true;
            if (rook != null) rook.isFirstMove = true;
        }
        if (castling.contains("q")) {
            Piece rook = board.getPiece(0, 0);
            if (blackKing != null) blackKing.isFirstMove = true;
            if (rook != null) rook.isFirstMove = true;
        }
    }

    public static void applyPawnFirstMoveFlags(Board board) {
        for (Piece piece : pieceList) {
            if (piece.type == PieceType.PAWN) {
                // White pawns start at row 6 (rank 2), black pawns start at row 1 (rank 7)
                if (piece.color == 0) {
                    piece.isFirstMove = (piece.row == 6);
                } else {
                    piece.isFirstMove = (piece.row == 1);
                }
            }
        }
    }

    public static void applyEnPassantTarget(Board board, String enPassantTarget) {
        if (enPassantTarget == null || enPassantTarget.equals("-")) {
            board.scanner.enPassantEnable = false;
            return;
        }
        int col = enPassantTarget.charAt(0) - 'a';
        int rank = Character.getNumericValue(enPassantTarget.charAt(1));
        int targetRow = 8 - rank;
        // rank 3 = white pawn just double-moved; it sits at targetRow-1 (one row above target)
        // rank 6 = black pawn just double-moved; it sits at targetRow+1 (one row below target)
        int pawnRow = (rank == 3) ? targetRow - 1 : targetRow + 1;
        board.scanner.enPassantEnable = true;
        board.scanner.enPassantCol = col;
        board.scanner.enPassantRow = pawnRow;
    }

    public static String computeCastlingString(Board board) {
        StringBuilder sb = new StringBuilder();

        // White king castling rights
        Piece whiteKing = board.getPiece(4, 7);
        if (whiteKing != null && whiteKing.type == PieceType.KING && whiteKing.isFirstMove) {
            Piece whiteRookA = board.getPiece(0, 7);
            if (whiteRookA != null && whiteRookA.type == PieceType.ROOK && whiteRookA.isFirstMove)
                sb.append('K');
            Piece whiteRookH = board.getPiece(7, 7);
            if (whiteRookH != null && whiteRookH.type == PieceType.ROOK && whiteRookH.isFirstMove)
                sb.append('Q');
        }

        Piece blackKing = board.getPiece(4, 0);
        if (blackKing != null && blackKing.type == PieceType.KING && blackKing.isFirstMove) {
            Piece blackRookA = board.getPiece(0, 0);
            if (blackRookA != null && blackRookA.type == PieceType.ROOK && blackRookA.isFirstMove)
                sb.append('k');
            Piece blackRookH = board.getPiece(7, 0);
            if (blackRookH != null && blackRookH.type == PieceType.ROOK && blackRookH.isFirstMove) {
                sb.append('q');
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    public static String computeEnPassantTarget(Board board, int color) {
        if (!board.scanner.enPassantEnable)
            return "-";
        int enPassantRow = (color == 0) ? 5 : 2;
        char file = (char) ('a' + board.scanner.enPassantCol);
        int rank = 8 - enPassantRow;
        return "" + file + rank;
    }

    public static String generateFEN(Board board, String enPassantTarget, String castlingRights) {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < Board.MAX_ROWS; row++) {
            int emptyCount = 0;
            for (int col = 0; col < Board.MAX_COLS; col++) {
                Piece piece = board.getPiece(col, row);
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    char pieceChar = switch (piece.type) {
                        case ROOK -> 'r';
                        case KNIGHT -> 'n';
                        case BISHOP -> 'b';
                        case QUEEN -> 'q';
                        case KING -> 'k';
                        case PAWN -> 'p';
                    };
                    fen.append(piece.color == 0 ? Character.toUpperCase(pieceChar) : pieceChar);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < Board.MAX_ROWS - 1) {
                fen.append('/');
            }
        }
        fen.append(" ").append(board.colorToMove == 0 ? "w" : "b");

        // Castling rights and en passant target square
        fen.append(" ").append(castlingRights == null ? "-" : castlingRights);
        fen.append(" ").append(enPassantTarget == null ? "-" : enPassantTarget);
        fen.append(" 0 1");
        return fen.toString();
    }

    public static void updateRepetitionMap(Board board, String fen) {
        int count = board.repetitionMap.getOrDefault(fen, 0) + 1;
        board.repetitionMap.put(fen, count);
        if (count >= 3) {
            board.threefold = true;
        }
    }
}

package main;

import Pieces.Piece;
import Pieces.PieceType;


public final class BoardFenHelper {

    private BoardFenHelper() {
    }

    public static void applyCastlingRights(Board board, String castling) {
        // Revoke all castling rights first
        for (Piece piece : board.pieceList) {
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
        for (Piece piece : board.pieceList) {
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
            Piece whiteKingSideRook = board.getPiece(7, 7);
            if (whiteKingSideRook != null && whiteKingSideRook.type == PieceType.ROOK && whiteKingSideRook.isFirstMove)
                sb.append('K');
            Piece whiteQueenSideRook = board.getPiece(0, 7);
            if (whiteQueenSideRook != null && whiteQueenSideRook.type == PieceType.ROOK && whiteQueenSideRook.isFirstMove)
                sb.append('Q');
        }

        Piece blackKing = board.getPiece(4, 0);
        if (blackKing != null && blackKing.type == PieceType.KING && blackKing.isFirstMove) {
            Piece blackKingSideRook = board.getPiece(7, 0);
            if (blackKingSideRook != null && blackKingSideRook.type == PieceType.ROOK && blackKingSideRook.isFirstMove)
                sb.append('k');
            Piece blackQueenSideRook = board.getPiece(0, 0);
            if (blackQueenSideRook != null && blackQueenSideRook.type == PieceType.ROOK && blackQueenSideRook.isFirstMove) {
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

        // Castling rights, en passant target square and move counters
        fen.append(" ").append(castlingRights == null ? "-" : castlingRights);
        fen.append(" ").append(enPassantTarget == null ? "-" : enPassantTarget);
        fen.append(" ").append(board.halfMoveCounter).append(" ").append(board.fullMoveNumber);
        return fen.toString();
    }

    public static String repetitionKey(String fen) {
        if (fen == null || fen.isBlank()) {
            return "";
        }

        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            return fen.trim();
        }

        return parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3];
    }

}

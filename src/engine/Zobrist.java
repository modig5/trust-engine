package engine;

import Pieces.Piece;
import Pieces.PieceType;
import main.Board;

import java.util.Random;

public class Zobrist {
    // [color][pieceType][square] — 2 colors, 6 piece types, 64 squares
    private static final long[][][] PIECE_KEYS = new long[2][6][64];
    private static final long SIDE_TO_MOVE_KEY;
    // 4 castling rights: white kingside, white queenside, black kingside, black queenside
    private static final long[] CASTLING_KEYS = new long[4];
    // 8 en passant files (a-h)
    private static final long[] EN_PASSANT_KEYS = new long[8];

    static {
        Random rng = new Random(0x12345678L);

        for (int color = 0; color < 2; color++) {
            for (int piece = 0; piece < 6; piece++) {
                for (int sq = 0; sq < 64; sq++) {
                    PIECE_KEYS[color][piece][sq] = rng.nextLong();
                }
            }
        }

        SIDE_TO_MOVE_KEY = rng.nextLong();

        for (int i = 0; i < 4; i++) {
            CASTLING_KEYS[i] = rng.nextLong();
        }

        for (int i = 0; i < 8; i++) {
            EN_PASSANT_KEYS[i] = rng.nextLong();
        }
    }

    private static int pieceIndex(PieceType type) {
        return switch (type) {
            case PAWN -> 0;
            case KNIGHT -> 1;
            case BISHOP -> 2;
            case ROOK -> 3;
            case QUEEN -> 4;
            case KING -> 5;
        };
    }

    private static int square(int col, int row) {
        return row * 8 + col;
    }

    // Get the key for a piece at a given position
    public static long pieceKey(int color, PieceType type, int col, int row) {
        return PIECE_KEYS[color][pieceIndex(type)][square(col, row)];
    }

    public static long sideKey() {
        return SIDE_TO_MOVE_KEY;
    }

    public static long castlingKey(int index) {
        return CASTLING_KEYS[index];
    }

    public static long enPassantKey(int col) {
        return EN_PASSANT_KEYS[col];
    }

    // Compute full hash from scratch for a given board
    public static long computeHash(Board board) {
        long hash = 0;

        for (Piece piece : board.pieceList) {
            hash ^= pieceKey(piece.color, piece.type, piece.col, piece.row);
        }

        if (board.colorToMove == 1) {
            hash ^= SIDE_TO_MOVE_KEY;
        }

        // Castling rights (based on king/rook isFirstMove)
        Piece whiteKing = board.getPiece(4, 7);
        if (whiteKing != null && whiteKing.type == PieceType.KING && whiteKing.isFirstMove) {
            Piece rookH = board.getPiece(7, 7);
            if (rookH != null && rookH.type == PieceType.ROOK && rookH.isFirstMove)
                hash ^= CASTLING_KEYS[0]; // white kingside
            Piece rookA = board.getPiece(0, 7);
            if (rookA != null && rookA.type == PieceType.ROOK && rookA.isFirstMove)
                hash ^= CASTLING_KEYS[1]; // white queenside
        }

        Piece blackKing = board.getPiece(4, 0);
        if (blackKing != null && blackKing.type == PieceType.KING && blackKing.isFirstMove) {
            Piece rookH = board.getPiece(7, 0);
            if (rookH != null && rookH.type == PieceType.ROOK && rookH.isFirstMove)
                hash ^= CASTLING_KEYS[2]; // black kingside
            Piece rookA = board.getPiece(0, 0);
            if (rookA != null && rookA.type == PieceType.ROOK && rookA.isFirstMove)
                hash ^= CASTLING_KEYS[3]; // black queenside
        }

        // En passant
        if (board.scanner.enPassantEnable) {
            hash ^= EN_PASSANT_KEYS[board.scanner.enPassantCol];
        }

        return hash;
    }
}

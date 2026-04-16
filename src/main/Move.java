package main;

import Pieces.Piece;
import Pieces.PieceType;

public class Move {
    public int col, row;
    public int newCol, newRow;
    public int oldColorToMove;
    public boolean enPassantEnabled;
    public int enPassantCol;
    public int enPassantRow;
    public boolean firstMove;
    public String previousFEN;
    public String resultingFEN;
    public boolean previousThreefold;
    public long previousZobristHash;

    public boolean wasCastling;
    public boolean wasEnPassant;
    public boolean wasPromotion;
    public Piece promotedFrom;
    public PieceType promotionPiece;
    
    // Save rookFirstMove so we can restore it when we undo castling
    public boolean rookFirstMove;

    public Piece piece;
    public Piece capture;

    public Move(Board board, Piece piece, int newCol, int newRow) {
        this(board, piece, newCol, newRow, null);
    }

    public Move(Board board, Piece piece, int newCol, int newRow, PieceType promotionPiece) {
        col = piece.col;
        row = piece.row;
        this.newCol = newCol;
        this.newRow = newRow;

        this.piece = piece;
        this.capture = board.getPiece(newCol, newRow);
        this.promotionPiece = promotionPiece;
    }

    public static void printMove(Move move) {
        if (move == null) {
            System.out.println("null move");
            return;
        }
        String s = "" + (char)('a' + move.col) + (8 - move.row)
                      + (char)('a' + move.newCol) + (8 - move.newRow);
        if (move.promotionPiece != null) {
            s += switch (move.promotionPiece) {
                case KNIGHT -> "n";
                case BISHOP -> "b";
                case ROOK   -> "r";
                case QUEEN  -> "q";
                default     -> "";
            };
        }
        System.out.println(s);
    }
}

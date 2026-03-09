package main;

import Pieces.Piece;

public class Move {
    public int col, row;
    public int newCol, newRow;
    public int oldColorToMove;
    public boolean enPassantEnabled;
    public int enPassantCol;
    public int enPassantRow;
    public boolean firstMove;

    public boolean wasCastling;
    public boolean wasEnPassant;
    public boolean wasPromotion;
    public Piece promotedFrom;
    public String promotionPiece; // "Queen", "Rook", "Bishop", or "Knight"
    
    // Save rookFirstMove so we can restore it when we undo castling
    public boolean rookFirstMove;

    public Piece piece;
    public Piece capture;

    public Move(Board board, Piece piece, int newCol, int newRow) {
        this(board, piece, newCol, newRow, null);
    }

    public Move(Board board, Piece piece, int newCol, int newRow, String promotionPiece) {
        col = piece.col;
        row = piece.row;
        this.newCol = newCol;
        this.newRow = newRow;

        this.piece = piece;
        this.capture = board.getPiece(newCol, newRow);
        this.promotionPiece = promotionPiece;
    }


}

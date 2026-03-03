package main;

import Pieces.Piece;

import java.util.ArrayList;

import static main.Board.pieceList;

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
        ArrayList<Piece> piecesCopy = new ArrayList<>(pieceList);

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

    public void enPassantPossible(Move move) {
        enPassantEnable = true;
        enPassantCol = move.piece.col;
        enPassantRow = move.newRow;
    }

    public boolean canCastleQueenSide(int color) {
        Piece king = findKing(color);
        if (king == null || !(king.isFirstMove)) return false;
        if (isInCheck(king.col, king.row, color)) return false;

        Piece rook = board.getPiece(0,king.row);
        if (rook == null || !rook.name.equals("Rook") || !(rook.isFirstMove) || rook.color != color) return false;

        for (int col = 1; col < king.col; col++) {
            if (board.getPiece(col, king.row) != null) return false;
        }

        if (isInCheck(2,king.row,color) || isInCheck(3,king.row,color)) return false;

        return true;
    }

    public boolean canCastleKingSide(int color) {
        Piece king = findKing(color);
        if (king == null || !(king.isFirstMove)) return false;
        if (isInCheck(king.col, king.row, color)) return false;

        Piece rook = board.getPiece(7,king.row);
        if (rook == null || !rook.name.equals("Rook") || !(rook.isFirstMove) || rook.color != color) return false;

        for (int col = 5; col < 7; col++) {
            if (board.getPiece(col, king.row) != null) return false;
        }

        if (isInCheck(6,king.row,color) || isInCheck(5,king.row,color)) return false;

        return true;
    }

    public boolean isValidMove(Move move) {
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

    public Piece findKing(int color) {
        for (Piece piece : pieceList) {
            if (piece.color == color && piece.name.equals("King")) {
                return piece;
            }
        }
        return null;
    }

    public boolean isInCheck(int col, int row, int color) {
        for (Piece piece : pieceList) {
            if (piece.color != color) {
                if (piece.isValidPieceMove(col, row) && !piece.checkForCollision(col, row)) {
                    return true;
                }
            }
        }
        return false;
    }

    // schizo solutions but works
    public boolean wouldBeInCheck(Move move) {
        int oldCol = move.piece.col;
        int oldRow = move.piece.row;
        Piece capturedPiece = board.getPiece(move.newCol, move.newRow);

        // For en passant, the captured pawn is not on the destination square
        Piece epCaptured = null;
        if (board.isEnPassant(move)) {
            int squareDiff = move.piece.color == 0 ? 1 : -1;
            epCaptured = board.getPiece(move.newCol, move.newRow + squareDiff);
            if (epCaptured != null) pieceList.remove(epCaptured);
        }

        move.piece.col = move.newCol;
        move.piece.row = move.newRow;
        if (capturedPiece != null) pieceList.remove(capturedPiece);

        Piece king = findKing(move.piece.color);
        boolean inCheck = isInCheck(king.col, king.row, king.color);

        move.piece.col = oldCol;
        move.piece.row = oldRow;
        if (capturedPiece != null) pieceList.add(capturedPiece);
        if (epCaptured != null) pieceList.add(epCaptured);

        return inCheck;
    }
}

package main;

import Pieces.Piece;
import Pieces.PieceType;

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
        ArrayList<Piece> whitePieces = new ArrayList<>();
        ArrayList<Piece> blackPieces = new ArrayList<>();

        // Add all pieces to either sides list
        for (Piece piece : board.pieceList) {
            if (piece.color == 0) whitePieces.add(piece);
            else blackPieces.add(piece);
        }

        int whiteAmount = whitePieces.size();
        int blackAmount = blackPieces.size();
        
        if (whiteAmount == 1 && blackAmount == 1)
            return true;

        // Check for bishop vs king (both sides)
        if (whiteAmount == 1 && blackAmount == 2) {
            Piece otherPiece = findNonKingPiece(blackPieces);
            if (otherPiece.type == PieceType.BISHOP || otherPiece.type == PieceType.KNIGHT)
                return true;
        }

        if (whiteAmount == 2 && blackAmount == 1) {
            Piece otherPiece = findNonKingPiece(whitePieces);
            if (otherPiece.type == PieceType.BISHOP || otherPiece.type == PieceType.KNIGHT)
                return true;
        }

        // King and Bishop vs King and Bishop (same colored bishops)
        if (whiteAmount == 2 && blackAmount == 2) {
            Piece whiteBishop = findPieceByType(whitePieces, PieceType.BISHOP);
            Piece blackBishop = findPieceByType(blackPieces, PieceType.BISHOP);
            if (whiteBishop != null && blackBishop != null) {
                boolean whiteBishopLightSquared = (whiteBishop.col + whiteBishop.row) % 2 == 0;
                boolean blackBishopLightSquared = (blackBishop.col + blackBishop.row) % 2 == 0;
                return blackBishopLightSquared == whiteBishopLightSquared;
            }
        }
        return false;
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
        if (rook == null || rook.type != PieceType.ROOK || !(rook.isFirstMove) || rook.color != color) return false;

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
        if (rook == null || rook.type != PieceType.ROOK || !(rook.isFirstMove) || rook.color != color) return false;

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
        for (Piece piece : board.pieceList) {
            if (piece.color == color && piece.type == PieceType.KING) {
                return piece;
            }
        }
        return null;
    }

    // Just checks if the king of given color is currently attacked
    public boolean isInCheck(int col, int row, int color) {
        for (Piece piece : board.pieceList) {
            if (piece.color != color) {
                if (isSquareAttackedBy(piece, col, row)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Checks for whether a square is attacked by a specific piece
    private boolean isSquareAttackedBy(Piece piece, int col, int row) {
        int dCol = Math.abs(col - piece.col);
        int dRow = Math.abs(row - piece.row);

        return switch (piece.type) {
            case PAWN -> row == piece.row + (piece.color == 0 ? -1 : 1) && dCol == 1;
            case KNIGHT -> (dCol == 2 && dRow == 1) || (dCol == 1 && dRow == 2);
            case KING -> dCol <= 1 && dRow <= 1;
            case BISHOP -> dCol == dRow && !piece.checkForCollision(col, row);
            case ROOK -> (piece.col == col || piece.row == row) && !piece.checkForCollision(col, row);
            case QUEEN -> ((dCol == dRow) || (piece.col == col || piece.row == row))
                    && !piece.checkForCollision(col, row);
        };
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
            if (epCaptured != null) board.pieceList.remove(epCaptured);
        }

        move.piece.col = move.newCol;
        move.piece.row = move.newRow;
        if (capturedPiece != null) board.pieceList.remove(capturedPiece);

        Piece king = findKing(move.piece.color);
        boolean inCheck = isInCheck(king.col, king.row, king.color);

        move.piece.col = oldCol;
        move.piece.row = oldRow;
        if (capturedPiece != null) board.pieceList.add(capturedPiece);
        if (epCaptured != null) board.pieceList.add(epCaptured);

        return inCheck;
    }
}

package Pieces;

import main.Board;

public class Pawn extends Piece {
    public int squareDiff = 1;

    public Pawn (Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.PAWN;

        if (color == 0) {
            image = getImage("/resources/Chess_plt60.png");
        }
        else {
            image = getImage("/resources/Chess_pdt60.png");
        }
    }

    @Override
    public boolean isValidPieceMove(int newCol, int newRow) {
        // reject off-board destinations
        if (newRow < 0 || newRow > 7 || newCol < 0 || newCol > 7) return false;

        if (this.color == 0)
            squareDiff = -1;
        else
            squareDiff = 1;

        // En Passant logic
        if (this.row == 3 && board.scanner.enPassantEnable && this.color == 0) {
            if (newCol == board.scanner.enPassantCol && row == board.scanner.enPassantRow && newRow == this.row - 1 && Math.abs(newCol - col) == 1) {
                return true;
            }
        }
        if (this.row == 4 && board.scanner.enPassantEnable && this.color == 1) {
            if (newCol == board.scanner.enPassantCol && row == board.scanner.enPassantRow && newRow == this.row + 1 && Math.abs(newCol - col) == 1) {
                return true;
            }
        }

        // Push pawn 1 square
        if (newRow == row + squareDiff && newCol == this.col) {
            return true;
        }
        // Push pawn 2 squares if first move
        if (this.isFirstMove && newRow == row + squareDiff * 2 && newCol == this.col) {
            int intermediate = row + squareDiff;
            if (intermediate < 0 || intermediate > 7) return false;
            if (board.getPiece(col, intermediate) == null)
                return true;
        }
        // Diagonal captures
        if (newRow == row + squareDiff) {
            if ((newCol == col - 1 || newCol == col + 1) &&
            board.getPiece(newCol, newRow) != null)
                return true;
        }
        return false;
    }

    @Override
    public boolean checkForCollision(int newCol, int newRow) {
        if (newCol == this.col)
            return board.getPiece(newCol, newRow) != null;
        return false;
    }
}

package Pieces;

import main.Board;

public class Bishop extends Piece {
    public Bishop(Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.BISHOP;


        if (color == 0) {
            image = getImage("/resources/Chess_blt60.png");
        } else {
            image = getImage("/resources/Chess_bdt60.png");
        }
    }

    public boolean isValidPieceMove(int newCol, int newRow) {
        int colDifference = Math.abs(newCol - this.col);
        int rowDifference = Math.abs(newRow - this.row);

        return (colDifference == rowDifference);
    }

    public boolean checkForCollision(int newCol, int newRow) {
        int colDir = (newCol - this.col) > 0 ? 1 : -1;
        int rowDir = (newRow - this.row) > 0 ? 1 : -1;

        int currentCol = this.col + colDir;
        int currentRow = this.row + rowDir;

        while (currentCol != newCol && currentRow != newRow) {
            if (board.getPiece(currentCol, currentRow) != null) {
                return true;
            }

            currentCol += colDir;
            currentRow += rowDir;
        }
        return false;
    }
}

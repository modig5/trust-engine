package Pieces;

import main.Board;

public class Queen extends Piece {
    public Queen(Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.QUEEN;


        if (color == 0) {
            image = getImage("/resources/Chess_qlt60.png");
        } else {
            image = getImage("/resources/Chess_qdt60.png");
        }
    }

    @Override
    public boolean isValidPieceMove(int newCol, int newRow) {
        int colDifference = Math.abs(newCol - this.col);
        int rowDifference = Math.abs(newRow - this.row);
        return (rowDifference == colDifference) ||
                (newCol == this.col || newRow == this.row);
    }

    @Override
    public boolean checkForCollision(int newCol, int newRow) {
        // DIAGONAL COLLISION
        if (Math.abs(this.col - newCol) == Math.abs(this.row - newRow)) {
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
        }
        // CHECK STRAIGHT
        else if (this.col == newCol || this.row == newRow) {
            int colDirection = Integer.compare(newCol - this.col, 0);
            int rowDirection = Integer.compare(newRow - this.row, 0);

            int currentCol = this.col + colDirection;
            int currentRow = this.row + rowDirection;

            while (currentCol != newCol || currentRow != newRow) {
                if (board.getPiece(currentCol, currentRow) != null)
                    return true;
                currentCol += colDirection;
                currentRow += rowDirection;
            }
        }
        return false;
    }
}


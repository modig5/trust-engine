package Pieces;

import main.Board;

public class Rook extends Piece {
    public Rook (Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.ROOK;


        if (color == 0) {
            image = getImage("/resources/Chess_rlt60.png");
        }
        else {
            image = getImage("/resources/Chess_rdt60.png");
        }
    }

    @Override
    public boolean isValidPieceMove(int newCol, int newRow) {
        if (newCol == this.col || newRow == this.row)
            return true;
        return false;
    }

    @Override
    public boolean checkForCollision(int newCol, int newRow) {
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
        return false;
    }
}
